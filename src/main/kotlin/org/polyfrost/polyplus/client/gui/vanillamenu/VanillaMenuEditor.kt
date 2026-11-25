package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.VanillaMenuLayout
import org.polyfrost.polyplus.client.gui.PauseMenuPanel
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The vanilla menu's editor, a HUD editor for the title screen: select pieces (click, Shift-click, drag a box), move
 * them, resize them with the handles, scale them with Ctrl+scroll, snap to a grid, to other pieces and to the middle
 * of the screen (hold Ctrl to place freely), group and ungroup, hide and show, add pictures, text, sliders and
 * buttons, undo and redo. Everything is saved to [MenuLayoutStore] as it changes.
 */
internal object VanillaMenuEditor {
    private const val SCOPE = MenuLayoutStore.VANILLA
    private const val HANDLE = 4
    private const val SNAP = 4
    private const val PANEL_WIDTH = 150
    private const val ROW_HEIGHT = 12
    private const val BAR_HEIGHT = 16
    private const val DRAG_START = 2
    private const val SCALE_STEP = 0.1f
    private const val SCALE_MERGE_NANOS = 700_000_000L

    private const val ACCENT = 0xFF3B6CFF.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val MUTED = 0xFF9A9CA3.toInt()
    private const val RED = 0xFFE05555.toInt()
    private const val GUIDE = 0xFFFF4FD8.toInt()
    private const val SURFACE = 0xF0101216.toInt()
    private const val SURFACE_HOVER = 0xF0262A33.toInt()

    private val font: Font get() = Minecraft.getInstance().font

    private enum class Mode { NONE, MOVE, RESIZE, MARQUEE, BAR, ROW }

    val selection = LinkedHashSet<String>()
    private var mode = Mode.NONE
    private var pressX = 0
    private var pressY = 0
    private var moved = false
    private var handle = -1
    private val start = HashMap<String, IntArray>()
    private val guides = ArrayList<Pair<Boolean, Int>>()
    private var marquee: IntArray? = null
    private var barStart = IntArray(2)
    private var rowDrag: String? = null
    private var rowDrop = -1
    private var panelScroll = 0
    private val folded = HashSet<String>()
    private var popup: Popup? = null
    private var confirm: Confirm? = null
    private var message: String? = null
    private var messageUntil = 0L
    private var lastScale: Pair<String, Long>? = null

    private class Item(val label: String, val enabled: Boolean = true, val action: () -> Unit)

    private class Popup(val x: Int, val y: Int, val items: List<Item?>) {
        val width = (items.filterNotNull().maxOfOrNull { Minecraft.getInstance().font.width(it.label) } ?: 40) + 16
        val height = items.sumOf { if (it == null) 5 else ROW_HEIGHT + 2 } + 4
    }

    private class Confirm(val text: String, val yes: String, val action: () -> Unit)

    private class Rect(val x: Int, val y: Int, val w: Int, val h: Int) {
        val right get() = x + w
        val bottom get() = y + h
        fun contains(px: Double, py: Double) = px >= x && px < x + w && py >= y && py < y + h
        fun intersects(o: Rect) = x < o.right && right > o.x && y < o.bottom && bottom > o.y
    }

    private fun rect(piece: MenuPiece) = Rect(piece.left, piece.top, piece.width, piece.height)

    private fun say(text: String) {
        message = text
        messageUntil = System.currentTimeMillis() + 2500
    }

    private fun prefs() = MenuLayoutStore.prefs()

    /** Pieces you can see in the editor (every piece, hidden ones faded), the one on top first. */
    private fun visiblePieces(state: VanillaTitleMenu.State) = state.pieces.values.filter { it.widget.visible }.sortedByDescending { it.z }

    private fun selected(state: VanillaTitleMenu.State) = selection.mapNotNull { state.pieces[it] }

    /** A piece and everything in its group. */
    private fun withGroup(state: VanillaTitleMenu.State, piece: MenuPiece): List<MenuPiece> {
        val group = piece.group ?: return listOf(piece)
        return state.pieces.values.filter { it.group == group }
    }

    private fun bounds(pieces: List<MenuPiece>): Rect? {
        if (pieces.isEmpty()) return null
        val x0 = pieces.minOf { it.left }
        val y0 = pieces.minOf { it.top }
        return Rect(x0, y0, pieces.maxOf { it.right } - x0, pieces.maxOf { it.bottom } - y0)
    }

    private fun changed(state: VanillaTitleMenu.State, rebuild: Boolean = false) {
        MenuLayoutStore.save()
        if (rebuild) VanillaTitleMenu.rebuild(state.screen) else VanillaTitleMenu.applyLayout(state)
    }

    // ---------------------------------------------------------------- the bar

    private class BarItem(val label: String, val tooltip: String, val on: Boolean = false, val enabled: Boolean = true, val action: (BarItem, Rect) -> Unit)

    private fun barItems(state: VanillaTitleMenu.State): List<BarItem> = listOf(
        BarItem("≡", "Drag to move this bar") { _, _ -> },
        BarItem("Preset ▾", "Start from a premade layout, or share yours") { _, r -> presetMenu(state, r) },
        BarItem(if (prefs().gridSnap) "Grid ☑" else "Grid ☐", "Snap to a grid (hold Ctrl to place freely)", on = prefs().gridSnap) { _, _ ->
            prefs().gridSnap = !prefs().gridSnap
            MenuLayoutStore.save()
        },
        BarItem("${prefs().gridSize}px", "Grid size") { _, _ ->
            prefs().gridSize = when (prefs().gridSize) {
                4 -> 8
                8 -> 16
                else -> 4
            }
            MenuLayoutStore.save()
        },
        BarItem("Undo", "Ctrl+Z", enabled = MenuLayoutStore.canUndo(SCOPE)) { _, _ -> undo(state) },
        BarItem("Redo", "Ctrl+Y", enabled = MenuLayoutStore.canRedo(SCOPE)) { _, _ -> redo(state) },
        BarItem("Elements", "Every piece on the menu, and pieces to add", on = prefs().panelOpen) { _, _ ->
            prefs().panelOpen = !prefs().panelOpen
            MenuLayoutStore.save()
        },
        BarItem("Reset all", "Back to the preset, without your changes") { _, _ ->
            confirm = Confirm("Reset the whole menu to the ${VanillaTitleMenu.presetName(PolyPlusMainMenuConfig.vanillaMenuLayout)} preset?", "Reset") {
                MenuLayoutStore.reset(SCOPE)
                selection.clear()
                VanillaTitleMenu.rebuild(state.screen)
            }
        },
        BarItem("Done", "Save and leave the editor (Esc)") { _, _ -> finish(state) },
    )

    private fun barRects(state: VanillaTitleMenu.State, items: List<BarItem>): List<Rect> {
        val widths = items.map { font.width(it.label) + 10 }
        val total = widths.sum() + (items.size - 1) * 2
        val screen = state.screen
        val x = prefs().barX.takeIf { it != Int.MIN_VALUE } ?: ((screen.width - total) / 2)
        val y = prefs().barY.takeIf { it != Int.MIN_VALUE } ?: 2
        val bx = x.coerceIn(0, (screen.width - total).coerceAtLeast(0))
        val by = y.coerceIn(0, (screen.height - BAR_HEIGHT).coerceAtLeast(0))
        var cx = bx
        return widths.map { w -> Rect(cx, by, w, BAR_HEIGHT).also { cx += w + 2 } }
    }

    private fun presetMenu(state: VanillaTitleMenu.State, under: Rect) {
        val items = VanillaMenuLayout.entries.map { layout ->
            val name = VanillaTitleMenu.presetName(layout)
            Item((if (layout == PolyPlusMainMenuConfig.vanillaMenuLayout) "● " else "   ") + name) {
                confirm = Confirm("Replace current layout with $name?", "Replace") {
                    MenuLayoutStore.reset(SCOPE)
                    PolyPlusMainMenuConfig.vanillaMenuLayout = layout
                    PolyPlusMainMenuConfig.save()
                    selection.clear()
                    VanillaTitleMenu.rebuild(state.screen)
                }
            }
        } + listOf(
            null,
            Item("Copy layout code") {
                Minecraft.getInstance().keyboardHandler.clipboard = MenuLayoutStore.exportCode(SCOPE)
                say("Layout code copied, share it with anyone")
            },
            Item("Paste layout code") {
                val code = Minecraft.getInstance().keyboardHandler.clipboard
                if (MenuLayoutStore.importCode(SCOPE, code)) {
                    selection.clear()
                    VanillaTitleMenu.rebuild(state.screen)
                    say("Layout pasted (Ctrl+Z to undo)")
                } else {
                    say("No layout code on the clipboard")
                }
            },
        )
        popup = Popup(under.x, under.bottom + 2, items)
    }

    private fun finish(state: VanillaTitleMenu.State) {
        selection.clear()
        popup = null
        confirm = null
        MenuLayoutEditor.stop()
        MenuLayoutStore.save()
        VanillaTitleMenu.rebuild(state.screen)
    }

    private fun undo(state: VanillaTitleMenu.State) {
        if (MenuLayoutStore.undo(SCOPE)) VanillaTitleMenu.rebuild(state.screen) else say("Nothing to undo")
    }

    private fun redo(state: VanillaTitleMenu.State) {
        if (MenuLayoutStore.redo(SCOPE)) VanillaTitleMenu.rebuild(state.screen) else say("Nothing to redo")
    }

    // ---------------------------------------------------------------- the elements panel

    private sealed class Row {
        class Header(val text: String) : Row()
        class Group(val name: String, val members: List<MenuPiece>) : Row()
        class Piece(val piece: MenuPiece, val indent: Boolean) : Row()
        class Add(val kind: MenuAddedElements.Kind) : Row()
    }

    private fun panelRect(state: VanillaTitleMenu.State) = Rect(state.screen.width - PANEL_WIDTH, 0, PANEL_WIDTH, state.screen.height)

    private fun rows(state: VanillaTitleMenu.State): List<Row> = buildList {
        add(Row.Header("On the menu"))
        val seen = HashSet<String>()
        for (piece in state.pieces.values.sortedByDescending { it.z }) {
            val group = piece.group
            if (group == null) {
                add(Row.Piece(piece, false))
                continue
            }
            if (!seen.add(group)) continue
            val members = state.pieces.values.filter { it.group == group }.sortedByDescending { it.z }
            add(Row.Group(group, members))
            if (group !in folded) members.forEach { add(Row.Piece(it, true)) }
        }
        add(Row.Header("Add element"))
        MenuAddedElements.kinds.filter { it.available(state) }.forEach { add(Row.Add(it)) }
    }

    private fun rowTop(panel: Rect, index: Int) = panel.y + 18 + index * ROW_HEIGHT - panelScroll

    private fun drawPanel(state: VanillaTitleMenu.State, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val panel = panelRect(state)
        graphics.fill(panel.x, panel.y, panel.right, panel.bottom, SURFACE)
        graphics.fill(panel.x, panel.y, panel.x + 1, panel.bottom, ACCENT)
        graphics.text(font, "Elements", panel.x + 6, panel.y + 5, WHITE)
        graphics.text(font, "×", panel.right - 10, panel.y + 5, if (Rect(panel.right - 14, panel.y, 14, 16).contains(mouseX.toDouble(), mouseY.toDouble())) WHITE else MUTED)
        graphics.enableScissor(panel.x, panel.y + 16, panel.right, panel.bottom)
        rows(state).forEachIndexed { index, row ->
            val top = rowTop(panel, index)
            if (top + ROW_HEIGHT < panel.y + 16 || top > panel.bottom) return@forEachIndexed
            val hovered = mouseX >= panel.x && mouseY >= top && mouseY < top + ROW_HEIGHT && mouseY >= panel.y + 16
            when (row) {
                is Row.Header -> {
                    graphics.text(font, row.text.uppercase(), panel.x + 6, top + 3, ACCENT, false)
                }
                is Row.Group -> {
                    val selectedRow = row.members.all { it.id in selection }
                    if (selectedRow || hovered) graphics.fill(panel.x + 1, top, panel.right, top + ROW_HEIGHT, if (selectedRow) 0x553B6CFF else 0x22FFFFFF)
                    val shown = row.members.any { it.shown }
                    graphics.text(font, if (shown) "◉" else "○", panel.x + 4, top + 2, if (shown) WHITE else RED, false)
                    graphics.text(font, if (row.name in folded) "▸" else "▾", panel.x + 14, top + 2, MUTED, false)
                    graphics.text(font, font.plainSubstrByWidth("Group: ${row.name}", PANEL_WIDTH - 30), panel.x + 22, top + 2, WHITE, false)
                }
                is Row.Piece -> {
                    val piece = row.piece
                    val selectedRow = piece.id in selection
                    if (rowDrag == piece.id) graphics.fill(panel.x + 1, top, panel.right, top + ROW_HEIGHT, 0x333B6CFF)
                    else if (selectedRow || hovered) graphics.fill(panel.x + 1, top, panel.right, top + ROW_HEIGHT, if (selectedRow) 0x553B6CFF else 0x22FFFFFF)
                    graphics.text(font, if (piece.shown) "◉" else "○", panel.x + 4, top + 2, if (piece.shown) WHITE else RED, false)
                    val x = panel.x + if (row.indent) 22 else 14
                    val name = piece.label + if (piece.builtin) "" else "  +"
                    graphics.text(font, font.plainSubstrByWidth(name, panel.right - x - 4), x, top + 2, if (piece.shown) WHITE else MUTED, false)
                }
                is Row.Add -> {
                    if (hovered) graphics.fill(panel.x + 1, top, panel.right, top + ROW_HEIGHT, 0x22FFFFFF)
                    graphics.text(font, "+", panel.x + 5, top + 2, ACCENT, false)
                    graphics.text(font, font.plainSubstrByWidth(row.kind.label, PANEL_WIDTH - 20), panel.x + 14, top + 2, WHITE, false)
                }
            }
        }
        if (rowDrag != null && rowDrop >= 0) {
            val y = rowTop(panel, rowDrop)
            graphics.fill(panel.x + 2, y - 1, panel.right - 2, y + 1, ACCENT)
        }
        graphics.disableScissor()
    }

    private fun panelClick(state: VanillaTitleMenu.State, x: Double, y: Double, event: MouseButtonEvent) {
        val panel = panelRect(state)
        if (y < panel.y + 16) {
            if (x >= panel.right - 14) {
                prefs().panelOpen = false
                MenuLayoutStore.save()
            }
            return
        }
        val list = rows(state)
        val index = ((y - panel.y - 18 + panelScroll) / ROW_HEIGHT).toInt()
        val row = list.getOrNull(index) ?: return
        val eye = x < panel.x + 13
        when (row) {
            is Row.Header -> Unit
            is Row.Group -> when {
                eye -> setShown(state, row.members, row.members.none { it.shown })
                x < panel.x + 21 -> if (!folded.remove(row.name)) folded.add(row.name)
                else -> {
                    if (!event.hasShiftDown()) selection.clear()
                    row.members.forEach { selection.add(it.id) }
                }
            }
            is Row.Piece -> when {
                eye -> setShown(state, listOf(row.piece), !row.piece.shown)
                else -> {
                    if (event.hasShiftDown()) {
                        if (!selection.remove(row.piece.id)) selection.add(row.piece.id)
                    } else {
                        selection.clear()
                        selection.add(row.piece.id)
                    }
                    mode = Mode.ROW
                    rowDrag = row.piece.id
                    rowDrop = -1
                    pressY = y.toInt()
                }
            }
            is Row.Add -> {
                val id = MenuAddedElements.add(state, row.kind)
                selection.clear()
                selection.add(id)
                if (row.kind.type == "image" && MenuImages.files().isEmpty()) say("Put pictures in config/polyplus/menu-images, then pick one in Properties")
                VanillaTitleMenu.rebuild(state.screen)
            }
        }
    }

    /** Drops a dragged row: the piece moves to that place in the draw order. */
    private fun dropRow(state: VanillaTitleMenu.State) {
        val id = rowDrag ?: return
        rowDrag = null
        if (rowDrop < 0) return
        val list = rows(state)
        val order = state.pieces.values.sortedByDescending { it.z }.toMutableList()
        val dragged = state.pieces[id] ?: return
        // the piece that will end up right under the dropped one
        val below = list.drop(rowDrop).firstNotNullOfOrNull { (it as? Row.Piece)?.piece?.takeIf { p -> p.id != id } }
        order.remove(dragged)
        val at = below?.let { order.indexOf(it) }?.takeIf { it >= 0 } ?: order.size
        order.add(at, dragged)
        MenuLayoutStore.checkpoint(SCOPE)
        order.forEachIndexed { index, piece -> MenuLayoutStore.edit(SCOPE, piece.id).z = (order.size - index) * 10 }
        changed(state)
    }

    // ---------------------------------------------------------------- actions on the selection

    private fun setShown(state: VanillaTitleMenu.State, pieces: List<MenuPiece>, shown: Boolean) {
        if (pieces.isEmpty()) return
        MenuLayoutStore.checkpoint(SCOPE)
        pieces.forEach { MenuLayoutStore.edit(SCOPE, it.id).visible = shown }
        changed(state)
    }

    private fun group(state: VanillaTitleMenu.State) {
        val pieces = selected(state)
        if (pieces.size < 2) return
        MenuLayoutStore.checkpoint(SCOPE)
        var n = 1
        val taken = state.pieces.values.mapNotNull { it.group }.toSet()
        while ("group-$n" in taken) n++
        pieces.forEach { MenuLayoutStore.edit(SCOPE, it.id).group = "group-$n" }
        changed(state)
        say("Grouped as group-$n")
    }

    private fun ungroup(state: VanillaTitleMenu.State) {
        val pieces = selected(state).filter { it.group != null }
        if (pieces.isEmpty()) return
        MenuLayoutStore.checkpoint(SCOPE)
        pieces.forEach { MenuLayoutStore.edit(SCOPE, it.id).group = "" }
        changed(state)
        say("Ungrouped, each piece moves on its own now")
    }

    private fun toFront(state: VanillaTitleMenu.State, front: Boolean) {
        val pieces = selected(state)
        if (pieces.isEmpty()) return
        MenuLayoutStore.checkpoint(SCOPE)
        val edge = if (front) (state.pieces.values.maxOf { it.z } + 10) else (state.pieces.values.minOf { it.z } - 10 * pieces.size)
        pieces.sortedBy { it.z }.forEachIndexed { index, piece -> MenuLayoutStore.edit(SCOPE, piece.id).z = edge + index * 10 }
        changed(state)
    }

    private fun resetPieces(state: VanillaTitleMenu.State) {
        val pieces = selected(state)
        if (pieces.isEmpty()) return
        MenuLayoutStore.checkpoint(SCOPE)
        for (piece in pieces) {
            if (piece.builtin) {
                MenuLayoutStore.remove(SCOPE, piece.id)
            } else {
                val element = MenuLayoutStore.edit(SCOPE, piece.id)
                element.scale = 1f
                element.opacity = 1f
                MenuAddedElements.kind(piece.type)?.let {
                    element.w = it.w.toFloat()
                    element.h = it.h.toFloat()
                }
            }
        }
        changed(state)
    }

    private fun delete(state: VanillaTitleMenu.State) {
        val pieces = selected(state)
        if (pieces.isEmpty()) return
        MenuLayoutStore.checkpoint(SCOPE)
        val (added, builtin) = pieces.partition { !it.builtin }
        added.forEach { MenuLayoutStore.remove(SCOPE, it.id) }
        // built-in pieces can't be deleted, only hidden, so they can always come back
        builtin.forEach { MenuLayoutStore.edit(SCOPE, it.id).visible = false }
        if (builtin.isNotEmpty()) say("Built-in pieces are hidden instead, show them again in Elements")
        selection.clear()
        MenuLayoutStore.save()
        VanillaTitleMenu.rebuild(state.screen)
    }

    private fun duplicate(state: VanillaTitleMenu.State) {
        val pieces = selected(state).filter { !it.builtin }
        if (pieces.isEmpty()) return
        MenuLayoutStore.checkpoint(SCOPE)
        val ids = pieces.mapNotNull { MenuAddedElements.duplicate(state, it) }
        selection.clear()
        selection.addAll(ids)
        MenuLayoutStore.save()
        VanillaTitleMenu.rebuild(state.screen)
    }

    private fun properties(state: VanillaTitleMenu.State, piece: MenuPiece) {
        popup = null
        ClientPlatform.setScreen(ElementPropertiesScreen(state, piece))
    }

    private fun contextMenu(state: VanillaTitleMenu.State, x: Int, y: Int) {
        val pieces = selected(state)
        if (pieces.isEmpty()) return
        val allHidden = pieces.none { it.shown }
        val anyAdded = pieces.any { !it.builtin }
        val items = listOf(
            Item(if (allHidden) "Show" else "Hide") { setShown(state, pieces, allHidden) },
            if (pieces.size >= 2 && pieces.map { it.group }.distinct().let { it.size > 1 || it.first() == null }) {
                Item("Group") { group(state) }
            } else {
                Item("Ungroup", enabled = pieces.any { it.group != null }) { ungroup(state) }
            },
            null,
            Item("Bring to front") { toFront(state, true) },
            Item("Send to back") { toFront(state, false) },
            null,
            Item("Duplicate", enabled = anyAdded) { duplicate(state) },
            Item("Reset this element") { resetPieces(state) },
            Item("Delete", enabled = anyAdded) { delete(state) },
            null,
            Item("Properties...", enabled = pieces.size == 1) { properties(state, pieces.first()) },
        )
        val popup = Popup(x, y, items)
        this.popup = Popup(x.coerceAtMost(state.screen.width - popup.width - 2), y.coerceAtMost(state.screen.height - popup.height - 2), items)
    }

    // ---------------------------------------------------------------- snapping

    private class Snap(val delta: Int, val line: Int)

    /** The smallest move (within [SNAP]) that puts one of [edges] on one of [targets]. */
    private fun nearest(edges: List<Int>, targets: List<Int>): Snap? {
        var best: Snap? = null
        for (edge in edges) for (target in targets) {
            val delta = target - edge
            if (abs(delta) <= SNAP && (best == null || abs(delta) < abs(best.delta))) best = Snap(delta, target)
        }
        return best
    }

    private fun targets(state: VanillaTitleMenu.State, vertical: Boolean): List<Int> {
        val screen = state.screen
        val size = if (vertical) screen.width else screen.height
        return buildList {
            add(0)
            add(size / 2)
            add(size)
            for (piece in state.pieces.values) {
                if (piece.id in selection || !piece.widget.visible) continue
                if (vertical) {
                    add(piece.left)
                    add(piece.left + piece.width / 2)
                    add(piece.right)
                } else {
                    add(piece.top)
                    add(piece.top + piece.height / 2)
                    add(piece.bottom)
                }
            }
        }
    }

    private fun snapping(): Boolean = !Minecraft.getInstance().hasControlDown()

    /** How far to move a box at [x] (or [y]) of [size] so it snaps; adds the guide it snapped to. */
    private fun snapAxis(state: VanillaTitleMenu.State, vertical: Boolean, pos: Int, size: Int): Int {
        if (!snapping()) return 0
        nearest(listOf(pos, pos + size / 2, pos + size), targets(state, vertical))?.let {
            guides.add(vertical to it.line)
            return it.delta
        }
        if (prefs().gridSnap) {
            val grid = prefs().gridSize.coerceAtLeast(1)
            return Math.round(pos / grid.toFloat()) * grid - pos
        }
        return 0
    }

    /** Where a moving edge ends up after snapping. */
    private fun snapEdge(state: VanillaTitleMenu.State, vertical: Boolean, edge: Int): Int {
        if (!snapping()) return edge
        nearest(listOf(edge), targets(state, vertical))?.let {
            guides.add(vertical to it.line)
            return it.line
        }
        if (prefs().gridSnap) {
            val grid = prefs().gridSize.coerceAtLeast(1)
            return Math.round(edge / grid.toFloat()) * grid
        }
        return edge
    }

    // ---------------------------------------------------------------- frame

    fun frameStart(state: VanillaTitleMenu.State, mouseX: Int, mouseY: Int) {
        if (mode == Mode.NONE) return
        val down = PauseMenuPanel.leftDown()
        val dx = mouseX - pressX
        val dy = mouseY - pressY
        if (!moved && (abs(dx) > DRAG_START || abs(dy) > DRAG_START)) moved = true
        guides.clear()
        when (mode) {
            Mode.MOVE -> if (moved) movePieces(state, dx, dy)
            Mode.RESIZE -> if (moved) resizePiece(state, dx, dy)
            Mode.MARQUEE -> marquee = intArrayOf(minOf(pressX, mouseX), minOf(pressY, mouseY), abs(dx), abs(dy))
            Mode.BAR -> {
                prefs().barX = barStart[0] + dx
                prefs().barY = barStart[1] + dy
            }
            Mode.ROW -> if (moved) {
                val panel = panelRect(state)
                rowDrop = ((mouseY - panel.y - 18 + panelScroll + ROW_HEIGHT / 2) / ROW_HEIGHT).coerceIn(1, rows(state).size)
            }
            Mode.NONE -> Unit
        }
        if (!down) release(state)
    }

    private fun movePieces(state: VanillaTitleMenu.State, dx: Int, dy: Int) {
        val pieces = selected(state)
        val box = bounds(pieces) ?: return
        // where the selection's box would be, then snapped as a whole
        val sx = start.values.minOf { it[0] }
        val sy = start.values.minOf { it[1] }
        val snapX = snapAxis(state, true, sx + dx, box.w)
        val snapY = snapAxis(state, false, sy + dy, box.h)
        for (piece in pieces) {
            val from = start[piece.id] ?: continue
            piece.widget.setPosition(from[0] + dx + snapX, from[1] + dy + snapY)
            piece.layoutExtras(piece)
        }
    }

    private fun resizePiece(state: VanillaTitleMenu.State, dx: Int, dy: Int) {
        val piece = selected(state).singleOrNull() ?: return
        val from = start[piece.id] ?: return
        val scale = piece.scale
        val x0 = from[0]
        val y0 = from[1]
        val w0 = (from[2] * scale).roundToInt()
        val h0 = (from[3] * scale).roundToInt()
        val mc = Minecraft.getInstance()
        val center = mc.hasAltDown()
        val left = handle in intArrayOf(0, 6, 7)
        val right = handle in intArrayOf(2, 3, 4)
        val top = handle in intArrayOf(0, 1, 2)
        val bottom = handle in intArrayOf(4, 5, 6)
        var l = x0
        var t = y0
        var r = x0 + w0
        var b = y0 + h0
        if (left) l = snapEdge(state, true, x0 + dx)
        if (right) r = snapEdge(state, true, x0 + w0 + dx)
        if (top) t = snapEdge(state, false, y0 + dy)
        if (bottom) b = snapEdge(state, false, y0 + h0 + dy)
        if (center) {
            // from the middle: the opposite side moves the other way
            if (left) r = x0 + w0 + (x0 - l)
            if (right) l = x0 - (r - x0 - w0)
            if (top) b = y0 + h0 + (y0 - t)
            if (bottom) t = y0 - (b - y0 - h0)
        }
        var w = (r - l).coerceAtLeast(4)
        var h = (b - t).coerceAtLeast(4)
        if (mc.hasShiftDown() && w0 > 0 && h0 > 0) {
            // keep the shape: the side that changed most decides
            val ratio = w0.toFloat() / h0
            if ((left || right) && (!(top || bottom) || abs(w - w0) / w0.toFloat() >= abs(h - h0) / h0.toFloat())) h = (w / ratio).roundToInt().coerceAtLeast(4)
            else w = (h * ratio).roundToInt().coerceAtLeast(4)
            if (center) {
                l = x0 + (w0 - w) / 2
                t = y0 + (h0 - h) / 2
            } else {
                if (left) l = x0 + w0 - w
                if (top) t = y0 + h0 - h
                if (!left && !right) l = x0 + (w0 - w) / 2
                if (!top && !bottom) t = y0 + (h0 - h) / 2
            }
        }
        if (!center && left) l = minOf(l, x0 + w0 - 4)
        if (!center && top) t = minOf(t, y0 + h0 - 4)
        piece.widget.setRectangle((w / scale).roundToInt().coerceAtLeast(2), (h / scale).roundToInt().coerceAtLeast(2), l, t)
        piece.layoutExtras(piece)
    }

    private fun release(state: VanillaTitleMenu.State) {
        val was = mode
        mode = Mode.NONE
        guides.clear()
        when (was) {
            Mode.MOVE, Mode.RESIZE -> {
                if (!moved) {
                    MenuLayoutStore.dropCheckpoint(SCOPE)
                    return
                }
                for (piece in selected(state)) VanillaTitleMenu.store(state, piece, piece.widget.x, piece.widget.y, piece.widget.width, piece.widget.height)
                changed(state)
            }
            Mode.MARQUEE -> {
                val box = marquee
                marquee = null
                if (box == null || !moved) return
                val area = Rect(box[0], box[1], box[2], box[3])
                state.pieces.values.filter { it.widget.visible && area.intersects(rect(it)) }.forEach { piece ->
                    withGroup(state, piece).forEach { selection.add(it.id) }
                }
            }
            Mode.BAR -> MenuLayoutStore.save()
            Mode.ROW -> if (moved) dropRow(state) else rowDrag = null
            Mode.NONE -> Unit
        }
    }

    // ---------------------------------------------------------------- drawing

    private fun handles(r: Rect): List<Rect> {
        val cx = r.x + r.w / 2
        val cy = r.y + r.h / 2
        val half = HANDLE / 2
        return listOf(
            r.x to r.y, cx to r.y, r.right to r.y, r.right to cy,
            r.right to r.bottom, cx to r.bottom, r.x to r.bottom, r.x to cy,
        ).map { (x, y) -> Rect(x - half, y - half, HANDLE, HANDLE) }
    }

    fun draw(state: VanillaTitleMenu.State, graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        selection.retainAll(state.pieces.keys)
        // the editor's own pieces always end up on top of the menu
        graphics.nextStratum()
        val mx = mouseX.toDouble()
        val my = mouseY.toDouble()
        val overUi = overEditorUi(state, mx, my)
        val hover = if (overUi || mode != Mode.NONE) null else visiblePieces(state).firstOrNull { rect(it).contains(mx, my) }
        for (piece in state.pieces.values) {
            if (!piece.widget.visible) continue
            val r = rect(piece)
            val selected = piece.id in selection
            val color = when {
                selected -> WHITE
                !piece.shown -> RED
                hover != null && withGroup(state, hover).contains(piece) -> 0xFF8FB0FF.toInt()
                else -> 0x663B6CFF
            }
            if (!piece.shown) graphics.fill(r.x, r.y, r.right, r.bottom, 0x40401010)
            graphics.outline(r.x - 1, r.y - 1, r.w + 2, r.h + 2, color)
            if (!piece.shown && r.w > 30) graphics.text(font, "hidden", r.x + 2, r.y + 2, 0xFFFF8888.toInt())
        }
        val chosen = selected(state)
        // a group shows its box around all of its pieces
        if (chosen.size > 1) bounds(chosen)?.let { graphics.outline(it.x - 3, it.y - 3, it.w + 6, it.h + 6, 0xAAFFFFFF.toInt()) }
        chosen.singleOrNull()?.let { piece ->
            val r = rect(piece)
            handles(r).forEach { h ->
                graphics.fill(h.x, h.y, h.right, h.bottom, WHITE)
                graphics.outline(h.x, h.y, h.w, h.h, 0xFF000000.toInt())
            }
            val info = "${piece.label}  ${piece.width}×${piece.height}" + if (piece.scale != 1f) "  ×${"%.2f".format(piece.scale)}" else ""
            val ty = if (r.y > 12) r.y - 11 else r.bottom + 3
            graphics.fill(r.x - 1, ty - 1, r.x + font.width(info) + 3, ty + 9, 0xC0000000.toInt())
            graphics.text(font, info, r.x + 1, ty, WHITE, false)
        }
        for ((vertical, line) in guides) {
            if (vertical) graphics.fill(line, 0, line + 1, state.screen.height, GUIDE)
            else graphics.fill(0, line, state.screen.width, line + 1, GUIDE)
        }
        marquee?.let { graphics.fill(it[0], it[1], it[0] + it[2], it[1] + it[3], 0x303B6CFF); graphics.outline(it[0], it[1], it[2], it[3], ACCENT) }

        if (prefs().panelOpen) drawPanel(state, graphics, mouseX, mouseY)

        val items = barItems(state)
        val rects = barRects(state, items)
        items.forEachIndexed { index, item ->
            val r = rects[index]
            val over = r.contains(mx, my)
            graphics.fill(r.x, r.y, r.right, r.bottom, if (over && item.enabled) SURFACE_HOVER else SURFACE)
            graphics.outline(r.x, r.y, r.w, r.h, if (item.on) ACCENT else 0xFF2E3138.toInt())
            graphics.text(font, item.label, r.x + 5, r.y + 4, if (item.enabled) WHITE else 0xFF5A5D66.toInt(), false)
            if (over && popup == null) {
                graphics.fill(r.x, r.bottom + 2, r.x + font.width(item.tooltip) + 6, r.bottom + 14, 0xE0000000.toInt())
                graphics.text(font, item.tooltip, r.x + 3, r.bottom + 4, MUTED, false)
            }
        }
        val hintY = rects.first().bottom + 3
        val hint = message?.takeIf { System.currentTimeMillis() < messageUntil }
            ?: if (rects.none { it.contains(mx, my) }) "Right click a piece for more. Shift: add / keep shape. Alt: one piece of a group. Ctrl: no snap, Ctrl+scroll: scale." else null
        if (hint != null) {
            val hx = rects.first().x
            graphics.fill(hx, hintY, hx + font.width(hint) + 6, hintY + 12, 0xC0000000.toInt())
            graphics.text(font, hint, hx + 3, hintY + 2, if (message != null && System.currentTimeMillis() < messageUntil) WHITE else MUTED, false)
        }

        popup?.let { drawPopup(graphics, it, mouseX, mouseY) }
        confirm?.let { drawConfirm(state, graphics, it, mouseX, mouseY) }

        // the cursor says what a press would do
        val handleIndex = chosen.singleOrNull()?.let { piece -> handles(rect(piece)).indexOfFirst { it.contains(mx, my) } } ?: -1
        when {
            mode == Mode.RESIZE || handleIndex >= 0 -> graphics.requestCursor(
                when (if (mode == Mode.RESIZE) handle else handleIndex) {
                    1, 5 -> CursorTypes.RESIZE_NS
                    3, 7 -> CursorTypes.RESIZE_EW
                    else -> CursorTypes.RESIZE_ALL
                },
            )
            mode == Mode.MOVE || mode == Mode.BAR -> graphics.requestCursor(CursorTypes.RESIZE_ALL)
            hover != null -> graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
    }

    private fun popupRows(popup: Popup): List<Pair<Item?, Rect>> {
        var y = popup.y + 2
        return popup.items.map { item ->
            val h = if (item == null) 5 else ROW_HEIGHT + 2
            (item to Rect(popup.x, y, popup.width, h)).also { y += h }
        }
    }

    private fun drawPopup(graphics: GuiGraphicsExtractor, popup: Popup, mouseX: Int, mouseY: Int) {
        graphics.fill(popup.x, popup.y, popup.x + popup.width, popup.y + popup.height, 0xF8101216.toInt())
        graphics.outline(popup.x, popup.y, popup.width, popup.height, ACCENT)
        for ((item, r) in popupRows(popup)) {
            if (item == null) {
                graphics.fill(r.x + 4, r.y + 2, r.right - 4, r.y + 3, 0xFF2E3138.toInt())
                continue
            }
            if (item.enabled && r.contains(mouseX.toDouble(), mouseY.toDouble())) graphics.fill(r.x + 1, r.y, r.right - 1, r.bottom, 0x553B6CFF)
            graphics.text(font, item.label, r.x + 8, r.y + 3, if (item.enabled) WHITE else 0xFF5A5D66.toInt(), false)
        }
    }

    private fun confirmRects(state: VanillaTitleMenu.State, confirm: Confirm): Triple<Rect, Rect, Rect> {
        val w = maxOf(font.width(confirm.text) + 20, 180)
        val box = Rect((state.screen.width - w) / 2, state.screen.height / 2 - 30, w, 56)
        val yes = Rect(box.x + w / 2 - 74, box.y + 30, 70, 18)
        val no = Rect(box.x + w / 2 + 4, box.y + 30, 70, 18)
        return Triple(box, yes, no)
    }

    private fun drawConfirm(state: VanillaTitleMenu.State, graphics: GuiGraphicsExtractor, confirm: Confirm, mouseX: Int, mouseY: Int) {
        graphics.fill(0, 0, state.screen.width, state.screen.height, 0x90000000.toInt())
        val (box, yes, no) = confirmRects(state, confirm)
        graphics.fill(box.x, box.y, box.right, box.bottom, 0xF8101216.toInt())
        graphics.outline(box.x, box.y, box.w, box.h, ACCENT)
        graphics.centeredText(font, confirm.text, box.x + box.w / 2, box.y + 10, WHITE)
        for ((r, label, primary) in listOf(Triple(yes, confirm.yes, true), Triple(no, "Cancel", false))) {
            val over = r.contains(mouseX.toDouble(), mouseY.toDouble())
            graphics.fill(r.x, r.y, r.right, r.bottom, if (primary) (if (over) 0xFF5A85FF.toInt() else ACCENT) else (if (over) SURFACE_HOVER else 0xFF1C1F26.toInt()))
            graphics.centeredText(font, label, r.x + r.w / 2, r.y + 5, WHITE)
        }
    }

    // ---------------------------------------------------------------- input

    private fun overEditorUi(state: VanillaTitleMenu.State, x: Double, y: Double): Boolean =
        confirm != null || popup?.let { Rect(it.x, it.y, it.width, it.height).contains(x, y) } == true ||
            barRects(state, barItems(state)).any { it.contains(x, y) } ||
            (prefs().panelOpen && panelRect(state).contains(x, y))

    fun mouseClicked(state: VanillaTitleMenu.State, event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val x = event.x()
        val y = event.y()
        val left = event.button() == InputConstants.MOUSE_BUTTON_LEFT
        val right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT
        moved = false
        pressX = x.toInt()
        pressY = y.toInt()

        confirm?.let { dialog ->
            val (_, yes, no) = confirmRects(state, dialog)
            if (yes.contains(x, y)) {
                confirm = null
                dialog.action()
            } else if (no.contains(x, y)) {
                confirm = null
            }
            return true
        }
        popup?.let { open ->
            popup = null
            val hit = popupRows(open).firstOrNull { it.second.contains(x, y) }
            if (hit != null) {
                hit.first?.takeIf { it.enabled }?.action?.invoke()
                return true
            }
            if (Rect(open.x, open.y, open.width, open.height).contains(x, y)) return true
        }

        val items = barItems(state)
        val rects = barRects(state, items)
        rects.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { index ->
            if (index == 0 || !left) {
                mode = Mode.BAR
                barStart = intArrayOf(rects.first().x, rects.first().y)
            } else if (items[index].enabled) {
                items[index].action(items[index], rects[index])
            }
            return true
        }
        if (prefs().panelOpen && panelRect(state).contains(x, y)) {
            if (left) panelClick(state, x, y, event)
            return true
        }

        // the handles of the one selected piece come before the pieces under them
        val chosen = selected(state)
        if (left && chosen.size == 1) {
            val index = handles(rect(chosen[0])).indexOfFirst { it.contains(x, y) }
            if (index >= 0) {
                MenuLayoutStore.checkpoint(SCOPE)
                mode = Mode.RESIZE
                handle = index
                start.clear()
                start[chosen[0].id] = intArrayOf(chosen[0].left, chosen[0].top, chosen[0].widget.width, chosen[0].widget.height)
                return true
            }
        }

        val hit = visiblePieces(state).firstOrNull { rect(it).contains(x, y) }
        if (hit == null) {
            if (right) return true
            if (!event.hasShiftDown()) selection.clear()
            mode = Mode.MARQUEE
            marquee = null
            return true
        }
        val picked = if (event.hasAltDown()) listOf(hit) else withGroup(state, hit)
        when {
            right -> {
                if (hit.id !in selection) {
                    selection.clear()
                    picked.forEach { selection.add(it.id) }
                }
                contextMenu(state, x.toInt(), y.toInt())
                return true
            }
            event.hasShiftDown() -> {
                if (hit.id in selection) picked.forEach { selection.remove(it.id) } else picked.forEach { selection.add(it.id) }
                if (hit.id !in selection) return true
            }
            hit.id !in selection || event.hasAltDown() -> {
                selection.clear()
                picked.forEach { selection.add(it.id) }
            }
        }
        if (doubleClick && left) {
            properties(state, hit)
            return true
        }
        if (left) {
            MenuLayoutStore.checkpoint(SCOPE)
            mode = Mode.MOVE
            start.clear()
            selected(state).forEach { start[it.id] = intArrayOf(it.left, it.top) }
        }
        return true
    }

    /** Keys while editing; true when used. */
    fun keyPressed(state: VanillaTitleMenu.State, event: KeyEvent): Boolean {
        if (!state.editing) return false
        val key = event.key()
        val ctrl = event.hasControlDown()
        when {
            event.isEscape() -> when {
                confirm != null -> confirm = null
                popup != null -> popup = null
                selection.isNotEmpty() -> selection.clear()
                else -> finish(state)
            }
            confirm != null -> return true
            ctrl && key == InputConstants.KEY_Z && event.hasShiftDown() -> redo(state)
            ctrl && key == InputConstants.KEY_Z -> undo(state)
            ctrl && key == InputConstants.KEY_Y -> redo(state)
            ctrl && key == InputConstants.KEY_A -> state.pieces.values.filter { it.widget.visible }.forEach { selection.add(it.id) }
            ctrl && key == InputConstants.KEY_D -> duplicate(state)
            ctrl && key == InputConstants.KEY_G && event.hasShiftDown() -> ungroup(state)
            ctrl && key == InputConstants.KEY_G -> group(state)
            key == InputConstants.KEY_DELETE || key == InputConstants.KEY_BACKSPACE -> delete(state)
            event.isLeft() || event.isRight() || event.isUp() || event.isDown() -> {
                val pieces = selected(state)
                if (pieces.isEmpty()) return true
                val step = if (event.hasShiftDown()) 10 else 1
                val dx = if (event.isLeft()) -step else if (event.isRight()) step else 0
                val dy = if (event.isUp()) -step else if (event.isDown()) step else 0
                MenuLayoutStore.checkpoint(SCOPE)
                for (piece in pieces) VanillaTitleMenu.store(state, piece, piece.left + dx, piece.top + dy)
                changed(state)
            }
            else -> return false
        }
        return true
    }

    /** The mouse wheel while editing: scrolls the panel, or scales with Ctrl; true when used. */
    fun scrolled(state: VanillaTitleMenu.State, x: Double, y: Double, amount: Double): Boolean {
        if (!state.editing) return false
        if (prefs().panelOpen && panelRect(state).contains(x, y)) {
            val max = (rows(state).size * ROW_HEIGHT + 24 - state.screen.height).coerceAtLeast(0)
            panelScroll = (panelScroll - (amount * ROW_HEIGHT * 2).roundToInt()).coerceIn(0, max)
            return true
        }
        if (!Minecraft.getInstance().hasControlDown()) return false
        val hit = visiblePieces(state).firstOrNull { rect(it).contains(x, y) } ?: return true
        val pieces = if (hit.id in selection) selected(state) else withGroup(state, hit)
        val now = System.nanoTime()
        // one undo step for a whole spin of the wheel
        val last = lastScale
        if (last == null || last.first != hit.id || now - last.second > SCALE_MERGE_NANOS) MenuLayoutStore.checkpoint(SCOPE)
        lastScale = hit.id to now
        for (piece in pieces) {
            val element = MenuLayoutStore.edit(SCOPE, piece.id)
            val next = ((element.scale + (if (amount > 0) SCALE_STEP else -SCALE_STEP)) * 100).roundToInt() / 100f
            // keeps the top left corner where it is
            val x0 = piece.left
            val y0 = piece.top
            element.scale = next.coerceIn(0.25f, 4f)
            VanillaTitleMenu.store(state, piece, x0, y0)
        }
        changed(state)
        say("Scale ×${"%.2f".format(pieces.first().data?.scale ?: 1f)}")
        return true
    }
}
//?}
