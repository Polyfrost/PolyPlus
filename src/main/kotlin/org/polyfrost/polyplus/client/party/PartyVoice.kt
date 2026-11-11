package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.eos.EosRtcSupport
import org.polyfrost.polyplus.client.network.http.responses.MemberVoice
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.VoiceTransport
import org.polyfrost.polyplus.client.network.p2p.EosStatus
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.SocialOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object PartyVoice {
    private val LOGGER = LogManager.getLogger()

    private const val RTC_RETRY_MS = 5 * 60_000L
    private const val VAD_HANGOVER_MS = 400L
    private const val SILENCE_HINT_MS = 10_000L

    data class Ui(
        val inVoice: Boolean = false,
        val transport: VoiceTransport? = null,
        val muted: Boolean = false,
        val deafened: Boolean = false,
        val micBlocked: Boolean = false,
        val micSilent: Boolean = false,
        val blockedNotice: String? = null,
        val talking: Boolean = false,
        val speaking: Set<String> = emptySet(),
        val noticeOpen: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    @Volatile private var inVoice = false
    @Volatile private var muted = false
    @Volatile private var deafened = false
    @Volatile private var micBlocked = false
    @Volatile private var pttHeld = false
    @Volatile private var rtcFailedAt = 0L
    @Volatile private var rtcSessionFailed = false
    @Volatile private var linuxRtcChecked = false
    @Volatile private var linuxRtcReady = false
    @Volatile private var active: VoiceTransport? = null
    @Volatile private var talking = false
    @Volatile private var selfHardMuted = false
    @Volatile private var noticeOpen = false
    @Volatile private var capture: VoiceStream? = null
    @Volatile private var lastLoud = 0L
    @Volatile private var silentSince = 0L
    @Volatile private var micHeard = false
    @Volatile private var micSilent = false
    private var speakingPlayers: Set<String> = emptySet()
    private var autoJoinedParty: String? = null
    private var voicePartyId: String? = null

    private val launcher: String? by lazy {
        runCatching {
            val parent = ProcessHandle.current().parent().orElse(null) ?: return@runCatching null
            val command = parent.info().command().orElse(null) ?: return@runCatching null
            Regex("([^/]+)\\.app/").find(command)?.groupValues?.get(1) ?: command.substringAfterLast('/').substringAfterLast('\\')
        }.getOrNull()?.takeIf { it.isNotBlank() && !it.startsWith("java", ignoreCase = true) }
    }

    fun join() {
        val party = PartyRepository.party.value ?: return
        if (!PolyPlusConfig.voiceEnabled) return
        if (!PolyPlusConfig.voiceNoticeAccepted) {
            noticeOpen = true
            publish()
            return
        }
        if (inVoice) return
        LOGGER.info("Joining party voice for party {}", party.id)
        inVoice = true
        voicePartyId = party.id
        micBlocked = false
        startCapture()
        publish()
    }

    fun acceptNotice() {
        PolyPlusConfig.voiceNoticeAccepted = true
        PolyPlusConfig.save()
        noticeOpen = false
        join()
    }

    fun dismissNotice() {
        noticeOpen = false
        publish()
    }

    fun leave() {
        if (!inVoice) return
        inVoice = false
        stopTransport()
        stopCapture()
        talking = false
        speakingPlayers = emptySet()
        publish()
    }

    fun toggleMute() {
        muted = !muted
        PartyMesh.sendState(muted, deafened)
        publish()
    }

    fun toggleDeafen() {
        deafened = !deafened
        PartyMesh.sendState(muted, deafened)
        publish()
    }

    fun onToggleMuteKey() {
        if (!inVoice) return
        toggleMute()
        notifyKey(if (muted) "Muted" else "Unmuted")
    }

    fun onToggleDeafenKey() {
        if (!inVoice) return
        toggleDeafen()
        notifyKey(if (deafened) "Deafened" else "Undeafened")
    }

    fun onJoinLeaveKey() {
        if (inVoice) {
            leave()
            notifyKey("Left party voice")
            return
        }
        if (PartyRepository.party.value == null) return
        join()
        when {
            noticeOpen -> SocialOverlay.open()
            inVoice -> notifyKey("Joined party voice")
        }
    }

    private fun notifyKey(message: String) {
        runCatching { Notifications.builder("Party Voice", message).duration(1500f).send() }
            .onFailure { LOGGER.warn("Could not show the party voice notification", it) }
    }

    fun retryMic() {
        if (!inVoice) return
        micBlocked = false
        stopCapture()
        startCapture()
        publish()
    }

    fun onPushToTalk(state: Boolean) {
        pttHeld = state
    }

    fun onSettingsChanged(restartAudio: Boolean = false) {
        RtcVoice.setInputGain(PartyVoiceSettings.inputGain())
        if (active == VoiceTransport.P2p) P2PSessionManager.setVoiceRelay(PolyPlusConfig.voiceHideIp)
        if (!restartAudio || !inVoice) return
        if (!micBlocked) {
            stopCapture()
            startCapture()
        }
        val party = PartyRepository.party.value
        if (active == VoiceTransport.P2p && party != null) {
            PartyMesh.stop()
            PartyMesh.start(party.id)
        }
        if (active == VoiceTransport.Rtc) {
            PolyPlusClient.SCOPE.launch { RtcVoice.applyDevices(PartyVoiceSettings.inputDevice(), PartyVoiceSettings.outputDevice()) }
        }
    }

    fun onCaptureFailed() {
        if (!inVoice || micBlocked) return
        LOGGER.warn("The microphone gave nothing; party voice is listen-only until you retry")
        micBlocked = true
        talking = false
        RtcVoice.setTransmit(false)
        capture?.stop()
        capture = null
        publish()
    }

    fun onRtcJoined() {
        RtcVoice.setInputGain(PartyVoiceSettings.inputGain())
        PolyPlusClient.SCOPE.launch { RtcVoice.applyDevices(PartyVoiceSettings.inputDevice(), PartyVoiceSettings.outputDevice()) }
    }

    fun rtcCapable(): Boolean {
        if (rtcSessionFailed || P2PSessionManager.activeBridge?.rtcEnabled != true) return false
        if (System.currentTimeMillis() - rtcFailedAt <= RTC_RETRY_MS) return false
        if (!EosRtcSupport.isLinux) return true
        checkLinuxRtc()
        return linuxRtcReady
    }

    private fun checkLinuxRtc() {
        if (linuxRtcChecked) return
        linuxRtcChecked = true
        PolyPlusClient.SCOPE.launch {
            val counts = RtcVoice.deviceCounts()
            if (counts != null && counts.first > 0 && counts.second > 0) {
                LOGGER.info("EOS voice on Linux found {} input and {} output device(s)", counts.first, counts.second)
                linuxRtcReady = true
            } else {
                LOGGER.warn("EOS voice on Linux found no usable devices ({}); using peer-to-peer voice", counts)
                rtcSessionFailed = true
            }
        }
    }

    private fun markRtcFailed(sessionWide: Boolean) {
        rtcFailedAt = System.currentTimeMillis()
        if (sessionWide && EosRtcSupport.isLinux) rtcSessionFailed = true
    }

    fun capabilities(): List<VoiceTransport> = buildList {
        if (rtcCapable()) add(VoiceTransport.Rtc)
        if (P2PSessionManager.status.value == EosStatus.Ready) add(VoiceTransport.P2p)
    }

    fun memberVoice(): MemberVoice = MemberVoice(
        inVoice = inVoice,
        muted = muted,
        deafened = deafened,
        micBlocked = micBlocked,
        capabilities = capabilities(),
        transport = if (inVoice) active else null,
    )

    private fun startCapture() {
        if (capture != null) return
        silentSince = 0L
        micHeard = false
        micSilent = false
        val mic = PartyAudio.openCapture(PartyVoiceSettings.inputDevice(), PartyVoiceSettings::inputGain, ::onFrame)
        if (mic != null) {
            capture = mic
        } else {
            onCaptureFailed()
        }
    }

    private fun stopCapture() {
        capture?.stop()
        capture = null
    }

    private fun onFrame(frame: ShortArray, level: Float) {
        val now = System.currentTimeMillis()
        if (EosRtcSupport.isMac && !micHeard) {
            if (level != 0f) {
                silentSince = 0L
                micHeard = true
                setMicSilent(false)
            } else if (muted || PartyVoiceSettings.inputGain() <= 0f) {
                silentSince = 0L
            } else if (silentSince == 0L) {
                silentSince = now
            } else if (now - silentSince >= SILENCE_HINT_MS) {
                setMicSilent(true)
            }
        }
        if (level >= PartyVoiceSettings.activityThreshold()) lastLoud = now
        val gate = if (PartyVoiceSettings.pushToTalk()) pttHeld else now - lastLoud < VAD_HANGOVER_MS
        val allowed = inVoice && !muted && !deafened && !micBlocked && !selfHardMuted
        val talk = allowed && gate
        talking = talk
        when (active) {
            VoiceTransport.P2p -> PartyMesh.onCapture(frame, talk)
            VoiceTransport.Rtc -> RtcVoice.setTransmit(talk)
            else -> Unit
        }
    }

    private fun setMicSilent(silent: Boolean) {
        if (micSilent == silent) return
        micSilent = silent
        if (silent) LOGGER.warn("The microphone has sent only silence so far; macOS may be blocking it")
        publish()
    }

    private fun desiredTransport(party: Party): VoiceTransport =
        if (party.voice.transport == VoiceTransport.Rtc && rtcCapable()) VoiceTransport.Rtc else VoiceTransport.P2p

    private fun stopTransport() {
        RtcVoice.stop()
        PartyMesh.stop()
        P2PSessionManager.setVoiceRelay(false)
        active = null
    }

    private fun startTransport(transport: VoiceTransport, party: Party) {
        stopTransport()
        active = transport
        LOGGER.info("Party voice transport is now {}", transport.serializedName)
        when (transport) {
            VoiceTransport.Rtc -> RtcVoice.start(party.id) { outcome ->
                LOGGER.warn("EOS party voice ended; moving to peer-to-peer voice")
                markRtcFailed(outcome !is RtcVoice.Outcome.WrongTransport)
                PartyPresence.forceSend()
            }
            else -> {
                P2PSessionManager.setVoiceRelay(PolyPlusConfig.voiceHideIp)
                PartyMesh.start(party.id)
            }
        }
    }

    fun tick() {
        val party = PartyRepository.party.value
        if (party == null) {
            autoJoinedParty = null
            if (inVoice) leave()
            if (noticeOpen) dismissNotice()
            return
        }
        if (inVoice && voicePartyId != party.id) leave()
        if (autoJoinedParty != party.id) {
            autoJoinedParty = party.id
            if (PolyPlusConfig.voiceAutoJoin && PolyPlusConfig.voiceNoticeAccepted) join()
        }
        if (!inVoice) {
            publish()
            return
        }
        val desired = desiredTransport(party)
        if (desired != active) startTransport(desired, party)
        selfHardMuted = PartyRepository.self(party)?.voiceHardMuted == true
        mixPeers(party)
        publish()
    }

    private fun mixPeers(party: Party) {
        val myKey = PartyPresence.local.location.key
        val others = PartyRepository.others(party)
        val gains = PartyProximity.gains(party, myKey)
        val talkers = others.filter { it.online && it.state?.voice?.inVoice == true }
        val outputGain = PartyVoiceSettings.outputGain()
        val mix = HashMap<String, PeerMix>()
        for (member in others) {
            val puid = member.eosProductUserId ?: continue
            val proximity = gains[member.player]
            val gain = (proximity?.gain ?: 1f) * PartyVoiceSettings.volumeOf(member.player)
            val silenced = PartyVoiceSettings.isMuted(member.player) ||
                (active == VoiceTransport.P2p && member.voiceHardMuted)
            mix[puid] = PeerMix(gain, proximity?.pan ?: 0f, silenced)
        }
        val byPuid = others.mapNotNull { member -> member.eosProductUserId?.let { it to member.player } }.toMap()
        val speaking = when (active) {
            VoiceTransport.Rtc -> {
                RtcVoice.setDeafened(deafened)
                RtcVoice.apply(mix, outputGain)
                RtcVoice.speaking()
            }
            VoiceTransport.P2p -> {
                PartyMesh.mix = mix
                PartyMesh.deafened = deafened
                PartyMesh.outputGain = outputGain
                PartyMesh.sync(talkers.mapNotNull { member -> member.eosProductUserId?.let { it to true } }.toMap())
                PartyMesh.sendState(muted, deafened)
                PartyMesh.speaking()
            }
            else -> emptySet()
        }
        speakingPlayers = speaking.mapNotNullTo(HashSet()) { byPuid[it] }.toSet()
    }

    private fun blockedNotice(): String? {
        if (!micBlocked && !micSilent) return null
        if (!EosRtcSupport.isMac) return "No microphone found. You can still hear your party."
        val name = launcher ?: "your launcher"
        if (!micBlocked) {
            return "Your microphone hasn't picked up any sound yet. If you're talking, macOS may not be giving $name " +
                "your microphone. Allow it in System Settings > Privacy & Security > Microphone if $name is listed there, " +
                "or play through OneLauncher. This goes away as soon as you're heard."
        }
        return "macOS isn't giving $name your microphone. Allow it in System Settings > Privacy & Security > Microphone " +
            "if $name is listed there, or play through OneLauncher to talk. You can still hear your party."
    }

    private fun publish() {
        val self = PartyRepository.localId()
        val talkingNow = talking && inVoice
        val speaking = if (talkingNow) speakingPlayers + self else speakingPlayers
        val next = Ui(
            inVoice = inVoice,
            transport = if (inVoice) active else null,
            muted = muted,
            deafened = deafened,
            micBlocked = micBlocked,
            micSilent = micSilent && !micBlocked,
            blockedNotice = blockedNotice(),
            talking = talkingNow,
            speaking = speaking,
            noticeOpen = noticeOpen,
        )
        if (next != _ui.value) _ui.value = next
    }
}
