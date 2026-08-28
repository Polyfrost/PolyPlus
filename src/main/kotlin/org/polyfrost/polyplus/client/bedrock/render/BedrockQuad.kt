package org.polyfrost.polyplus.client.bedrock.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix4fc
import org.joml.Vector3f

data class BedrockMeshVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    val u: Float,
    val v: Float,
)

data class BedrockQuad(
    val v0: BedrockMeshVertex,
    val v1: BedrockMeshVertex,
    val v2: BedrockMeshVertex,
    val v3: BedrockMeshVertex,
    val nx: Float,
    val ny: Float,
    val nz: Float,
    // Fake glow 0..15 or -1 for incoming ambient light
    val lightLevel: Int = -1,
) {
    fun emit(
        pose: PoseStack.Pose,
        buffer: VertexConsumer,
        lightCoords: Int,
        overlayCoords: Int,
        color: Int,
        scratch: Vector3f,
        vScale: Float = 1f,
        vOffset: Float = 0f,
    ) {
        val matrix = pose.pose()
        val normal = pose.transformNormal(nx, ny, nz, scratch)
        val nxOut = normal.x
        val nyOut = normal.y
        val nzOut = normal.z

        val light = if (lightLevel < 0) lightCoords else emissive(lightCoords, lightLevel)

        emitVertex(v0, matrix, buffer, color, overlayCoords, light, nxOut, nyOut, nzOut, scratch, vScale, vOffset)
        emitVertex(v1, matrix, buffer, color, overlayCoords, light, nxOut, nyOut, nzOut, scratch, vScale, vOffset)
        emitVertex(v2, matrix, buffer, color, overlayCoords, light, nxOut, nyOut, nzOut, scratch, vScale, vOffset)
        emitVertex(v3, matrix, buffer, color, overlayCoords, light, nxOut, nyOut, nzOut, scratch, vScale, vOffset)
    }

    private fun emitVertex(
        vertex: BedrockMeshVertex,
        matrix: Matrix4fc,
        buffer: VertexConsumer,
        color: Int,
        overlayCoords: Int,
        lightCoords: Int,
        nx: Float,
        ny: Float,
        nz: Float,
        scratch: Vector3f,
        vScale: Float,
        vOffset: Float,
    ) {
        val pos = matrix.transformPosition(
            vertex.x / PIXEL_SCALE,
            vertex.y / PIXEL_SCALE,
            vertex.z / PIXEL_SCALE,
            scratch,
        )

        val v = vertex.v * vScale + vOffset
        buffer.addVertex(pos.x(), pos.y(), pos.z(), color, vertex.u, v, overlayCoords, lightCoords, nx, ny, nz)
    }

    companion object {
        const val PIXEL_SCALE = 16f

        private fun emissive(base: Int, level: Int): Int {
            val block = maxOf((base shr 4) and 0xF, level)
            val sky = maxOf((base shr 20) and 0xF, level)
            return (block shl 4) or (sky shl 20)
        }
    }
}
