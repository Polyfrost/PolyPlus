@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.animation.Crossfade
import org.polyfrost.polyplus.client.gui.KitDangerColor
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuHeading
import org.polyfrost.oneconfig.api.ui.v1.components.MenuRadioGroup
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.lobby.LobbyPacks
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene
import androidx.compose.ui.text.style.TextOverflow

private val ToolHeight = 38.dp
private val TriggerWidth = 200.dp
private const val MAX_QUERY = 64

@Composable
internal fun ScenesSection(state: SceneManagerState) {
    val party by PartyRepository.party.collectAsState()
    val member = PartyScene.isMember(party)
    val canPick = !member || PartyScene.keepOwnBackdrop
    val revision = LobbyPacks.revision
    val captures = LobbySceneStore.captures
    val query = SceneQuery(state.query, state.filter, state.sort)
    val items = remember(captures, state.query, state.filter, state.sort, revision) { sceneItems(captures, query) }
    LaunchedEffect(captures) {
        val live = captures.mapTo(HashSet()) { it.id }
        if (state.selected.any { it !in live }) state.selected = state.selected.filterTo(HashSet()) { it in live }
        val open = state.expanded
        if (open != null && open != AUTOMATIC_KEY && open !in live && LobbySceneStore.bundledScenes.none { it.id == open }) state.expanded = null
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (member) PartyBackdrop(!canPick)
        SceneTools(state, captures)
        SceneList(state, items, canPick, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun SceneTools(state: SceneManagerState, captures: List<LobbySceneEntry>) {
    Crossfade(state.selecting, Modifier.fillMaxWidth(), Motion.finiteSpec(tween(SCENE_FADE_MILLIS)), "Scene tools") { selecting ->
        Row(Modifier.fillMaxWidth().height(ToolHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selecting) SelectionBar(state, captures) else SearchBar(state, captures)
        }
    }
}

@Composable
private fun RowScope.SearchBar(state: SceneManagerState, captures: List<LobbySceneEntry>) {
    SearchField(
        query = state.query,
        onQueryChange = { state.query = it.take(MAX_QUERY) },
        modifier = Modifier.weight(1f),
        placeholder = "Search scenes",
        clearable = false,
        size = ControlSize.Large,
    )
    SceneViewMenu(state, captures)
    TextButton(
        text = "Select",
        onClick = {
            state.expanded = null
            state.selecting = true
        },
        size = ControlSize.Large,
        style = ControlStyle.Secondary,
        enabled = captures.isNotEmpty(),
    )
}

@Composable
private fun RowScope.SelectionBar(state: SceneManagerState, captures: List<LobbySceneEntry>) {
    val picked = captures.filter { it.id in state.selected }
    val favourites = picked.isNotEmpty() && picked.all { it.favourite }
    Text(if (picked.isEmpty()) "None selected" else "${picked.size} selected", Modifier.weight(1f), style = OneConfigTheme.current.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    TextButton(
        text = if (favourites) "Unfavourite" else "Favourite",
        onClick = { LobbySceneStore.setFavourite(picked, !favourites) },
        size = ControlSize.Large,
        style = ControlStyle.Secondary,
        enabled = picked.isNotEmpty(),
    )
    TextButton(
        text = "Delete",
        onClick = {
            state.pending = SceneDeletion("Delete ${plural(picked.size, "scene")}?", "They will be deleted from your computer. This can't be undone.", picked)
        },
        size = ControlSize.Large,
        style = ControlStyle.DangerFilled,
        dangerColor = KitDangerColor,
        enabled = picked.isNotEmpty(),
    )
    TextButton(text = "Cancel", onClick = state::leaveSelect, size = ControlSize.Large, style = ControlStyle.Secondary)
}

@Composable
private fun SceneViewMenu(state: SceneManagerState, captures: List<LobbySceneEntry>) {
    var open by remember { mutableStateOf(false) }
    val theme = OneConfigTheme.current
    val value = listOfNotNull(state.filter.label.takeIf { state.filter != SceneFilter.ALL }, state.sort.label).joinToString(", ")
    val chevron by Motion.animateFloatAsState(if (open) 180f else 0f, tween(SCENE_FADE_MILLIS))
    val filters = SceneFilter.entries.filter { it == SceneFilter.ALL || it == state.filter || captures.any(it::matches) }
    val entries: List<MenuEntry> = listOf(
        MenuHeading("Show"),
        MenuRadioGroup(filters, state.filter, { it.label }, { state.filter = it }, closeOnSelect = false),
        MenuHeading("Sort"),
        MenuRadioGroup(SceneSort.entries, state.sort, { it.label }, { state.sort = it }, closeOnSelect = false),
    )
    Box {
        TextButton(
            text = value,
            onClick = { open = !open },
            modifier = Modifier.width(TriggerWidth),
            size = ControlSize.Large,
            contentDescription = "Sort and filter, $value",
            active = open,
            leading = { Text("Sort", Modifier, theme.textColorSecondary, theme.typography.small) },
            trailing = { ThemedIcon(SOCIAL_ASSETS + "chevron-up.svg", theme.textColor, 14.dp, Modifier.rotate(chevron)) },
        )
        DropdownMenu(open, { open = false }, entries, placement = Placement.BelowEnd)
    }
}

@Composable
private fun SceneList(state: SceneManagerState, items: List<SceneItem>, canPick: Boolean, modifier: Modifier) {
    val list = state.list
    Box(modifier) {
        if (items.isEmpty()) {
            EmptyState("Nothing matches", Modifier.fillMaxSize(), "Try another search or filter.")
        } else {
            ScrollContainer(list, Modifier.fillMaxSize(), scrollbar = false, fadingEdges = true) {
                LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.key }) { item -> SceneListItem(state, item, canPick) }
                }
            }
        }
    }
}

@Composable
private fun SceneListItem(state: SceneManagerState, item: SceneItem, canPick: Boolean) {
    when (item) {
        SceneItem.Automatic -> SceneCard(state, null, canPick)
        SceneItem.NoCaptures -> Box(Modifier.padding(horizontal = 4.dp)) { SceneNote("Good spots are saved here after you play your worlds.", OneConfigTheme.current.mutedText) }
        is SceneItem.Header -> SectionHeader(item.title, Modifier.padding(start = 4.dp))
        is SceneItem.Scene -> SceneCard(state, item.entry, canPick)
    }
}
