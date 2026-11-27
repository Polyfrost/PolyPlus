package org.polyfrost.polyplus.client.cosmetics

import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.phys.Vec3
//? if <= 1.8.9 {
/*import org.polyfrost.polyplus.client.lobby.id
import org.polyfrost.polyplus.client.lobby.level
import org.polyfrost.polyplus.client.lobby.yBodyRot
import org.polyfrost.polyplus.client.lobby.yHeadRot
import org.polyfrost.polyplus.client.lobby.yRot
*///?}
import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.polyplus.client.lobby.LobbyCompat
import org.polyfrost.polyplus.client.nameplate.NameplateWearers
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.pets.PetManager
import org.polyfrost.polyplus.events.WebSocketMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object DevCrowd {
    const val ALL = "all"
    const val RANDOM = "random"
    const val MAX = 256

    private const val ID_BASE = 0x50435244
    private const val SPACING = 2.5
    private const val SWAY_DEGREES = 30.0
    private const val SWAY_SPEED = 0.04
    private const val EMOTE_EVERY = 4
    private const val RANDOM_CHANCE = 0.65
    private const val RADIANS_TO_DEGREES = 180.0 / Math.PI

    class Spawned(val players: Int, val worn: Int, val emoting: Int)

    private class Member(val player: RemotePlayer, val baseYaw: Float, val phase: Double, val emoteId: Int?)

    private val members = LinkedHashMap<UUID, Member>()
    private val ids: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private var registered = false
    private var ticks = 0L

    fun isMember(uuid: UUID): Boolean = uuid in ids

    fun spawn(count: Int, pick: String): Result<Spawned> = runCatching {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: error("Join a world first.")
        val self = mc.player ?: error("Join a world first.")
        val pool = pool(pick)
        if (pool.slots.isEmpty() && pool.emotes.isEmpty()) {
            error(if (pick == ALL || pick == RANDOM) "Load some dev bundles first with /polyplus dev load all." else "No loaded dev bundle is called $pick or starts with it. Load it with /polyplus dev load first.")
        }
        register()
        clear()
        var worn = 0
        var emoting = 0
        val origin = Vec3(self.x, self.y, self.z)
        for ((index, cell) in cells(count).withIndex()) {
            val uuid = UUID.nameUUIDFromBytes("polyplus-dev-crowd-$index".toByteArray(Charsets.UTF_8))
            val dummy = RemotePlayer(level, GameProfile(uuid, "Crowd%03d".format(index + 1)))
            val pos = Vec3(origin.x + cell.first * SPACING, origin.y, origin.z + cell.second * SPACING)
            val yaw = (atan2(-(origin.x - pos.x), origin.z - pos.z) * RADIANS_TO_DEGREES).toFloat()
            dummy.id = ID_BASE + index
            LobbyCompat.snapEntity(dummy, pos, yaw)
            dummy.yHeadRot = yaw
            dummy.yBodyRot = yaw
            //? if > 1.8.9 {
            level.addEntity(dummy)
            //?} else {
            /*level.forceEntity(dummy.id, dummy)
            *///?}
            ids += uuid
            val outfit = pool.outfit(index, pick)
            worn += outfit.size
            CosmeticCatalog.setPolyPlusUser(uuid, true)
            CosmeticCatalog.applyRemoteEquipped(uuid, outfit)
            val emote = pool.emote(index)
            if (emote != null) {
                emoting++
                EventManager.INSTANCE.post(WebSocketMessage(ClientboundPacket.PlayerEmoteStarted(uuid.toString(), emote)))
            }
            members[uuid] = Member(dummy, yaw, index * 0.7, emote)
        }
        Spawned(members.size, worn, emoting)
    }

    fun clear(): Int {
        val removed = members.size
        val level = Minecraft.getInstance().level
        for ((uuid, member) in members) {
            forget(uuid, member)
            if (level != null) LobbyCompat.removeEntity(level, member.player)
        }
        members.clear()
        ids.clear()
        return removed
    }

    private fun forget(uuid: UUID, member: Member) {
        if (member.emoteId != null) EventManager.INSTANCE.post(WebSocketMessage(ClientboundPacket.PlayerEmoteStopped(uuid.toString())))
        CosmeticCatalog.removeRemote(uuid)
        NameplateWearers.forget(uuid)
        PetManager.despawn(uuid)
    }

    private fun register() {
        if (registered) return
        registered = true
        eventHandler<TickEvent.End> { sway() }
        eventHandler<WorldEvent.Unload> {
            for ((uuid, member) in members) forget(uuid, member)
            members.clear()
            ids.clear()
        }
    }

    private fun sway() {
        if (members.isEmpty()) return
        ticks++
        for (member in members.values) {
            val yaw = member.baseYaw + (SWAY_DEGREES * sin(ticks * SWAY_SPEED + member.phase)).toFloat()
            val dummy = member.player
            dummy.yRot = yaw
            dummy.yBodyRot = yaw
            dummy.yHeadRot = yaw
        }
    }

    private fun cells(count: Int): List<Pair<Int, Int>> {
        val radius = ceil(sqrt(count + 1.0) / 2.0).toInt() + 1
        val cells = ArrayList<Pair<Int, Int>>()
        for (x in -radius..radius) {
            for (z in -radius..radius) {
                if (x != 0 || z != 0) cells += x to z
            }
        }
        cells.sortWith(compareBy<Pair<Int, Int>> { it.first * it.first + it.second * it.second }.thenBy { it.first }.thenBy { it.second })
        return cells.take(count)
    }

    private class Pool(val slots: Map<BodySlot, List<Int>>, val emotes: List<Int>, val single: Boolean) {
        fun outfit(index: Int, pick: String): Map<BodySlot, Int> {
            if (pick != RANDOM) return slots.mapValues { (slot, ids) -> ids[(index + slot.ordinal) % ids.size] }
            val random = Random(index * 7919 + 17)
            val outfit = LinkedHashMap<BodySlot, Int>()
            for ((slot, ids) in slots) {
                if (random.nextDouble() < RANDOM_CHANCE) outfit[slot] = ids[random.nextInt(ids.size)]
            }
            return outfit
        }

        fun emote(index: Int): Int? {
            if (emotes.isEmpty()) return null
            if (single) return emotes[index % emotes.size]
            if (index % EMOTE_EVERY != EMOTE_EVERY - 1) return null
            return emotes[(index / EMOTE_EVERY) % emotes.size]
        }
    }

    private fun pool(pick: String): Pool {
        val names = DevBundles.loadedNames()
        val single = pick in names
        val chosen = names
            .filter { pick == ALL || pick == RANDOM || if (single) it == pick else it.startsWith(pick) }
            .mapNotNull { DevBundles.get(it)?.takeIf { loaded -> loaded.installed } }
        val slots = LinkedHashMap<BodySlot, MutableList<Int>>()
        val emotes = ArrayList<Int>()
        for (loaded in chosen) {
            val slot = loaded.slot
            when {
                loaded.definition.type == CosmeticType.Emote -> emotes += loaded.definition.id
                slot != null -> slots.getOrPut(slot) { ArrayList() } += loaded.definition.id
            }
        }
        return Pool(slots, emotes, single)
    }
}
