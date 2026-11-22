package org.polyfrost.polyplus.client.lobby

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.Pose
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
//? if >= 1.21.11 {
import net.minecraft.world.entity.animal.feline.Cat
//?} else {
/*import net.minecraft.world.entity.animal.Cat
*///?}
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

object LobbyCats {
    private const val CHANCE = 0.3
    private const val VILLAGER = "villager"
    private const val FORCE_PROPERTY = "polyplus.cats"
    private const val THIRD_CAT = 0.35
    private const val VILLAGE_RADIUS = 24
    private const val VILLAGE_DEPTH = 8
    private const val MIN_WORKSTATIONS = 3

    private const val SEARCH_RADIUS = 12
    private const val SEARCH_UP = 3
    private const val SEARCH_DOWN = 4
    private const val PLAY_RADIUS = 6.0
    private const val PLAY_RISE = 1.0
    private const val MIN_ROOM = 24
    private const val PICKS = 4
    private const val CAST_CLEARANCE = 1.3
    private const val LENS_CLEARANCE = 1.2
    private const val MIN_DEPTH = 1.5
    private const val SIGHT_HEIGHT = 0.35
    private const val SIGHT_SLACK = 0.3
    private const val CAT_HEIGHT = 0.8
    private const val CAT_WIDTH = 1.1

    private const val PATH_STEP = 0.3
    private const val MAX_STEP = 1.0
    private const val CLIMB = 0.55
    private const val PROBE = 0.7
    private const val GROUND_GRIP = 0.546
    private const val AIR_GRIP = 0.91
    private const val GRAVITY = 0.08
    private const val DRAG = 0.98
    private const val JUMP = 0.42
    private const val MOVE_TURN = 90f
    private const val BODY_SHARE = 0.35f
    private const val HEAD_TURN = 18f
    private const val HEAD_RANGE = 75f
    private const val PITCH_TURN = 10f
    private const val MAX_PITCH = 40f
    private const val FACE_TURN = 8f
    private const val EYE = 0.45
    private const val MOVING = 0.015
    private const val SPRINTING = 0.17
    private const val SPACE = 1.0
    private const val AVOID = 1.6

    private const val WALK = 0.127
    private const val TROT = 0.2
    private const val RUN = 0.27
    private const val FLEE = 0.25
    private const val ZOOM = 0.32
    private const val CREEP = 0.04
    private const val ARRIVE = 0.3
    private const val TAG_REACH = 0.6
    private const val SWAT_REACH = 0.9
    private const val STALK_GAP = 2.0
    private const val POUNCE_LIFT = 0.36
    private const val POUNCE_SHORT = 0.45
    private const val POUNCE_FLIGHT = 6.6
    private const val MAX_LEAP = 0.45
    private const val HOP = 0.22
    private const val REAR = 0.27
    private const val STARTLE_HOP = 0.38
    private const val WRESTLE_REACH = 1.4
    private const val JUKE_RANGE = 1.6
    private const val JUKE_ODDS = 0.12
    private const val LEAD_TICKS = 6.0
    private const val ESCAPE_REACH = 4.5
    private const val MIN_WANDER = 1.5
    private const val ZOOM_REACH = 3.0
    private const val CROWD = 1.2
    private const val BUG_RANGE = 1.4

    private const val STALL_TICKS = 10
    private const val TAG_PAUSE_TICKS = 8
    private const val CHASE_TICKS = 140
    private const val MAX_ROUNDS = 5
    private const val TAG_BACK = 0.6
    private const val TAG_WRESTLE = 0.75

    private const val PLAY_DRAIN = 0.0035
    private const val REST_GAIN = 0.004
    private const val PLAYFUL = 0.45

    private val VARIANTS = listOf(
        "tabby", "black", "red", "siamese", "british_shorthair", "calico", "persian", "ragdoll", "white", "jellie", "all_black",
    )

    private val WORKSTATIONS: Set<Block> = setOf(
        Blocks.LECTERN, Blocks.CARTOGRAPHY_TABLE, Blocks.FLETCHING_TABLE, Blocks.SMITHING_TABLE, Blocks.STONECUTTER, Blocks.LOOM,
        Blocks.GRINDSTONE,
    )

    private enum class Act(val playing: Boolean, val calm: Boolean) {
        WANDER(false, true),
        SIT(false, true),
        GROOM(false, true),
        LOUNGE(false, true),
        STALK(true, false),
        WIGGLE(true, false),
        POUNCE(true, false),
        WRESTLE(true, false),
        PINNED(true, false),
        SWAT(true, false),
        CHASE(true, false),
        FLEE(true, false),
        ZOOM(true, false),
        HUNT(true, true),
        STARTLE(true, false),
    }

    private class Playground(val camera: Vec3, val center: Vec3, val area: List<Vec3>, val count: Int) {
        val floors: Map<Long, Double> = area.associate { column(it.x, it.z) to it.y }
    }

    private class Kitty(val body: Cat) {
        var act = Act.SIT
        var ticks = 0
        var age = 0
        var target: Vec3? = null
        var mate: Kitty? = null
        var rounds = 0
        var legs = 0
        var pause = 0
        var vx = 0.0
        var vz = 0.0
        var lift = 0.0
        var airborne = false
        var moveYaw = body.yRot
        var bodyYaw = body.yRot
        var headYaw = body.yRot
        var pitch = 0f
        var face: Float? = null
        var look: Vec3? = null
        var energy = Random.nextDouble(0.75, 1.0)
        var stall = 0
        var beat = 0
    }

    private var pending: Playground? = null
    private var ground: Playground? = null
    private val cats = ArrayList<Kitty>()
    private var warned = false
    private var quiet = false

    fun owns(entity: Entity): Boolean = cats.any { it.body === entity }

    private fun column(x: Double, z: Double): Long = BlockPos.asLong(floor(x).toInt(), 0, floor(z).toInt())

    fun onSceneShown(level: ClientLevel, stage: LobbyStage, props: List<Entity>, aspect: Double) {
        stop()
        try {
            val forced = java.lang.Boolean.getBoolean(FORCE_PROPERTY)
            if (!forced && Random.nextDouble() >= CHANCE) return
            if (!village(level, LobbyCompat.blockAt(stage.aim), props)) return
            pending = playground(level, stage, props, aspect)
        } catch (error: Throwable) {
            failed("prepare", error)
        }
    }

    fun tick(level: ClientLevel, still: Boolean) {
        quiet = still
        try {
            pending?.let { if (LobbyScene.revealStarted() && !LobbyScene.unrevealed(floor(it.center.x).toInt(), floor(it.center.z).toInt())) spawn(level, it) }
            if (cats.removeAll { it.body.isRemoved }) cats.forEach { if (it.mate?.body?.isRemoved == true) it.mate = null }
            for (kitty in cats) {
                val body = kitty.body
                body.setOldPosAndRot()
                val yawBefore = body.yRot
                val bodyBefore = body.yBodyRot
                val headBefore = body.yHeadRot
                val pitchBefore = body.xRot
                body.tickCount++
                body.deltaMovement = Vec3.ZERO
                behave(kitty)
                orient(kitty)
                apply(kitty)
                body.tick()
                body.deltaMovement = Vec3.ZERO
                apply(kitty)
                body.yRotO = yawBefore
                body.yBodyRotO = bodyBefore
                body.yHeadRotO = headBefore
                body.xRotO = pitchBefore
            }
        } catch (error: Throwable) {
            failed("play", error)
            cats.forEach { runCatching { LobbyCompat.removeEntity(level, it.body) } }
            stop()
        }
    }

    fun stop() {
        pending = null
        ground = null
        cats.clear()
    }

    private fun failed(step: String, error: Throwable) {
        if (warned) return
        warned = true
        LOBBY_LOGGER.warn("Lobby cats could not {}", step, error)
    }

    private fun village(level: ClientLevel, center: BlockPos, props: List<Entity>): Boolean {
        if (props.any { EntityType.getKey(it.type).path == VILLAGER }) return true
        val stations = HashSet<Block>()
        val from = center.offset(-VILLAGE_RADIUS, -VILLAGE_DEPTH, -VILLAGE_RADIUS)
        val to = center.offset(VILLAGE_RADIUS, VILLAGE_DEPTH, VILLAGE_RADIUS)
        for (pos in LobbyCompat.between(from, to)) {
            val block = level.getBlockState(pos).block
            if (block == Blocks.BELL) return true
            if (block in WORKSTATIONS && stations.add(block) && stations.size >= MIN_WORKSTATIONS) return true
        }
        return false
    }

    private fun playground(level: ClientLevel, stage: LobbyStage, props: List<Entity>, aspect: Double): Playground? {
        val camera = stage.camera()
        val view = LobbyEntrance.view(camera, stage.yaw.toDouble(), stage.pitch.toDouble(), LobbyStage.CAMERA_FOV.toFloat(), aspect)
        val cast = (stage.slots + stage.background).map { it.pos } + props.map { it.position() }
        val origin = LobbyCompat.blockAt(stage.aim)
        val spots = ArrayList<Vec3>()
        for (dx in -SEARCH_RADIUS..SEARCH_RADIUS) {
            for (dz in -SEARCH_RADIUS..SEARCH_RADIUS) {
                val feet = surface(level, origin.offset(dx, 0, dz)) ?: continue
                if (fits(level, view, stage.aim, cast, feet)) spots += feet
            }
        }
        val patches = patches(spots)
        val roomy = spots.map { spot -> spot to spots.filter { patches[it] == patches[spot] && near(it, spot) } }.filter { it.second.size >= MIN_ROOM }
        val (center, area) = roomy.sortedByDescending { it.second.size }.take(PICKS).randomOrNull() ?: return null
        return Playground(camera, center, area, if (Random.nextDouble() < THIRD_CAT) 3 else 2)
    }

    private fun patches(spots: List<Vec3>): Map<Vec3, Int> {
        val byColumn = spots.associateBy { column(it.x, it.z) }
        val patch = HashMap<Vec3, Int>()
        for (start in spots) {
            if (start in patch) continue
            val id = patch.size
            val queue = ArrayDeque(listOf(start))
            patch[start] = id
            while (queue.isNotEmpty()) {
                val spot = queue.removeFirst()
                for (direction in Direction.Plane.HORIZONTAL) {
                    val next = byColumn[column(spot.x + direction.stepX, spot.z + direction.stepZ)] ?: continue
                    if (next in patch || abs(next.y - spot.y) > MAX_STEP) continue
                    patch[next] = id
                    queue += next
                }
            }
        }
        return patch
    }

    private fun near(a: Vec3, b: Vec3): Boolean = hypot(a.x - b.x, a.z - b.z) <= PLAY_RADIUS && abs(a.y - b.y) <= PLAY_RISE

    private fun surface(level: ClientLevel, column: BlockPos): Vec3? {
        for (dy in SEARCH_UP downTo -SEARCH_DOWN) {
            val pos = column.offset(0, dy, 0)
            if (!LobbyStage.canStand(level, pos)) continue
            val below = pos.below()
            val ground = level.getBlockState(below)
            if (ground.`is`(BlockTags.LEAVES) || ground.`is`(BlockTags.CAMPFIRES) || ground.`is`(Blocks.CACTUS) || ground.`is`(Blocks.MAGMA_BLOCK)) return null
            val top = ground.getCollisionShape(level, below).max(Direction.Axis.Y)
            return if (top > 1.0) null else Vec3(pos.x + 0.5, below.y + top, pos.z + 0.5)
        }
        return null
    }

    private fun fits(level: ClientLevel, view: LobbyEntrance.View, aim: Vec3, cast: List<Vec3>, feet: Vec3): Boolean {
        if (cast.any { hypot(it.x - feet.x, it.z - feet.z) < CAST_CLEARANCE && abs(it.y - feet.y) < 2.0 }) return false
        if (gap(view.camera, aim, feet) < LENS_CLEARANCE) return false
        if (feet.subtract(view.camera).dot(view.look) < MIN_DEPTH) return false
        if (LobbyStage.underInterface(view, feet, CAT_HEIGHT, CAT_WIDTH)) return false
        val point = feet.add(0.0, SIGHT_HEIGHT, 0.0)
        return LobbyStage.reach(level, view.camera, point) >= view.camera.distanceTo(point) - SIGHT_SLACK
    }

    private fun gap(from: Vec3, to: Vec3, point: Vec3): Double {
        val line = to.subtract(from)
        val share = (point.subtract(from).dot(line) / line.lengthSqr()).coerceIn(0.0, 1.0)
        return point.distanceTo(from.add(line.scale(share)))
    }

    private fun spawn(level: ClientLevel, playground: Playground) {
        pending = null
        ground = playground
        val coats = VARIANTS.shuffled()
        val spots = ArrayList<Vec3>()
        for (spot in playground.area.shuffled()) {
            if (spots.size >= playground.count) break
            if (spots.none { hypot(it.x - spot.x, it.z - spot.z) < SPACE * 2 }) spots += spot
        }
        for ((index, spot) in spots.withIndex()) {
            val body = create(level, coats[index % coats.size]) ?: continue
            body.isSilent = true
            LobbyCompat.snapEntity(body, spot, Random.nextFloat() * 360f)
            body.yHeadRot = body.yRot
            body.yBodyRot = body.yRot
            body.setOldPosAndRot()
            LobbyEntities.enter(level, body)
            if (level.getEntity(body.id) !== body) continue
            cats += Kitty(body).also { sit(it) }
        }
    }

    private fun create(level: ClientLevel, coat: String): Cat? {
        val tag = CompoundTag()
        tag.putString("id", "minecraft:cat")
        tag.putString("variant", "minecraft:$coat")
        return runCatching { LobbyEntities.create(level, tag) }.getOrNull() as? Cat
    }

    private fun behave(kitty: Kitty) {
        kitty.age++
        kitty.energy = (kitty.energy + if (kitty.act.playing) -PLAY_DRAIN else REST_GAIN).coerceIn(0.0, 1.0)
        if (quiet && kitty.act.playing && kitty.act != Act.POUNCE) rest(kitty)
        if (--kitty.ticks <= 0) finish(kitty)
        val from = kitty.body.position()
        when (kitty.act) {
            Act.SIT, Act.GROOM, Act.LOUNGE, Act.PINNED -> brake(kitty)
            Act.WANDER -> if (drive(kitty, kitty.target, WALK, ARRIVE)) wandered(kitty)
            Act.STALK -> stalking(kitty)
            Act.WIGGLE -> brake(kitty)
            Act.POUNCE -> flying(kitty)
            Act.WRESTLE -> wrestling(kitty)
            Act.SWAT -> swatting(kitty)
            Act.CHASE -> chasing(kitty)
            Act.FLEE -> fleeing(kitty)
            Act.ZOOM -> if (drive(kitty, kitty.target, ZOOM, ARRIVE)) zoomLeg(kitty)
            Act.HUNT -> hunting(kitty)
            Act.STARTLE -> startled(kitty)
        }
        watchStall(kitty, from)
        kitty.body.isSprinting = kitty.body.onGround() && hypot(kitty.vx, kitty.vz) > SPRINTING
    }

    private fun watchStall(kitty: Kitty, from: Vec3) {
        val travelling = kitty.act == Act.WANDER || kitty.act == Act.CHASE || kitty.act == Act.FLEE || kitty.act == Act.ZOOM
        val moved = hypot(kitty.body.x - from.x, kitty.body.z - from.z)
        kitty.stall = if (travelling && kitty.pause <= 0 && moved < MOVING) kitty.stall + 1 else 0
        if (kitty.stall < STALL_TICKS) return
        kitty.stall = 0
        when (kitty.act) {
            Act.FLEE -> kitty.target = escape(kitty, true)
            Act.ZOOM -> zoomLeg(kitty)
            else -> choose(kitty)
        }
    }

    private fun finish(kitty: Kitty) {
        when (kitty.act) {
            Act.STALK -> wiggle(kitty)
            Act.WIGGLE -> leap(kitty)
            Act.POUNCE -> landed(kitty)
            Act.WRESTLE -> scatter(kitty)
            Act.PINNED -> if (kitty.mate?.act != Act.WRESTLE) choose(kitty) else kitty.ticks = 10
            Act.SWAT -> bolt(kitty)
            Act.ZOOM -> rest(kitty)
            Act.HUNT -> leapAt(kitty, kitty.target)
            Act.STARTLE -> sit(kitty)
            else -> choose(kitty)
        }
    }

    private fun choose(kitty: Kitty) {
        val partner = cats.filter { it !== kitty && it.act.calm }.randomOrNull()
        if (quiet) {
            pick(
                3.0 to { sit(kitty) },
                3.0 to { groom(kitty) },
                2.0 to { lounge(kitty) },
            )
            return
        }
        if (kitty.energy < PLAYFUL) {
            pick(
                3.0 to { sit(kitty) },
                3.0 to { groom(kitty) },
                2.0 to { lounge(kitty) },
                2.5 to { wander(kitty) },
            )
            return
        }
        if (partner != null) {
            pick(
                2.2 to { chase(kitty, partner, 0) },
                2.2 to { stalk(kitty, partner) },
                1.4 to { swat(kitty, partner) },
                1.2 to { zoom(kitty) },
                1.2 to { hunt(kitty) },
                0.8 to { wander(kitty) },
                0.6 to { sit(kitty) },
            )
        } else {
            pick(
                2.0 to { zoom(kitty) },
                3.0 to { hunt(kitty) },
                2.5 to { wander(kitty) },
                1.5 to { sit(kitty) },
                1.0 to { groom(kitty) },
            )
        }
    }

    private fun pick(vararg options: Pair<Double, () -> Unit>) {
        var roll = Random.nextDouble() * options.sumOf { it.first }
        for ((weight, action) in options) {
            roll -= weight
            if (roll <= 0.0) return action()
        }
        options.last().second()
    }

    private fun begin(kitty: Kitty, act: Act, ticks: Int) {
        val body = kitty.body
        body.setInSittingPose(act == Act.SIT || act == Act.GROOM)
        body.setLying(act == Act.LOUNGE || act == Act.PINNED)
        body.pose = if (act == Act.STALK || act == Act.WIGGLE) Pose.CROUCHING else Pose.STANDING
        kitty.act = act
        kitty.ticks = ticks
        kitty.age = 0
        kitty.target = null
        kitty.mate = null
        kitty.pause = 0
        kitty.face = null
        kitty.look = null
        kitty.stall = 0
        kitty.beat = 0
    }

    private fun others(kitty: Kitty): List<Kitty> = cats.filter { it !== kitty }

    private fun sit(kitty: Kitty) {
        begin(kitty, Act.SIT, Random.nextInt(40, 110))
        val camera = ground?.camera ?: return
        kitty.look = if (Random.nextBoolean()) camera else others(kitty).randomOrNull()?.body?.position()?.add(0.0, EYE, 0.0)
    }

    private fun groom(kitty: Kitty) {
        begin(kitty, Act.GROOM, Random.nextInt(50, 110))
    }

    private fun lounge(kitty: Kitty) {
        begin(kitty, Act.LOUNGE, Random.nextInt(80, 160))
    }

    private fun rest(kitty: Kitty) {
        if (Random.nextBoolean()) groom(kitty) else sit(kitty)
    }

    private fun wander(kitty: Kitty) {
        begin(kitty, Act.WANDER, Random.nextInt(80, 160))
        kitty.target = roam(kitty, MIN_WANDER)
        kitty.look = kitty.target
    }

    private fun wandered(kitty: Kitty) {
        if (Random.nextDouble() < 0.5) {
            kitty.target = roam(kitty, MIN_WANDER)
            kitty.look = kitty.target
        } else {
            sit(kitty)
        }
    }

    private fun roam(kitty: Kitty, reach: Double): Vec3? {
        val from = kitty.body.position()
        val crowd = others(kitty).flatMap { listOfNotNull(it.body.position(), it.target) }
        return ground?.area?.filter { spot ->
            hypot(spot.x - from.x, spot.z - from.z) >= reach && crowd.none { hypot(it.x - spot.x, it.z - spot.z) < CROWD } && clear(from, spot)
        }?.randomOrNull()
    }

    private fun stalk(kitty: Kitty, prey: Kitty) {
        begin(kitty, Act.STALK, Random.nextInt(30, 55))
        kitty.mate = prey
        if (prey.act == Act.WANDER || prey.act == Act.HUNT) sit(prey)
        prey.ticks = maxOf(prey.ticks, kitty.ticks + 40)
    }

    private fun stalking(kitty: Kitty) {
        val prey = kitty.mate ?: return choose(kitty)
        val at = prey.body.position()
        kitty.look = at.add(0.0, EYE * 0.5, 0.0)
        if (!clear(kitty.body.position(), at)) return wander(kitty)
        if (drive(kitty, at, CREEP, STALK_GAP, prey)) kitty.face = yawTo(kitty.body.position(), at)
    }

    private fun wiggle(kitty: Kitty) {
        val prey = kitty.mate ?: return choose(kitty)
        begin(kitty, Act.WIGGLE, Random.nextInt(12, 24))
        kitty.mate = prey
        kitty.look = prey.body.position().add(0.0, EYE * 0.5, 0.0)
        kitty.face = yawTo(kitty.body.position(), prey.body.position())
    }

    private fun leap(kitty: Kitty) {
        val prey = kitty.mate ?: return choose(kitty)
        val from = kitty.body.position()
        val at = prey.body.position()
        val gap = hypot(at.x - from.x, at.z - from.z)
        if (gap <= POUNCE_SHORT || !clear(from, at)) return chase(kitty, prey, 0)
        launch(kitty, from.lerp(at, (gap - POUNCE_SHORT) / gap))
        kitty.mate = prey
    }

    private fun leapAt(kitty: Kitty, spot: Vec3?) {
        if (spot == null || !clear(kitty.body.position(), spot)) return rest(kitty)
        launch(kitty, spot)
    }

    private fun launch(kitty: Kitty, spot: Vec3) {
        begin(kitty, Act.POUNCE, 30)
        val from = kitty.body.position()
        val dx = spot.x - from.x
        val dz = spot.z - from.z
        val gap = hypot(dx, dz)
        val speed = min(MAX_LEAP, gap / POUNCE_FLIGHT)
        kitty.target = spot
        kitty.look = spot
        if (gap > 1.0e-4) {
            kitty.vx = dx / gap * speed
            kitty.vz = dz / gap * speed
            kitty.moveYaw = yawOf(dx, dz)
            kitty.bodyYaw = kitty.moveYaw
        }
        kitty.lift = POUNCE_LIFT
        kitty.airborne = true
    }

    private fun flying(kitty: Kitty) {
        travel(kitty, 0.0, 0.0, 0.0, free = true)
        if (kitty.airborne && kitty.body.onGround() && kitty.lift <= 0.0 && kitty.age > 2) landed(kitty)
    }

    private fun landed(kitty: Kitty) {
        kitty.airborne = false
        val prey = kitty.mate
        if (prey == null || prey.body.isRemoved) return rest(kitty)
        val gap = kitty.body.position().distanceTo(prey.body.position())
        if (gap <= WRESTLE_REACH && prey.act.calm) wrestle(kitty, prey) else chase(kitty, prey, 0)
    }

    private fun wrestle(kitty: Kitty, prey: Kitty) {
        begin(kitty, Act.WRESTLE, Random.nextInt(35, 60))
        begin(prey, Act.PINNED, kitty.ticks + 5)
        kitty.mate = prey
        prey.mate = kitty
        kitty.look = prey.body.position()
        prey.look = kitty.body.position().add(0.0, EYE, 0.0)
        prey.face = yawTo(prey.body.position(), kitty.body.position()) + 90f
    }

    private fun wrestling(kitty: Kitty) {
        val prey = kitty.mate ?: return choose(kitty)
        kitty.look = prey.body.position()
        kitty.face = yawTo(kitty.body.position(), prey.body.position())
        brake(kitty)
        if (kitty.beat-- <= 0 && kitty.body.onGround()) {
            kitty.beat = Random.nextInt(5, 11)
            kitty.body.pose = if (kitty.body.pose == Pose.CROUCHING) Pose.STANDING else Pose.CROUCHING
            if (Random.nextBoolean()) kitty.lift = HOP
        }
    }

    private fun scatter(kitty: Kitty) {
        val prey = kitty.mate ?: return choose(kitty)
        when (Random.nextInt(3)) {
            0 -> chase(prey, kitty, 0)
            1 -> chase(kitty, prey, 0)
            else -> {
                rest(kitty)
                startle(prey)
            }
        }
    }

    private fun swat(kitty: Kitty, partner: Kitty) {
        begin(kitty, Act.SWAT, Random.nextInt(45, 70))
        begin(partner, Act.SWAT, kitty.ticks + 10)
        kitty.mate = partner
        partner.mate = kitty
        partner.beat = 6
    }

    private fun swatting(kitty: Kitty) {
        val partner = kitty.mate ?: return choose(kitty)
        val at = partner.body.position()
        kitty.look = at.add(0.0, EYE, 0.0)
        if (!drive(kitty, at, TROT, SWAT_REACH, partner)) return
        kitty.face = yawTo(kitty.body.position(), at)
        if (kitty.beat-- > 0 || !kitty.body.onGround()) return
        kitty.beat = Random.nextInt(8, 16)
        kitty.lift = REAR
        kitty.body.pose = if (Random.nextDouble() < 0.4) Pose.CROUCHING else Pose.STANDING
    }

    private fun bolt(kitty: Kitty) {
        val partner = kitty.mate
        if (partner == null || partner.act != Act.SWAT) return choose(kitty)
        if (Random.nextBoolean()) chase(partner, kitty, 0) else chase(kitty, partner, 0)
    }

    private fun chase(chaser: Kitty, prey: Kitty, rounds: Int) {
        begin(chaser, Act.CHASE, CHASE_TICKS)
        begin(prey, Act.FLEE, CHASE_TICKS)
        chaser.mate = prey
        chaser.rounds = rounds
        prey.mate = chaser
        prey.target = escape(prey, false)
    }

    private fun chasing(kitty: Kitty) {
        val prey = kitty.mate
        if (prey == null || prey.act != Act.FLEE || prey.mate !== kitty) return choose(kitty)
        val at = prey.body.position()
        kitty.look = at.add(0.0, EYE, 0.0)
        if (kitty.pause > 0) {
            kitty.pause--
            kitty.face = yawTo(kitty.body.position(), at)
            brake(kitty)
            return
        }
        val lead = Vec3(at.x + prey.vx * LEAD_TICKS, at.y, at.z + prey.vz * LEAD_TICKS)
        val aim = if (clear(kitty.body.position(), lead)) lead else at
        if (hypot(at.x - kitty.body.x, at.z - kitty.body.z) > TAG_REACH) {
            drive(kitty, aim, RUN, TAG_REACH * 0.5, prey)
            return
        }
        tagged(kitty, prey)
    }

    private fun tagged(kitty: Kitty, prey: Kitty) {
        val roll = Random.nextDouble()
        when {
            kitty.rounds < MAX_ROUNDS && roll < TAG_BACK -> {
                chase(prey, kitty, kitty.rounds + 1)
                prey.pause = TAG_PAUSE_TICKS
                kitty.lift = STARTLE_HOP * 0.7
            }
            roll < TAG_WRESTLE -> wrestle(kitty, prey)
            else -> {
                rest(kitty)
                rest(prey)
                prey.look = kitty.body.position().add(0.0, EYE, 0.0)
            }
        }
    }

    private fun fleeing(kitty: Kitty) {
        val chaser = kitty.mate
        if (chaser == null || chaser.act != Act.CHASE) return choose(kitty)
        val from = kitty.body.position()
        val close = hypot(chaser.body.x - from.x, chaser.body.z - from.z)
        if (close < JUKE_RANGE && Random.nextDouble() < JUKE_ODDS) {
            kitty.target = escape(kitty, true)
            if (kitty.body.onGround() && Random.nextDouble() < 0.3) kitty.lift = HOP
        }
        kitty.look = kitty.target
        if (drive(kitty, kitty.target, FLEE, ARRIVE, chaser)) kitty.target = escape(kitty, true)
    }

    private fun escape(kitty: Kitty, juke: Boolean): Vec3? {
        val area = ground?.area ?: return null
        val from = kitty.body.position()
        val chaser = kitty.mate?.body?.position() ?: from
        val awayX = from.x - chaser.x
        val awayZ = from.z - chaser.z
        val away = hypot(awayX, awayZ).coerceAtLeast(1.0e-4)
        return area.filter { hypot(it.x - from.x, it.z - from.z) in MIN_WANDER..ESCAPE_REACH && clear(from, it) }.maxByOrNull { spot ->
            val dx = spot.x - from.x
            val dz = spot.z - from.z
            val reach = hypot(dx, dz)
            val outward = (dx * awayX + dz * awayZ) / (reach * away)
            val sideways = 1.0 - abs(outward)
            hypot(spot.x - chaser.x, spot.z - chaser.z) * 0.4 + (if (juke) sideways * 2.0 else outward) + Random.nextDouble() * 1.2
        }
    }

    private fun zoom(kitty: Kitty) {
        begin(kitty, Act.ZOOM, 200)
        kitty.legs = Random.nextInt(3, 7)
        zoomLeg(kitty)
        val friend = others(kitty).firstOrNull { it.act.calm && it.act != Act.LOUNGE }
        if (friend != null && Random.nextDouble() < 0.35) chase(friend, kitty, MAX_ROUNDS - 1)
    }

    private fun zoomLeg(kitty: Kitty) {
        if (kitty.act != Act.ZOOM) return
        if (--kitty.legs < 0) return rest(kitty)
        kitty.target = roam(kitty, ZOOM_REACH) ?: roam(kitty, MIN_WANDER)
        kitty.look = kitty.target
        if (kitty.body.onGround() && Random.nextDouble() < 0.35) kitty.lift = HOP
    }

    private fun hunt(kitty: Kitty) {
        begin(kitty, Act.HUNT, Random.nextInt(70, 140))
        kitty.target = bug(kitty)
    }

    private fun bug(kitty: Kitty): Vec3? {
        val from = kitty.body.position()
        return ground?.area?.filter { hypot(it.x - from.x, it.z - from.z) in 0.6..BUG_RANGE && clear(from, it) }?.randomOrNull()
            ?.add(Random.nextDouble(-0.3, 0.3), 0.0, Random.nextDouble(-0.3, 0.3))
    }

    private fun hunting(kitty: Kitty) {
        if (kitty.beat-- <= 0) {
            kitty.beat = Random.nextInt(6, 16)
            kitty.target = bug(kitty) ?: kitty.target
            kitty.pause = Random.nextInt(0, 6)
        }
        val bug = kitty.target ?: return brake(kitty)
        kitty.look = bug.add(0.0, if (kitty.beat % 7 == 0) 0.9 else 0.05, 0.0)
        if (kitty.pause > 0) {
            kitty.pause--
            kitty.face = yawTo(kitty.body.position(), bug)
            kitty.body.pose = Pose.CROUCHING
            brake(kitty)
            return
        }
        kitty.body.pose = Pose.STANDING
        if (drive(kitty, bug, TROT, 0.4) && kitty.body.onGround() && Random.nextDouble() < 0.25) kitty.lift = HOP
    }

    private fun startle(kitty: Kitty) {
        begin(kitty, Act.STARTLE, Random.nextInt(25, 40))
        kitty.lift = STARTLE_HOP
        kitty.face = kitty.bodyYaw + 180f
        kitty.target = roam(kitty, MIN_WANDER)
    }

    private fun startled(kitty: Kitty) {
        if (kitty.age < 8) return brake(kitty)
        if (drive(kitty, kitty.target, RUN, ARRIVE)) sit(kitty)
    }

    private fun brake(kitty: Kitty) {
        travel(kitty, 0.0, 0.0, 0.0)
    }

    private fun drive(kitty: Kitty, target: Vec3?, pace: Double, reach: Double, ignore: Kitty? = null): Boolean {
        val from = kitty.body.position()
        if (target == null) {
            brake(kitty)
            return true
        }
        val dx = target.x - from.x
        val dz = target.z - from.z
        val distance = hypot(dx, dz)
        if (distance <= reach) {
            brake(kitty)
            return true
        }
        var wantX = dx / distance
        var wantZ = dz / distance
        for (other in cats) {
            if (other === kitty || other === ignore) continue
            val ox = from.x - other.body.x
            val oz = from.z - other.body.z
            val apart = hypot(ox, oz)
            if (apart >= SPACE || apart < 1.0e-4) continue
            val push = (SPACE - apart) / SPACE * AVOID
            wantX += ox / apart * push
            wantZ += oz / apart * push
        }
        val wanted = passable(from, yawOf(wantX, wantZ))
        if (wanted == null) {
            brake(kitty)
            return false
        }
        kitty.moveYaw += Mth.wrapDegrees(wanted - kitty.moveYaw).coerceIn(-MOVE_TURN, MOVE_TURN)
        val radians = Math.toRadians(kitty.moveYaw.toDouble())
        val slowing = min(1.0, distance / (pace * 4.0) + 0.35)
        travel(kitty, -sin(radians), cos(radians), pace * slowing)
        return false
    }

    private fun passable(from: Vec3, yaw: Float): Float? {
        val floors = ground?.floors ?: return null
        val here = floors[column(from.x, from.z)] ?: return yaw
        for (turn in floatArrayOf(0f, 25f, -25f, 50f, -50f, 80f, -80f, 115f, -115f)) {
            val radians = Math.toRadians((yaw + turn).toDouble())
            val ahead = floors[column(from.x - sin(radians) * PROBE, from.z + cos(radians) * PROBE)] ?: continue
            if (abs(ahead - here) <= MAX_STEP) return yaw + turn
        }
        return null
    }

    private fun travel(kitty: Kitty, dirX: Double, dirZ: Double, pace: Double, free: Boolean = false) {
        val body = kitty.body
        val grounded = body.onGround()
        val grip = if (grounded) GROUND_GRIP else AIR_GRIP
        val push = if (grounded) pace * (1.0 - GROUND_GRIP) else 0.0
        kitty.vx = kitty.vx * grip + dirX * push
        kitty.vz = kitty.vz * grip + dirZ * push
        val from = body.position()
        if (!free && !safe(from, kitty.vx, kitty.vz)) {
            kitty.vx = 0.0
            kitty.vz = 0.0
        }
        val speed = hypot(kitty.vx, kitty.vz)
        val climb = speed > 1.0e-3 && climbs(from, kitty.vx / speed, kitty.vz / speed)
        val rise = kitty.lift
        body.move(MoverType.SELF, Vec3(kitty.vx, rise, kitty.vz))
        kitty.lift = if (body.onGround()) -GRAVITY else (rise - GRAVITY) * DRAG
        if (body.verticalCollision && rise > 0.0) kitty.lift = 0.0
        if (body.horizontalCollision) {
            kitty.vx = body.x - from.x
            kitty.vz = body.z - from.z
            if (climb && body.onGround()) kitty.lift = JUMP
        }
    }

    private fun safe(from: Vec3, vx: Double, vz: Double): Boolean {
        val floors = ground?.floors ?: return true
        val here = floors[column(from.x, from.z)] ?: return true
        val speed = hypot(vx, vz)
        if (speed < 1.0e-4) return true
        val reach = speed + 0.3
        val ahead = floors[column(from.x + vx / speed * reach, from.z + vz / speed * reach)] ?: return false
        return abs(ahead - here) <= MAX_STEP
    }

    private fun climbs(from: Vec3, dirX: Double, dirZ: Double): Boolean {
        val ahead = ground?.floors?.get(column(from.x + dirX * PROBE, from.z + dirZ * PROBE)) ?: return false
        return ahead - from.y > CLIMB
    }

    private fun clear(from: Vec3, to: Vec3): Boolean {
        val floors = ground?.floors ?: return false
        val distance = hypot(to.x - from.x, to.z - from.z)
        var last = floors[column(from.x, from.z)] ?: from.y
        var travelled = 0.0
        while (travelled <= distance) {
            val share = if (distance > 0.0) travelled / distance else 1.0
            val height = floors[column(from.x + (to.x - from.x) * share, from.z + (to.z - from.z) * share)] ?: return false
            if (abs(height - last) > MAX_STEP) return false
            last = height
            travelled += PATH_STEP
        }
        return true
    }

    private fun orient(kitty: Kitty) {
        val body = kitty.body
        val dx = body.x - body.xo
        val dz = body.z - body.zo
        val moving = hypot(dx, dz) > MOVING
        val face = kitty.face
        when {
            kitty.act == Act.WIGGLE -> kitty.bodyYaw = (face ?: kitty.bodyYaw) + (sin(kitty.age * 1.7) * 7.0).toFloat()
            moving && kitty.act != Act.POUNCE -> kitty.bodyYaw += Mth.wrapDegrees(yawOf(dx, dz) - kitty.bodyYaw) * BODY_SHARE
            face != null -> kitty.bodyYaw += Mth.wrapDegrees(face - kitty.bodyYaw).coerceIn(-FACE_TURN * 2, FACE_TURN * 2)
        }
        if (!moving) kitty.moveYaw = kitty.bodyYaw
        val eye = body.position().add(0.0, EYE, 0.0)
        val look = kitty.look
        var headGoal = if (look != null) yawTo(eye, look) else kitty.bodyYaw
        var pitchGoal = if (look != null) pitchTo(eye, look) else 0f
        if (kitty.act == Act.GROOM) {
            headGoal = kitty.bodyYaw + (sin(kitty.age * 0.35) * 25.0).toFloat()
            pitchGoal = 38f + (sin(kitty.age * 0.9) * 6.0).toFloat()
        }
        kitty.headYaw += Mth.wrapDegrees(headGoal - kitty.headYaw).coerceIn(-HEAD_TURN, HEAD_TURN)
        val twist = Mth.wrapDegrees(kitty.headYaw - kitty.bodyYaw)
        if (abs(twist) > HEAD_RANGE) {
            if (moving) kitty.headYaw = kitty.bodyYaw + twist.coerceIn(-HEAD_RANGE, HEAD_RANGE)
            else kitty.bodyYaw += (abs(twist) - HEAD_RANGE) * Math.signum(twist)
        }
        kitty.pitch += (pitchGoal.coerceIn(-MAX_PITCH, MAX_PITCH) - kitty.pitch).coerceIn(-PITCH_TURN, PITCH_TURN)
    }

    private fun apply(kitty: Kitty) {
        val body = kitty.body
        body.yRot = kitty.bodyYaw
        body.yBodyRot = kitty.bodyYaw
        body.yHeadRot = kitty.headYaw
        body.xRot = kitty.pitch
    }

    private fun yawOf(x: Double, z: Double): Float = Math.toDegrees(atan2(-x, z)).toFloat()

    private fun yawTo(from: Vec3, to: Vec3): Float = yawOf(to.x - from.x, to.z - from.z)

    private fun pitchTo(from: Vec3, to: Vec3): Float = (-Math.toDegrees(atan2(to.y - from.y, hypot(to.x - from.x, to.z - from.z)))).toFloat()
}
//?}
