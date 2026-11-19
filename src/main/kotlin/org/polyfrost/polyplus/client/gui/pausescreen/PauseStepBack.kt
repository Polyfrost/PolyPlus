@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.polyplus.client.gui.lobbyshell.centerVerticallyExact
import androidx.compose.foundation.layout.fillMaxHeight
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.polyplus.client.gui.MENU_PREVIEW_YAW
import org.polyfrost.polyplus.client.gui.menuPreviewSpec
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.NavTabRow
import org.polyfrost.polyplus.client.gui.lobbyshell.navTabBadge
import org.polyfrost.polyplus.client.gui.lobbyshell.socialDotLabel
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyActions
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyInviteCards
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyRoster
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayButton
import org.polyfrost.polyplus.client.gui.lobbyshell.Wordmark
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private val CLASSIC_ROW = 44.dp
private val TIGHT_ROW = 38.dp
private const val FLAT_DIM = 0.45f
private val TOOLS_GAP = 8.dp
private const val FLAT_AVATAR_X = 0.35f
private const val FLAT_AVATAR_SHARE = 0.85f
private val FLAT_AVATAR_MIN = 120.dp
private val FLAT_PREVIEW_COLUMN = 300.dp
private val FLAT_TAG_ROOM = 56.dp
private val FLAT_TAG_GAP = 4.dp
private val BAR_HEAD = 30.dp
private val AWAY_SHIFT = 48.dp

private enum class PauseNavTab { Game, Wardrobe, Social }

@Composable
internal fun BoxScope.StepBackScreen(screen: OneClientPauseScreen, narrow: Boolean, away: Float = 0f) {
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
        if (PolyPlusPauseConfig.pauseVignette) {
            Box(Modifier.graphicsLayer { alpha = reveal }.fillMaxWidth().height(150.dp).background(Brush.verticalGradient(0f to ink.copy(alpha = 0.92f), 0.42f to ink.copy(alpha = 0.74f), 1f to ink.copy(alpha = 0f))))
            Box(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = reveal }.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = 0.6f)))))
        }
    }
    BoxWithConstraints(Modifier.matchParentSize().graphicsLayer { alpha = reveal }) {
        val roomWidth = maxWidth
        val roomHeight = maxHeight
        Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - away }) {
            if (screen.staged) AvatarTag(place.title) else if (screen.playerPreview) FlatAvatar(place.title, roomWidth, roomHeight, if (narrow) 368.dp else 472.dp)
        }
        LobbyDarkChrome { StepBackNav(screen) }
        LobbyDarkChrome {
            val scroll = rememberScrollState()
            ScrollContainer(scroll, Modifier.align(Alignment.CenterEnd).padding(end = if (narrow) 28.dp else 72.dp, top = 68.dp).width(if (narrow) 340.dp else 400.dp).graphicsLayer {
                alpha = 1f - away
                translationX = away * AWAY_SHIFT.toPx()
            }, scrollbar = false) {
                Column(Modifier.fillMaxWidth()) {
                    SectionHeader(place.eyebrow, contentPadding = PaddingValues(0.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(place.title, style = OneConfigTheme.current.typography.body.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(2.dp))
                    if (place.detail.isNotBlank()) Text(place.detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    Spacer(Modifier.height(6.dp))
                    PauseStateLine(state)
                    Spacer(Modifier.height(if (narrow) 12.dp else 18.dp))
                    PlayButton(Modifier.fillMaxWidth().onGloballyPositioned { screen.resumeBounds = it.boundsInRoot() }, screen.capture.entry(PauseSlot.RESUME)?.label ?: "Back to Game", null, screen::resume)
                    Spacer(Modifier.height(if (narrow) 8.dp else 12.dp))
                    StepBackRows(screen, if (narrow) TIGHT_ROW else CLASSIC_ROW)
                }
            }
        }
        val toolsWidth = pauseWidgetWidth(narrow)
        val widgetRoom = maxHeight - LobbyLayout.NavbarHeight - LobbyLayout.Gap - LobbyLayout.BarHeight - TOOLS_GAP - LobbyLayout.Margin
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LobbyLayout.Margin, bottom = LobbyLayout.Margin)
                .width(toolsWidth)
                .graphicsLayer { alpha = 1f - away },
            verticalArrangement = Arrangement.spacedBy(TOOLS_GAP),
        ) {
            PauseWidgetColumn(screen, toolsWidth, widgetRoom.coerceAtLeast(0.dp))
            PartyBar(screen, Modifier.fillMaxWidth())
        }
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
    if (screen.canWorldOptions) MenuRow(asset("globe-01"), "World options", height = rowHeight, onClick = screen::openWorldOptions)
    capture.entry(PauseSlot.WORLD)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    if (screen.canHost) MenuRow(asset("log-in-04"), PauseWorldAccess.hostLabel, height = rowHeight, onClick = screen::openHost)
    capture.entry(PauseSlot.ADVANCEMENTS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    capture.entry(PauseSlot.STATS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    MenuRow("settings", "Mods", height = rowHeight, onClick = screen::openMods)
    LinkedModRow(screen, rowHeight)
    val moreShown = screen.moreOpen && !screen.moreLinked
    Box(Modifier.focusRequester(rememberMoreTrigger(moreShown))) {
        MenuRow(asset("dots-vertical"), "More", height = rowHeight) {
            screen.moreLinked = false
            screen.moreOpen = !moreShown
        }
        if (moreShown) MorePopup(screen, rememberToolAnchor(true))
    }
    Spacer(Modifier.height(6.dp))
    Divider()
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
        val tabs = buildList {
            add(TabItem(PauseNavTab.Game, "Game"))
            add(TabItem(PauseNavTab.Wardrobe, "Wardrobe"))
            if (screen.online) {
                val dot = socialDotLabel(groups.any { it.unread }, invites.isNotEmpty())
                add(TabItem(PauseNavTab.Social, "Social", badge = navTabBadge(dot != null), badgeDescription = dot))
            }
        }
        NavTabRow(tabs, PauseNavTab.Game, { tab ->
            when (tab) {
                PauseNavTab.Game -> Unit
                PauseNavTab.Wardrobe -> screen.openWardrobe()
                PauseNavTab.Social -> screen.openSocial()
            }
        })
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            CustomizeButton(screen)
            IconButton(icon = "settings", contentDescription = "Settings", onClick = screen::openSettings)
            Spacer(Modifier.width(2.dp))
            Text(ClientPlatform.localPlayerName(), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            PlayerHead(playerId = ClientPlatform.localPlayerUuid(), size = 38.dp, modifier = Modifier, shape = headShape(38.dp))
        }
    }
}

@Composable
private fun PartyBar(screen: OneClientPauseScreen, modifier: Modifier) {
    val party by PartyRepository.party.collectAsState()
    Row(
        modifier.height(LobbyLayout.BarHeight).pauseGlass().padding(start = (LobbyLayout.BarHeight - BAR_HEAD) / 2, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val current = party
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            if (current != null) {
                PartyRoster(PauseHost, current, maxWidth)
            } else {
                Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerHead(playerId = ClientPlatform.localPlayerUuid(), size = BAR_HEAD, modifier = Modifier.centerVerticallyExact(), shape = headShape(BAR_HEAD))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(ClientPlatform.localPlayerName(), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        Text("You", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    }
                }
            }
        }
        Box(Modifier.centerVerticallyExact()) { PartyActions(current, onVoice = { SocialOverlay.openVoice(screen) }.takeIf { screen.online }) }
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
            Text(ClientPlatform.localPlayerName(), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text("You", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        Text(where, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}
