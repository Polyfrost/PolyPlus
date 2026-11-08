@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.singleplayer

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ContextMenuHost
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.NoMatchState
import org.polyfrost.polyplus.client.gui.lobbyshell.PageGrid
import org.polyfrost.polyplus.client.gui.lobbyshell.PageHeader
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Locale

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
    ContextMenuHost {
        Row(
            Modifier.fillMaxSize().padding(start = LobbyLayout.Margin, end = LobbyLayout.Margin, top = LobbyLayout.Top),
            horizontalArrangement = Arrangement.spacedBy(LobbyLayout.Gap),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(bottom = LobbyLayout.PageBottom),
                verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            ) {
                WorldsHeader(state, onBack)
                if (state.filter == WorldFilter.All) LastPlayedWorldStrip(state, onHost)
                WorldsBody(state, onHost, Modifier.weight(1f).fillMaxWidth())
            }
            WorldDetailsPanel(state, onHost, Modifier.width(LobbyLayout.UnitWidth).fillMaxHeight().padding(bottom = LobbyLayout.RightColumnBottom))
        }
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
            { TabRow(WorldFilter.entries.map { TabItem(it, it.label) }, state.filter, Modifier) { state.filter = it } }
        } else {
            null
        },
    ) {
        TextButton("Create world", themedIcon(LobbyIcons.PLUS), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Secondary, true, state.flows::create)
    }
}

@Composable
private fun WorldsBody(state: SingleplayerState, onHost: (String) -> Unit, modifier: Modifier) {
    when {
        state.phase == WorldListPhase.Failed -> EmptyState("Unable to load worlds", modifier.panel(), state.failure.orEmpty()) {
            TextButton("Try again", themedIcon(LobbyIcons.REFRESH), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Primary, true, state::reload)
        }
        state.phase == WorldListPhase.Loading -> EmptyState("Loading worlds", modifier.panel())
        state.worlds.isEmpty() -> EmptyState("No worlds yet", modifier.panel(), "Create a world to start playing.") {
            TextButton("Create world", themedIcon(LobbyIcons.PLUS), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Primary, true, state.flows::create)
        }
        else -> {
            val visible = state.visible()
            when {
                visible.isNotEmpty() -> WorldGrid(state, visible, onHost, modifier)
                state.narrowed -> NoMatchState("worlds", state.filter.takeIf { it != WorldFilter.All }?.label?.lowercase(Locale.ROOT), state.query, modifier.panel())
                else -> EmptyState("No other worlds", modifier.panel(), "Your last played world is above.")
            }
        }
    }
}

@Composable
private fun WorldGrid(state: SingleplayerState, worlds: List<SingleplayerWorld>, onHost: (String) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = PageGrid.rowHeight(maxHeight)
        LazyVerticalGrid(
            GridCells.Fixed(PageGrid.COLUMNS),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
        ) {
            items(worlds, key = { it.id }) { world ->
                WorldCard(state, world, onHost, Modifier.fillMaxWidth().height(rowHeight))
            }
        }
    }
}
