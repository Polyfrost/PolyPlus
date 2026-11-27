package org.polyfrost.polyplus.client.party

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
//? if > 1.8.9 {
import com.mojang.text2speech.Narrator
//?}

internal object VoiceAnnouncer {
    private val LOGGER = LogManager.getLogger()

    private var known: Set<String>? = null

    //? if > 1.8.9 {
    const val SUPPORTED = true
    //?} else {
    /*const val SUPPORTED = false
    *///?}

    //? if > 1.8.9 {
    private val narrator: Narrator? by lazy {
        runCatching { Narrator.getNarrator() }.onFailure { LOGGER.warn("Text-to-speech isn't available for voice announcements", it) }.getOrNull()
    }
    //?}

    fun reset() {
        known = null
    }

    fun update(members: Map<String, String>) {
        val previous = known
        known = members.keys
        if (previous == null || !PolyPlusConfig.voiceAnnounceJoins) return
        (members.keys - previous).forEach { say("${members.getValue(it)} joined voice") }
        (previous - members.keys).forEach { player -> say("${PlayerNamesRepository.nameOr(player)} left voice") }
    }

    fun say(text: String) {
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            runCatching { speak(text) }.onFailure { LOGGER.warn("Couldn't read out a voice announcement", it) }
        }
    }

    private fun speak(text: String) {
        //? if > 1.8.9 {
        val target = narrator ?: return
        if (!target.active()) return
        val method = Narrator::class.java.methods.firstOrNull { it.name == "say" } ?: return
        if (method.parameterCount == 3) method.invoke(target, text, false, 1f) else method.invoke(target, text, false)
        //?}
    }
}
