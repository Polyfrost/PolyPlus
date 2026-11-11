package org.polyfrost.polyplus.client.lobby

import net.minecraft.world.phys.Vec3
import org.joml.Vector4f
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

object LobbyWaveState {
    const val SPEED = 28f
    private const val LEVEL_EPSILON = 1e-4
    private const val STEEP = 1.5707

    @Volatile
    private var frame = Vector4f(0f, 0f, 0f, 0f)

    @JvmStatic
    fun active(): Boolean = LobbyScene.terrainActive()

    @JvmStatic
    fun seconds(): Float = LobbyScene.waveClock()

    @JvmStatic
    fun aim(): Float = LobbyScene.waveAim()

    @JvmStatic
    fun vector(): Vector4f = if (active()) Vector4f(frame) else Vector4f(0f, 0f, 0f, 0f)

    fun view(look: Vec3, fov: Float) {
        val distance = aim().toDouble()
        val level = sqrt(look.x * look.x + look.z * look.z) + LEVEL_EPSILON
        val aimAlong = level * distance
        val dip = atan2(-look.y, level) + Math.toRadians(fov / 2.0)
        val start = when {
            dip <= 0.0 -> aimAlong
            dip >= STEEP -> 0.0
            else -> (-look.y * distance / tan(dip)).coerceIn(0.0, aimAlong)
        }
        frame = Vector4f((look.x / level).toFloat(), (look.z / level).toFloat(), start.toFloat(), seconds() * SPEED)
    }

    fun distance(x: Double, z: Double, camera: Vec3): Double {
        val wave = frame
        return ((x - camera.x) * wave.x + (z - camera.z) * wave.y - wave.z).coerceAtLeast(0.0)
    }
}
