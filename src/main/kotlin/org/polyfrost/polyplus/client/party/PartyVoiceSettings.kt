package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.eos.EosNativeSupport
import kotlin.math.log10

object PartyVoiceSettings {
    private const val DEFAULT_VOLUME = 1f
    private const val MAX_VOLUME = 2f
    private const val MIN_DB = -70f
    private const val DB_PER_STEP = 0.6f

    var memberVolumes by mutableStateOf(parseVolumes(PolyPlusConfig.voiceMemberVolumes))
        private set

    var memberMutes by mutableStateOf(parseMutes(PolyPlusConfig.voiceMemberMutes))
        private set

    var soundVolumes by mutableStateOf(parseVolumes(PolyPlusConfig.voiceSoundMemberVolumes))
        private set

    var soundMutes by mutableStateOf(parseMutes(PolyPlusConfig.voiceSoundMemberMutes))
        private set

    fun inputDevice(): String = PolyPlusConfig.voiceInputDevice

    fun outputDevice(): String = PolyPlusConfig.voiceOutputDevice

    fun inputGain(): Float = PolyPlusConfig.voiceInputVolume / 100f

    fun outputGain(): Float = PolyPlusConfig.voiceOutputVolume / 100f

    fun inputVolume(): Float = PolyPlusConfig.voiceInputVolume

    fun outputVolume(): Float = PolyPlusConfig.voiceOutputVolume

    fun setInputVolume(percent: Float) {
        PolyPlusConfig.voiceInputVolume = percent
    }

    fun setOutputVolume(percent: Float) {
        PolyPlusConfig.voiceOutputVolume = percent
    }

    fun saveVolumes() = PolyPlusConfig.save()

    fun inputMode(): VoiceInputMode = PolyPlusConfig.voiceInputMode

    fun setInputMode(mode: VoiceInputMode) {
        PolyPlusConfig.voiceInputMode = mode
        PolyPlusConfig.save()
    }

    fun proximityRange(): Float = PolyPlusConfig.voiceProximityRange.coerceIn(16f, 128f)

    fun pushToTalk(): Boolean = PolyPlusConfig.voiceInputMode == VoiceInputMode.PUSH_TO_TALK

    fun pushToMute(): Boolean = PolyPlusConfig.voiceInputMode == VoiceInputMode.PUSH_TO_MUTE

    fun autoSensitivity(): Boolean = PolyPlusConfig.voiceAutoSensitivity && NoiseSuppressor.available

    fun setAutoSensitivity(enabled: Boolean) {
        PolyPlusConfig.voiceAutoSensitivity = enabled
        PolyPlusConfig.save()
    }

    fun noiseStrength(): Float = PolyPlusConfig.voiceNoiseStrength.coerceIn(0f, 100f)

    fun setNoiseStrength(percent: Float) {
        PolyPlusConfig.voiceNoiseStrength = percent.coerceIn(0f, 100f)
    }

    fun echoCancellation(): Boolean = PolyPlusConfig.voiceEchoCancellation

    fun setEchoCancellation(enabled: Boolean) {
        PolyPlusConfig.voiceEchoCancellation = enabled
        PolyPlusConfig.save()
        PartyVoice.onSettingsChanged()
    }

    fun announceJoins(): Boolean = PolyPlusConfig.voiceAnnounceJoins

    fun setAnnounceJoins(enabled: Boolean) {
        PolyPlusConfig.voiceAnnounceJoins = enabled
        PolyPlusConfig.save()
    }

    fun soundboardOthers(): Float = PolyPlusConfig.voiceSoundboardOthers

    fun setSoundboardOthers(percent: Float) {
        PolyPlusConfig.voiceSoundboardOthers = percent
    }

    fun soundVolumeOf(player: String): Float = soundVolumes[player.lowercase()] ?: DEFAULT_VOLUME

    fun isSoundMuted(player: String): Boolean = player.lowercase() in soundMutes

    fun setSoundVolume(player: String, volume: Float) {
        val key = player.lowercase()
        val clamped = volume.coerceIn(0f, MAX_VOLUME)
        soundVolumes = if (clamped == DEFAULT_VOLUME) soundVolumes - key else soundVolumes + (key to clamped)
        PolyPlusConfig.voiceSoundMemberVolumes = soundVolumes.entries.joinToString(",") { "${it.key}=${it.value}" }
        PolyPlusConfig.save()
    }

    fun toggleSoundMute(player: String) {
        val key = player.lowercase()
        soundMutes = if (key in soundMutes) soundMutes - key else soundMutes + key
        PolyPlusConfig.voiceSoundMemberMutes = soundMutes.joinToString(",")
        PolyPlusConfig.save()
    }

    fun activityThreshold(): Float {
        val db = MIN_DB + PolyPlusConfig.voiceActivityThreshold.coerceIn(0f, 100f) * DB_PER_STEP
        return Math.pow(10.0, db / 20.0).toFloat()
    }

    fun sensitivity(): Float = PolyPlusConfig.voiceActivityThreshold

    fun setSensitivity(value: Float) {
        PolyPlusConfig.voiceActivityThreshold = value.coerceIn(0f, 100f)
    }

    fun levelPosition(level: Float): Float {
        if (level <= 0f) return 0f
        val db = 20f * log10(level)
        return ((db - MIN_DB) / (DB_PER_STEP * 100f)).coerceIn(0f, 1f)
    }

    fun noiseSuppression(): Boolean = PolyPlusConfig.voiceNoiseSuppression && NoiseSuppressor.available

    fun setNoiseSuppression(enabled: Boolean) {
        PolyPlusConfig.voiceNoiseSuppression = enabled
        PolyPlusConfig.save()
    }

    fun soundboardVolume(): Float = PolyPlusConfig.voiceSoundboardVolume

    fun setSoundboardVolume(percent: Float) {
        PolyPlusConfig.voiceSoundboardVolume = percent
    }

    fun soundboardMonitor(): Float = PolyPlusConfig.voiceSoundboardMonitor

    fun setSoundboardMonitor(percent: Float) {
        PolyPlusConfig.voiceSoundboardMonitor = percent
    }

    fun voiceAvailable(): Boolean = PolyPlusConfig.voiceEnabled && EosNativeSupport.unsupportedReason == null

    fun volumeOf(player: String): Float = memberVolumes[player.lowercase()] ?: DEFAULT_VOLUME

    fun isMuted(player: String): Boolean = player.lowercase() in memberMutes

    fun setVolume(player: String, volume: Float) {
        val key = player.lowercase()
        val clamped = volume.coerceIn(0f, MAX_VOLUME)
        memberVolumes = if (clamped == DEFAULT_VOLUME) memberVolumes - key else memberVolumes + (key to clamped)
        PolyPlusConfig.voiceMemberVolumes = memberVolumes.entries.joinToString(",") { "${it.key}=${it.value}" }
        PolyPlusConfig.save()
    }

    fun toggleMute(player: String) {
        val key = player.lowercase()
        memberMutes = if (key in memberMutes) memberMutes - key else memberMutes + key
        PolyPlusConfig.voiceMemberMutes = memberMutes.joinToString(",")
        PolyPlusConfig.save()
    }

    fun setInputDevice(name: String) {
        PolyPlusConfig.voiceInputDevice = name
        PolyPlusConfig.save()
        PartyVoice.onSettingsChanged(restartAudio = true)
    }

    fun setOutputDevice(name: String) {
        PolyPlusConfig.voiceOutputDevice = name
        PolyPlusConfig.save()
        PartyVoice.onSettingsChanged(restartAudio = true)
    }

    private fun parseVolumes(raw: String): Map<String, Float> = raw.split(',').mapNotNull { entry ->
        val key = entry.substringBefore('=', "").trim().lowercase()
        val value = entry.substringAfter('=', "").toFloatOrNull()
        if (key.isEmpty() || value == null) null else key to value.coerceIn(0f, MAX_VOLUME)
    }.toMap()

    private fun parseMutes(raw: String): Set<String> = raw.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
}
