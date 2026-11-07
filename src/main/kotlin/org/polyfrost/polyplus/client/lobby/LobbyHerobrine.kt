package org.polyfrost.polyplus.client.lobby

import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.player.RemotePlayer
//? if > 1.8.9 {
import net.minecraft.client.resources.DefaultPlayerSkin
//?} else {
/*import net.minecraft.client.resource.skin.DefaultSkinUtils
*///?}
import net.minecraft.core.Direction
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
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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
    private const val DISTANCE_STEP = 3.0
    private const val MIN_TURN = 6.0
    private const val MAX_TURN = 22.0
    private const val TURN_STEP = 2.0
    private const val ASPECT = 1.6
    private const val MIN_SCREEN_X = 0.3
    private const val MAX_SCREEN_X = 0.72
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

    val EYES: Identifier = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/entity/herobrine_eyes.png")

    private enum class State { IDLE, WAITING, STANDING, GONE }

    private class Figure(level: ClientLevel, profile: GameProfile, private val skin: PlayerSkin) : RemotePlayer(level, profile) {
        //? if > 1.8.9 {
        override fun getSkin(): PlayerSkin = skin
        //?} else {
        /*override fun getSkinTextureLocation(): Identifier = skin.texture()

        override fun getModelType(): String = "default"

        override fun hasSkinTexture(): Boolean = true
        *///?}
    }

    private val profile: Pair<GameProfile, PlayerSkin>? by lazy {
        (0L until UUID_TRIES).asSequence().map { UUID(UUID_HIGH, it) }.firstNotNullOfOrNull { uuid ->
            defaultSkin(uuid).takeIf(::isSteve)?.let { GameProfile(uuid, NAME) to it }
        }
    }

    //? if >= 1.21.10 {
    private fun defaultSkin(uuid: UUID): PlayerSkin = DefaultPlayerSkin.get(uuid)

    private fun isSteve(skin: PlayerSkin): Boolean = skin.body().texturePath().path.contains("wide/steve")
    //?} elif > 1.8.9 {
    /*private fun defaultSkin(uuid: UUID): PlayerSkin = DefaultPlayerSkin.get(uuid)

    private fun isSteve(skin: PlayerSkin): Boolean = skin.texture().path.contains("wide/steve")
    *///?} else {
    /*private fun defaultSkin(uuid: UUID): PlayerSkin = PlayerSkin(DefaultSkinUtils.getDefaultSkin(uuid))

    private fun isSteve(skin: PlayerSkin): Boolean = skin.texture().getPath().endsWith("/steve.png")
    *///?}

    private var state = State.IDLE
    private var spot: Vec3? = null
    private var figure: Figure? = null
    private var calmTicks = 0
    private var dueTicks = 0
    private var stayTicks = 0
    private var stayLimit = 0
    private var shownLook: Vec3? = null
    private var shownLast = false
    private var heldOff = false

    fun shows(entityId: Int): Boolean = state == State.STANDING && entityId == ENTITY_ID

    fun onSceneShown(level: ClientLevel, stage: LobbyStage) {
        reset()
        val repeat = shownLast
        shownLast = false
        if (onboardingNear()) heldOff = true
        val forced = java.lang.Boolean.getBoolean(FORCE_PROPERTY)
        if (!forced && (heldOff || repeat || Random.nextDouble() >= CHANCE)) return
        if (profile == null) return
        spot = runCatching { pick(level, stage) }.getOrNull() ?: return
        dueTicks = Random.nextInt(MIN_DELAY_TICKS, MAX_DELAY_TICKS)
        stayLimit = Random.nextInt(MIN_STAY_TICKS, MAX_STAY_TICKS)
        state = State.WAITING
    }

    fun tick(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        if (onboardingNear()) heldOff = true
        when (state) {
            State.WAITING -> wait(level, rig, calm)
            State.STANDING -> stand(level, rig, calm)
            else -> Unit
        }
    }

    fun stop() {
        reset()
    }

    private fun wait(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        if (heldOff) {
            state = State.GONE
            return
        }
        if (!calm) {
            calmTicks = 0
            return
        }
        if (++calmTicks < dueTicks) return
        val feet = spot ?: return
        if (LobbyScene.unrevealed(floor(feet.x).toInt(), floor(feet.z).toInt())) return
        val eye = eyeOf(rig)
        if (!visible(level, eye, feet)) {
            state = State.GONE
            return
        }
        appear(level, feet, eye, lookOf(rig))
    }

    private fun stand(level: ClientLevel, rig: LocalPlayer, calm: Boolean) {
        val body = figure ?: return vanish(level)
        val look = lookOf(rig)
        val moved = shownLook?.let { it.dot(look) < cos(Math.toRadians(DRIFT_DEGREES)) } ?: true
        stayTicks++
        if (!calm || heldOff || (stayTicks >= stayLimit && moved) || stayTicks >= stayLimit + LINGER_TICKS) {
            vanish(level)
            return
        }
        face(body, eyeOf(rig))
    }

    private fun appear(level: ClientLevel, feet: Vec3, eye: Vec3, look: Vec3) {
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
        stayTicks = 0
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

    private fun pick(level: ClientLevel, stage: LobbyStage): Vec3? {
        val camera = stage.camera()
        val look = lookOf(stage.yaw.toDouble(), stage.pitch.toDouble())
        val right = look.cross(Vec3(0.0, 1.0, 0.0)).normalize()
        val up = right.cross(look)
        val tanHalf = tan(Math.toRadians(LobbyStage.CAMERA_FOV / 2.0))
        val cast = stage.slots.map { it.pos } + stage.background.map { it.pos }
        val found = ArrayList<Pair<Vec3, Double>>()
        for (side in listOf(-1.0, 1.0)) {
            var turn = MIN_TURN
            while (turn <= MAX_TURN) {
                val heading = Math.toRadians(stage.yaw + side * turn)
                var distance = NEAR
                while (distance <= FAR) {
                    val x = camera.x - sin(heading) * distance
                    val z = camera.z + cos(heading) * distance
                    distance += DISTANCE_STEP
                    val feet = ground(level, x, z, camera.y) ?: continue
                    if (cast.any { it.distanceTo(feet) < CLEAR_OF_CAST }) continue
                    if (!framed(feet, camera, look, right, up, tanHalf)) continue
                    if (!visible(level, camera, feet)) continue
                    found += feet to cover(level, camera, feet) + Random.nextDouble()
                }
                turn += TURN_STEP
            }
        }
        return found.maxByOrNull { it.second }?.first
    }

    private fun framed(feet: Vec3, camera: Vec3, look: Vec3, right: Vec3, up: Vec3, tanHalf: Double): Boolean =
        listOf(feet, feet.add(0.0, HEAD_TOP, 0.0)).all { point ->
            val offset = point.subtract(camera)
            val depth = offset.dot(look)
            if (depth <= 1.0) return@all false
            val screenX = abs(offset.dot(right) / (depth * tanHalf * ASPECT))
            val screenY = abs(offset.dot(up) / (depth * tanHalf))
            screenX in MIN_SCREEN_X..MAX_SCREEN_X && screenY <= MAX_SCREEN_Y
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
