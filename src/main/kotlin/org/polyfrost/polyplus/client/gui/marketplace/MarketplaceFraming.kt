package org.polyfrost.polyplus.client.gui.marketplace

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.playback.AnimationSampler
import org.polyfrost.polyplus.client.bedrock.render.BedrockBoneRenderer
import org.polyfrost.polyplus.client.bedrock.render.BedrockQuad
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val FLAP_SAMPLES = 24
private const val BODY_REACH = 0.5f

internal class SideReach(val left: Float, val right: Float) {
    val span: Float get() = left + right
}

private fun walkBone(bone: BedrockBoneRenderer, parent: Matrix4f, visit: (Vector3f) -> Unit) {
    val matrix = Matrix4f(parent)
        .translate(bone.x / BedrockQuad.PIXEL_SCALE, bone.y / BedrockQuad.PIXEL_SCALE, bone.z / BedrockQuad.PIXEL_SCALE)
        .rotate(Quaternionf().rotationZYX(bone.zRot, bone.yRot, bone.xRot))
        .scale(bone.xScale, bone.yScale, bone.zScale)
    for (quad in bone.mesh.quads) {
        for (vertex in listOf(quad.v0, quad.v1, quad.v2, quad.v3)) {
            visit(matrix.transformPosition(Vector3f(vertex.x, vertex.y, vertex.z).div(BedrockQuad.PIXEL_SCALE)))
        }
    }
    bone.children.forEach { walkBone(it, matrix, visit) }
}

private fun AttachedCosmetic.visitPose(step: Int, visit: (Vector3f) -> Unit) {
    model.resetPose()
    animation?.let { model.applyPose(AnimationSampler.sample(it, it.lengthTicks * step / FLAP_SAMPLES), 1f) }
    val root = Matrix4f().scale(scale)
    model.attachments.forEach { walkBone(it.rootBone, root, visit) }
    model.resetPose()
}

internal fun typicalReach(worn: List<AttachedCosmetic>, yawDeg: Float): SideReach {
    val yaw = Math.toRadians(yawDeg.toDouble()).toFloat()
    val across = cos(yaw)
    val depth = sin(yaw)
    val shown = worn.filter { it.slot != BodySlot.Aura }
    val poses = (0 until FLAP_SAMPLES).map { step ->
        var left = BODY_REACH
        var right = BODY_REACH
        shown.forEach { cosmetic ->
            cosmetic.visitPose(step) { point ->
                val side = point.x * across + point.z * depth
                left = max(left, side)
                right = max(right, -side)
            }
        }
        SideReach(left, right)
    }
    return poses.sortedBy { it.span }[FLAP_SAMPLES / 2]
}
