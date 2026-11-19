package org.polyfrost.polyplus.client.gui.onboarding.live

//? if = 1.8.9 {
/*import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import net.minecraft.block.Blocks
import net.minecraft.block.SnowLayerBlock
import net.minecraft.block.state.BlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.render.block.BlockLayer
import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.platform.Lighting
import net.minecraft.client.render.texture.TextureAtlas
import net.minecraft.client.render.vertex.BufferBuilder
import net.minecraft.client.render.vertex.BufferUploader
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL20
import org.lwjgl.util.glu.Project
import org.polyfrost.polyplus.client.lobby.LobbySky
import org.polyfrost.polyplus.client.lobby.LobbyUtil
import org.slf4j.LoggerFactory
import java.util.EnumMap
import java.util.concurrent.CompletableFuture
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal enum class LiveSpot { STANDING, RIDING_PATH }

internal class LiveSpotPlace(val feet: Vec3d, val yaw: Float, val length: Double) {
    fun ahead(distance: Double): Vec3d {
        val rad = Math.toRadians(yaw.toDouble())
        return feet.add(-sin(rad) * distance, 0.0, cos(rad) * distance)
    }
}

internal class LiveCamera(val position: Vec3d, val yaw: Float, val pitch: Float, val fov: Float = DEFAULT_FOV) {
    companion object {
        const val DEFAULT_FOV = 70f
        private const val PLAYER_CENTER = 1.0

        fun lookingAt(position: Vec3d, target: Vec3d, fov: Float = DEFAULT_FOV): LiveCamera {
            val d = target.subtract(position)
            val yaw = Math.toDegrees(atan2(-d.x, d.z)).toFloat()
            val pitch = Math.toDegrees(-atan2(d.y, sqrt(d.x * d.x + d.z * d.z))).toFloat()
            return LiveCamera(position, yaw, pitch, fov)
        }

        fun orbit(
            place: LiveSpotPlace,
            angleDeg: Float,
            distance: Double,
            height: Double,
            targetHeight: Double = PLAYER_CENTER,
            fov: Float = DEFAULT_FOV,
        ): LiveCamera {
            val rad = Math.toRadians((place.yaw + angleDeg).toDouble())
            val target = place.feet.add(0.0, targetHeight, 0.0)
            val eye = place.feet.add(-sin(rad) * distance, height, cos(rad) * distance)
            return lookingAt(eye, target, fov)
        }

        fun ride(place: LiveSpotPlace, forward: Double, eyeHeight: Double, pitch: Float = 0f, fov: Float = DEFAULT_FOV): LiveCamera =
            LiveCamera(place.ahead(forward).add(0.0, eyeHeight, 0.0), place.yaw, pitch, fov)
    }
}

internal class LiveSceneFrame(val camera: LiveCamera, private val terrain: LiveLegacyTerrain.Built) {
    fun relative(pos: Vec3d): Vec3d = pos.subtract(camera.position)

    fun light(pos: Vec3d): Int = terrain.light(BlockPos(pos))
}

internal class RenderBlocksRenderer(private val label: String) : AutoCloseable {
    override fun close() = Unit

    override fun toString(): String = label
}

internal fun liveRetire(task: () -> Unit) = task()

private object LiveLegacyState {
    fun reset(depth: Boolean) {
        GL20.glUseProgram(0)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.activeTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableBlend()
        GlStateManager.disableBlend()
        GlStateManager.enableLighting()
        GlStateManager.disableLighting()
        GlStateManager.enableFog()
        GlStateManager.disableFog()
        GlStateManager.disableTexture()
        GlStateManager.enableTexture()
        GlStateManager.colorMask(false, false, false, false)
        GlStateManager.colorMask(true, true, true, true)
        if (depth) {
            GlStateManager.disableDepthTest()
            GlStateManager.enableDepthTest()
        } else {
            GlStateManager.enableDepthTest()
            GlStateManager.disableDepthTest()
        }
        GlStateManager.depthMask(!depth)
        GlStateManager.depthMask(depth)
        GlStateManager.depthFunc(GL11.GL_ALWAYS)
        GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.color4f(0f, 0f, 0f, 0f)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
    }
}

internal class LiveScene(val spot: LiveSpot) : AutoCloseable {
    private var terrain: LiveLegacyTerrain.Built? = null
    private var sceneTarget: RenderTarget? = null
    private val fogColor = BufferUtils.createFloatBuffer(FOG_COLOR_SIZE)

    val ready: Boolean get() = current() != null

    fun place(): LiveSpotPlace = current()?.place ?: LiveSpotPlace(Vec3d(0.0, 0.0, 0.0), 0f, 0.0)

    fun render(target: RenderTarget, aspect: Float, camera: LiveCamera, drawEntities: (LiveSceneFrame) -> Unit): Boolean {
        val built = current() ?: return false
        val mc = Minecraft.getInstance()
        val scene = sceneTarget(target) ?: return false
        val fog = built.level.getFogColor(1f)
        LiveLegacyState.reset(true)
        scene.setClearColor(fog.x.toFloat(), fog.y.toFloat(), fog.z.toFloat(), 1f)
        scene.clear()
        scene.bindWrite(true)
        LiveLegacyState.reset(true)
        GlStateManager.colorMask(true, true, true, false)
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        Project.gluPerspective(camera.fov, aspect, NEAR, FAR)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.rotatef(camera.pitch, 1f, 0f, 0f)
        GlStateManager.rotatef(camera.yaw + HALF_TURN, 0f, 1f, 0f)
        try {
            setupFog(fog)
            if (mc.world === built.level && mc.getCamera() != null) {
                GlStateManager.disableAlphaTest()
                mc.worldRenderer.renderSky(1f, SKY_PASS)
            }
            Lighting.turnOff()
            GlStateManager.enableDepthTest()
            GlStateManager.depthMask(true)
            setupFog(fog)
            GlStateManager.pushMatrix()
            GlStateManager.translated(
                built.origin.x - camera.position.x,
                built.origin.y - camera.position.y,
                built.origin.z - camera.position.z,
            )
            drawTerrain(mc, built)
            GlStateManager.popMatrix()
            resetTextures()
            drawEntities(LiveSceneFrame(camera, built))
            resetTextures()
            Lighting.turnOff()
            GlStateManager.disableFog()
        } finally {
            GlStateManager.colorMask(true, true, true, true)
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.popMatrix()
            GlStateManager.color4f(1f, 1f, 1f, 1f)
        }
        val graded = LiveLegacyGrade.apply(scene, target, built.fade(System.nanoTime()))
        mc.renderTarget.bindWrite(true)
        return graded
    }

    private fun resetTextures() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.activeTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture()
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
    }

    private fun setupFog(color: Vec3d) {
        fogColor.clear()
        fogColor.put(color.x.toFloat()).put(color.y.toFloat()).put(color.z.toFloat()).put(1f)
        fogColor.flip()
        GlStateManager.enableFog()
        GlStateManager.fogMode(GL11.GL_LINEAR)
        GlStateManager.fogStart(FOG_START)
        GlStateManager.fogEnd(FOG_END)
        GL11.glFog(GL11.GL_FOG_COLOR, fogColor)
    }

    private fun drawTerrain(mc: Minecraft, built: LiveLegacyTerrain.Built) {
        mc.gameRenderer.enableLightMap()
        resetTextures()
        mc.textureManager.bind(TextureAtlas.BLOCKS_LOCATION)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mc.textureManager.get(TextureAtlas.BLOCKS_LOCATION).glId)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)
        GlStateManager.disableAlphaTest()
        built.call(BlockLayer.SOLID)
        GlStateManager.enableAlphaTest()
        GlStateManager.alphaFunc(GL11.GL_GREATER, ALPHA_CUTOUT)
        built.call(BlockLayer.CUTOUT_MIPPED)
        built.call(BlockLayer.CUTOUT)
        GlStateManager.enableBlend()
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        GlStateManager.depthMask(false)
        built.call(BlockLayer.TRANSLUCENT)
        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.shadeModel(GL11.GL_FLAT)
        mc.gameRenderer.disableLightMap()
    }

    private fun sceneTarget(target: RenderTarget): RenderTarget? {
        val width = target.width
        val height = target.height
        if (width <= 0 || height <= 0) return null
        val current = sceneTarget
        if (current != null && current.width == width && current.height == height) return current
        current?.destroyBuffers()
        return RenderTarget(width, height, true).also { sceneTarget = it }
    }

    private fun current(): LiveLegacyTerrain.Built? {
        val wanted = LiveLegacyTerrain.current(spot) ?: return null
        val held = terrain
        if (held === wanted) return wanted
        held?.let(LiveLegacyTerrain::release)
        LiveLegacyTerrain.retain(wanted)
        terrain = wanted
        return wanted
    }

    override fun close() {
        terrain?.let(LiveLegacyTerrain::release)
        terrain = null
        sceneTarget?.destroyBuffers()
        sceneTarget = null
    }

    private companion object {
        const val HALF_TURN = 180f
        const val NEAR = 0.05f
        const val FAR = 512f
        const val FOG_START = 30f
        const val FOG_END = 46f
        const val FOG_COLOR_SIZE = 16
        const val SKY_PASS = 2
        const val ALPHA_CUTOUT = 0.1f
    }
}

internal object LiveLegacyTerrain {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")

    class Built(val level: ClientWorld, val origin: BlockPos, val place: LiveSpotPlace, private val lists: Map<BlockLayer, Int>) {
        var refs = 0
        var idleSince = System.nanoTime()
        private val readyAt = System.nanoTime()

        fun fade(now: Long): Float = ((now - readyAt) / FADE_NANOS.toFloat()).coerceIn(0f, 1f)

        fun light(pos: BlockPos): Int = level.getLightColor(pos, 0)

        fun call(layer: BlockLayer) {
            lists[layer]?.let(GlStateManager::callList)
        }

        fun close() {
            lists.values.forEach { GL11.glDeleteLists(it, 1) }
        }
    }

    private class Pending(val level: ClientWorld, val origin: BlockPos, val place: LiveSpotPlace, val buffers: Map<BlockLayer, BufferBuilder>)

    private class Job(val level: ClientWorld, val future: CompletableFuture<Pending?>)

    private val built = HashMap<LiveSpot, Built>()
    private val jobs = HashMap<LiveSpot, Job>()
    private val sweeper = LivePreviewFrame { sweep() }
    private var sweeping = false

    fun prepareAll() {
        LiveSpot.values().forEach(::current)
    }

    fun releaseAll() {
        jobs.clear()
        built.entries.removeIf { (_, terrain) ->
            val idle = terrain.refs <= 0
            if (idle) terrain.close()
            idle
        }
    }

    fun current(spot: LiveSpot): Built? {
        startSweeping()
        val level = LiveSceneWorld.level() ?: return null
        val existing = built[spot]
        if (existing != null && existing.level === level) return existing
        val job = jobs[spot]?.takeIf { it.level === level } ?: start(spot, level)
        if (!job.future.isDone) return existing
        jobs.remove(spot)
        val pending = runCatching { job.future.join() }.getOrNull() ?: return existing
        val next = Built(pending.level, pending.origin, pending.place, upload(pending.buffers))
        existing?.let { if (it.refs <= 0) it.close() }
        built[spot] = next
        return next
    }

    fun retain(terrain: Built) {
        terrain.refs++
    }

    fun release(terrain: Built) {
        terrain.refs--
        if (terrain.refs <= 0) terrain.idleSince = System.nanoTime()
    }

    private fun start(spot: LiveSpot, level: ClientWorld): Job {
        val future = CompletableFuture.supplyAsync({ build(spot, level) }, LobbyUtil.ioPool())
        return Job(level, future).also { jobs[spot] = it }
    }

    private fun build(spot: LiveSpot, level: ClientWorld): Pending? = runCatching {
        val center = LiveSceneWorld.center(level) ?: return null
        val found = LiveLegacySpots(level).find(spot, center) ?: return null
        val origin = BlockPos(found.place.feet)
        val dispatcher = Minecraft.getInstance().blockRenderDispatcher
        val buffers = EnumMap<BlockLayer, BufferBuilder>(BlockLayer::class.java)
        for (layer in BlockLayer.values()) {
            buffers[layer] = BufferBuilder(BUFFER_BYTES).apply {
                begin(GL11.GL_QUADS, DefaultVertexFormat.BLOCK)
                offset(-origin.x.toDouble(), -origin.y.toDouble(), -origin.z.toDouble())
            }
        }
        for (x in found.min.x..found.max.x) for (y in found.min.y..found.max.y) for (z in found.min.z..found.max.z) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            val block = state.block
            if (block.renderType == -1) continue
            val buffer = buffers[block.renderLayer] ?: continue
            dispatcher.render(state, pos, level, buffer)
        }
        buffers.values.forEach { it.end() }
        Pending(level, origin, found.place, buffers)
    }.onFailure {
        LOG.warn("[live-scene] could not build the lobby scene", it)
    }.getOrNull()

    private fun upload(buffers: Map<BlockLayer, BufferBuilder>): Map<BlockLayer, Int> {
        val uploader = BufferUploader()
        val lists = EnumMap<BlockLayer, Int>(BlockLayer::class.java)
        for ((layer, buffer) in buffers) {
            if (buffer.vertexCount == 0) continue
            val list = GL11.glGenLists(1)
            GL11.glNewList(list, GL11.GL_COMPILE)
            uploader.end(buffer)
            GL11.glEndList()
            lists[layer] = list
        }
        return lists
    }

    private fun startSweeping() {
        if (sweeping) return
        LivePreviewFrames.register(sweeper)
        sweeping = true
    }

    private fun sweep() {
        val now = System.nanoTime()
        val level = LiveSceneWorld.level()
        built.entries.removeIf { (_, terrain) ->
            val idle = terrain.refs <= 0 && (terrain.level !== level || now - terrain.idleSince > IDLE_NANOS)
            if (idle) terrain.close()
            idle
        }
        if (built.isEmpty() && jobs.isEmpty()) {
            LivePreviewFrames.unregister(sweeper)
            sweeping = false
        }
    }

    private const val BUFFER_BYTES = 2 * 1024 * 1024
    private const val IDLE_NANOS = 30_000_000_000L
    private const val FADE_NANOS = 300_000_000L
}

private class LiveLegacySpot(val place: LiveSpotPlace, val min: BlockPos, val max: BlockPos)

private class LiveLegacySpots(private val level: ClientWorld) {
    private val heights = HashMap<Long, Int>()
    private val tops = HashMap<Long, Int>()
    private var centerY = 0

    fun find(spot: LiveSpot, center: BlockPos): LiveLegacySpot? {
        centerY = center.y
        return when (spot) {
            LiveSpot.STANDING -> standing(center)
            LiveSpot.RIDING_PATH -> ridingPath(center)
        }
    }

    private fun standing(center: BlockPos): LiveLegacySpot? {
        var best: LiveLegacySpot? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (dx in -SEARCH..SEARCH step STEP) for (dz in -SEARCH..SEARCH step STEP) {
            val x = center.x + dx
            val z = center.z + dz
            val h = ground(x, z) ?: continue
            if (!flat(x, z, h)) continue
            if (!loaded(x - REGION, z - REGION, x + REGION, z + REGION)) continue
            var sumX = 0.0
            var sumZ = 0.0
            var scenery = 0
            for (rx in -SCENERY_FAR..SCENERY_FAR step 2) for (rz in -SCENERY_FAR..SCENERY_FAR step 2) {
                val d = sqrt((rx * rx + rz * rz).toDouble())
                if (d < SCENERY_NEAR || d > SCENERY_FAR) continue
                if (tall(x + rx, z + rz, h)) {
                    scenery++
                    sumX += rx
                    sumZ += rz
                }
            }
            val score = scenery - sqrt((dx * dx + dz * dz).toDouble()) * CENTER_WEIGHT
            if (score <= bestScore) continue
            bestScore = score
            val yaw = if (scenery == 0) 0f else Math.toDegrees(atan2(-sumX, sumZ)).toFloat()
            best = LiveLegacySpot(
                LiveSpotPlace(Vec3d(x + HALF, standY(x, h, z), z + HALF), yaw, 0.0),
                BlockPos(x - REGION, h - BELOW, z - REGION),
                BlockPos(x + REGION, h + ABOVE, z + REGION),
            )
        }
        return best
    }

    private fun ridingPath(center: BlockPos): LiveLegacySpot? {
        var best: LiveLegacySpot? = null
        var bestScore = Double.NEGATIVE_INFINITY
        for (dx in -SEARCH..SEARCH step STEP) for (dz in -SEARCH..SEARCH step STEP) {
            val sx = center.x + dx
            val sz = center.z + dz
            for ((stepX, stepZ) in DIRECTIONS) {
                val score = pathScore(sx, sz, stepX, stepZ) ?: continue
                if (score <= bestScore) continue
                val ex = sx + stepX * PATH_LENGTH
                val ez = sz + stepZ * PATH_LENGTH
                if (!loaded(minOf(sx, ex) - REGION, minOf(sz, ez) - REGION, maxOf(sx, ex) + REGION, maxOf(sz, ez) + REGION)) continue
                val h = ground(sx, sz) ?: continue
                bestScore = score
                val yaw = Math.toDegrees(atan2(-stepX.toDouble(), stepZ.toDouble())).toFloat()
                best = LiveLegacySpot(
                    LiveSpotPlace(Vec3d(sx + HALF, standY(sx, h, sz), sz + HALF), yaw, PATH_LENGTH.toDouble()),
                    BlockPos(minOf(sx, ex) - REGION, h - BELOW - PATH_DROP, minOf(sz, ez) - REGION),
                    BlockPos(maxOf(sx, ex) + REGION, h + ABOVE + PATH_DROP, maxOf(sz, ez) + REGION),
                )
            }
        }
        return best
    }

    private fun pathScore(sx: Int, sz: Int, stepX: Int, stepZ: Int): Double? {
        var previous = ground(sx, sz) ?: return null
        val start = previous
        var bumps = 0
        var paved = 0
        for (k in 1..PATH_LENGTH) {
            val x = sx + stepX * k
            val z = sz + stepZ * k
            val h = ground(x, z) ?: return null
            if (abs(h - previous) > 1 || abs(h - start) > PATH_DROP) return null
            for (side in -1..1 step 2) {
                val sh = ground(x + stepZ * side, z + stepX * side) ?: return null
                if (abs(sh - h) > 1) return null
            }
            if (h != previous) bumps++
            if (level.getBlockState(BlockPos(x, h, z)).block === Blocks.GRAVEL) paved++
            previous = h
        }
        val ex = sx + stepX * PATH_LENGTH
        val ez = sz + stepZ * PATH_LENGTH
        var scenery = 0
        for (rx in -SCENERY_NEAR * 2..SCENERY_NEAR * 2 step 2) for (rz in -SCENERY_NEAR * 2..SCENERY_NEAR * 2 step 2) {
            if (tall(ex + rx, ez + rz, previous)) scenery++
        }
        return scenery + paved * PAVED_WEIGHT - bumps * BUMP_WEIGHT
    }

    private fun flat(x: Int, z: Int, h: Int): Boolean {
        for (rx in -FLAT_RADIUS..FLAT_RADIUS) for (rz in -FLAT_RADIUS..FLAT_RADIUS) {
            if (ground(x + rx, z + rz) != h) return false
        }
        return true
    }

    private fun tall(x: Int, z: Int, base: Int): Boolean {
        ground(x, z)
        val top = tops[key(x, z)] ?: return false
        return top - base in TALL_MIN..TALL_MAX
    }

    private fun standY(x: Int, h: Int, z: Int): Double {
        val above = level.getBlockState(BlockPos(x, h + 1, z))
        val layers = if (above.block === Blocks.SNOW_LAYER) above.get(SnowLayerBlock.LAYERS) - 1 else 0
        return h + 1.0 + layers * SNOW_LAYER
    }

    private fun loaded(minX: Int, minZ: Int, maxX: Int, maxZ: Int): Boolean {
        for (cx in (minX shr SECTION_BITS)..(maxX shr SECTION_BITS)) {
            for (cz in (minZ shr SECTION_BITS)..(maxZ shr SECTION_BITS)) {
                if (!level.chunkSource.hasChunk(cx, cz)) return false
            }
        }
        return true
    }

    private fun ground(x: Int, z: Int): Int? {
        val key = key(x, z)
        heights[key]?.let { return if (it == NO_GROUND) null else it }
        val found = scanGround(x, z)
        heights[key] = found ?: NO_GROUND
        return found
    }

    private fun scanGround(x: Int, z: Int): Int? {
        if (!level.chunkSource.hasChunk(x shr SECTION_BITS, z shr SECTION_BITS)) return null
        for (y in centerY + SCAN_UP downTo centerY - SCAN_DOWN) {
            val pos = BlockPos(x, y, z)
            val state = level.getBlockState(pos)
            val block = state.block
            if (block === Blocks.AIR) continue
            tops.putIfAbsent(key(x, z), y)
            if (block.material.isLiquid) return null
            if (block === Blocks.SNOW_LAYER || !block.material.blocksMovement()) continue
            return if (natural(state)) y else null
        }
        return null
    }

    private fun natural(state: BlockState): Boolean {
        val block = state.block
        return block === Blocks.GRASS || block === Blocks.DIRT || block === Blocks.SAND || block === Blocks.STONE ||
            block === Blocks.GRAVEL || block === Blocks.SNOW || block === Blocks.PACKED_ICE || block === Blocks.COBBLESTONE ||
            block === Blocks.SANDSTONE || block === Blocks.MYCELIUM || block === Blocks.CLAY
    }

    private fun key(x: Int, z: Int): Long = (x.toLong() shl Int.SIZE_BITS) or (z.toLong() and LOW_BITS)

    private companion object {
        val DIRECTIONS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        const val SEARCH = 40
        const val STEP = 2
        const val FLAT_RADIUS = 2
        const val REGION = 48
        const val BELOW = 14
        const val ABOVE = 26
        const val SCENERY_NEAR = 4
        const val SCENERY_FAR = 14
        const val TALL_MIN = 2
        const val TALL_MAX = 10
        const val CENTER_WEIGHT = 0.3
        const val PATH_LENGTH = 30
        const val PATH_DROP = 3
        const val PAVED_WEIGHT = 2.0
        const val BUMP_WEIGHT = 3.0
        const val SCAN_UP = 40
        const val SCAN_DOWN = 30
        const val NO_GROUND = Int.MIN_VALUE
        const val HALF = 0.5
        const val SNOW_LAYER = 0.125
        const val SECTION_BITS = 4
        const val LOW_BITS = 0xFFFFFFFFL
    }
}

private object LiveLegacyGrade {
    private val LOG = LoggerFactory.getLogger("polyplus/live-scene")
    private var program = 0
    private var failed = false

    fun apply(source: RenderTarget, destination: RenderTarget, fade: Float): Boolean {
        val shader = shader()
        destination.bindWrite(true)
        LiveLegacyState.reset(false)
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.disableDepthTest()
        GlStateManager.depthMask(false)
        GlStateManager.disableBlend()
        GlStateManager.disableAlphaTest()
        GlStateManager.disableFog()
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GlStateManager.activeTexture(GL13.GL_TEXTURE0)
        GlStateManager.enableTexture()
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        GlStateManager.bindTexture(source.colorTextureId)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, source.colorTextureId)
        if (shader != 0) {
            val mood = LobbySky.mood
            GL20.glUseProgram(shader)
            GL20.glUniform1i(GL20.glGetUniformLocation(shader, "InSampler"), 0)
            GL20.glUniform4f(GL20.glGetUniformLocation(shader, "ColorModulator"), mood.night, mood.exposure, mood.golden, fade)
        }
        try {
            val tesselator = Tesselator.getInstance()
            val buffer = tesselator.buffer
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX)
            buffer.vertex(-1.0, -1.0, 0.0).texture(0.0, 0.0).nextVertex()
            buffer.vertex(1.0, -1.0, 0.0).texture(1.0, 0.0).nextVertex()
            buffer.vertex(1.0, 1.0, 0.0).texture(1.0, 1.0).nextVertex()
            buffer.vertex(-1.0, 1.0, 0.0).texture(0.0, 1.0).nextVertex()
            tesselator.end()
        } finally {
            if (shader != 0) GL20.glUseProgram(0)
            GlStateManager.depthMask(true)
            GlStateManager.enableDepthTest()
            GlStateManager.enableAlphaTest()
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.popMatrix()
        }
        return true
    }

    private fun shader(): Int {
        if (failed || program != 0) return program
        program = runCatching { build() }.onFailure {
            failed = true
            LOG.warn("[live-scene] grade shader unavailable, showing the ungraded scene", it)
        }.getOrDefault(0)
        return program
    }

    private fun build(): Int {
        val id = GL20.glCreateProgram()
        GL20.glAttachShader(id, compile(GL20.GL_VERTEX_SHADER, "live_scene_grade.vsh"))
        GL20.glAttachShader(id, compile(GL20.GL_FRAGMENT_SHADER, "live_scene_grade.fsh"))
        GL20.glLinkProgram(id)
        check(GL20.glGetProgrami(id, GL20.GL_LINK_STATUS) == GL11.GL_TRUE) { GL20.glGetProgramInfoLog(id, LOG_LENGTH) }
        return id
    }

    private fun compile(type: Int, name: String): Int {
        val source = checkNotNull(LiveLegacyGrade::class.java.getResourceAsStream("/assets/polyplus/shaders/legacy/$name")).use { it.readBytes().decodeToString() }
        val id = GL20.glCreateShader(type)
        GL20.glShaderSource(id, source)
        GL20.glCompileShader(id)
        check(GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE) { GL20.glGetShaderInfoLog(id, LOG_LENGTH) }
        return id
    }

    private const val LOG_LENGTH = 4096
}

@Composable
internal fun LiveSceneCanvas(
    spot: LiveSpot,
    modifier: Modifier,
    camera: (place: LiveSpotPlace, time: Float) -> LiveCamera,
    drawEntities: (frame: LiveSceneFrame, time: Float) -> Unit,
) {
    val content = remember(spot) { LiveSceneContent(LiveScene(spot)) }
    val slot = remember(content) { RenderBlocksSlot("polyplus_live_scene", content) }
    SideEffect {
        content.camera = camera
        content.drawEntities = drawEntities
    }
    RenderBlocksCanvas(slot, modifier)
}

private class LiveSceneContent(private val scene: LiveScene) : RenderBlocksContent {
    var camera: (LiveSpotPlace, Float) -> LiveCamera = { place, _ -> LiveCamera.orbit(place, 0f, ORBIT_DISTANCE, ORBIT_HEIGHT) }
    var drawEntities: (LiveSceneFrame, Float) -> Unit = { _, _ -> }
    private val startNanos = System.nanoTime()

    override fun render(renderer: RenderBlocksRenderer, target: RenderTarget, width: Int, height: Int, aspect: Float, nowNanos: Long): Boolean {
        val time = ((nowNanos - startNanos) / NANOS_PER_SECOND).toFloat()
        if (!scene.ready) return false
        val view = camera(scene.place(), time)
        return scene.render(target, aspect, view) { drawEntities(it, time) }
    }

    override fun release(): List<AutoCloseable> = listOf(AutoCloseable { scene.close() })

    private companion object {
        const val ORBIT_DISTANCE = 3.5
        const val ORBIT_HEIGHT = 1.4
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

internal fun prepareLiveScenes() = LiveLegacyTerrain.prepareAll()

internal fun releaseLiveScenes() = LiveLegacyTerrain.releaseAll()
*///?}
