package org.polyfrost.polyplus.client.render

// !! this is to simplify the legacy branch

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.vertex.BufferBuilder
import net.minecraft.client.render.vertex.DefaultVertexFormat
import net.minecraft.client.render.vertex.Tesselator
import net.minecraft.client.render.vertex.VertexFormat
import net.minecraft.client.render.vertex.VertexFormatElement
import net.minecraft.resources.Identifier
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL14
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
*///?}

//? if > 1.8.9 {
typealias PoseStack = com.mojang.blaze3d.vertex.PoseStack

typealias Pose = com.mojang.blaze3d.vertex.PoseStack.Pose

typealias VertexConsumer = com.mojang.blaze3d.vertex.VertexConsumer

typealias NativeImage = com.mojang.blaze3d.platform.NativeImage
//?} else {
/*typealias NativeImage = org.polyfrost.oneconfig.internal.legacy.NativeImage

typealias Pose = PoseStack.Pose

class PoseStack {
    class Pose(private val pose: Matrix4f = Matrix4f(), private val normal: Matrix3f = Matrix3f()) {
        fun pose(): Matrix4f = pose

        fun normal(): Matrix3f = normal

        fun transformNormal(x: Float, y: Float, z: Float, dest: Vector3f): Vector3f =
            normal.transform(x, y, z, dest).normalize()

        fun set(other: Pose) {
            pose.set(other.pose)
            normal.set(other.normal)
        }

        fun copy(): Pose = Pose(Matrix4f(pose), Matrix3f(normal))
    }

    private val stack = ArrayDeque<Pose>().apply { addLast(Pose()) }

    fun last(): Pose = stack.last()

    fun pushPose() = stack.addLast(last().copy())

    fun popPose() {
        stack.removeLast()
    }

    fun translate(x: Float, y: Float, z: Float) {
        last().pose().translate(x, y, z)
    }

    fun scale(x: Float, y: Float, z: Float) {
        last().pose().scale(x, y, z)
        if (abs(x) == abs(y) && abs(y) == abs(z)) {
            if (x < 0f || y < 0f || z < 0f) last().normal().scale(sign(x), sign(y), sign(z))
        } else {
            last().normal().scale(1f / x, 1f / y, 1f / z)
        }
    }

    fun mulPose(rotation: Quaternionfc) {
        last().pose().rotate(rotation)
        last().normal().rotate(rotation)
    }
}

class VertexConsumer private constructor(private val buffer: BufferBuilder, private val sorted: Boolean) {
    fun addVertex(
        x: Float, y: Float, z: Float,
        color: Int,
        u: Float, v: Float,
        @Suppress("UNUSED_PARAMETER") overlayCoords: Int,
        lightCoords: Int,
        nx: Float, ny: Float, nz: Float,
    ) {
        if (sorted) {
            hold(x, y, z, color, u, v, lightCoords, nx, ny, nz)
        } else {
            put(buffer, x, y, z, color, u, v, lightCoords, nx, ny, nz)
        }
    }

    companion object {
        private const val HELD_FLOATS = 8
        private const val HELD_INTS = 2

        private var heldFloats = FloatArray(HELD_FLOATS * 64)
        private var heldInts = IntArray(HELD_INTS * 64)
        private var heldCount = 0
        private var order = LongArray(16)
        private val matrixFloats = BufferUtils.createFloatBuffer(16)
        private val modelView = Matrix4f()
        private val eye = Vector3f()

        private val FORMAT = VertexFormat()
            .addElement(DefaultVertexFormat.POSITION_ELEMENT)
            .addElement(DefaultVertexFormat.UV0_ELEMENT)
            .addElement(DefaultVertexFormat.COLOR_ELEMENT)
            .addElement(DefaultVertexFormat.UV1_ELEMENT)
            .addElement(VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.NORMAL, 3))

        private var cachedBuffer: BufferBuilder? = null
        private var plainConsumer: VertexConsumer? = null
        private var sortedConsumer: VertexConsumer? = null

        private fun consumerFor(buffer: BufferBuilder, sorted: Boolean): VertexConsumer {
            if (buffer !== cachedBuffer) {
                cachedBuffer = buffer
                plainConsumer = VertexConsumer(buffer, false)
                sortedConsumer = VertexConsumer(buffer, true)
            }
            return (if (sorted) sortedConsumer else plainConsumer)!!
        }

        private val stateInts = BufferUtils.createIntBuffer(16)
        private val stateFloats = BufferUtils.createFloatBuffer(16)

        fun draw(texture: Identifier, translucent: Boolean, additive: Boolean = false, emit: (VertexConsumer) -> Unit) {
            Minecraft.getInstance().textureManager.bind(texture)
            val blended = translucent || additive
            val sorted = translucent && !additive
            var alphaFunc = GL11.GL_ALWAYS
            var alphaReference = 0f
            var blending = false
            var srcRgb = GL11.GL_ONE
            var dstRgb = GL11.GL_ZERO
            var srcAlpha = GL11.GL_ONE
            var dstAlpha = GL11.GL_ZERO
            var depthWrite = true
            var culling = false
            var lighting = false
            if (blended) {
                blending = GL11.glIsEnabled(GL11.GL_BLEND)
                srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
                dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
                srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
                dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
                GlStateManager.enableBlend()
                if (additive) {
                    GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_SRC_ALPHA, GL11.GL_ONE)
                } else {
                    GlStateManager.blendFuncSeparate(770, 771, 1, 771)
                }
                stateInts.clear()
                GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC, stateInts)
                alphaFunc = stateInts.get(0)
                stateFloats.clear()
                GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF, stateFloats)
                alphaReference = stateFloats.get(0)
                GlStateManager.alphaFunc(GL11.GL_GREATER, 0f)
            }
            if (additive) {
                depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
                culling = GL11.glIsEnabled(GL11.GL_CULL_FACE)
                lighting = GL11.glIsEnabled(GL11.GL_LIGHTING)
                GlStateManager.depthMask(false)
                GlStateManager.disableCull()
                GlStateManager.disableLighting()
            }
            val tessellator = Tesselator.getInstance()
            val buffer = tessellator.buffer
            buffer.begin(GL11.GL_QUADS, FORMAT)
            try {
                if (sorted) {
                    heldCount = 0
                    emit(consumerFor(buffer, true))
                    putBackToFront(buffer)
                } else {
                    emit(consumerFor(buffer, false))
                }
            } finally {
                tessellator.end()
                if (additive) {
                    GlStateManager.depthMask(depthWrite)
                    if (culling) GlStateManager.enableCull()
                    if (lighting) GlStateManager.enableLighting()
                }
                if (blended) {
                    GlStateManager.alphaFunc(alphaFunc, alphaReference)
                    GlStateManager.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
                    if (!blending) GlStateManager.disableBlend()
                }
            }
        }

        private fun hold(x: Float, y: Float, z: Float, color: Int, u: Float, v: Float, light: Int, nx: Float, ny: Float, nz: Float) {
            if ((heldCount + 1) * HELD_FLOATS > heldFloats.size) {
                heldFloats = heldFloats.copyOf(heldFloats.size * 2)
                heldInts = heldInts.copyOf(heldInts.size * 2)
            }
            val f = heldCount * HELD_FLOATS
            heldFloats[f] = x
            heldFloats[f + 1] = y
            heldFloats[f + 2] = z
            heldFloats[f + 3] = u
            heldFloats[f + 4] = v
            heldFloats[f + 5] = nx
            heldFloats[f + 6] = ny
            heldFloats[f + 7] = nz
            heldInts[heldCount * HELD_INTS] = color
            heldInts[heldCount * HELD_INTS + 1] = light
            heldCount++
        }

        private fun putBackToFront(buffer: BufferBuilder) {
            val quads = heldCount / 4
            if (quads == 0) return
            matrixFloats.clear()
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, matrixFloats)
            val orthographic = matrixFloats.get(15) == 1f
            matrixFloats.clear()
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixFloats)
            modelView.set(matrixFloats)
            if (order.size < quads) order = LongArray(max(quads, order.size * 2))
            for (quad in 0 until quads) {
                var x = 0f
                var y = 0f
                var z = 0f
                for (vertex in 0 until 4) {
                    val f = (quad * 4 + vertex) * HELD_FLOATS
                    x += heldFloats[f]
                    y += heldFloats[f + 1]
                    z += heldFloats[f + 2]
                }
                modelView.transformPosition(x * 0.25f, y * 0.25f, z * 0.25f, eye)
                val depth = if (orthographic) eye.z else -eye.lengthSquared()
                order[quad] = DepthOrder.key(depth, quad)
            }
            order.sort(0, quads)
            for (i in 0 until quads) {
                val quad = DepthOrder.index(order[i])
                for (vertex in 0 until 4) {
                    val at = quad * 4 + vertex
                    val f = at * HELD_FLOATS
                    put(
                        buffer,
                        heldFloats[f], heldFloats[f + 1], heldFloats[f + 2],
                        heldInts[at * HELD_INTS],
                        heldFloats[f + 3], heldFloats[f + 4],
                        heldInts[at * HELD_INTS + 1],
                        heldFloats[f + 5], heldFloats[f + 6], heldFloats[f + 7],
                    )
                }
            }
            heldCount = 0
        }

        private fun put(
            buffer: BufferBuilder,
            x: Float, y: Float, z: Float,
            color: Int,
            u: Float, v: Float,
            lightCoords: Int,
            nx: Float, ny: Float, nz: Float,
        ) {
            buffer.vertex(x.toDouble(), y.toDouble(), z.toDouble())
                .texture(u.toDouble(), v.toDouble())
                .color(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF, color ushr 24)
                .texture(lightCoords shr 16 and 0xFFFF, lightCoords and 0xFFFF)
                .normal(nx, ny, nz)
                .nextVertex()
        }
    }
}
*///?}

typealias InputConstants = com.mojang.blaze3d.platform.InputConstants
