package org.polyfrost.polyplus.compat.iris

import net.irisshaders.iris.Iris
import org.polyfrost.polyplus.client.lobby.shader.Capability
import org.polyfrost.polyplus.client.lobby.shader.DepthModel
import org.polyfrost.polyplus.client.lobby.shader.LobbyShaderBridge
import java.util.EnumSet

object IrisLobbyBridge : LobbyShaderBridge {
    override val capabilities: Set<Capability>
        get() {
            val found = EnumSet.noneOf(Capability::class.java)
            if (IrisProbe.bufferGuard) found += Capability.SSBO_GUARD
            if (IrisProbe.lobbyPipeline && IrisHooks.hasSeen(Capability.LOBBY_PIPELINE) && !IrisLobbyPipelines.gaveUp()) {
                found += Capability.LOBBY_PIPELINE
            }
            if (depthModel() != DepthModel.UNKNOWN) found += Capability.DEPTH_MODEL
            if (IrisLobbyPipelines.lobbyPatched() && IrisLobbyReveal.ready()) found += Capability.TERRAIN_REVEAL
            for (capability in IrisHooks.observed()) {
                if (capability != Capability.LOBBY_PIPELINE && capability != Capability.SSBO_GUARD) found += capability
            }
            return found
        }

    override fun packActive(): Boolean = runCatching { Iris.getCurrentPack().isPresent }.getOrDefault(false)

    override fun engagedThisFrame(): Boolean = IrisLobbyPipelines.engaged()

    override fun prewarm() = IrisLobbyPipelines.prewarm()

    override fun release() = IrisLobbyPipelines.releaseNow()

    override fun revealInTerrain(): Boolean =
        engagedThisFrame() &&
            IrisLobbyPipelines.lobbyPatched() &&
            IrisLobbyReveal.ready() &&
            IrisHooks.hasSeen(Capability.WAVE_UNIFORM)

    override fun ringInPost(): Boolean = IrisHooks.ring(packActive())

    override fun depthModel(): DepthModel = IrisDepthModel.depthModel()

    override fun packDepthOfField(): Boolean = IrisPackOptions.depthOfField()
}
