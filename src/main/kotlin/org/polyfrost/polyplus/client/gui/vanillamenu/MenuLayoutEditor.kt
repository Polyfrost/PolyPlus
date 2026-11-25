@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import kotlin.math.roundToInt

/**
 * Which menu is being edited, and the editor for the Compose menus (lobby and PolyPlus menu). Their pieces are
 * [EditableBlock]s; the layout (moves, scale, hidden pieces, groups) is in [MenuLayoutStore] like the vanilla menu's,
 * with offsets in dp from where the menu normally puts a piece.
 */
object MenuLayoutEditor {
    /** The scope being edited right now, or null. */
    var editing by mutableStateOf<String?>(null)

    internal val selection = mutableStateMapOf<String, Boolean>()

    /** The pieces being dragged right now and how far, shown live and saved once on release. */
    internal var dragIds by mutableStateOf<Set<String>>(emptySet())
    internal var dragDelta by mutableStateOf(Offset.Zero)
    internal var liveScale = mutableStateMapOf<String, Float>()

    /** Right click menu: the piece and where it was opened, in root pixels. */
    internal var menu by mutableStateOf<Pair<String, Offset>?>(null)

    /** Each scope's pieces on screen right now, with the group the menu gives them. */
    internal val blocks = HashMap<String, MutableMap<String, String?>>()
    internal val labels = HashMap<String, String>()

    @JvmStatic
    fun isEditing(scope: String): Boolean = editing == scope

    @JvmStatic
    fun start(scope: String) {
        editing = scope
        selection.clear()
        menu = null
    }

    @JvmStatic
    fun stop() {
        editing = null
        selection.clear()
        menu = null
    }

    internal fun key(scope: String, id: String) = "$scope/$id"

    internal fun group(scope: String, id: String): String? {
        val stored = MenuLayoutStore.element(scope, id)?.group
        return (stored ?: blocks[scope]?.get(id))?.takeIf { it.isNotEmpty() }
    }

    /** A piece and the others in its group. */
    internal fun withGroup(scope: String, id: String): Set<String> {
        val group = group(scope, id) ?: return setOf(id)
        return blocks[scope].orEmpty().keys.filter { group(scope, it) == group }.toSet()
    }

    internal fun select(scope: String, id: String, add: Boolean) {
        val ids = withGroup(scope, id)
        if (!add) selection.clear()
        val already = ids.all { selection[key(scope, it)] == true }
        ids.forEach { if (add && already) selection.remove(key(scope, it)) else selection[key(scope, it)] = true }
    }

    internal fun selected(scope: String): List<String> = selection.keys.filter { it.startsWith("$scope/") }.map { it.substringAfter('/') }

    /** Rounds an offset (dp) to the grid, unless the grid is off or Ctrl is held. */
    internal fun snap(value: Offset): Offset {
        val prefs = MenuLayoutStore.prefs()
        if (!prefs.gridSnap || Minecraft.getInstance().hasControlDown()) return value
        val grid = prefs.gridSize.coerceAtLeast(1).toFloat()
        return Offset((value.x / grid).roundToInt() * grid, (value.y / grid).roundToInt() * grid)
    }

    internal fun commitDrag(scope: String) {
        val ids = dragIds
        val delta = dragDelta
        dragIds = emptySet()
        dragDelta = Offset.Zero
        if (ids.isEmpty() || delta == Offset.Zero) return
        MenuLayoutStore.checkpoint(scope)
        for (id in ids) {
            val element = MenuLayoutStore.edit(scope, id)
            val moved = snap(Offset(element.x + delta.x, element.y + delta.y))
            element.x = moved.x
            element.y = moved.y
        }
        MenuLayoutStore.save()
    }

    internal fun setShown(scope: String, ids: Collection<String>, shown: Boolean) {
        MenuLayoutStore.checkpoint(scope)
        ids.forEach { MenuLayoutStore.edit(scope, it).visible = shown }
        MenuLayoutStore.save()
    }

    internal fun regroup(scope: String, ids: Collection<String>, join: Boolean) {
        MenuLayoutStore.checkpoint(scope)
        var name = ""
        if (join) {
            val taken = blocks[scope].orEmpty().keys.mapNotNull { group(scope, it) }.toSet()
            var n = 1
            while ("group-$n" in taken) n++
            name = "group-$n"
        }
        ids.forEach { MenuLayoutStore.edit(scope, it).group = name }
        MenuLayoutStore.save()
    }

    internal fun resetPieces(scope: String, ids: Collection<String>) {
        MenuLayoutStore.checkpoint(scope)
        ids.forEach { MenuLayoutStore.remove(scope, it) }
        MenuLayoutStore.save()
    }

    @JvmStatic
    fun reset(scope: String) {
        MenuLayoutStore.reset(scope)
        selection.clear()
    }
}

private val Accent = Color(0xFF3B6CFF)
private val Hidden = Color(0xFFE05555)

/**
 * A piece of a Compose menu the editor can change. [modifier] places it as usual (align, padding); the layout moves it
 * from there, scales it and can hide it. While editing, an outline on top takes clicks (select, Shift to add), drags
 * (move, with its group), right clicks (more) and the corner handle (scale). [group] puts it in a group by default.
 */
@Composable
fun EditableBlock(scope: String, id: String, label: String, modifier: Modifier = Modifier, group: String? = null, content: @Composable () -> Unit) {
    MenuLayoutStore.revision.intValue
    val editing = MenuLayoutEditor.isEditing(scope)
    val element = MenuLayoutStore.element(scope, id)
    val shown = element?.visible ?: true
    DisposableEffect(scope, id, group) {
        MenuLayoutEditor.blocks.getOrPut(scope) { HashMap() }[id] = group
        MenuLayoutEditor.labels[MenuLayoutEditor.key(scope, id)] = label
        onDispose { MenuLayoutEditor.blocks[scope]?.remove(id) }
    }
    if (!shown && !editing) return
    val density = LocalDensity.current
    val dragging = id in MenuLayoutEditor.dragIds
    val base = Offset(element?.x ?: 0f, element?.y ?: 0f)
    val shownOffset = if (dragging) MenuLayoutEditor.snap(base + MenuLayoutEditor.dragDelta) else base
    val scale = MenuLayoutEditor.liveScale[MenuLayoutEditor.key(scope, id)] ?: element?.scale ?: 1f
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier
            .offset { with(density) { IntOffset(shownOffset.x.dp.roundToPx(), shownOffset.y.dp.roundToPx()) } }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                alpha = if (shown) 1f else 0.35f
            }
            .onGloballyPositioned { coords = it },
    ) {
        content()
        if (!editing) return@Box
        val selected = MenuLayoutEditor.selection[MenuLayoutEditor.key(scope, id)] == true
        Box(
            Modifier
                .matchParentSize()
                .background(if (shown) Color(0x223B6CFF) else Color(0x44401010), RoundedCornerShape(6.dp))
                .border(if (selected) 2.dp else 1.dp, if (selected) Color.White else if (shown) Accent else Hidden, RoundedCornerShape(6.dp))
                .pointerInput(scope, id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press) continue
                            val position = event.changes.firstOrNull()?.position ?: continue
                            val shift = Minecraft.getInstance().hasShiftDown()
                            if (event.buttons.isSecondaryPressed) {
                                if (MenuLayoutEditor.selection[MenuLayoutEditor.key(scope, id)] != true) MenuLayoutEditor.select(scope, id, false)
                                val root = coords?.positionInRoot() ?: Offset.Zero
                                MenuLayoutEditor.menu = MenuLayoutEditor.key(scope, id) to (root + position * scale)
                                event.changes.forEach { it.consume() }
                            } else {
                                MenuLayoutEditor.menu = null
                                if (shift || MenuLayoutEditor.selection[MenuLayoutEditor.key(scope, id)] != true) MenuLayoutEditor.select(scope, id, shift)
                            }
                        }
                    }
                }
                .pointerInput(scope, id) {
                    detectDragGestures(
                        onDragStart = {
                            val ids = MenuLayoutEditor.selected(scope).takeIf { id in it } ?: MenuLayoutEditor.withGroup(scope, id).toList()
                            MenuLayoutEditor.dragIds = ids.toSet()
                            MenuLayoutEditor.dragDelta = Offset.Zero
                        },
                        onDragEnd = { MenuLayoutEditor.commitDrag(scope) },
                        onDragCancel = { MenuLayoutEditor.commitDrag(scope) },
                    ) { change, drag ->
                        change.consume()
                        // the pointer is in this piece's scaled space, the offset in its parent's
                        MenuLayoutEditor.dragDelta += Offset(drag.x * scale / density.density, drag.y * scale / density.density)
                    }
                },
        ) {
            MenuText(if (shown) label else "$label (hidden)", 11.sp, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            if (selected) {
                // drag the corner to scale
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .background(Color.White, RoundedCornerShape(2.dp))
                        .border(1.dp, Color.Black, RoundedCornerShape(2.dp))
                        .pointerInput(scope, id) {
                            var start = 1f
                            var travelled = 0f
                            val size = (coords?.size?.let { maxOf(it.width, it.height) } ?: 100).toFloat().coerceAtLeast(20f)
                            detectDragGestures(
                                onDragStart = {
                                    start = MenuLayoutStore.element(scope, id)?.scale ?: 1f
                                    travelled = 0f
                                },
                                onDragEnd = {
                                    val live = MenuLayoutEditor.liveScale.remove(MenuLayoutEditor.key(scope, id))
                                    if (live != null) {
                                        MenuLayoutStore.checkpoint(scope)
                                        MenuLayoutStore.edit(scope, id).scale = live
                                        MenuLayoutStore.save()
                                    }
                                },
                            ) { change, drag ->
                                change.consume()
                                travelled += (drag.x + drag.y) / 2f
                                val next = (start * (1f + travelled * scale / size))
                                val stepped = if (Minecraft.getInstance().hasControlDown()) next else (next * 20).roundToInt() / 20f
                                MenuLayoutEditor.liveScale[MenuLayoutEditor.key(scope, id)] = stepped.coerceIn(0.25f, 4f)
                            }
                        },
                )
            }
        }
    }
}

/** The bar shown at the top while a Compose menu is being edited, and the right click menu. */
@Composable
fun BoxScope.MenuEditorBar(scope: String) {
    if (!MenuLayoutEditor.isEditing(scope)) return
    MenuLayoutStore.revision.intValue
    val prefs = MenuLayoutStore.prefs()
    Row(
        Modifier
            .align(Alignment.TopCenter)
            // under the menus' top bars, so it never covers their buttons
            .padding(top = 64.dp)
            .background(Color(0xE0101216), RoundedCornerShape(8.dp))
            .border(1.dp, Accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuText("Drag to move (Ctrl: no snap), corner to scale, right click for more", 12.sp, color = Color.White)
        TextButton(text = if (prefs.gridSnap) "Grid ${prefs.gridSize}" else "Grid off", size = ControlSize.Compact, style = ControlStyle.Secondary, enabled = true, onClick = {
            when {
                !prefs.gridSnap -> {
                    prefs.gridSnap = true
                    prefs.gridSize = 4
                }
                prefs.gridSize < 16 -> prefs.gridSize *= 2
                else -> prefs.gridSnap = false
            }
            MenuLayoutStore.save()
        })
        TextButton(text = "Undo", size = ControlSize.Compact, style = ControlStyle.Secondary, enabled = MenuLayoutStore.canUndo(scope), onClick = { MenuLayoutStore.undo(scope) })
        TextButton(text = "Redo", size = ControlSize.Compact, style = ControlStyle.Secondary, enabled = MenuLayoutStore.canRedo(scope), onClick = { MenuLayoutStore.redo(scope) })
        TextButton(text = "Reset all", size = ControlSize.Compact, style = ControlStyle.Secondary, enabled = true, onClick = { MenuLayoutEditor.reset(scope) })
        TextButton(text = "Done", size = ControlSize.Compact, style = ControlStyle.Primary, enabled = true, onClick = { MenuLayoutEditor.stop() })
    }
    val (target, at) = MenuLayoutEditor.menu ?: return
    if (!target.startsWith("$scope/")) return
    val ids = MenuLayoutEditor.selected(scope).ifEmpty { listOf(target.substringAfter('/')) }
    val allHidden = ids.none { MenuLayoutStore.element(scope, it)?.visible ?: true }
    val groups = ids.map { MenuLayoutEditor.group(scope, it) }.distinct()
    val join = ids.size >= 2 && (groups.size > 1 || groups.first() == null)
    Column(
        Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(at.x.roundToInt(), at.y.roundToInt()) }
            .width(170.dp)
            .background(Color(0xF8101216), RoundedCornerShape(6.dp))
            .border(1.dp, Accent, RoundedCornerShape(6.dp))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        @Composable
        fun item(text: String, enabled: Boolean = true, action: () -> Unit) {
            TextButton(text = text, size = ControlSize.Compact, style = ControlStyle.Secondary, enabled = enabled, onClick = {
                MenuLayoutEditor.menu = null
                action()
            })
        }
        item(if (allHidden) "Show" else "Hide") { MenuLayoutEditor.setShown(scope, ids, allHidden) }
        if (join) {
            item("Group") { MenuLayoutEditor.regroup(scope, ids, true) }
        } else {
            item("Ungroup", groups.any { it != null }) { MenuLayoutEditor.regroup(scope, ids, false) }
        }
        item("Reset this element") { MenuLayoutEditor.resetPieces(scope, ids) }
        item("Close") {}
    }
}
