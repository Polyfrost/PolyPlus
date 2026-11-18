@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSlider
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelToggle
import org.polyfrost.polyplus.client.gui.lobbyshell.Segmented
import org.polyfrost.polyplus.client.gui.lobbyshell.UnderlineTabs
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.screens.world.ConfirmDialog

private val RailWidth = 212.dp
private val RailRowHeight = 36.dp
private val TabGap = 14.dp
private val RowMinHeight = 44.dp
private val SelectWidth = 190.dp
private val InlineSegmentWidth = 150.dp
private const val INLINE_SEGMENT_CHARS = 10
private val TwoColumnWidth = 620.dp
private val SectionGap = 20.dp
private val RowGap = 2.dp
private val ColumnGap = 8.dp
private const val SEGMENT_LIMIT = 5
private const val REFRESH_MILLIS = 500L

@Composable
internal fun SettingsBody(parent: () -> Screen?, modifier: Modifier = Modifier) {
    val nodes = remember { SettingsPages.nodes(builtInEntries(), trailingEntries()) }
    val restored = remember { resolve(nodes, SettingsMemory.restore(parent())) }
    var category by remember { mutableStateOf(restored.category) }
    val tabs = remember { mutableStateMapOf<String, String>().apply { restored.tab?.let { put(restored.category, it) } } }
    val node = nodes.firstOrNull { it.id == category } ?: nodes.firstOrNull { it.entry.external == null } ?: nodes.first()
    val tab = node.tabs.firstOrNull { it.id == tabs[node.id] && it.entry.external == null }
        ?: node.tabs.firstOrNull { it.entry.external == null }
        ?: node.tabs.first()
    val leaving: () -> Screen? = { parent().also { SettingsMemory.leaving(SettingsSelection(node.id, tab.id), it) } }
    val activate = { entry: SettingsEntry ->
        val external = entry.external
        if (external != null) external(leaving()) else category = entry.id
    }
    val pick = { picked: SettingsTab ->
        val external = picked.entry.external
        if (external != null) external(leaving()) else tabs[node.id] = picked.id
    }
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsRail(nodes.map { it.entry }, node.id, activate, Modifier.width(RailWidth).fillMaxHeight())
        Divider(vertical = true)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            key(node.id) {
                CategorySession(node)
                if (node.tabs.size > 1) {
                    val tabScroll = rememberScrollState()
                    ScrollContainer(
                        tabScroll,
                        Modifier.fillMaxWidth().wheelScrollsHorizontally(tabScroll),
                        orientation = Orientation.Horizontal,
                        scrollbar = false,
                        fadingEdges = true,
                        smoothWheel = false,
                    ) {
                        UnderlineTabs(node.tabs.map { it.item() }, tab.id) { id -> node.tabs.firstOrNull { it.id == id }?.let(pick) }
                    }
                    Spacer(Modifier.height(TabGap))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalSettingsOwner provides tab.entry.owner) {
                        key(tab.id) { EntryPage(tab.entry, tab.provider, leaving) }
                    }
                }
            }
        }
    }
}

private fun resolve(nodes: List<SettingsNode>, saved: SettingsSelection): SettingsSelection {
    if (nodes.any { it.id == saved.category }) return saved
    val home = nodes.firstOrNull { node -> node.tabs.any { it.id == saved.category } } ?: return saved
    return SettingsSelection(home.id, saved.category)
}

private fun SettingsTab.item(): TabItem<String> {
    val tooltip = if (entry.external != null) "Opens in ${entry.owner.name}'s own screen." else null
    return TabItem(id, label, tooltip = tooltip)
}

@Composable
private fun CategorySession(node: SettingsNode) {
    DisposableEffect(Unit) {
        val providers = node.providers
        providers.forEach { runCatching { it.opened(node.id) } }
        onDispose { providers.forEach { runCatching { it.closed(node.id) } } }
    }
}

// Compose only scrolls a horizontal row from horizontal wheel input, which most mice never send
private fun Modifier.wheelScrollsHorizontally(state: ScrollState): Modifier = pointerInput(state) {
    // retargeting keeps the distance of a glide that a newer scroll event interrupts
    var target = 0f
    coroutineScope {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Scroll) continue
                val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
                if (abs(delta.y) <= abs(delta.x)) continue
                val forward = delta.y > 0f
                if (if (forward) !state.canScrollForward else !state.canScrollBackward) continue
                event.changes.forEach { it.consume() }
                if (!state.isScrollInProgress || (target > state.value) != forward) target = state.value.toFloat()
                target = (target + delta.y * density).coerceIn(0f, state.maxValue.toFloat())
                launch { if (Motion.reduced) state.scrollTo(target.roundToInt()) else state.animateScrollTo(target.roundToInt()) }
            }
        }
    }
}

@Composable
private fun EntryPage(entry: SettingsEntry, provider: SettingsEntryProvider?, parent: () -> Screen?) {
    val category = entry.category
    when {
        provider != null -> provider.Page(entry, parent)
        category == SettingsCategory.Video -> if (SettingsPlatform.sodium) ExternalVideo { parent()?.let { SettingsPlatform.open(category, it) } } else VideoPage()
        category != null -> SettingsPages.provider(category)?.Page(category, parent)
    }
}

private fun builtInEntries(): List<SettingsEntry> {
    val controlsInPlace = SettingsPages.provider(SettingsCategory.Controls) != null
    return SettingsCategory.entries.filter { it != SettingsCategory.Mods && SettingsPlatform.supports(it) }.map { category ->
        val inPlace = category == SettingsCategory.Video || SettingsPages.provider(category) != null
        val external: ((Screen?) -> Unit)? = if (inPlace) null else { host -> host?.let { SettingsPlatform.open(category, it) } }
        val placement = if (category == SettingsCategory.Mouse && controlsInPlace) {
            SettingsPlacement.TabIn(SettingsCategory.Controls.entryId)
        } else {
            SettingsPlacement.End
        }
        val tabLabel = if (category == SettingsCategory.Controls) "Keyboard" else null
        SettingsEntry(category.entryId, category.label, category.icon, placement = placement, external = external, category = category, tabLabel = tabLabel)
    }
}

private fun trailingEntries(): List<SettingsEntry> = listOf(
    SettingsEntry(
        "oneclient:mods",
        "Mods",
        "dots-grid",
        SettingsOwner.OneClient,
        SettingsGroup.OneClient,
        external = { host -> SettingsMemory.returnOnClose(host) { PolyPlusOneConfigIntegration.openMods() } },
    ),
    SettingsEntry(
        "oneclient:more-from-mods",
        SettingsCategory.Mods.label,
        SettingsCategory.Mods.icon,
        SettingsOwner.OneClient,
        SettingsGroup.OneClient,
        external = { host -> host?.let { SettingsPlatform.open(SettingsCategory.Mods, it) } },
        category = SettingsCategory.Mods,
    ),
)

@Composable
private fun SettingsRail(entries: List<SettingsEntry>, selected: String, activate: (SettingsEntry) -> Unit, modifier: Modifier) {
    ScrollContainer(rememberScrollState(), modifier, fadingEdges = true) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsGroup.entries.forEachIndexed { index, group ->
                val members = entries.filter { it.group == group }
                if (members.isEmpty()) return@forEachIndexed
                RailGroup(group.title, if (index == 0) Modifier else Modifier.padding(top = 14.dp))
                members.forEach { entry ->
                    val external = entry.external != null
                    RailRow(entry.label, entry.icon, active = !external && entry.id == selected, external = external) { activate(entry) }
                }
            }
        }
    }
}

private val SettingsGroup.title: String
    get() = when (this) {
        SettingsGroup.Game -> "Game"
        SettingsGroup.OneClient -> "OneClient"
    }

@Composable
private fun RailGroup(title: String, modifier: Modifier = Modifier) {
    SectionHeader(title, modifier.padding(start = 10.dp, bottom = 4.dp), contentPadding = PaddingValues(0.dp))
}

@Composable
private fun RailRow(label: String, icon: String, active: Boolean, external: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val ink = SelectionDefaults.itemContentColor(active)
    Row(
        Modifier
            .fillMaxWidth()
            .height(RailRowHeight)
            .selectableRow(active, onClick, label, theme.sideBarNavigationEntryShape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemedIcon(icon, ink, 16.dp)
        Text(
            label,
            Modifier.weight(1f),
            color = ink,
            style = theme.typography.body.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        if (external) LobbyIcon("chevron-right", SelectionDefaults.itemSecondaryColor(active, rest = theme.mutedText), 14.dp)
    }
}

@Composable
private fun ExternalVideo(onOpen: () -> Unit) {
    val name = SettingsPlatform.videoModName ?: "Sodium"
    EmptyState(
        "$name handles video settings",
        Modifier.fillMaxSize(),
        icon = SettingsCategory.Video.icon,
        text = "Video settings open in $name's own screen.",
        action = { TextButton("Open video settings", onOpen, style = ControlStyle.Primary, size = ControlSize.Large) },
    )
}

@Composable
private fun VideoPage() {
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MILLIS)
            revision++
        }
    }
    DisposableEffect(Unit) {
        val session = SettingsPlatform.beginVideo()
        onDispose { SettingsPlatform.endVideo(session) }
    }
    val sections = remember(revision) { SettingsPlatform.videoSections() }
    val restart = remember(revision) { SettingsPlatform.restartRequired() }
    val warning = remember(revision) { SettingsPlatform.graphicsWarning() }
    val changed: () -> Unit = { revision++ }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= TwoColumnWidth) 2 else 1
        ScrollContainer(rememberScrollState(), Modifier.fillMaxSize(), fadingEdges = true) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
                VideoHeader(restart)
                sections.forEach { section -> SectionBlock(section, columns, changed) }
            }
        }
    }
    warning?.let { spec ->
        ConfirmDialog(spec) { accepted ->
            spec.onResult(accepted)
            changed()
        }
    }
}

@Composable
private fun VideoHeader(restart: Boolean) {
    SettingsPageHeader(SettingsCategory.Video.label, "Changes apply as you make them.") {
        if (restart) Badge(SettingsPlatform.restartNotice(), tone = BadgeTone.Warning)
    }
}

@Composable
internal fun SectionBlock(section: SettingSection, columns: Int, changed: () -> Unit) {
    if (section.rows.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(section.title, Modifier.padding(start = 4.dp), contentPadding = PaddingValues(0.dp))
        Column(
            Modifier.fillMaxWidth().card(14.dp).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(RowGap),
        ) {
            lines(section.rows, columns).forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ColumnGap), verticalAlignment = Alignment.CenterVertically) {
                    line.forEach { row ->
                        Box(Modifier.weight(1f)) { key(row.key) { SettingControl(row, changed) } }
                    }
                    if (!line.first().wide) repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

internal fun lines(rows: List<SettingRow>, columns: Int): List<List<SettingRow>> {
    val out = mutableListOf<List<SettingRow>>()
    val pending = mutableListOf<SettingRow>()
    fun flush() {
        pending.chunked(columns).forEach { out += it }
        pending.clear()
    }
    rows.forEach { row ->
        if (row.wide) {
            flush()
            out += listOf(row)
        } else {
            pending += row
        }
    }
    flush()
    return out
}

@Composable
internal fun SettingControl(row: SettingRow, changed: () -> Unit) {
    val label = SettingsLabels.label(row.key, row.label)
    TooltipBox(row.hint, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp) {
        when (val kind = row.kind) {
            is SettingKind.Toggle -> PanelToggle(
                label,
                kind.on,
                {
                    kind.set(it)
                    changed()
                },
                Modifier.heightIn(min = RowMinHeight),
                enabled = row.enabled,
            )
            is SettingKind.Choice -> if (row.wide && kind.options.size <= SEGMENT_LIMIT) {
                SegmentRow(row, label, kind, changed)
            } else if (kind.options.size == 2 && kind.options.all { it.length <= INLINE_SEGMENT_CHARS }) {
                InlineSegmentRow(row, label, kind, changed)
            } else {
                ChoiceRow(row, label, kind, changed)
            }
            is SettingKind.Range -> RangeRow(row, label, kind, changed)
        }
    }
}

@Composable
private fun ChoiceRow(row: SettingRow, label: String, kind: SettingKind.Choice, changed: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Select(
            kind.selected,
            kind.options.indices.toList(),
            {
                kind.select(it)
                changed()
            },
            Modifier.widthIn(max = SelectWidth),
            label = { kind.options.getOrElse(it) { "" } },
            enabled = row.enabled && kind.options.size > 1,
            contentDescription = label,
        )
    }
}

@Composable
private fun InlineSegmentRow(row: SettingRow, label: String, kind: SettingKind.Choice, changed: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Segmented(
            kind.options.indices.toList(),
            kind.selected,
            {
                kind.select(it)
                changed()
            },
            Modifier.width(InlineSegmentWidth),
            label = { kind.options.getOrElse(it) { "" } },
            enabled = row.enabled,
        )
    }
}

@Composable
private fun SegmentRow(row: SettingRow, label: String, kind: SettingKind.Choice, changed: () -> Unit) {
    val theme = OneConfigTheme.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Segmented(
            kind.options.indices.toList(),
            kind.selected,
            {
                kind.select(it)
                changed()
            },
            label = { kind.options.getOrElse(it) { "" } },
            enabled = row.enabled,
        )
    }
}

@Composable
private fun RangeRow(row: SettingRow, label: String, kind: SettingKind.Range, changed: () -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    Box(Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
        PanelSlider(
            label,
            drag ?: kind.value,
            { value ->
                drag = value
                if (kind.live) kind.set(value)
            },
            {
                drag?.let(kind.set)
                drag = null
                SettingsPlatform.save()
                changed()
            },
            range = kind.range,
            step = kind.step,
            enabled = row.enabled,
            valueText = kind.text,
        )
    }
}
