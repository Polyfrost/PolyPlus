package org.polyfrost.polyplus.client.lobby

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig

enum class LobbyReveal(val id: String, private val label: String, private val shader: Float) {
    RANDOM("random", "Random", 0f),
    WAVE("wave", "Wave", 0f),
    FOCUS("focus", "Focus pull", 1f),
    LIGHT("light", "First light", 2f);

    val waves: Boolean get() = shader == 0f

    override fun toString(): String = label

    companion object {
        private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
        private var picked = roll(null)

        val active: LobbyReveal
            get() = LobbyScene.drivenReveal() ?: PolyPlusMainMenuConfig.lobbyReveal.let { if (it == RANDOM) picked else it }

        fun byId(id: String?): LobbyReveal? = entries.firstOrNull { it != RANDOM && it.id == id }

        fun next() {
            picked = if (StartupOverlay.active) WAVE else roll(picked)
            if (PolyPlusMainMenuConfig.lobbyReveal == RANDOM) LOGGER.info("Lobby reveal: {}", picked)
        }

        fun shaderMode(progress: Float): Float = if (progress < 1f) active.shader else 0f

        private fun roll(last: LobbyReveal?): LobbyReveal = entries.filter { it != RANDOM && it != last }.random()
    }
}
