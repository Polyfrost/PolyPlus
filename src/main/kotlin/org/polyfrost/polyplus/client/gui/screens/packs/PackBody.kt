@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.packs

//? if >= 26.3 || = 1.8.9 {
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextPromptDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.screens.world.ConfirmDialog
import org.polyfrost.polyplus.client.gui.screens.world.DialogSpec
//?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

//? if >= 26.3 || = 1.8.9 {
private val PackIconSize = 36.dp
private val PackRowHeight = 52.dp
private val RowSpacing = 2.dp
private const val PACK_TICK_MILLIS = 50L
private const val EDGE_SCROLL_MILLIS = 16L
private const val LIFT_SCALE = 0.03f

private sealed interface PackLine {
    val key: String

    class Folder(val folder: PackFolder) : PackLine {
        override val key: String get() = "folder:${folder.path}"
    }

    class Item(val item: PackItem) : PackLine {
        override val key: String get() = item.id
    }
}

private class PackView(
    val session: PackSession,
    val drag: PackDragState,
    val focus: FocusRequester,
    val everything: List<PackItem>,
    val foldered: Boolean,
    val captions: Boolean,
)

@Composable
internal fun PackBody(session: PackSession, modifier: Modifier = Modifier, trailing: (@Composable RowScope.(hasSelection: Boolean) -> Unit)? = null) {
    LaunchedEffect(session) {
        while (true) {
            delay(PACK_TICK_MILLIS)
            session.tick()
        }
    }
    DisposableEffect(session) {
        session.resume()
        onDispose {}
    }
    val drag = remember(session) { PackDragState() }
    drag.folderDrops = session.foldersEnabled
    drag.spacing = with(LocalDensity.current) { RowSpacing.toPx() }
    LaunchedEffect(drag) {
        while (true) {
            delay(EDGE_SCROLL_MILLIS)
            drag.edgeScroll()?.let { (scroll, by) -> scroll.scrollBy(by) }
        }
    }
    val focus = remember { FocusRequester() }
    val revision = session.revision
    var query by remember(session) { mutableStateOf("") }
    val selected = remember(session, revision) { session.selected() }
    val available = remember(session, revision, session.sort) { session.available() }
    val everything = remember(selected, available) { selected + available }
    val filter = session.filter
    val filtering = query.isNotBlank() || filter != PackFilter.All
    val foldered = session.foldersEnabled && !session.flat && !filtering
    val path = if (foldered && session.folders.exists(session.folder)) session.folder else ""
    val counts = remember(everything) { PackFilter.entries.associateWith { f -> everything.count { f.accepts(it) } } }
    val availableLines = remember(available, everything, query, filter, foldered, path) {
        if (!foldered) {
            available.filter { filter.accepts(it) && it.matches(query) }.map<PackItem, PackLine> { PackLine.Item(it) }
        } else {
            buildList<PackLine> {
                session.folders.children(everything, path).forEach { add(PackLine.Folder(it)) }
                session.folders.directPacks(available, path).forEach { add(PackLine.Item(it)) }
            }
        }
    }
    val selectedLines = remember(selected, query, filter) { selected.filter { filter.accepts(it) && it.matches(query) }.map { PackLine.Item(it) } }
    val view = PackView(session, drag, focus, everything, foldered, !foldered || session.foldersEnabled)
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.fillMaxSize().onGloballyPositioned { origin = it.localToRoot(Offset.Zero) }) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PackToolbar(session, query, { query = it }, counts)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .focusable()
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown || session.dialog != null || session.picked.isEmpty()) return@onKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                session.toggle(session.picked.toList())
                                true
                            }
                            Key.Delete -> {
                                session.askDelete(session.pickedItems())
                                true
                            }
                            else -> false
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PackColumn(
                    PackSide.Available,
                    path,
                    availableLines,
                    when {
                        filtering -> "No packs match."
                        path.isNotEmpty() -> "This folder is empty. Drag packs here to move them in."
                        else -> "Every pack is in use."
                    },
                    view,
                    Modifier.weight(1f),
                )
                PackColumn(
                    PackSide.Selected,
                    "",
                    selectedLines,
                    if (filtering) "No packs match." else "No packs selected. Drag packs here to turn them on.",
                    view,
                    Modifier.weight(1f),
                )
            }
            if (trailing != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    trailing(selected.isNotEmpty())
                }
            }
        }
        PackGhost(view, origin)
    }
    PackDialogs(session)
}

@Composable
private fun PackColumn(side: PackSide, path: String, lines: List<PackLine>, empty: String, view: PackView, modifier: Modifier) {
    val session = view.session
    val drag = view.drag
    val theme = OneConfigTheme.current
    val accent = OneConfigTheme.accent
    val scroll = rememberScrollState()
    val items = lines.mapNotNull { (it as? PackLine.Item)?.item }
    val visible = items.map { it.id }
    val movableIds = items.filter { if (side == PackSide.Available) it.canSelect || it.file != null else !it.fixed || it.canUnselect }.map { it.id }.toSet()
    val pickedHere = visible.filter { it in session.picked }
    val showGapAtEnd = drag.gapBefore(side, null)
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp).height(ControlSize.Compact.height), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (side == PackSide.Available && view.foldered) {
                if (path.isNotEmpty()) {
                    IconButton("left-arrow", "Back", { session.folder = PackFolders.parentOf(path) }, size = ControlSize.Compact)
                }
                Breadcrumbs(path, view, Modifier.weight(1f))
            } else {
                Text(if (side == PackSide.Available) "Available" else "Selected", Modifier.weight(1f), color = theme.textColor, style = theme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            if (pickedHere.isNotEmpty()) {
                Text("${pickedHere.size} picked", color = theme.mutedText, style = theme.typography.small, maxLines = 1, softWrap = false)
                if (side == PackSide.Available) {
                    IconButton("right-arrow", "Turn on picked packs", { session.transfer(side, pickedHere) }, size = ControlSize.Compact)
                } else {
                    IconButton("left-arrow", "Turn off picked packs", { session.transfer(side, pickedHere) }, size = ControlSize.Compact)
                }
            } else {
                Text(items.size.toString(), color = theme.mutedText, style = theme.typography.small, maxLines = 1, softWrap = false)
            }
            if (side == PackSide.Available) {
                if (view.foldered) {
                    IconButton("plus", "New folder", { session.dialog = PackDialog.NewFolder(path, emptyList()) }, size = ControlSize.Compact)
                }
                IconButton("select-all", "Turn on every pack shown", { session.transfer(side, items.filter { it.canSelect }.map { it.id }) }, size = ControlSize.Compact, enabled = items.any { it.canSelect })
            } else {
                IconButton("x-circle", "Turn off every pack shown", { session.transfer(side, items.filter { it.canUnselect }.map { it.id }) }, size = ControlSize.Compact, enabled = items.any { it.canUnselect })
            }
        }
        val shape = OneConfigTheme.shape(14.dp)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .card(14.dp)
                .then(if (drag.columnTargeted(side)) Modifier.border(2.dp, accent, shape) else Modifier)
                .onGloballyPositioned { drag.column(side, it, scroll) }
                .padding(6.dp),
        ) {
            if (lines.isEmpty() && !showGapAtEnd) {
                Text(empty, Modifier.align(Alignment.Center).padding(horizontal = 12.dp), color = theme.textColorSecondary, style = theme.typography.small)
            } else {
                ScrollContainer(scroll, Modifier.fillMaxSize(), fadingEdges = true) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowSpacing)) {
                        lines.forEach { line ->
                            if (line is PackLine.Item && drag.gapBefore(side, line.item.id)) key("gap") { DropGap(drag) }
                            key(line.key) {
                                when (line) {
                                    is PackLine.Folder -> PackFolderRow(line.folder, view)
                                    is PackLine.Item -> PackRow(line.item, side, visible, { it in movableIds }, view)
                                }
                            }
                        }
                        if (showGapAtEnd) key("gap") { DropGap(drag) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: String, view: PackView, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val parts = if (path.isEmpty()) emptyList() else path.split('/')
    val crumbs = listOf("" to "Available") + parts.indices.map { i -> parts.take(i + 1).joinToString("/") to parts[i] }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        crumbs.forEachIndexed { index, (crumb, label) ->
            val last = index == crumbs.lastIndex
            if (index > 0) Text("/", color = theme.mutedText, style = theme.typography.small, maxLines = 1, softWrap = false)
            val targeted = view.drag.folderTargeted(crumb)
            val key = "crumb:$crumb"
            DisposableEffect(key) { onDispose { view.drag.forgetFolder(key) } }
            Box(
                Modifier
                    .then(if (last) Modifier.weight(1f, fill = false) else Modifier)
                    .clip(OneConfigTheme.shape(6.dp))
                    .then(if (targeted) Modifier.background(OneConfigTheme.accent.copy(alpha = 0.22f)) else Modifier)
                    .onGloballyPositioned { if (!last) view.drag.folderSpot(key, crumb, it) else view.drag.forgetFolder(key) }
                    .clickable(enabled = !last) { view.session.folder = crumb }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    label,
                    color = if (last) theme.textColor else theme.textColorSecondary,
                    style = theme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun DropGap(drag: PackDragState) {
    val accent = OneConfigTheme.accent
    val height = with(LocalDensity.current) { drag.cardSize.height.toDp() }
    val shape = OneConfigTheme.shape(10.dp)
    val shown = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!Motion.reduced) shown.animateTo(1f, tween(Motion.fast)) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = shown.value }
            .clip(shape)
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.5f), shape),
    )
}

private fun handleDrop(view: PackView) {
    val session = view.session
    val (payload, target) = view.drag.finish() ?: return
    when (target) {
        is PackDropTarget.Folder -> session.movePacks(payload.ids, target.path)
        is PackDropTarget.Column -> when (target.side) {
            PackSide.Selected -> session.request(PackPlacement(payload.ids.filter { session.sideOf(it) != null }, target.anchor, true))
            PackSide.Available -> if (payload.from == PackSide.Selected) session.unselect(payload.ids)
        }
        null -> {}
    }
}

@Composable
private fun PackRow(item: PackItem, side: PackSide, visible: List<String>, movable: (String) -> Boolean, view: PackView) {
    val session = view.session
    val drag = view.drag
    val accent = OneConfigTheme.accent
    val picked = item.id in session.picked
    val hidden = drag.hidden(item.id)
    val landing = drag.landingHides(item.id)
    DisposableEffect(side, item.id) { onDispose { drag.forget(side, item.id) } }
    val shape = OneConfigTheme.shape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .collapsedWhen(hidden)
            .onGloballyPositioned { drag.row(side, item.id, item.selected && item.fixed, it) }
            .animatePlacement(hidden || landing)
            .alpha(if (hidden || landing) 0f else 1f)
            .contextMenu(entries = { packMenu(item, side, visible, view) })
            .clip(shape)
            .rowHover(drag.payload == null && !hidden)
            .then(if (picked) Modifier.background(accent.copy(alpha = 0.16f)) else Modifier)
            .packGesture(
                drag,
                item.id,
                payload = {
                    val ids = session.dragIds(side, item.id, visible).filter(movable)
                    if (!movable(item.id) || ids.isEmpty()) {
                        null
                    } else {
                        val sources = ids.mapNotNull { id -> view.everything.firstOrNull { it.id == id }?.folder }.toSet()
                        PackDragPayload(side, ids, item, null, sources)
                    }
                },
                onClick = { click ->
                    runCatching { view.focus.requestFocus() }
                    if (click.double) {
                        session.transfer(side, session.dragIds(side, item.id, visible))
                    } else {
                        session.click(side, item.id, visible, click.toggle, click.range)
                    }
                },
                onDrop = { handleDrop(view) },
            ),
    ) {
        PackCard(item, side, visible, view, true)
    }
}

@Composable
private fun PackCard(item: PackItem, side: PackSide, visible: List<String>, view: PackView, interactive: Boolean) {
    val session = view.session
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = PackRowHeight).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PackIcon(item, session)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, Modifier.weight(1f, fill = false), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                if (item.selected && (item.required || item.fixed)) {
                    TooltipBox(if (item.required) "The game needs this pack." else "This pack stays in place.", placement = TooltipPlacement.AboveCenter) {
                        ThemedIcon("lock", theme.mutedText, 12.dp)
                    }
                }
            }
            if (item.description.isNotBlank()) {
                Text(item.description, color = theme.textColorSecondary, style = theme.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (item.folder.isNotEmpty() && (side == PackSide.Selected || !view.foldered)) {
                Text("In ${item.folder}", color = theme.mutedText, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            if (item.server || !item.compatible) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (item.server) Badge("From server")
                    if (!item.compatible) Badge(item.warning, tone = BadgeTone.Warning)
                }
            }
        }
        PackActions(item, side, visible, view, interactive)
    }
}

@Composable
private fun PackActions(item: PackItem, side: PackSide, visible: List<String>, view: PackView, interactive: Boolean) {
    val session = view.session
    val configurable = remember(item.id, session.revision) { session.optionsFor(item) }
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (configurable) IconButton("settings", "Pack options", { if (interactive) session.openOptions(item) })
        if (item.selected) {
            IconButton("minus", "Turn off", { if (interactive) session.unselect(item) }, enabled = item.canUnselect)
        } else {
            IconButton("plus", "Turn on", { if (interactive) session.select(item) }, enabled = item.canSelect)
        }
        Box {
            IconButton("dots-vertical", "More actions", { if (interactive) open = !open }, active = open, tooltip = if (open || !interactive) null else "More actions")
            if (interactive) {
                DropdownMenu(open, { open = false }, if (open) packMenu(item, side, visible, view) else emptyList(), placement = Placement.BelowEnd)
            }
        }
    }
}

private fun packMenu(item: PackItem, side: PackSide, visible: List<String>, view: PackView): List<MenuEntry> {
    val session = view.session
    val group = if (item.id in session.picked) session.dragIds(side, item.id, visible) else listOf(item.id)
    val groupItems = group.mapNotNull { id -> view.everything.firstOrNull { it.id == id } }.ifEmpty { listOf(item) }
    val many = groupItems.size > 1
    return buildList {
        if (item.selected) {
            add(MenuAction(if (many) "Turn off ${groupItems.size} packs" else "Turn off", { session.unselect(groupItems.map { it.id }) }, icon = "minus", enabled = groupItems.any { it.canUnselect }, shortcutHint = "Enter"))
            if (!many) {
                add(MenuAction("Move to top", { session.moveToTop(item) }, icon = "up", enabled = item.canMoveUp))
                add(MenuAction("Move to bottom", { session.moveToBottom(item) }, icon = "down", enabled = item.canMoveDown))
            }
        } else {
            add(MenuAction(if (many) "Turn on ${groupItems.size} packs" else "Turn on", { session.request(PackPlacement(groupItems.map { it.id }, null, false)) }, icon = "plus", enabled = groupItems.any { it.canSelect }, shortcutHint = "Enter"))
        }
        if (!many && session.optionsFor(item)) add(MenuAction("Options", { session.openOptions(item) }, icon = "settings"))
        val files = groupItems.filter { it.file != null }
        if (session.foldersEnabled && files.isNotEmpty()) {
            add(MenuSeparator)
            if (!many) add(MenuAction("Show in folder", { session.showInFolder(item) }, icon = "external-link"))
            val ids = files.map { it.id }
            val here = files.map { it.folder }.toSet()
            val destinations = buildList<MenuEntry> {
                if (here != setOf("")) add(MenuAction("Main pack folder", { session.movePacks(ids, "") }, icon = "folder"))
                session.folders.allPaths(view.everything).filter { here != setOf(it) }.forEach { path ->
                    add(MenuAction(path, { session.movePacks(ids, path) }, icon = "folder"))
                }
                if (isNotEmpty()) add(MenuSeparator)
                add(MenuAction("New folder", { session.dialog = PackDialog.NewFolder(session.folder, ids) }, icon = "plus"))
            }
            add(MenuAction("Move to folder", {}, icon = "move", children = destinations))
        }
        val deletable = groupItems.filter { it.deletable }
        if (deletable.isNotEmpty()) {
            add(MenuSeparator)
            add(MenuAction(if (deletable.size > 1) "Delete ${deletable.size} packs" else "Delete", { session.askDelete(deletable) }, icon = "trash", danger = true, shortcutHint = "Delete"))
        }
    }
}

private fun folderMenu(folder: PackFolder, view: PackView): List<MenuEntry> {
    val session = view.session
    val ids = folder.packs.filter { it.canSelect }.map { it.id }
    return buildList {
        add(MenuAction("Open", { session.folder = folder.path }, icon = "folder"))
        add(MenuAction("Turn on every pack", { session.transfer(PackSide.Available, ids) }, icon = "plus", enabled = ids.isNotEmpty()))
        add(MenuSeparator)
        add(MenuAction("New folder inside", { session.dialog = PackDialog.NewFolder(folder.path, emptyList()) }, icon = "plus"))
        add(MenuAction("Rename", { session.dialog = PackDialog.RenameFolder(folder.path) }, icon = "text-input"))
        session.folders.dir(folder.path)?.let { dir -> add(MenuAction("Show in folder", { PackFiles.reveal(dir) }, icon = "external-link")) }
        add(MenuSeparator)
        add(MenuAction("Delete", { session.deleteFolder(folder) }, icon = "trash", danger = true))
    }
}

@Composable
private fun PackFolderRow(folder: PackFolder, view: PackView) {
    val session = view.session
    val drag = view.drag
    val accent = OneConfigTheme.accent
    val ids = folder.packs.filter { it.canSelect }.map { it.id }
    val key = "folder:${folder.path}"
    val targeted = drag.folderTargeted(folder.path)
    val hidden = drag.payload?.folder?.path == folder.path
    DisposableEffect(key) { onDispose { drag.forgetFolder(key) } }
    val shape = OneConfigTheme.shape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { drag.folderSpot(key, folder.path, it) }
            .animatePlacement(false)
            .alpha(if (hidden) 0.4f else 1f)
            .contextMenu(entries = { folderMenu(folder, view) })
            .clip(shape)
            .rowHover(drag.payload == null)
            .then(if (targeted) Modifier.background(accent.copy(alpha = 0.22f)).border(2.dp, accent, shape) else Modifier)
            .packGesture(
                drag,
                key,
                payload = { if (ids.isEmpty()) null else PackDragPayload(PackSide.Available, ids, null, folder, setOf(folder.path)) },
                onClick = { session.folder = folder.path },
                onDrop = { handleDrop(view) },
            ),
    ) {
        FolderCard(folder, view, true)
    }
}

private fun Modifier.rowHover(active: Boolean): Modifier = composed {
    val theme = OneConfigTheme.current
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val on = hovered && active
    val fill by Motion.animateColorAsState(if (on) theme.textColor.copy(alpha = theme.hoverOverlayAlpha) else Color.Transparent)
    val edge by Motion.animateColorAsState(if (on) theme.borderColor else Color.Transparent)
    val shape = OneConfigTheme.shape(10.dp)
    this
        .hoverable(source)
        .pointerHoverIcon(PointerIcon.Hand)
        .background(fill)
        .border(1.dp, edge, shape)
}

@Composable
private fun FolderCard(folder: PackFolder, view: PackView, interactive: Boolean) {
    val session = view.session
    val theme = OneConfigTheme.current
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = PackRowHeight).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FolderIcon(folder, session)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(folder.name, color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            val enabled = folder.enabled
            val summary = when {
                folder.packs.isEmpty() -> "Empty"
                enabled == 0 -> packCount(folder.packs.size)
                else -> "${packCount(folder.packs.size)}, $enabled on"
            }
            Text(
                if (folder.description.isBlank()) summary else "${folder.description} - $summary",
                color = theme.textColorSecondary,
                style = theme.typography.small,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton("dots-vertical", "More actions", { if (interactive) open = !open }, active = open, tooltip = if (open || !interactive) null else "More actions")
            if (interactive) DropdownMenu(open, { open = false }, if (open) folderMenu(folder, view) else emptyList(), placement = Placement.BelowEnd)
        }
        Box(Modifier.size(PackIconSize - 8.dp), contentAlignment = Alignment.Center) {
            ThemedIcon("right-arrow", theme.mutedText, 14.dp)
        }
    }
}

@Composable
private fun PackIcon(item: PackItem, session: PackSession) {
    val icon by produceState(session.cachedIcon(item), item.id, session.revision) {
        value = withContext(Dispatchers.IO) { session.icon(item) }
    }
    IconFrame(icon, SOCIAL_ASSETS + "package-01.svg", false)
}

@Composable
private fun FolderIcon(folder: PackFolder, session: PackSession) {
    val icon by produceState(session.cachedFolderIcon(folder.path), folder.path, session.revision) {
        value = withContext(Dispatchers.IO) { session.folderIcon(folder.path) }
    }
    IconFrame(icon, "folder", true)
}

@Composable
private fun IconFrame(bitmap: ImageBitmap?, fallback: String, kitIcon: Boolean) {
    val theme = OneConfigTheme.current
    Box(Modifier.size(PackIconSize).clip(OneConfigTheme.shape(8.dp)).background(theme.componentBackground), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        } else if (kitIcon) {
            Icon(fallback, null, Modifier.size(18.dp), theme.mutedText)
        } else {
            ThemedIcon(fallback, theme.mutedText, 18.dp)
        }
    }
}

@Composable
private fun PackGhost(view: PackView, origin: Offset) {
    val drag = view.drag
    val payload = drag.payload
    val landing = drag.landing
    if (payload == null && landing == null) return
    val item = payload?.item ?: landing?.item
    val folder = payload?.folder ?: landing?.folder
    val count = payload?.count ?: landing?.count ?: 1
    val theme = OneConfigTheme.current
    val density = LocalDensity.current
    val position = remember { Animatable(drag.cardTopLeft, Offset.VectorConverter) }
    val lift = remember { Animatable(if (Motion.reduced) 1f else 0f) }
    val shrink = remember { Animatable(1f) }
    var synced by remember(landing) { mutableStateOf(false) }
    LaunchedEffect(payload) {
        if (payload == null) return@LaunchedEffect
        shrink.snapTo(1f)
        if (Motion.reduced) lift.snapTo(1f) else lift.animateTo(1f, tween(Motion.fast))
    }
    LaunchedEffect(landing) {
        val land = landing ?: return@LaunchedEffect
        position.snapTo(land.from)
        synced = true
        if (Motion.reduced) {
            drag.landed(land)
            return@LaunchedEffect
        }
        var dest: Rect? = null
        repeat(3) {
            withFrameNanos { }
            dest = (land.into?.let(drag::folderRect) ?: drag.rowRect(land.ids.first(), null))?.takeIf(drag::onScreen) ?: dest
        }
        val target = dest
        coroutineScope {
            when {
                target == null -> launch { shrink.animateTo(0f, tween(Motion.standard)) }
                land.into != null -> {
                    val center = target.center - Offset(drag.cardSize.width / 2f, drag.cardSize.height / 2f)
                    launch { position.animateTo(center, tween(Motion.standard + 60)) }
                    launch { shrink.animateTo(0f, tween(Motion.standard + 60)) }
                }
                else -> {
                    launch { position.animateTo(target.topLeft, tween(Motion.standard + 60)) }
                    launch { lift.animateTo(0f, tween(Motion.standard + 60)) }
                }
            }
        }
        drag.landed(land)
    }
    val at = when {
        payload != null -> drag.cardTopLeft
        synced -> position.value
        else -> landing?.from ?: position.value
    }
    val width = with(density) { drag.cardSize.width.toDp() }
    val shape = OneConfigTheme.shape(10.dp)
    Box(
        Modifier
            .offset { (at - origin).round() }
            .zIndex(10f)
            .width(width)
            .graphicsLayer {
                val scale = (1f + LIFT_SCALE * lift.value) * (0.6f + 0.4f * shrink.value)
                scaleX = scale
                scaleY = scale
                alpha = shrink.value
            },
    ) {
        if (count > 2) StackedCard(Modifier.matchParentSize().offset(8.dp, 8.dp), shape)
        if (count > 1) StackedCard(Modifier.matchParentSize().offset(4.dp, 4.dp), shape)
        Box(
            Modifier
                .then(if (theme.shadowEnabled) Modifier.shadow((14f * lift.value).dp, shape, ambientColor = theme.shadowColor, spotColor = theme.shadowColor) else Modifier)
                .card(10.dp, selected = true),
        ) {
            when {
                item != null -> PackCard(item, item.side, emptyList(), view, false)
                folder != null -> FolderCard(folder, view, false)
            }
        }
        if (count > 1) {
            Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp)) {
                Badge(count.toString(), tone = BadgeTone.Accent)
            }
        }
    }
}

@Composable
private fun StackedCard(modifier: Modifier, shape: Shape) {
    Box(modifier.clip(shape).card(10.dp))
}

@Composable
private fun PackDialogs(session: PackSession) {
    when (val dialog = session.dialog) {
        is PackDialog.Incompatible -> ConfirmDialog(
            DialogSpec(
                PackSession.INCOMPATIBLE_TITLE,
                if (dialog.items.size == 1) dialog.items.first().confirm else "${dialog.items.size} of these packs were made for a different version of Minecraft. Turn them on anyway?",
                "Turn on anyway",
                "Cancel",
                false,
            ) {},
        ) { session.answerIncompatible(dialog, it) }
        is PackDialog.Drop -> ConfirmDialog(
            DialogSpec("Add these packs?", PackSession.names(dialog.files), "Add", "Cancel", false) {},
        ) { session.answerDrop(dialog.files, it) }
        is PackDialog.Notice -> ConfirmDialog(
            DialogSpec(dialog.title, dialog.message, "OK", null, false) {},
        ) { session.dialog = null }
        is PackDialog.SaveProfile -> TextPromptDialog(
            "Save profile",
            "",
            { session.saveProfile(it) },
            { session.dialog = null },
            subtitle = "Saves the packs you have turned on and their order.",
            placeholder = "Profile name",
            maxLength = 32,
            validate = { if (it.isBlank()) "Give the profile a name." else null },
            confirmLabel = "Save",
        )
        is PackDialog.DeleteProfile -> ConfirmDialog(
            DialogSpec("Delete ${dialog.profile.name}?", "Your packs stay installed. Only the profile is removed.", "Delete", "Cancel", true) {},
        ) { session.deleteProfile(dialog.profile, it) }
        is PackDialog.NewFolder -> TextPromptDialog(
            "New folder",
            "",
            { session.createFolder(dialog.parent, it, dialog.moving) },
            { session.dialog = null },
            subtitle = when {
                dialog.moving.isNotEmpty() -> "The ${if (dialog.moving.size == 1) "pack moves" else "packs move"} into the new folder."
                dialog.parent.isEmpty() -> "Creates a folder in your pack folder."
                else -> "Creates a folder inside ${PackFolders.nameOf(dialog.parent)}."
            },
            placeholder = "Folder name",
            maxLength = 64,
            validate = { session.folderNameError(dialog.parent, it) },
            confirmLabel = "Create",
        )
        is PackDialog.RenameFolder -> TextPromptDialog(
            "Rename folder",
            PackFolders.nameOf(dialog.path),
            { session.renameFolder(dialog.path, it) },
            { session.dialog = null },
            placeholder = "Folder name",
            maxLength = 64,
            validate = { session.folderNameError(PackFolders.parentOf(dialog.path), it, dialog.path) },
            confirmLabel = "Rename",
        )
        is PackDialog.DeleteFolder -> ConfirmDialog(
            DialogSpec(
                "Delete ${dialog.folder.name}?",
                buildString {
                    append(if (dialog.folder.packs.isEmpty()) "This folder has other files in it." else "This folder has ${packCount(dialog.folder.packs.size)} in it.")
                    append(' ')
                    append(if (PackFiles.trashSupported) "Everything inside goes to the ${PackFiles.trashLabel}." else "Everything inside is deleted for good.")
                },
                "Delete",
                "Cancel",
                true,
            ) {},
        ) { session.answerDeleteFolder(dialog.folder, it) }
        is PackDialog.DeletePacks -> ConfirmDialog(
            DialogSpec(
                if (dialog.items.size == 1) "Delete ${dialog.items.first().title}?" else "Delete ${dialog.items.size} packs?",
                buildString {
                    append(if (PackFiles.trashSupported) "The ${if (dialog.items.size == 1) "pack goes" else "packs go"} to the ${PackFiles.trashLabel}." else "This can't be undone.")
                    if (dialog.items.any { it.selected }) append(" Packs that are on may be in use. If deleting fails, turn them off, leave this page, then try again.")
                },
                "Delete",
                "Cancel",
                true,
            ) {},
        ) { session.deletePacks(dialog.items, it) }
        is PackDialog.Overwrite -> ConfirmDialog(
            DialogSpec(
                "Replace existing files?",
                "${dialog.names.joinToString(", ")} already ${if (dialog.names.size == 1) "exists" else "exist"} in ${if (dialog.target.isEmpty()) "the main pack folder" else PackFolders.nameOf(dialog.target)}. Replacing deletes the old ${if (dialog.names.size == 1) "copy" else "copies"} for good.",
                "Replace",
                "Cancel",
                true,
            ) {},
        ) { accepted -> if (accepted) session.movePacks(dialog.ids, dialog.target, true) else session.dialog = null }
        is PackDialog.HardDelete -> ConfirmDialog(
            DialogSpec(
                "Couldn't use the ${PackFiles.trashLabel}",
                "Delete ${PackSession.names(dialog.paths)} for good instead? This can't be undone.",
                "Delete",
                "Cancel",
                true,
            ) {},
        ) { session.deleteHard(dialog.paths, it) }
        null -> {}
    }
}
//?}
