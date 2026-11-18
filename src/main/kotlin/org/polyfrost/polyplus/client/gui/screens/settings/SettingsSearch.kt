@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.ThemedIcon

private const val RESULT_LIMIT = 80
private const val PATH_SEPARATOR = " › "
private val ResultMinHeight = 48.dp

internal class SettingsSearchItem(
    val label: String,
    val detail: String? = null,
    val section: String? = null,
    val focus: String? = null,
    val query: String? = null,
)

internal fun List<SettingSection>.searchItems(): List<SettingsSearchItem> = flatMap { section ->
    section.rows.map { row -> SettingsSearchItem(SettingsLabels.label(row.key, row.label), row.hint, section.title, row.key) }
}

internal class SettingsFocus {
    var key by mutableStateOf<String?>(null)
    var query by mutableStateOf<String?>(null)

    fun take(prefix: String): String? {
        if (key?.startsWith(prefix) != true) return null
        val taken = query
        key = null
        query = null
        return taken
    }
}

internal val LocalSettingsFocus = staticCompositionLocalOf { SettingsFocus() }

internal class SettingsHit(val node: SettingsNode, val tab: SettingsTab, val item: SettingsSearchItem?) {
    val label: String get() = item?.label ?: if (node.tabs.size > 1) tab.label else node.entry.label

    val path: String
        get() {
            val page = if (node.tabs.size > 1) node.entry.label + PATH_SEPARATOR + tab.label else node.entry.label
            val section = item?.section?.takeIf { it.isNotBlank() && !it.equals(label, ignoreCase = true) }
            return when {
                item == null -> if (node.tabs.size > 1) node.entry.label else node.entry.group.title
                section != null -> page + PATH_SEPARATOR + section
                else -> page
            }
        }

    val icon: String get() = node.entry.icon
}

internal object SettingsSearch {
    fun index(nodes: List<SettingsNode>, parent: () -> Screen?): List<SettingsHit> = nodes.flatMap { node ->
        node.tabs.flatMap { tab -> listOf(SettingsHit(node, tab, null)) + items(tab, parent).map { SettingsHit(node, tab, it) } }
    }

    fun filter(index: List<SettingsHit>, query: String): List<SettingsHit> {
        val tokens = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        return index
            .mapNotNull { hit -> score(hit, tokens)?.let { hit to it } }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { listOf(it.node.id, it.tab.id, it.path, it.label, it.item?.detail, it.item?.focus) }
            .take(RESULT_LIMIT)
    }

    private fun score(hit: SettingsHit, tokens: List<String>): Int? {
        val label = hit.label.lowercase()
        val path = hit.path.lowercase()
        val detail = hit.item?.detail.orEmpty().lowercase()
        var total = if (hit.item == null) 1 else 0
        for (token in tokens) {
            total += when {
                label.startsWith(token) -> 4
                token in label -> 3
                token in path -> 1
                token in detail -> 0
                else -> return null
            }
        }
        return total
    }

    private fun items(tab: SettingsTab, parent: () -> Screen?): List<SettingsSearchItem> {
        val entry = tab.entry
        if (entry.external != null) return emptyList()
        val provider = tab.provider
        val category = entry.category
        return runCatching {
            when {
                provider != null -> provider.search(entry, parent)
                category == SettingsCategory.Video -> if (SettingsPlatform.sodium) emptyList() else SettingsPlatform.videoSections().searchItems()
                category != null -> SettingsPages.provider(category)?.search(category, parent).orEmpty()
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
    }
}

@Composable
internal fun SettingsSearchResults(hits: List<SettingsHit>, open: (SettingsHit) -> Unit, modifier: Modifier = Modifier) {
    if (hits.isEmpty()) {
        EmptyState("Nothing matches", modifier.fillMaxSize(), icon = "search", text = "Try a different word.")
        return
    }
    ScrollContainer(rememberScrollState(), modifier.fillMaxSize(), scrollbar = true, fadingEdges = true) {
        Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            hits.forEachIndexed { index, hit -> key(index, hit.path, hit.label) { SearchResultRow(hit) { open(hit) } } }
        }
    }
}

@Composable
private fun SearchResultRow(hit: SettingsHit, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ResultMinHeight)
            .selectableRow(false, onClick, hit.label, theme.sideBarNavigationEntryShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemedIcon(hit.icon, theme.mutedText, 16.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(hit.label, color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(hit.path, color = theme.mutedText, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}
