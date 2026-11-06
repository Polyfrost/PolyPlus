package org.polyfrost.polyplus.client.lobby.shader

//? if iris {
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.polyplus.compat.iris.IrisProbe
//?}
import org.apache.logging.log4j.LogManager

enum class Capability { LOBBY_PIPELINE, SSBO_GUARD, TERRAIN_REVEAL, WAVE_UNIFORM, DEPTH_MODEL }

enum class DepthModel { FORWARD, REVERSE, UNKNOWN }

interface LobbyShaderBridge {
    val capabilities: Set<Capability>
    fun packActive(): Boolean
    fun engagedThisFrame(): Boolean
    fun prewarm()
    fun release()
    fun revealInTerrain(): Boolean
    fun ringInPost(): Boolean
    fun depthModel(): DepthModel
    fun packDepthOfField(): Boolean
}

object NoBridge : LobbyShaderBridge {
    override val capabilities: Set<Capability> = emptySet()
    override fun packActive(): Boolean = false
    override fun engagedThisFrame(): Boolean = false
    override fun prewarm() {}
    override fun release() {}
    override fun revealInTerrain(): Boolean = false
    override fun ringInPost(): Boolean = false
    override fun depthModel(): DepthModel = DepthModel.UNKNOWN
    override fun packDepthOfField(): Boolean = false
}

object LobbyShaderBridges {
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")

    @JvmStatic
    val current: LobbyShaderBridge by lazy {
        val probed = runCatching { probe() }
            .onFailure { LOGGER.warn("Shader bridge probe failed, lobby uses no shader bridge", it) }
            .getOrNull()
        probed ?: NoBridge
    }

    private fun probe(): LobbyShaderBridge? {
        //? if iris {
        if (FabricLoader.getInstance().isModLoaded("iris")) return IrisProbe.bridge()
        //?}
        return null
    }
}
