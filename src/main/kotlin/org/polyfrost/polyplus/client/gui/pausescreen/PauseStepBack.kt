@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.polyplus.client.gui.MENU_PREVIEW_YAW
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.menuPreviewSpec
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.NavTab
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyActions
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyInviteCards
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyRoster
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayButton
import org.polyfrost.polyplus.client.gui.lobbyshell.Wordmark
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberModMenu
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.roundToInt

private val CLASSIC_ROW = 44.dp
private val TIGHT_ROW = 38.dp
private const val FLAT_DIM = 0.45f
private val TOOLS_GAP = 8.dp
private val MENU_RESERVE = 472.dp
private val PANEL_WIDTH = 360.dp
private val PANEL_MIN_WIDTH = 240.dp
private val PANEL_PREFERRED_MIN = 300.dp
private val PANEL_MAX_HEIGHT = 220.dp
private val PANEL_MIN_HEIGHT = 120.dp
private const val MAX_PANELS = 2
private const val FLAT_AVATAR_X = 0.35f
private const val FLAT_AVATAR_SHARE = 0.85f
private val FLAT_AVATAR_MIN = 120.dp
private val FLAT_PREVIEW_COLUMN = 300.dp
private val FLAT_TAG_ROOM = 56.dp
private val FLAT_TAG_GAP = 4.dp

private class PanelSlots(val count: Int, val width: Dp, val height: Dp)

private val NO_PANELS = PanelSlots(0, 0.dp, 0.dp)

private fun panelSlots(narrow: Boolean, wanted: Int, maxWidth: Dp, maxHeight: Dp, rowWidth: Dp): PanelSlots {
    if (narrow || wanted == 0) return NO_PANELS
    val preferred = if (rowWidth > 0.dp) maxOf(rowWidth, PANEL_PREFERRED_MIN) else PANEL_WIDTH
    val width = minOf(preferred, maxWidth - LobbyLayout.Margin - MENU_RESERVE - LobbyLayout.Gap)
    if (width < PANEL_MIN_WIDTH) return NO_PANELS
    val room = maxHeight - LobbyLayout.NavbarHeight - LobbyLayout.Gap - LobbyLayout.BarHeight - LobbyLayout.Margin - LobbyLayout.Gap
    var count = minOf(MAX_PANELS, wanted)
    while (count > 0 && (room - LobbyLayout.Gap * (count - 1)) / count < PANEL_MIN_HEIGHT) count--
    if (count == 0) return NO_PANELS
    return PanelSlots(count, width, minOf(PANEL_MAX_HEIGHT, (room - LobbyLayout.Gap * (count - 1)) / count))
}

@Composable
internal fun BoxScope.StepBackScreen(screen: OneClientPauseScreen, narrow: Boolean) {
    val reveal = rememberReveal()
    val place = rememberPlace()
    val state = rememberPauseState()
    LobbyDarkChrome {
        val ink = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        if (!screen.staged) Box(Modifier.matchParentSize().graphicsLayer { alpha = reveal }.background(ink.copy(alpha = FLAT_DIM)))
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = reveal }
                .background(Brush.horizontalGradient(0.5f to ink.copy(alpha = 0f), 0.66f to ink.copy(alpha = 0.72f), 0.8f to ink.copy(alpha = 0.92f), 1f to ink.copy(alpha = 0.96f))),
        )
        Box(Modifier.graphicsLayer { alpha = reveal }.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(0f to ink.copy(alpha = 0.92f), 0.42f to ink.copy(alpha = 0.74f), 1f to ink.copy(alpha = 0f))))
        Box(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = reveal }.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = 0.6f)))))
    }
    val widgets = rememberPauseWidgets()
    val density = LocalDensity.current
    var rowWidth by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.matchParentSize().graphicsLayer { alpha = reveal }) {
        val slots = panelSlots(narrow, widgets.view.panels.size, maxWidth, maxHeight, rowWidth)
        val panels = widgets.view.panels.take(slots.count)
        val panelArea = panelSlots(narrow, 1, maxWidth, maxHeight, rowWidth).count > 0
        val panelled = remember(panels) { panels.mapTo(HashSet()) { it.id } }
        if (screen.staged) AvatarTag(place.title) else if (screen.playerPreview) FlatAvatar(place.title, maxWidth, maxHeight, if (narrow) 368.dp else 472.dp)
        LobbyDarkChrome { StepBackNav(screen) }
        LobbyDarkChrome {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (narrow) 28.dp else 72.dp, top = 68.dp)
                    .width(if (narrow) 340.dp else 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Eyebrow(place.eyebrow)
                Spacer(Modifier.height(6.dp))
                SocialText(place.title, 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                if (place.detail.isNotBlank()) Label(place.detail, 14.sp, color = MutedText)
                Spacer(Modifier.height(6.dp))
                PauseStateLine(state)
                Spacer(Modifier.height(if (narrow) 12.dp else 18.dp))
                PlayButton(Modifier.fillMaxWidth().onGloballyPositioned { screen.resumeBounds = it.boundsInRoot() }, screen.capture.entry(PauseSlot.RESUME)?.label ?: "Back to Game", null, screen::resume)
                Spacer(Modifier.height(if (narrow) 8.dp else 12.dp))
                StepBackRows(screen, if (narrow) TIGHT_ROW else CLASSIC_ROW)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LobbyLayout.Margin, end = (if (narrow) 368.dp else 472.dp) + LobbyLayout.Gap, bottom = LobbyLayout.Margin)
                .onGloballyPositioned { rowWidth = with(density) { it.size.width.toDp() } },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(TOOLS_GAP),
        ) {
            PartyBar(screen, Modifier.weight(1f, fill = false))
            PauseWidgetBar(screen, widgets, panelled, panelArea = panelArea)
        }
        PauseWidgetPanels(
            screen,
            widgets,
            panels,
            slots.width,
            slots.height,
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LobbyLayout.Margin, bottom = LobbyLayout.Margin + LobbyLayout.BarHeight + LobbyLayout.Gap),
        )
    }
    if (screen.moreOpen) MoreDismiss(screen)
    if (screen.customizeOpen) {
        CustomizeDismiss(screen)
        LobbyDarkChrome {
            PauseCustomizePanel(screen, Modifier.align(Alignment.TopEnd).padding(top = LobbyLayout.NavbarHeight, end = LobbyLayout.Margin))
        }
    }
}

@Composable
private fun StepBackRows(screen: OneClientPauseScreen, rowHeight: Dp) {
    val capture = screen.capture
    capture.entry(PauseSlot.OPTIONS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    capture.entry(PauseSlot.WORLD)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    if (screen.canHost) MenuRow(asset("log-in-04"), PauseWorldAccess.hostLabel, height = rowHeight, onClick = screen::openHost)
    capture.entry(PauseSlot.ADVANCEMENTS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    capture.entry(PauseSlot.STATS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    rememberModMenu()?.let { (modMenu, logo) ->
        MenuRow(modMenu.icon, "Mods", height = rowHeight, logo = logo, tooltip = modMenu.tooltip) { modMenu.onClick(screen) }
    }
    LinkedModRow(screen, rowHeight)
    Box {
        MenuRow(asset("dots-vertical"), "More", height = rowHeight) {
            val showing = screen.moreOpen && !screen.moreLinked
            screen.moreLinked = false
            screen.moreOpen = !showing
        }
        if (screen.moreOpen && !screen.moreLinked) MorePopup(screen, rememberToolAnchor(true))
    }
    Spacer(Modifier.height(6.dp))
    Hairline()
    Spacer(Modifier.height(6.dp))
    capture.entry(PauseSlot.QUIT)?.let { MenuRow(it.glyph, it.label, height = rowHeight, danger = true, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
}

@Composable
private fun StepBackNav(screen: OneClientPauseScreen) {
    val groups by GroupsRepository.groups.collectAsState()
    val invites by PartyRepository.incomingInvites.collectAsState()
    Row(
        Modifier.fillMaxWidth().height(LobbyLayout.NavbarHeight).padding(horizontal = LobbyLayout.Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Wordmark() }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            NavTab("Game", active = true) { }
            NavTab("Wardrobe", active = false, onClick = screen::openWardrobe)
            if (screen.online) NavTab("Social", active = false, unread = groups.any { it.unread } || invites.isNotEmpty(), onClick = screen::openSocial)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            CustomizeButton(screen)
            IconButton(themedIcon("settings"), "Settings", Modifier, screen::openSettings)
            Spacer(Modifier.width(2.dp))
            Label(ClientPlatform.localPlayerName(), 14.sp, weight = FontWeight.Medium)
            PlayerHead(ClientPlatform.localPlayerUuid(), 38.dp, Modifier, headShape(38.dp))
        }
    }
}

@Composable
private fun PartyBar(screen: OneClientPauseScreen, modifier: Modifier) {
    val party by PartyRepository.party.collectAsState()
    Row(
        modifier.height(LobbyLayout.BarHeight).pauseGlass().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val current = party
        BoxWithConstraints(Modifier.widthIn(max = 520.dp).weight(1f, fill = false)) {
            if (current != null) {
                PartyRoster(PauseHost, current, maxWidth)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerHead(ClientPlatform.localPlayerUuid(), 30.dp, Modifier, headShape(30.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label(ClientPlatform.localPlayerName(), 14.sp, weight = FontWeight.Medium)
                        Label("You", 11.sp, color = MutedText)
                    }
                }
            }
        }
        PartyActions(current, onVoice = { SocialOverlay.openVoice(screen) }.takeIf { screen.online })
        if (screen.online) PartyInviteCards(PauseHost)
    }
}

@Composable
private fun BoxScope.AvatarTag(where: String) {
    val frame by rememberFrame()
    val root = LocalRootSize.current
    val spot = remember(frame) { Offset(PauseStage.tagX, PauseStage.tagY) }
    AvatarTagCard(
        where,
        Modifier
            .align(Alignment.TopStart)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val x = (spot.x * root.width).roundToInt()
                val y = (spot.y * root.height).roundToInt().coerceAtLeast(LobbyLayout.NavbarHeight.roundToPx() + placeable.height)
                layout(0, 0) { placeable.place(x - placeable.width / 2, y - placeable.height) }
            },
    )
}

@Composable
private fun BoxScope.FlatAvatar(where: String, maxWidth: Dp, maxHeight: Dp, menuReserve: Dp) {
    val spec = menuPreviewSpec()
    val top = LobbyLayout.NavbarHeight + LobbyLayout.Gap
    val bottom = LobbyLayout.PageBottom
    val right = maxWidth - menuReserve - LobbyLayout.Gap
    val roomWidth = right - LobbyLayout.Margin
    val roomHeight = maxHeight - top - bottom - FLAT_TAG_ROOM
    val aspect = FLAT_PREVIEW_COLUMN / spec.height
    val height = minOf(roomHeight * FLAT_AVATAR_SHARE, roomWidth / aspect)
    if (height < FLAT_AVATAR_MIN) return
    val width = height * aspect
    val centre = (maxWidth * FLAT_AVATAR_X).coerceIn(LobbyLayout.Margin + width / 2, right - width / 2)
    Box(Modifier.matchParentSize().padding(top = top, bottom = bottom)) {
        Column(
            Modifier.align(Alignment.CenterStart).offset(x = centre - width / 2).width(width),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarTagCard(where, Modifier)
            Spacer(Modifier.height(FLAT_TAG_GAP))
            PlayerPreview(
                Modifier.width(width).height(height),
                source = PlayerPreviewSource.LocalLive,
                autoSpin = !Motion.reduced,
                modelScale = spec.modelScale,
                verticalAnchor = 1f,
                initialYaw = MENU_PREVIEW_YAW,
                live = true,
                bottomFadeFraction = 1f - spec.fadeStart,
            )
        }
    }
}

@Composable
private fun AvatarTagCard(where: String, modifier: Modifier) {
    Column(
        modifier
            .pauseGlass(8.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Label(ClientPlatform.localPlayerName(), 14.sp, weight = FontWeight.Medium)
            Label("You", 12.sp, color = MutedText)
        }
        Label(where, 11.sp, color = MutedText)
    }
}
