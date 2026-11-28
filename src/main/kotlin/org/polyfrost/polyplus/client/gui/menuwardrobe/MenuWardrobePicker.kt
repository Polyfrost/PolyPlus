@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalLayoutApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.polyfrost.compose.host.controller.controllerArrows
import org.polyfrost.compose.host.controller.isFromController
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlIndication
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.CustomizeKit
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelHint
import org.polyfrost.polyplus.client.gui.lobbyshell.Segmented
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.wardrobe.AuraColorPopup
import org.polyfrost.polyplus.client.gui.wardrobe.AuraSwatchRow
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.ForceCapesToggle
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

private const val GRID_COLUMNS = 3
private const val DOUBLE_CLICK_MS = 400L
private const val GET_MORE_KEY = "get-more"
private val TileGap = 8.dp
private val TileRadius = 12.dp
internal val OutlineOnHover = ControlIndication(hoverAlpha = 0f, hoverBorder = true)

internal class PickerEntry(val key: String, val name: String, val equipped: Boolean, val item: CosmeticUiItem?, val cape: MojangCape?)

internal class AuraChoice(val color: Int?, val onPreview: (Int) -> Unit, val onCommit: (Int?) -> Unit)

internal class PickerModel(
    val type: CosmeticType,
    val entries: List<PickerEntry>,
    val selected: PickerEntry?,
    val target: PickerEntry?,
    val unequips: Boolean,
    val variantOf: (CosmeticUiItem) -> Int?,
    val onHover: (PickerEntry, Boolean) -> Unit,
    val onPick: (PickerEntry) -> Unit,
    val onPickVariant: (CosmeticUiItem, Int) -> Unit,
    val onApply: () -> Unit,
    val onClose: () -> Unit,
    val onMarketplace: (() -> Unit)?,
    val aura: AuraChoice?,
    val forceToggle: Boolean,
    val note: String?,
    val status: String? = null,
    val picksToWheel: Boolean = false,
)

private enum class PickerFilter(val label: String) {
    All("All"),
    Equipped("Equipped"),
    PolyPlus("PolyPlus"),
    Minecraft("Minecraft"),
}

private enum class ApplyAction { Equip, Unequip, Equipped }

private fun filtersFor(type: CosmeticType): List<PickerFilter> =
    if (type == CosmeticType.Cape) listOf(PickerFilter.All, PickerFilter.PolyPlus, PickerFilter.Minecraft)
    else listOf(PickerFilter.All, PickerFilter.Equipped)

private fun PickerEntry.matches(filter: PickerFilter, query: String): Boolean {
    val passes = when (filter) {
        PickerFilter.All -> true
        PickerFilter.Equipped -> equipped
        PickerFilter.PolyPlus -> item != null
        PickerFilter.Minecraft -> cape != null
    }
    return passes && (query.isBlank() || name.contains(query.trim(), ignoreCase = true))
}

private fun PickerModel.applyAction(): ApplyAction? {
    val target = target ?: return null
    return when {
        unequips && selected == null -> ApplyAction.Equipped
        unequips -> ApplyAction.Unequip
        else -> ApplyAction.Equip
    }
}

private fun PickerModel.variantItem(): CosmeticUiItem? = (selected ?: target)?.item?.takeIf { it.hasVariants }

@Composable
private fun EntryArt(entry: PickerEntry, model: PickerModel, modifier: Modifier) {
    val item = entry.item
    val cape = entry.cape
    when {
        item != null -> CosmeticArt(item, model.variantOf(item), modifier)
        cape != null -> MojangCapeArt(cape, modifier)
    }
}

@Composable
private fun TryOnWhileHovered(entry: PickerEntry, hovered: Boolean, onHover: (PickerEntry, Boolean) -> Unit) {
    val callback by rememberUpdatedState(onHover)
    DisposableEffect(entry.key, hovered) {
        if (hovered) callback(entry, true)
        onDispose { if (hovered) callback(entry, false) }
    }
}

@Composable
internal fun OutlineIconButton(icon: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val description = when (icon) {
        "left-arrow" -> "Back"
        "close" -> "Close"
        else -> icon.replace('-', ' ')
    }
    val text = OneConfigTheme.current.textColor
    IconButton(
        icon = icon,
        contentDescription = description,
        onClick = onClick,
        modifier = modifier,
        style = IconButtonStyle.Hairline,
        tooltip = null,
        restTint = text.copy(alpha = text.alpha * 0.7f),
        frameSize = 26.dp,
        iconSize = 18.dp,
    )
}

@Composable
internal fun SlotPicker(model: PickerModel, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(model)
    var query by remember(model.type) { mutableStateOf("") }
    var filter by remember(model.type) { mutableStateOf(PickerFilter.All) }
    var cursor by remember(model.type) { mutableStateOf(model.selected?.key) }
    var gridFocused by remember { mutableStateOf(false) }
    val grid = remember(model.type) { LazyGridState() }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val filters = filtersFor(model.type)
    val shown = model.entries.filter { it.matches(filter, query) }

    LaunchedEffect(model.selected?.key) { model.selected?.key?.let { cursor = it } }
    LaunchedEffect(model.type) { runCatching { focus.requestFocus() } }

    fun move(delta: Int, controller: Boolean): Boolean {
        val list = current.entries.filter { it.matches(filter, query) }
        val getMore = controller && current.onMarketplace != null
        val last = if (getMore) list.size else list.lastIndex
        if (last < 0) return false
        val key = cursor ?: current.selected?.key ?: current.target?.key
        val from = if (getMore && key == GET_MORE_KEY) list.size else list.indexOfFirst { it.key == key }
        val raw = from + delta
        val leaves = raw < 0 || (raw > last && from / GRID_COLUMNS == last / GRID_COLUMNS)
        if (controller && from >= 0 && leaves) return false
        val next = if (from < 0) 0 else raw.coerceIn(0, last)
        if (next == from) return false
        scope.revealItem(grid, next)
        if (next == list.size) {
            cursor = GET_MORE_KEY
            return true
        }
        val entry = list[next]
        cursor = entry.key
        if (!current.picksToWheel) current.onPick(entry)
        return true
    }

    fun confirm(): Boolean {
        val model = current
        if (cursor == GET_MORE_KEY) {
            val open = model.onMarketplace ?: return false
            open()
            return true
        }
        val entry = model.entries.firstOrNull { it.key == cursor }
        if (model.picksToWheel) {
            model.onPick(entry ?: return false)
            return true
        }
        if (entry != null && entry.key != model.selected?.key) {
            model.onPick(entry)
            return true
        }
        val action = model.applyAction() ?: return false
        if (action == ApplyAction.Equipped) return false
        model.onApply()
        return true
    }

    fun onKey(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        val controller = event.isFromController
        val arrows = !controller || gridFocused
        return when (event.key) {
            Key.DirectionLeft -> arrows && move(-1, controller)
            Key.DirectionRight -> arrows && move(1, controller)
            Key.DirectionUp -> arrows && move(-GRID_COLUMNS, controller)
            Key.DirectionDown -> arrows && move(GRID_COLUMNS, controller)
            Key.Enter, Key.NumPadEnter -> gridFocused && confirm()
            else -> false
        }
    }

    Column(modifier.fillMaxSize()) {
        if (model.entries.isEmpty()) {
            EmptySlot(model, Modifier.fillMaxWidth().weight(1f))
        } else {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CustomizeKit.SectionGap),
            ) {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Search ${model.type.displayName.lowercase()}",
                    size = ControlSize.Regular,
                )
                Segmented(filters, filter, { filter = it }, label = { it.label })
                model.note?.let { PanelHint(it) }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = CustomizeKit.SectionGap)
                    .focusRequester(focus)
                    .onPreviewKeyEvent(::onKey)
                    .onFocusChanged { gridFocused = it.isFocused }
                    .controllerArrows()
                    .focusable(),
            ) {
                if (shown.isEmpty() && model.onMarketplace == null) {
                    NoMatches(Modifier.fillMaxSize())
                } else {
                    PickerGrid(model, shown, grid, cursor, gridFocused, onTileFocus = { cursor = it.key }) { entry ->
                        cursor = entry.key
                        runCatching { focus.requestFocus() }
                    }
                    if (shown.isEmpty()) NoMatches(Modifier.fillMaxWidth().padding(top = 140.dp))
                }
            }
        }
        model.status?.let {
            Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                Badge(it, overImage = true)
            }
        }
        PickerFooter(model)
    }
}

private fun CoroutineScope.revealItem(grid: LazyGridState, index: Int) {
    val visible = grid.layoutInfo.visibleItemsInfo
    val viewport = grid.layoutInfo.viewportEndOffset
    val info = visible.firstOrNull { it.index == index }
    val fullyVisible = info != null && info.offset.y >= 0 && info.offset.y + info.size.height <= viewport
    if (!fullyVisible) launch { grid.animateScrollToItem(index) }
}

@Composable
private fun PickerGrid(
    model: PickerModel,
    shown: List<PickerEntry>,
    grid: LazyGridState,
    cursor: String?,
    gridFocused: Boolean,
    onTileFocus: (PickerEntry) -> Unit,
    onTileClick: (PickerEntry) -> Unit,
) {
    val current by rememberUpdatedState(model)
    var lastClickKey by remember { mutableStateOf<String?>(null) }
    var lastClickAt by remember { mutableLongStateOf(0L) }

    fun click(entry: PickerEntry) {
        val now = System.currentTimeMillis()
        val doubled = lastClickKey == entry.key && now - lastClickAt <= DOUBLE_CLICK_MS
        lastClickKey = entry.key
        lastClickAt = now
        onTileClick(entry)
        val model = current
        if (model.picksToWheel) {
            model.onPick(entry)
            return
        }
        if (!doubled) {
            model.onPick(entry)
            return
        }
        lastClickKey = null
        if (model.selected?.key == entry.key && !model.unequips) model.onApply()
    }

    ScrollContainer(grid, Modifier.fillMaxSize(), scrollbar = false, fadingEdges = true) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = grid,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 2.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
        ) {
            items(shown, key = { it.key }) { entry ->
                PickerTile(
                    entry = entry,
                    model = model,
                    selected = entry.key == model.selected?.key || (model.picksToWheel && entry.key == cursor),
                    onFocus = { onTileFocus(entry) },
                    onClick = { click(entry) },
                )
            }
            model.onMarketplace?.let { open ->
                item(key = GET_MORE_KEY) { GetMoreTile(gridFocused && cursor == GET_MORE_KEY, open) }
            }
        }
    }
}

@Composable
private fun PickerTile(entry: PickerEntry, model: PickerModel, selected: Boolean, onFocus: () -> Unit, onClick: () -> Unit) {
    val hover = rememberHoverState()
    TryOnWhileHovered(entry, hover.hovered, model.onHover)
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(TileRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (entry.equipped && !selected) Modifier.border(1.5.dp, OneConfigTheme.accent, shape) else Modifier)
            .hoverable(hover.interactionSource)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .selectableRow(selected, onClick, entry.name, shape, style = ItemSelectionStyle.Accent, interactionSource = hover.interactionSource)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .themedSurface(SurfaceKind.Field, OneConfigTheme.shape(8.dp), glass = false),
        ) {
            EntryArt(entry, model, Modifier.fillMaxSize().padding(6.dp))
            if (entry.equipped) EquippedCheck(Modifier.align(Alignment.TopEnd).padding(4.dp))
        }
        Text(
            entry.name,
            Modifier.fillMaxWidth(),
            color = SelectionDefaults.itemContentColor(selected, ItemSelectionStyle.Accent),
            style = theme.typography.small.copy(fontWeight = FontWeight.Medium),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}

@Composable
private fun EquippedCheck(modifier: Modifier) {
    Box(
        modifier.size(18.dp).clip(CircleShape).background(OneConfigTheme.accent),
        contentAlignment = Alignment.Center,
    ) {
        LobbyIcon("check", SelectionDefaults.itemContentColor(true, ItemSelectionStyle.Accent), 12.dp)
    }
}

@Composable
private fun GetMoreTile(highlighted: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val color = if (highlighted) OneConfigTheme.accent else theme.textColorSecondary
    val shape = OneConfigTheme.shape(TileRadius)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(shape)
            .then(
                if (highlighted) Modifier.border(1.5.dp, OneConfigTheme.accent, shape)
                else Modifier.border(1.dp, theme.textColorSecondary.copy(alpha = 0.25f), shape),
            )
            .oneConfigClickable("Get more in the Marketplace", onClick, shape = shape, recipe = OutlineOnHover)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        ThemedIcon("plus", color, 16.dp)
        Text("Get more", color = color, style = theme.typography.small, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NoMatches(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Text("Nothing matches", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
    }
}

@Composable
private fun EmptySlot(model: PickerModel, modifier: Modifier) {
    val name = model.type.displayName.lowercase()
    Box(modifier, contentAlignment = Alignment.Center) {
        EmptyState(
            title = if (model.type == CosmeticType.Cape) "No capes yet" else "No $name yet",
            text = model.note ?: "You don't own any $name yet. Find one you like in the Marketplace.",
            action = model.onMarketplace?.let { open ->
                { TextButton(text = "Open Marketplace", onClick = open, style = ControlStyle.Primary) }
            },
        )
    }
}

@Composable
private fun PickerFooter(model: PickerModel) {
    val variantItem = model.variantItem()
    val action = model.applyAction()
    val aura = model.aura
    if (variantItem == null && aura == null && !model.forceToggle && action == null) return
    Column(
        Modifier.fillMaxWidth().padding(top = CustomizeKit.SectionGap),
        verticalArrangement = Arrangement.spacedBy(CustomizeKit.SectionGap),
    ) {
        if (variantItem != null) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (variant in variantItem.variants) {
                    Chip(label = variant.name, selected = variant.id == model.variantOf(variantItem), onClick = { model.onPickVariant(variantItem, variant.id) })
                }
            }
        }
        if (aura != null) AuraControl(aura)
        if (model.forceToggle) ForceCapesToggle()
        val target = model.target
        if (action != null && target != null) {
            TextButton(
                text = when (action) {
                    ApplyAction.Equip -> "Equip ${target.name}"
                    ApplyAction.Unequip -> "Unequip ${target.name}"
                    ApplyAction.Equipped -> "Equipped"
                },
                onClick = model.onApply,
                style = if (action == ApplyAction.Equip) ControlStyle.Primary else ControlStyle.Secondary,
                size = ControlSize.Large,
                enabled = action != ApplyAction.Equipped,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AuraControl(aura: AuraChoice) {
    var customOpen by remember { mutableStateOf(false) }
    Box {
        AuraSwatchRow(aura.color, customOpen, { customOpen = !customOpen }, aura.onCommit)
        if (customOpen) {
            AuraColorPopup(aura.color, aura.onPreview, aura.onCommit, onDismiss = { customOpen = false })
        }
    }
}

@Composable
internal fun SlotPlaceholder() {
    ThemedIcon("plus", OneConfigTheme.current.textColorSecondary, 14.dp)
}
