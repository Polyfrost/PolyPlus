@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.singleplayer

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.polyplus.client.gui.lobbyshell.UnderlineTabs
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.vanillamenu.MenuText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.NoMatchState
import org.polyfrost.polyplus.client.gui.lobbyshell.PageGrid
import org.polyfrost.polyplus.client.gui.lobbyshell.PageHeader
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Locale
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer

private object LastPlayedKey

@Composable
internal fun SingleplayerPage(selectedWorldId: String?, onBack: () -> Unit, onSelect: (String) -> Unit, onHost: (String) -> Unit) {
    val host = ClientPlatform.currentScreen()
    val state = remember(host) { SingleplayerState.of(host) }
    SideEffect { state.onSelect = onSelect }
    DisposableEffect(state) { onDispose { state.onSelect = {} } }
    LaunchedEffect(state) { state.reload() }
    LaunchedEffect(selectedWorldId) {
        if (selectedWorldId != null) state.selectedId = selectedWorldId
    }
    PageScrim()
    Row(
        Modifier.fillMaxSize().padding(start = LobbyLayout.Margin, end = LobbyLayout.Margin, top = LobbyLayout.Top),
        horizontalArrangement = Arrangement.spacedBy(LobbyLayout.Gap),
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(bottom = LobbyLayout.PageBottom),
            verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
        ) {
            WorldsHeader(state, onBack)
            WorldsBody(state, onHost, Modifier.weight(1f).fillMaxWidth())
        }
        WorldDetailsPanel(state, onHost, Modifier.width(LobbyLayout.UnitWidth).fillMaxHeight().padding(bottom = LobbyLayout.RightColumnBottom))
    }
}

@Composable
private fun WorldsHeader(state: SingleplayerState, onBack: () -> Unit) {
    val showTabs = state.phase == WorldListPhase.Loading || state.worlds.isNotEmpty()
    PageHeader(
        "Worlds",
        state.query,
        { state.query = it },
        "Search worlds",
        onBack,
        filters = if (showTabs) {
            { UnderlineTabs(WorldFilter.entries.map { TabItem(it, it.label) }, state.filter) { state.filter = it } }
        } else {
            null
        },
    ) {
        TextButton(text = "Create world", icon = LobbyIcons.PLUS, onClick = state.flows::create, style = ControlStyle.Secondary, size = ControlSize.Large)
    }
}

@Composable
private fun WorldsBody(state: SingleplayerState, onHost: (String) -> Unit, modifier: Modifier) {
    when {
        state.phase == WorldListPhase.Failed -> EmptyState("Unable to load worlds", modifier.panel(), state.failure.orEmpty()) {
            TextButton(text = "Try again", icon = LobbyIcons.REFRESH, onClick = state::reload, style = ControlStyle.Primary, size = ControlSize.Large)
        }
        state.phase == WorldListPhase.Loading -> EmptyState("Loading worlds", modifier.panel())
        state.worlds.isEmpty() -> EmptyState("No worlds yet", modifier.panel(), "Create a world to start playing.") {
            TextButton(text = "Create world", icon = LobbyIcons.PLUS, onClick = state.flows::create, style = ControlStyle.Primary, size = ControlSize.Large)
        }
        else -> {
            val visible = state.visible()
            val hero = state.lastPlayedWorld.takeUnless { state.narrowed }
            when {
                visible.isNotEmpty() || hero != null -> WorldGrid(state, hero, visible, onHost, modifier)
                state.narrowed -> NoMatchState("worlds", state.filter.takeIf { it != WorldFilter.All }?.label?.lowercase(Locale.ROOT), state.query, modifier.panel())
                else -> EmptyState("No worlds yet", modifier.panel(), "Create a world to start playing.")
            }
        }
    }
}

@Composable
private fun WorldGrid(state: SingleplayerState, hero: SingleplayerWorld?, worlds: List<SingleplayerWorld>, onHost: (String) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = PageGrid.rowHeight(maxHeight)
        val grid = rememberLazyGridState()
        ScrollContainer(grid, Modifier.fillMaxSize(), scrollbar = false) {
            LazyVerticalGrid(
                GridCells.Fixed(PageGrid.COLUMNS),
                Modifier.fillMaxSize(),
                grid,
                horizontalArrangement = Arrangement.spacedBy(PageGrid.Gap),
                verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            ) {
                if (hero != null) {
                    item(key = LastPlayedKey) {
                        WorldCard(state, hero, onHost, Modifier.fillMaxWidth().height(rowHeight), lastPlayed = true)
                    }
                }
                val (current, other) = worlds.partition { !it.otherVersion }
                if (other.isNotEmpty() && current.isNotEmpty()) {
                    item(key = "section-current", span = { GridItemSpan(maxLineSpan) }) { WorldSectionHeader("Playable on this version") }
                }
                items(current, key = { it.id }) { world ->
                    WorldCard(state, world, onHost, Modifier.fillMaxWidth().height(rowHeight))
                }
                if (other.isNotEmpty()) {
                    item(key = "section-other", span = { GridItemSpan(maxLineSpan) }) { WorldSectionHeader("Other versions") }
                }
                items(other, key = { it.id }) { world ->
                    WorldCard(state, world, onHost, Modifier.fillMaxWidth().height(rowHeight))
                }
            }
        }
    }
}

@Composable
private fun WorldSectionHeader(label: String) {
    MenuText(label, 13.sp, color = SocialTextSecondary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
}
