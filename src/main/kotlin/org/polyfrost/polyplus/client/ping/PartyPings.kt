package org.polyfrost.polyplus.client.ping

import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.lobby.currentServer
import org.polyfrost.polyplus.client.lobby.level
*///?}
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object PartyPings {
    private val LOGGER = LogManager.getLogger()

    const val LIFETIME_MS = 8000L
    const val MAX_PER_SENDER = 3
    const val SEND_LIMIT = 6
    const val SEND_WINDOW_MS = 4000L

    private const val CLEAR_LIMIT = 10
    private const val REPLY_LIMIT = 8
    private const val MAX_LABEL = 48
    private const val MAX_FIELD = 64
    private const val MAX_COORD = 30_000_000.0
    private const val ERROR_WINDOW_MS = 10_000L
    private val QUIET_ERRORS = setOf("not_in_party", "invalid_ping", "rate_limited")

    private class Entry(val ping: Ping, val server: String, val shared: Boolean)

    private val lock = Any()
    private val entries = ArrayList<Entry>()
    private val sendTimes = ArrayDeque<Long>()
    private val clearTimes = ArrayDeque<Long>()
    private val replyTimes = ArrayDeque<Long>()
    private val replies = HashMap<String, MutableList<PingResponse>>()

    @Volatile private var lastPingPacketAt = 0L
    @Volatile private var currentKey: String? = null
    @Volatile private var currentDim: String? = null
    @Volatile private var currentLocal: String? = null
    private var currentPartyId: String? = null

    private var hashedInput: String? = null
    private var hashedKey: String? = null

    private val _pings = MutableStateFlow<List<Ping>>(emptyList())
    val pings: StateFlow<List<Ping>> = _pings.asStateFlow()

    init {
        PolyPlusClient.SCOPE.launch {
            ClientPlatform.runOnMain { eventHandler<TickEvent.End> { tick() } }
        }
    }

    fun send(kind: PingKind, dimension: String, x: Double, y: Double, z: Double, target: PingTarget?, label: String?): Boolean {
        val key = refreshContext()
        val local = currentLocal ?: return false
        if (!validCoord(x) || !validCoord(y) || !validCoord(z)) return false
        if (dimension.isEmpty() || dimension.length > MAX_FIELD) return false
        val now = System.currentTimeMillis()
        if (!synchronized(lock) { hasRoom(sendTimes, SEND_LIMIT, now) }) return false

        val id = UUID.randomUUID().toString()
        val cleanTarget = target?.let { PingTarget(it.type, it.id?.takeIf(::fits), it.entity?.takeIf(::fits)) }
        val cleanLabel = label?.let(::cleanLabel)
        val shared = key != null && canShare() &&
            PolyConnection.sendPacket(ServerboundPacket.SendPing(id, key, dimension, x, y, z, kind, cleanTarget, cleanLabel)).isSuccess
        if (shared) lastPingPacketAt = now

        val ping = Ping(id, PartyRepository.localId(), true, kind, dimension, x, y, z, cleanTarget, cleanLabel, now, now + LIFETIME_MS)
        synchronized(lock) {
            sendTimes.addLast(now)
            add(Entry(ping, if (shared) key!! else local, shared))
        }
        publish(now)
        return true
    }

    fun sharesPings(): Boolean = refreshContext() != null && canShare()

    private fun canShare(): Boolean = PartyRepository.party.value != null && PolyConnection.isConnected

    fun clear(id: String) {
        val removed = synchronized(lock) {
            val index = entries.indexOfFirst { it.ping.own && it.ping.id == id }
            if (index >= 0) entries.removeAt(index) else null
        } ?: return
        val now = System.currentTimeMillis()
        publish(now)
        if (!removed.shared) return
        if (!synchronized(lock) { hasRoom(clearTimes, CLEAR_LIMIT, now) }) return
        if (PolyConnection.sendPacket(ServerboundPacket.ClearPing(removed.ping.id)).isSuccess) {
            lastPingPacketAt = now
            synchronized(lock) { clearTimes.addLast(now) }
        }
    }

    fun canReply(ping: Ping): Boolean {
        if (ping.own || !canShare()) return false
        return synchronized(lock) { entries.any { !it.ping.own && it.shared && it.ping.id == ping.id } }
    }

    fun myReply(id: String): PingReply? = synchronized(lock) {
        replies[id]?.firstOrNull { PartyRepository.isSelf(it.from) }?.reply
    }

    fun reply(ping: Ping, reply: PingReply): Boolean {
        if (!canReply(ping)) return false
        val now = System.currentTimeMillis()
        if (!synchronized(lock) { hasRoom(replyTimes, REPLY_LIMIT, now) }) return false
        if (PolyConnection.sendPacket(ServerboundPacket.ReplyPing(ping.id, ping.from, reply)).isFailure) return false
        lastPingPacketAt = now
        synchronized(lock) {
            replyTimes.addLast(now)
            putReply(ping.id, PingResponse(PartyRepository.localId(), reply, now))
        }
        publish(now)
        return true
    }

    fun currentDimension(): String? {
        val level = Minecraft.getInstance().level ?: return null
        //? if >= 1.21.11 {
        return level.dimension().identifier().toString()
        //?} elif > 1.8.9 {
        /*return level.dimension().location().toString()
        *///?} else {
        /*return when (val id = level.dimension.id) {
            0 -> "minecraft:overworld"
            -1 -> "minecraft:the_nether"
            1 -> "minecraft:the_end"
            else -> "minecraft:dim$id"
        }
        *///?}
    }

    fun handle(packet: ClientboundPacket) {
        when (packet) {
            is ClientboundPacket.PartyPing -> ClientPlatform.runOnMain { onPing(packet) }
            is ClientboundPacket.PartyPingCleared -> ClientPlatform.runOnMain { onCleared(packet) }
            is ClientboundPacket.PartyPingReply -> ClientPlatform.runOnMain { onReply(packet) }
            else -> Unit
        }
    }

    fun handleError(packet: ClientboundPacket.Error): Boolean {
        if (packet.code !in QUIET_ERRORS) return false
        if (System.currentTimeMillis() - lastPingPacketAt > ERROR_WINDOW_MS) return false
        LOGGER.debug("Ping rejected by Poly+ ({}): {}", packet.code, packet.message)
        return true
    }

    private fun onPing(packet: ClientboundPacket.PartyPing) {
        val party = PartyRepository.party.value ?: return
        val key = refreshContext() ?: return
        if (packet.server != key || !allowed(packet.from, party)) return
        if (!validCoord(packet.x) || !validCoord(packet.y) || !validCoord(packet.z)) return
        val now = System.currentTimeMillis()
        val ping = Ping(
            packet.id,
            packet.from,
            false,
            packet.kind,
            packet.dimension,
            packet.x,
            packet.y,
            packet.z,
            packet.target,
            packet.label?.let(::cleanLabel),
            packet.sentAt,
            now + LIFETIME_MS,
        )
        synchronized(lock) {
            if (entries.any { it.ping.own && it.ping.id == packet.id }) return
            add(Entry(ping, key, true))
        }
        publish(now)
    }

    private fun onCleared(packet: ClientboundPacket.PartyPingCleared) {
        val removed = synchronized(lock) {
            entries.removeAll { !it.ping.own && it.ping.id == packet.id && PartyRepository.samePlayer(it.ping.from, packet.from) }
        }
        if (removed) publish(System.currentTimeMillis())
    }

    private fun onReply(packet: ClientboundPacket.PartyPingReply) {
        val party = PartyRepository.party.value ?: return
        val self = PartyRepository.isSelf(packet.from)
        if (!self && !allowed(packet.from, party)) return
        val now = System.currentTimeMillis()
        val cue = synchronized(lock) {
            val entry = entries.firstOrNull { it.shared && it.ping.id == packet.id } ?: return
            val owner = if (entry.ping.own) PartyRepository.isSelf(packet.owner) else PartyRepository.samePlayer(entry.ping.from, packet.owner)
            if (!owner) return
            val previous = replies[packet.id]?.firstOrNull { PartyRepository.samePlayer(it.from, packet.from) }?.reply
            putReply(packet.id, PingResponse(packet.from, packet.reply, now))
            entry.ping.own && !self && previous != packet.reply && (packet.reply == PingReply.COMING || packet.reply == PingReply.SEEN)
        }
        publish(now)
        if (cue) PingInput.replyCue()
    }

    private fun putReply(id: String, response: PingResponse) {
        val list = replies.getOrPut(id) { ArrayList() }
        list.removeAll { PartyRepository.samePlayer(it.from, response.from) }
        if (response.reply != PingReply.NONE) list.add(response)
        if (list.isEmpty()) replies.remove(id)
    }

    private fun tick() {
        refreshContext()
        val party = PartyRepository.party.value
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (!PolyConnection.isConnected) entries.removeAll { it.shared || !it.ping.own }
            entries.removeAll { it.ping.expiresAt <= now || (!it.ping.own && (party == null || !allowed(it.ping.from, party))) }
            for (list in replies.values) list.removeAll { !PartyRepository.isSelf(it.from) && (party == null || !allowed(it.from, party)) }
        }
        publish(now)
    }

    private fun refreshContext(): String? {
        val partyId = PartyRepository.party.value?.id
        val key = partyId?.let { id -> place()?.let { hash("$id|$it") } }
        val dimension = currentDimension()
        val local = localPlace()
        synchronized(lock) {
            if (local != currentLocal) entries.clear()
            else if (partyId != currentPartyId || key != currentKey) entries.removeAll { it.server != local }
            currentPartyId = partyId
            currentKey = key
            currentDim = dimension
            currentLocal = local
        }
        return key
    }

    private fun localPlace(): String? {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return null
        if (LobbyScene.terrainActive()) return null
        return "local:" + System.identityHashCode(level)
    }

    private fun place(): String? {
        val mc = Minecraft.getInstance()
        if (mc.level == null || LobbyScene.terrainActive()) return null
        //? if > 1.8.9 {
        val integrated = mc.hasSingleplayerServer()
        //?} else {
        /*val integrated = mc.isIntegratedServerRunning
        *///?}
        if (integrated) return P2PSessionManager.currentSessionId?.let { "p2p:$it" }
        val address = mc.currentServer?.ip?.trim() ?: return null
        if (address == P2PSessionManager.P2P_PLACEHOLDER_IP) return P2PSessionManager.joinedSessionId?.let { "p2p:$it" }
        return address.lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }

    private fun hash(input: String): String = synchronized(lock) {
        if (input == hashedInput) return@synchronized hashedKey!!
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { "%02x".format(it) }
        hashedInput = input
        hashedKey = key
        key
    }

    private fun allowed(from: String, party: Party): Boolean {
        if (PartyRepository.isSelf(from)) return false
        if (party.members.none { PartyRepository.samePlayer(it.player, from) }) return false
        if (FriendsRepository.blocked.value.any { PartyRepository.samePlayer(it.player, from) }) return false
        return !PartyVoiceSettings.isMuted(from)
    }

    private fun add(entry: Entry) {
        entries.removeAll { it.ping.id == entry.ping.id }
        entries.add(entry)
        val sender = entry.ping.from
        while (entries.count { PartyRepository.samePlayer(it.ping.from, sender) } > MAX_PER_SENDER) {
            val oldest = entries.indexOfFirst { PartyRepository.samePlayer(it.ping.from, sender) }
            entries.removeAt(oldest)
        }
    }

    private fun publish(now: Long) {
        val key = currentKey
        val local = currentLocal
        val dimension = currentDim
        val visible = synchronized(lock) {
            replies.keys.retainAll(entries.mapTo(HashSet()) { it.ping.id })
            replies.values.removeAll { it.isEmpty() }
            if (dimension == null) emptyList()
            else entries.filter { (it.server == key || it.server == local) && it.ping.dimension == dimension && it.ping.expiresAt > now }
                .map { entry -> replies[entry.ping.id]?.let { entry.ping.copy(replies = it.toList()) } ?: entry.ping }
        }
        _pings.value = visible
    }

    private fun hasRoom(times: ArrayDeque<Long>, limit: Int, now: Long): Boolean {
        while (times.isNotEmpty() && now - times.first() >= SEND_WINDOW_MS) times.removeFirst()
        return times.size < limit
    }

    private fun validCoord(value: Double): Boolean = value.isFinite() && kotlin.math.abs(value) <= MAX_COORD

    private fun fits(value: String): Boolean = value.length <= MAX_FIELD

    private fun cleanLabel(label: String): String? = label.filterNot { it.isISOControl() }.trim().take(MAX_LABEL).ifEmpty { null }
}
