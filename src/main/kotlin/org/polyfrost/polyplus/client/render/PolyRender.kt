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
import org.lwjgl.opengl.GL11
import kotlin.math.abs
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

class VertexConsumer private constructor(private val buffer: BufferBuilder) {
    fun addVertex(
        x: Float, y: Float, z: Float,
        color: Int,
        u: Float, v: Float,
        @Suppress("UNUSED_PARAMETER") overlayCoords: Int,
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

    companion object {
        private val FORMAT = VertexFormat()
            .addElement(DefaultVertexFormat.POSITION_ELEMENT)
            .addElement(DefaultVertexFormat.UV0_ELEMENT)
            .addElement(DefaultVertexFormat.COLOR_ELEMENT)
            .addElement(DefaultVertexFormat.UV1_ELEMENT)
            .addElement(VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.NORMAL, 3))

        fun draw(texture: Identifier, translucent: Boolean, emit: (VertexConsumer) -> Unit) {
            Minecraft.getInstance().textureManager.bind(texture)
            if (translucent) {
                GlStateManager.enableBlend()
                GlStateManager.blendFuncSeparate(770, 771, 1, 771)
            }
            val tessellator = Tesselator.getInstance()
            val buffer = tessellator.buffer
            buffer.begin(GL11.GL_QUADS, FORMAT)
            try {
                emit(VertexConsumer(buffer))
            } finally {
                tessellator.end()
                if (translucent) GlStateManager.disableBlend()
            }
        }
    }
}
*///?}

typealias InputConstants = com.mojang.blaze3d.platform.InputConstants
