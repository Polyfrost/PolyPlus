package org.polyfrost.polyplus.client.gui.marketplace

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

private fun AttachedCosmetic.visitPose(step: Int, visit: (x: Float, z: Float) -> Unit) {
    val positions = scene.restPositions(scene.animationSeconds * step / FLAP_SAMPLES)
    for (i in positions.indices step 3) visit(positions[i], positions[i + 2])
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
            cosmetic.visitPose(step) { x, z ->
                val side = x * across + z * depth
                left = max(left, side)
                right = max(right, -side)
            }
        }
        SideReach(left, right)
    }
    return poses.sortedBy { it.span }[FLAP_SAMPLES / 2]
}
