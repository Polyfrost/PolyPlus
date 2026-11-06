package org.polyfrost.polyplus.compat.iris

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.lobby.shader.LobbyRevealGlsl
import java.util.concurrent.ConcurrentHashMap

object IrisLobbyReveal {
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private val programs = ConcurrentHashMap<String, Boolean>()
    private val reported = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    fun vertex(name: String?, vertex: String?, geometry: String?, tessControl: String?, tessEval: String?, fragment: String?): String? {
        if (!IrisLobbyBuild.isBuilding()) return vertex
        return plan(name, vertex, geometry, tessControl, tessEval, fragment)?.vertex ?: vertex
    }

    @JvmStatic
    fun fragment(name: String?, vertex: String?, geometry: String?, tessControl: String?, tessEval: String?, fragment: String?): String? {
        if (!IrisLobbyBuild.isBuilding()) return fragment
        return plan(name, vertex, geometry, tessControl, tessEval, fragment)?.fragment ?: fragment
    }

    @Volatile
    private var ready = false

    @JvmStatic
    fun ready(): Boolean = ready

    @JvmStatic
    fun reset() {
        programs.clear()
        ready = false
    }

    private fun refresh() {
        ready = programs.isNotEmpty() && programs.values.all { it }
    }

    private fun plan(
        name: String?,
        vertex: String?,
        geometry: String?,
        tessControl: String?,
        tessEval: String?,
        fragment: String?,
    ): LobbyRevealGlsl.Plan? {
        val program = name ?: "unnamed"
        val plan = runCatching { LobbyRevealGlsl.plan(vertex, geometry, tessControl, tessEval, fragment) }
            .onFailure { error ->
                programs[program] = false
                refresh()
                if (reported.add("$program/error")) LOGGER.warn("Iris lobby reveal failed for {}, TERRAIN_REVEAL unavailable", program, error)
            }
            .getOrNull() ?: return null
        programs[program] = plan.hit
        refresh()
        val miss = plan.miss
        if (miss != null && reported.add("$program/$miss")) {
            LOGGER.warn("Iris lobby reveal missed in {}: {}, TERRAIN_REVEAL unavailable", program, miss)
        }
        return plan
    }
}
