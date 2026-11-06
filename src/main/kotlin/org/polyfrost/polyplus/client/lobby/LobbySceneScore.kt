package org.polyfrost.polyplus.client.lobby

import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class LobbySurvey(val origin: BlockPos, val minY: Int) {
    val ground = IntArray(CELLS)
    val top = IntArray(CELLS)
    val block = IntArray(CELLS)
    val kind = ByteArray(CELLS)
    val lit = BooleanArray(CELLS)

    fun index(cellX: Int, cellZ: Int): Int = (cellX + RADIUS) * SIDE + cellZ + RADIUS

    companion object {
        const val RADIUS = 32
        const val STEP = 2
        const val SIDE = RADIUS * 2 + 1
        const val CELLS = SIDE * SIDE

        const val UNLOADED: Byte = 0
        const val VOID: Byte = 1
        const val NATURAL: Byte = 2
        const val WATER: Byte = 3
        const val LAVA: Byte = 4
        const val BUILT: Byte = 5
    }
}

object LobbySceneScore {
    enum class Rejection { DIMENSION, UNDERGROUND, ENCLOSED, CEILING, SMALL, VOID, FLOODED, UNEVEN, DULL, UNLOADED, NO_STAGE, WEAKER }

    data class Verdict(val anchor: BlockPos?, val score: Double, val rejection: Rejection?)

    const val MIN_SCORE = 34.0

    private const val CANDIDATE_STEP = 3
    private const val CANDIDATE_REACH = 20
    private const val PLAYER_RISE = 10
    private const val STAGE_RADIUS = 4
    private const val PAD_RADIUS = 8
    private const val VIEW_RADIUS = 16
    private const val FLAT_RISE = 2
    private const val PAD_RISE = 4
    private const val MIN_FLAT = 0.55
    private const val MIN_PAD = 0.4
    private const val MIN_SKY = 0.4
    private const val MAX_VOID = 0.08
    private const val MAX_WATER = 0.65
    private const val MAX_LAVA = 0.05
    private const val MIN_LOADED = 0.6
    private const val MAX_RELIEF = 26.0
    private const val VARIETY_FULL = 14.0
    private const val RELIEF_FULL = 5.0
    private const val BUILT_FULL = 0.12
    private const val LIGHTS_FULL = 8.0
    private const val FLAT_WEIGHT = 30.0
    private const val SKY_WEIGHT = 15.0
    private const val VARIETY_WEIGHT = 20.0
    private const val RELIEF_WEIGHT = 10.0
    private const val BUILT_WEIGHT = 20.0
    private const val LIGHTS_WEIGHT = 8.0
    private const val WATER_COST = 10.0
    private const val FEET_COST = 0.2

    private class Area {
        var cells = 0
        var loaded = 0
        var void = 0
        var water = 0
        var lava = 0
        var built = 0
        var lights = 0
        var sum = 0.0
        var sumSquares = 0.0
        var land = 0
        val blocks = HashSet<Int>()

        val relief: Double
            get() = if (land == 0) 0.0 else sqrt((sumSquares / land - (sum / land) * (sum / land)).coerceAtLeast(0.0))
    }

    fun judge(survey: LobbySurvey): Verdict {
        var best: Verdict? = null
        var worst: Rejection? = null
        val reach = CANDIDATE_REACH
        for (cellX in -reach..reach step CANDIDATE_STEP) {
            for (cellZ in -reach..reach step CANDIDATE_STEP) {
                val verdict = candidate(survey, cellX, cellZ) ?: continue
                if (verdict.rejection != null) {
                    if (cellX == 0 && cellZ == 0) worst = verdict.rejection
                    worst = worst ?: verdict.rejection
                } else if (verdict.score > (best?.score ?: Double.NEGATIVE_INFINITY)) {
                    best = verdict
                }
            }
        }
        return best ?: Verdict(null, 0.0, worst ?: Rejection.SMALL)
    }

    private fun candidate(survey: LobbySurvey, cellX: Int, cellZ: Int): Verdict? {
        val center = survey.index(cellX, cellZ)
        val kind = survey.kind[center]
        if (kind == LobbySurvey.UNLOADED) return null
        val feet = survey.origin.y
        val height = survey.ground[center] + 1
        if (kind != LobbySurvey.NATURAL && kind != LobbySurvey.BUILT) return Verdict(null, 0.0, reject(survey, cellX, cellZ, kind))
        if (abs(height - feet) > PLAYER_RISE) return null

        val stage = stageShape(survey, cellX, cellZ, height)
        if (stage.first < MIN_FLAT) return Verdict(null, 0.0, Rejection.SMALL)
        if (stage.second < MIN_SKY) return Verdict(null, 0.0, Rejection.CEILING)
        val pad = padShape(survey, cellX, cellZ, height)
        if (pad < MIN_PAD) return Verdict(null, 0.0, Rejection.SMALL)

        val area = view(survey, cellX, cellZ)
        val rejection = rejection(area)
        if (rejection != null) return Verdict(null, 0.0, rejection)

        val anchor = BlockPos(survey.origin.x + cellX * LobbySurvey.STEP, height, survey.origin.z + cellZ * LobbySurvey.STEP)
        val waterShare = area.water.toDouble() / area.loaded
        val score = FLAT_WEIGHT * stage.first +
            SKY_WEIGHT * stage.second +
            VARIETY_WEIGHT * min(1.0, area.blocks.size / VARIETY_FULL) +
            RELIEF_WEIGHT * min(1.0, area.relief / RELIEF_FULL) +
            BUILT_WEIGHT * min(1.0, area.built.toDouble() / area.loaded / BUILT_FULL) +
            LIGHTS_WEIGHT * min(1.0, area.lights / LIGHTS_FULL) -
            WATER_COST * waterShare -
            FEET_COST * (abs(cellX) + abs(cellZ))
        return Verdict(anchor, score, if (score < MIN_SCORE) Rejection.DULL else null)
    }

    private fun reject(survey: LobbySurvey, cellX: Int, cellZ: Int, kind: Byte): Rejection = when {
        kind == LobbySurvey.VOID -> Rejection.VOID
        cellX == 0 && cellZ == 0 && survey.kind[survey.index(0, 0)] == LobbySurvey.WATER -> Rejection.FLOODED
        else -> Rejection.SMALL
    }

    private fun rejection(area: Area): Rejection? {
        if (area.loaded < area.cells * MIN_LOADED) return Rejection.UNLOADED
        return when {
            area.void > area.loaded * MAX_VOID -> Rejection.VOID
            area.water > area.loaded * MAX_WATER -> Rejection.FLOODED
            area.lava > area.loaded * MAX_LAVA -> Rejection.FLOODED
            area.relief > MAX_RELIEF -> Rejection.UNEVEN
            else -> null
        }
    }

    private fun stageShape(survey: LobbySurvey, cellX: Int, cellZ: Int, height: Int): Pair<Double, Double> {
        var cells = 0
        var flat = 0
        var sky = 0
        forCells(cellX, cellZ, STAGE_RADIUS) { x, z ->
            val index = survey.index(x, z)
            cells++
            val kind = survey.kind[index]
            val ground = survey.ground[index] + 1
            if ((kind == LobbySurvey.NATURAL || kind == LobbySurvey.BUILT) && abs(ground - height) <= FLAT_RISE) flat++
            if (survey.top[index] - survey.ground[index] <= 1) sky++
        }
        return flat.toDouble() / cells to sky.toDouble() / cells
    }

    private fun padShape(survey: LobbySurvey, cellX: Int, cellZ: Int, height: Int): Double {
        var cells = 0
        var land = 0
        forCells(cellX, cellZ, PAD_RADIUS) { x, z ->
            val index = survey.index(x, z)
            cells++
            val kind = survey.kind[index]
            if ((kind == LobbySurvey.NATURAL || kind == LobbySurvey.BUILT) && abs(survey.ground[index] + 1 - height) <= PAD_RISE) land++
        }
        return land.toDouble() / cells
    }

    private fun view(survey: LobbySurvey, cellX: Int, cellZ: Int): Area {
        val area = Area()
        forCells(cellX, cellZ, VIEW_RADIUS) { x, z ->
            val index = survey.index(x, z)
            area.cells++
            when (survey.kind[index]) {
                LobbySurvey.UNLOADED -> return@forCells
                LobbySurvey.VOID -> area.void++
                LobbySurvey.WATER -> area.water++
                LobbySurvey.LAVA -> area.lava++
                LobbySurvey.BUILT -> area.built++
            }
            area.loaded++
            if (survey.lit[index]) area.lights++
            area.blocks += survey.block[index]
            val kind = survey.kind[index]
            if (kind == LobbySurvey.NATURAL || kind == LobbySurvey.BUILT) {
                val ground = survey.ground[index].toDouble()
                area.land++
                area.sum += ground
                area.sumSquares += ground * ground
            }
        }
        return area
    }

    private inline fun forCells(cellX: Int, cellZ: Int, radius: Int, visit: (Int, Int) -> Unit) {
        val squared = radius * radius
        for (x in maxOf(-LobbySurvey.RADIUS, cellX - radius)..minOf(LobbySurvey.RADIUS, cellX + radius)) {
            for (z in maxOf(-LobbySurvey.RADIUS, cellZ - radius)..minOf(LobbySurvey.RADIUS, cellZ + radius)) {
                if ((x - cellX) * (x - cellX) + (z - cellZ) * (z - cellZ) <= squared) visit(x, z)
            }
        }
    }
}
