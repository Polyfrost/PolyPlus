package org.polyfrost.polyplus.client.emotes.context

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
*///?}
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.DevBundles
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.EmoteRequirement
import org.polyfrost.polyplus.client.emotes.conditions.EmoteConditions
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.emotes.duo.DuoPlacement
import org.polyfrost.polyplus.client.emotes.duo.DuoPlayers
import org.polyfrost.polyplus.client.emotes.duo.Spot
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.pets.PetCollision
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin

object ContextEmotes {
    private val LOGGER = LogManager.getLogger("polyplus/emotes")

    internal class Target(val emoteId: Int, val anchor: EmoteAnchor, val spot: Spot, val until: Long)

    private class Found(val anchor: EmoteAnchor, val spot: Spot)

    @Volatile
    internal var target: Target? = null
        private set

    private val ray = PetCollision.Ray()

    fun begin(emoteId: Int): Boolean {
        target = null
        val emote = CosmeticAssetCache.getEmote(emoteId) ?: return false
        if (emote.isDuo || emote.requirement == EmoteRequirement.FLOOR) return false
        val local = DuoPlayers.local() ?: return false
        val found = level()?.let { find(it, local, emote.requirement) }
        when {
            found != null -> target = Target(emoteId, found.anchor, found.spot, System.currentTimeMillis() + REACH_MS)
            emote.hasFallback -> play(emoteId, null)
            else -> DuoEmotes.tell(refusal(emote.requirement))
        }
        return true
    }

    fun cancel(): Boolean {
        EmoteApi.dropQueuedPlays()
        if (target == null) return false
        target = null
        return true
    }

    internal fun tick(now: Long) {
        val current = target ?: return
        val local = DuoPlayers.local()
        if (local == null) {
            target = null
            return
        }
        val distance = current.spot.distanceTo(DuoPlayers.x(local), DuoPlayers.y(local), DuoPlayers.z(local))
        when {
            distance <= START_DISTANCE && settled(local, current.emoteId) -> {
                target = null
                start(local, current)
            }
            distance > GIVE_UP_DISTANCE -> {
                target = null
                DuoEmotes.tell(WALKED_AWAY)
            }
            now >= current.until -> {
                target = null
                DuoEmotes.tell(TOO_SLOW)
            }
        }
    }

    private fun settled(player: AbstractClientPlayer, emoteId: Int): Boolean {
        if (EmoteConditions.interrupts(player)) return false
        val signals = PlayerSignals.sample(player, 1f)
        val emote = CosmeticAssetCache.getEmote(emoteId) ?: return !signals.flag(PlayerSignals.IS_MOVING)
        return !emote.restrains(signals)
    }

    private fun start(player: AbstractClientPlayer, current: Target) {
        val emote = CosmeticAssetCache.getEmote(current.emoteId)
        val requirement = emote?.requirement ?: EmoteRequirement.FLOOR
        val fresh = level()?.let { recheck(it, player, requirement, current.anchor) }
        when {
            fresh != null -> play(current.emoteId, fresh)
            emote?.hasFallback == true -> play(current.emoteId, null)
            else -> DuoEmotes.tell(refusal(requirement))
        }
    }

    private fun recheck(level: ClientLevel, player: AbstractClientPlayer, requirement: EmoteRequirement, anchor: EmoteAnchor): EmoteAnchor? {
        if (!PlayerSignals.sample(player, 1f).flag(PlayerSignals.IS_ON_GROUND)) return null
        val radians = Math.toRadians(anchor.yaw.toDouble())
        val dx = Math.round(-sin(radians)).toInt()
        val dz = Math.round(cos(radians)).toInt()
        if (abs(dx) + abs(dz) != 1) return null
        val x = DuoPlayers.x(player)
        val y = DuoPlayers.y(player)
        val z = DuoPlayers.z(player)
        val found = when (requirement) {
            EmoteRequirement.LEDGE -> ledge(level, x, y, z, dx, dz)
            EmoteRequirement.WALL -> wallAt(level, x, y, z, -dx, -dz)
            EmoteRequirement.FLOOR -> null
        } ?: return null
        val moved = found.anchor
        val gapX = moved.x - anchor.x
        val gapZ = moved.z - anchor.z
        if (gapX * gapX + gapZ * gapZ > ANCHOR_SLACK * ANCHOR_SLACK || abs(moved.y - anchor.y) > ANCHOR_RISE) return null
        return moved
    }

    private fun play(emoteId: Int, anchor: EmoteAnchor?) {
        if (DevBundles.playEmote(emoteId, anchor)) return
        DuoEmotes.cancelForSoloPlay()
        val ticket = EmoteApi.playTicket()
        PolyPlusClient.SCOPE.launch {
            CosmeticService.playEmote(emoteId, anchor, ticket).onFailure {
                if (it is EmoteApi.PlayCancelled) return@onFailure
                LOGGER.warn("Couldn't play emote {}", emoteId, it)
                DuoEmotes.tell(COULD_NOT_SEND)
            }
        }
    }

    private fun find(level: ClientLevel, player: AbstractClientPlayer, requirement: EmoteRequirement): Found? {
        if (!PlayerSignals.sample(player, 1f).flag(PlayerSignals.IS_ON_GROUND)) return null
        val radians = Math.toRadians(DuoPlayers.lookYaw(player).toDouble())
        val lookX = -sin(radians)
        val lookZ = cos(radians)
        val dx = if (abs(lookX) >= abs(lookZ)) sign(lookX).toInt() else 0
        val dz = if (dx == 0) sign(lookZ).toInt() else 0
        val x = DuoPlayers.x(player)
        val y = DuoPlayers.y(player)
        val z = DuoPlayers.z(player)
        return when (requirement) {
            EmoteRequirement.LEDGE -> ledge(level, x, y, z, dx, dz)
            EmoteRequirement.WALL -> wall(level, x, y, z, dx, dz)
            EmoteRequirement.FLOOR -> null
        }
    }

    private fun ledge(level: ClientLevel, x: Double, y: Double, z: Double, dx: Int, dz: Int): Found? {
        if (PetCollision.ray(level, x, y + BODY_HEIGHT, z, x + dx * LEDGE_REACH, y + BODY_HEIGHT, z + dz * LEDGE_REACH, ray)) {
            return null
        }
        var forward = LEDGE_FIRST
        while (forward <= LEDGE_REACH + 1e-6) {
            val px = x + dx * forward
            val pz = z + dz * forward
            val top = y + PROBE_LIFT
            val bottom = y - PROBE_DEPTH
            if (!PetCollision.ray(level, px, top, pz, px, bottom, pz, ray)) return null
            val drop = y - (top + (bottom - top) * ray.fraction)
            if (drop > MAX_DROP) return null
            if (drop < -STEP_TOLERANCE) return null
            if (drop >= MIN_DROP) return edge(level, x, y, z, px, pz, dx, dz)
            forward += LEDGE_STEP
        }
        return null
    }

    private fun edge(level: ClientLevel, x: Double, y: Double, z: Double, px: Double, pz: Double, dx: Int, dz: Int): Found {
        val height = y - EDGE_DIP
        val hit = PetCollision.ray(level, px, height, pz, x, height, z, ray) && ray.normalX == dx && ray.normalZ == dz
        val fraction = if (hit) ray.fraction else 0.0
        val ex = px + (x - px) * fraction
        val ez = pz + (z - pz) * fraction
        val yaw = yawOf(dx, dz)
        return Found(
            EmoteAnchor(ex, y, ez, yaw),
            Spot(ex - dx * LEDGE_STAND_BACK, y, ez - dz * LEDGE_STAND_BACK, yaw),
        )
    }

    private fun wall(level: ClientLevel, x: Double, y: Double, z: Double, dx: Int, dz: Int): Found? {
        var best: Found? = null
        var bestDistance = Double.MAX_VALUE
        for ((wx, wz) in arrayOf(-dx to -dz, dz to -dx, -dz to dx)) {
            val found = wallAt(level, x, y, z, wx, wz) ?: continue
            val distance = abs(found.anchor.x - x) + abs(found.anchor.z - z)
            if (distance >= bestDistance) continue
            bestDistance = distance
            best = found
        }
        return best
    }

    private fun wallAt(level: ClientLevel, x: Double, y: Double, z: Double, wx: Int, wz: Int): Found? {
        val distance = face(level, x, y + WALL_LOW, z, wx, wz) ?: return null
        if (face(level, x, y + WALL_HIGH, z, wx, wz) == null) return null
        val ax = x + wx * distance
        val az = z + wz * distance
        val yaw = yawOf(-wx, -wz)
        return Found(
            EmoteAnchor(ax, y, az, yaw),
            Spot(ax - wx * WALL_STAND_OFF, y, az - wz * WALL_STAND_OFF, yaw),
        )
    }

    private fun face(level: ClientLevel, x: Double, y: Double, z: Double, wx: Int, wz: Int): Double? {
        if (!PetCollision.ray(level, x, y, z, x + wx * WALL_REACH, y, z + wz * WALL_REACH, ray)) return null
        if (ray.normalX != -wx || ray.normalZ != -wz) return null
        return ray.fraction * WALL_REACH
    }

    private fun yawOf(dx: Int, dz: Int): Float =
        DuoPlacement.wrap(Math.toDegrees(atan2(-dx.toDouble(), dz.toDouble())).toFloat())

    private fun level(): ClientLevel? =
        //? if > 1.8.9 {
        Minecraft.getInstance().level
        //?} else {
        /*Minecraft.getInstance().world
        *///?}

    private fun refusal(requirement: EmoteRequirement): String = when (requirement) {
        EmoteRequirement.LEDGE -> NEEDS_LEDGE
        else -> NEEDS_WALL
    }

    private const val REACH_MS = 20_000L
    private const val START_DISTANCE = 0.3
    private const val ANCHOR_SLACK = 0.35
    private const val ANCHOR_RISE = 0.1
    private const val GIVE_UP_DISTANCE = 6.0
    private const val BODY_HEIGHT = 0.6
    private const val LEDGE_FIRST = 0.35
    private const val LEDGE_STEP = 0.1
    private const val LEDGE_REACH = 1.25
    private const val PROBE_LIFT = 0.05
    private const val PROBE_DEPTH = 1.0
    private const val MIN_DROP = 0.4
    private const val MAX_DROP = 0.6
    private const val STEP_TOLERANCE = 0.05
    private const val EDGE_DIP = 0.05
    private const val LEDGE_STAND_BACK = 0.3
    private const val WALL_LOW = 0.4
    private const val WALL_HIGH = 1.3
    private const val WALL_REACH = 0.8
    private const val WALL_STAND_OFF = 0.3
    private const val NEEDS_LEDGE = "Stand at the edge of a ledge about half a block high"
    private const val NEEDS_WALL = "Stand with your back or side to a wall"
    private const val WALKED_AWAY = "Emote cancelled. You walked away from the spot"
    private const val TOO_SLOW = "You didn't reach the spot in time"
    private const val COULD_NOT_SEND = "Couldn't reach PolyPlus. Try again in a moment."
}
