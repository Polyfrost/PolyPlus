package org.polyfrost.polyplus.client.emotes.playback

import org.joml.Matrix4f
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val MIN_SCALE = 1.0e-6f

internal class PartTransform {
    var x = 0f
    var y = 0f
    var z = 0f
    var xRot = 0f
    var yRot = 0f
    var zRot = 0f
    var xScale = 1f
    var yScale = 1f
    var zScale = 1f

    fun decompose(matrix: Matrix4f): PartTransform {
        x = matrix.m30()
        y = matrix.m31()
        z = matrix.m32()
        xScale = length(matrix.m00(), matrix.m01(), matrix.m02())
        yScale = length(matrix.m10(), matrix.m11(), matrix.m12())
        zScale = length(matrix.m20(), matrix.m21(), matrix.m22())
        if (xScale < MIN_SCALE || yScale < MIN_SCALE || zScale < MIN_SCALE) {
            xRot = 0f
            yRot = 0f
            zRot = 0f
            return this
        }

        val r00 = matrix.m00() / xScale
        val r10 = matrix.m01() / xScale
        val r20 = matrix.m02() / xScale
        val r01 = matrix.m10() / yScale
        val r11 = matrix.m11() / yScale
        val r02 = matrix.m20() / zScale
        val r12 = matrix.m21() / zScale

        zRot = atan2(r10, r00)
        yRot = atan2(-r20, sqrt(r00 * r00 + r10 * r10))
        val sinZ = sin(zRot)
        val cosZ = cos(zRot)
        xRot = atan2(sinZ * r02 - cosZ * r12, cosZ * r11 - sinZ * r01)
        return this
    }

    companion object {
        fun compose(
            dest: Matrix4f,
            x: Float, y: Float, z: Float,
            xRot: Float, yRot: Float, zRot: Float,
            xScale: Float = 1f, yScale: Float = 1f, zScale: Float = 1f,
        ): Matrix4f = dest.translation(x, y, z).rotateZYX(zRot, yRot, xRot).scale(xScale, yScale, zScale)

        private fun length(a: Float, b: Float, c: Float): Float = sqrt(a * a + b * b + c * c)
    }
}
