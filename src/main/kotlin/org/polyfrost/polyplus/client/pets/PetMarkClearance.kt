package org.polyfrost.polyplus.client.pets

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal object PetMarkClearance {
    const val OWNER_HALF_WIDTH = 0.3
    const val OWNER_HEIGHT = 1.8
    const val CLEARANCE = 0.3
    const val STRIDE = 5
    const val STEP = 0.5
    const val SIDE_REACH = 4.0
    const val ALONG_REACH = 3.0

    private val offsets: IntArray = lattice()

    val probes: Int get() = offsets.size / 2

    fun interface Candidate {
        fun accept(x: Double, z: Double): Boolean
    }

    fun clears(
        discs: DoubleArray,
        count: Int,
        x: Double,
        z: Double,
        ownerX: Double,
        ownerY: Double,
        ownerZ: Double,
        clearance: Double = CLEARANCE,
    ): Boolean {
        for (i in 0 until count) {
            val at = i * STRIDE
            if (ownerY >= discs[at + 4] || ownerY + OWNER_HEIGHT <= discs[at + 3]) continue
            val gapX = max(abs(x + discs[at] - ownerX) - OWNER_HALF_WIDTH, 0.0)
            val gapZ = max(abs(z + discs[at + 1] - ownerZ) - OWNER_HALF_WIDTH, 0.0)
            val need = discs[at + 2] + clearance
            if (gapX * gapX + gapZ * gapZ < need * need) return false
        }
        return true
    }

    fun search(
        out: DoubleArray,
        x: Double,
        z: Double,
        aOwnerX: Double,
        aOwnerZ: Double,
        bOwnerX: Double,
        bOwnerZ: Double,
        tieSide: Boolean,
        candidate: Candidate,
    ): Boolean {
        var alongX = bOwnerX - aOwnerX
        var alongZ = bOwnerZ - aOwnerZ
        val length = sqrt(alongX * alongX + alongZ * alongZ)
        if (length < 1.0e-4) {
            alongX = 0.0
            alongZ = -1.0
        } else {
            alongX /= length
            alongZ /= length
        }
        val sideX = -alongZ
        val sideZ = alongX
        val lean = (x - (aOwnerX + bOwnerX) / 2.0) * sideX + (z - (aOwnerZ + bOwnerZ) / 2.0) * sideZ
        val first = if (abs(lean) > 1.0e-4) (if (lean > 0.0) 1.0 else -1.0) else if (tieSide) 1.0 else -1.0
        for (i in 0 until probes) {
            val along = offsets[i * 2] * STEP
            val side = offsets[i * 2 + 1] * STEP * first
            val cx = x + alongX * along + sideX * side
            val cz = z + alongZ * along + sideZ * side
            if (candidate.accept(cx, cz)) return found(out, cx, cz)
        }
        return false
    }

    private fun lattice(): IntArray {
        val alongCells = (ALONG_REACH / STEP).toInt()
        val sideCells = (SIDE_REACH / STEP).toInt()
        val points = ArrayList<IntArray>()
        for (along in -alongCells..alongCells) {
            for (side in -sideCells..sideCells) {
                val a = along.toDouble() / alongCells
                val b = side.toDouble() / sideCells
                if (a * a + b * b <= 1.0) points += intArrayOf(along, side)
            }
        }
        points.sortWith(
            compareBy<IntArray> { it[0] * it[0] + it[1] * it[1] }
                .thenByDescending { abs(it[1]) }
                .thenBy { if (it[1] < 0) 1 else 0 }
                .thenBy { if (it[0] < 0) 1 else 0 },
        )
        val out = IntArray(points.size * 2)
        for (i in points.indices) {
            out[i * 2] = points[i][0]
            out[i * 2 + 1] = points[i][1]
        }
        return out
    }

    private fun found(out: DoubleArray, x: Double, z: Double): Boolean {
        out[0] = x
        out[1] = z
        return true
    }
}
