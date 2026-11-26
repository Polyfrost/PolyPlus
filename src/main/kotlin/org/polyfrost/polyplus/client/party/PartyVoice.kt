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
import org.polyfrost.polyplus.client.party.soundboard.Soundboard
import org.polyfrost.polyplus.client.party.soundboard.SoundboardStream
import org.polyfrost.polyplus.client.social.SocialOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object PartyVoice {
    private val LOGGER = LogManager.getLogger()

    private const val RTC_RETRY_MS = 5 * 60_000L
    private const val VAD_HANGOVER_MS = 400L
    private const val SILENCE_HINT_MS = 10_000L
    private const val LEVEL_EVERY_FRAMES = 3
    private const val SPEECH_PROBABILITY = 0.5f
    private const val AUTO_FLOOR = 0.001f

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
        val micTest: Boolean = false,
        val micTestFailed: Boolean = false,
        val soundsReady: Boolean = false,
    )

    private val _ui = MutableStateFlow(Ui())
    val ui = _ui.asStateFlow()

    private val _inputLevel = MutableStateFlow(0f)
    val inputLevel = _inputLevel.asStateFlow()

    @Volatile private var inVoice = false
    @Volatile private var muted = false
    @Volatile private var deafened = false
    @Volatile private var micBlocked = false
    @Volatile private var pttHeld = false
    @Volatile private var ptmHeld = false
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
    @Volatile private var micTest = false
    @Volatile private var micTestFailed = false
    @Volatile private var suppressor: NoiseSuppressor? = null
    private val suppressorLock = Any()
    @Volatile private var captureToken = 0
    @Volatile private var soundClockStarted = false
    private val silence = ShortArray(PartyAudio.FRAME_SAMPLES)
    private var levelPeak = 0f
    private var levelFrames = 0
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
        startSoundClock()
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
        if (!micTest) stopCapture()
        Soundboard.onVoiceStopped()
        SoundboardStream.reset()
        VoiceLinks.reset()
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

    fun setMicTest(enabled: Boolean) {
        if (micTest == enabled && !(enabled && micTestFailed)) return
        micTest = enabled
        micTestFailed = false
        LocalMonitor.setHearSelf(enabled)
        if (enabled) {
            if (inVoice && micBlocked) micBlocked = false
            startCapture()
        } else if (!inVoice) {
            stopCapture()
        }
        publish()
    }

    fun canSendSounds(): Boolean = inVoice && !deafened && !soundboardBlocked() && soundTransportReady()

    private fun soundTransportReady(): Boolean = when (active) {
        VoiceTransport.P2p -> PartyMesh.hasListeners()
        VoiceTransport.Rtc -> RtcVoice.dataReady
        else -> false
    }

    private fun startSoundClock() {
        if (soundClockStarted) return
        soundClockStarted = true
        val clock = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "polyplus-soundboard").apply { isDaemon = true } }
        clock.scheduleAtFixedRate({
            runCatching { if (inVoice && capture == null) SoundboardStream.pump(active, canSendSounds()) }
                .onFailure { LOGGER.warn("Soundboard clock failed", it) }
        }, PartyAudio.FRAME_MILLIS, PartyAudio.FRAME_MILLIS, TimeUnit.MILLISECONDS)
    }

    fun soundboardBlocked(): Boolean = PartyRepository.party.value?.let { PartyRepository.self(it)?.soundboardMuted } == true

    fun onPushToTalk(state: Boolean) {
        pttHeld = state
    }

    fun onPushToMute(state: Boolean) {
        ptmHeld = state
    }

    fun onSettingsChanged(restartAudio: Boolean = false) {
        if (EchoReference.enabled != PolyPlusConfig.voiceEchoCancellation) {
            EchoReference.setEnabled(PolyPlusConfig.voiceEchoCancellation)
            if (active == VoiceTransport.Rtc) stopTransport()
            PartyPresence.forceSend()
        }
        if (active == VoiceTransport.P2p) P2PSessionManager.setVoiceRelay(PolyPlusConfig.voiceHideIp)
        if (!restartAudio) return
        LocalMonitor.restart()
        if (!inVoice) {
            if (micTest) {
                stopCapture()
                startCapture()
            }
            return
        }
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
            PolyPlusClient.SCOPE.launch { RtcVoice.applyOutputDevice(PartyVoiceSettings.outputDevice()) }
        }
    }

    fun onCaptureFailed() {
        val testing = micTest
        if (testing) {
            LOGGER.warn("The microphone gave nothing during the mic test")
            micTest = false
            micTestFailed = true
            LocalMonitor.setHearSelf(false)
        }
        if (!inVoice) {
            if (!testing) return
            stopCapture()
            publish()
            return
        }
        if (micBlocked) {
            publish()
            return
        }
        LOGGER.warn("The microphone gave nothing; party voice is listen-only until you retry")
        micBlocked = true
        talking = false
        RtcVoice.setTransmit(false)
        stopCapture()
        publish()
    }

    fun onRtcJoined() {
        PolyPlusClient.SCOPE.launch { RtcVoice.applyOutputDevice(PartyVoiceSettings.outputDevice()) }
    }

    fun rtcCapable(): Boolean {
        if (PolyPlusConfig.voiceEchoCancellation && EchoReference.tapFailed) return false
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
        EchoReference.setEnabled(PolyPlusConfig.voiceEchoCancellation)
        silentSince = 0L
        micHeard = false
        micSilent = false
        val token = synchronized(suppressorLock) { ++captureToken }
        val mic = PartyAudio.openCapture(PartyVoiceSettings.inputDevice(), PartyVoiceSettings::inputGain) { frame, level ->
            synchronized(suppressorLock) { if (token == captureToken) onFrame(frame, level) }
        }
        if (mic != null) {
            capture = mic
        } else {
            onCaptureFailed()
        }
    }

    private fun stopCapture() {
        capture?.stop()
        capture = null
        synchronized(suppressorLock) {
            captureToken++
            suppressor?.close()
            suppressor = null
            levelPeak = 0f
            levelFrames = 0
            _inputLevel.value = 0f
        }
    }

    private fun denoise(frame: ShortArray): Float? {
        val clean = PolyPlusConfig.voiceNoiseSuppression
        val current = synchronized(suppressorLock) {
            if (!clean && !PolyPlusConfig.voiceAutoSensitivity) {
                suppressor?.close()
                suppressor = null
                return null
            }
            suppressor ?: NoiseSuppressor.open()?.also { suppressor = it }
        } ?: return null
        return if (clean) current.process(frame, PartyVoiceSettings.noiseStrength() / 100f) else current.analyze(frame)
    }

    private fun reportLevel(level: Float) {
        levelPeak = max(levelPeak, level)
        if (++levelFrames < LEVEL_EVERY_FRAMES) return
        _inputLevel.value = levelPeak
        levelPeak = 0f
        levelFrames = 0
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
        EchoReference.process(frame)
        val speech = denoise(frame)
        val voiceLevel = PartyAudio.rms(frame)
        reportLevel(voiceLevel)
        if (micTest) LocalMonitor.pushLoopback(frame)
        if (!inVoice) return
        val voiced = if (speech != null && PartyVoiceSettings.autoSensitivity()) {
            speech >= SPEECH_PROBABILITY && voiceLevel >= AUTO_FLOOR
        } else {
            voiceLevel >= PartyVoiceSettings.activityThreshold()
        }
        if (voiced) lastLoud = now
        val recent = now - lastLoud < VAD_HANGOVER_MS
        val gate = when (PartyVoiceSettings.inputMode()) {
            VoiceInputMode.PUSH_TO_TALK -> pttHeld
            VoiceInputMode.PUSH_TO_MUTE -> recent && !ptmHeld
            VoiceInputMode.VOICE_ACTIVITY -> recent
        }
        val allowed = !muted && !deafened && !micBlocked && !selfHardMuted
        val talk = allowed && gate
        talking = talk
        when (active) {
            VoiceTransport.P2p -> PartyMesh.onCapture(if (talk) frame else silence, talk)
            VoiceTransport.Rtc -> {
                RtcVoice.setTransmit(talk)
                if (talk) RtcVoice.send(frame)
            }
            else -> Unit
        }
        SoundboardStream.pump(active, canSendSounds())
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
        migrateSensitivity()
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

    private fun migrateSensitivity() {
        if (PolyPlusConfig.voiceSensitivityMigrated) return
        PolyPlusConfig.voiceSensitivityMigrated = true
        if (PolyPlusConfig.voiceNoticeAccepted) PolyPlusConfig.voiceAutoSensitivity = false
        PolyPlusConfig.save()
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
        val othersGain = PartyVoiceSettings.soundboardOthers() / 100f
        val soundMix = HashMap<String, PeerMix>()
        for (member in others) {
            val puid = member.eosProductUserId ?: continue
            val proximity = gains[member.player]
            val gain = (proximity?.gain ?: 1f) * PartyVoiceSettings.soundVolumeOf(member.player) * othersGain
            val silenced = member.soundboardMuted || PartyVoiceSettings.isSoundMuted(member.player)
            soundMix[puid] = PeerMix(gain, proximity?.pan ?: 0f, silenced)
        }
        SoundboardStream.mix = soundMix
        SoundboardStream.deafened = deafened
        SoundboardStream.outputGain = outputGain
        SoundboardStream.retain(byPuid.keys)
        val linked = when (active) {
            VoiceTransport.P2p -> PartyMesh.connected()
            VoiceTransport.Rtc -> RtcVoice.participants().filterTo(HashSet()) { it in byPuid }
            else -> emptySet()
        }
        VoiceLinks.tick(active, linked)
        if (active == VoiceTransport.Rtc) EchoReference.checkTap(linked.isNotEmpty())
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
            micTest = micTest,
            micTestFailed = micTestFailed,
            soundsReady = canSendSounds(),
        )
        if (next != _ui.value) _ui.value = next
    }
}
