package org.polyfrost.polyplus.client.emotes.duo

//? if > 1.8.9 {
import net.minecraft.client.player.AbstractClientPlayer
//?} else {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity as AbstractClientPlayer
*///?}
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticSync
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.EmoteRole
import org.polyfrost.polyplus.client.emotes.conditions.EmoteConditions
import org.polyfrost.polyplus.client.emotes.context.ContextEmotes
import org.polyfrost.polyplus.client.gui.EmoteWheelOverlay
import org.polyfrost.polyplus.client.network.http.responses.EmoteAnchor
import org.polyfrost.polyplus.client.network.http.responses.SharedEmote
import org.polyfrost.polyplus.client.network.http.responses.SharedEmotePrivacy
import org.polyfrost.polyplus.client.network.http.responses.SharedEmoteResult
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DuoEmotes {
    private val LOGGER = LogManager.getLogger("polyplus/emotes")

    const val NEARBY_BLOCKS = 8.0

    class Partner(val uuid: UUID, val name: String, val distance: Double)

    class Nearby(val partners: List<Partner>, val preselected: Int)

    internal class Approach(
        val request: DuoEmoteState.Request,
        val emote: Emote,
        val leader: AbstractClientPlayer?,
        val follower: AbstractClientPlayer?,
        val anchor: EmoteAnchor?,
        val spot: Spot?,
        val ready: Boolean,
    ) {
        val leading: Boolean get() = !request.incoming
    }

    private val notices = NoticeQueue(NOTICE_MS)

    private val known = HashMap<String, DuoEmoteState.Request>()
    private val shownPrompts = HashSet<String>()
    private val answered = HashSet<String>()
    private val loading = HashSet<Any>()
    private val requestedNames = HashSet<String>()
    private var readyFor: String? = null
    private var readySince = 0L
    private var startSentFor: String? = null
    private var startSentAt = 0L
    private var leftSession: SharedEmote? = null
    private var stopFailedFor: SharedEmote? = null
    private var stopRetryAt = 0L
    @Volatile
    private var syncedPrivacy = SharedEmotePrivacy.Unknown

    init {
        eventHandler<TickEvent.End> { tick() }
    }

    fun initialize() = Unit

    fun roleFor(player: AbstractClientPlayer, emoteId: Int?, emote: Emote): EmoteRole? {
        if (emoteId == null) return null
        val session = DuoEmoteState.sharedFor(player.uuid) ?: return soloRole(player, emoteId)
        if (session.emoteId != emoteId) return null
        val seat = DuoEmoteState.roleOf(player.uuid) ?: return null
        return DuoPlacement.role(
            emote.roleIndex(seat),
            session.anchor,
            DuoPlayers.x(player),
            DuoPlayers.y(player),
            DuoPlayers.z(player),
            DuoPlayers.bodyYaw(player),
        )
    }

    private fun soloRole(player: AbstractClientPlayer, emoteId: Int): EmoteRole? {
        val start = DuoEmoteState.soloFor(player.uuid)?.takeIf { it.emoteId == emoteId } ?: return null
        val anchor = start.anchor ?: return null
        return DuoPlacement.role(
            SOLO_ROLE,
            anchor,
            DuoPlayers.x(player),
            DuoPlayers.y(player),
            DuoPlayers.z(player),
            DuoPlayers.bodyYaw(player),
        )
    }

    fun isDuo(emoteId: Int): Boolean = CosmeticAssetCache.getEmote(emoteId)?.isDuo == true

    fun preload(emoteIds: Collection<Int>) {
        for (id in emoteIds) load(id)
    }

    fun isLoading(emoteId: Int): Boolean = emoteId in loading

    fun nearby(): Nearby {
        val local = DuoPlayers.local() ?: return Nearby(emptyList(), -1)
        val eyeX = DuoPlayers.x(local)
        val eyeY = DuoPlayers.y(local) + DuoPlayers.eyeHeight()
        val eyeZ = DuoPlayers.z(local)
        val yaw = Math.toRadians(DuoPlayers.lookYaw(local).toDouble())
        val pitch = Math.toRadians(DuoPlayers.pitch(local).toDouble())
        val lookX = -sin(yaw) * cos(pitch)
        val lookY = -sin(pitch)
        val lookZ = cos(yaw) * cos(pitch)
        val found = ArrayList<Pair<Partner, Double>>()
        for (player in DuoPlayers.loaded()) {
            if (player === local || DuoPlayers.isLocal(player)) continue
            if (!CosmeticCatalog.isPolyPlusUser(player.uuid)) continue
            val dx = DuoPlayers.x(player) - DuoPlayers.x(local)
            val dy = DuoPlayers.y(player) - DuoPlayers.y(local)
            val dz = DuoPlayers.z(player) - DuoPlayers.z(local)
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance > NEARBY_BLOCKS) continue
            val tx = DuoPlayers.x(player) - eyeX
            val ty = DuoPlayers.y(player) + BODY_CENTER - eyeY
            val tz = DuoPlayers.z(player) - eyeZ
            val length = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-6)
            val dot = ((tx * lookX + ty * lookY + tz * lookZ) / length).coerceIn(-1.0, 1.0)
            found += Partner(player.uuid, DuoPlayers.name(player), distance) to Math.toDegrees(acos(dot))
        }
        found.sortBy { it.first.distance }
        val looked = found.withIndex()
            .filter { it.value.second <= LOOK_DEGREES }
            .minByOrNull { it.value.second }
            ?.index
        return Nearby(found.map { it.first }, looked ?: if (found.isEmpty()) -1 else 0)
    }

    fun ask(emoteId: Int, partner: UUID) {
        load(emoteId)
        DuoEmoteState.request(emoteId, partner).onFailure {
            LOGGER.warn("Couldn't ask {} for duo emote {}", partner, emoteId, it)
            tell(COULD_NOT_SEND)
        }
    }

    fun tellNobodyNearby() = tell(NOBODY_NEARBY)

    fun onAcceptKey(pressed: Boolean) {
        if (pressed) answer(true)
    }

    fun onDeclineKey(pressed: Boolean) {
        if (pressed) answer(false)
    }

    fun onStopKey(pressed: Boolean) {
        if (!pressed) return
        val markerCancelled = cancelPendingPlays()
        val local = DuoPlayers.localOrLobby() ?: return
        val askCancelled = DuoEmoteState.cancelSentAsks()
        val plan = DuoStop.plan(
            markerCancelled,
            DuoEmoteState.sharedFor(local.uuid) != null,
            DuoEmoteState.hasOpenRequests(),
            askCancelled,
        )
        if (plan == DuoStop.Shared) sendStop()
        if (DuoStop.stopsSolo(plan, EmoteApi.playsSolo(local))) EmoteApi.stop(local)
    }

    fun cancelPendingPlays(): Boolean {
        EmoteWheelOverlay.cancelPending()
        val markerCancelled = ContextEmotes.cancel()
        EmoteApi.cancelSentPlay()
        return markerCancelled
    }

    fun stopAll() {
        cancelPendingPlays()
        DuoEmoteState.stopShared(DuoPlayers.localOrLobby()?.uuid)
            ?.onFailure { LOGGER.warn("Couldn't stop the duo emote", it) }
    }

    fun onPrivacyPicked(value: DuoEmoteRequests) {
        val save = DuoEmoteState.choosePrivacy(value.privacy)
        PolyPlusClient.SCOPE.launch {
            val result = DuoEmoteState.savePrivacy(save) ?: return@launch
            result
                .onSuccess { syncedPrivacy = value.privacy }
                .onFailure {
                    LOGGER.warn("Couldn't save the duo emote request setting", it)
                    tell(COULD_NOT_SAVE)
                    ClientPlatform.runOnMain {
                        val saved = DuoEmoteState.privacy
                        if (saved == SharedEmotePrivacy.Unknown || DuoEmoteState.savingPrivacy) return@runOnMain
                        syncedPrivacy = saved
                        PolyPlusCosmeticsConfig.duoEmoteRequests = DuoEmoteRequests.of(saved)
                    }
                }
        }
    }

    internal fun onError(code: String, message: String, requestId: Long?) {
        val failed = DuoEmoteState.failAsk(code, message, requestId) ?: return
        LOGGER.warn("The duo emote request to {} was turned down ({}): {}", failed.target, code, message)
        val name = nameOf(failed.target)
        tell(
            when (code) {
                "player_offline" -> "$name isn't online right now"
                "shared_emote_pending" -> "You already have a request open with $name"
                "rate_limited" -> "Too many requests. Wait a moment and try again."
                "not_owned" -> "You don't own that emote"
                "bad_request" -> "$name can't do duo emotes"
                else -> COULD_NOT_SEND
            },
        )
    }

    internal fun incomingPrompt(): DuoEmoteState.Request? = incomingPrompts().firstOrNull()

    internal fun incomingPrompts(): List<DuoEmoteState.Request> = DuoEmoteState.incomingPrompts()

    internal fun promptShown(request: DuoEmoteState.Request) {
        shownPrompts += request.requestId
    }

    internal fun visibleNotice(now: Long): String? = notices.show(now)

    internal fun waiting(): DuoEmoteState.Request? =
        DuoEmoteState.outgoingRequests().firstOrNull { !it.accepted }

    internal fun approach(): Approach? {
        val request = DuoEmoteState.outgoingRequests().firstOrNull { it.accepted }
            ?: DuoEmoteState.incomingRequests().firstOrNull { it.accepted }
            ?: return null
        val emote = emoteFor(request.emoteId, request.revision)?.takeIf { it.isDuo }
        if (emote == null) {
            load(request.emoteId, request.revision)
            return null
        }
        val local = DuoPlayers.local() ?: return null
        val partner = DuoPlayers.find(request.partner)
        val leader = if (request.incoming) partner else local
        val follower = if (request.incoming) local else partner
        val initiator = emote.mark(Emote.INITIATOR) ?: return null
        val partnerMark = emote.mark(Emote.PARTNER) ?: return null
        val anchor = leader?.let {
            DuoPlacement.anchorFrom(DuoPlayers.x(it), DuoPlayers.y(it), DuoPlayers.z(it), DuoPlayers.headYaw(it), initiator)
        }
        val spot = anchor?.let { DuoPlacement.spot(it, partnerMark) }
        val ready = spot != null && leader != null && follower != null && DuoPlacement.ready(
            spot,
            DuoPlayers.x(follower),
            DuoPlayers.y(follower),
            DuoPlayers.z(follower),
            DuoPlayers.headYaw(follower),
        ) && settled(emote, leader) && settled(emote, follower)
        return Approach(request, emote, leader, follower, anchor, spot, ready)
    }

    private fun settled(emote: Emote, player: AbstractClientPlayer): Boolean =
        !emote.restrains(PlayerSignals.sample(player, 1f)) && !EmoteConditions.interrupts(player)

    internal fun nameOf(uuid: UUID): String {
        DuoPlayers.find(uuid)?.let { return DuoPlayers.name(it) }
        val id = uuid.toString()
        PlayerNamesRepository.names.value[id]?.let { return it }
        if (requestedNames.add(id)) PlayerNamesRepository.resolve(listOf(id))
        return SOMEONE
    }

    internal fun emoteName(emoteId: Int): String =
        CosmeticCatalog.getEmoteDefinition(emoteId)?.name ?: AN_EMOTE

    private fun answer(accept: Boolean) {
        val request = incomingPrompt()?.takeIf { it.requestId !in answered } ?: return
        answered += request.requestId
        shownPrompts += request.requestId
        DuoEmoteState.respond(request.requestId, accept).onFailure {
            answered -= request.requestId
            LOGGER.warn("Couldn't answer duo emote request {}", request.requestId, it)
            tell(COULD_NOT_SEND)
        }
    }

    fun stopBeforeLocalPlay() {
        ContextEmotes.cancel()
        EmoteApi.cancelSentPlay()
        val local = DuoPlayers.localOrLobby() ?: return
        DuoEmoteState.stopForLocalPlay(local.uuid)?.let { CosmeticSync.endSharedLocally(it.players) }
        if (EmoteApi.playsSolo(local)) EmoteApi.stopNow(local)
    }

    fun cancelForSoloPlay() {
        val local = DuoPlayers.localOrLobby() ?: return
        DuoEmoteState.stopForLocalPlay(local.uuid)?.let { CosmeticSync.endSharedLocally(it.players) }
    }

    private fun sendStop(): Boolean =
        DuoEmoteState.stop().onFailure { LOGGER.warn("Couldn't stop the duo emote", it) }.isSuccess

    private fun tick() {
        val now = System.currentTimeMillis()
        syncPrivacy()
        if (DuoPlayers.local() == null) {
            known.clear()
            shownPrompts.clear()
            notices.clear()
            DuoEmoteState.dropEndings()
            requestedNames.clear()
            ContextEmotes.cancel()
            return
        }
        if (PolyConnection.isConnected) DuoEmoteState.cancelDeparted()
        followRequests()
        ContextEmotes.tick(now)
        startWhenReady(now)
        stopWhenLeft(now)
    }

    private fun syncPrivacy() {
        if (PolyConnection.isConnected) DuoEmoteState.ensurePrivacy()
        if (DuoEmoteState.savingPrivacy) return
        val privacy = DuoEmoteState.privacy
        if (privacy == SharedEmotePrivacy.Unknown || privacy == syncedPrivacy) return
        syncedPrivacy = privacy
        PolyPlusCosmeticsConfig.duoEmoteRequests = DuoEmoteRequests.of(privacy)
    }

    private fun followRequests() {
        if (known.isEmpty() && !DuoEmoteState.hasRequests() && !DuoEmoteState.hasEndings()) {
            if (shownPrompts.isNotEmpty()) shownPrompts.clear()
            return
        }
        val current = DuoEmoteState.unresolvedRequests().associateBy { it.requestId }
        for (ending in DuoEmoteState.followUp(known, current)) {
            answered -= ending.request.requestId
            noticeFor(ending.request, ending.result)?.let(::tell)
        }
        known.clear()
        known.putAll(current)
        shownPrompts.retainAll(current.keys)
        for (request in current.values) load(request.emoteId, request.revision)
    }

    private fun noticeFor(request: DuoEmoteState.Request, result: SharedEmoteResult?): String? =
        duoNotice(request, result, nameOf(request.partner), request.requestId in shownPrompts)

    private fun startWhenReady(now: Long) {
        val approach = approach()?.takeIf { it.leading }
        val anchor = approach?.anchor
        if (approach == null || anchor == null || !approach.ready) {
            readyFor = null
            return
        }
        val id = approach.request.requestId
        if (readyFor != id) {
            readyFor = id
            readySince = now
        }
        if (now - readySince < READY_HOLD_MS) return
        if (startSentFor == id && now - startSentAt < START_RETRY_MS) return
        startSentFor = id
        startSentAt = now
        DuoEmoteState.start(id, anchor).onFailure { LOGGER.warn("Couldn't start duo emote {}", id, it) }
    }

    private fun stopWhenLeft(now: Long) {
        val local = DuoPlayers.local() ?: return
        val session = DuoEmoteState.sharedFor(local.uuid) ?: return
        if (leftSession == session) return
        val index = DuoEmoteState.roleOf(local.uuid) ?: return
        val revision = DuoEmoteState.revisionOf(session)
        val emote = emoteFor(session.emoteId, revision)
        if (emote == null) {
            load(session.emoteId, revision)
            return
        }
        val mark = emote.mark(index) ?: return
        val spot = DuoPlacement.spot(session.anchor, mark)
        if (spot.distanceTo(DuoPlayers.x(local), DuoPlayers.y(local), DuoPlayers.z(local)) <= DuoPlacement.LEAVE_DISTANCE) return
        if (stopFailedFor == session && now < stopRetryAt) return
        DuoEmoteState.stop()
            .onSuccess {
                leftSession = session
                stopFailedFor = null
            }
            .onFailure {
                if (stopFailedFor != session) LOGGER.warn("Couldn't stop the duo emote", it)
                stopFailedFor = session
                stopRetryAt = now + STOP_RETRY_MS
            }
    }

    private fun emoteFor(emoteId: Int, revision: EmoteRevision?): Emote? =
        if (revision != null) CosmeticAssetCache.getEmote(revision) else CosmeticAssetCache.getEmote(emoteId)

    private fun load(emoteId: Int, revision: EmoteRevision? = null) {
        val key: Any = revision ?: emoteId
        if (emoteFor(emoteId, revision) != null || !loading.add(key)) return
        PolyPlusClient.SCOPE.launch {
            try {
                if (revision != null) {
                    CosmeticAssetCache.ensureEmoteLoaded(revision)
                } else {
                    CosmeticAssetCache.ensureEmoteLoaded(emoteId)
                }
            } finally {
                ClientPlatform.runOnMain { loading.remove(key) }
            }
        }
    }

    internal fun tell(text: String) {
        notices.add(text)
    }

    private const val SOLO_ROLE = 0
    private const val LOOK_DEGREES = 12.0
    private const val BODY_CENTER = 1.0
    private const val READY_HOLD_MS = 500L
    private const val START_RETRY_MS = 1000L
    private const val STOP_RETRY_MS = 1000L
    private const val NOTICE_MS = 3500L
    private const val SOMEONE = "Someone"
    private const val AN_EMOTE = "Duo emote"
    private const val COULD_NOT_SEND = "Couldn't reach PolyPlus. Try again in a moment."
    private const val COULD_NOT_SAVE = "Couldn't save who can send you duo emote requests"
    private const val NOBODY_NEARBY = "Nobody with PolyPlus is close enough. Stand within 8 blocks of them."
}
