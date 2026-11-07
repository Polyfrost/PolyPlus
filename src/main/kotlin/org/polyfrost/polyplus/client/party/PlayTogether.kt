package org.polyfrost.polyplus.client.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.multiplayer.ServerData
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPartyConfig
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.launcher.SessionRefresh
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.http.SessionsApi
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.network.http.responses.PlayDestination
import org.polyfrost.polyplus.client.network.http.responses.PlayDestinationKind
import org.polyfrost.polyplus.client.network.http.responses.PlayEndReason
import org.polyfrost.polyplus.client.network.http.responses.PlayReason
import org.polyfrost.polyplus.client.network.http.responses.PlayResponse
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.events.WebSocketMessage
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object PlayTogether {
    private val LOGGER = LogManager.getLogger()

    const val COUNTDOWN_MS = 5_000L
    private const val LIFETIME_MS = 2 * 60_000L
    private const val JOIN_TIMEOUT_MS = 45_000L
    private const val JOIN_RETRY_DELAY_MS = 2_000L
    private const val MAX_JOIN_RETRIES = 2
    private const val PLAN_TIMEOUT_MS = 10_000L
    private const val MAX_MODS = 300
    private const val MAX_MOD_CHARS = 96
    private const val MAX_NAME = 64
    private const val SHOWN_MODS = 5
    private const val LEGACY_VERSION = "1.8.9"

    enum class Stage { COUNTDOWN, CHECKING, WAITING, CONNECTING, DONE }

    class Active(val id: String, val leader: String, val destination: PlayDestination, val startsAt: Long, val leading: Boolean) {
        var sessionId by mutableStateOf<String?>(null)
        var stage by mutableStateOf(Stage.COUNTDOWN)
        var response by mutableStateOf<PlayResponse?>(null)
        var message by mutableStateOf<String?>(null)
        var warning by mutableStateOf<String?>(null)
        val responses = mutableStateMapOf<String, Pair<PlayResponse, PlayReason?>>()
        internal var check: ServerCheck? = null
        internal var session: Job? = null
        internal var retry: SessionRefresh.ConnectionAttempt? = null
        internal var retryAt = 0L
        internal var retries = 0
        internal var partyId: String? = null
        internal var connectAt = 0L
        internal var pendingInvite: Pair<Int, String>? = null
        val leaderName: String get() = PlayerNamesRepository.nameOr(leader)
    }

    private class Plan(val destination: PlayDestination, val worldId: String?, val at: Long)

    var active by mutableStateOf<Active?>(null)
        private set

    @Volatile private var plan: Plan? = null
    private var worldId: String? = null

    var joinServer: ((address: String, name: String) -> Unit)? = null

    fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.PlayTogetherStarted -> ClientPlatform.runOnMain { onStarted(packet) }
                is ClientboundPacket.PlayTogetherUpdated -> ClientPlatform.runOnMain { onUpdated(packet) }
                is ClientboundPacket.PlayTogetherEnded -> ClientPlatform.runOnMain { onEnded(packet.id, packet.reason) }
                is ClientboundPacket.SessionInviteReceived -> ClientPlatform.runOnMain { onSessionInvite(packet.inviteId, packet.sessionId) }
                else -> Unit
            }
        }
    }

    fun canStart(): Boolean {
        val party = PartyRepository.party.value ?: return false
        return PartyRepository.isLeader(party) && party.members.size > 1 && active?.let { it.stage == Stage.DONE } != false
    }

    fun serverDestination(address: String, name: String) =
        PlayDestination(PlayDestinationKind.Server, address.trim(), name.ifBlank { address }.take(MAX_NAME))

    fun worldDestination(name: String) =
        PlayDestination(PlayDestinationKind.World, null, name.take(MAX_NAME), PartyScene.mcVersion, loader(), localMods())

    private fun loader(): String = if (PartyScene.mcVersion == LEGACY_VERSION) "ornithe" else "fabric"

    private fun localMods(): List<String> = FabricLoader.getInstance().allMods
        .map { "${it.metadata.id}@${it.metadata.version.friendlyString}" }
        .filter { it.length <= MAX_MOD_CHARS && !it.startsWith("java@") }
        .sorted()
        .take(MAX_MODS)

    fun start(destination: PlayDestination, worldId: String? = null) {
        if (!canStart()) return
        val sent = PolyConnection.sendPacket(ServerboundPacket.StartPlayTogether(destination))
        if (sent.isFailure) {
            SocialErrors.emit("Couldn't start Play together. You're offline")
            return
        }
        plan = Plan(destination, worldId, System.currentTimeMillis())
    }

    fun bringHere() {
        val address = PartyJoin.serverAddress() ?: return
        start(serverDestination(address, address))
    }

    fun canJoinLate(current: Active): Boolean =
        current.response == PlayResponse.Busy &&
            current.stage == Stage.DONE &&
            current.destination.kind == PlayDestinationKind.Server &&
            current.destination.address != null

    fun joinLate() {
        val current = active?.takeIf(::canJoinLate) ?: return
        val address = current.destination.address ?: return
        PartyJoin.server(ServerData(current.destination.name, address, ServerData.Type.OTHER)) {
            respond(current, PlayResponse.Joining)
            current.stage = Stage.CONNECTING
            current.connectAt = System.currentTimeMillis()
        }
    }

    private fun joinFromNotice(current: Active) {
        if (active !== current) return
        joinLate()
        if (PartyJoin.pending != null) PartyNotices.showDialogs()
    }

    fun cancel() {
        val current = active ?: return
        if (current.leading) {
            PolyConnection.sendPacket(ServerboundPacket.CancelPlayTogether(current.id))
            finish(current, "Play together cancelled")
        } else {
            respond(current, PlayResponse.Cancelled)
            current.message = null
            finish(current, null)
        }
    }

    fun dismiss() {
        val current = active ?: return
        if (current.stage == Stage.DONE) active = null
    }

    private fun respond(current: Active, response: PlayResponse, reason: PlayReason? = null) {
        if (current.leading) return
        current.response = response
        PolyConnection.sendPacket(ServerboundPacket.PlayTogetherResponse(current.id, response, reason))
    }

    private fun onStarted(packet: ClientboundPacket.PlayTogetherStarted) {
        val party = PartyRepository.party.value ?: return
        if (party.id != packet.partyId || PartyRepository.isSelf(packet.leader)) return
        val startsAt = runCatching { Instant.parse(packet.startsAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis() + COUNTDOWN_MS)
        val current = Active(packet.id, packet.leader, packet.destination, startsAt, leading = false)
        current.sessionId = packet.sessionId
        current.partyId = party.id
        active?.check?.close()
        active = current
        val where = packet.destination.name
        if (alreadyThere(current)) {
            respond(current, PlayResponse.Joined)
            current.stage = Stage.DONE
            current.message = "You're already on $where"
            return
        }
        if (PartyPresence.local.location.kind != LocationKind.Menu) {
            respond(current, PlayResponse.Busy)
            current.stage = Stage.DONE
            if (canJoinLate(current)) {
                PartyNotices.send("${current.leaderName} started Play together on $where", PartyNotices.MOVE_MS, "Join", { joinFromNotice(current) })
            } else {
                Notifications.info("PolyPlus", "${current.leaderName} started Play together on $where. Open Social to join.")
            }
            return
        }
        if (!PolyPlusPartyConfig.partyPlayTogetherJoin) {
            respond(current, PlayResponse.Cancelled)
            current.stage = Stage.DONE
            current.message = "${current.leaderName} started Play together on $where"
            Notifications.info("PolyPlus", "${current.leaderName} started Play together on $where")
            return
        }
        respond(current, PlayResponse.Joining)
        when (packet.destination.kind) {
            PlayDestinationKind.Server -> packet.destination.address?.let {
                current.check = ServerCheck(it)
                current.session = SessionRefresh.prepareForJoin()
            }
            else -> checkWorld(current)
        }
    }

    private fun alreadyThere(current: Active): Boolean {
        val here = PartyPresence.local.location
        return when (current.destination.kind) {
            PlayDestinationKind.Server -> {
                val address = current.destination.address ?: return false
                here.kind == LocationKind.Server && here.key == PartyPresence.serverKey(address)
            }
            else -> current.sessionId != null && here.key == "session:${current.sessionId}"
        }
    }

    private fun checkWorld(current: Active) {
        val destination = current.destination
        if (destination.mcVersion != null && destination.mcVersion != PartyScene.mcVersion) {
            incompatible(current, PlayReason.Version, "Needs Minecraft ${destination.mcVersion}")
            return
        }
        if (destination.loader != null && destination.loader != loader()) {
            incompatible(current, PlayReason.Version, "Needs the ${destination.loader} loader")
            return
        }
        val mine = localMods().associate { it.substringBefore('@') to it.substringAfter('@') }
        val theirs = destination.mods.associate { it.substringBefore('@') to it.substringAfter('@') }
        val differing = theirs.filter { (id, version) -> mine[id] != version }.keys
        if (differing.isEmpty()) return
        val names = differing.take(SHOWN_MODS).map { id -> FabricLoader.getInstance().getModContainer(id).map { it.metadata.name }.orElse(id) }
        val more = if (differing.size > SHOWN_MODS) " and ${differing.size - SHOWN_MODS} more" else ""
        current.warning = "Your mods differ: ${names.joinToString(", ")}$more"
    }

    private fun incompatible(current: Active, reason: PlayReason, message: String) {
        respond(current, PlayResponse.Incompatible, reason)
        current.check?.close()
        current.check = null
        current.stage = Stage.DONE
        current.message = message
    }

    private fun onUpdated(packet: ClientboundPacket.PlayTogetherUpdated) {
        val pending = plan
        if (active?.id != packet.id && pending != null && PartyRepository.isSelf(packet.player)) {
            val party = PartyRepository.party.value ?: return
            plan = null
            worldId = pending.worldId
            active?.check?.close()
            active = Active(packet.id, party.leader, pending.destination, pending.at + COUNTDOWN_MS, leading = true).also {
                it.partyId = party.id
                if (pending.destination.kind == PlayDestinationKind.Server) it.session = SessionRefresh.prepareForJoin()
            }
        }
        val current = active?.takeIf { it.id == packet.id } ?: return
        if (packet.sessionId != null) current.sessionId = packet.sessionId
        if (!PartyRepository.samePlayer(packet.player, current.leader)) current.responses[packet.player] = packet.response to packet.reason
        val reason = packet.reason
        if (current.leading && packet.response == PlayResponse.Incompatible) {
            val name = PlayerNamesRepository.nameOr(packet.player)
            SocialErrors.emit("$name can't join: ${reasonText(reason)}")
        }
    }

    fun reasonText(reason: PlayReason?): String = when (reason) {
        PlayReason.Version -> "different version"
        PlayReason.Unreachable -> "server unreachable"
        PlayReason.Mods -> "different mods"
        PlayReason.Full -> "server full"
        PlayReason.Kicked -> "kicked"
        else -> "something went wrong"
    }

    private fun onEnded(id: String, reason: PlayEndReason) {
        val current = active?.takeIf { it.id == id } ?: return
        if (canJoinLate(current)) {
            active = null
            return
        }
        if (current.stage == Stage.DONE || current.stage == Stage.CONNECTING) return
        current.check?.close()
        current.check = null
        finish(current, when (reason) {
            PlayEndReason.Cancelled -> "${current.leaderName} cancelled Play together"
            PlayEndReason.LeaderLeft -> "${current.leaderName} left the party"
            PlayEndReason.Replaced -> null
            else -> "Play together ended"
        })
    }

    private fun finish(current: Active, message: String?) {
        current.stage = Stage.DONE
        current.message = message
        if (message == null && active === current) active = null
    }

    private fun onSessionInvite(inviteId: Int, sessionId: String) {
        val current = active ?: return
        if (current.leading || current.response != PlayResponse.Joining) return
        val linked = current.sessionId
        if (linked == null || current.stage != Stage.WAITING) {
            current.pendingInvite = inviteId to sessionId
            return
        }
        if (linked != sessionId) return
        current.pendingInvite = null
        current.stage = Stage.CONNECTING
        current.connectAt = System.currentTimeMillis()
        PolyPlusClient.SCOPE.launch {
            val invite = SessionsApi.incomingInvites().getOrNull()?.firstOrNull { it.id == inviteId }
            if (invite == null) {
                ClientPlatform.runOnMain { failed(current, "The world invite is gone") }
                return@launch
            }
            if (active !== current || current.stage != Stage.CONNECTING) return@launch
            SessionsRepository.accept(invite)
        }
    }

    fun tick() {
        val pending = plan
        if (pending != null && System.currentTimeMillis() - pending.at > PLAN_TIMEOUT_MS) {
            plan = null
            SocialErrors.emit("Couldn't start Play together")
        }
        val current = active ?: return
        val now = System.currentTimeMillis()
        val party = PartyRepository.party.value
        if (party == null || (current.partyId != null && current.partyId != party.id)) {
            current.check?.close()
            active = null
            return
        }
        if (now - current.startsAt > LIFETIME_MS && current.stage != Stage.CONNECTING) {
            if (current.stage == Stage.DONE) active = null else finish(current, "Play together ended")
            return
        }
        current.check?.let { check ->
            when (check.poll()) {
                ServerCheck.Result.INCOMPATIBLE -> incompatible(current, PlayReason.Version, "Needs Minecraft ${check.serverVersion.ifBlank { "another version" }}")
                ServerCheck.Result.UNREACHABLE -> incompatible(current, PlayReason.Unreachable, "Can't reach ${current.destination.name}")
                ServerCheck.Result.OK -> current.check = null
                ServerCheck.Result.PENDING -> Unit
            }
        }
        when (current.stage) {
            Stage.COUNTDOWN, Stage.CHECKING -> if (now >= current.startsAt) go(current)
            Stage.WAITING -> current.pendingInvite?.let { (invite, session) -> if (current.sessionId != null) onSessionInvite(invite, session) }
            Stage.CONNECTING -> watch(current, now)
            Stage.DONE -> Unit
        }
    }

    private fun go(current: Active) {
        if (current.check != null || current.session?.isActive == true) {
            current.stage = Stage.CHECKING
            return
        }
        val destination = current.destination
        if (alreadyThere(current)) {
            respond(current, PlayResponse.Joined)
            finish(current, if (current.leading) "Your party is on its way to ${destination.name}" else "You're already on ${destination.name}")
            return
        }
        if (destination.kind == PlayDestinationKind.Server) {
            val address = destination.address ?: return failed(current, "No server address")
            if (PartyJoin.inWorld()) {
                respond(current, PlayResponse.Busy)
                current.stage = Stage.DONE
                return
            }
            val join = joinServer ?: { target: String, name: String -> PartyJoin.server(ServerData(name, target, ServerData.Type.OTHER)) }
            current.stage = Stage.CONNECTING
            current.connectAt = System.currentTimeMillis()
            join(address, destination.name)
            return
        }
        if (!current.leading) {
            current.stage = Stage.WAITING
            current.connectAt = System.currentTimeMillis()
            return
        }
        val id = worldId ?: return failed(current, "No world picked")
        current.stage = Stage.CONNECTING
        current.connectAt = System.currentTimeMillis()
        val screen = ClientPlatform.currentScreen()
        PolyPlusClient.SCOPE.launch {
            val entry = HostWorldManager.loadWorlds().firstOrNull { it.id == id }
            if (entry == null || screen == null) {
                ClientPlatform.runOnMain { failed(current, "That world is gone") }
                return@launch
            }
            HostWorldManager.hostViaP2P(screen, entry, entry.gameMode, false, PolyPlusConfig.voiceHideIp) { sessionId ->
                current.sessionId = sessionId
                PolyPlusClient.SCOPE.launch {
                    PartyApi.inviteSession(sessionId)
                        .onSuccess { LOGGER.info("Invited the party to session {} ({} invites)", sessionId, it.size) }
                        .onFailure { error ->
                            LOGGER.error("Could not invite the party to session {}", sessionId, error)
                            SocialErrors.emit(PartyErrors.message(error, "Couldn't invite your party"))
                        }
                }
            }.onFailure { error ->
                ClientPlatform.runOnMain { failed(current, PartyErrors.message(error, "Couldn't host the world")) }
            }
        }
    }

    private fun watch(current: Active, now: Long) {
        val here = PartyPresence.local.location
        val arrived = when (current.destination.kind) {
            PlayDestinationKind.Server -> current.destination.address?.let { here.key == PartyPresence.serverKey(it) } == true
            else -> here.kind == LocationKind.HostedWorld || (current.leading && here.kind == LocationKind.Singleplayer)
        }
        if (arrived) {
            respond(current, PlayResponse.Joined)
            finish(current, null)
            return
        }
        if (current.destination.kind == PlayDestinationKind.Server) followJoin(current, now)
        if (current.stage == Stage.CONNECTING && now - current.connectAt > JOIN_TIMEOUT_MS) failed(current, "Couldn't join ${current.destination.name}")
    }

    private fun followJoin(current: Active, now: Long) {
        val where = current.destination.name
        val ended = SessionRefresh.takeEndedAttempt()
        if (ended != null) {
            val failure = ended.failure
            when {
                failure == null -> failed(current, "Couldn't join $where")
                current.retries >= MAX_JOIN_RETRIES -> failed(current, loginFailureText(failure, where))
                else -> {
                    LOGGER.warn("Joining {} failed with {}; retrying", where, failure)
                    current.retries++
                    current.retry = ended.attempt
                    current.retryAt = now + JOIN_RETRY_DELAY_MS
                }
            }
            return
        }
        val attempt = current.retry ?: return
        if (now < current.retryAt || !SessionRefresh.refreshSettled()) return
        current.retry = null
        current.connectAt = now
        if (!SessionRefresh.reconnect(attempt)) failed(current, "Couldn't join $where")
    }

    private fun loginFailureText(failure: SessionRefresh.LoginFailure, where: String): String = when (failure) {
        SessionRefresh.LoginFailure.INVALID_SESSION -> "Your Minecraft session expired. Restart the game to join $where"
        SessionRefresh.LoginFailure.SERVERS_UNAVAILABLE -> "Minecraft's login servers aren't responding. Try $where again in a moment"
    }

    private fun failed(current: Active, message: String) {
        respond(current, PlayResponse.Failed, PlayReason.Other)
        current.check?.close()
        current.check = null
        current.stage = Stage.DONE
        current.message = message
    }
}
