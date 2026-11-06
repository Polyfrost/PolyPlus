package org.polyfrost.polyplus.compat.iris

import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.lobby.shader.Capability
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.BooleanSupplier

interface IrisLobbyLifecycle {
    fun beforeBuild() = Unit

    fun built(pipeline: IrisRenderingPipeline) = Unit

    fun switched(lobby: Boolean) = Unit

    fun released(pipeline: IrisRenderingPipeline) = Unit
}

object IrisHooks {
    private val LOGGER = LogManager.getLogger("PolyPlus/Lobby")
    private val seen: MutableSet<Capability> = ConcurrentHashMap.newKeySet()
    private val reported: MutableSet<Any> = ConcurrentHashMap.newKeySet()
    private val lifecycles = CopyOnWriteArrayList<IrisLobbyLifecycle>()

    @JvmStatic
    @Volatile
    var ringInPost: BooleanSupplier? = null

    @JvmStatic
    fun seen(capability: Capability) {
        seen.add(capability)
    }

    @JvmStatic
    fun hasSeen(capability: Capability): Boolean = capability in seen

    fun observed(): Set<Capability> = seen.toSet()

    @JvmStatic
    fun addLifecycle(lifecycle: IrisLobbyLifecycle) {
        lifecycles.addIfAbsent(lifecycle)
    }

    @JvmStatic
    fun removeLifecycle(lifecycle: IrisLobbyLifecycle) {
        lifecycles.remove(lifecycle)
    }

    internal fun ring(fallback: Boolean): Boolean = ask(ringInPost, fallback)

    internal fun beforeBuild() = each { it.beforeBuild() }

    internal fun built(pipeline: IrisRenderingPipeline) = each { it.built(pipeline) }

    internal fun switched(lobby: Boolean) = each { it.switched(lobby) }

    internal fun released(pipeline: IrisRenderingPipeline) = each { it.released(pipeline) }

    private fun ask(provider: BooleanSupplier?, fallback: Boolean): Boolean {
        if (provider == null) return fallback
        return runCatching { provider.asBoolean }.getOrElse {
            report(provider, it)
            fallback
        }
    }

    private inline fun each(action: (IrisLobbyLifecycle) -> Unit) {
        if (lifecycles.isEmpty()) return
        for (lifecycle in lifecycles) runCatching { action(lifecycle) }.onFailure { report(lifecycle, it) }
    }

    private fun report(owner: Any, error: Throwable) {
        if (reported.add(owner)) LOGGER.warn("Iris lobby hook {} failed", owner, error)
    }
}
