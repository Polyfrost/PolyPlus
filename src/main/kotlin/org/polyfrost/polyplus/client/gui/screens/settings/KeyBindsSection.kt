@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalLayoutApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.KeyCap
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SegmentedChips
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.cardSurface
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.screens.world.ConfirmDialog
import org.polyfrost.polyplus.client.gui.screens.world.DialogSpec

private val ChipMinWidth = 104.dp
private val ChipHeight = 30.dp
private val RowMinHeight = 56.dp
private const val MAX_QUERY = 64
private const val MOUSE_PREFIX = "key.mouse."

internal enum class KeyBindSearch(val label: String, val placeholder: String) {
    Name("Name", "Search by name"),
    Key("Key", "Search by key"),
    Category("Category", "Search by category"),
    Any("Everything", "Search names, keys and categories"),
}

internal enum class KeyBindView { All, Conflicts, Unbound }

internal enum class KeyBindSort(val label: String) {
    Default("Default order"),
    NameAZ("Name A to Z"),
    NameZA("Name Z to A"),
    KeyAZ("Key A to Z"),
    KeyZA("Key Z to A"),
}

internal class KeyBindsState {
    var selectedKey by mutableStateOf<String?>(null)
    var showFree by mutableStateOf(false)
}

private val Prefixes = listOf(
    "name:" to KeyBindSearch.Name,
    "key:" to KeyBindSearch.Key,
    "category:" to KeyBindSearch.Category,
    "cat:" to KeyBindSearch.Category,
)

private fun matches(row: KeyBindRow, query: String, mode: KeyBindSearch): Boolean {
    var needle = query.trim().lowercase()
    var active = mode
    Prefixes.firstOrNull { needle.startsWith(it.first) }?.let { (prefix, forced) ->
        needle = needle.removePrefix(prefix).trim()
        active = forced
    }
    if (needle.isEmpty()) return true
    fun byKey(): Boolean {
        val key = row.key.lowercase()
        return if (needle.length == 1) key == needle else needle in key
    }
    fun byCategory(): Boolean = needle in row.category.lowercase() || needle in row.owner.lowercase()
    return when (active) {
        KeyBindSearch.Name -> needle in row.name.lowercase()
        KeyBindSearch.Key -> byKey()
        KeyBindSearch.Category -> byCategory()
        KeyBindSearch.Any -> needle in row.name.lowercase() || byKey() || byCategory()
    }
}

private fun sorted(rows: List<KeyBindRow>, sort: KeyBindSort): List<KeyBindRow> = when (sort) {
    KeyBindSort.Default -> rows
    KeyBindSort.NameAZ -> rows.sortedBy { it.name.lowercase() }
    KeyBindSort.NameZA -> rows.sortedByDescending { it.name.lowercase() }
    KeyBindSort.KeyAZ -> rows.sortedWith(compareBy<KeyBindRow>({ it.unbound }, { it.key.lowercase() }, { it.name.lowercase() }))
    KeyBindSort.KeyZA -> rows.sortedWith(compareBy<KeyBindRow>({ it.unbound }).thenByDescending { it.key.lowercase() }.thenByDescending { it.name.lowercase() })
}

@Composable
private fun rememberKeyBindRows(refresh: Int): List<KeyBindRow> {
    val revision = KeyBindCapture.revision
    return remember(refresh, revision) { KeyBindsPlatform.rows() }
}

@Composable
internal fun KeyBindsKeymap(state: KeyBindsState, refresh: Int) {
    if (!KeyBindsPlatform.keymap) return
    val rows = rememberKeyBindRows(refresh)
    Keymap(rows, state.selectedKey, { state.selectedKey = it }, state.showFree, { state.showFree = it })
}

@Composable
internal fun KeyBindsSection(state: KeyBindsState, refresh: Int) {
    var query by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(KeyBindSearch.Name) }
    var view by remember { mutableStateOf(KeyBindView.All) }
    var sort by remember { mutableStateOf(KeyBindSort.Default) }
    var category by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val selectedKey = state.selectedKey
    val target = KeyBindCapture.target
    val revision = KeyBindCapture.revision
    val rows = rememberKeyBindRows(refresh)
    val changed = remember(refresh, revision) { KeyBindsPlatform.anyChanged() }
    DisposableEffect(Unit) { onDispose { KeyBindsPlatform.stop() } }
    val categories = remember(rows) { rows.map { it.category }.distinct() }
    val conflictCount = rows.count { it.conflicts.isNotEmpty() }
    val unboundCount = rows.count { it.unbound }
    val filtered = rows.filter { row ->
        (selectedKey == null || (!row.unbound && row.keyId == selectedKey)) &&
            (category == null || row.category == category) &&
            when (view) {
                KeyBindView.All -> true
                KeyBindView.Conflicts -> row.conflicts.isNotEmpty()
                KeyBindView.Unbound -> row.unbound
            } &&
            matches(row, query, mode)
    }
    val shown = sorted(filtered, sort)
    val flat = sort != KeyBindSort.Default
    val theme = OneConfigTheme.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader(KeyBindsPlatform.text("controls.keybinds.title"), Modifier.padding(start = 4.dp).weight(1f), contentPadding = PaddingValues(0.dp))
            SearchField(
                query,
                { query = it.take(MAX_QUERY) },
                Modifier.weight(1f),
                placeholder = mode.placeholder,
                size = ControlSize.Regular,
            )
            Select(
                selected = mode,
                options = KeyBindSearch.entries,
                onSelect = { mode = it },
                modifier = Modifier.width(140.dp),
                label = { it.label },
                size = ControlSize.Regular,
                contentDescription = "Search by",
            )
        }
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SegmentedChips(
                KeyBindView.entries,
                view,
                { view = it },
                Modifier.align(Alignment.CenterVertically),
                label = {
                    when (it) {
                        KeyBindView.All -> "All"
                        KeyBindView.Conflicts -> "Clashing ($conflictCount)"
                        KeyBindView.Unbound -> "Not bound ($unboundCount)"
                    }
                },
            )
            Select(
                selected = category,
                options = listOf<String?>(null) + categories,
                onSelect = { category = it },
                modifier = Modifier.width(190.dp),
                label = { it ?: "All categories" },
                contentDescription = "Category",
            )
            Select(
                selected = sort,
                options = KeyBindSort.entries,
                onSelect = { sort = it },
                modifier = Modifier.width(160.dp),
                label = { it.label },
                contentDescription = "Sort",
            )
            TextButton(KeyBindsPlatform.text("controls.resetAll"), { confirmReset = true }, enabled = changed)
        }
        selectedKey?.let { keyId ->
            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Showing key binds on", color = theme.textColorSecondary, style = theme.typography.small, maxLines = 1)
                KeyCap(keymapLabel(keyId).ifEmpty { "Space" })
                TextButton("Show all", { state.selectedKey = null }, style = ControlStyle.Ghost)
            }
        }
        if (shown.isEmpty()) {
            val free = selectedKey != null && rows.none { !it.unbound && it.keyId == selectedKey }
            if (free) {
                EmptyState(
                    "Nothing uses ${keymapLabel(selectedKey!!)}",
                    Modifier.fillMaxWidth().height(160.dp),
                    text = "Click a key bind's button, then press this key to use it.",
                )
            } else {
                EmptyState("No key binds match", Modifier.fillMaxWidth().height(160.dp), text = "Try a different search or filter.")
            }
        } else if (flat) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { row -> key(row.id) { KeyBindCard(row, row.id == target) } }
            }
        } else {
            shown.groupBy { it.category }.forEach { (title, groupRows) ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title, Modifier.padding(start = 4.dp, top = 4.dp), contentPadding = PaddingValues(0.dp))
                    groupRows.forEach { row -> key(row.id) { KeyBindCard(row, row.id == target) } }
                }
            }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            DialogSpec(
                title = "Reset all key binds?",
                message = "Every key bind goes back to its default key. This can't be undone.",
                confirmLabel = KeyBindsPlatform.text("controls.resetAll"),
                dismissLabel = "Cancel",
                danger = true,
                onResult = {},
            ),
        ) { confirmed ->
            confirmReset = false
            if (confirmed) KeyBindsPlatform.resetAll()
        }
    }
}

@Composable
private fun KeyBindCard(row: KeyBindRow, capturing: Boolean) {
    val theme = OneConfigTheme.current
    val conflict = row.conflicts.isNotEmpty() && !capturing
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .hoverable(source)
            .cardSurface()
            .heightIn(min = RowMinHeight)
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.name, color = theme.textColor, style = theme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(
                "${row.owner} / ${row.category}",
                color = theme.textColorSecondary,
                style = theme.typography.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            if (conflict) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon("alert-circle", null, Modifier.size(12.dp), tint = theme.error)
                    Text(
                        "Clashes with " + row.conflicts.joinToString(", "),
                        color = theme.error,
                        style = theme.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
        }
        val resetLabel = KeyBindsPlatform.text("controls.reset")
        Row(Modifier.alpha(if (row.isDefault) 0f else 1f)) {
            IconButton(
                "undo",
                "$resetLabel ${row.name}",
                { KeyBindsPlatform.reset(row.id) },
                enabled = !row.isDefault,
                size = ControlSize.Compact,
                restTint = theme.textColorSecondary,
                tooltip = resetLabel,
            )
        }
        Row(Modifier.alpha(if (!row.unbound && (hovered || capturing)) 1f else 0f)) {
            IconButton(
                "trash",
                "Unbind " + row.name,
                { KeyBindsPlatform.clear(row.id) },
                enabled = !row.unbound,
                size = ControlSize.Compact,
                restTint = theme.textColorSecondary,
                tooltip = "Unbind",
            )
        }
        val hint = when {
            capturing -> "Press a key or mouse button. Escape unbinds."
            conflict -> row.conflictText
            else -> null
        }
        TooltipBox(hint, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
            KeyChip(row, capturing, conflict)
        }
    }
}

@Composable
private fun KeyChip(row: KeyBindRow, capturing: Boolean, conflict: Boolean) {
    val theme = OneConfigTheme.current
    val accent = OneConfigTheme.accent
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val shape = theme.shapes.field
    val fill by Motion.animateColorAsState(
        when {
            capturing -> accent.copy(alpha = 0.2f)
            conflict -> theme.error.copy(alpha = 0.12f)
            else -> theme.componentBackground
        },
    )
    val border by Motion.animateColorAsState(
        when {
            capturing -> accent
            conflict -> theme.error
            hovered -> theme.textColor.copy(alpha = 0.25f)
            else -> theme.borderColor
        },
    )
    val content by Motion.animateColorAsState(
        when {
            capturing -> accent
            conflict -> theme.error
            row.unbound -> theme.textColorSecondary
            else -> theme.textColor
        },
    )
    val label = when {
        capturing -> "Press a key"
        row.unbound -> "Not bound"
        else -> row.key
    }
    Row(
        Modifier
            .widthIn(min = ChipMinWidth)
            .height(ChipHeight)
            .background(fill, shape)
            .border(1.dp, border, shape)
            .oneConfigClickable(
                row.name,
                { if (capturing) KeyBindsPlatform.stop() else KeyBindsPlatform.start(row.id) },
                shape = shape,
                indication = false,
                interactionSource = source,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (row.keyId.startsWith(MOUSE_PREFIX) && !row.unbound && !capturing) {
            MouseGlyph(content)
        } else {
            Icon("keyboard", null, Modifier.size(16.dp), tint = content)
        }
        Text(label, color = content, style = theme.typography.smallMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun MouseGlyph(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = 1.4.dp.toPx()
        val w = size.width * 0.56f
        val h = size.height * 0.84f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(w / 2f), style = Stroke(stroke))
        drawLine(color, Offset(size.width / 2f, top + h * 0.14f), Offset(size.width / 2f, top + h * 0.36f), stroke)
    }
}
