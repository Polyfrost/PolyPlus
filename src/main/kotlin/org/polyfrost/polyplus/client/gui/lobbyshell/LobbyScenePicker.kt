@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.selectableCard
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.lobby.LobbyPacks
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val QUICK_RECENT = 4
private val RowHeight = 46.dp
private val RowGap = 6.dp
private val PickerChrome = 16.dp * 2 + 32.dp + 10.dp
private val PartyGap = 10.dp
private val PartySeedHeight = 44.dp * 2 + PartyGap
private val FallbackListHeight = 420.dp

@Composable
internal fun BoxScope.SceneControl(lobby: LobbyShell, barStart: () -> Float) {
    if (lobby.page != LobbyPage.Home || !lobby.sceneOpen) return
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .offset {
                val start = barStart()
                IntOffset(if (start.isNaN()) LobbyLayout.BarStart.roundToPx() else start.roundToInt(), 0)
            }
            .padding(bottom = LobbyLayout.PageBottom)
            .width(LobbyLayout.UnitWidth),
    ) {
        EscapeCloses { lobby.sceneOpen = false }
        ScenePicker { lobby.sceneOpen = false }
    }
}

@Composable
private fun ScenePicker(onClose: () -> Unit) {
    val party by PartyRepository.party.collectAsState()
    val member = PartyScene.isMember(party)
    val following = member && !PartyScene.keepOwnBackdrop
    val pinned = LobbySceneStore.pinned
    var managing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var partyHeight by remember(member) { mutableStateOf(with(density) { PartySeedHeight.roundToPx() }) }
    if (managing) SceneManagerDialog { managing = false }
    Column(Modifier.fillMaxWidth().panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Lobby scene", Modifier.weight(1f), style = OneConfigTheme.current.typography.heading, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            IconButton(
                icon = SOCIAL_ASSETS + "settings-02.svg",
                contentDescription = "Scenes and settings",
                onClick = { managing = true },
                size = ControlSize.Regular,
                tooltipPlacement = TooltipPlacement.BelowStart,
            )
            IconButton(
                icon = SOCIAL_ASSETS + "x-close.svg",
                contentDescription = "Close",
                onClick = onClose,
                size = ControlSize.Regular,
                tooltip = null,
                tooltipPlacement = TooltipPlacement.BelowStart,
            )
        }
        if (member) {
            Column(Modifier.onSizeChanged { partyHeight = it.height }, verticalArrangement = Arrangement.spacedBy(PartyGap)) { PartyBackdrop(following) }
        }
        val scroll = rememberScrollState()
        ScrollContainer(scroll, Modifier.heightIn(max = listRoom(if (member) partyHeight else null)), scrollbar = false, fadingEdges = true) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                QuickRow("Automatic", "Best recent scene", !following && pinned == null, !following, false) { pickScene(null) }
                key(LobbyPacks.revision) {
                    if (LobbySceneStore.captures.isEmpty()) SceneNote("No captures yet. Good spots appear here after you play your worlds.")
                    quickScenes(pinned).forEach { entry ->
                        QuickRow(entry.label, sceneLine(entry), !following && pinned == entry.id, !following, entry.favourite) { pickScene(entry.id) }
                    }
                }
                SectionHeader("Polyfrost scenes")
                LobbySceneStore.bundledScenes.forEach { entry ->
                    QuickRow(entry.label, sceneLine(entry), !following && pinned == entry.id, !following, false) { pickScene(entry.id) }
                }
            }
        }
    }
}

@Composable
private fun listRoom(partyPx: Int?): Dp {
    val root = LocalRootSize.current
    if (root.height <= 0f) return FallbackListHeight
    val density = LocalDensity.current
    val height = with(density) { root.height.toDp() }
    val party = if (partyPx == null) 0.dp else with(density) { partyPx.toDp() } + PartyGap
    val fixed = LobbyLayout.Top + LobbyLayout.PageBottom + PickerChrome + party
    return (height - fixed).coerceAtLeast(RowHeight)
}

private fun quickScenes(pinned: String?): List<LobbySceneEntry> {
    val usable = LobbySceneStore.usableScenes
    val rest = usable.filterNot { it.favourite }
    return usable.filter { it.favourite } + rest.take(QUICK_RECENT) + rest.drop(QUICK_RECENT).filter { it.id == pinned }
}

@Composable
private fun QuickRow(
    title: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .card(8.dp)
            .selectableCard(selected, onClick, "$title, $detail", OneConfigTheme.shape(8.dp), enabled, role = A11yRole.Button)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = theme.textColor, style = theme.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(detail, color = theme.textColorSecondary, style = theme.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (favourite) FavouriteStar()
    }
}
