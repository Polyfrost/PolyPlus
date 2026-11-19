package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 {
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.data.worldgen.biome.OverworldBiomes
//? if >= 1.21.4 {
import net.minecraft.util.ARGB
//?} else {
/*import org.polyfrost.polyplus.client.gui.onboarding.live.LiveArgb as ARGB
*///?}
//? if >= 26.1 {
import net.minecraft.util.LightCoordsUtil
//?} else {
/*import net.minecraft.client.renderer.LightTexture as LightCoordsUtil
*///?}
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import net.minecraft.world.level.LightLayer
//? if >= 26.1 {
import net.minecraft.client.renderer.block.BlockAndTintGetter
//?} else {
/*import net.minecraft.world.level.BlockAndTintGetter
*///?}
//? if >= 1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttribute
import net.minecraft.world.attribute.EnvironmentAttributes
//?} else {
/*import net.minecraft.util.Mth
import org.polyfrost.polyplus.client.lobby.LobbySky
*///?}
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import org.joml.Vector3fc
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

internal object LiveSceneTerrain {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")

    class Atmosphere(val sky: Int, val fog: Int, val lighting: RenderBlocksLighting)

    class Built(
        val key: Any,
        val mesh: RenderBlocksMesh,
        val origin: BlockPos,
        val place: LiveSpotPlace,
        private val level: BlockAndTintGetter,
        private val world: ClientLevel?,
    ) {
        val originVector: Vector3fc = Vector3f(origin.x.toFloat(), origin.y.toFloat(), origin.z.toFloat())
        var refs = 0
        var idleSince = System.nanoTime()
        val readyAt = System.nanoTime()
        val fromLobby: Boolean get() = world != null && level === world
        val skyLevel: ClientLevel? get() = world?.takeIf { fromLobby }

        fun fade(now: Long): Float = ((now - readyAt) / FADE_NANOS.toFloat()).coerceIn(0f, 1f)

        fun light(pos: BlockPos): Int = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos))

        fun atmosphere(pos: Vec3): Atmosphere {
            val brightness = runCatching { Minecraft.getInstance().options.gamma().get().toFloat() }.getOrDefault(DEFAULT_BRIGHTNESS).coerceAtLeast(0f)
            val lobby = skyLevel
            if (lobby != null) {
                val live = runCatching { lobbyAtmosphere(lobby, pos, brightness) }.getOrNull()
                if (live != null) return live
            }
            return Atmosphere(
                ARGB.opaque(OverworldBiomes.calculateSkyColor(OVERWORLD_TEMPERATURE)),
                ARGB.opaque(OVERWORLD_FOG),
                RenderBlocksLighting(brightness = brightness),
            )
        }
    }

    //? if >= 1.21.11 {
    private fun lobbyAtmosphere(lobby: ClientLevel, pos: Vec3, brightness: Float): Atmosphere {
        val attributes = lobby.environmentAttributes()
        return Atmosphere(
            opaque(adjusted(EnvironmentAttributes.SKY_COLOR, attributes.getValue(EnvironmentAttributes.SKY_COLOR, pos, null))),
            opaque(adjusted(EnvironmentAttributes.FOG_COLOR, attributes.getValue(EnvironmentAttributes.FOG_COLOR, pos, null))),
            RenderBlocksLighting(
                brightness = brightness,
                skyFactor = adjusted(EnvironmentAttributes.SKY_LIGHT_FACTOR, attributes.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, pos, null)),
                skyLightColor = vector(adjusted(EnvironmentAttributes.SKY_LIGHT_COLOR, attributes.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, pos, null))),
                //? if >= 26.1 {
                blockLightTint = vector(attributes.getValue(EnvironmentAttributes.BLOCK_LIGHT_TINT, pos, null)),
                ambientColor = vector(attributes.getValue(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, pos, null)),
                //?}
            ),
        )
    }
    //?} else {
    /*fun lobbyAtmosphere(lobby: ClientLevel, pos: Vec3, brightness: Float): Atmosphere {
        val block = BlockPos.containing(pos)
        //? if >= 1.21.4 {
        val sky = lobby.getSkyColor(pos, 1f)
        //?} else {
        /^val sky = lobby.getSkyColor(pos, 1f).let { ARGB.color(0, (it.x * CHANNEL).toInt(), (it.y * CHANNEL).toInt(), (it.z * CHANNEL).toInt()) }
        ^///?}
        val daylight = Mth.clamp(Mth.cos(lobby.getTimeOfDay(1f) * Mth.TWO_PI) * 2f + 0.5f, 0f, 1f)
        val base = Vec3.fromRGB24(lobby.getBiome(block).value().fogColor)
        val fog = lobby.effects().getBrightnessDependentFogColor(base, daylight)
        val darken = lobby.getSkyDarken(1f)
        val skyLight = Vector3f(darken, darken, 1f).lerp(Vector3f(1f, 1f, 1f), SKY_LIGHT_BLEND)
        return Atmosphere(
            ARGB.opaque(sky),
            ARGB.opaque(LobbySky.fogColor(ARGB.color(0, (fog.x * CHANNEL).toInt(), (fog.y * CHANNEL).toInt(), (fog.z * CHANNEL).toInt()))),
            RenderBlocksLighting(
                brightness = brightness,
                skyFactor = LobbySky.skyLightFactor(if (lobby.skyFlashTime > 0) 1f else darken * SKY_FACTOR_SCALE + SKY_FACTOR_FLOOR),
                skyLightColor = LobbySky.skyLightColor(skyLight),
            ),
        )
    }
    *///?}

    private class Job(val key: Any, val future: CompletableFuture<Pending?>)

    private class Pending(
        val mesh: RenderBlocksMesh,
        val prepared: RenderBlocksMesh.Prepared,
        val origin: BlockPos,
        val place: LiveSpotPlace,
        val level: BlockAndTintGetter,
    ) {
        fun discard() {
            prepared.close()
            mesh.close()
        }
    }

    private val built = HashMap<LiveSpot, Built>()
    private val jobs = HashMap<LiveSpot, Job>()
    private val retired = ArrayList<Built>()
    private val sweeper = LivePreviewFrame { sweep() }
    private var sweeping = false
    private var prepared = false

    fun prepareAll() {
        prepared = true
        LiveSpot.entries.forEach(::current)
    }

    fun releaseAll() {
        prepared = false
        jobs.values.forEach(::discard)
        jobs.clear()
        built.entries.removeIf { (_, terrain) ->
            if (terrain.refs <= 0) retired += terrain
            terrain.refs <= 0
        }
        sweep()
    }

    fun current(spot: LiveSpot): Built? {
        startSweeping()
        val key = currentKey()
        val existing = built[spot]
        if (existing != null && existing.key === key) return existing
        val job = jobs[spot]?.takeIf { it.key === key } ?: start(spot, key)
        if (!job.future.isDone) return existing
        return adopt(spot, job, key)
    }

    private fun currentKey(): Any = LiveSceneWorld.level() ?: FALLBACK_KEY

    private fun adopt(spot: LiveSpot, job: Job, key: Any): Built? {
        jobs.remove(spot)
        val pending = runCatching { job.future.join() }.getOrNull()
        if (pending == null) {
            jobs[spot] = Job(key, CompletableFuture.completedFuture(fallback(spot)))
            return null
        }
        pending.mesh.upload(pending.prepared)
        val next = Built(key, pending.mesh, pending.origin, pending.place, pending.level, key as? ClientLevel)
        built[spot]?.let(::retire)
        built[spot] = next
        return next
    }

    private fun discard(job: Job) {
        job.future.whenComplete { pending, _ -> pending?.discard() }
    }

    fun retain(terrain: Built) {
        terrain.refs++
    }

    fun release(terrain: Built) {
        terrain.refs--
        if (terrain.refs <= 0) terrain.idleSince = System.nanoTime()
    }

    private fun start(spot: LiveSpot, key: Any): Job {
        jobs.remove(spot)?.let(::discard)
        val world = key as? ClientLevel
        val future = CompletableFuture.supplyAsync({
            world?.let { lobby(spot, it) } ?: fallback(spot)
        }, Util.backgroundExecutor())
        return Job(key, future).also { jobs[spot] = it }
    }

    private fun lobby(spot: LiveSpot, level: ClientLevel): Pending? = runCatching {
        val center = LiveSceneWorld.center(level) ?: return null
        val found = LiveSceneSpots(level).find(spot, center) ?: return null
        val origin = BlockPos.containing(found.place.feet)
        val positions = ArrayList<BlockPos>()
        for (pos in BlockPos.betweenClosed(found.min, found.max)) {
            if (!level.getBlockState(pos).isAir) positions += pos.immutable()
        }
        val mesh = RenderBlocksMesh("polyplus_live_scene ${spot.name}")
        Pending(mesh, mesh.prepareRegion(level, positions, origin, fluids = true), origin, found.place, level)
    }.onFailure {
        LOG.warn("[live-scene] could not build the lobby scene, using the stand-in terrain", it)
    }.getOrNull()

    private fun fallback(spot: LiveSpot): Pending {
        val scene = LiveSceneFallback.scene()
        val place = LiveSceneFallback.place(spot)
        val origin = BlockPos.containing(place.feet)
        val mesh = RenderBlocksMesh("polyplus_live_scene ${spot.name} stand-in")
        return Pending(mesh, mesh.prepareRegion(scene, scene.blocks.keys, origin), origin, place, scene)
    }

    private fun retire(terrain: Built) {
        built.values.remove(terrain)
        retired += terrain
    }

    private fun startSweeping() {
        if (sweeping) return
        LivePreviewFrames.register(sweeper)
        sweeping = true
    }

    private fun sweep() {
        val now = System.nanoTime()
        val key = currentKey()
        jobs.entries.removeIf { (_, job) ->
            val stale = job.key !== key
            if (stale) discard(job)
            stale
        }
        jobs.entries.filter { it.value.future.isDone }.forEach { (spot, job) -> adopt(spot, job, key) }
        if (prepared) {
            LiveSpot.entries.filter { built[it]?.key !== key && jobs[it] == null }.forEach { start(it, key) }
        }
        built.entries.removeIf { (_, terrain) ->
            val expired = !prepared && now - terrain.idleSince > IDLE_NANOS
            val idle = terrain.refs <= 0 && (terrain.key !== key || expired)
            if (idle) retired += terrain
            idle
        }
        val done = retired.filter { it.refs <= 0 }
        if (done.isNotEmpty()) {
            retired.removeAll(done.toSet())
            liveRetire { done.forEach { it.mesh.close() } }
        }
        if (!prepared && built.isEmpty() && retired.isEmpty() && jobs.isEmpty()) {
            LivePreviewFrames.unregister(sweeper)
            sweeping = false
        }
    }

    //? if >= 1.21.11
    private fun <T : Any> adjusted(attribute: EnvironmentAttribute<T>, value: T): T = LiveSceneAtmosphere.adjusted(attribute, value)

    //? if >= 26.3 {
    private fun opaque(color: Vector3fc): Int = ARGB.colorFromFloat(1f, color.x(), color.y(), color.z())

    private fun vector(color: Vector3fc): Vector3fc = color
    //?} elif >= 1.21.11 {
    /*private fun opaque(color: Int): Int = ARGB.opaque(color)

    private fun vector(color: Int): Vector3fc = ARGB.vector3fFromRGB24(color)
    *///?} else {
    /*private const val CHANNEL = 255f
    private const val SKY_LIGHT_BLEND = 0.35f
    private const val SKY_FACTOR_SCALE = 0.95f
    private const val SKY_FACTOR_FLOOR = 0.05f
    *///?}

    private val FALLBACK_KEY = Any()
    private const val IDLE_NANOS = 30_000_000_000L
    private const val FADE_NANOS = 300_000_000L
    private const val DEFAULT_BRIGHTNESS = 0.5f
    private const val OVERWORLD_TEMPERATURE = 0.8f
    private const val OVERWORLD_FOG = -4138753
}
//?}
