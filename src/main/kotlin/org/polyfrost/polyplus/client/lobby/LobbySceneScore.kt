package org.polyfrost.polyplus.client.lobby

import net.minecraft.core.BlockPos
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
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
        const val WALL: Byte = 6
    }
}

class LobbyRoom(val origin: BlockPos, val biome: String) {
    val floor = IntArray(CELLS)
    val headroom = IntArray(CELLS)
    val kind = ByteArray(CELLS)
    val block = IntArray(CELLS)
    val crown = IntArray(CELLS)
    val light = FloatArray(CELLS)
    val lit = BooleanArray(CELLS)
    val crafted = BooleanArray(CELLS)
    val beauty = IntArray(CELLS)

    fun index(cellX: Int, cellZ: Int): Int = (cellX + RADIUS) * SIDE + cellZ + RADIUS

    companion object {
        const val RADIUS = 20
        const val SIDE = RADIUS * 2 + 1
        const val CELLS = SIDE * SIDE
        const val DROP = 6
        const val RISE = 3
        const val MAX_HEADROOM = 16
    }
}

object LobbySceneScore {
    enum class Rejection(val reason: String) {
        DIMENSION("Only works where the sky is normal"),
        UNDERGROUND("You are underground"),
        ENCLOSED("Needs open sky"),
        CEILING("Something is blocking the sky overhead"),
        SMALL("Needs more flat ground to stand on"),
        VOID("Too close to the edge of the world"),
        FLOODED("Too much water nearby"),
        UNEVEN("The ground is too steep"),
        DULL("Not interesting enough yet"),
        UNLOADED("Not enough of the area is loaded yet"),
        NO_STAGE("Could not find a good place to stand"),
        WEAKER("You already have a better scene from here"),
        ROOM("Room is too small"),
        LOW_CEILING("Ceiling is too low for the camera"),
        CAMERA("Not enough room for the camera to stand back"),
        DARK("Too dark here. Add some lights."),
        LAVA("Too close to lava"),
    }

    enum class Profile(val label: String, val key: String) {
        OUTDOOR("Outdoor", "outdoor"),
        INDOOR("Indoor", "indoor"),
        CAVE("Cave", "cave"),
        ;

        val interior: Boolean
            get() = this != OUTDOOR

        companion object {
            fun of(key: String?): Profile = entries.firstOrNull { it.key == key } ?: OUTDOOR
        }
    }

    data class Category(val key: String, val label: String, val points: Double, val max: Double)

    data class Breakdown(val profile: Profile, val categories: List<Category>, val distanceCost: Double) {
        val max: Double
            get() = categories.sumOf { it.max }

        val total: Int
            get() = display(categories.sumOf { it.points } - distanceCost, max)
    }

    enum class CheckState { PASSED, FAILED, UNCHECKED }

    data class Check(val label: String, val state: CheckState, val reason: String?, val blocking: Boolean = true)

    data class Report(val breakdown: Breakdown?, val checks: List<Check>, val profile: Profile = breakdown?.profile ?: Profile.OUTDOOR) {
        val passed: Boolean
            get() = checks.none { it.blocking && it.state == CheckState.FAILED }
    }

    data class Verdict(
        val anchor: BlockPos?,
        val score: Double,
        val rejection: Rejection?,
        val breakdown: Breakdown? = null,
        val profile: Profile = breakdown?.profile ?: Profile.OUTDOOR,
    )

    private class Requirement(val label: String, val rejections: Set<Rejection>)

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
    private const val OUTDOOR_MAX = FLAT_WEIGHT + SKY_WEIGHT + VARIETY_WEIGHT + RELIEF_WEIGHT + BUILT_WEIGHT + LIGHTS_WEIGHT + WATER_COST
    private const val FULL_SCORE = 100.0

    val passPoints: Int = display(MIN_SCORE + WATER_COST, OUTDOOR_MAX)

    private val REQUIREMENTS = listOf(
        Requirement("Normal sky", setOf(Rejection.DIMENSION)),
        Requirement("Above ground", setOf(Rejection.UNDERGROUND)),
        Requirement("Open sky", setOf(Rejection.ENCLOSED, Rejection.CEILING)),
        Requirement("Room to stand", setOf(Rejection.SMALL, Rejection.NO_STAGE)),
        Requirement("Solid ground all around", setOf(Rejection.VOID)),
        Requirement("Not flooded", setOf(Rejection.FLOODED)),
        Requirement("Area loaded", setOf(Rejection.UNLOADED)),
        Requirement("Ground not too steep", setOf(Rejection.UNEVEN)),
        Requirement("Scene score of at least $passPoints", setOf(Rejection.DULL)),
    )

    private val INTERIOR_REQUIREMENTS = listOf(
        Requirement("Normal world", setOf(Rejection.DIMENSION)),
        Requirement("Big enough room", setOf(Rejection.ROOM, Rejection.NO_STAGE)),
        Requirement("Room for the camera", setOf(Rejection.LOW_CEILING, Rejection.CAMERA)),
        Requirement("Bright enough", setOf(Rejection.DARK)),
        Requirement("Not flooded", setOf(Rejection.FLOODED, Rejection.LAVA)),
        Requirement("Area loaded", setOf(Rejection.UNLOADED)),
        Requirement("Scene score of at least $passPoints", setOf(Rejection.DULL)),
    )

    private val NEWER = Requirement("Better than your saved scenes here", setOf(Rejection.WEAKER))

    private fun display(raw: Double, max: Double): Int = (raw / max * FULL_SCORE).roundToInt().coerceIn(0, FULL_SCORE.toInt())

    fun report(
        breakdown: Breakdown?,
        rejection: Rejection?,
        surveyPassed: Boolean,
        weaker: Boolean?,
        weakerBlocks: Boolean,
        reason: String? = rejection?.reason,
        profile: Profile = breakdown?.profile ?: Profile.OUTDOOR,
    ): Report {
        val requirements = if (profile.interior) INTERIOR_REQUIREMENTS else REQUIREMENTS
        val failed = requirements.indexOfFirst { rejection != null && rejection in it.rejections }
        val checks = requirements.mapIndexed { index, requirement ->
            when {
                index == failed -> Check(requirement.label, CheckState.FAILED, reason)
                surveyPassed || failed < 0 || index < failed -> Check(requirement.label, CheckState.PASSED, null)
                else -> Check(requirement.label, CheckState.UNCHECKED, null)
            }
        }
        val newer = when (weaker) {
            null -> Check(NEWER.label, CheckState.UNCHECKED, null, weakerBlocks)
            true -> Check(NEWER.label, CheckState.FAILED, if (weakerBlocks) Rejection.WEAKER.reason else "${Rejection.WEAKER.reason}. It will be saved as an extra scene.", weakerBlocks)
            false -> Check(NEWER.label, CheckState.PASSED, null, weakerBlocks)
        }
        return Report(breakdown, checks + newer, profile)
    }

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
        var scored: Verdict? = null
        val reach = CANDIDATE_REACH
        for (cellX in -reach..reach step CANDIDATE_STEP) {
            for (cellZ in -reach..reach step CANDIDATE_STEP) {
                val verdict = candidate(survey, cellX, cellZ) ?: continue
                if (verdict.breakdown != null && verdict.score > (scored?.score ?: Double.NEGATIVE_INFINITY)) scored = verdict
                if (verdict.rejection != null) {
                    if (cellX == 0 && cellZ == 0) worst = verdict.rejection
                    worst = worst ?: verdict.rejection
                } else if (verdict.score > (best?.score ?: Double.NEGATIVE_INFINITY)) {
                    best = verdict
                }
            }
        }
        return best ?: Verdict(null, 0.0, worst ?: Rejection.SMALL, scored?.breakdown)
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
        return Verdict(anchor, score, if (score < MIN_SCORE) Rejection.DULL else null, breakdown(stage, area, waterShare, cellX, cellZ))
    }

    private fun breakdown(stage: Pair<Double, Double>, area: Area, waterShare: Double, cellX: Int, cellZ: Int) = Breakdown(
        Profile.OUTDOOR,
        listOf(
            Category("flat", "Flat ground", FLAT_WEIGHT * stage.first, FLAT_WEIGHT),
            Category("sky", "Open sky", SKY_WEIGHT * stage.second, SKY_WEIGHT),
            Category("variety", "Block variety", VARIETY_WEIGHT * min(1.0, area.blocks.size / VARIETY_FULL), VARIETY_WEIGHT),
            Category("relief", "Hills and slopes", RELIEF_WEIGHT * min(1.0, area.relief / RELIEF_FULL), RELIEF_WEIGHT),
            Category("built", "Builds", BUILT_WEIGHT * min(1.0, area.built.toDouble() / area.loaded / BUILT_FULL), BUILT_WEIGHT),
            Category("lights", "Lights", LIGHTS_WEIGHT * min(1.0, area.lights / LIGHTS_FULL), LIGHTS_WEIGHT),
            Category("dry", "Dry land", WATER_COST * (1.0 - waterShare), WATER_COST),
        ),
        FEET_COST * (abs(cellX) + abs(cellZ)),
    )

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

    //? if > 1.8.9 {
    private const val CAVE_LOOK = true
    //?} else {
    /*private const val CAVE_LOOK = false
    *///?}
    private const val ROOM_STEP = 2
    private const val ROOM_REACH = 10
    private const val ROOM_PLAYER_RISE = 4
    private const val ROOM_VIEW = 12
    private const val ROOM_STAGE = 3
    private const val ROOM_PAD = 5
    private const val ROOM_FLAT_RISE = 2
    private const val STAGE_FLAT_RISE = 1
    private const val STAND_HEADROOM = 3
    private const val CAMERA_HEADROOM = 5
    private const val CAMERA_REACH = 9
    private const val CAMERA_STEP_UP = 3
    private const val CAMERA_HEADINGS = 16
    private const val MIN_ROOM_CELLS = 64
    private const val MIN_HEAD_SHARE = 0.7
    private const val MIN_LIGHT = 6.0
    private const val MAX_ROOM_WATER = 0.5
    private const val MAX_ROOM_LAVA = 0.06
    private const val CAVE_BUILT = 0.12
    private const val ROOM_FULL = 170.0
    private const val ROOM_BUILT_FULL = 0.25
    private const val ROOM_LIGHT_FULL = 12.0
    private const val FEATURE_FULL = 2.0
    private const val POOL_CAP = 0.15
    private const val POOL_POINTS = 4.0
    private const val LAVA_GLOW_CAP = 0.02
    private const val LAVA_GLOW_POINTS = 20.0
    private const val BIOME_SHARE = 0.45
    private const val ROOM_WEIGHT = 25.0
    private const val ROOM_FLAT_WEIGHT = 15.0
    private const val ROOM_BUILT_WEIGHT = 20.0
    private const val ROOM_LIGHTS_WEIGHT = 12.0
    private const val ROOM_VARIETY_WEIGHT = 12.0
    private const val BEAUTY_WEIGHT = 25.0
    private const val ROOM_FEET_COST = 0.1
    private const val OTHER_CAVE = 0.15

    private val CAVE_BIOMES = mapOf(
        "minecraft:lush_caves" to 1.0,
        "minecraft:dripstone_caves" to 0.6,
        "minecraft:deep_dark" to 0.0,
    )

    private class Room {
        var cells = 0
        var loaded = 0
        var open = 0
        var floor = 0
        var water = 0
        var lava = 0
        var lavaNear = 0
        var crafted = 0
        var lights = 0
        var light = 0.0
        var beauty = 0.0
        val blocks = HashSet<Int>()
    }

    fun judgeRoom(room: LobbyRoom): Verdict {
        var best: Verdict? = null
        var worst: Verdict? = null
        var scored: Verdict? = null
        for (cellX in -ROOM_REACH..ROOM_REACH step ROOM_STEP) {
            for (cellZ in -ROOM_REACH..ROOM_REACH step ROOM_STEP) {
                val verdict = roomCandidate(room, cellX, cellZ) ?: continue
                if (verdict.breakdown != null && verdict.score > (scored?.score ?: Double.NEGATIVE_INFINITY)) scored = verdict
                if (verdict.rejection != null) {
                    if (cellX == 0 && cellZ == 0) worst = verdict
                    worst = worst ?: verdict
                } else if (verdict.score > (best?.score ?: Double.NEGATIVE_INFINITY)) {
                    best = verdict
                }
            }
        }
        best?.let { return it }
        val profile = scored?.profile ?: worst?.profile ?: Profile.INDOOR
        return Verdict(null, 0.0, worst?.rejection ?: Rejection.ROOM, scored?.breakdown, profile)
    }

    private fun roomCandidate(room: LobbyRoom, cellX: Int, cellZ: Int): Verdict? {
        val center = room.index(cellX, cellZ)
        val kind = room.kind[center]
        if (kind == LobbySurvey.UNLOADED) return null
        val origin = cellX == 0 && cellZ == 0
        when (kind) {
            LobbySurvey.WATER -> return Verdict(null, 0.0, if (origin) Rejection.FLOODED else Rejection.ROOM, profile = Profile.INDOOR)
            LobbySurvey.LAVA -> return Verdict(null, 0.0, Rejection.LAVA, profile = Profile.INDOOR)
            LobbySurvey.NATURAL, LobbySurvey.BUILT -> Unit
            else -> return Verdict(null, 0.0, Rejection.ROOM, profile = Profile.INDOOR)
        }
        val height = room.floor[center]
        if (abs(height - room.origin.y) > ROOM_PLAYER_RISE) return null
        val area = roomView(room, cellX, cellZ, height)
        val builtShare = if (area.loaded == 0) 0.0 else area.crafted.toDouble() / area.loaded
        val profile = if (builtShare < CAVE_BUILT || room.biome in CAVE_BIOMES) Profile.CAVE else Profile.INDOOR
        if (area.loaded < area.cells * MIN_LOADED) return Verdict(null, 0.0, Rejection.UNLOADED, profile = profile)
        if (area.lavaNear > 0 || area.lava > area.loaded * MAX_ROOM_LAVA) return Verdict(null, 0.0, Rejection.LAVA, profile = profile)
        if (area.water > area.loaded * MAX_ROOM_WATER) return Verdict(null, 0.0, Rejection.FLOODED, profile = profile)
        if (area.floor < MIN_ROOM_CELLS) return Verdict(null, 0.0, Rejection.ROOM, profile = profile)

        val stage = roomStage(room, cellX, cellZ, height)
        val breakdown = roomBreakdown(room, area, stage.first, builtShare, profile, cellX, cellZ)
        val score = breakdown.categories.sumOf { it.points } - breakdown.distanceCost
        val anchor = BlockPos(room.origin.x + cellX, height, room.origin.z + cellZ)
        val rejection = when {
            stage.second < MIN_HEAD_SHARE -> Rejection.LOW_CEILING
            !cameraFits(room, cellX, cellZ, height) -> Rejection.CAMERA
            area.light / area.open.coerceAtLeast(1) < MIN_LIGHT -> Rejection.DARK
            breakdown.total < passPoints -> Rejection.DULL
            else -> null
        }
        return Verdict(if (rejection == null) anchor else null, score, rejection, breakdown, profile)
    }

    private fun roomBreakdown(room: LobbyRoom, area: Room, flat: Double, builtShare: Double, profile: Profile, cellX: Int, cellZ: Int): Breakdown {
        val open = area.open.coerceAtLeast(1)
        val categories = arrayListOf(
            Category("room", "Room size", ROOM_WEIGHT * min(1.0, area.floor / ROOM_FULL), ROOM_WEIGHT),
            Category("flat", "Flat floor", ROOM_FLAT_WEIGHT * flat, ROOM_FLAT_WEIGHT),
            Category("built", "Builds", ROOM_BUILT_WEIGHT * min(1.0, builtShare / ROOM_BUILT_FULL), ROOM_BUILT_WEIGHT),
            Category("lights", "Lights", ROOM_LIGHTS_WEIGHT * min(1.0, area.light / open / ROOM_LIGHT_FULL), ROOM_LIGHTS_WEIGHT),
            Category("variety", "Block variety", ROOM_VARIETY_WEIGHT * min(1.0, area.blocks.size / VARIETY_FULL), ROOM_VARIETY_WEIGHT),
        )
        if (CAVE_LOOK) {
            val loaded = area.loaded.coerceAtLeast(1).toDouble()
            val biome = CAVE_BIOMES[room.biome] ?: OTHER_CAVE
            val pools = min(POOL_CAP, area.water / loaded) * POOL_POINTS
            val glow = min(LAVA_GLOW_CAP, area.lava / loaded) * LAVA_GLOW_POINTS
            val features = min(1.0, (area.beauty / loaded + pools + glow) / FEATURE_FULL)
            categories += Category("cave", "Cave beauty", BEAUTY_WEIGHT * (BIOME_SHARE * biome + (1.0 - BIOME_SHARE) * features), BEAUTY_WEIGHT)
        }
        return Breakdown(profile, categories, ROOM_FEET_COST * (abs(cellX) + abs(cellZ)))
    }

    private fun roomView(room: LobbyRoom, cellX: Int, cellZ: Int, height: Int): Room {
        val area = Room()
        roomCells(cellX, cellZ, ROOM_VIEW) { x, z ->
            val index = room.index(x, z)
            area.cells++
            val kind = room.kind[index]
            if (kind == LobbySurvey.UNLOADED) return@roomCells
            area.loaded++
            val near = (x - cellX) * (x - cellX) + (z - cellZ) * (z - cellZ) <= ROOM_PAD * ROOM_PAD
            if (room.crafted[index]) area.crafted++
            if (room.lit[index]) area.lights++
            area.beauty += room.beauty[index]
            area.blocks += room.block[index]
            if (room.crown[index] != 0) area.blocks += room.crown[index]
            when (kind) {
                LobbySurvey.WATER -> area.water++
                LobbySurvey.LAVA -> {
                    area.lava++
                    if (near) area.lavaNear++
                }
                LobbySurvey.WALL -> return@roomCells
            }
            area.open++
            area.light += room.light[index]
            val standable = kind == LobbySurvey.NATURAL || kind == LobbySurvey.BUILT
            if (standable && abs(room.floor[index] - height) <= ROOM_FLAT_RISE && room.headroom[index] >= STAND_HEADROOM) area.floor++
        }
        return area
    }

    private fun roomStage(room: LobbyRoom, cellX: Int, cellZ: Int, height: Int): Pair<Double, Double> {
        var cells = 0
        var flat = 0
        var head = 0
        roomCells(cellX, cellZ, ROOM_STAGE) { x, z ->
            val index = room.index(x, z)
            cells++
            val kind = room.kind[index]
            if ((kind == LobbySurvey.NATURAL || kind == LobbySurvey.BUILT) && abs(room.floor[index] - height) <= STAGE_FLAT_RISE) flat++
            if (kind != LobbySurvey.WALL && kind != LobbySurvey.UNLOADED && room.floor[index] + room.headroom[index] >= height + CAMERA_HEADROOM) head++
        }
        return flat.toDouble() / cells to head.toDouble() / cells
    }

    private fun cameraFits(room: LobbyRoom, cellX: Int, cellZ: Int, height: Int): Boolean = (0 until CAMERA_HEADINGS).any { heading ->
        val angle = heading * Math.PI * 2.0 / CAMERA_HEADINGS
        (1..CAMERA_REACH).all { step ->
            val x = cellX + (kotlin.math.cos(angle) * step).roundToInt()
            val z = cellZ + (kotlin.math.sin(angle) * step).roundToInt()
            if (abs(x) > LobbyRoom.RADIUS || abs(z) > LobbyRoom.RADIUS) return@all false
            val index = room.index(x, z)
            val kind = room.kind[index]
            kind != LobbySurvey.WALL && kind != LobbySurvey.UNLOADED &&
                room.floor[index] <= height + CAMERA_STEP_UP &&
                room.floor[index] + room.headroom[index] >= height + CAMERA_HEADROOM
        }
    }

    private inline fun roomCells(cellX: Int, cellZ: Int, radius: Int, visit: (Int, Int) -> Unit) {
        val squared = radius * radius
        for (x in maxOf(-LobbyRoom.RADIUS, cellX - radius)..minOf(LobbyRoom.RADIUS, cellX + radius)) {
            for (z in maxOf(-LobbyRoom.RADIUS, cellZ - radius)..minOf(LobbyRoom.RADIUS, cellZ + radius)) {
                if ((x - cellX) * (x - cellX) + (z - cellZ) * (z - cellZ) <= squared) visit(x, z)
            }
        }
    }
}
