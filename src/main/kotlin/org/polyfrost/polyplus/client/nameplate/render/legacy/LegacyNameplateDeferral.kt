package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.ClientPlayerEntity
import net.minecraft.client.render.block.BlockLayer
import net.minecraft.client.render.entity.LivingEntityRenderer
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.entity.living.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import org.apache.logging.log4j.LogManager
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.nameplate.render.NameplateGate
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateIris
import java.nio.FloatBuffer
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor

object LegacyNameplateDeferral {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val MATRIX_SIZE = 16
    private const val TAG_LIFT = 0.5
    private const val FULL_BRIGHT = 15728880
    private const val LIGHT_SPLIT = 65536
    private const val SMOOTH = 7425
    private const val FLAT = 7424
    private const val GL_BLEND_DST_RGB = 0x80C8
    private const val GL_BLEND_SRC_RGB = 0x80C9
    private const val GL_BLEND_DST_ALPHA = 0x80CA
    private const val GL_BLEND_SRC_ALPHA = 0x80CB
    private const val MAX_STEPS = 256
    private const val CACHE_TICKS = 10L
    private const val CELL_BITS = 21
    private const val CELL_MASK = (1L shl CELL_BITS) - 1
    private const val Y_SHIFT = CELL_BITS
    private const val Z_SHIFT = CELL_BITS * 2

    private class Entry {
        val matrix: FloatBuffer = BufferUtils.createFloatBuffer(MATRIX_SIZE)
        var renderer: LivingEntityRenderer<*>? = null
        var entity: LivingEntity? = null
        var dx = 0.0
        var dy = 0.0
        var dz = 0.0
        var distance = 0.0
    }

    private class Sight(val eyeCell: Long, val tagCell: Long, val time: Long, val blocked: Boolean)

    private val entries = ArrayList<Entry>()
    private val byDistance = Comparator<Entry> { a, b -> b.distance.compareTo(a.distance) }
    private val sights = WeakHashMap<LivingEntity, Sight>()
    private val cell = BlockPos.Mutable()
    private val saved = SavedState()
    private var size = 0
    private var collecting = false
    private var replaying = false
    private var broken = false

    @JvmStatic
    fun beginEntities() {
        clear()
        collecting = !broken
    }

    @JvmStatic
    fun endEntities() {
        collecting = false
    }

    @JvmStatic
    fun defer(renderer: LivingEntityRenderer<*>, entity: LivingEntity, dx: Double, dy: Double, dz: Double): Boolean {
        if (!collecting || replaying || NameplateIris.shadowPass() || entity !is ClientPlayerEntity || !NameplateGate.active() || NameplateGate.bound(entity.uuid) == null) {
            return false
        }
        if (size == entries.size) {
            entries += Entry()
        }
        val entry = entries[size]
        entry.matrix.clear()
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, entry.matrix)
        val tagY = dy + entity.height + TAG_LIFT
        if (translucentBetween(entity, entry.matrix, dx, tagY, dz)) {
            return false
        }
        entry.renderer = renderer
        entry.entity = entity
        entry.dx = dx
        entry.dy = dy
        entry.dz = dz
        entry.distance = dx * dx + tagY * tagY + dz * dz
        size++
        return true
    }

    @JvmStatic
    fun flush(tickDelta: Float) {
        collecting = false
        if (size == 0) {
            return
        }
        val minecraft = Minecraft.getInstance()
        minecraft.profiler.push("polyplus_nameplates")
        val pending = entries.subList(0, size)
        pending.sortWith(byDistance)
        saved.capture()
        replaying = true
        try {
            minecraft.gameRenderer.enableLightMap()
            GlStateManager.shadeModel(FLAT)
            GlStateManager.enableCull()
            GlStateManager.enableDepthTest()
            GlStateManager.depthFunc(GL11.GL_LEQUAL)
            GlStateManager.enableAlphaTest()
            for (entry in pending) {
                replay(entry, tickDelta)
            }
        } catch (e: RuntimeException) {
            broken = true
            LOGGER.error("Deferred nameplate drawing failed; nameplates draw with the entities from now on", e)
        } finally {
            replaying = false
            clear()
            saved.restore()
            minecraft.profiler.pop()
        }
    }

    @JvmStatic
    fun endFrame() {
        collecting = false
        if (size > 0) {
            broken = true
            LOGGER.warn("Deferred nameplates were never drawn this frame; nameplates draw with the entities from now on")
            clear()
        }
    }

    private fun replay(entry: Entry, tickDelta: Float) {
        val entity = entry.entity ?: return
        @Suppress("UNCHECKED_CAST")
        val renderer = entry.renderer as? LivingEntityRenderer<LivingEntity> ?: return
        val light = if (entity.isOnFire) FULL_BRIGHT else entity.getLightLevel(tickDelta)
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, (light % LIGHT_SPLIT).toFloat(), (light / LIGHT_SPLIT).toFloat())
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        GlStateManager.pushMatrix()
        try {
            entry.matrix.rewind()
            GL11.glLoadMatrix(entry.matrix)
            renderer.renderNameTag(entity, entry.dx, entry.dy, entry.dz)
        } finally {
            GlStateManager.popMatrix()
            LegacyNameplateRenderer.reset()
        }
    }

    private fun clear() {
        for (i in 0 until size) {
            entries[i].renderer = null
            entries[i].entity = null
        }
        size = 0
    }

    private fun translucentBetween(entity: LivingEntity, matrix: FloatBuffer, tx: Double, ty: Double, tz: Double): Boolean {
        val minecraft = Minecraft.getInstance()
        val world = minecraft.world ?: return false
        val dispatcher = minecraft.entityRenderDispatcher
        val ox = dispatcher.cameraX
        val oy = dispatcher.cameraY
        val oz = dispatcher.cameraZ
        val m12 = matrix.get(12).toDouble()
        val m13 = matrix.get(13).toDouble()
        val m14 = matrix.get(14).toDouble()
        val ex = ox - (matrix.get(0) * m12 + matrix.get(1) * m13 + matrix.get(2) * m14)
        val ey = oy - (matrix.get(4) * m12 + matrix.get(5) * m13 + matrix.get(6) * m14)
        val ez = oz - (matrix.get(8) * m12 + matrix.get(9) * m13 + matrix.get(10) * m14)
        val px = ox + tx
        val py = oy + ty
        val pz = oz + tz
        val eyeCell = pack(floor(ex).toInt(), floor(ey).toInt(), floor(ez).toInt())
        val tagCell = pack(floor(px).toInt(), floor(py).toInt(), floor(pz).toInt())
        val time = world.time / CACHE_TICKS
        val cached = sights[entity]
        if (cached != null && cached.eyeCell == eyeCell && cached.tagCell == tagCell && cached.time == time) {
            return cached.blocked
        }
        val blocked = march(world, ex, ey, ez, px, py, pz)
        sights[entity] = Sight(eyeCell, tagCell, time, blocked)
        return blocked
    }

    private fun pack(x: Int, y: Int, z: Int): Long =
        (x.toLong() and CELL_MASK) or ((y.toLong() and CELL_MASK) shl Y_SHIFT) or ((z.toLong() and CELL_MASK) shl Z_SHIFT)

    private fun march(world: World, x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double): Boolean {
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        var x = floor(x0).toInt()
        var y = floor(y0).toInt()
        var z = floor(z0).toInt()
        val stepX = if (dx > 0) 1 else -1
        val stepY = if (dy > 0) 1 else -1
        val stepZ = if (dz > 0) 1 else -1
        val deltaX = if (dx == 0.0) Double.MAX_VALUE else abs(1.0 / dx)
        val deltaY = if (dy == 0.0) Double.MAX_VALUE else abs(1.0 / dy)
        val deltaZ = if (dz == 0.0) Double.MAX_VALUE else abs(1.0 / dz)
        var nextX = if (dx == 0.0) Double.MAX_VALUE else (if (dx > 0) x + 1 - x0 else x0 - x) * deltaX
        var nextY = if (dy == 0.0) Double.MAX_VALUE else (if (dy > 0) y + 1 - y0 else y0 - y) * deltaY
        var nextZ = if (dz == 0.0) Double.MAX_VALUE else (if (dz > 0) z + 1 - z0 else z0 - z) * deltaZ
        repeat(MAX_STEPS) {
            if (translucent(world, x, y, z)) {
                return true
            }
            if (minOf(nextX, nextY, nextZ) > 1.0) {
                return false
            }
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX
                nextX += deltaX
            } else if (nextY <= nextZ) {
                y += stepY
                nextY += deltaY
            } else {
                z += stepZ
                nextZ += deltaZ
            }
        }
        return false
    }

    private fun translucent(world: World, x: Int, y: Int, z: Int): Boolean =
        world.getBlockState(cell.set(x, y, z)).block.renderLayer == BlockLayer.TRANSLUCENT

    private class SavedState {
        private val ints = BufferUtils.createIntBuffer(MATRIX_SIZE)
        private val floats = BufferUtils.createFloatBuffer(MATRIX_SIZE)
        private var lighting = false
        private var blend = false
        private var depthTest = false
        private var depthMask = false
        private var depthFunc = 0
        private var alphaTest = false
        private var alphaFunc = 0
        private var alphaRef = 0f
        private var cull = false
        private var texture = false
        private var boundTexture = 0
        private var lightMap = false
        private var shadeModel = SMOOTH
        private var srcRgb = 0
        private var dstRgb = 0
        private var srcAlpha = 0
        private var dstAlpha = 0

        fun capture() {
            lighting = GL11.glIsEnabled(GL11.GL_LIGHTING)
            blend = GL11.glIsEnabled(GL11.GL_BLEND)
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
            alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
            depthFunc = integer(GL11.GL_DEPTH_FUNC)
            alphaFunc = integer(GL11.GL_ALPHA_TEST_FUNC)
            floats.clear()
            GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF, floats)
            alphaRef = floats.get(0)
            shadeModel = integer(GL11.GL_SHADE_MODEL)
            srcRgb = integer(GL_BLEND_SRC_RGB)
            dstRgb = integer(GL_BLEND_DST_RGB)
            srcAlpha = integer(GL_BLEND_SRC_ALPHA)
            dstAlpha = integer(GL_BLEND_DST_ALPHA)
            GlStateManager.activeTexture(GLX.GL_TEXTURE1)
            lightMap = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
            GlStateManager.activeTexture(GLX.GL_TEXTURE0)
            texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
            boundTexture = integer(GL11.GL_TEXTURE_BINDING_2D)
        }

        fun restore() {
            if (!lightMap) {
                Minecraft.getInstance().gameRenderer.disableLightMap()
            }
            GlStateManager.activeTexture(GLX.GL_TEXTURE0)
            if (texture) GlStateManager.enableTexture() else GlStateManager.disableTexture()
            GlStateManager.bindTexture(boundTexture)
            if (lighting) GlStateManager.enableLighting() else GlStateManager.disableLighting()
            if (blend) GlStateManager.enableBlend() else GlStateManager.disableBlend()
            GlStateManager.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            if (depthTest) GlStateManager.enableDepthTest() else GlStateManager.disableDepthTest()
            GlStateManager.depthMask(depthMask)
            GlStateManager.depthFunc(depthFunc)
            if (alphaTest) GlStateManager.enableAlphaTest() else GlStateManager.disableAlphaTest()
            GlStateManager.alphaFunc(alphaFunc, alphaRef)
            if (cull) GlStateManager.enableCull() else GlStateManager.disableCull()
            GlStateManager.shadeModel(shadeModel)
            GlStateManager.color4f(1f, 1f, 1f, 1f)
            GlStateManager.clearColor()
        }

        private fun integer(name: Int): Int {
            ints.clear()
            GL11.glGetInteger(name, ints)
            return ints.get(0)
        }
    }
}
*///?}
