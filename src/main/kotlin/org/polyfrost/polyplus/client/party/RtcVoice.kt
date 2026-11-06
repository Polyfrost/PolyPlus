package org.polyfrost.polyplus.client.party

import gg.sona.eos.EosPlatform
import gg.sona.eos.EosResult
import gg.sona.eos.common.ProductUserId
import gg.sona.eos.rtc.EosRtcAudioInputStatus
import gg.sona.eos.rtc.EosRtcAudioStatus
import gg.sona.eos.rtc.EosRtcParticipantStatus
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.eos.EosSdkBridge
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.http.PartyApiException
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal object RtcVoice {
    private val LOGGER = LogManager.getLogger()

    private const val MAX_BACKOFF_MS = 60_000L
    private const val VOLUME_STEP = 2f
    private const val VOLUME_INTERVAL_MS = 100L
    private const val UNCHANGED = 50f
    private const val NOT_SENT = -1f

    sealed interface Outcome {
        object Left : Outcome
        object WrongTransport : Outcome
        class Failed(val reason: String) : Outcome
    }

    private class Volume(var value: Float, var at: Long)

    @Volatile private var job: Job? = null
    @Volatile private var bridge: EosSdkBridge? = null
    @Volatile private var room: String? = null
    @Volatile private var partyId: String? = null
    @Volatile private var notifications: List<(EosPlatform) -> Unit> = emptyList()
    @Volatile private var transmit = true
    @Volatile private var deafened = false
    @Volatile private var sentInputVolume = NOT_SENT

    private val speaking = ConcurrentHashMap.newKeySet<String>()
    private val volumes = ConcurrentHashMap<String, Volume>()
    private val receiving = ConcurrentHashMap<String, Boolean>()

    @Volatile var joined = false
        private set

    val running: Boolean get() = job?.isActive == true

    fun start(party: String, onOutcome: (Outcome) -> Unit) {
        if (partyId == party && job?.isActive == true) return
        stop()
        partyId = party
        job = PolyPlusClient.SCOPE.launch {
            var backoff = 1_000L
            while (partyId == party) {
                val outcome = session() ?: break
                when (outcome) {
                    is Outcome.Failed -> if (outcome.reason == INVALID_AUTH) {
                        delay(backoff)
                        backoff = min(backoff * 2, MAX_BACKOFF_MS)
                        continue
                    }
                    else -> Unit
                }
                if (partyId == party) onOutcome(outcome)
                break
            }
        }
    }

    private const val INVALID_AUTH = "invalid_auth"

    private suspend fun session(): Outcome? {
        val bridge = P2PSessionManager.activeBridge ?: return Outcome.Failed("EOS isn't running")
        if (!bridge.rtcEnabled) return Outcome.Failed("EOS voice rooms aren't available")
        val local = bridge.localUser ?: return Outcome.Failed("EOS isn't logged in")
        val token = PartyApi.voiceToken().getOrElse { error ->
            val code = PartyErrors.code(error)
            return when {
                code == PartyErrors.TRANSPORT_P2P -> Outcome.WrongTransport
                error is PartyApiException && error.status == 429 -> Outcome.Failed(INVALID_AUTH)
                else -> Outcome.Failed(code ?: error.message ?: "token request failed")
            }
        }
        this.bridge = bridge
        val disconnected = CompletableDeferred<EosResult>()
        this.room = token.roomName
        val result = runSuspendCatching {
            bridge.callOnTick("EOS::RTC::JoinRoom") { platform ->
                val user = ProductUserId.fromString(local.raw)
                install(platform, user, token.roomName, disconnected)
                platform.rtc.joinRoom(user, token.roomName, token.clientBaseUrl, token.participantToken)
            }.result
        }.getOrElse { error ->
            LOGGER.warn("Joining the party voice room failed", error)
            queueLeave(bridge, token.roomName)
            this.room = null
            uninstall()
            return Outcome.Failed(error.message ?: "join failed")
        }
        if (result != EosResult.Success) {
            LOGGER.warn("EOS refused the party voice room: {}", result)
            this.room = null
            uninstall()
            return if (result == EosResult.InvalidAuth) Outcome.Failed(INVALID_AUTH) else Outcome.Failed(result.toString())
        }
        room = token.roomName
        joined = true
        LOGGER.info("Joined party voice room {}", token.roomName)
        volumes.clear()
        receiving.clear()
        sentInputVolume = NOT_SENT
        applySending()
        applyDeafen()
        PartyVoice.onRtcJoined()
        val reason = disconnected.await()
        joined = false
        uninstall()
        room = null
        speaking.clear()
        LOGGER.info("Left party voice room ({})", reason)
        return when (reason) {
            EosResult.Success -> if (partyId == null) null else Outcome.Left
            EosResult.InvalidAuth -> Outcome.Failed(INVALID_AUTH)
            else -> Outcome.Failed(reason.toString())
        }
    }

    private fun install(platform: EosPlatform, user: ProductUserId, room: String, disconnected: CompletableDeferred<EosResult>) {
        val rtc = platform.rtc
        val handles = ArrayList<(EosPlatform) -> Unit>()
        val onDisconnect = rtc.addNotifyDisconnected(user, room) { info -> disconnected.complete(info.result) }
        handles += { it.rtc.removeNotifyDisconnected(onDisconnect) }
        val onStatus = rtc.addNotifyParticipantStatusChanged(user, room) { info ->
            if (info.status == EosRtcParticipantStatus.Left) {
                val id = info.participantId.toStringValue()
                speaking.remove(id)
                volumes.remove(id)
                receiving.remove(id)
            }
        }
        handles += { it.rtc.removeNotifyParticipantStatusChanged(onStatus) }
        val onUpdate = rtc.audio.addNotifyParticipantUpdated(user, room) { info ->
            val id = info.participantId.toStringValue()
            if (info.speaking && info.audioStatus == EosRtcAudioStatus.Enabled) speaking.add(id) else speaking.remove(id)
        }
        handles += { it.rtc.audio.removeNotifyParticipantUpdated(onUpdate) }
        val onInput = rtc.audio.addNotifyAudioInputState(user, room) { info ->
            if (info.status == EosRtcAudioInputStatus.Failed) PartyVoice.onCaptureFailed()
        }
        handles += { it.rtc.audio.removeNotifyAudioInputState(onInput) }
        notifications = handles
    }

    private fun uninstall() {
        val handles = notifications
        notifications = emptyList()
        if (handles.isEmpty()) return
        bridge?.onTick { platform -> handles.forEach { remove -> runCatching { remove(platform) } } }
    }

    fun stop() {
        val party = partyId
        partyId = null
        val current = job
        job = null
        val bridge = bridge
        val room = room
        if (bridge != null && room != null) queueLeave(bridge, room)
        current?.cancel()
        joined = false
        uninstall()
        this.room = null
        speaking.clear()
        volumes.clear()
        receiving.clear()
        if (party != null) LOGGER.info("Stopped EOS party voice for party {}", party)
    }

    private fun queueLeave(bridge: EosSdkBridge, room: String) {
        val local = bridge.localUser ?: return
        bridge.onTick { platform -> platform.rtc.leaveRoom(ProductUserId.fromString(local.raw), room) }
    }

    private fun onRoom(block: (EosPlatform, ProductUserId, String) -> CompletableFuture<*>?) {
        val bridge = bridge ?: return
        val room = room ?: return
        val local = bridge.localUser ?: return
        if (!joined) return
        bridge.onTick { platform -> block(platform, ProductUserId.fromString(local.raw), room) }
    }

    fun setTransmit(enabled: Boolean) {
        if (transmit == enabled) return
        transmit = enabled
        applySending()
    }

    private fun applySending() {
        val status = if (transmit) EosRtcAudioStatus.Enabled else EosRtcAudioStatus.Disabled
        onRoom { platform, user, room -> platform.rtc.audio.updateSending(user, room, status) }
    }

    fun setDeafened(value: Boolean) {
        if (deafened == value) return
        deafened = value
        applyDeafen()
    }

    private fun applyDeafen() {
        val volume = if (deafened) 0f else UNCHANGED
        onRoom { platform, user, room -> platform.rtc.audio.updateReceivingVolume(user, room, volume) }
    }

    fun setInputGain(gain: Float) {
        val volume = (UNCHANGED * gain).coerceIn(0f, 100f)
        if (!joined || volume == sentInputVolume) return
        sentInputVolume = volume
        onRoom { platform, user, room ->
            platform.rtc.audio.updateSendingVolume(user, room, volume).whenComplete { result, error ->
                if (result != EosResult.Success) LOGGER.warn("EOS refused the microphone volume: {}", result, error)
            }
        }
    }

    fun apply(mix: Map<String, PeerMix>, outputGain: Float) {
        if (!joined) return
        val now = System.currentTimeMillis()
        mix.forEach { (puid, peer) ->
            val receive = !peer.silenced
            if (receiving[puid] != receive) {
                receiving[puid] = receive
                onRoom { platform, user, room -> platform.rtc.audio.updateReceiving(user, room, ProductUserId.fromString(puid), receive) }
            }
            val target = (UNCHANGED * peer.gain * outputGain).coerceIn(0f, 100f)
            val last = volumes[puid]
            if (last != null) {
                val close = target > 0f && abs(last.value - target) <= VOLUME_STEP
                if (last.value == target || close || now - last.at < VOLUME_INTERVAL_MS) return@forEach
            }
            volumes[puid] = Volume(target, now)
            onRoom { platform, user, room -> platform.rtc.audio.updateParticipantVolume(user, room, ProductUserId.fromString(puid), target) }
        }
    }

    fun speaking(): Set<String> = speaking.toSet()

    suspend fun deviceCounts(): Pair<Int, Int>? {
        val bridge = P2PSessionManager.activeBridge ?: return null
        if (!bridge.rtcEnabled) return null
        return runSuspendCatching {
            bridge.callOnTick("EOS::RTCAudio::QueryInputDevicesInformation") { it.rtc.audio.queryInputDevicesInformation() }
            bridge.callOnTick("EOS::RTCAudio::QueryOutputDevicesInformation") { it.rtc.audio.queryOutputDevicesInformation() }
            bridge.callOnTick("EOS::RTCAudio::CountDevices") { platform ->
                CompletableFuture.completedFuture(platform.rtc.audio.getInputDevicesCount() to platform.rtc.audio.getOutputDevicesCount())
            }
        }.onFailure { LOGGER.warn("Could not list EOS voice devices", it) }.getOrNull()
    }

    suspend fun applyDevices(input: String, output: String) {
        val bridge = bridge ?: P2PSessionManager.activeBridge ?: return
        if (!bridge.rtcEnabled) return
        val local = bridge.localUser ?: return
        runSuspendCatching {
            bridge.callOnTick("EOS::RTCAudio::QueryInputDevicesInformation") { it.rtc.audio.queryInputDevicesInformation() }
            bridge.callOnTick("EOS::RTCAudio::QueryOutputDevicesInformation") { it.rtc.audio.queryOutputDevicesInformation() }
            bridge.callOnTick("EOS::RTCAudio::SetDevices") { platform ->
                val audio = platform.rtc.audio
                val user = ProductUserId.fromString(local.raw)
                val inputId = (0 until audio.getInputDevicesCount()).mapNotNull(audio::copyInputDeviceInformationByIndex)
                    .firstOrNull { PartyAudio.deviceLabel(it.deviceName) == PartyAudio.deviceLabel(input) }?.deviceId
                val outputId = (0 until audio.getOutputDevicesCount()).mapNotNull(audio::copyOutputDeviceInformationByIndex)
                    .firstOrNull { PartyAudio.deviceLabel(it.deviceName) == PartyAudio.deviceLabel(output) }?.deviceId
                audio.setOutputDeviceSettings(user, outputId)
                audio.setInputDeviceSettings(user, inputId)
            }
            sentInputVolume = NOT_SENT
            setInputGain(PartyVoiceSettings.inputGain())
        }.onFailure { LOGGER.warn("Could not apply party voice devices", it) }
    }
}
