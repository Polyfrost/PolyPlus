package org.polyfrost.polyplus.client.emoji

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import java.util.function.Consumer
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >= 26.1 {
typealias PickerGraphics = GuiGraphicsExtractor
//?} else {
/*typealias PickerGraphics = GuiGraphics
*///?}

class EmojiChatPicker {
    var isOpen = false
        private set

    private var buttonX = 0
    private var buttonY = 0
    private var panelX = 0
    private var panelY = 0
    private var columns = MIN_COLUMNS
    private var scrollRow = 0
    private var selected = 0
    private var query = ""
    private var entries: List<EmojiRegistry.EmojiEntry> = emptyList()
    private var buttonHovered = false
    private var buttonGlyph: String? = null

    fun layout(inputX: Int, inputY: Int, inputWidth: Int) {
        columns = ((inputWidth - PADDING * 2 - SCROLLBAR) / CELL).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        val boxTop = inputY - CHAT_BOX_INSET
        buttonX = (inputX + inputWidth - BUTTON - GAP).coerceAtLeast(0)
        buttonY = (boxTop - GAP - BUTTON).coerceAtLeast(0)
        panelX = inputX.coerceAtMost(buttonX + BUTTON - panelWidth()).coerceAtLeast(0)
        panelY = (buttonY - GAP - panelHeight()).coerceAtLeast(0)
    }

    fun toggle() {
        isOpen = !isOpen
        if (isOpen) {
            query = ""
            selected = 0
            scrollRow = 0
            refresh()
        }
    }

    fun close() {
        isOpen = false
        query = ""
    }

    fun handleKey(key: Int, shiftDown: Boolean, onPick: Consumer<String>): Boolean {
        if (!isOpen) return false
        when (key) {
            KEY_ESCAPE -> close()
            KEY_BACKSPACE -> if (query.isNotEmpty()) {
                query = query.dropLast(1)
                refresh()
            }
            KEY_ENTER, KEY_NUMPAD_ENTER, KEY_TAB -> entries.getOrNull(selected)?.let { pick(it, shiftDown, onPick) }
            KEY_LEFT -> move(-1)
            KEY_RIGHT -> move(1)
            KEY_UP -> move(-columns)
            KEY_DOWN -> move(columns)
            KEY_PAGE_UP -> move(-columns * ROWS)
            KEY_PAGE_DOWN -> move(columns * ROWS)
            else -> return false
        }
        return true
    }

    fun charTyped(codepoint: Int): Boolean {
        if (!isOpen) return false
        val ch = codepoint.toChar()
        if (codepoint > Char.MAX_VALUE.code || ch.isISOControl() || ch == SECTION_SIGN) return false
        if (query.length >= MAX_QUERY) return true
        query += ch
        refresh()
        return true
    }

    fun render(graphics: PickerGraphics, font: Font, mouseX: Int, mouseY: Int) {
        if (!EmojiRegistry.enabled()) return
        renderButton(graphics, font, mouseX, mouseY)
        if (isOpen) renderPanel(graphics, font, mouseX, mouseY)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, shiftDown: Boolean, onPick: Consumer<String>): Boolean {
        if (!EmojiRegistry.enabled() || button != 0) return false
        if (inRect(mouseX, mouseY, buttonX, buttonY, BUTTON, BUTTON)) {
            toggle()
            return true
        }
        if (!isOpen) return false
        if (!inRect(mouseX, mouseY, panelX, panelY, panelWidth(), panelHeight())) {
            close()
            return false
        }
        val entry = entries.getOrNull(cellAt(mouseX, mouseY)) ?: return true
        pick(entry, shiftDown, onPick)
        return true
    }

    fun mouseScrolled(mouseX: Double, mouseY: Double, deltaY: Double): Boolean {
        if (!isOpen || deltaY == 0.0) return false
        if (!inRect(mouseX, mouseY, panelX, panelY, panelWidth(), panelHeight())) return false
        val rows = if (deltaY > 0) -1 else 1
        scrollRow = (scrollRow + rows).coerceIn(0, maxScrollRow())
        return true
    }

    private fun pick(entry: EmojiRegistry.EmojiEntry, shiftDown: Boolean, onPick: Consumer<String>) {
        EmojiRecents.record(entry.alias)
        onPick.accept(entry.alias)
        if (shiftDown) refresh() else close()
    }

    private fun refresh() {
        entries = if (query.isBlank()) defaultEntries() else EmojiRegistry.search(query)
        selected = selected.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        if (query.isNotEmpty()) selected = 0
        scrollRow = scrollRow.coerceIn(0, maxScrollRow())
        revealSelection()
    }

    private fun move(delta: Int) {
        if (entries.isEmpty()) return
        selected = (selected + delta).coerceIn(0, entries.size - 1)
        revealSelection()
    }

    private fun revealSelection() {
        if (entries.isEmpty()) return
        val row = selected / columns
        if (row < scrollRow) scrollRow = row
        if (row >= scrollRow + ROWS) scrollRow = row - ROWS + 1
        scrollRow = scrollRow.coerceIn(0, maxScrollRow())
    }

    private fun renderButton(graphics: PickerGraphics, font: Font, mouseX: Int, mouseY: Int) {
        val hovered = inRect(mouseX.toDouble(), mouseY.toDouble(), buttonX, buttonY, BUTTON, BUTTON)
        if (hovered && !buttonHovered) buttonGlyph = randomGlyph()
        buttonHovered = hovered
        graphics.fill(buttonX, buttonY, buttonX + BUTTON, buttonY + BUTTON, background())
        if (hovered || isOpen) {
            graphics.fill(buttonX, buttonY, buttonX + BUTTON, buttonY + BUTTON, HIGHLIGHT)
        }
        val glyph = buttonGlyph ?: defaultGlyph() ?: return
        drawGlyph(graphics, font, glyph, buttonX, buttonY)
    }

    private fun renderPanel(graphics: PickerGraphics, font: Font, mouseX: Int, mouseY: Int) {
        val width = panelWidth()
        val height = panelHeight()
        graphics.fill(panelX, panelY, panelX + width, panelY + height, background())

        val gridX = panelX + PADDING
        val gridY = panelY + PADDING + LINE
        var hoveredEntry: EmojiRegistry.EmojiEntry? = null

        for (row in 0 until ROWS) {
            for (col in 0 until columns) {
                val index = (scrollRow + row) * columns + col
                val entry = entries.getOrNull(index) ?: continue
                val cellX = gridX + col * CELL
                val cellY = gridY + row * CELL
                val hovered = inRect(mouseX.toDouble(), mouseY.toDouble(), cellX, cellY, CELL, CELL)
                if (hovered) hoveredEntry = entry
                if (hovered || index == selected) {
                    graphics.fill(cellX, cellY, cellX + CELL, cellY + CELL, if (hovered) HIGHLIGHT else SELECTION)
                }
                drawGlyph(graphics, font, entry.glyph, cellX, cellY)
            }
        }

        renderSearch(graphics, font)
        renderScrollbar(graphics, gridY)
        renderLabel(graphics, font, hoveredEntry ?: entries.getOrNull(selected), gridY)
    }

    private fun renderSearch(graphics: PickerGraphics, font: Font) {
        val textX = panelX + PADDING
        val textY = panelY + PADDING + TEXT_OFFSET
        var cursorX = textX
        if (query.isNotEmpty()) {
            drawString(graphics, font, query, textX, textY, TEXT_COLOR)
            cursorX += font.width(query)
        }
        if (System.currentTimeMillis() % CARET_PERIOD < CARET_PERIOD / 2) {
            drawString(graphics, font, "_", cursorX, textY, TEXT_COLOR)
        }
        if (query.isEmpty()) {
            drawString(graphics, font, HINT, cursorX + font.width("_") + 2, textY, MUTED_COLOR)
        } else {
            val count = entries.size.toString()
            drawString(graphics, font, count, panelX + panelWidth() - PADDING - font.width(count), textY, MUTED_COLOR)
        }
    }

    private fun renderLabel(graphics: PickerGraphics, font: Font, entry: EmojiRegistry.EmojiEntry?, gridY: Int) {
        val y = gridY + ROWS * CELL + TEXT_OFFSET
        if (entry == null) {
            drawString(graphics, font, if (query.isEmpty()) EMPTY_LABEL else NO_MATCH, panelX + PADDING, y, MUTED_COLOR)
            return
        }
        drawString(graphics, font, ":${entry.alias}:", panelX + PADDING, y, SELECTED_TEXT_COLOR)
    }

    private fun renderScrollbar(graphics: PickerGraphics, gridY: Int) {
        val maxRow = maxScrollRow()
        if (maxRow <= 0) return
        val trackX = panelX + panelWidth() - PADDING - SCROLLBAR
        val trackHeight = ROWS * CELL
        graphics.fill(trackX, gridY, trackX + SCROLLBAR, gridY + trackHeight, HIGHLIGHT)
        val thumbHeight = (trackHeight * ROWS / (maxRow + ROWS)).coerceAtLeast(6)
        val thumbY = gridY + (trackHeight - thumbHeight) * scrollRow / maxRow
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR, thumbY + thumbHeight, MUTED_COLOR)
    }

    private fun cellAt(mouseX: Double, mouseY: Double): Int {
        val gridX = panelX + PADDING
        val gridY = panelY + PADDING + LINE
        val col = ((mouseX - gridX) / CELL).toInt()
        val row = ((mouseY - gridY) / CELL).toInt()
        if (mouseX < gridX || mouseY < gridY || col !in 0 until columns || row !in 0 until ROWS) return -1
        return (scrollRow + row) * columns + col
    }

    private fun drawGlyph(graphics: PickerGraphics, font: Font, glyph: String, cellX: Int, cellY: Int) {
        val component = EmojiFont.glyph(glyph, Style.EMPTY)
        val width = font.width(component)
        drawText(graphics, font, component.visualOrderText, cellX + (CELL - width) / 2, cellY + 2, -1)
    }

    private fun drawString(graphics: PickerGraphics, font: Font, text: String, x: Int, y: Int, color: Int) {
        drawText(graphics, font, Component.literal(text).visualOrderText, x, y, color)
    }

    private fun drawText(graphics: PickerGraphics, font: Font, seq: FormattedCharSequence, x: Int, y: Int, color: Int) {
        //? if >= 26.1 {
        graphics.text(font, seq, x, y, color)
        //?} else {
        /*graphics.drawString(font, seq, x, y, color)
        *///?}
    }

    private fun defaultEntries(): List<EmojiRegistry.EmojiEntry> {
        val recents = EmojiRecents.entries()
        if (recents.isEmpty()) return EmojiRegistry.catalog
        val seen = recents.mapTo(HashSet()) { it.glyph }
        return recents + EmojiRegistry.catalog.filterNot { it.glyph in seen }
    }

    private fun defaultGlyph(): String? = EmojiRegistry.resolve("smile") ?: EmojiRegistry.catalog.firstOrNull()?.glyph

    private fun randomGlyph(): String? {
        val catalog = EmojiRegistry.catalog
        if (catalog.isEmpty()) return null
        var glyph = catalog.random().glyph
        if (catalog.size > 1) {
            while (glyph == buttonGlyph) glyph = catalog.random().glyph
        }
        return glyph
    }

    private fun background(): Int = Minecraft.getInstance().options.getBackgroundColor(CHAT_BACKGROUND)

    private fun maxScrollRow(): Int {
        val rows = (entries.size + columns - 1) / columns
        return (rows - ROWS).coerceAtLeast(0)
    }

    private fun panelWidth(): Int = columns * CELL + PADDING * 2 + SCROLLBAR

    private fun panelHeight(): Int = ROWS * CELL + PADDING * 2 + LINE * 2

    private fun inRect(mouseX: Double, mouseY: Double, x: Int, y: Int, width: Int, height: Int): Boolean =
        mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

    private companion object {
        const val CELL = 12
        const val LINE = 12
        const val ROWS = 7
        const val MIN_COLUMNS = 8
        const val MAX_COLUMNS = 24
        const val PADDING = 2
        const val GAP = 3
        const val CHAT_BOX_INSET = 2
        const val BUTTON = 12
        const val SCROLLBAR = 2
        const val TEXT_OFFSET = 2
        const val MAX_QUERY = 24
        const val CARET_PERIOD = 1000L
        const val SECTION_SIGN = '§'
        const val HINT = "search emoji"
        const val EMPTY_LABEL = "no emoji"
        const val NO_MATCH = "no matches"
        const val KEY_ESCAPE = 256
        const val KEY_ENTER = 257
        const val KEY_TAB = 258
        const val KEY_BACKSPACE = 259
        const val KEY_RIGHT = 262
        const val KEY_LEFT = 263
        const val KEY_DOWN = 264
        const val KEY_UP = 265
        const val KEY_PAGE_UP = 266
        const val KEY_PAGE_DOWN = 267
        const val KEY_NUMPAD_ENTER = 335
        const val CHAT_BACKGROUND = 0x80000000.toInt()
        const val HIGHLIGHT = 0x40FFFFFF
        const val SELECTION = 0x28FFFFFF
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFFAAAAAA.toInt()
        const val SELECTED_TEXT_COLOR = 0xFFFFFF55.toInt()
    }
}
