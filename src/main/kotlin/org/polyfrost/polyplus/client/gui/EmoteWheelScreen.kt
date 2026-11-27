@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.WorldEvent
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.PageDots
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.DevBundles
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import org.polyfrost.polyplus.client.emotes.EmoteApi
import org.polyfrost.polyplus.client.emotes.EmoteLoads
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import org.polyfrost.polyplus.client.emotes.context.ContextEmotes
import org.polyfrost.polyplus.client.emotes.duo.DuoEmotes
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.min
import kotlin.math.roundToInt

private const val EMOTE_ICON = "assets/polyplus/mainmenu/play-circle.svg"
private const val PIXEL_EMOTE_ICON = "/assets/oneconfig/ico/minecraft/star.svg"
private const val EMPTY_ICON = "assets/polyplus/mainmenu/plus.svg"
private const val DUO_ICON = "assets/polyplus/mainmenu/users-01.svg"
private const val PARTNER_ICON = "assets/polyplus/mainmenu/user-01.svg"
private const val PRESELECT_FRAMES = 10
private const val LOAD_FAILED = "That emote didn't load. Try again."
private const val PARTNER_PAGE = 8
private const val LOAD_ATTEMPTS = 3
private const val LOBBY_DUO_NOTE = "Duo emotes only play in game"

object EmoteWheelOverlay {
    private val logger = LogManager.getLogger("polyplus/emote-wheel")

    @Volatile
    private var picks = 0

    private var partnerWheel: WheelSession? = null

    private var mainWheel: WheelSession? = null

    init {
        eventHandler<WorldEvent.Load> { cancelPending() }
        eventHandler<WorldEvent.Unload> { cancelPending() }
    }

    fun cancelPending() {
        picks++
        EmoteApi.dropQueuedPlays()
        mainWheel?.let { WheelOverlay.settle(it, false) }
        mainWheel = null
        partnerWheel?.let { WheelOverlay.settle(it, false) }
        partnerWheel = null
    }

    fun open(): Boolean {
        preloadActive()
        val wheel = session(::play, lobby = false)
        if (!WheelOverlay.open(wheel)) return false
        mainWheel = wheel
        return true
    }

    fun openOverLobby(): Boolean {
        preloadActive()
        val wheel = session(::playInLobby, lobby = true)
        if (!WheelOverlay.openOverLobby(wheel)) return false
        mainWheel = wheel
        return true
    }

    private fun session(onPick: (Int) -> Unit, lobby: Boolean) = WheelSession(
        held = EmoteWheelKeybind::isHeld,
        onPick = { slot ->
            mainWheel = null
            onPick(slot)
        },
        onScroll = { step ->
            EmoteWheels.cycle(step)
            preloadActive()
        },
        content = { session -> EmoteWheelContent(session, lobby) },
    )

    private fun preloadActive() {
        DuoEmotes.preload(EmoteWheels.slots(EmoteWheels.activeIndex).filterNotNull())
    }

    private fun play(slot: Int) {
        val emoteId = EmoteWheels.slots(EmoteWheels.activeIndex).getOrNull(slot) ?: return
        pick(emoteId, false)
    }

    fun pick(emoteId: Int, later: Boolean) {
        val pick = ++picks
        ContextEmotes.cancel()
        if (!later && ready(emoteId)) {
            start(emoteId)
            return
        }
        load(emoteId, pick, LOAD_ATTEMPTS, ::start)
    }

    private fun load(emoteId: Int, pick: Int, attempts: Int, then: (Int) -> Unit) {
        PolyPlusClient.SCOPE.launch {
            val attempted = revision(emoteId)
            val loaded = ready(emoteId) || (attempted != null && CosmeticAssetCache.ensureEmoteLoaded(attempted))
            ClientPlatform.runOnMain {
                if (pick != picks) return@runOnMain
                when {
                    ready(emoteId) -> then(emoteId)
                    EmoteLoads.retry(attempted, revision(emoteId), loaded, attempts) -> load(emoteId, pick, attempts - 1, then)
                    else -> {
                        logger.warn("Emote {} didn't load, so it wasn't played", emoteId)
                        DuoEmotes.tell(LOAD_FAILED)
                    }
                }
            }
        }
    }

    internal fun wheelSlot(definition: CosmeticDefinition?): EmoteLoads.WheelSlot {
        if (definition == null) return EmoteLoads.WheelSlot.Empty
        val ready = ready(definition.id)
        return EmoteLoads.wheelSlot(
            defined = true,
            ready = ready,
            loading = !ready && DuoEmotes.isLoading(definition.id),
            duo = ready && DuoEmotes.isDuo(definition.id),
        )
    }

    private fun ready(emoteId: Int): Boolean {
        val revision = revision(emoteId) ?: return true
        return CosmeticAssetCache.getEmote(revision) != null
    }

    private fun revision(emoteId: Int): EmoteRevision? = CosmeticCatalog.getEmoteDefinition(emoteId)?.let(EmoteRevision::of)

    private fun start(emoteId: Int) {
        if (ContextEmotes.begin(emoteId)) return
        if (DuoEmotes.isDuo(emoteId)) {
            pickPartner(emoteId)
            return
        }
        if (DevBundles.playEmote(emoteId, null)) return
        DuoEmotes.cancelForSoloPlay()
        val ticket = EmoteApi.playTicket()
        PolyPlusClient.SCOPE.launch {
            CosmeticService.playEmote(emoteId, ticket = ticket).onFailure {
                if (it !is EmoteApi.PlayCancelled) logger.error("Failed to play emote {}", emoteId, it)
            }
        }
    }

    private fun pickPartner(emoteId: Int) {
        val nearby = DuoEmotes.nearby()
        if (nearby.partners.isEmpty()) {
            DuoEmotes.tellNobodyNearby()
            return
        }
        val pages = PartnerPages(nearby)
        val pick = picks
        val wheel = WheelSession(
            held = { true },
            onPick = { index ->
                partnerWheel = null
                if (pick == picks) pages.partner(index)?.let { DuoEmotes.ask(emoteId, it.uuid) }
            },
            onScroll = pages::cycle,
            content = { session -> PartnerWheelContent(session, pages) },
        )
        if (WheelOverlay.open(wheel)) {
            partnerWheel = wheel
        } else {
            logger.warn("Could not open the partner picker for emote {}", emoteId)
        }
    }

    private fun playInLobby(slot: Int) {
        val emoteId = EmoteWheels.slots(EmoteWheels.activeIndex).getOrNull(slot) ?: return
        val pick = ++picks
        if (ready(emoteId)) {
            startInLobby(emoteId)
            return
        }
        load(emoteId, pick, LOAD_ATTEMPTS, ::startInLobby)
    }

    private fun startInLobby(emoteId: Int) {
        if (DuoEmotes.isDuo(emoteId)) return
        if (DevBundles.playEmote(emoteId, null)) return
        PolyPlusClient.SCOPE.launch {
            if (!EmoteApi.playOwnedEmote(emoteId)) logger.warn("Could not play emote {} in the lobby", emoteId)
        }
    }
}

@Composable
private fun EmoteWheelContent(session: WheelSession, lobby: Boolean) {
    val wheelIndex = EmoteWheels.activeIndex
    val wheelCount = EmoteWheels.wheelCount
    val slotIds = remember(wheelIndex, wheelCount) { EmoteWheels.slots(wheelIndex) }
    val emoteIcon = if (isMinecraftTheme) PIXEL_EMOTE_ICON else EMOTE_ICON
    val duoIcon = if (isMinecraftTheme) PIXEL_EMOTE_ICON else DUO_ICON
    val definitions = remember(slotIds) { slotIds.map { id -> id?.let(CosmeticCatalog::getEmoteDefinition) } }
    var kinds by remember(definitions) { mutableStateOf(definitions.map(EmoteWheelOverlay::wheelSlot)) }
    LaunchedEffect(definitions) {
        while (kinds.any { it == EmoteLoads.WheelSlot.Loading }) {
            withFrameNanos { }
            val next = definitions.map(EmoteWheelOverlay::wheelSlot)
            if (next != kinds) kinds = next
        }
    }
    val lobbyDuo = lobby && kinds.any { it == EmoteLoads.WheelSlot.Duo }
    val entries = remember(definitions, kinds, emoteIcon, duoIcon, lobby) {
        definitions.mapIndexed { index, definition ->
            if (definition == null) return@mapIndexed RadialMenuItem(label = "Empty", icon = EMPTY_ICON, enabled = false)
            when (kinds[index]) {
                EmoteLoads.WheelSlot.Empty -> RadialMenuItem(label = "Empty", icon = EMPTY_ICON, enabled = false)
                EmoteLoads.WheelSlot.Loading -> RadialMenuItem(
                    label = definition.name,
                    icon = emoteIcon,
                    detail = "Loading…",
                    enabled = false,
                )
                EmoteLoads.WheelSlot.Unloaded -> RadialMenuItem(
                    label = definition.name,
                    icon = emoteIcon,
                    detail = "Didn't load. Release to try again",
                )
                EmoteLoads.WheelSlot.Duo -> if (lobby) {
                    RadialMenuItem(label = definition.name, icon = duoIcon, detail = LOBBY_DUO_NOTE, enabled = false)
                } else {
                    RadialMenuItem(label = definition.name, icon = duoIcon, detail = "Release to pick a partner")
                }
                EmoteLoads.WheelSlot.Solo -> RadialMenuItem(label = definition.name, icon = emoteIcon, detail = "Release to play")
            }
        }
    }
    val empty = definitions.all { it == null }
    val title = if (empty) "No emotes yet" else "Emotes"
    val subtitle = when {
        empty -> null
        wheelCount > 1 && lobbyDuo -> "Wheel ${wheelIndex + 1} of $wheelCount · $LOBBY_DUO_NOTE"
        wheelCount > 1 -> "Wheel ${wheelIndex + 1} of $wheelCount · Scroll for more"
        lobbyDuo -> LOBBY_DUO_NOTE
        else -> null
    }
    HudRadialMenu(
        items = entries,
        session = session,
        title = title,
        subtitle = subtitle,
        below = if (wheelCount > 1) {
            { PageDots(wheelCount, wheelIndex, framed = true, glass = liquidGlassTheme && hudBackdropGlass) }
        } else {
            null
        },
    )
}

private class PartnerPages(val nearby: DuoEmotes.Nearby) {
    val count = (nearby.partners.size + PARTNER_PAGE - 1) / PARTNER_PAGE

    var page by mutableStateOf(if (nearby.preselected < 0) 0 else nearby.preselected / PARTNER_PAGE)

    fun shown(page: Int): List<DuoEmotes.Partner> {
        val from = page * PARTNER_PAGE
        if (from >= nearby.partners.size) return emptyList()
        return nearby.partners.subList(from, min(nearby.partners.size, from + PARTNER_PAGE))
    }

    fun partner(index: Int): DuoEmotes.Partner? =
        if (index in 0 until PARTNER_PAGE) nearby.partners.getOrNull(page * PARTNER_PAGE + index) else null

    fun preselected(page: Int): Int =
        if (nearby.preselected >= 0 && nearby.preselected / PARTNER_PAGE == page) nearby.preselected % PARTNER_PAGE else -1

    fun cycle(step: Int) {
        if (count > 1) page = Math.floorMod(page + step, count)
    }
}

@Composable
private fun PartnerWheelContent(session: WheelSession, pages: PartnerPages) {
    val page = pages.page
    val entries = remember(pages, page) {
        pages.shown(page).map { RadialMenuItem(label = it.name, icon = PARTNER_ICON, detail = distanceText(it.distance)) }
    }
    LaunchedEffect(pages, page) {
        val preselect = pages.preselected(page)
        if (preselect < 0) return@LaunchedEffect
        repeat(PRESELECT_FRAMES) {
            withFrameNanos { }
            if (session.state.highlight(preselect)) return@LaunchedEffect
        }
    }
    HudRadialMenu(
        items = entries,
        session = session,
        title = "Who with?",
        subtitle = if (pages.count > 1) {
            "Page ${page + 1} of ${pages.count} · Scroll for more"
        } else {
            "Click to ask, right click to cancel"
        },
        below = if (pages.count > 1) {
            { PageDots(pages.count, page, framed = true, glass = liquidGlassTheme && hudBackdropGlass) }
        } else {
            null
        },
    )
}

private fun distanceText(distance: Double): String = when (val blocks = distance.roundToInt()) {
    0 -> "Right next to you"
    1 -> "1 block away"
    else -> "$blocks blocks away"
}
