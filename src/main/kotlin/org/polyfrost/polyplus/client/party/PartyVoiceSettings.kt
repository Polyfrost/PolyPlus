package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.network.eos.EosNativeSupport

object PartyVoiceSettings {
    private const val DEFAULT_VOLUME = 1f
    private const val MAX_VOLUME = 2f

    var memberVolumes by mutableStateOf(parseVolumes(PolyPlusConfig.voiceMemberVolumes))
        private set

    var memberMutes by mutableStateOf(parseMutes(PolyPlusConfig.voiceMemberMutes))
        private set

    fun inputDevice(): String = PolyPlusConfig.voiceInputDevice

    fun outputDevice(): String = PolyPlusConfig.voiceOutputDevice

    fun inputGain(): Float = PolyPlusConfig.voiceInputVolume / 100f

    fun outputGain(): Float = PolyPlusConfig.voiceOutputVolume / 100f

    fun inputVolume(): Float = PolyPlusConfig.voiceInputVolume

    fun outputVolume(): Float = PolyPlusConfig.voiceOutputVolume

    fun setInputVolume(percent: Float) {
        PolyPlusConfig.voiceInputVolume = percent
        RtcVoice.setInputGain(inputGain())
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

    fun activityThreshold(): Float {
        val db = -70f + PolyPlusConfig.voiceActivityThreshold.coerceIn(0f, 100f) * 0.6f
        return Math.pow(10.0, db / 20.0).toFloat()
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
