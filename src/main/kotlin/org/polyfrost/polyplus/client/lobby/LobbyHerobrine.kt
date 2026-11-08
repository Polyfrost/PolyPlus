package org.polyfrost.polyplus.client.lobby

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
//? if > 1.8.9 {
import net.minecraft.client.resources.DefaultPlayerSkin
//?} else {
/*import net.minecraft.client.resource.skin.DefaultSkinUtils
*///?}
import net.minecraft.core.Direction
//? if > 1.8.9 {
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.player.Player
//?} else {
/*import net.minecraft.entity.living.player.PlayerEntity as Player
import net.minecraft.entity.particle.ParticleType
*///?}
import net.minecraft.resources.Identifier
import net.minecraft.tags.BlockTags
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin
//?} elif > 1.8.9 {
/*import net.minecraft.client.resources.PlayerSkin
*///?} else {
/*import org.polyfrost.oneconfig.internal.legacy.PlayerSkin
*///?}
import net.minecraft.world.phys.Vec3
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.utils.Easing
//? if > 1.8.9 {
import com.mojang.blaze3d.vertex.PoseStack
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.utils.rotateBy
//?} else {
/*import net.minecraft.client.render.platform.GlStateManager
*///?}
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

object LobbyHerobrine {
    private const val CHANCE = 0.04
    private const val FORCE_PROPERTY = "polyplus.herobrine"
    private const val ENTITY_ID = 0x48524221
    private const val NAME = "Herobrine"
    private const val TEAM = "lobby"
    private const val NEAR = 26.0
    private const val FAR = 44.0
    private const val DISTANCE_STEP = 1.5
    private const val MIN_TURN = 6.0
    private const val MAX_TURN = 22.0
    private const val TURN_STEP = 2.0
    private const val ASPECT = 1.6
    private const val MIN_SCREEN_X = 0.3
    private const val MAX_SCREEN_X = 0.72
    private const val REVEAL_GRACE_TICKS = 600
    private const val MAX_SIGHT_MISSES = 6
    private const val SIGHT_RETRY_TICKS = 40
    private const val WIDE_NEAR = 12.0
    private const val WIDE_FAR = 60.0
    private const val WIDE_MIN_TURN = 2.0
    private const val WIDE_MAX_TURN = 34.0
    private const val WIDE_MIN_SCREEN_X = 0.12
    private const val WIDE_MAX_SCREEN_X = 0.86
    private const val MAX_SCREEN_Y = 0.75
    private const val SCAN_UP = 10
    private const val SCAN_DOWN = 24
    private const val EYE = 1.62
    private const val HEAD_TOP = 1.8
    private const val SIGHT_SLACK = 0.35
    private const val CLEAR_OF_CAST = 5.0
    private const val MIN_DELAY_TICKS = 160
    private const val MAX_DELAY_TICKS = 520
    private const val MIN_STAY_TICKS = 60
    private const val MAX_STAY_TICKS = 120
    private const val LINGER_TICKS = 200
    private const val DRIFT_DEGREES = 0.9
    private const val UUID_HIGH = 0x4865726F6272696EL
    private const val UUID_TRIES = 256L
    private const val EYES_IN = 0.9
    private const val FLICKER_RATE = 31.0
    private const val FLICKER_DEPTH = 0.35
    private const val BODY_FROM = 0.7
    private const val BODY_IN = 1.3
    private const val BODY_OUT = 1.1
    private const val EYES_LINGER = 0.45
    private const val EYES_OUT = 0.6
    private const val SOLID = 0.995f
    private const val WISP_CHANCE = 0.55
    private const val WISP_MIN_RADIUS = 0.15
    private const val WISP_MAX_RADIUS = 0.45
    private const val WISP_LIFT = 0.25
    private const val WISP_RISE = 0.012
    private const val ASH_SHARE = 0.6
    private const val NANOS = 1_000_000_000.0
    private const val LEAN_DEGREES = 19.0
    private const val HEAD_TILT_DEGREES = 12.0
    private const val PEEK_EYES_IN = 1.3
    private const val PEEK_BODY_FROM = 0.45
    private const val TUCK_BODY_OUT = 0.75
    private const val TUCK_EYES_FROM = 0.25
    private const val LEAN_IN = 2.2
    private const val TUCK_OUT = 1.0
    private const val TUCK_MIN = 0.05f
    private const val EYE_SIDE = 0.17
    private const val FACE_DEPTH = 0.25
    private const val SHOULDER = 1.25
    private const val SHOULDER_SIDE = 0.32
    private const val COVER_GAP = 4.5
    private const val COVER_BONUS = 10.0
    private const val PEEK_EXTRA_TICKS = 60
    private const val PEEK_SLACK = 0.1
    private const val FORCED_NEAR = 6.0
    private const val FORCED_FAR = 52.0
    private const val FORCED_MAX_TURN = 28.0
    private const val FORCED_MAX_SCREEN_X = 0.92
    private const val FORCED_MAX_SCREEN_Y = 0.9
    private const val FORCED_CLEAR = 2.5
    private const val FORCED_STEP = 1.0

    private val LEAN_RADIANS = Math.toRadians(LEAN_DEGREES)
    private val HEAD_TILT_RADIANS = Math.toRadians(HEAD_TILT_DEGREES)

    val EYES: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/entity/herobrine_eyes.png")

    private enum class State { IDLE, WAITING, STANDING, TUCKING, LEAVING, GONE }

    private class Spot(val feet: Vec3, val covered: Boolean)

    private class Candidate(val spot: Spot, val score: Double)

    private class Search(val spot: Spot?, val candidates: Int, val covered: Int)

    private class Figure(level: ClientLevel, profile: GameProfile, private val skin: PlayerSkin) : RemotePlayer(level, profile) {
        //? if > 1.8.9 {
        override fun getSkin(): PlayerSkin = skin
        //?} else {
        /*override fun getSkinTextureLocation(): Identifier = skin.texture()

        override fun getModelType(): String = "default"

        override fun hasSkinTexture(): Boolean = true
        *///?}

        override fun isInvisible(): Boolean = bodyAlpha() < SOLID

        override fun isInvisibleTo(player: Player): Boolean = isInvisible()
    }

    private val profile: Pair<GameProfile, PlayerSkin>? by lazy {
        (0L until UUID_TRIES).asSequence().map { UUID(UUID_HIGH, it) }.firstNotNullOfOrNull { uuid ->
            defaultSkin(uuid).takeIf(::isSteve)?.let { GameProfile(uuid, NAME) to it }
        }
    }

    //? if >= 1.21.10 {
    private fun defaultSkin(uuid: UUID): PlayerSkin = DefaultPlayerSkin.get(uuid)

    private fun isSteve(skin: PlayerSkin): Boolean = skin.body().texturePath().path.contains("wide/steve")

    private fun textureOf(skin: PlayerSkin): Identifier = skin.body().texturePath()
    //?} elif > 1.8.9 {
    /*private fun defaultSkin(uuid: UUID): PlayerSkin = DefaultPlayerSkin.get(uuid)

    private fun isSteve(skin: PlayerSkin): Boolean = skin.texture().path.contains("wide/steve")

    private fun textureOf(skin: PlayerSkin): Identifier = skin.texture()
    *///?} else {
    /*private fun defaultSkin(uuid: UUID): PlayerSkin = PlayerSkin(DefaultSkinUtils.getDefaultSkin(uuid))

    private fun isSteve(skin: PlayerSkin): Boolean = skin.texture().getPath().endsWith("/steve.png")

    private fun textureOf(skin: PlayerSkin): Identifier = skin.texture()
    *///?}

    private var state = State.IDLE
    private var spot: Spot? = null
    private var peekDir: Vec3? = null
    private var tuckAt = 0L
    private var tuckFrom = 0f
    private var tuckBody = 0f
    private var tuckEyes = 0f
    private var leftLean = 0f
    private var sightMisses = 0
    private var figure: Figure? = null
    private var calmTicks = 0
    private var dueTicks = 0
    private var stayTicks = 0
    private var stayLimit = 0
    private var shownLook: Vec3? = null
    private var shownLast = false
    private var heldOff = false
    private var forced = false
    private var castCache: List<Vec3> = emptyList()
    private var researched = false
    private var warned = false
    private var shownAt = 0L
    private var leftAt = 0L
    private var leftBody = 0f
    private var leftEyes = 0f

    fun shows(entityId: Int): Boolean =
        (state == State.STANDING || state == State.TUCKING || state == State.LEAVING) && figure != null && entityId == ENTITY_ID

    fun skinTexture(): Identifier? = profile?.second?.let(::textureOf)

    //? if > 1.8.9 {
    @JvmStatic
    fun lean(entityId: Int, poseStack: PoseStack) {
        val (dir, angle) = leaning(entityId) ?: return
        poseStack.rotateBy(Quaternionf().rotationAxis(angle, dir.z.toFloat(), 0f, (-dir.x).toFloat()))
    }
    //?} else {
    /*@JvmStatic
    fun lean(entityId: Int) {
        val (dir, angle) = leaning(entityId) ?: return
        GlStateManager.rotatef(Math.toDegrees(angle.toDouble()).toFloat(), dir.z.toFloat(), 0f, (-dir.x).toFloat())
    }
    *///?}

    @JvmStatic
    fun tilt(entityId: Int): Float {
        val (dir, _) = leaning(entityId) ?: return 0f
        val body = figure ?: return 0f
        val yaw = Math.toRadians(body.yBodyRot.toDouble())
        val side = sign(dir.x * cos(yaw) + dir.z * sin(yaw))
        return (side * leanWeight() * HEAD_TILT_RADIANS).toFloat()
    }

    private fun leaning(entityId: Int): Pair<Vec3, Float>? {
        if (!shows(entityId)) return null
        val dir = peekDir ?: return null
        val angle = (leanWeight() * LEAN_RADIANS).toFloat()
        return if (angle > 0f) dir to angle else null
    }

    private fun leanWeight(): Float {
        if (peekDir == null) return 0f
        return when (state) {
            State.STANDING -> ease(since(shownAt) / LEAN_IN)
            State.TUCKING -> tuckFrom * (1f - ease(since(tuckAt) / TUCK_OUT))
            State.LEAVING -> leftLean * (1f - ease(since(leftAt) / TUCK_OUT))
            else -> 0f
        }
    }

    private fun bodyFrom(): Double = if (peekDir != null) PEEK_BODY_FROM else BODY_FROM

    private fun bodyIn(): Double = if (peekDir != null) LEAN_IN - PEEK_BODY_FROM else BODY_IN

    private fun eyesIn(): Double = if (peekDir != null) PEEK_EYES_IN else EYES_IN

    fun bodyAlpha(): Float = when (state) {
        State.STANDING -> ease((since(shownAt) - bodyFrom()) / bodyIn())
        State.TUCKING -> tuckBody * (1f - ease(since(tuckAt) / TUCK_BODY_OUT))
        State.LEAVING -> leftBody * (1f - ease(since(leftAt) / BODY_OUT))
        else -> 0f
    }

    fun eyesAlpha(): Float = when (state) {
        State.STANDING -> {
            val elapsed = since(shownAt)
            val fadeIn = eyesIn()
            val flicker = if (elapsed < fadeIn) 1.0 - FLICKER_DEPTH * abs(sin(elapsed * FLICKER_RATE)) * (1.0 - elapsed / fadeIn) else 1.0
            ease(elapsed / fadeIn) * flicker.toFloat()
        }
        State.TUCKING -> tuckEyes * (1f - ease((since(tuckAt) - TUCK_EYES_FROM) / (TUCK_OUT - TUCK_EYES_FROM)))
        State.LEAVING -> leftEyes * (1f - ease((since(leftAt) - BODY_OUT - EYES_LINGER) / EYES_OUT))
        else -> 0f
    }

    private fun since(start: Long): Double = (System.nanoTime() - start) / NANOS

    private fun ease(progress: Double): Float = Easing.smoothstep(progress).toFloat()

    fun onSceneShown(level: ClientLevel, stage: LobbyStage) {
        try {
            show(level, stage)
        } catch (e: Throwable) {
            failed("prepare", e)
        }
    }

    private fun show(level: ClientLevel, stage: LobbyStage) {
        reset()
        forced = java.lang.Boolean.getBoolean(FORCE_PROPERTY)
        if (stage.interior) {
            note("gave up: interior scene")
            return
        }
        val repeat = shownLast
        shownLast = false
        if (onboardingNear()) heldOff = true
        if (!forced && (heldOff || repeat || Random.nextDouble() >= CHANCE)) return
        if (profile == null) {
            note("gave up: no default Steve skin found")
            return
        }
        val cast = castOf(stage)
        val search = search(level, stage.camera(), stage.yaw.toDouble(), stage.pitch.toDouble(), cast)
        val chosen = search.spot
        if (chosen == null) {
            note("no spot found (${search.candidates} candidates, ${search.covered} covered)")
            return
        }
        spot = chosen
        castCache = cast
        dueTicks = if (forced) MIN_DELAY_TICKS else Random.nextInt(MIN_DELAY_TICKS, MAX_DELAY_TICKS)
        stayLimit = Random.nextInt(MIN_STAY_TICKS, MAX_STAY_TICKS)
        state = State.WAITING
        note("waiting for calm, ${describe(chosen)} picked from ${search.candidates} candidates (${search.covered} covered)")
    }

    fun tick(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        try {
            if (onboardingNear()) heldOff = true
            when (state) {
                State.WAITING -> wait(level, rig, calm)
                State.STANDING -> stand(level, rig, calm)
                State.TUCKING -> tuck(level, rig, calm)
                State.LEAVING -> leave(level, rig)
                else -> Unit
            }
        } catch (e: Throwable) {
            failed("tick", e)
            vanish(level)
        }
    }

    fun stop() {
        reset()
    }

    private fun held(): Boolean = heldOff && !forced

    private fun note(message: String) {
        if (forced) LOBBY_LOGGER.info("Herobrine: {}", message)
    }

    private fun failed(step: String, error: Throwable) {
        state = State.GONE
        if (warned) return
        warned = true
        LOBBY_LOGGER.warn("Herobrine could not {}", step, error)
    }

    private fun describe(spot: Spot): String =
        "${if (spot.covered) "covered" else "open"} spot at ${format(spot.feet)}"

    private fun format(point: Vec3): String = "%.1f, %.1f, %.1f".format(point.x, point.y, point.z)

    private fun giveUp(reason: String) {
        state = State.GONE
        note("gave up: $reason")
    }

    private fun wait(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        if (held()) {
            giveUp("onboarding is showing")
            return
        }
        if (!calm) {
            calmTicks = 0
            return
        }
        if (++calmTicks < dueTicks) return
        val chosen = spot ?: return giveUp("no spot")
        val feet = chosen.feet
        if (LobbyScene.unrevealed(floor(feet.x).toInt(), floor(feet.z).toInt())) {
            if (calmTicks > dueTicks + REVEAL_GRACE_TICKS) giveUp("spot at ${format(feet)} was never revealed")
            return
        }
        val eye = eyeOf(rig)
        val peek = if (chosen.covered && shielded(level, eye, feet)) peekSide(level, eye, feet) else null
        if (peek == null && !visible(level, eye, feet)) {
            if (++sightMisses < MAX_SIGHT_MISSES) {
                calmTicks = dueTicks - SIGHT_RETRY_TICKS
                return
            }
            if (forced && !researched) {
                researched = true
                val search = search(level, eye, rig.yRot.toDouble(), rig.xRot.toDouble(), castCache)
                val next = search.spot
                if (next != null) {
                    spot = next
                    sightMisses = 0
                    calmTicks = dueTicks - SIGHT_RETRY_TICKS
                    note("spot at ${format(feet)} lost sight, retrying with ${describe(next)}")
                    return
                }
            }
            giveUp("spot at ${format(feet)} out of sight after $sightMisses tries")
            return
        }
        appear(level, feet, eye, lookOf(rig), peek)
        note("appeared at ${format(feet)}${if (peek != null) ", peeking" else ""}")
    }

    private fun stand(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        val body = figure ?: return vanish(level)
        val look = lookOf(rig)
        val moved = shownLook?.let { it.dot(look) < cos(Math.toRadians(DRIFT_DEGREES)) } ?: true
        stayTicks++
        if (!calm || held() || (stayTicks >= stayLimit && moved) || stayTicks >= stayLimit + LINGER_TICKS) {
            depart(calm && !held())
            return
        }
        face(body, eyeOf(rig))
        val elapsed = since(shownAt)
        if (elapsed >= bodyFrom() && elapsed <= bodyFrom() + bodyIn()) wisp(body.position())
    }

    private fun depart(tuck: Boolean) {
        val lean = leanWeight()
        if (tuck && lean > TUCK_MIN) {
            tuckFrom = lean
            tuckBody = bodyAlpha()
            tuckEyes = eyesAlpha()
            tuckAt = System.nanoTime()
            state = State.TUCKING
            return
        }
        fade()
    }

    private fun tuck(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        val body = figure ?: return vanish(level)
        val elapsed = since(tuckAt)
        if (elapsed >= TUCK_OUT) {
            vanish(level)
            return
        }
        if (!calm || held()) {
            fade()
            return
        }
        face(body, eyeOf(rig))
        if (elapsed <= TUCK_BODY_OUT && tuckBody > 0f) wisp(body.position())
    }

    private fun fade() {
        leftLean = leanWeight()
        leftBody = bodyAlpha()
        leftEyes = eyesAlpha()
        leftAt = System.nanoTime()
        state = State.LEAVING
    }

    private fun leave(level: ClientLevel, rig: LocalPlayer) {
        val body = figure ?: return vanish(level)
        val elapsed = since(leftAt)
        if (elapsed >= BODY_OUT + EYES_LINGER + EYES_OUT) {
            vanish(level)
            return
        }
        face(body, eyeOf(rig))
        if (elapsed <= BODY_OUT && leftBody > 0f) wisp(body.position())
    }

    private fun wisp(feet: Vec3) {
        if (Random.nextDouble() >= WISP_CHANCE || !LobbyScene.particlesAllowed()) return
        val angle = Random.nextDouble(2.0 * PI)
        val radius = Random.nextDouble(WISP_MIN_RADIUS, WISP_MAX_RADIUS)
        val x = feet.x + cos(angle) * radius
        val y = feet.y + Random.nextDouble(WISP_LIFT)
        val z = feet.z + sin(angle) * radius
        val ash = Random.nextDouble() < ASH_SHARE
        runCatching {
            val engine = Minecraft.getInstance().particleEngine
            //? if > 1.8.9 {
            engine.createParticle(if (ash) ParticleTypes.ASH else ParticleTypes.SMOKE, x, y, z, 0.0, WISP_RISE, 0.0)
            //?} else {
            /*engine.addParticle((if (ash) ParticleType.SUSPENDED_DEPTH else ParticleType.SMOKE_NORMAL).id, x, y, z, 0.0, WISP_RISE, 0.0)
            *///?}
        }
    }

    private fun appear(level: ClientLevel, feet: Vec3, eye: Vec3, look: Vec3, peek: Vec3?) {
        val (gameProfile, skin) = profile ?: return
        val body = Figure(level, gameProfile, skin)
        body.id = ENTITY_ID
        body.isSilent = true
        LobbyCompat.snapEntity(body, feet, 0f)
        face(body, eye)
        //? if > 1.8.9 {
        level.addEntity(body)
        level.scoreboard.getPlayerTeam(TEAM)?.let { level.scoreboard.addPlayerToTeam(body.scoreboardName, it) }
        //?} else {
        /*level.forceEntity(body.id, body)
        level.scoreboard.addMemberToTeam(body.name, TEAM)
        *///?}
        figure = body
        shownLook = look
        peekDir = peek
        if (peek != null) stayLimit += PEEK_EXTRA_TICKS
        stayTicks = 0
        shownAt = System.nanoTime()
        state = State.STANDING
        shownLast = true
    }

    private fun vanish(level: ClientLevel) {
        figure?.let { body ->
            runCatching {
                LobbyCompat.removeEntity(level, body)
                //? if > 1.8.9 {
                level.scoreboard.removePlayerFromTeam(body.scoreboardName)
                //?} else {
                /*level.scoreboard.removeMemberFromTeam(body.name)
                *///?}
            }
        }
        figure = null
        state = State.GONE
    }

    private fun reset() {
        state = State.IDLE
        spot = null
        peekDir = null
        sightMisses = 0
        researched = false
        figure = null
        calmTicks = 0
        stayTicks = 0
        shownLook = null
    }

    private fun onboardingNear(): Boolean = LobbyFraming.onboarding || !PolyPlusConfig.onboardingCompleted

    private fun face(body: Figure, eye: Vec3) {
        val head = body.position().add(0.0, EYE, 0.0)
        val yaw = Math.toDegrees(atan2(-(eye.x - head.x), eye.z - head.z)).toFloat()
        val pitch = (-Math.toDegrees(atan2(eye.y - head.y, hypot(eye.x - head.x, eye.z - head.z)))).toFloat()
        body.yRot = yaw
        body.yRotO = yaw
        body.xRot = pitch
        body.xRotO = pitch
        body.yHeadRot = yaw
        body.yHeadRotO = yaw
        body.yBodyRot = yaw
        body.yBodyRotO = yaw
    }

    private fun eyeOf(rig: LocalPlayer): Vec3 = rig.position().add(0.0, rig.eyeHeight.toDouble(), 0.0)

    private fun lookOf(rig: LocalPlayer): Vec3 = lookOf(rig.yRot.toDouble(), rig.xRot.toDouble())

    private fun lookOf(yaw: Double, pitch: Double): Vec3 {
        val yawRad = Math.toRadians(yaw)
        val pitchRad = Math.toRadians(pitch)
        return Vec3(-sin(yawRad) * cos(pitchRad), -sin(pitchRad), cos(yawRad) * cos(pitchRad))
    }

    private fun visible(level: ClientLevel, eye: Vec3, feet: Vec3): Boolean {
        val head = feet.add(0.0, EYE, 0.0)
        return LobbyStage.reach(level, eye, head) >= eye.distanceTo(head) - SIGHT_SLACK
    }

    private class Reach(
        val near: Double,
        val far: Double,
        val minTurn: Double,
        val maxTurn: Double,
        val minScreenX: Double,
        val maxScreenX: Double,
        val maxScreenY: Double = MAX_SCREEN_Y,
        val clear: Double = CLEAR_OF_CAST,
        val step: Double = DISTANCE_STEP,
        val peeks: Boolean = true,
    )

    private val reaches = listOf(
        Reach(NEAR, FAR, MIN_TURN, MAX_TURN, MIN_SCREEN_X, MAX_SCREEN_X),
        Reach(WIDE_NEAR, WIDE_FAR, WIDE_MIN_TURN, WIDE_MAX_TURN, WIDE_MIN_SCREEN_X, WIDE_MAX_SCREEN_X),
    )

    private val lastResort = Reach(FORCED_NEAR, FORCED_FAR, 0.0, FORCED_MAX_TURN, 0.0, FORCED_MAX_SCREEN_X, FORCED_MAX_SCREEN_Y, FORCED_CLEAR, FORCED_STEP, false)

    private fun castOf(stage: LobbyStage): List<Vec3> = stage.slots.map { it.pos } + stage.background.map { it.pos }

    private fun search(level: ClientLevel, camera: Vec3, yaw: Double, pitch: Double, cast: List<Vec3>): Search {
        val passes = reaches.map { reach -> scan(level, camera, yaw, pitch, cast, reach) }
        val spot = passes.firstNotNullOfOrNull { pass -> pass.filter { it.spot.covered }.maxByOrNull { it.score }?.spot }
            ?: passes.firstNotNullOfOrNull { pass -> pass.maxByOrNull { it.score }?.spot }
        val total = passes.sumOf { it.size }
        val covered = passes.sumOf { pass -> pass.count { it.spot.covered } }
        if (spot != null || !forced) return Search(spot, total, covered)
        val rest = scan(level, camera, yaw, pitch, cast, lastResort)
        val fallback = rest.maxByOrNull { it.score }?.spot
        if (fallback != null) note("only the last-resort search found a spot")
        return Search(fallback, total + rest.size, covered)
    }

    private fun scan(level: ClientLevel, camera: Vec3, yaw: Double, pitch: Double, cast: List<Vec3>, reach: Reach): List<Candidate> =
        try {
            candidates(level, camera, yaw, pitch, cast, reach)
        } catch (e: Throwable) {
            failed("search for a spot", e)
            emptyList()
        }

    private fun candidates(level: ClientLevel, camera: Vec3, yaw: Double, pitch: Double, cast: List<Vec3>, reach: Reach): List<Candidate> {
        val look = lookOf(yaw, pitch)
        val right = look.cross(Vec3(0.0, 1.0, 0.0)).normalize()
        val up = right.cross(look)
        val tanHalf = tan(Math.toRadians(LobbyStage.CAMERA_FOV / 2.0))
        val found = ArrayList<Candidate>()
        for (side in listOf(-1.0, 1.0)) {
            var turn = reach.minTurn
            while (turn <= reach.maxTurn) {
                val heading = Math.toRadians(yaw + side * turn)
                var distance = reach.near
                while (distance <= reach.far) {
                    val x = camera.x - sin(heading) * distance
                    val z = camera.z + cos(heading) * distance
                    distance += reach.step
                    val feet = ground(level, x, z, camera.y) ?: continue
                    if (cast.any { it.distanceTo(feet) < reach.clear }) continue
                    if (!framed(feet, camera, look, right, up, tanHalf, reach)) continue
                    if (reach.peeks && shielded(level, camera, feet) && peekSide(level, camera, feet) != null) {
                        found += Candidate(Spot(feet, true), COVER_BONUS + cover(level, camera, feet) + shoulders(level, camera, feet) + Random.nextDouble())
                        continue
                    }
                    if (!visible(level, camera, feet)) continue
                    found += Candidate(Spot(feet, false), cover(level, camera, feet) + Random.nextDouble())
                }
                turn += TURN_STEP
            }
        }
        return found
    }

    private fun axes(from: Vec3, feet: Vec3): Pair<Vec3, Vec3>? {
        val dx = feet.x - from.x
        val dz = feet.z - from.z
        val length = hypot(dx, dz)
        if (length < 1.0) return null
        return Vec3(dx / length, 0.0, dz / length) to Vec3(-dz / length, 0.0, dx / length)
    }

    private fun offset(feet: Vec3, toward: Vec3, side: Vec3, across: Double, lift: Double): Vec3 =
        Vec3(feet.x + side.x * across - toward.x * FACE_DEPTH, feet.y + lift, feet.z + side.z * across - toward.z * FACE_DEPTH)

    private fun hidden(level: ClientLevel, from: Vec3, point: Vec3): Boolean =
        LobbyStage.reach(level, from, point) < from.distanceTo(point) - SIGHT_SLACK

    private fun exposed(level: ClientLevel, from: Vec3, point: Vec3): Boolean =
        LobbyStage.reach(level, from, point) >= from.distanceTo(point) - PEEK_SLACK

    private fun shielded(level: ClientLevel, from: Vec3, feet: Vec3): Boolean {
        val (toward, side) = axes(from, feet) ?: return false
        return listOf(0.0, EYE_SIDE, -EYE_SIDE).all { across ->
            val point = offset(feet, toward, side, across, EYE)
            val distance = from.distanceTo(point)
            val reached = LobbyStage.reach(level, from, point)
            reached < distance - SIGHT_SLACK && reached > distance - COVER_GAP
        }
    }

    private fun shoulders(level: ClientLevel, from: Vec3, feet: Vec3): Double {
        val (toward, side) = axes(from, feet) ?: return 0.0
        return listOf(SHOULDER_SIDE, -SHOULDER_SIDE).count { hidden(level, from, offset(feet, toward, side, it, SHOULDER)) }.toDouble()
    }

    private fun peekSide(level: ClientLevel, from: Vec3, feet: Vec3): Vec3? {
        val (toward, side) = axes(from, feet) ?: return null
        val lean = EYE * sin(LEAN_RADIANS)
        val lift = EYE * cos(LEAN_RADIANS)
        return listOf(side, Vec3(-side.x, 0.0, -side.z)).shuffled().firstOrNull { dir ->
            listOf(lean - EYE_SIDE, lean + EYE_SIDE).all { across -> exposed(level, from, offset(feet, toward, dir, across, lift)) }
        }
    }

    private fun framed(feet: Vec3, camera: Vec3, look: Vec3, right: Vec3, up: Vec3, tanHalf: Double, reach: Reach): Boolean =
        listOf(feet, feet.add(0.0, HEAD_TOP, 0.0)).all { point ->
            val offset = point.subtract(camera)
            val depth = offset.dot(look)
            if (depth <= 1.0) return@all false
            val screenX = abs(offset.dot(right) / (depth * tanHalf * ASPECT))
            val screenY = abs(offset.dot(up) / (depth * tanHalf))
            screenX in reach.minScreenX..reach.maxScreenX && screenY <= reach.maxScreenY
        }

    private fun cover(level: ClientLevel, camera: Vec3, feet: Vec3): Double {
        val hidden = listOf(0.3, 0.9).count { lift ->
            val point = feet.add(0.0, lift, 0.0)
            LobbyStage.reach(level, camera, point) < camera.distanceTo(point) - SIGHT_SLACK
        }
        return if (hidden == 2) 1.5 else hidden.toDouble()
    }

    private fun ground(level: ClientLevel, x: Double, z: Double, cameraY: Double): Vec3? {
        val top = LobbyCompat.blockAt(x, cameraY + SCAN_UP, z)
        for (drop in 0..SCAN_UP + SCAN_DOWN) {
            val pos = top.below(drop)
            if (!LobbyStage.canStand(level, pos)) continue
            val below = pos.below()
            val ground = level.getBlockState(below)
            if (ground.`is`(BlockTags.LEAVES) || ground.`is`(BlockTags.FENCES) || ground.`is`(BlockTags.WALLS)) return null
            val floor = below.y + ground.getCollisionShape(level, below).max(Direction.Axis.Y)
            return Vec3(pos.x + 0.5, floor, pos.z + 0.5)
        }
        return null
    }
}
