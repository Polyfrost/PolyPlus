package org.polyfrost.polyplus.client.lobby

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.level.BlockGetter
//? if > 1.8.9
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
//? if > 1.8.9
import net.minecraft.world.phys.shapes.CollisionContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class LobbySlot(val pos: Vec3, val yaw: Float)

data class LobbyShot(val yaw: Float, val pitch: Float)

data class LobbyStage(
    val anchor: Vec3,
    val aim: Vec3,
    val yaw: Float,
    val pitch: Float,
    val distance: Double,
    val slots: List<LobbySlot>,
    val lanterns: List<BlockPos>,
    val craneOrbit: Double,
    val craneDistance: Double,
    val quality: Double,
    val spot: BlockPos,
    val heading: Double,
    val background: List<LobbySlot> = emptyList(),
    val cranePitch: Double = CRANE_PITCH,
    val interior: Boolean = false,
) {
    fun forward(): Vec3 {
        val radians = Math.toRadians(yaw.toDouble())
        return Vec3(-sin(radians), 0.0, cos(radians))
    }

    fun right(): Vec3 {
        val forward = forward()
        return Vec3(-forward.z, 0.0, forward.x)
    }

    fun camera(): Vec3 {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        return aim.subtract(Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad)).scale(distance))
    }

    fun portraitShot(level: BlockGetter): LobbyShot {
        val head = slots.first().pos.add(0.0, PORTRAIT_HEAD, 0.0)
        for (orbit in PORTRAIT_ORBITS) {
            for (tilt in PORTRAIT_PITCHES) {
                val shotYaw = yaw + orbit
                val yawRad = Math.toRadians(shotYaw.toDouble())
                val pitchRad = Math.toRadians(tilt.toDouble())
                val back = Vec3(sin(yawRad) * cos(pitchRad), sin(pitchRad), -cos(yawRad) * cos(pitchRad))
                val camera = head.add(back.scale(PORTRAIT_REACH))
                if (reach(level, camera, head) >= PORTRAIT_REACH - 0.25) return LobbyShot(shotYaw, tilt)
            }
        }
        return LobbyShot(yaw, pitch)
    }

    fun toTag(): CompoundTag = CompoundTag().apply {
        putIntArray("spot", intArrayOf(spot.x, spot.y, spot.z))
        putDouble("heading", heading)
        putFloat("pitch", pitch)
        putDouble("distance", distance)
        if (interior) putByte("interior", 1)
    }

    private class Solver(val level: BlockGetter, val interior: Boolean = false) {
        val shots = if (interior) INTERIOR_SHOTS else SHOTS
        val base = if (interior) INTERIOR_BASE else SHOTS.first()

        private val standing = HashMap<Long, Boolean>()
        private val open = HashMap<Long, Boolean>()
        private val walkable = HashMap<Long, Set<Long>>()

        fun canStand(pos: BlockPos): Boolean = standing.getOrPut(pos.asLong()) { LobbyStage.canStand(level, pos) && firmGround(level, pos) }

        fun surface(near: BlockPos, above: Int, below: Int): BlockPos? =
            (above downTo -below).firstNotNullOfOrNull { dy -> near.offset(0, dy, 0).takeIf(::canStand) }

        fun open(pos: BlockPos): Boolean = open.getOrPut(pos.asLong()) {
            if (interior) (2..INTERIOR_HEADROOM).all { clear(level, pos.above(it)) } else (2..SKY_CHECK).all { level.getBlockState(pos.above(it)).isAir }
        }

        fun settled(cell: BlockPos, loose: Boolean): Boolean {
            val below = cell.below()
            val ground = level.getBlockState(below)
            val shape = ground.getCollisionShape(level, below)
            if (ground.`is`(BlockTags.STAIRS)) return false
            if (shape.max(Direction.Axis.X) - shape.min(Direction.Axis.X) < WIDE_GROUND) return false
            if (shape.max(Direction.Axis.Z) - shape.min(Direction.Axis.Z) < WIDE_GROUND) return false
            val head = level.getBlockState(cell.above())
            if (level.getBlockState(cell).lightEmission > 0 || !(head.isAir || head.canBeReplaced())) return false
            val support = Direction.Plane.HORIZONTAL.count { surface(cell.relative(it), 1, 1) != null }
            return support >= if (loose) LOOSE_SUPPORT else FIRM_SUPPORT
        }

        fun walkable(spot: BlockPos): Set<Long> = walkable.getOrPut(spot.asLong()) { flood(spot) }

        fun ground(near: BlockPos, rise: Int, reachable: Set<Long>): BlockPos? =
            (0..rise).firstNotNullOfOrNull { dy ->
                near.above(dy).takeIf { it.asLong() in reachable } ?: near.below(dy).takeIf { it.asLong() in reachable }
            }

        private fun flood(spot: BlockPos): Set<Long> {
            val found = hashSetOf(spot.asLong())
            val queue = ArrayDeque(listOf(spot))
            while (queue.isNotEmpty() && found.size < WALK_CELLS) {
                val cell = queue.removeFirst()
                for (direction in Direction.Plane.HORIZONTAL) {
                    val flat = cell.relative(direction)
                    if (hypot((flat.x - spot.x).toDouble(), (flat.z - spot.z).toDouble()) > WALK_REACH) continue
                    val next = listOf(flat, flat.above(), flat.below()).firstOrNull(::canStand) ?: continue
                    if (found.add(next.asLong())) queue += next
                }
            }
            return found
        }
    }

    private class Box(val left: Double, val top: Double, val right: Double, val bottom: Double) {
        fun hits(other: Box): Boolean = left < other.right && right > other.left && top < other.bottom && bottom > other.top

        fun grown(margin: Double): Box = Box(left - margin, top - margin, right + margin, bottom + margin)

        fun swayed(): Box = Box(left - SWAY_PIXELS, top, right + SWAY_PIXELS, bottom)
    }

    private class Figure(val body: Box, val head: Box, val label: Box) {
        val whole = Box(minOf(body.left, label.left), label.top, maxOf(body.right, label.right), body.bottom)

        fun clashes(other: Figure): Boolean =
            body.hits(other.body) || label.hits(other.label.grown(TAG_GAP)) || label.hits(other.head) || head.hits(other.label)
    }

    private class Frame(val spot: BlockPos, val heading: Double, val pitchDegrees: Double, val distance: Double, base: Pair<Double, Double> = SHOTS.first()) {
        val forwardX = sin(heading)
        val forwardZ = cos(heading)
        val pitch = Math.toRadians(pitchDegrees)
        val spread = distance / base.second
        val rows = spread * sin(Math.toRadians(base.first)) / sin(pitch)

        fun project(lateral: Double, depth: Double, y: Double): Vec3 = Vec3(
            spot.x + 0.5 + forwardZ * lateral + forwardX * depth,
            y,
            spot.z + 0.5 - forwardX * lateral + forwardZ * depth,
        )

        fun member(lateral: Double, depth: Double, y: Double): Vec3 = project(lateral * spread, depth * rows, y)

        fun depthOf(cell: BlockPos): Double = (cell.x - spot.x) * forwardX + (cell.z - spot.z) * forwardZ

        fun aim(floor: Double): Vec3 = project(AIM_LATERAL, AIM_SHIFT, floor + AIM_HEIGHT)

        fun camera(aim: Vec3): Vec3 = aim.add(-forwardX * cos(pitch) * distance, sin(pitch) * distance, -forwardZ * cos(pitch) * distance)
    }

    private class Candidate(val frame: Frame, val quality: Double, val bonus: Double)

    private class Gathering(val solver: Solver, val frame: Frame, val view: LobbyEntrance.View, val reachable: Set<Long>) {
        private val level = solver.level
        val cells = ArrayList<BlockPos>(PARTY_MAX)
        private val figures = ArrayList<Figure>(PARTY_MAX)

        fun fits(cell: BlockPos, loose: Boolean): Boolean {
            val feet = feetOf(level, cell)
            val player = cells.firstOrNull()
            if (player == null) {
                if (!solver.open(cell)) return false
            } else {
                if (loose && !level.getBlockState(cell.above(2)).isAir) return false
                if (!loose && !solver.open(cell)) return false
                if (cells.any { planar(it, cell) < PARTY_SPACE } || frame.depthOf(cell) < FRONT_LIMIT) return false
                if (abs(feet.y - feetOf(level, player).y) > if (loose) LOOSE_RISE else PARTY_RISE) return false
                if (!solver.settled(cell, loose)) return false
            }
            val figure = figure(view, feet)
            return !underInterface(figure) && figures.none(figure::clashes) && seen(level, view, feet)
        }

        fun add(cell: BlockPos) {
            cells += cell
            figures += figure(view, feetOf(level, cell))
        }

        fun place(lateral: Double, depth: Double): BlockPos? {
            val near = LobbyCompat.blockAt(frame.member(lateral, depth, frame.spot.y.toDouble()))
            return spiral(SLOT_SEARCH) { dx, dz ->
                solver.ground(near.offset(dx, 0, dz), SLOT_RISE, reachable)?.takeIf { fits(it, false) }
            }?.also(::add)
        }

        fun fill(loose: Boolean) {
            val player = cells.first()
            val nearby = ArrayList<BlockPos>()
            for (dx in -FILL_REACH..FILL_REACH) {
                for (dz in -FILL_REACH..FILL_REACH) {
                    solver.ground(player.offset(dx, 0, dz), SLOT_RISE, reachable)?.let(nearby::add)
                }
            }
            for (cell in nearby.sortedBy { planar(it, player) }) {
                if (cells.size >= PARTY_MAX) return
                if (fits(cell, loose)) add(cell)
            }
        }

        fun feet(): List<Vec3> {
            val player = cells.first()
            val waiting = cells.drop(1).toMutableList()
            val friends = ArrayList<BlockPos>(waiting.size)
            while (waiting.isNotEmpty()) {
                val next = waiting.minBy { joinCost(it, player, friends) }
                waiting -= next
                friends += next
            }
            return (listOf(player) + friends).map { feetOf(level, it) }
        }

        private fun joinCost(cell: BlockPos, player: BlockPos, friends: List<BlockPos>): Double {
            val row = friends.count { abs(frame.depthOf(it) - frame.depthOf(cell)) < ROW_DEPTH }
            return planar(cell, player) + abs(cell.y - player.y) * STEP_COST + (row - 1).coerceAtLeast(0) * ROW_COST
        }
    }

    companion object {
        private const val AIM_HEIGHT = 2.4
        private const val AIM_SHIFT = 2.0
        private const val AIM_LATERAL = 1.0
        private const val YAW_CANDIDATES = 24
        private const val BACKDROP_REACH = 72.0
        private const val SPOT_SEARCH_RADIUS = 18
        private const val SPOT_STEP = 2
        private const val OPEN_AREA_RADIUS = 6
        private const val TOP_SPOTS = 16
        private const val ANCHOR_DISTANCE_COST = 0.3
        private const val PORTRAIT_HEAD = 1.4
        private const val PORTRAIT_REACH = 9.0
        private const val SLOT_SEARCH = 2
        private const val SLOT_RISE = 3
        private const val PARTY_MIN = 4
        private const val PARTY_MAX = 9
        private const val CROWD_MAX = 6
        private const val CROWD_GAP = 3.5
        private const val CROWD_REACH = 12
        private const val CROWD_WIDTH = 14
        private const val CROWD_SEARCH = 2
        private const val CROWD_RISE = 4
        private const val CROWD_SPACE = 3.0
        private const val CROWD_TURN = 0.45f
        private const val CROWD_TRIES = 500
        private const val PARTY_LABEL_WIDTH = 165.0
        private const val PARTY_SPACE = 1.7
        private const val PARTY_RISE = 1.6
        private const val LOOSE_RISE = 2.1
        private const val STEP_COST = 0.8
        private const val ROW_DEPTH = 1.8
        private const val ROW_COST = 2.5
        private const val FRONT_LIMIT = -0.75
        private const val WIDE_GROUND = 0.6
        private const val FIRM_SUPPORT = 3
        private const val LOOSE_SUPPORT = 2
        private const val FILL_REACH = 9
        private const val TURN_IN = 0.3f
        private const val TAG_GAP = 4.0
        private const val PARTY_WEIGHT = 7.0
        private const val FINAL_FRAMES = 30
        private const val SHOT_DISTANCE_COST = 1.6
        private const val SHOT_PITCH_COST = 0.5
        private const val DRIFT_COST = 1.4
        private const val RISE_COST = 1.1
        private const val SCREEN_WIDTH = 1920.0
        private const val SCREEN_HEIGHT = 1080.0
        private const val LABEL_HEIGHT = 40.0
        private const val BODY_WIDTH = 0.9
        private const val HEAD_HEIGHT = 1.9
        private const val HEAD_SIZE = 0.55
        private const val LABEL_LIFT = 2.22
        private const val SWAY_PIXELS = 26.0
        private const val BACKDROP_WEIGHT = 40.0
        private const val BLOCKED_NEAR_COST = 9.0
        private const val OPEN_WEIGHT = 0.12
        const val CAMERA_FOV = 34.0
        private const val ASPECT = 16.0 / 9.0
        private const val VISIBLE_SLACK = 0.45
        private const val LANTERN_GAP = 1.6
        private const val POST_HEIGHT = 3.0
        const val CRANE_PITCH = 46.0
        private const val CRANE_SAMPLES = 24
        private const val CRANE_MARGIN = 1.5
        private const val SKY_CHECK = 24
        private const val SKY_RADIUS = 5
        private const val MIN_SKY_SHARE = 0.7
        private const val WALK_REACH = 46.0
        private const val WALK_CELLS = 9000
        private const val FOREGROUND_SHARE = 0.62
        private const val MAX_LEAF_SHARE = 0.3
        private const val MAX_NEAR_SHARE = 0.35
        private const val GROUND_RAY = -0.4
        private const val INTERIOR_HEADROOM = 3
        private const val INTERIOR_FOREGROUND_SHARE = 0.45
        private const val INTERIOR_NEAR_SHARE = 0.6

        private val PORTRAIT_ORBITS = listOf(8f, -8f, 20f, -20f, 34f, -34f, 50f, -50f)
        private val PORTRAIT_PITCHES = listOf(5f, 9f, 14f, 20f)
        private val SHOTS = listOf(16.0 to 18.5, 20.0 to 18.5, 12.0 to 20.5, 24.0 to 17.0, 16.0 to 22.0)
        private val INTERIOR_SHOTS = listOf(10.0 to 10.5, 8.0 to 9.0, 14.0 to 12.0, 6.0 to 8.0, 12.0 to 14.0)
        private val INTERIOR_BASE = 10.0 to 14.0
        private val CRANE_REACH = listOf(46.0, 38.0, 30.0, 24.0)
        private val CRANE_ORBITS = listOf(38.0, -38.0, 22.0, -22.0, 0.0)
        private val SIGHT_POINTS = listOf(
            0.0 to 1.75,
            -0.3 to 1.3,
            0.3 to 1.3,
            0.0 to 0.8,
            -0.25 to 0.15,
            0.25 to 0.15,
            0.0 to 0.04,
        )
        private val BACKDROP_RAYS = listOf(
            -0.8 to 0.75, -0.4 to 0.75, 0.0 to 0.75, 0.4 to 0.75, 0.8 to 0.75,
            -0.8 to 0.35, -0.4 to 0.35, 0.0 to 0.35, 0.4 to 0.35, 0.8 to 0.35,
            -0.8 to -0.05, -0.4 to -0.05, 0.0 to -0.05, 0.4 to -0.05, 0.8 to -0.05,
            -0.5 to -0.45, 0.0 to -0.45, 0.5 to -0.45,
        )

        private val PERSISTENT_UI = listOf(
            Box(0.0, 0.0, SCREEN_WIDTH, 84.0),
            Box(1760.0, 84.0, SCREEN_WIDTH, 142.0),
            Box(0.0, 750.0, 456.0, SCREEN_HEIGHT),
            Box(0.0, 950.0, SCREEN_WIDTH, SCREEN_HEIGHT),
            Box(1454.0, 626.0, SCREEN_WIDTH, SCREEN_HEIGHT),
        )

        private val PARTY_SPOTS = listOf(
            0.0 to 0.0,
            2.2 to 0.5,
            -2.1 to 0.9,
            1.2 to 2.7,
            -1.25 to 3.1,
            4.3 to 2.0,
            2.8 to 5.0,
            -2.7 to 5.3,
            0.1 to 5.6,
        )

        private val CROWD_SPOTS = listOf(
            -4.5 to 0.5,
            5.0 to 1.5,
            -9.0 to 2.5,
            9.5 to 0.5,
            0.5 to 4.0,
            -6.0 to 6.0,
            6.5 to 6.5,
        )

        private val LANTERN_OFFSETS = listOf(
            -7.0 to 1.0,
            7.5 to 0.5,
            -5.0 to 4.4,
            6.0 to 5.0,
            -9.0 to 7.5,
            8.5 to 8.0,
        )

        fun build(level: BlockGetter, anchor: BlockPos, interior: Boolean = false): LobbyStage? {
            val solver = Solver(level, interior)
            val frames = ArrayList<Candidate>()
            for ((spot, open) in openSpots(solver, anchor)) {
                val drift = hypot((spot.x - anchor.x).toDouble(), (spot.z - anchor.z).toDouble()) * ANCHOR_DISTANCE_COST
                for (index in 0 until YAW_CANDIDATES) {
                    val heading = index * Math.PI * 2.0 / YAW_CANDIDATES
                    for ((pitch, distance) in solver.shots) {
                        val frame = Frame(spot, heading, pitch, distance, solver.base)
                        val stage = solve(solver, frame, PARTY_MIN, false) ?: continue
                        val bonus = open * OPEN_WEIGHT - drift
                        frames += Candidate(frame, stage.quality + bonus, bonus)
                    }
                }
            }
            val best = frames.sortedByDescending { it.quality }.take(FINAL_FRAMES).mapNotNull { candidate ->
                solve(solver, candidate.frame, PARTY_MIN, true)?.let { it.copy(quality = it.quality + candidate.bonus) }
            }.maxByOrNull { it.quality }
            return best?.let { finish(solver, it) }
        }

        fun restore(level: BlockGetter, tag: CompoundTag): LobbyStage? {
            val spot = tag.optIntArray("spot") ?: return null
            val solver = Solver(level, tag.optByte("interior") == 1.toByte())
            val frame = Frame(
                BlockPos(spot[0], spot[1], spot[2]),
                tag.getDoubleOr("heading", 0.0),
                tag.getFloatOr("pitch", 16f).toDouble(),
                tag.getDoubleOr("distance", 18.5),
                solver.base,
            )
            return solve(solver, frame, 1, true)?.let { finish(solver, it) }
        }

        private fun solve(solver: Solver, frame: Frame, required: Int, complete: Boolean): LobbyStage? {
            val level = solver.level
            val spot = frame.spot
            val draft = frame.camera(frame.aim(spot.y.toDouble()))
            if (!clearAir(level, draft)) return null
            val reachable = solver.walkable(spot)
            val sketch = Gathering(solver, frame, viewOf(frame, draft), reachable)
            var drift = 0.0
            for ((index, offset) in PARTY_SPOTS.withIndex()) {
                val (lateral, depth) = offset
                val cell = sketch.place(lateral, depth)
                if (cell == null) {
                    if (index < required) return null
                    continue
                }
                val wanted = frame.member(lateral, depth, 0.0)
                drift += hypot(cell.x + 0.5 - wanted.x, cell.z + 0.5 - wanted.z)
            }
            val rest = sketch.cells.take(PARTY_MIN).map { feetOf(level, it).y }.average()
            val aim = frame.aim(rest)
            val camera = frame.camera(aim)
            if (!clearAir(level, camera)) return null
            val view = viewOf(frame, camera)
            val final = Gathering(solver, frame, view, reachable)
            for ((index, cell) in sketch.cells.withIndex()) {
                if (final.fits(cell, false)) final.add(cell) else if (index < required) return null
            }
            if (complete) {
                final.fill(false)
                final.fill(true)
            }
            val backdrop = backdrop(level, view, frame.distance, solver.interior) ?: return null
            val feet = final.feet()
            val player = feet.first()
            val slots = feet.mapIndexed { index, pos ->
                val front = lookYaw(pos, camera)
                LobbySlot(pos, if (index == 0) front else front + Mth.wrapDegrees(lookYaw(pos, player) - front) * TURN_IN)
            }
            val rise = feet.sumOf { abs(it.y - rest) }
            val shot = solver.shots.first()
            val shotCost = abs(frame.distance - shot.second) * SHOT_DISTANCE_COST + abs(frame.pitchDegrees - shot.first) * SHOT_PITCH_COST
            val quality = backdrop * BACKDROP_WEIGHT - drift * DRIFT_COST - rise * RISE_COST + feet.size * PARTY_WEIGHT - shotCost
            return LobbyStage(
                Vec3(spot.x + 0.5, spot.y.toDouble(), spot.z + 0.5),
                aim,
                yawOf(frame).toFloat(),
                frame.pitchDegrees.toFloat(),
                frame.distance,
                slots,
                emptyList(),
                0.0,
                frame.distance,
                quality,
                spot,
                frame.heading,
                interior = solver.interior,
            )
        }

        private fun yawOf(frame: Frame): Double = Math.toDegrees(atan2(-frame.forwardX, frame.forwardZ))

        private fun viewOf(frame: Frame, camera: Vec3): LobbyEntrance.View =
            LobbyEntrance.view(camera, yawOf(frame), frame.pitchDegrees, CAMERA_FOV.toFloat(), ASPECT)

        private fun feetOf(level: BlockGetter, cell: BlockPos): Vec3 = Vec3(cell.x + 0.5, floor(level, cell), cell.z + 0.5)

        private inline fun <T : Any> spiral(reach: Int, visit: (Int, Int) -> T?): T? {
            for (radius in 0..reach) {
                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (maxOf(abs(dx), abs(dz)) == radius) visit(dx, dz)?.let { return it }
                    }
                }
            }
            return null
        }

        private fun planar(a: BlockPos, b: BlockPos): Double = hypot((a.x - b.x).toDouble(), (a.z - b.z).toDouble())

        private fun seen(level: BlockGetter, view: LobbyEntrance.View, feet: Vec3): Boolean = SIGHT_POINTS.all { (side, height) ->
            val point = feet.add(view.right.scale(side)).add(0.0, height, 0.0)
            reach(level, view.camera, point) >= view.camera.distanceTo(point) - VISIBLE_SLACK
        }

        private fun clearAir(level: BlockGetter, point: Vec3): Boolean {
            val pos = LobbyCompat.blockAt(point)
            //? if >= 1.21.4 {
            return Direction.entries.all { !level.getBlockState(pos.relative(it)).isSolidRender } && level.getBlockState(pos).isAir
            //?} elif > 1.8.9 {
            /*return Direction.entries.all { !level.getBlockState(pos.relative(it)).isSolidRender(level, pos.relative(it)) } && level.getBlockState(pos).isAir
            *///?} else {
            /*return Direction.entries.all { !level.getBlockState(pos.relative(it)).block.isOpaque } && level.getBlockState(pos).isAir
            *///?}
        }

        private fun underInterface(figure: Figure): Boolean {
            val box = figure.whole
            if (box.left < SWAY_PIXELS || box.right > SCREEN_WIDTH - SWAY_PIXELS) return true
            val body = figure.body.swayed()
            val label = figure.label.swayed()
            return PERSISTENT_UI.any { body.hits(it) || label.hits(it) }
        }

        private fun figure(view: LobbyEntrance.View, feet: Vec3): Figure {
            val low = pixels(view, feet)
            val head = pixels(view, feet.add(0.0, HEAD_HEIGHT, 0.0))
            val tag = pixels(view, feet.add(0.0, LABEL_LIFT, 0.0))
            val tall = low.second - head.second
            val half = tall * BODY_WIDTH / HEAD_HEIGHT / 2.0
            val face = tall * HEAD_SIZE / HEAD_HEIGHT
            return Figure(
                Box(low.first - half, head.second, low.first + half, low.second),
                Box(low.first - face / 2.0, head.second, low.first + face / 2.0, head.second + face),
                Box(tag.first - PARTY_LABEL_WIDTH / 2.0, tag.second - LABEL_HEIGHT, tag.first + PARTY_LABEL_WIDTH / 2.0, tag.second),
            )
        }

        private fun depthOf(view: LobbyEntrance.View, point: Vec3): Double = point.subtract(view.camera).dot(view.look)

        private fun pixels(view: LobbyEntrance.View, point: Vec3): Pair<Double, Double> {
            val offset = point.subtract(view.camera)
            val depth = offset.dot(view.look).coerceAtLeast(0.1)
            val x = offset.dot(view.right) / (depth * view.tanHalf * view.aspect)
            val y = offset.dot(view.up) / (depth * view.tanHalf)
            return (x + 1.0) * 0.5 * SCREEN_WIDTH to (1.0 - y) * 0.5 * SCREEN_HEIGHT
        }

        private fun backdrop(level: BlockGetter, view: LobbyEntrance.View, distance: Double, interior: Boolean): Double? {
            val foreground = if (interior) INTERIOR_FOREGROUND_SHARE else FOREGROUND_SHARE
            val nearShare = if (interior) INTERIOR_NEAR_SHARE else MAX_NEAR_SHARE
            var total = 0.0
            var leaves = 0
            var near = 0
            var raised = 0
            for ((x, y) in BACKDROP_RAYS) {
                val direction = view.look.add(view.right.scale(x * view.tanHalf * view.aspect)).add(view.up.scale(y * view.tanHalf)).normalize()
                val end = view.camera.add(direction.scale(BACKDROP_REACH))
                val hit = clip(level, view.camera, end)
                val length = if (hit.type == HitResult.Type.MISS) BACKDROP_REACH else view.camera.distanceTo(hit.location)
                if (length < distance * foreground) return null
                if (hit.type != HitResult.Type.MISS && canopy(level, hit.blockPos)) leaves++
                total += length / BACKDROP_REACH
                if (length < distance) total -= BLOCKED_NEAR_COST / BACKDROP_RAYS.size
                if (y > GROUND_RAY) {
                    raised++
                    if (length < distance) near++
                }
            }
            if (leaves > BACKDROP_RAYS.size * MAX_LEAF_SHARE || near > raised * nearShare) return null
            return total / BACKDROP_RAYS.size
        }

        private fun canopy(level: BlockGetter, pos: BlockPos): Boolean {
            val state = level.getBlockState(pos)
            //? if > 1.8.9
            if (state.`is`(Blocks.MANGROVE_ROOTS) || state.`is`(Blocks.MUDDY_MANGROVE_ROOTS)) return true
            return state.`is`(BlockTags.LEAVES) || state.`is`(BlockTags.LOGS) || (state.`is`(Blocks.SNOW) && level.getBlockState(pos.below()).`is`(BlockTags.LEAVES))
        }

        private fun finish(solver: Solver, stage: LobbyStage): LobbyStage {
            val level = solver.level
            val frame = Frame(stage.spot, stage.heading, stage.pitch.toDouble(), stage.distance, solver.base)
            val view = viewOf(frame, frame.camera(stage.aim))
            val lanterns = if (stage.interior) emptyList() else lanternCells(solver, frame, stage).filter { cell ->
                val post = post(view, cell)
                stage.slots.none { depthOf(view, it.pos) > depthOf(view, Vec3(cell.x + 0.5, cell.y.toDouble(), cell.z + 0.5)) && post.hits(figure(view, it.pos).body) }
            }
            val (orbit, reach, cranePitch) = if (stage.interior) Triple(0.0, stage.distance, stage.pitch.toDouble()) else crane(level, stage)
            return stage.copy(lanterns = lanterns, craneOrbit = orbit, craneDistance = reach, cranePitch = cranePitch, background = crowd(solver, frame, view, stage, lanterns))
        }

        private class Crowd(val solver: Solver, val frame: Frame, val view: LobbyEntrance.View, val stage: LobbyStage, val lanterns: List<BlockPos>) {
            private val level = solver.level
            private val front = stage.slots.map { LobbyCompat.blockAt(it.pos) }
            val line = front.maxOf(frame::depthOf) + CROWD_GAP
            val floor = stage.slots.first().pos.y
            val reachable = solver.walkable(stage.spot)
            val cells = ArrayList<BlockPos>(CROWD_MAX)
            private val figures = stage.slots.mapTo(ArrayList()) { figure(view, it.pos) }

            fun full(): Boolean = cells.size >= CROWD_MAX

            fun fits(cell: BlockPos): Boolean {
                if (frame.depthOf(cell) < line - 0.5) return false
                if (cells.any { planar(it, cell) < CROWD_SPACE } || lanterns.any { planar(it, cell) < LANTERN_GAP }) return false
                if (!level.getBlockState(cell.above(2)).isAir || !solver.settled(cell, true)) return false
                val feet = feetOf(level, cell)
                val figure = figure(view, feet)
                if (underInterface(figure) || figures.any(figure::clashes)) return false
                val depth = depthOf(view, feet)
                if (lanterns.any { depthOf(view, Vec3(it.x + 0.5, it.y.toDouble(), it.z + 0.5)) < depth && post(view, it).hits(figure.body) }) return false
                return seen(level, view, feet)
            }

            fun add(cell: BlockPos) {
                cells += cell
                figures += figure(view, feetOf(level, cell))
            }

            fun near(lateral: Double, depth: Double): BlockPos = LobbyCompat.blockAt(frame.project(lateral, line + depth, floor))

            fun slots(): List<LobbySlot> {
                val player = stage.slots.first().pos
                return cells.map { cell ->
                    val feet = feetOf(level, cell)
                    val facing = lookYaw(feet, view.camera)
                    LobbySlot(feet, facing + Mth.wrapDegrees(lookYaw(feet, player) - facing) * CROWD_TURN)
                }
            }
        }

        private fun crowd(solver: Solver, frame: Frame, view: LobbyEntrance.View, stage: LobbyStage, lanterns: List<BlockPos>): List<LobbySlot> {
            val crowd = Crowd(solver, frame, view, stage, lanterns)
            for ((lateral, depth) in CROWD_SPOTS) {
                if (crowd.full()) break
                spiral(CROWD_SEARCH) { dx, dz ->
                    solver.ground(crowd.near(lateral, depth).offset(dx, 0, dz), CROWD_RISE, crowd.reachable)?.takeIf(crowd::fits)
                }?.let(crowd::add)
            }
            if (!crowd.full()) {
                val spare = ArrayList<Pair<BlockPos, Double>>()
                for (lateral in -CROWD_WIDTH..CROWD_WIDTH) {
                    for (depth in 0..CROWD_REACH) {
                        val cell = solver.ground(crowd.near(lateral.toDouble(), depth.toDouble()), CROWD_RISE, crowd.reachable) ?: continue
                        spare += cell to depth + abs(lateral) * 0.25
                    }
                }
                for ((cell, _) in spare.sortedBy { it.second }.take(CROWD_TRIES)) {
                    if (crowd.full()) break
                    if (crowd.fits(cell)) crowd.add(cell)
                }
            }
            return crowd.slots()
        }

        private fun lanternCells(solver: Solver, frame: Frame, stage: LobbyStage): List<BlockPos> = LANTERN_OFFSETS.mapNotNull { (lateral, depth) ->
            val near = LobbyCompat.blockAt(frame.project(lateral, depth, stage.spot.y.toDouble()))
            solver.surface(near, SLOT_RISE, SLOT_RISE)?.takeIf { cell ->
                stage.slots.none { hypot(it.pos.x - cell.x - 0.5, it.pos.z - cell.z - 0.5) < LANTERN_GAP }
            }
        }

        private fun post(view: LobbyEntrance.View, cell: BlockPos): Box {
            val low = pixels(view, Vec3(cell.x + 0.5, cell.y.toDouble(), cell.z + 0.5))
            val high = pixels(view, Vec3(cell.x + 0.5, cell.y + POST_HEIGHT, cell.z + 0.5))
            val half = (low.second - high.second) / POST_HEIGHT / 2.0
            return Box(low.first - half, high.second, low.first + half, low.second)
        }

        private fun crane(level: BlockGetter, stage: LobbyStage): Triple<Double, Double, Double> {
            for (reach in CRANE_REACH) {
                for (orbit in CRANE_ORBITS) {
                    if ((0..CRANE_SAMPLES).all { craneClear(level, stage, orbit, reach, CRANE_PITCH, it.toDouble() / CRANE_SAMPLES) }) return Triple(orbit, reach, CRANE_PITCH)
                }
            }
            return Triple(0.0, stage.distance, stage.pitch.toDouble())
        }

        private fun craneClear(level: BlockGetter, stage: LobbyStage, orbit: Double, reach: Double, startPitch: Double, progress: Double): Boolean {
            val eased = 1.0 - (1.0 - progress) * (1.0 - progress) * (1.0 - progress)
            val distance = reach + (stage.distance - reach) * eased
            val pitch = Math.toRadians(startPitch + (stage.pitch - startPitch) * eased)
            val yaw = Math.toRadians(stage.yaw + orbit * (1.0 - eased))
            val look = Vec3(-sin(yaw) * cos(pitch), -sin(pitch), cos(yaw) * cos(pitch))
            val camera = stage.aim.subtract(look.scale(distance))
            return clearAir(level, camera) && reach(level, camera, stage.aim) >= distance - CRANE_MARGIN
        }

        private fun lookYaw(from: Vec3, to: Vec3): Float =
            Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z)).toFloat()

        private fun openSpots(solver: Solver, anchor: BlockPos): List<Pair<BlockPos, Int>> {
            val spots = ArrayList<Pair<BlockPos, Int>>()
            for (dx in -SPOT_SEARCH_RADIUS..SPOT_SEARCH_RADIUS step SPOT_STEP) {
                for (dz in -SPOT_SEARCH_RADIUS..SPOT_SEARCH_RADIUS step SPOT_STEP) {
                    val spot = solver.surface(anchor.offset(dx, 0, dz), 8, 8) ?: continue
                    if (skyShare(solver, spot) < MIN_SKY_SHARE) continue
                    spots += spot to openArea(solver, spot)
                }
            }
            return spots.sortedByDescending { it.second }.take(TOP_SPOTS)
        }

        private fun skyShare(solver: Solver, spot: BlockPos): Double {
            var cells = 0
            var open = 0
            for (dx in -SKY_RADIUS..SKY_RADIUS) {
                for (dz in -SKY_RADIUS..SKY_RADIUS) {
                    val cell = solver.surface(spot.offset(dx, 0, dz), 2, 2) ?: continue
                    cells++
                    if (solver.open(cell)) open++
                }
            }
            return if (cells == 0) 0.0 else open.toDouble() / cells
        }

        private fun openArea(solver: Solver, spot: BlockPos): Int {
            var open = 0
            for (dx in -OPEN_AREA_RADIUS..OPEN_AREA_RADIUS) {
                for (dz in -OPEN_AREA_RADIUS..OPEN_AREA_RADIUS) {
                    if ((-1..1).any { dy -> solver.canStand(spot.offset(dx, dy, dz)) }) open++
                }
            }
            return open
        }

        fun reach(level: BlockGetter, from: Vec3, to: Vec3): Double {
            val hit = clip(level, from, to)
            return if (hit.type == HitResult.Type.MISS) from.distanceTo(to) else from.distanceTo(hit.location)
        }

        //? if > 1.8.9 {
        private fun clip(level: BlockGetter, from: Vec3, to: Vec3): BlockHitResult =
            level.clip(ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()))
        //?} else {
        /*private fun clip(level: BlockGetter, from: Vec3, to: Vec3): BlockHitResult = LobbyClip.clip(level, from, to)
        *///?}

        fun canStand(level: BlockGetter, pos: BlockPos): Boolean {
            val below = level.getBlockState(pos.below())
            if (below.getCollisionShape(level, pos.below()).isEmpty) return false
            if (!level.getFluidState(pos.below()).isEmpty) return false
            return listOf(pos, pos.above()).all {
                level.getBlockState(it).getCollisionShape(level, it).isEmpty && level.getFluidState(it).isEmpty
            }
        }

        private fun clear(level: BlockGetter, pos: BlockPos): Boolean =
            level.getBlockState(pos).getCollisionShape(level, pos).isEmpty && level.getFluidState(pos).isEmpty

        private fun firmGround(level: BlockGetter, pos: BlockPos): Boolean {
            val below = level.getBlockState(pos.below())
            if (below.`is`(BlockTags.LEAVES) || below.`is`(BlockTags.FENCES) || below.`is`(BlockTags.WALLS)) return false
            return !below.`is`(Blocks.CACTUS) && !below.`is`(Blocks.MAGMA_BLOCK) && !below.`is`(BlockTags.CAMPFIRES)
        }

        private fun floor(level: BlockGetter, pos: BlockPos): Double {
            val below = pos.below()
            return below.y + level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y)
        }
    }
}
