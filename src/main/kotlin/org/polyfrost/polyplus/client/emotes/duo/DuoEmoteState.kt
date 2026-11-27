package org.polyfrost.polyplus.client.emotes.duo

import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.network.http.SharedEmoteApi
import org.polyfrost.polyplus.client.network.http.responses.EmoteDefinition
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.SharedEmote
import org.polyfrost.polyplus.client.network.http.responses.SharedEmotePrivacy
import org.polyfrost.polyplus.client.network.http.responses.SharedEmoteResult
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.network.websocket.ServerboundPacket
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.polyfrost.polyplus.client.emotes.EmoteServerClock

object DuoEmoteState {
    private val LOGGER = LogManager.getLogger("polyplus/emotes")

    data class SoloStart(val emoteId: Int, val startTime: Long?, val anchor: EmoteAnchor?)

    data class Request(
        val requestId: String,
        val partner: UUID,
        val emoteId: Int,
        val expiresAt: Long,
        val incoming: Boolean,
        val accepted: Boolean = false,
        val revision: EmoteRevision? = null,
        val acceptSent: Boolean = false,
        val departed: Boolean = false,
    ) {
        fun isExpired(now: Long = EmoteServerClock.now()): Boolean = expiresAt <= now

        val open: Boolean get() = !incoming || accepted || acceptSent
    }

    private val solo = ConcurrentHashMap<UUID, SoloStart>()
    private val shared = ConcurrentHashMap<UUID, SharedEmote>()
    private val requests = ConcurrentHashMap<String, Request>()
    private val results = ConcurrentHashMap<String, SharedEmoteResult>()
    private val sessionRevisions = ConcurrentHashMap<SharedEmote, EmoteRevision>()
    private val endings = ConcurrentLinkedQueue<Ending>()

    class Ending(val request: Request, val result: SharedEmoteResult?)

    private data class RequestPin(val requestId: String)

    private class SentAsk(
        val target: UUID,
        val emoteId: Int,
        val requestId: Long?,
        val revision: EmoteRevision? = null,
    ) {
        @Volatile
        var cancelled = false

        @Volatile
        var departed = false
    }

    class FailedAsk(val target: UUID, val emoteId: Int, val code: String, val message: String)

    private val sentAsks = CopyOnWriteArrayList<SentAsk>()

    private val cancelledStarts = CancelledStarts()

    private val departedSessions = ConcurrentHashMap.newKeySet<SharedEmote>()

    @Volatile
    private var departRetryAt = 0L

    @Volatile
    var privacy: SharedEmotePrivacy = SharedEmotePrivacy.Unknown
        private set

    @Volatile
    private var privacyRequested = false

    @Volatile
    private var privacyRetryAt = 0L

    @Volatile
    private var privacyBackoffMs = PRIVACY_RETRY_MIN_MS

    @Volatile
    private var privacyGeneration = 0

    private val privacyLock = Mutex()

    private val privacyChoice = AtomicInteger()

    private val privacySaves = AtomicInteger()

    private val privacyAccount = AtomicInteger()

    private val privacyMonitor = Any()

    private var privacyOwner: UUID? = null

    internal var privacySaver: suspend (SharedEmotePrivacy) -> Result<Unit> = { SharedEmoteApi.setPrivacy(it) }

    internal var privacyLoader: suspend () -> Result<SharedEmotePrivacy> = { SharedEmoteApi.privacy() }

    internal var accountProvider: () -> UUID? = { runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() }

    internal var stopSender: () -> Result<Unit> = { PolyConnection.sendPacket(ServerboundPacket.SharedEmoteStop) }

    internal var responseSender: (ServerboundPacket.SharedEmoteResponse) -> Result<Unit> = { PolyConnection.sendPacket(it) }

    internal var cancelSender: (ServerboundPacket.SharedEmoteCancel) -> Result<Unit> = { PolyConnection.sendPacket(it) }

    val savingPrivacy: Boolean
        get() = privacySaves.get() > 0

    fun sharedFor(player: UUID): SharedEmote? = shared[accountId(player)]

    internal fun sessionOf(account: UUID): SharedEmote? = shared[account]

    fun soloFor(player: UUID): SoloStart? = solo[accountId(player)]

    fun anchorFor(player: UUID): EmoteAnchor? = sharedFor(player)?.anchor ?: soloFor(player)?.anchor

    fun roleOf(player: UUID): Int? {
        val session = sharedFor(player) ?: return null
        val index = session.players.indexOf(accountId(player).toString())
        return index.takeIf { it >= 0 }
    }

    fun partnerOf(player: UUID): UUID? {
        val session = sharedFor(player) ?: return null
        val self = accountId(player).toString()
        return session.players.firstOrNull { it != self }?.let(::parse)
    }

    fun incomingRequests(): List<Request> = pendingRequests().let { if (it.isEmpty()) it else it.filter { request -> request.incoming } }

    fun incomingPrompts(): List<Request> = incomingRequests().let { if (it.isEmpty()) it else it.filter { request -> !request.accepted } }

    fun outgoingRequests(): List<Request> = pendingRequests().let { if (it.isEmpty()) it else it.filter { request -> !request.incoming } }

    fun hasRequests(): Boolean = requests.isNotEmpty()

    fun hasEndings(): Boolean = !endings.isEmpty()

    fun unresolvedRequests(): List<Request> {
        if (requests.isEmpty()) return emptyList()
        prune()
        return requests.values.filter { !it.departed }
    }

    fun hasOpenRequests(): Boolean {
        prune()
        val now = EmoteServerClock.now()
        return requests.values.any { it.open && it.live(now) }
    }

    fun resultOf(requestId: String): SharedEmoteResult? = results[requestId]

    fun followUp(known: Map<String, Request>, current: Map<String, Request>): List<Ending> {
        val ended = ArrayList<Ending>()
        for ((id, request) in known) {
            if (id in current) continue
            val result = results[id]
            if (result != null && result != SharedEmoteResult.Accepted) continue
            ended += Ending(request, result)
        }
        while (true) {
            val ending = endings.poll() ?: break
            val id = ending.request.requestId
            if (ending.request.incoming && id !in known && id !in current) continue
            ended += ending
        }
        return ended
    }

    fun dropEndings() {
        endings.clear()
    }

    fun revisionOf(session: SharedEmote): EmoteRevision? = sessionRevisions[session]

    internal var requestSender: (ServerboundPacket.SharedEmoteRequest) -> Result<Unit> = { PolyConnection.sendPacket(it) }

    fun request(emoteId: Int, target: UUID): Result<Unit> {
        val requestId = CosmeticSync.nextRequestId()
        val ask = SentAsk(target, emoteId, requestId, ownRevision(emoteId))
        sentAsks += ask
        return runCatching { requestSender(ServerboundPacket.SharedEmoteRequest(emoteId, target.toString(), requestId)).getOrThrow() }
            .onFailure { sentAsks.remove(ask) }
    }

    internal fun noteAsk(target: UUID, emoteId: Int, requestId: Long? = null, revision: EmoteRevision? = null) {
        sentAsks += SentAsk(target, emoteId, requestId, revision)
    }

    internal fun failAsk(code: String, message: String, requestId: Long?): FailedAsk? {
        val ask = if (requestId != null) {
            sentAsks.firstOrNull { it.requestId == requestId }
        } else {
            sentAsks.firstOrNull().takeIf { code in ASK_ERRORS }
        } ?: return null
        if (!sentAsks.remove(ask) || ask.cancelled) return null
        return FailedAsk(ask.target, ask.emoteId, code, message)
    }

    fun hasSentAsks(): Boolean = sentAsks.any { !it.cancelled }

    fun cancelSentAsks(): Boolean {
        val asks = sentAsks.filter { !it.cancelled }
        if (asks.isEmpty()) return false
        stop()
            .onSuccess { asks.forEach { it.cancelled = true } }
            .onFailure { LOGGER.warn("Couldn't cancel the duo emote request", it) }
        return true
    }

    fun stopShared(local: UUID?): Result<Unit>? {
        val asks = sentAsks.filter { !it.cancelled }
        val open = asks.isNotEmpty() ||
            (local != null && sharedFor(local) != null) ||
            requests.values.any { it.open }
        if (!open) return null
        return stop().onSuccess {
            asks.forEach { it.cancelled = true }
            val now = System.currentTimeMillis()
            for (request in requests.values) {
                if (request.accepted || request.acceptSent) cancelledStarts.add(request.emoteId, request.partner, now)
            }
            requests.clear()
            releaseRequestPins()
        }
    }

    fun stopForLocalPlay(local: UUID?): SharedEmote? {
        val session = local?.let(::sharedFor)
        val result = stopShared(local) ?: return null
        result.onFailure { LOGGER.warn("Couldn't stop the duo emote", it) }
        return session?.takeIf { result.isSuccess }
    }

    internal fun cancelledStart(emoteId: Int, partner: UUID?): Boolean =
        cancelledStarts.matches(emoteId, partner, System.currentTimeMillis())

    internal fun forgetUnanswered() {
        sentAsks.clear()
    }

    fun respond(requestId: String, accept: Boolean): Result<Unit> =
        responseSender(ServerboundPacket.SharedEmoteResponse(requestId, accept))
            .onSuccess {
                if (accept) {
                    requests.computeIfPresent(requestId) { _, request -> request.copy(acceptSent = true) }
                } else {
                    requests.remove(requestId)
                    releaseRequestPins()
                }
            }

    fun start(requestId: String, anchor: EmoteAnchor): Result<Unit> =
        EmoteApi.requestSharedStart(requestId, anchor)

    fun stop(): Result<Unit> = stopSender().onSuccess { departedSessions.clear() }

    fun playSolo(emoteId: Int, anchor: EmoteAnchor?): Result<Unit> =
        EmoteApi.requestPlay(emoteId, anchor)

    fun ensurePrivacy() {
        syncAccount()
        if (!privacyRequested && System.currentTimeMillis() >= privacyRetryAt) refreshPrivacy()
    }

    fun refreshPrivacy() {
        val generation = beginRefresh()
        PolyPlusClient.SCOPE.launch { loadPrivacy(generation) }
    }

    internal fun beginRefresh(): Int = synchronized(privacyMonitor) {
        privacyRequested = true
        privacyGeneration
    }

    internal suspend fun loadPrivacy(generation: Int) {
        val result = privacyLoader()
        synchronized(privacyMonitor) {
            if (generation != privacyGeneration) return
            result
                .onSuccess {
                    privacy = it
                    privacyBackoffMs = PRIVACY_RETRY_MIN_MS
                }
                .onFailure {
                    if (privacyBackoffMs == PRIVACY_RETRY_MIN_MS) LOGGER.warn("Couldn't load the duo emote request setting", it)
                    privacyRetryAt = System.currentTimeMillis() + privacyBackoffMs
                    privacyBackoffMs = (privacyBackoffMs * 2).coerceAtMost(PRIVACY_RETRY_MAX_MS)
                    privacyRequested = false
                }
        }
    }

    class PrivacySave internal constructor(val value: SharedEmotePrivacy, internal val choice: Int, internal val account: Int)

    fun choosePrivacy(value: SharedEmotePrivacy): PrivacySave {
        syncAccount()
        return synchronized(privacyMonitor) {
            val save = PrivacySave(value, privacyChoice.incrementAndGet(), privacyAccount.get())
            privacyGeneration++
            privacySaves.incrementAndGet()
            save
        }
    }

    suspend fun savePrivacy(save: PrivacySave): Result<Unit>? {
        try {
            return privacyLock.withLock {
                if (save.choice != privacyChoice.get() || save.account != privacyAccount.get()) return@withLock null
                val result = privacySaver(save.value)
                synchronized(privacyMonitor) {
                    if (save.account != privacyAccount.get()) return@withLock null
                    privacyGeneration++
                    result.onSuccess {
                        privacy = save.value
                        privacyRequested = true
                    }
                    if (save.choice != privacyChoice.get()) return@withLock null
                    result.onFailure { privacyRequested = false }
                }
            }
        } finally {
            privacySaves.decrementAndGet()
        }
    }

    private fun syncAccount() {
        val account = accountProvider() ?: return
        synchronized(privacyMonitor) {
            val previous = privacyOwner
            if (account == previous) return
            privacyOwner = account
            if (previous != null) forgetPrivacy()
        }
    }

    private fun forgetPrivacy() {
        privacyAccount.incrementAndGet()
        privacyChoice.incrementAndGet()
        privacyGeneration++
        privacy = SharedEmotePrivacy.Unknown
        privacyRequested = false
        privacyRetryAt = 0L
        privacyBackoffMs = PRIVACY_RETRY_MIN_MS
    }

    suspend fun updatePrivacy(value: SharedEmotePrivacy): Result<Unit>? = savePrivacy(choosePrivacy(value))

    internal fun applySnapshot(
        players: Collection<String>,
        activeEmotes: Map<String, Int>,
        starts: Map<String, Long>,
        anchors: Map<String, EmoteAnchor>,
        sessions: Map<String, SharedEmote>,
        definitionFor: (players: Collection<String>, emoteId: Int) -> EmoteDefinition? = { _, _ -> null },
    ) {
        for (uuidString in players + activeEmotes.keys + sessions.keys) {
            val uuid = parse(uuidString) ?: continue
            val session = sessions[uuidString]
            if (session != null) {
                shared[uuid] = session
                solo.remove(uuid)
                continue
            }
            shared.remove(uuid)?.let(::forgetSession)
            val emoteId = activeEmotes[uuidString]
            if (emoteId == null) {
                solo.remove(uuid)
            } else {
                solo[uuid] = SoloStart(emoteId, starts[uuidString], anchors[uuidString])
            }
        }
        for (session in sessions.values.toSet()) {
            for (player in session.players.mapNotNull(::parse)) {
                shared[player]?.takeIf { it != session }?.let(::endSession)
                solo.remove(player)
                shared[player] = session
            }
            definitionFor(session.players, session.emoteId)
                ?.takeIf { it.id == session.emoteId }
                ?.let { sessionRevisions[session] = EmoteRevision(it.id, it.hash) }
        }
    }

    internal fun handleSoloStart(packet: ClientboundPacket.PlayerEmoteStarted) {
        val uuid = parse(packet.player) ?: return
        val session = shared[uuid]
        if (session != null && session.emoteId == packet.emoteId && session.startTime == packet.startTime) return
        if (session != null) endSession(session)
        solo[uuid] = SoloStart(packet.emoteId, packet.startTime, packet.anchor)
    }

    internal fun dropSolo(player: UUID, emoteId: Int) {
        solo.computeIfPresent(player) { _, start -> start.takeIf { it.emoteId != emoteId } }
    }

    internal fun handleSoloStop(player: String) {
        val uuid = parse(player) ?: return
        solo.remove(uuid)
        shared[uuid]?.let(::endSession)
    }

    internal fun handle(packet: ClientboundPacket.SharedEmoteRequested) {
        val from = parse(packet.from) ?: return
        cancelledStarts.forget(from)
        prune()
        ensurePrivacy()
        if (shouldAutoDecline(from)) {
            respond(packet.requestId, accept = false)
            return
        }
        val revision = CosmeticCatalog.pinEmote(RequestPin(packet.requestId), packet.emoteId, packet.emote)
        requests[packet.requestId] = Request(
            packet.requestId,
            from,
            packet.emoteId,
            packet.expiresAt,
            incoming = true,
            revision = revision,
        )
    }

    internal fun handle(packet: ClientboundPacket.SharedEmoteRequestSent) {
        val target = parse(packet.target) ?: return
        cancelledStarts.forget(target)
        prune()
        val ask = sentAsks.firstOrNull { it.target == target && it.emoteId == packet.emoteId }
        if (ask != null && (!sentAsks.remove(ask) || ask.cancelled)) return
        val departed = ask?.departed == true
        val pinned = packet.emote?.takeIf { it.id == packet.emoteId }
        val revision = if (pinned != null) {
            CosmeticCatalog.pinEmote(RequestPin(packet.requestId), packet.emoteId, pinned)
        } else {
            ask?.revision ?: ownRevision(packet.emoteId)
        }
        requests[packet.requestId] = Request(
            packet.requestId,
            target,
            packet.emoteId,
            packet.expiresAt,
            incoming = false,
            revision = revision,
            departed = departed,
        )
        if (departed) cancelDeparted()
    }

    internal fun handle(packet: ClientboundPacket.SharedEmoteResolved) {
        results[packet.requestId] = packet.result
        if (packet.result == SharedEmoteResult.Accepted) {
            val until = EmoteServerClock.now() + START_WINDOW_MS
            requests.computeIfPresent(packet.requestId) { _, request -> request.copy(accepted = true, expiresAt = until) }
        } else {
            requests.remove(packet.requestId)?.takeIf { !it.departed }?.let { endings += Ending(it, packet.result) }
            releaseRequestPins()
        }
        if (results.size > MAX_RESULTS) results.keys.take(results.size - MAX_RESULTS).forEach(results::remove)
    }

    internal fun handle(packet: ClientboundPacket.SharedEmoteStart, skip: UUID? = null) {
        val session = packet.toSharedEmote()
        val players = session.players.mapNotNull(::parse)
        noteDepartedStart(session, players)
        for (player in players) {
            if (player != skip) shared[player]?.takeIf { it != session }?.let(::endSession)
        }
        val revision = packet.emote?.takeIf { it.id == packet.emoteId }?.let { EmoteRevision(it.id, it.hash) }
            ?: requests.values.firstOrNull { it.accepted && it.emoteId == packet.emoteId && it.partner in players }?.revision
        if (revision != null) sessionRevisions[session] = revision
        for (player in players) {
            if (player == skip) continue
            solo.remove(player)
            shared[player] = session
        }
        if (ClientPlatform.localPlayerUuid() in players) {
            requests.values.removeIf { it.partner in players }
            releaseRequestPins()
        }
    }

    internal fun noteDepartedStart(session: SharedEmote, players: List<UUID>) {
        val local = accountProvider() ?: return
        if (local in players && requests.values.any { it.departed && it.partner in players }) departedSessions += session
    }

    internal fun handle(packet: ClientboundPacket.SharedEmoteStop) {
        val players = packet.players.mapNotNull(::parse)
        for (player in players) {
            val session = shared[player] ?: continue
            if (session.players.containsAll(packet.players)) endSession(session)
        }
    }

    internal fun forget(player: UUID) {
        solo.remove(player)
        val local = accountProvider()
        if (player != local) {
            shared.remove(player)?.let { session ->
                if (local != null && shared[local] == session) departedSessions += session
                forgetSession(session)
            }
        }
        prune()
        val gone = requests.values.filter { it.partner == player && !it.departed }
        for (prompt in gone.filter { !it.open }) {
            cancelSender(ServerboundPacket.SharedEmoteCancel(prompt.requestId))
                .onFailure { LOGGER.warn("Couldn't cancel the duo emote request from a player who left", it) }
            dropCancelled(prompt.requestId)
        }
        for (request in gone.filter { it.open }) {
            requests.computeIfPresent(request.requestId) { _, current -> current.copy(departed = true) }
            endings += Ending(request, SharedEmoteResult.Cancelled)
        }
        sentAsks.filter { it.target == player && !it.cancelled }.forEach { it.departed = true }
        releaseRequestPins()
        cancelDeparted()
    }

    internal fun cancelDeparted(now: Long = System.currentTimeMillis()) {
        if (requests.isEmpty() && departedSessions.isEmpty() && sentAsks.isEmpty()) return
        prune()
        val session = accountProvider()?.let(::sessionOf)
        departedSessions.retainAll { it == session }
        val sessionGone = session != null && session in departedSessions
        if (!sessionGone) {
            val departed = requests.values.any { it.departed } || sentAsks.any { it.departed && !it.cancelled }
            if (!departed || !onlyDepartedOpen(session)) return
        }
        if (now < departRetryAt) return
        val asks = sentAsks.filter { !it.cancelled }
        stop()
            .onSuccess {
                departRetryAt = 0L
                asks.forEach { it.cancelled = true }
                val cancelledAt = System.currentTimeMillis()
                for (request in requests.values) {
                    if (request.accepted || request.acceptSent) cancelledStarts.add(request.emoteId, request.partner, cancelledAt)
                    if (request.departed) requests.remove(request.requestId) else dropCancelled(request.requestId)
                }
                releaseRequestPins()
            }
            .onFailure {
                if (departRetryAt == 0L) LOGGER.warn("Couldn't cancel the duo emote with a player who left", it)
                departRetryAt = now + DEPART_RETRY_MS
            }
    }

    private fun onlyDepartedOpen(session: SharedEmote?): Boolean =
        session == null &&
            requests.values.all { it.departed } &&
            sentAsks.none { !it.cancelled && !it.departed }

    private fun dropCancelled(requestId: String) {
        requests.remove(requestId)?.let { endings += Ending(it, SharedEmoteResult.Cancelled) }
    }

    internal fun leaveServer() {
        prune()
        val account = accountProvider()
        val open = requests.isNotEmpty() ||
            sentAsks.any { !it.cancelled } ||
            (account != null && shared.containsKey(account))
        if (open) stop().onFailure { LOGGER.warn("Couldn't cancel duo emotes when leaving the server", it) }
        reset()
    }

    internal fun reset() {
        solo.clear()
        shared.clear()
        sessionRevisions.clear()
        requests.clear()
        releaseRequestPins()
        results.clear()
        endings.clear()
        sentAsks.clear()
        cancelledStarts.clear()
        departedSessions.clear()
        departRetryAt = 0L
        syncAccount()
        synchronized(privacyMonitor) {
            privacyRequested = false
            privacyRetryAt = 0L
            privacyBackoffMs = PRIVACY_RETRY_MIN_MS
        }
    }

    private fun endSession(session: SharedEmote) {
        for (player in session.players.mapNotNull(::parse)) shared.remove(player, session)
        forgetSession(session)
    }

    private fun forgetSession(session: SharedEmote) {
        if (shared.containsValue(session)) return
        sessionRevisions.remove(session)
    }

    private fun ownRevision(emoteId: Int): EmoteRevision? =
        CosmeticCatalog.allEmoteDefinitions().firstOrNull { it.id == emoteId }?.let(EmoteRevision::of)

    private fun pendingRequests(): List<Request> {
        if (requests.isEmpty()) return emptyList()
        prune()
        val now = EmoteServerClock.now()
        return requests.values.filter { !it.departed && !it.isExpired(now) }.sortedBy { it.expiresAt }
    }

    private fun Request.live(now: Long): Boolean =
        !isExpired(if (accepted) now else now - RESOLVE_GRACE_MS)

    private fun prune() {
        val now = EmoteServerClock.now()
        if (requests.values.removeIf { it.isExpired(now - RESOLVE_GRACE_MS) }) releaseRequestPins()
    }

    private fun releaseRequestPins() {
        CosmeticCatalog.releaseEmotePins { it is RequestPin && !requests.containsKey(it.requestId) }
    }

    private fun shouldAutoDecline(from: UUID): Boolean = when (privacy) {
        SharedEmotePrivacy.Nobody -> true
        SharedEmotePrivacy.Friends, SharedEmotePrivacy.Everyone, SharedEmotePrivacy.Unknown -> isBlocked(from)
    }

    private fun isBlocked(player: UUID): Boolean =
        FriendsRepository.blocked.value.any { parse(it.player) == player }

    private fun parse(uuid: String): UUID? = runCatching { UUID.fromString(uuid) }.getOrNull()

    private fun accountId(uuid: UUID): UUID =
        if (uuid == Minecraft.getInstance().player?.uuid) ClientPlatform.localPlayerUuid() else uuid

    private val ASK_ERRORS = setOf(
        "player_offline",
        "shared_emote_pending",
        "rate_limited",
        "not_owned",
        "bad_request",
        "internal_server_error",
    )

    private const val MAX_RESULTS = 32
    private const val START_WINDOW_MS = 10_000L
    private const val RESOLVE_GRACE_MS = 5_000L
    private const val DEPART_RETRY_MS = 1_000L
    private const val PRIVACY_RETRY_MIN_MS = 2_000L
    private const val PRIVACY_RETRY_MAX_MS = 60_000L
}
