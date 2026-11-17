@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.packs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuHeading
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

@Composable
internal fun PackToolbar(session: PackSession, query: String, onQuery: (String) -> Unit, counts: Map<PackFilter, Int>) {
    val theme = OneConfigTheme.current
    val online by produceState(emptyList<PackOnlineAction>(), session, session.revision) {
        value = withContext(Dispatchers.IO) { runCatching { session.onlineActions() }.getOrDefault(emptyList()) }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchField(query, onQuery, Modifier.weight(1f), placeholder = "Search packs, * matches anything", size = ControlSize.Regular)
            Spacer(Modifier.widthIn(min = 2.dp))
            Select(session.sort, PackSort.entries, { session.sort = it }, Modifier.widthIn(min = 150.dp, max = 170.dp), label = { it.label }, size = ControlSize.Regular)
            IconButton("folder", "Open pack folder", session::openFolder)
            online.firstOrNull()?.let { action ->
                TextButton("Get packs", action.icon, { session.runOnline(action) }, contentDescription = action.label)
            }
            PackOverflow(session, online.drop(1))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            PackFilter.entries.filter(session::offers).forEach { filter ->
                val count = counts[filter] ?: 0
                val label = if (filter == PackFilter.All || count == 0) filter.label else "${filter.label} $count"
                Chip(label, session.filter == filter, { session.filter = filter }, size = ControlSize.Compact)
            }
            Spacer(Modifier.weight(1f))
            if (session.busy) Text("Working...", color = theme.mutedText, style = theme.typography.small, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun PackOverflow(session: PackSession, extra: List<PackOnlineAction>) {
    var open by remember { mutableStateOf(false) }
    val entries = if (!open) emptyList() else buildList<MenuEntry> {
        add(MenuAction("Refresh packs", session::refresh, icon = "refresh"))
        if (session.resources) add(MenuAction("Profiles", {}, icon = "profiles", children = profileEntries(session)))
        extra.forEach { action -> add(MenuAction(action.label, { session.runOnline(action) }, icon = action.icon)) }
        if (session.canOpenPackedPacks()) add(MenuAction("Pack options in Packed Packs", session::openPackedPacks, icon = "settings"))
        if (session.foldersEnabled) {
            add(MenuSeparator)
            add(MenuAction("New folder", { session.dialog = PackDialog.NewFolder(session.folder, emptyList()) }, icon = "plus", enabled = PackFolders.canAddFolder(session.folder), disabledReason = PackFolders.TOO_DEEP))
            add(MenuAction("Show every pack in one list", { session.flat = !session.flat }, checked = session.flat))
        }
        add(MenuSeparator)
        add(MenuAction("Clear selection", session::clearPicks, enabled = session.picked.isNotEmpty()))
    }
    Box {
        IconButton("dots-vertical", "More actions", { open = !open }, active = open, tooltip = if (open) null else "More actions")
        DropdownMenu(open, { open = false }, entries, placement = Placement.BelowEnd)
    }
}

private fun profileEntries(session: PackSession): List<MenuEntry> {
    return buildList {
        val profiles = session.profiles()
        val own = profiles.filter { !it.external }
        val external = profiles.filter { it.external }
        if (profiles.isEmpty()) add(MenuHeading("No profiles yet"))
        own.forEach { profile -> add(MenuAction(profile.name, { session.applyProfile(profile) }, detail = packCount(profile.packs.size))) }
        if (external.isNotEmpty()) {
            add(MenuHeading("From Packed Packs"))
            external.forEach { profile -> add(MenuAction(profile.name, { session.applyProfile(profile) }, detail = packCount(profile.packs.size))) }
        }
        add(MenuSeparator)
        add(MenuAction("Save current packs as a profile", { session.dialog = PackDialog.SaveProfile }, icon = "plus"))
        if (own.isNotEmpty()) {
            add(
                MenuAction(
                    "Delete a profile",
                    {},
                    icon = "trash",
                    children = own.map { profile -> MenuAction(profile.name, { session.dialog = PackDialog.DeleteProfile(profile) }) },
                ),
            )
        }
    }
}

internal fun packCount(count: Int): String = if (count == 1) "1 pack" else "$count packs"
