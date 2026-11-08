@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.replays

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.NoMatchState
import org.polyfrost.polyplus.client.gui.lobbyshell.PageGrid
import org.polyfrost.polyplus.client.gui.lobbyshell.PageHeader
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import org.polyfrost.polyplus.compat.replay.ReplayProvider

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
                verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
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
    PageHeader(
        "Replays",
        view.query,
        { view.query = it },
        "Search replays",
        onBack,
        filters = if (providers.size > 1) {
            { TabRow(listOf(TabItem<String?>(null, "All")) + providers.map { TabItem<String?>(it.id, it.name) }, view.providerId, Modifier) { view.providerId = it } }
        } else {
            null
        },
    ) {
        if (focus != null) {
            TextButton("Open folder", themedIcon(FOLDER_ICON), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Secondary, true) { focus.openFolderSafely() }
            TextButton("Open ${focus.name}", themedIcon(VIDEO_ICON), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Secondary, true) { focus.openBrowserSafely() }
        } else {
            providers.forEach { provider ->
                TextButton("Open ${provider.name}", themedIcon(VIDEO_ICON), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Secondary, true) { provider.openBrowserSafely() }
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
        entries.isEmpty() && ReplayLibrary.failed -> EmptyState("Couldn't load replays", modifier.panel(), "Your replay folder couldn't be read.") {
            TextButton("Try again", null, Modifier.height(BUTTON_HEIGHT), ButtonStyle.Primary, true) { ReplayLibrary.reload() }
        }
        entries.isEmpty() -> EmptyState("No replays yet", modifier.panel(), "Start recording from the pause menu.") {
            view.provider(ReplayLibrary.providers)?.let { provider ->
                TextButton("Open folder", themedIcon(FOLDER_ICON), Modifier.height(BUTTON_HEIGHT), ButtonStyle.Primary, true) { provider.openFolderSafely() }
            }
        }
        else -> {
            val matching = view.matching(entries)
            val visible = if (view.narrowed) matching else matching.filter { it !== latest }
            when {
                visible.isNotEmpty() -> ReplayGrid(visible, selected, onSelect, onWatch, modifier)
                view.narrowed -> NoMatchState("replays", ReplayLibrary.providers.firstOrNull { it.id == view.providerId }?.name, view.query, modifier.panel())
                else -> EmptyState("No other replays", modifier.panel(), "Your latest replay is above.")
            }
        }
    }
}

@Composable
private fun ReplayGrid(entries: List<ReplayEntry>, selected: ReplayEntry?, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = PageGrid.rowHeight(maxHeight)
        LazyVerticalGrid(
            GridCells.Fixed(PageGrid.COLUMNS),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
        ) {
            items(entries, key = { "${it.provider.id}:${it.path}" }) { entry ->
                ReplayCard(entry, entry.path == selected?.path, onSelect, onWatch, Modifier.fillMaxWidth().height(rowHeight))
            }
        }
    }
}
