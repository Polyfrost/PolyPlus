package org.polyfrost.polyplus.client.emotes.props

import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.joml.Vector3f
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.VertexConsumer
import kotlin.math.sqrt

internal class EmotePropSkin(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val glow: ByteArray,
    val bones: IntArray,
    val runs: IntArray,
    val runCount: Int,
    val boneCount: Int,
) {
    val frameFloats: Int get() = boneCount * BONE_FLOATS + runCount

    companion object {
        const val BONE_FLOATS = 12
    }
}

internal class EmotePropDraw(val skin: EmotePropSkin, val frame: FloatArray, val texture: Identifier)

internal object EmoteProps {
    private val matrix = Matrix4f()
    private val bone = Matrix4f()
    private val position = Vector3f()
    private val normal = Vector3f()

    fun emit(
        draw: EmotePropDraw,
        pose: Pose,
        buffer: VertexConsumer,
        lightCoords: Int,
        overlayCoords: Int,
        correction: Matrix4f?,
    ) {
        val skin = draw.skin
        val out = draw.frame
        val offsets = skin.boneCount * EmotePropSkin.BONE_FLOATS
        for (pick in 0 until skin.runs.size / PICK_INTS) {
            val vOffset = out[offsets + skin.runs[pick * PICK_INTS]]
            val start = skin.runs[pick * PICK_INTS + 1]
            val end = skin.runs[pick * PICK_INTS + 2]
            var current = -1
            var b = 0
            for (i in start until end) {
                if (skin.bones[i] != current) {
                    current = skin.bones[i]
                    b = current * EmotePropSkin.BONE_FLOATS
                    bone.set(
                        out[b], out[b + 4], out[b + 8], 0f,
                        out[b + 1], out[b + 5], out[b + 9], 0f,
                        out[b + 2], out[b + 6], out[b + 10], 0f,
                        out[b + 3], out[b + 7], out[b + 11], 1f,
                    )
                    matrix.set(pose.pose())
                    if (correction != null) matrix.mul(correction)
                    matrix.mul(bone)
                }
                matrix.transformPosition(skin.positions[i * 3], skin.positions[i * 3 + 1], skin.positions[i * 3 + 2], position)
                val nx = skin.normals[i * 3]
                val ny = skin.normals[i * 3 + 1]
                val nz = skin.normals[i * 3 + 2]
                var tx = out[b] * nx + out[b + 1] * ny + out[b + 2] * nz
                var ty = out[b + 4] * nx + out[b + 5] * ny + out[b + 6] * nz
                var tz = out[b + 8] * nx + out[b + 9] * ny + out[b + 10] * nz
                if (correction != null) {
                    normal.set(tx, ty, tz)
                    correction.transformDirection(normal)
                    tx = normal.x
                    ty = normal.y
                    tz = normal.z
                }
                val length = sqrt(tx * tx + ty * ty + tz * tz)
                val inverse = if (length > 0f) 1f / length else 0f
                pose.transformNormal(tx * inverse, ty * inverse, tz * inverse, normal)
                buffer.addVertex(
                    position.x, position.y, position.z, WHITE,
                    skin.uvs[i * 2], skin.uvs[i * 2 + 1] + vOffset,
                    overlayCoords, emissive(lightCoords, skin.glow[i].toInt()), normal.x, normal.y, normal.z,
                )
            }
        }
    }

    private fun emissive(base: Int, level: Int): Int {
        if (level == 0) return base
        val block = maxOf((base shr 4) and 0xF, level)
        val sky = maxOf((base shr 20) and 0xF, level)
        return (block shl 4) or (sky shl 20)
    }

    private const val PICK_INTS = 3
    private const val WHITE = -1
}
