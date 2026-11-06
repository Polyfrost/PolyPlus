package org.polyfrost.polyplus.client.lobby

import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

object LobbyWaveFront {
    fun distance(x: Double, z: Double, camera: Vec3, aim: Vec3, yawDegrees: Double, pitchDegrees: Double, fov: Double): Double {
        val yaw = Math.toRadians(yawDegrees)
        val forwardX = -sin(yaw)
        val forwardZ = cos(yaw)
        val aimAlong = ((aim.x - camera.x) * forwardX + (aim.z - camera.z) * forwardZ).coerceAtLeast(0.0)
        val dip = Math.toRadians(pitchDegrees + fov / 2.0)
        val start = when {
            dip <= 0.0 -> aimAlong
            dip >= PI / 2.0 -> 0.0
            else -> ((camera.y - aim.y) / tan(dip)).coerceIn(0.0, aimAlong)
        }
        return ((x - camera.x) * forwardX + (z - camera.z) * forwardZ - start).coerceAtLeast(0.0)
    }
}
