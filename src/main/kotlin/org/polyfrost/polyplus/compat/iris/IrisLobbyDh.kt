package org.polyfrost.polyplus.compat.iris

import net.fabricmc.loader.api.FabricLoader
import net.irisshaders.iris.Iris
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.api.lobby.LobbyApi
import java.lang.reflect.Field

object IrisLobbyDh {
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private const val DH_COMPAT = "net.irisshaders.iris.compat.dh.DHCompat"
    private const val PIPELINE = "net.irisshaders.iris.pipeline.IrisRenderingPipeline"
    private const val DH_INTERNAL = "net.irisshaders.iris.compat.dh.DHCompatInternal"

    private val dhEnabled: Field? by lazy {
        runCatching { Class.forName(DH_INTERNAL).getDeclaredField("dhEnabled").apply { isAccessible = true } }.getOrNull()
    }

    @Volatile
    private var lobbyPipeline: (Any) -> Boolean = { false }

    @Volatile
    private var building: () -> Boolean = { false }

    private var checked = false

    @JvmStatic
    fun bind(isLobbyPipeline: (Any) -> Boolean, isBuilding: () -> Boolean) {
        lobbyPipeline = isLobbyPipeline
        building = isBuilding
        check()
    }

    @JvmStatic
    fun isLobbyPipeline(pipeline: Any?): Boolean = pipeline != null && lobbyPipeline(pipeline)

    @JvmStatic
    fun isBuilding(): Boolean = building()

    @JvmStatic
    fun engaged(): Boolean = building() || (LobbyApi.isRendering() && isLobbyPipeline(Iris.getPipelineManager().pipelineNullable))

    @JvmStatic
    fun frameCheck(): Boolean? {
        if (!building() && !LobbyApi.isRendering()) return null
        if (engaged()) return false
        return dhEnabled?.let { field -> runCatching { field.getBoolean(null) }.getOrNull() }
    }

    @Synchronized
    private fun check() {
        if (checked || !FabricLoader.getInstance().isModLoaded("distanthorizons")) return
        checked = true
        val missing = runCatching {
            val compat = Class.forName(DH_COMPAT)
            buildList {
                if (runCatching { compat.getDeclaredConstructor(Class.forName(PIPELINE), Boolean::class.javaPrimitiveType) }.isFailure) add("DHCompat(IrisRenderingPipeline, boolean)")
                if (runCatching { compat.getDeclaredField("dhPresent") }.isFailure) add("dhPresent")
                if (runCatching { compat.getDeclaredMethod("checkFrame") }.isFailure) add("checkFrame")
                if (runCatching { compat.getDeclaredMethod("hasRenderingEnabled") }.isFailure) add("hasRenderingEnabled")
            }
        }.getOrElse { listOf("DHCompat") }
        if (missing.isNotEmpty()) {
            LOGGER.warn("Iris Distant Horizons lobby hooks missing ({}), the lobby pipeline may build DH programs", missing.joinToString())
        }
    }
}
