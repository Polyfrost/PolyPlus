package org.polyfrost.polyplus.client.nameplate.render

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
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import java.nio.FloatBuffer
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor

object LegacyNameTagDeferral {
    private const val MATRIX_FLOATS = 16
    private const val TAG_LIFT = 0.5
    private const val FULL_BRIGHT = 15728880
    private const val LIGHT_SPLIT = 65536
    private const val ALPHA_REFERENCE = 0.1f
    private const val MAX_STEPS = 256
    private const val SIGHT_TICKS = 10L
    private const val CELL_BITS = 21
    private const val CELL_MASK = (1L shl CELL_BITS) - 1
    private const val GL_BLEND_DST_RGB = 0x80C8
    private const val GL_BLEND_SRC_RGB = 0x80C9
    private const val GL_BLEND_DST_ALPHA = 0x80CA
    private const val GL_BLEND_SRC_ALPHA = 0x80CB

    private class Pending {
        val matrix: FloatBuffer = BufferUtils.createFloatBuffer(MATRIX_FLOATS)
        var renderer: LivingEntityRenderer<*>? = null
        var entity: LivingEntity? = null
        var dx = 0.0
        var dy = 0.0
        var dz = 0.0
        var distance = 0.0
    }

    private class Sight(val eyeCell: Long, val tagCell: Long, val window: Long, val blocked: Boolean)

    private val pending = ArrayList<Pending>()
    private val farFirst = Comparator<Pending> { a, b -> b.distance.compareTo(a.distance) }
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
        if (!collecting || replaying || entity !is ClientPlayerEntity || !NameTags.bindable(entity)) return false
        if (LegacyCeraShaders.managed()) return false
        if (size == pending.size) pending += Pending()
        val entry = pending[size]
        entry.matrix.clear()
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, entry.matrix)
        val tagY = dy + entity.height + TAG_LIFT
        if (translucentBetween(entity, entry.matrix, dx, tagY, dz)) return false
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
        if (size == 0) return
        val minecraft = Minecraft.getInstance()
        minecraft.profiler.push("polyplus_name_tags")
        val drawn = pending.subList(0, size)
        drawn.sortWith(farFirst)
        saved.capture()
        replaying = true
        try {
            minecraft.gameRenderer.enableLightMap()
            GlStateManager.enableCull()
            GlStateManager.enableDepthTest()
            GlStateManager.depthFunc(GL11.GL_LEQUAL)
            GlStateManager.enableAlphaTest()
            GlStateManager.alphaFunc(GL11.GL_GREATER, ALPHA_REFERENCE)
            for (entry in drawn) replay(entry, tickDelta)
        } catch (e: RuntimeException) {
            broken = true
            NameTagSupport.error("Deferred name tags failed to draw; name tags draw with the entities for the rest of this session", e)
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
        if (size == 0) return
        broken = true
        clear()
        NameTagSupport.error("Deferred name tags were never drawn; name tags draw with the entities for the rest of this session", IllegalStateException("translucent pass skipped"))
    }

    private fun replay(entry: Pending, tickDelta: Float) {
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
            NameTags.release()
        }
    }

    private fun clear() {
        for (i in 0 until size) {
            pending[i].renderer = null
            pending[i].entity = null
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
        val window = world.time / SIGHT_TICKS
        val known = sights[entity]
        if (known != null && known.eyeCell == eyeCell && known.tagCell == tagCell && known.window == window) return known.blocked
        val blocked = march(world, ex, ey, ez, px, py, pz)
        sights[entity] = Sight(eyeCell, tagCell, window, blocked)
        return blocked
    }

    private fun pack(x: Int, y: Int, z: Int): Long =
        (x.toLong() and CELL_MASK) or ((y.toLong() and CELL_MASK) shl CELL_BITS) or ((z.toLong() and CELL_MASK) shl (CELL_BITS * 2))

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
        val spanX = if (dx == 0.0) Double.MAX_VALUE else abs(1.0 / dx)
        val spanY = if (dy == 0.0) Double.MAX_VALUE else abs(1.0 / dy)
        val spanZ = if (dz == 0.0) Double.MAX_VALUE else abs(1.0 / dz)
        var nextX = if (dx == 0.0) Double.MAX_VALUE else (if (dx > 0) x + 1 - x0 else x0 - x) * spanX
        var nextY = if (dy == 0.0) Double.MAX_VALUE else (if (dy > 0) y + 1 - y0 else y0 - y) * spanY
        var nextZ = if (dz == 0.0) Double.MAX_VALUE else (if (dz > 0) z + 1 - z0 else z0 - z) * spanZ
        for (step in 0 until MAX_STEPS) {
            if (translucent(world, x, y, z)) return true
            if (minOf(nextX, nextY, nextZ) > 1.0) return false
            if (nextX <= nextY && nextX <= nextZ) {
                x += stepX
                nextX += spanX
            } else if (nextY <= nextZ) {
                y += stepY
                nextY += spanY
            } else {
                z += stepZ
                nextZ += spanZ
            }
        }
        return false
    }

    private fun translucent(world: World, x: Int, y: Int, z: Int): Boolean =
        world.getBlockState(cell.set(x, y, z)).block.renderLayer == BlockLayer.TRANSLUCENT

    private class SavedState {
        private val ints = BufferUtils.createIntBuffer(MATRIX_FLOATS)
        private val floats = BufferUtils.createFloatBuffer(MATRIX_FLOATS)
        private var lighting = false
        private var blend = false
        private var depthTest = false
        private var depthMask = false
        private var depthFunc = 0
        private var alphaTest = false
        private var alphaFunc = 0
        private var alphaReference = 0f
        private var cull = false
        private var texture = false
        private var boundTexture = 0
        private var lightMap = false
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
            alphaReference = floats.get(0)
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
            if (!lightMap) Minecraft.getInstance().gameRenderer.disableLightMap()
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
            GlStateManager.alphaFunc(alphaFunc, alphaReference)
            if (cull) GlStateManager.enableCull() else GlStateManager.disableCull()
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
