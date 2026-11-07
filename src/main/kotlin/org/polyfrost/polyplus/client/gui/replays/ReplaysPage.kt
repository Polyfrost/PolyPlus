@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.replays

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.polyplus.client.gui.ContextMenuHost
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import org.polyfrost.polyplus.compat.replay.ReplayProvider

private val CardGap = 12.dp
private const val COLUMNS = 3
private const val ROWS = 2

private class ReplaysView {
    var query by mutableStateOf("")
    var providerId by mutableStateOf<String?>(null)

    fun provider(providers: List<ReplayProvider>): ReplayProvider? =
        providers.firstOrNull { it.id == providerId } ?: providers.singleOrNull()

    val narrowed: Boolean get() = query.isNotBlank() || providerId != null

    fun matching(entries: List<ReplayEntry>): List<ReplayEntry> {
        val needle = query.trim()
        return entries.filter { entry ->
            (providerId == null || entry.provider.id == providerId) &&
                (needle.isEmpty() || entry.name.contains(needle, true) || entry.world?.contains(needle, true) == true)
        }
    }
}

@Composable
internal fun ReplaysPage(selected: ReplayEntry?, onBack: () -> Unit, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit) {
    if (!ReplayLibrary.enabled) return
    val view = remember { ReplaysView() }
    LaunchedEffect(Unit) { ReplayLibrary.reload() }
    ContextMenuHost {
        Row(
            Modifier.fillMaxSize().padding(start = LobbyLayout.Margin, end = LobbyLayout.Margin, top = LobbyLayout.Top),
            horizontalArrangement = Arrangement.spacedBy(LobbyLayout.Gap),
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(bottom = LobbyLayout.PageBottom),
                verticalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                ReplaysHeader(view, onBack)
                val latest = ReplayLibrary.entries.firstOrNull()
                if (latest != null && !view.narrowed) LatestReplayStrip(latest, latest.path == selected?.path, onSelect, onWatch)
                ReplaysBody(view, latest, selected, onSelect, onWatch, Modifier.weight(1f).fillMaxWidth())
            }
            ReplayDetailsPanel(selected, onWatch, Modifier.width(LobbyLayout.UnitWidth).fillMaxHeight().padding(bottom = LobbyLayout.RightColumnBottom))
        }
    }
}

@Composable
private fun ReplaysHeader(view: ReplaysView, onBack: () -> Unit) {
    val providers = ReplayLibrary.providers
    val focus = view.provider(providers)
    Row(
        Modifier.fillMaxWidth().height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        PageTitle("Replays", Modifier.padding(end = 2.dp))
        SocialTextField(
            value = view.query,
            onValueChange = { view.query = it },
            modifier = Modifier.weight(1f),
            placeholder = "Search replays",
            leadingIcon = LobbyIcons.SEARCH,
        )
        if (providers.size > 1) {
            Chip("All", selected = view.providerId == null) { view.providerId = null }
            providers.forEach { provider ->
                Chip(provider.name, selected = view.providerId == provider.id) { view.providerId = provider.id }
            }
        }
        if (focus != null) {
            SocialButton("Open folder", icon = FOLDER_ICON) { focus.openFolderSafely() }
            SocialButton("Open ${focus.name}", icon = VIDEO_ICON) { focus.openBrowserSafely() }
        } else {
            providers.forEach { provider ->
                SocialButton("Open ${provider.name}", icon = VIDEO_ICON) { provider.openBrowserSafely() }
            }
        }
    }
}

@Composable
private fun ReplaysBody(
    view: ReplaysView,
    latest: ReplayEntry?,
    selected: ReplayEntry?,
    onSelect: (ReplayEntry) -> Unit,
    onWatch: (ReplayEntry) -> Unit,
    modifier: Modifier,
) {
    val entries = ReplayLibrary.entries
    when {
        entries.isEmpty() && (ReplayLibrary.loading || !ReplayLibrary.loaded) -> EmptyState("Loading replays", modifier.panel())
        entries.isEmpty() -> EmptyState("No replays yet", modifier.panel(), "Start recording from the pause menu.") {
            view.provider(ReplayLibrary.providers)?.let { provider ->
                SocialButton("Open folder", icon = FOLDER_ICON, filled = true) { provider.openFolderSafely() }
            }
        }
        else -> {
            val matching = view.matching(entries)
            val visible = if (view.narrowed) matching else matching.filter { it !== latest }
            when {
                visible.isNotEmpty() -> ReplayGrid(visible, selected, onSelect, onWatch, modifier)
                view.narrowed -> EmptyState("No replays match that search", modifier.panel())
                else -> EmptyState("No other replays", modifier.panel(), "Your latest replay is above.")
            }
        }
    }
}

@Composable
private fun ReplayGrid(entries: List<ReplayEntry>, selected: ReplayEntry?, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = (maxHeight - CardGap * (ROWS - 1)) / ROWS
        LazyVerticalGrid(
            GridCells.Fixed(COLUMNS),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            items(entries, key = { "${it.provider.id}:${it.path}" }) { entry ->
                ReplayCard(entry, entry.path == selected?.path, onSelect, onWatch, Modifier.fillMaxWidth().height(rowHeight))
            }
        }
    }
}
