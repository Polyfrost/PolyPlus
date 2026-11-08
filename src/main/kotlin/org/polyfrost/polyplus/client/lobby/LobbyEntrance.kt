package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
//? if > 1.8.9 {
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
//?}
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.block.state.BlockState
//? if > 1.8.9 {
import net.minecraft.world.phys.AABB
//?} else {
/*import net.minecraft.util.math.Box as AABB
*///?}
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimationParser
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.Emote
import java.util.Random
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

abstract class LobbyMotion(val startTick: Long) {
    open fun begin(avatar: LobbyAvatar) = Unit

    abstract fun advance(avatar: LobbyAvatar, elapsed: Long): Boolean

    open fun settle(avatar: LobbyAvatar) = Unit
}

class LobbyRoute(private val points: List<Vec3>) {
    private val lengths = points.zipWithNext { a, b -> hypot(b.x - a.x, b.z - a.z) }
    val length = lengths.sum()
    val start: Vec3 = points.first()

    fun at(distance: Double): Vec3 {
        var left = distance
        for ((index, segment) in lengths.withIndex()) {
            if (left <= segment && segment > 0.0) return points[index].lerp(points[index + 1], left / segment)
            left -= segment
        }
        return points.last()
    }

    fun heading(distance: Double): Float {
        val from = at(distance)
        val to = at(min(distance + LOOKAHEAD, length))
        if (hypot(to.x - from.x, to.z - from.z) < 0.01) return yawBetween(points[points.size - 2], points.last())
        return yawBetween(from, to)
    }

    private fun yawBetween(from: Vec3, to: Vec3): Float = Math.toDegrees(atan2(-(to.x - from.x), to.z - from.z)).toFloat()

    companion object {
        private const val LOOKAHEAD = 1.4
    }
}

private class RunIn(start: Long, private val level: ClientLevel, private val route: LobbyRoute, private val turning: Boolean = false) : LobbyMotion(start) {
    private var travelled = 0.0
    private var speed = 0.0
    private var yaw = 0f
    private var lift = 0.0
    private var stuck = 0
    private var arrived = false

    override fun begin(avatar: LobbyAvatar) {
        yaw = if (turning) avatar.yRot else route.heading(0.0)
        avatar.place(route.start, yaw)
    }

    override fun advance(avatar: LobbyAvatar, elapsed: Long): Boolean {
        if (arrived) return true
        val remaining = route.length - travelled
        speed = min(speed + ACCELERATION, min(LobbyEntrance.RUN_SPEED, remaining * BRAKE + CREEP))
        val target = min(route.length, travelled + speed)
        val spot = route.at(target)
        val from = avatar.position()
        if (avatar.onGround() && jumpDue(avatar, from)) lift = avatar.leap()
        avatar.walk(Vec3(spot.x - from.x, lift, spot.z - from.z))
        if (avatar.bumpedVertically) lift = 0.0
        lift = (lift - avatar.fallRate) * DRAG
        val to = avatar.position()
        val progress = hypot(to.x - from.x, to.z - from.z)
        travelled = if (avatar.bumped) min(target, travelled + progress) else target
        val moving = remaining > 0.01
        stuck = if (moving && progress < STALL) stuck + 1 else 0
        val going = moving && stuck <= STUCK_TICKS
        val wanted = if (going) route.heading(travelled) else avatar.rest.yaw
        yaw += Mth.wrapDegrees(wanted - yaw).coerceIn(-TURN_RATE, TURN_RATE)
        avatar.place(to, yaw)
        avatar.stride(if (moving) Vec3(to.x - from.x, 0.0, to.z - from.z) else null)
        if (going || !avatar.onGround() || abs(Mth.wrapDegrees(avatar.rest.yaw - yaw)) > TURN_DONE) return false
        avatar.place(avatar.rest.pos, avatar.rest.yaw)
        arrived = true
        return false
    }

    private fun jumpDue(avatar: LobbyAvatar, from: Vec3): Boolean {
        var feet = from.y
        var ahead = PROBE
        var due = false
        while (ahead <= REACH_TICKS * speed && travelled + ahead <= route.length) {
            val at = route.at(travelled + ahead)
            val top = level.obstacle(at.x, at.z, feet)
            if (top == null) {
                feet = level.ground(at.x, at.z, feet, LOOK_DROP) ?: break
            } else if (top - feet <= avatar.climb && level.obstacle(at.x, at.z, top) == null) {
                feet = top
            } else {
                val rise = top - from.y
                val room = level.obstacle(at.x, at.z, top) == null && level.obstacle(from.x, from.z, from.y, BODY_HEIGHT + rise + HEADROOM) == null
                due = rise <= MAX_RISE && room && ahead <= (riseTicks(rise) + 1) * speed
                break
            }
            ahead += PROBE
        }
        return due
    }

    companion object {
        private const val ACCELERATION = 0.09
        private const val BRAKE = 0.3
        private const val CREEP = 0.05
        private const val TURN_RATE = 32f
        private const val TURN_DONE = 6f
        private const val DRAG = 0.98
        private const val PROBE = 0.05
        private const val REACH_TICKS = 7.0
        private const val LOOK_DROP = 0.5
        private const val STALL = 0.005
        private const val STUCK_TICKS = 40
        private const val JUMP_LIFT = 0.42
        private const val JUMP_FALL = 0.08
        private const val MAX_ARC_TICKS = 12

        private fun riseTicks(rise: Double): Int {
            var height = 0.0
            var lift = JUMP_LIFT
            var ticks = 0
            while (height < rise && ticks < MAX_ARC_TICKS) {
                height += lift
                lift = (lift - JUMP_FALL) * DRAG
                ticks++
            }
            return ticks
        }
    }
}

private const val HALF_WIDTH = 0.3
private const val BODY_HEIGHT = 1.8
private const val PLAYER_STEP = 0.6
private const val MAX_RISE = 1.2
private const val HEADROOM = 0.15
private const val SNAP = 1.0e-3

private fun ClientLevel.obstacle(x: Double, z: Double, feet: Double, height: Double = BODY_HEIGHT): Double? =
    highest(AABB(x - HALF_WIDTH, feet + SNAP, z - HALF_WIDTH, x + HALF_WIDTH, feet + height, z + HALF_WIDTH))

private fun ClientLevel.ground(x: Double, z: Double, feet: Double, depth: Double): Double? =
    highest(AABB(x - HALF_WIDTH, feet - depth, z - HALF_WIDTH, x + HALF_WIDTH, feet + SNAP, z + HALF_WIDTH))

private fun ClientLevel.highest(box: AABB): Double? {
    //? if > 1.8.9 {
    return getBlockCollisions(null, box).flatMap { it.toAabbs() }.filter { it.intersects(box) }.maxOfOrNull { it.maxY }
    //?} else {
    /*return getBlockCollisions(box).maxOfOrNull { it.maxY }
    *///?}
}

private class DropIn(start: Long, private val level: ClientLevel, private val height: Double) : LobbyMotion(start) {
    private var fall = START_FALL
    private var offset = height
    private var crouching = 0

    override fun begin(avatar: LobbyAvatar) {
        avatar.place(avatar.rest.pos.add(0.0, offset, 0.0), avatar.rest.yaw)
        LobbyEntrance.fallPose()?.let { (avatar as PlayerEmotesAccess).`polyplus$emoteController`().play(it) }
    }

    override fun advance(avatar: LobbyAvatar, elapsed: Long): Boolean {
        fall += GRAVITY
        offset -= fall
        if (offset > 0.0) {
            avatar.place(avatar.rest.pos.add(0.0, offset, 0.0), avatar.rest.yaw)
            return false
        }
        avatar.place(avatar.rest.pos, avatar.rest.yaw)
        (avatar as PlayerEmotesAccess).`polyplus$emoteController`().stop()
        avatar.pose = Pose.CROUCHING
        crouching = CROUCH_TICKS
        LobbyEntrance.land(level, avatar.rest.pos, height)
        return true
    }

    override fun settle(avatar: LobbyAvatar) {
        if (crouching <= 0 || --crouching > 0) return
        avatar.pose = Pose.STANDING
        LobbyEntrance.wave(avatar)
    }

    companion object {
        private const val START_FALL = 1.1
        private const val GRAVITY = 0.16
        private const val CROUCH_TICKS = 7
    }
}

object LobbyEntrance {
    data class View(val camera: Vec3, val look: Vec3, val right: Vec3, val up: Vec3, val tanHalf: Double, val aspect: Double)

    const val RUN_SPEED = 0.27

    private val ANIMATIONS by lazy {
        LobbyEntrance::class.java.getResourceAsStream("/assets/polyplus/lobby/entrance.animation.json")?.use(BedrockAnimationParser::parseStream)?.animations.orEmpty()
    }

    private const val FIRST_TICK = 24L
    private const val FIRST_STOP_TICK = 46L
    private const val STAGGER_TICKS = 5L
    private const val STOP_MARGIN_TICKS = 6L
    private const val MIN_ROUTE_STEPS = 7
    private const val MAX_ROUTE_STEPS = 16
    private const val SEARCH_LIMIT = 2400
    private const val WALK_LIMIT = 6000
    private const val START_GAP = 3
    private const val FRAME_PAD = 1.02
    private const val SLOT_CLEARANCE = 0.85
    private const val LINE_STEP = 0.3
    private const val MAX_DROP = 1.6
    private const val LEVEL_MATCH = 0.01
    private const val DROP_HEIGHT = 15

    private const val SAFE_FALL = 3.0
    private const val LANDING_PARTICLES = 150.0
    private const val LANDING_BASE = 0.2
    private const val LANDING_SPAN = 15.0
    private const val LANDING_CAP = 2.5
    private const val LANDING_SPEED = 0.15

    private val VIEW_TIMES = listOf(1.1, 1.8, 2.6, 5.0)
    private val BODY_HEIGHTS = listOf(0.2, 1.0, 1.8)

    private val waveEmote by lazy { emote("wave") }
    private val fallEmote by lazy { emote("fall") }

    private fun emote(name: String): Emote? = ANIMATIONS["animation.lobby.$name"]?.let {
        Emote(Identifier.fromNamespaceAndPath("polyplus", "lobby/$name"), it)
    }

    fun wave(avatar: LobbyAvatar) {
        val emote = waveEmote ?: return
        (avatar as PlayerEmotesAccess).`polyplus$emoteController`().play(emote)
        avatar.gaze.wave((emote.animation.lengthTicks / BedrockConstants.TICKS_PER_SECOND).toDouble())
    }

    fun fallPose(): Emote? = fallEmote

    fun plan(level: ClientLevel, avatars: List<LobbyAvatar>, standing: List<LobbyAvatar>, fromTick: Long = 0L, viewAt: (Double) -> View) {
        val planner = Planner(level, standing, viewAt)
        var dropped = 0
        avatars.forEachIndexed { index, avatar ->
            val route = planner.fromCover(avatar)
            if (route == null) {
                dropped++
                avatar.begin(DropIn(fromTick + FIRST_TICK + index * STAGGER_TICKS, level, headroom(level, avatar.rest.pos)))
            } else {
                val ticks = (route.length / RUN_SPEED).toLong() + STOP_MARGIN_TICKS
                avatar.begin(RunIn(fromTick + (FIRST_STOP_TICK + index * STAGGER_TICKS - ticks).coerceAtLeast(FIRST_TICK), level, route))
            }
        }
        LOBBY_LOGGER.debug("Entrance: {} run in, {} drop in", avatars.size - dropped, dropped)
    }

    fun walk(level: ClientLevel, avatar: LobbyAvatar, slot: LobbySlot, standing: List<LobbyAvatar>, fromTick: Long, viewAt: (Double) -> View) {
        val from = avatar.position()
        avatar.moveTo(slot)
        val route = Planner(level, standing, viewAt).between(avatar, from) ?: LobbyRoute(listOf(from, slot.pos))
        avatar.travel(RunIn(fromTick, level, route, turning = true))
    }

    private fun headroom(level: ClientLevel, feet: Vec3): Double {
        val base = LobbyCompat.blockAt(feet)
        val blocked = (2..DROP_HEIGHT).firstOrNull { !level.getBlockState(base.above(it)).getCollisionShape(level, base.above(it)).isEmpty }
        return (blocked ?: DROP_HEIGHT).toDouble()
    }

    fun land(level: ClientLevel, feet: Vec3, fallen: Double) {
        val power = (fallen - SAFE_FALL).toInt()
        if (power <= 0 || !LobbyScene.particlesAllowed()) return
        val count = (LANDING_PARTICLES * min(LANDING_BASE + power / LANDING_SPAN, LANDING_CAP)).toInt()
        val engine = Minecraft.getInstance().particleEngine
        val random = Random()
        //? if > 1.8.9
        val particle = BlockParticleOption(ParticleTypes.BLOCK, groundState(level, feet))
        //? if = 1.8.9
        /*val state = groundState(level, feet)*/
        repeat(count) {
            val vx = random.nextGaussian() * LANDING_SPEED
            val vy = random.nextGaussian() * LANDING_SPEED
            val vz = random.nextGaussian() * LANDING_SPEED
            //? if > 1.8.9 {
            engine.createParticle(particle, feet.x, feet.y, feet.z, vx, vy, vz)
            //?} else {
            /*LobbyCompat.blockDust(engine, state, feet.x, feet.y, feet.z, vx, vy, vz)
            *///?}
        }
    }

    private fun groundState(level: ClientLevel, feet: Vec3): BlockState {
        val pos = LobbyCompat.blockAt(feet)
        val here = level.getBlockState(pos)
        return if (here.isAir) level.getBlockState(pos.below()) else here
    }

    fun view(camera: Vec3, yaw: Double, pitch: Double, fov: Float, aspect: Double): View {
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        val look = Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
        val right = Vec3(-cos(yawRad), 0.0, -sin(yawRad))
        return View(camera, look, right, right.cross(look), tan(Math.toRadians(fov / 2.0)), aspect)
    }

    private class Planner(
        private val level: ClientLevel,
        avatars: List<LobbyAvatar>,
        viewAt: (Double) -> View,
    ) {
        private val views = VIEW_TIMES.map(viewAt)
        private val walkable = HashMap<BlockPos, Boolean>()
        private val cover = HashMap<BlockPos, Boolean>()
        private val slots = avatars.map { it.rest.pos }
        private val starts = ArrayList<BlockPos>()

        fun fromCover(avatar: LobbyAvatar): LobbyRoute? {
            val cells = search(avatar) ?: return null
            starts += cells.first()
            return route(cells, avatar)
        }

        private fun search(avatar: LobbyAvatar): List<BlockPos>? {
            val origin = LobbyCompat.blockAt(avatar.rest.pos.x, avatar.rest.pos.y + 0.6, avatar.rest.pos.z)
            val parents = HashMap<BlockPos, BlockPos>()
            val depth = hashMapOf(origin to 0)
            val queue = ArrayDeque(listOf(origin))
            var visited = 0
            while (queue.isNotEmpty() && visited++ < SEARCH_LIMIT) {
                val cell = queue.removeFirst()
                val steps = depth.getValue(cell)
                if (steps >= MIN_ROUTE_STEPS && hidden(cell) && !taken(cell)) return generateSequence(cell) { parents[it] }.toList()
                if (steps >= MAX_ROUTE_STEPS) continue
                for (next in neighbours(cell)) {
                    if (next in depth || blockedBySlot(next, avatar)) continue
                    depth[next] = steps + 1
                    parents[next] = cell
                    queue += next
                }
            }
            return null
        }

        fun between(avatar: LobbyAvatar, from: Vec3): LobbyRoute? {
            val origin = LobbyCompat.blockAt(from.x, from.y + 0.6, from.z)
            val target = LobbyCompat.blockAt(avatar.rest.pos.x, avatar.rest.pos.y + 0.6, avatar.rest.pos.z)
            val parents = HashMap<BlockPos, BlockPos>()
            val queue = ArrayDeque(listOf(origin))
            val seen = hashSetOf(origin)
            var visited = 0
            while (queue.isNotEmpty() && visited++ < WALK_LIMIT) {
                val cell = queue.removeFirst()
                if (cell.distManhattan(target) <= 1) {
                    val cells = generateSequence(cell) { parents[it] }.toList().asReversed()
                    return if (cells.size < 2) null else route(cells, avatar, from)
                }
                for (next in neighbours(cell, forward = true)) {
                    if (!seen.add(next) || blockedBySlot(next, avatar)) continue
                    parents[next] = cell
                    queue += next
                }
            }
            return null
        }

        private fun neighbours(cell: BlockPos, forward: Boolean = false): List<BlockPos> {
            val found = ArrayList<BlockPos>(4)
            for (direction in Direction.Plane.HORIZONTAL) {
                val flat = cell.relative(direction)
                listOf(flat, flat.above(), flat.below()).firstOrNull { open(it) && if (forward) climbable(cell, it) else climbable(it, cell) }?.let(found::add)
            }
            return found
        }

        private fun climbable(from: BlockPos, to: BlockPos): Boolean {
            val base = height(from)
            val rise = height(to) - base
            if (rise <= PLAYER_STEP) return true
            return rise <= MAX_RISE && level.obstacle(from.x + 0.5, from.z + 0.5, base, BODY_HEIGHT + rise + HEADROOM) == null
        }

        private fun height(cell: BlockPos): Double {
            val below = cell.below()
            return below.y + level.getBlockState(below).getCollisionShape(level, below).max(Direction.Axis.Y)
        }

        private fun open(pos: BlockPos): Boolean = walkable.getOrPut(pos) { LobbyStage.canStand(level, pos) }

        private fun taken(cell: BlockPos): Boolean = starts.any { it.distManhattan(cell) < START_GAP }

        private fun blockedBySlot(pos: BlockPos, own: LobbyAvatar): Boolean = blockedBySlot(Vec3(pos.x + 0.5, pos.y.toDouble(), pos.z + 0.5), own)

        private fun blockedBySlot(point: Vec3, own: LobbyAvatar): Boolean = slots.any {
            it !== own.rest.pos && hypot(it.x - point.x, it.z - point.z) < SLOT_CLEARANCE && abs(it.y - point.y) < 2.0
        }

        private fun hidden(pos: BlockPos): Boolean = cover.getOrPut(pos) {
            views.all { view -> BODY_HEIGHTS.all { height -> concealed(view, Vec3(pos.x + 0.5, pos.y + height, pos.z + 0.5)) } }
        }

        private fun concealed(view: View, point: Vec3): Boolean {
            val offset = point.subtract(view.camera)
            val depth = offset.dot(view.look)
            if (depth <= 0.1) return true
            val halfHeight = depth * view.tanHalf * FRAME_PAD
            if (abs(offset.dot(view.up)) > halfHeight || abs(offset.dot(view.right)) > halfHeight * view.aspect) return true
            return LobbyStage.reach(level, view.camera, point) < view.camera.distanceTo(point) - 0.6
        }

        private fun route(cells: List<BlockPos>, avatar: LobbyAvatar, start: Vec3? = null): LobbyRoute {
            val feet = cells.map { Vec3(it.x + 0.5, height(it), it.z + 0.5) }
            val pulled = arrayListOf(start ?: feet.first())
            var index = 0
            while (index < feet.size - 1) {
                var next = index + 1
                while (next + 1 < feet.size && clear(feet[index], feet[next + 1], avatar)) next++
                pulled += feet[next]
                index = next
            }
            pulled[pulled.size - 1] = avatar.rest.pos
            return LobbyRoute(pulled.distinct())
        }

        private fun clear(from: Vec3, to: Vec3, avatar: LobbyAvatar): Boolean {
            val distance = hypot(to.x - from.x, to.z - from.z)
            var travelled = 0.0
            var height: Double? = from.y
            var last = from
            while (height != null && travelled < distance + LINE_STEP) {
                val point = if (distance > 0.0) from.lerp(to, min(travelled, distance) / distance) else to
                height = if (blockedBySlot(point, avatar)) null else walk(last, point, height)
                last = point
                travelled += LINE_STEP
            }
            return height != null && abs(height - to.y) < LEVEL_MATCH
        }

        private fun walk(from: Vec3, to: Vec3, feet: Double): Double? {
            val top = level.obstacle(to.x, to.z, feet)
            val next = top ?: level.ground(to.x, to.z, feet, MAX_DROP) ?: return null
            val rise = next - feet
            val fits = top == null || rise <= MAX_RISE && level.obstacle(to.x, to.z, top) == null &&
                (rise <= PLAYER_STEP || level.obstacle(from.x, from.z, feet, BODY_HEIGHT + rise + HEADROOM) == null)
            val dry = level.getFluidState(LobbyCompat.blockAt(to.x, next + SNAP, to.z)).isEmpty
            return next.takeIf { fits && dry }
        }
    }
}
