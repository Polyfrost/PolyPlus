package org.polyfrost.polyplus.client.pets

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

internal object PetFootprint {
    private const val EPSILON = 1.0e-7
    private const val FEET_PROBE = 0.05

    fun interface Probe {
        fun at(x: Int, y: Int, z: Int): Int
    }

    fun hazard(x: Double, y: Double, z: Double, halfWidth: Double, height: Double, probe: Probe): Int =
        sweep(x, y, z, 0.0, 0.0, 0.0, halfWidth, height, probe)

    fun sweep(
        x: Double,
        y: Double,
        z: Double,
        dx: Double,
        dy: Double,
        dz: Double,
        halfWidth: Double,
        height: Double,
        probe: Probe,
    ): Int {
        val lowX = min(x, x + dx)
        val highX = max(x, x + dx)
        val lowY = min(y, y + dy)
        val highY = max(y, y + dy)
        val lowZ = min(z, z + dz)
        val highZ = max(z, z + dz)
        val x0: Int
        val x1: Int
        val z0: Int
        val z1: Int
        if (halfWidth > 0.0) {
            x0 = floor(lowX - halfWidth + EPSILON).toInt()
            x1 = max(x0, floor(highX + halfWidth - EPSILON).toInt())
            z0 = floor(lowZ - halfWidth + EPSILON).toInt()
            z1 = max(z0, floor(highZ + halfWidth - EPSILON).toInt())
        } else {
            x0 = floor(lowX).toInt()
            x1 = floor(highX).toInt()
            z0 = floor(lowZ).toInt()
            z1 = floor(highZ).toInt()
        }
        val below = floor(lowY - FEET_PROBE).toInt()
        val feet = floor(highY + FEET_PROBE).toInt()
        val top = if (height > FEET_PROBE) max(feet, floor(highY + height - EPSILON).toInt()) else feet
        var worst = PetCollision.SAFE
        for (bx in x0..x1) {
            for (bz in z0..z1) {
                for (by in below..top) {
                    val hazard = probe.at(bx, by, bz)
                    if (hazard == PetCollision.DANGER) return PetCollision.DANGER
                    worst = max(worst, hazard)
                }
            }
        }
        return worst
    }
}
