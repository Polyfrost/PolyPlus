package org.polyfrost.polyplus.client.cosmetics.moments

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import java.util.UUID
import kotlin.math.abs

//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientLevel
//?} else {
/*import net.minecraft.client.world.ClientWorld
*///?}

object Moments {
    private const val COOLDOWN_MS = 10_000L
    private const val JOIN_WINDOW_MS = 10_000L
    private const val JOIN_QUIET_MS = 5_000L
    private const val RESPAWN_WINDOW_MS = 60_000L
    private const val NEVER = Long.MIN_VALUE

    private const val PENDING_MS = 10_000L
    private const val MAX_PENDING = 64
    private const val EVENT_BUCKET_TICKS = 200L
    private val MOMENT_SLOT = BodySlot.EffectB

    private val LOGGER = LogManager.getLogger("${PolyPlusConstants.NAME}/Moments")

    private class Tracked {
        var entity: Any? = null
        var present = false
        var dying = false
        var hidden = false
        var listed = false
        var listedAt = NEVER
        var joinPending = false
        var appearedAt = NEVER
        var diedAt = NEVER
        var seenAt = NEVER
        var x = 0.0
        var y = 0.0
        var z = 0.0
        var yaw = 0f
        var stamp = 0L
    }

    private class Pending(
        val moment: Moment,
        val wearer: UUID,
        val cosmeticId: Int?,
        val detached: Boolean,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val queuedAt: Long,
        val sequence: Int,
    )

    private class Seen(
        val uuid: UUID,
        val entity: Any,
        val dying: Boolean,
        val hidden: Boolean,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
    )

    private val tracked = HashMap<UUID, Tracked>()
    private val cooldowns = MomentCooldowns(Moment.entries.size, COOLDOWN_MS)
    private val pending = ArrayList<Pending>()
    private var lastLevel: Any? = null
    private var quietUntil = NEVER
    private var levelTick = 0L
    private var localRespawn = false
    private var scanStamp = 0L

    fun initialize() {
        MomentEffects.current = CosmeticMoments
        MomentChannel.initialize()
        eventHandler { _: TickEvent.End -> tick() }.register()
    }

    @JvmStatic
    fun onRespawnPacket() {
        val player = Minecraft.getInstance().player ?: return
        //? if > 1.8.9 {
        if (player.isDeadOrDying) localRespawn = true
        //?} else {
        /*if (player.health <= 0f) localRespawn = true
        *///?}
    }

    internal fun onKill(killer: UUID, victim: UUID) {
        val now = System.currentTimeMillis()
        val level = currentLevel(Minecraft.getInstance()) ?: return
        enterLevel(level, now)
        val live = livePlayer(level, victim)
        val place = live?.let { placeOf(it, now) } ?: tracked[victim] ?: return
        val hidden = killVictimHidden(live?.hidden, place.hidden)
        if (!killPlaceKnown(live != null || place.present, hidden, place.seenAt, now)) return
        trigger(Moment.Kill, killer, place, detached = true, now)
    }

    private fun placeOf(player: Seen, now: Long): Tracked = Tracked().also {
        it.x = player.x
        it.y = player.y
        it.z = player.z
        it.yaw = player.yaw
        it.hidden = player.hidden
        it.seenAt = now
    }

    //? if > 1.8.9 {
    private fun currentLevel(mc: Minecraft): ClientLevel? = mc.level
    //?} else {
    /*private fun currentLevel(mc: Minecraft): ClientWorld? = mc.world
    *///?}

    private fun enterLevel(level: Any, now: Long) {
        if (level === lastLevel) return
        lastLevel = level
        CosmeticMoments.clear()
        pending.clear()
        quietUntil = now + JOIN_QUIET_MS
        for (t in tracked.values) {
            t.entity = null
            t.present = false
            t.dying = false
            t.seenAt = NEVER
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        cooldowns.prune(now)
        val mc = Minecraft.getInstance()
        val level = currentLevel(mc)
        if (level == null) {
            tracked.clear()
            pending.clear()
            lastLevel = null
            localRespawn = false
            CosmeticMoments.clear()
            return
        }
        //? if > 1.8.9 {
        levelTick = level.gameTime
        //?} else {
        /*levelTick = level.time
        *///?}
        enterLevel(level, now)

        updateList(listedPlayers(mc), now)

        val local = mc.player?.uuid
        scanStamp++
        val matched = scanPlayers(level, now, local)
        if (localRespawn && local != null) respawnLocal(local, now)

        if (matched != tracked.size) sweepAbsent(now)

        retryPending(now)
    }

    private fun sweepAbsent(now: Long) {
        val iterator = tracked.values.iterator()
        while (iterator.hasNext()) {
            val t = iterator.next()
            if (t.stamp != scanStamp) {
                t.entity = null
                t.present = false
                t.dying = false
            }
            val waitingForRespawn = t.diedAt != NEVER && now - t.diedAt <= RESPAWN_WINDOW_MS
            if (!t.listed && !t.present && !waitingForRespawn) iterator.remove()
        }
    }

    private fun retryPending(now: Long) {
        if (pending.isEmpty()) return
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val waiting = iterator.next()
            if (now - waiting.queuedAt > PENDING_MS || fire(waiting, now)) iterator.remove()
        }
    }

    private fun updateList(listed: Collection<UUID>, now: Long) {
        val quiet = now < quietUntil
        for (uuid in listed) {
            val t = tracked.getOrPut(uuid, ::Tracked)
            if (t.listed) continue
            t.listed = true
            t.listedAt = now
            t.joinPending = !quiet
        }
        if (tracked.size == listed.size) return
        val set = listed as? Set<UUID> ?: listed.toHashSet()
        for ((uuid, t) in tracked) {
            if (uuid in set || !t.listed) continue
            t.listed = false
            t.joinPending = false
        }
    }

    private fun respawnLocal(local: UUID, now: Long) {
        val t = tracked[local]?.takeIf { it.present && !it.dying } ?: return
        localRespawn = false
        t.diedAt = NEVER
        trigger(Moment.Respawn, local, t, detached = false, now)
    }

    private fun update(
        t: Tracked,
        uuid: UUID,
        entity: Any,
        dying: Boolean,
        hidden: Boolean,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float,
        now: Long,
        local: Boolean,
    ) {
        t.x = x
        t.y = y
        t.z = z
        t.yaw = yaw
        t.hidden = hidden
        t.seenAt = now
        if (t.entity !== entity) {
            t.entity = entity
            if (!t.present) {
                t.present = true
                t.appearedAt = now
            }
            if (!local && !dying && t.diedAt != NEVER && now - t.diedAt <= RESPAWN_WINDOW_MS) {
                t.diedAt = NEVER
                trigger(Moment.Respawn, uuid, t, detached = false, now)
            }
            t.dying = dying
        } else {
            if (dying && !t.dying) {
                t.diedAt = now
                if (!hidden) trigger(Moment.Death, uuid, t, detached = true, now)
            }
            t.dying = dying
        }

        if (t.joinPending) {
            if (now - t.listedAt > JOIN_WINDOW_MS && (!t.present || abs(t.appearedAt - t.listedAt) > JOIN_WINDOW_MS)) {
                t.joinPending = false
            } else if (t.present && abs(t.appearedAt - t.listedAt) <= JOIN_WINDOW_MS && now >= quietUntil) {
                t.joinPending = false
                trigger(Moment.Join, uuid, t, detached = false, now)
            }
        }
    }

    private fun trigger(moment: Moment, wearer: UUID, place: Tracked, detached: Boolean, now: Long) {
        if (coolingDown(moment, wearer, now)) return
        val cosmeticId = if (CosmeticSync.equipmentKnown(wearer)) eligibleId(wearer) ?: return else null
        val sequence = Math.floorDiv(levelTick, EVENT_BUCKET_TICKS).toInt()
        val waiting = Pending(moment, wearer, cosmeticId, detached, place.x, place.y, place.z, place.yaw, now, sequence)
        pending.removeIf { it.moment == moment && it.wearer == wearer }
        if (fire(waiting, now)) return
        if (pending.size >= MAX_PENDING) pending.removeAt(0)
        pending += waiting
    }

    private fun coolingDown(moment: Moment, wearer: UUID, now: Long): Boolean = cooldowns.coolingDown(wearer, moment.ordinal, now)

    private fun fire(waiting: Pending, now: Long): Boolean {
        val id = waiting.cosmeticId ?: CosmeticCatalog.getActiveId(waiting.wearer, MOMENT_SLOT) ?: return false
        val definition = effectDefinition(id, waiting.wearer) ?: return false
        val play = MomentPlay(
            moment = waiting.moment,
            wearer = waiting.wearer,
            slot = MOMENT_SLOT,
            cosmeticId = definition.id,
            colorway = definition.variantKey,
            customColors = CosmeticColors.get(waiting.wearer, definition.id),
            detached = waiting.detached,
            x = waiting.x,
            y = waiting.y,
            z = waiting.z,
            yaw = waiting.yaw,
            sequence = waiting.sequence,
        )
        val played = try {
            MomentEffects.current.play(play)
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not play the {} effect of cosmetic {}", waiting.moment.effectName, definition.id, ex)
            true
        }
        if (played) cooldowns.record(waiting.wearer, waiting.moment.ordinal, now)
        return played
    }

    private fun eligibleId(wearer: UUID): Int? {
        val id = CosmeticCatalog.getActiveId(wearer, MOMENT_SLOT) ?: return null
        if (!PolyPlusCosmeticsConfig.isVisible(MOMENT_SLOT, wearer)) return null
        val type = CosmeticCatalog.getCosmeticDefinition(id)?.type
        return id.takeIf { type == null || type == CosmeticType.Effect }
    }

    private fun effectDefinition(id: Int, wearer: UUID): CosmeticDefinition? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return null
        if (definition.type != CosmeticType.Effect) return null
        if (!PolyPlusCosmeticsConfig.isVisible(MOMENT_SLOT, wearer)) return null
        return definition
    }

    //? if > 1.8.9 {
    private fun listedPlayers(mc: Minecraft): Collection<UUID> = mc.connection?.onlinePlayerIds ?: emptyList()

    private fun scanPlayers(level: ClientLevel, now: Long, local: UUID?): Int {
        val players = level.players()
        var matched = 0
        for (i in players.indices) {
            val player = players[i]
            val t = tracked.getOrPut(player.uuid, ::Tracked)
            if (t.stamp != scanStamp) {
                t.stamp = scanStamp
                matched++
            }
            update(t, player.uuid, player, player.deathTime > 0, player.isInvisible || player.isSpectator, player.x, player.y, player.z, player.yRot, now, player.uuid == local)
        }
        return matched
    }

    private fun livePlayer(level: ClientLevel, uuid: UUID): Seen? =
        level.getPlayerByUUID(uuid)?.let { Seen(it.uuid, it, it.deathTime > 0, it.isInvisible || it.isSpectator, it.x, it.y, it.z, it.yRot) }
    //?} else {
    /*private fun listedPlayers(mc: Minecraft): Collection<UUID> =
        mc.networkHandler?.onlinePlayers?.map { it.profile.id } ?: emptyList()

    private fun scanPlayers(level: ClientWorld, now: Long, local: UUID?): Int {
        val players = level.players
        var matched = 0
        for (i in players.indices) {
            val player = players[i]
            val t = tracked.getOrPut(player.uuid, ::Tracked)
            if (t.stamp != scanStamp) {
                t.stamp = scanStamp
                matched++
            }
            update(t, player.uuid, player, player.deathTicks > 0, player.isInvisible || player.isSpectator, player.x, player.y, player.z, player.yaw, now, player.uuid == local)
        }
        return matched
    }

    private fun livePlayer(level: ClientWorld, uuid: UUID): Seen? =
        level.players.firstOrNull { it.uuid == uuid }?.let { Seen(it.uuid, it, it.deathTicks > 0, it.isInvisible || it.isSpectator, it.x, it.y, it.z, it.yaw) }
    *///?}
}

private const val KILL_POSITION_MS = 5_000L

internal fun killVictimHidden(live: Boolean?, cached: Boolean): Boolean = live ?: cached

internal fun killPlaceKnown(present: Boolean, hidden: Boolean, seenAt: Long, now: Long): Boolean =
    !hidden && (present || (seenAt != Long.MIN_VALUE && now - seenAt in 0..KILL_POSITION_MS))
