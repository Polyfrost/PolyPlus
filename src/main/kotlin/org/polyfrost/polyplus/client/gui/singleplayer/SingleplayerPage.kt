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
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.utils.ClientPlatform

private const val ROW_GAP = 12
private const val COLUMNS = 3
private const val PEEK = 30
private const val MIN_CARD_HEIGHT = 280

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
    Row(
        Modifier.fillMaxSize().padding(start = LobbyLayout.Margin, end = LobbyLayout.Margin, top = LobbyLayout.Top),
        horizontalArrangement = Arrangement.spacedBy(LobbyLayout.Gap),
    ) {
        FeaturedWorldSlot(state, Modifier.width(LobbyLayout.ChatWidth).fillMaxHeight().padding(bottom = LobbyLayout.PageBottom))
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(bottom = LobbyLayout.PageBottom),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
        ) {
            WorldsHeader(state, onBack)
            WorldsBody(state, Modifier.weight(1f).fillMaxWidth())
        }
        WorldDetailsPanel(state, onHost, Modifier.width(LobbyLayout.UnitWidth).fillMaxHeight().padding(bottom = LobbyLayout.RightColumnBottom))
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
        WorldFilter.entries.forEach { entry ->
            Chip(entry.label, selected = entry == state.filter) { state.filter = entry }
        }
        SocialButton("Create world", icon = LobbyIcons.PLUS, onClick = state.flows::create)
    }
}

@Composable
private fun WorldsBody(state: SingleplayerState, modifier: Modifier) {
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
                visible.isNotEmpty() -> WorldGrid(state, visible, modifier)
                state.narrowed -> EmptyState("No worlds match that filter", modifier.panel())
                else -> EmptyState("No other worlds", modifier.panel(), "Your last played world is on the left.")
            }
        }
    }
}

@Composable
private fun WorldGrid(state: SingleplayerState, worlds: List<SingleplayerWorld>, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val available = maxHeight.value
        val visibleRows = ((available + ROW_GAP) / (MIN_CARD_HEIGHT + ROW_GAP)).toInt().coerceAtLeast(1)
        val totalRows = (worlds.size + COLUMNS - 1) / COLUMNS
        val reserved = ROW_GAP * (visibleRows - 1) + if (totalRows > visibleRows) ROW_GAP + PEEK else 0
        val cardHeight = ((available - reserved) / visibleRows).dp
        LazyVerticalGrid(
            GridCells.Fixed(COLUMNS),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
            verticalArrangement = Arrangement.spacedBy(ROW_GAP.dp),
        ) {
            items(worlds, key = { it.id }) { world ->
                WorldCard(state, world, Modifier.fillMaxWidth().height(cardHeight))
            }
        }
    }
}
