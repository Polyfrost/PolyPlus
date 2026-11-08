@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.singleplayer

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.polyplus.client.gui.ContextMenuHost
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Locale

private val CardGap = 12.dp
private const val COLUMNS = 3
private const val ROWS = 2

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
                verticalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                WorldsHeader(state, onBack)
                if (state.phase == WorldListPhase.Loading || state.worlds.isNotEmpty()) WorldFilterTabs(state)
                if (state.filter == WorldFilter.All) LastPlayedWorldStrip(state, onHost)
                WorldsBody(state, onHost, Modifier.weight(1f).fillMaxWidth())
            }
            WorldDetailsPanel(state, onHost, Modifier.width(LobbyLayout.UnitWidth).fillMaxHeight().padding(bottom = LobbyLayout.RightColumnBottom))
        }
    }
}

@Composable
private fun WorldsHeader(state: SingleplayerState, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        PageTitle("Worlds", Modifier.padding(end = 2.dp))
        SocialTextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier.weight(1f),
            placeholder = "Search worlds",
            leadingIcon = LobbyIcons.SEARCH,
        )
        SocialButton("Create world", icon = LobbyIcons.PLUS, onClick = state.flows::create)
    }
}

@Composable
private fun WorldFilterTabs(state: SingleplayerState) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorldFilter.entries.forEach { entry ->
            SocialButton(entry.label, modifier = Modifier.weight(1f), filled = entry == state.filter) { state.filter = entry }
        }
    }
}

private fun noMatchTitle(state: SingleplayerState): String {
    val mode = if (state.filter == WorldFilter.All) "" else "${state.filter.label.lowercase(Locale.ROOT)} "
    return if (state.query.isBlank()) "No ${mode}worlds" else "No ${mode}worlds match that search"
}

private fun noMatchText(state: SingleplayerState): String =
    if (state.query.isBlank()) "Pick another tab to see more worlds." else "Try a different name."

@Composable
private fun WorldsBody(state: SingleplayerState, onHost: (String) -> Unit, modifier: Modifier) {
    when {
        state.phase == WorldListPhase.Failed -> EmptyState("Unable to load worlds", modifier.panel(), state.failure.orEmpty()) {
            SocialButton("Try again", icon = LobbyIcons.REFRESH, filled = true, onClick = state::reload)
        }
        state.phase == WorldListPhase.Loading -> EmptyState("Loading worlds", modifier.panel())
        state.worlds.isEmpty() -> EmptyState("No worlds yet", modifier.panel(), "Create a world to start playing.") {
            SocialButton("Create world", icon = LobbyIcons.PLUS, filled = true, onClick = state.flows::create)
        }
        else -> {
            val visible = state.visible()
            when {
                visible.isNotEmpty() -> WorldGrid(state, visible, onHost, modifier)
                state.narrowed -> EmptyState(noMatchTitle(state), modifier.panel(), noMatchText(state))
                else -> EmptyState("No other worlds", modifier.panel(), "Your last played world is above.")
            }
        }
    }
}

@Composable
private fun WorldGrid(state: SingleplayerState, worlds: List<SingleplayerWorld>, onHost: (String) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = (maxHeight - CardGap * (ROWS - 1)) / ROWS
        LazyVerticalGrid(
            GridCells.Fixed(COLUMNS),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            items(worlds, key = { it.id }) { world ->
                WorldCard(state, world, onHost, Modifier.fillMaxWidth().height(rowHeight))
            }
        }
    }
}
