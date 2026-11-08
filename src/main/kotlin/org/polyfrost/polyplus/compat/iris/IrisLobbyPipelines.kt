package org.polyfrost.polyplus.compat.iris

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.irisshaders.iris.Iris
import net.irisshaders.iris.gl.buffer.ShaderStorageBuffer
import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import net.irisshaders.iris.pipeline.PipelineManager
import net.irisshaders.iris.pipeline.WorldRenderingPipeline
import net.irisshaders.iris.shaderpack.DimensionId
import net.irisshaders.iris.shaderpack.ShaderPack
import net.irisshaders.iris.shaderpack.materialmap.NamespacedId
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import org.polyfrost.polyplus.client.lobby.LOBBY_LOGGER
import org.lwjgl.opengl.GL15C
import org.polyfrost.polyplus.api.lobby.LobbyApi
import org.polyfrost.polyplus.api.lobby.LobbyListener
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import org.polyfrost.polyplus.client.lobby.shader.Capability
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

object IrisLobbyPipelines {
    private val LOBBY = NamespacedId("polyplus", "lobby")
    private const val ATTEMPTS = 2
    private const val MAX_FAILURES = 3
    private const val NANOS_PER_MILLI = 1_000_000L

    @JvmField
    @Volatile
    var armed = true

    @JvmField
    @Volatile
    var building = false

    @Volatile
    private var patchAllowed = true

    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private var lobby: IrisRenderingPipeline? = null
    private var builtFor: ShaderPack? = null
    private var gaveUpFor: ShaderPack? = null
    private var patched = false
    private var home: WorldRenderingPipeline? = null
    private var routed = false
    private var swapped = false
    private var scene = false
    private var releasePending = false
    private var installed = false
    private var broken = false
    private var failures = 0

    private val destroyBuffer: MethodHandle? by lazy {
        runCatching {
            val method = ShaderStorageBuffer::class.java.getDeclaredMethod("destroy")
            method.isAccessible = true
            MethodHandles.lookup().unreflect(method)
        }.getOrNull()
    }

    init {
        IrisLobbyUniforms.buildFlag = IrisBuildFlag { building }
        IrisLobbyBuild.wire { patching() }
        IrisLobbyDh.bind(::isLobbyPipeline) { building }
    }

    private val listener = object : LobbyListener {
        override fun onSceneBuilt(level: ClientLevel) {
            scene = true
            releasePending = false
            armed = true
            prewarm()
        }

        override fun onSwapIn(level: ClientLevel) {
            swapped = true
            scene = true
            armed = true
        }

        override fun onSwapOut(level: ClientLevel) {
            swapped = false
        }

        override fun onSceneEnded() {
            scene = false
            releasePending = true
            armed = true
        }
    }

    @JvmStatic
    fun patching(): Boolean = building && patchAllowed

    @JvmStatic
    fun isLobbyPipeline(pipeline: Any?): Boolean = pipeline != null && pipeline === lobby

    fun engaged(): Boolean = routed && lobby != null

    fun lobbyPatched(): Boolean = patched && lobby != null

    fun gaveUp(): Boolean = gaveUpFor != null || broken

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        LobbyApi.addListener(listener)
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { if (releasePending) releaseNow() })
    }

    @JvmStatic
    fun route(manager: Any, dimension: NamespacedId): NamespacedId {
        if (broken) {
            armed = false
            return dimension
        }
        return runCatching { routeChecked(manager, dimension).also { failures = 0 } }.getOrElse {
            if (++failures >= MAX_FAILURES) {
                broken = true
                armed = false
            }
            report("route", it)
            dimension
        }
    }

    private fun routeChecked(manager: Any, dimension: NamespacedId): NamespacedId {
        install()
        IrisHooks.seen(Capability.LOBBY_PIPELINE)
        if (!IrisProbe.lobbyPipeline) {
            armed = false
            return dimension
        }
        val access = manager as PipelineManager
        if (!swapped) {
            val next = IrisAccess.pipelines(access)[dimension]
            val released = (releasePending || !scene) && release(access)
            if (routed) leave(access, next) else if (released) rebind(next)
            armed = scene || lobby != null
            return dimension
        }
        val pipeline = ensure(access)
        if (pipeline == null) {
            if (routed) leave(access, IrisAccess.pipelines(access)[dimension])
            return dimension
        }
        if (!routed) {
            home = IrisAccess.pipeline(access).takeIf { it !== pipeline }
            routed = true
            rebind(pipeline)
            IrisHooks.switched(true)
        }
        return LOBBY
    }

    fun prewarm() {
        val access = manager() ?: return
        runCatching {
            keepDimension(access)
            ensure(access)
        }.onFailure { report("prewarm", it) }
    }

    fun releaseNow() {
        val access = manager() ?: return
        runCatching { release(access) }.onFailure { report("release", it) }
    }

    private fun manager(): PipelineManager? {
        if (!IrisProbe.lobbyPipeline) return null
        return Iris.getPipelineManager()
    }

    private fun keepDimension(access: PipelineManager) {
        if (Iris.lastDimension != null) return
        Iris.lastDimension = IrisAccess.pipelines(access).keys.firstOrNull { it != LOBBY } ?: return
    }

    private fun ensure(access: PipelineManager): IrisRenderingPipeline? {
        val pack = Iris.getCurrentPack().orElse(null)
        val current = lobby
        if (current != null) {
            if (pack != null && builtFor === pack && IrisAccess.pipelines(access)[LOBBY] === current) return current
            release(access)
        }
        if (pack == null || gaveUpFor === pack) return null
        return build(access, pack)
    }

    private fun build(access: PipelineManager, pack: ShaderPack): IrisRenderingPipeline? {
        val active = IrisAccess.activeBuffers()
        val before: MutableSet<ShaderStorageBuffer> = Collections.newSetFromMap(IdentityHashMap())
        before.addAll(active)
        IrisHooks.beforeBuild()
        val started = System.nanoTime()
        var built: IrisRenderingPipeline? = null
        var attempt = 0
        while (built == null && attempt < ATTEMPTS) {
            val patch = attempt == 0
            attempt++
            IrisLobbyReveal.reset()
            building = true
            patchAllowed = patch
            val result = try {
                runCatching { IrisRenderingPipeline(pack.getProgramSet(DimensionId.OVERWORLD)) }
            } finally {
                building = false
                patchAllowed = true
            }
            result.onSuccess {
                built = it
                patched = patch
                if (IrisLobbyUniforms.available()) IrisHooks.seen(Capability.WAVE_UNIFORM)
            }.onFailure {
                discardBuffers(active, before)
                rebind(IrisAccess.pipeline(access))
                report(if (patch) "build" else "build unpatched", it)
            }
        }
        val pipeline = built
        if (pipeline == null) {
            gaveUpFor = pack
            LOBBY_LOGGER.warn("Iris lobby pipeline unavailable for this pack, the lobby uses the shared pipeline")
            return null
        }
        IrisAccess.pipelines(access)[LOBBY] = pipeline
        lobby = pipeline
        builtFor = pack
        gaveUpFor = null
        routed = false
        rebind(IrisAccess.pipeline(access))
        IrisHooks.built(pipeline)
        reloadChunksIfNeeded()
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        LOBBY_LOGGER.info("Iris lobby pipeline built in {} ms ({})", millis, if (patched) "patched" else "unpatched")
        return pipeline
    }

    private fun reloadChunksIfNeeded() {
        if (!WorldRenderingSettings.INSTANCE.isReloadRequired) return
        val mc = Minecraft.getInstance()
        if (mc.level == null || swapped) LobbyCompat.levelSink(mc).allChanged()
    }

    private fun leave(access: PipelineManager, next: WorldRenderingPipeline?) {
        routed = false
        rebind(next ?: home?.takeIf { alive(access, it) })
        IrisHooks.switched(false)
    }

    private fun release(access: PipelineManager): Boolean {
        releasePending = false
        val pipeline = lobby
        if (pipeline == null) {
            armed = scene || swapped
            return false
        }
        val map = IrisAccess.pipelines(access)
        val owned = map[LOBBY] === pipeline
        if (owned) map.remove(LOBBY)
        val restore = home?.takeIf { alive(access, it) }
        if (IrisAccess.pipeline(access) === pipeline) IrisAccess.setPipeline(access, restore)
        lobby = null
        builtFor = null
        patched = false
        home = null
        IrisDepthModel.reset()
        IrisHooks.released(pipeline)
        if (owned) runCatching { pipeline.destroy() }.onFailure { report("destroy", it) }
        runCatching { IrisAccess.evictOverrides(pipeline) }.onFailure { report("evict", it) }
        if (routed) {
            routed = false
            IrisHooks.switched(false)
        }
        rebind(IrisAccess.pipeline(access))
        armed = scene || swapped
        LOBBY_LOGGER.info("Iris lobby pipeline released{}", if (owned) "" else " (already destroyed by Iris)")
        return true
    }

    private fun alive(access: PipelineManager, pipeline: WorldRenderingPipeline): Boolean =
        IrisAccess.pipelines(access).values.any { it === pipeline }

    private fun rebind(pipeline: WorldRenderingPipeline?) {
        if (pipeline !is IrisRenderingPipeline) return
        val buffers = IrisAccess.storageBuffers(pipeline) ?: return
        runCatching { buffers.setupBuffers() }.onFailure { report("rebind", it) }
    }

    private fun discardBuffers(active: MutableList<ShaderStorageBuffer>, before: Set<ShaderStorageBuffer>) {
        val fresh = active.filter { it !in before }
        for (buffer in fresh) {
            active.remove(buffer)
            val destroy = destroyBuffer
            runCatching {
                if (destroy != null) destroy.invoke(buffer) else GL15C.glDeleteBuffers(buffer.id)
            }.onFailure { report("discard", it) }
        }
    }

    private fun report(what: String, error: Throwable) {
        if (reported.add(what)) LOBBY_LOGGER.warn("Iris lobby pipeline {} failed", what, error)
    }
}
