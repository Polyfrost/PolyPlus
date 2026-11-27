package org.polyfrost.polyplus.client.emotes.duo

import org.polyfrost.polyplus.client.emotes.EmoteMark
import org.polyfrost.polyplus.client.emotes.EmoteRole
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal data class Spot(val x: Double, val y: Double, val z: Double, val yaw: Float) {
    fun distanceTo(x: Double, y: Double, z: Double): Double {
        val dx = x - this.x
        val dy = y - this.y
        val dz = z - this.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun turnFrom(yaw: Float): Float = abs(DuoPlacement.wrap(yaw - this.yaw))
}

internal object DuoPlacement {
    const val READY_DISTANCE = 0.75
    const val READY_DEGREES = 30f
    const val LEAVE_DISTANCE = 1.0

    fun spot(anchor: EmoteAnchor, mark: EmoteMark): Spot {
        val (dx, dz) = turn(mark.x.toDouble(), mark.z.toDouble(), anchor.yaw)
        return Spot(anchor.x + dx, anchor.y + mark.y, anchor.z + dz, wrap(anchor.yaw + mark.yaw))
    }

    fun anchorFrom(x: Double, y: Double, z: Double, yaw: Float, mark: EmoteMark): EmoteAnchor {
        val anchorYaw = wrap(yaw - mark.yaw)
        val (dx, dz) = turn(mark.x.toDouble(), mark.z.toDouble(), anchorYaw)
        return EmoteAnchor(x - dx, y - mark.y, z - dz, anchorYaw)
    }

    fun role(index: Int, anchor: EmoteAnchor, x: Double, y: Double, z: Double, bodyYaw: Float): EmoteRole {
        val (dx, dz) = turn(anchor.x - x, anchor.z - z, -bodyYaw)
        return EmoteRole(index, dx.toFloat(), (anchor.y - y).toFloat(), dz.toFloat(), wrap(anchor.yaw - bodyYaw))
    }

    fun ready(spot: Spot, x: Double, y: Double, z: Double, yaw: Float): Boolean =
        spot.distanceTo(x, y, z) <= READY_DISTANCE && spot.turnFrom(yaw) <= READY_DEGREES

    fun wrap(degrees: Float): Float {
        var a = degrees % FULL_TURN
        if (a >= HALF_TURN) a -= FULL_TURN
        if (a < -HALF_TURN) a += FULL_TURN
        return a
    }

    private fun turn(x: Double, z: Double, yaw: Float): Pair<Double, Double> {
        val radians = Math.toRadians(yaw.toDouble())
        val s = sin(radians)
        val c = cos(radians)
        return (x * c - z * s) to (x * s + z * c)
    }

    private const val FULL_TURN = 360f
    private const val HALF_TURN = 180f
}
