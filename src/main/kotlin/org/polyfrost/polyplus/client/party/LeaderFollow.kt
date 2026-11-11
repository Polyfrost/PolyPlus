package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notification
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.gui.serveredit.ServerStatusProbe
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.http.SessionsApi
import org.polyfrost.polyplus.client.network.http.responses.HostedJoinResult
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import java.time.Instant
import net.minecraft.client.multiplayer.ServerData

class ServerCheck(address: String) {
    enum class Result { PENDING, OK, INCOMPATIBLE, UNREACHABLE }

    private val probe = ServerStatusProbe(Minecraft.getInstance(), null).also {
        it.reset(address)
        it.ping()
    }

    var result = Result.PENDING
        private set

    val serverVersion: String get() = runCatching { probe.data.version.string }.getOrDefault("")

    fun poll(): Result {
        if (result != Result.PENDING) return result
        probe.tick()
        result = when (probe.data.state()) {
            ServerData.State.SUCCESSFUL -> Result.OK
            ServerData.State.INCOMPATIBLE -> Result.INCOMPATIBLE
            ServerData.State.UNREACHABLE -> Result.UNREACHABLE
            else -> Result.PENDING
        }
        if (result != Result.PENDING) probe.close()
        return result
    }

    fun close() = probe.close()
}

enum class HostJoinPolicy {
    ASK,
    ALWAYS,
    NEVER,
}

object LeaderFollow {
    private val LOGGER = LogManager.getLogger()

    private const val CARD_MS = 60_000L
    private const val JOIN_COOLDOWN_MS = 15_000L
    private const val SESSION_PREFIX = "session:"
    private const val REQUEST_MS = 60_000L
    private const val REQUEST_GRACE_MS = 5_000L

    class Hosted(val leader: String, val sessionId: String) {
        var here by mutableStateOf(false)
        var joining by mutableStateOf(false)
        var requested by mutableStateOf(false)
        var dismissed by mutableStateOf(false)
        internal var requestId: String? = null
        internal var requestUntil = 0L
        internal var fromNotice = false
        internal var joinedAt = 0L
        val name: String get() = PlayerNamesRepository.nameOr(leader)
        val busy: Boolean get() = joining || requested
        val status: String? get() = when {
            joining -> "Joining..."
            requested -> "Requested..."
            else -> null
        }
    }

    var hosted by mutableStateOf<Hosted?>(null)
        private set

    private var hostedNotice: Notification? = null

    fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.HostedJoinRequestSent -> ClientPlatform.runOnMain { onRequestSent(packet) }
                is ClientboundPacket.HostedJoinResolved -> ClientPlatform.runOnMain { onResolved(packet) }
                else -> Unit
            }
        }
    }

    fun hostedFor(player: String): Hosted? = hosted?.takeIf { !it.here && PartyRepository.samePlayer(it.leader, player) }

    fun dismissHosted() {
        hosted?.dismissed = true
        hostedNotice?.let(PartyNotices::retire)
        hostedNotice = null
    }

    private fun joinHostedFromNotice(sessionId: String) {
        if (hosted?.sessionId != sessionId) return
        join(fromNotice = true)
    }

    fun joinHosted() = join(fromNotice = false)

    private fun join(fromNotice: Boolean) {
        val current = hosted?.takeIf { !it.here } ?: return
        val now = System.currentTimeMillis()
        if (current.busy || now - current.joinedAt < JOIN_COOLDOWN_MS) return
        current.joining = true
        current.fromNotice = fromNotice
        hostedNotice?.let(PartyNotices::retire)
        hostedNotice = null
        PolyPlusClient.SCOPE.launch {
            val invites = SessionsApi.incomingInvites().getOrElse { error ->
                LOGGER.error("Failed to look up session {} to join the party leader", current.sessionId, error)
                ClientPlatform.runOnMain {
                    current.joining = false
                    SocialErrors.emit("Couldn't reach Poly+ to join ${current.name}'s world", error)
                }
                return@launch
            }
            val invite = invites.firstOrNull { it.sessionId == current.sessionId }
            if (invite != null) {
                ClientPlatform.runOnMain {
                    current.joining = false
                    enter(current, invite)
                }
                return@launch
            }
            val result = PartyApi.requestHostedJoin(current.sessionId)
            ClientPlatform.runOnMain {
                current.joining = false
                result
                    .onSuccess { request -> markRequested(current, request.id, request.expiresAt) }
                    .onFailure { error -> requestFailed(current, error) }
            }
        }
    }

    private fun enter(current: Hosted, invite: SessionInvite) {
        if (hosted !== current || current.here) return
        current.joinedAt = System.currentTimeMillis()
        PartyJoin.world("${current.name}'s world") { SessionsRepository.accept(invite) }
        if (current.fromNotice && PartyJoin.pending != null) PartyNotices.showDialogs()
    }

    private fun acceptInvite(current: Hosted, match: (SessionInvite) -> Boolean) {
        current.joining = true
        PolyPlusClient.SCOPE.launch {
            val invite = SessionsApi.incomingInvites().getOrNull()?.firstOrNull(match)
            ClientPlatform.runOnMain {
                current.joining = false
                if (invite == null) SocialErrors.emit("The invite to ${current.name}'s world is gone") else enter(current, invite)
            }
        }
    }

    private fun markRequested(current: Hosted, requestId: String, expiresAt: String) {
        if (hosted !== current) return
        current.requestId = requestId
        current.requested = true
        current.requestUntil = expiry(expiresAt)
    }

    private fun expiry(at: String): Long =
        runCatching { Instant.parse(at).toEpochMilli() }.getOrDefault(System.currentTimeMillis() + REQUEST_MS) + REQUEST_GRACE_MS

    private fun requestFailed(current: Hosted, error: Throwable) {
        LOGGER.warn("Asking to join session {} failed", current.sessionId, error)
        when (PartyErrors.code(error)) {
            "join_request_pending" -> {
                current.requested = true
                current.requestUntil = System.currentTimeMillis() + REQUEST_MS + REQUEST_GRACE_MS
            }
            "already_invited_to_session" -> acceptInvite(current) { it.sessionId == current.sessionId }
            "blocked" -> SocialErrors.emit("You can't join ${current.name}'s world")
            else -> SocialErrors.emit(PartyErrors.message(error, "Couldn't ask to join ${current.name}'s world"))
        }
    }

    private fun onRequestSent(packet: ClientboundPacket.HostedJoinRequestSent) {
        val current = hosted?.takeIf { it.sessionId == packet.sessionId } ?: return
        markRequested(current, packet.requestId, packet.expiresAt)
    }

    private fun onResolved(packet: ClientboundPacket.HostedJoinResolved) {
        val current = hosted?.takeIf {
            it.requestId == packet.requestId || (it.requestId == null && it.requested && it.sessionId == packet.sessionId)
        } ?: return
        current.requested = false
        current.requestId = null
        when (packet.result) {
            HostedJoinResult.Allowed -> {
                val id = packet.inviteId
                if (id == null) acceptInvite(current) { it.sessionId == current.sessionId } else acceptInvite(current) { it.id == id }
            }
            HostedJoinResult.Denied -> SocialErrors.emit("${current.name} didn't let you join their world")
            HostedJoinResult.Expired -> SocialErrors.emit("${current.name} didn't answer your request to join")
            else -> SocialErrors.emit("Your request to join ${current.name}'s world was cancelled")
        }
    }

    private fun trackHosted(party: Party?) {
        val leader = party?.takeUnless { PartyRepository.isLeader(it) }?.let { it.member(it.leader) }
        val key = leader?.takeIf { it.online }?.state?.location
            ?.takeIf { it.kind == LocationKind.HostedWorld }
            ?.key?.takeIf { it.startsWith(SESSION_PREFIX) }
        val session = key?.removePrefix(SESSION_PREFIX)
        val here = key != null && key == PartyPresence.local.location.key
        val current = hosted
        if (current != null && current.sessionId == session && PartyRepository.samePlayer(current.leader, leader?.player.orEmpty())) {
            current.here = here
            if (current.requested && System.currentTimeMillis() > current.requestUntil) {
                current.requested = false
                current.requestId = null
                SocialErrors.emit("${current.name} didn't answer your request to join")
            }
            return
        }
        hostedNotice?.let(PartyNotices::retire)
        hostedNotice = null
        if (session == null || leader == null) {
            hosted = null
            return
        }
        val next = Hosted(leader.player, session).also { it.here = here }
        hosted = next
        if (!here && PartyPresence.local.location.kind != LocationKind.Menu) {
            hostedNotice = PartyNotices.send("${next.name} is playing in a hosted world", PartyNotices.MOVE_MS, "Join", { joinHostedFromNotice(session) })
        }
    }

    class Card(val leader: String, val name: String, val address: String, val key: String, var shownAt: Long) {
        var check by mutableStateOf(ServerCheck.Result.PENDING)
        var needs by mutableStateOf<String?>(null)
        var checking by mutableStateOf(false)
        var cleared by mutableStateOf(false)
        var probe: ServerCheck? = null
    }

    var card by mutableStateOf<Card?>(null)
        private set

    private var lastKey: String? = null
    private var lastParty: String? = null
    private var notice: Notification? = null

    fun dismiss() {
        card?.probe?.close()
        card = null
        notice?.let(PartyNotices::retire)
        notice = null
    }

    private fun joinFromNotice(key: String) {
        if (card?.key != key) return
        requestJoin()
        PartyNotices.showDialogs()
    }

    fun requestJoin() {
        val current = card ?: return
        if (current.probe != null || current.cleared) return
        current.shownAt = System.currentTimeMillis()
        current.checking = true
        current.probe = ServerCheck(current.address)
    }

    fun tick() {
        val party = PartyRepository.party.value
        trackHosted(party)
        if (party == null || PartyRepository.isLeader(party)) {
            if (card != null) dismiss()
            lastKey = null
            lastParty = party?.id
            return
        }
        val leader = party.member(party.leader)
        val location = leader?.state?.location
        val key = location?.takeIf { it.kind == LocationKind.Server && it.display != null }?.key
        if (party.id != lastParty) {
            lastParty = party.id
            lastKey = key
            return
        }
        if (key != lastKey) {
            lastKey = key
            dismiss()
            val address = location?.display
            if (key != null && PlayTogether.isSafeServerAddress(address) && address != null && key != PartyPresence.local.location.key && leader != null) {
                val name = PlayerNamesRepository.nameOr(leader.player)
                card = Card(leader.player, name, address, key, System.currentTimeMillis())
                if (PartyPresence.local.location.kind != LocationKind.Menu) {
                    notice = PartyNotices.send("$name is playing on $address", PartyNotices.MOVE_MS, "Join", { joinFromNotice(key) })
                }
            }
        }
        val current = card ?: return
        if (System.currentTimeMillis() - current.shownAt > CARD_MS || current.key == PartyPresence.local.location.key) {
            dismiss()
            return
        }
        val probe = current.probe ?: return
        val result = probe.poll()
        if (result != ServerCheck.Result.PENDING) {
            current.check = result
            if (result == ServerCheck.Result.INCOMPATIBLE) current.needs = probe.serverVersion.ifBlank { null } else current.cleared = true
            current.checking = false
            current.probe = null
        }
    }
}
