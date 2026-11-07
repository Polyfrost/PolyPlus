@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.PauseMenuPanel
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbyshell.Head
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyActions
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyInviteCards
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyRoster
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayButton
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.roundToInt

private val CLASSIC_ROW = 44.dp
private val TIGHT_ROW = 38.dp
private const val FLAT_DIM = 0.45f
private val TOOLS_GAP = 8.dp
private val TOOLS_RULE = 24.dp

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
    Box(Modifier.matchParentSize().graphicsLayer { alpha = reveal }) {
        if (screen.staged) AvatarTag(place.title)
        LobbyDarkChrome { StepBackNav(screen) }
        LobbyDarkChrome {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = if (narrow) 28.dp else 72.dp, top = 68.dp)
                    .width(if (narrow) 340.dp else 400.dp),
            ) {
                Eyebrow(place.eyebrow)
                Spacer(Modifier.height(6.dp))
                SocialText(place.title, 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                if (place.detail.isNotBlank()) Label(place.detail, 14.sp, color = MutedText)
                Spacer(Modifier.height(6.dp))
                PauseStateLine(state)
                Spacer(Modifier.height(if (narrow) 12.dp else 18.dp))
                PlayButton(Modifier.fillMaxWidth().onGloballyPositioned { screen.resumeBounds = it.boundsInRoot() }, screen.capture.entry(PauseSlot.RESUME)?.label ?: "Back to Game", "Esc", screen::resume)
                Spacer(Modifier.height(if (narrow) 8.dp else 12.dp))
                StepBackRows(screen, if (narrow) TIGHT_ROW else CLASSIC_ROW)
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LobbyLayout.Margin, end = (if (narrow) 368.dp else 472.dp) + LobbyLayout.Gap, bottom = LobbyLayout.Margin),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(TOOLS_GAP),
        ) {
            PartyBar(screen, Modifier.weight(1f, fill = false))
            StepBackTools(screen)
        }
    }
    if (screen.moreOpen) {
        MoreDismiss(screen)
        LobbyDarkChrome {
            MorePanel(screen, Modifier.align(Alignment.CenterEnd).padding(end = if (narrow) 376.dp else 488.dp, top = 68.dp))
        }
    }
}

@Composable
private fun StepBackTools(screen: OneClientPauseScreen) {
    val tools = rememberPauseTools()
    if (tools.isEmpty()) return
    Row(
        Modifier.height(LobbyLayout.BarHeight).pauseGlass(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tools.forEachIndexed { index, tool ->
            key(tool) {
                if (index > 0) Box(Modifier.width(1.dp).height(TOOLS_RULE).background(SocialBorderColor))
                when (tool) {
                    PauseTool.RECORDING -> RecordingBarTool(screen, glass = false)
                    PauseTool.PERFORMANCE -> PerformanceBarTool(screen, glass = false)
                    PauseTool.SCENE -> SceneBarTool(glass = false)
                }
            }
        }
    }
}

@Composable
private fun StepBackRows(screen: OneClientPauseScreen, rowHeight: Dp) {
    val capture = screen.capture
    capture.entry(PauseSlot.OPTIONS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    capture.entry(PauseSlot.WORLD)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    if (screen.canHost) MenuRow(asset("log-in-04"), PauseWorldAccess.hostLabel, "Invite friends", height = rowHeight, onClick = screen::openHost)
    capture.entry(PauseSlot.ADVANCEMENTS)?.let { MenuRow(it.glyph, it.label, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press) }
    capture.entry(PauseSlot.STATS)?.let {
        val time = remember { PauseMenuPanel.playtimeShort() }
        MenuRow(it.glyph, it.label, time, height = rowHeight, enabled = it.enabled, tooltip = it.tooltip, onClick = it.press)
    }
    MenuRow(asset("dots-vertical"), "More", height = rowHeight) { screen.moreOpen = !screen.moreOpen }
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
            SocialIconButton("settings", tooltip = "Settings", onClick = screen::openSettings)
            Spacer(Modifier.width(2.dp))
            Label(ClientPlatform.localPlayerName(), 14.sp, weight = FontWeight.Medium)
            Head(ClientPlatform.localPlayerUuid(), 38.dp, realSkin = true)
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
                    Head(ClientPlatform.localPlayerUuid(), 30.dp, realSkin = true)
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
    Column(
        Modifier
            .align(Alignment.TopStart)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val x = (spot.x * root.width).roundToInt()
                val y = (spot.y * root.height).roundToInt().coerceAtLeast(LobbyLayout.NavbarHeight.roundToPx() + placeable.height)
                layout(0, 0) { placeable.place(x - placeable.width / 2, y - placeable.height) }
            }
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
