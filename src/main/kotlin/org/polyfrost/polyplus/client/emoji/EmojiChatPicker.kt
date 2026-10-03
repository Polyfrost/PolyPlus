package org.polyfrost.polyplus.client.emoji

import net.minecraft.client.Minecraft
//? if > 1.8.9 {
import net.minecraft.client.gui.Font
//?} else {
/*import net.minecraft.client.render.TextRenderer as Font
import net.minecraft.client.gui.GuiElement
import net.minecraft.client.render.platform.GlStateManager
*///?}
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.polyfrost.polyplus.client.render.InputConstants
import org.polyfrost.polyplus.compat.ChattingButtonRow
import java.util.function.Consumer

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if > 1.8.9 && < 26.1 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >= 26.1 {
typealias PickerGraphics = GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*typealias PickerGraphics = GuiGraphics
*///?} else {
/*object PickerGraphics {
    fun fill(x1: Int, y1: Int, x2: Int, y2: Int, color: Int) = GuiElement.fill(x1, y1, x2, y2, color)
}

private fun Font.width(text: String): Int = getWidth(text)
*///?}

class EmojiChatPicker {
    var isOpen = false
        private set

    private var buttonX = 0
    private var buttonY = 0
    private var buttonSize = BUTTON
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
        val slot = ChattingButtonRow.slot()
        buttonSize = if (slot != null) ChattingButtonRow.SIZE else BUTTON
        buttonX = (slot?.x ?: (inputX + inputWidth - BUTTON - GAP)).coerceAtLeast(0)
        buttonY = (slot?.y ?: (boxTop - GAP - BUTTON)).coerceAtLeast(0)
        panelX = inputX.coerceAtMost(buttonX + buttonSize - panelWidth()).coerceAtLeast(0)
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
            InputConstants.KEY_ESCAPE -> close()
            InputConstants.KEY_BACKSPACE -> if (query.isNotEmpty()) {
                query = query.dropLast(1)
                refresh()
            }
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_TAB ->
                entries.getOrNull(selected)?.let { pick(it, shiftDown, onPick) }
            InputConstants.KEY_LEFT -> move(-1)
            InputConstants.KEY_RIGHT -> move(1)
            InputConstants.KEY_UP -> move(-columns)
            InputConstants.KEY_DOWN -> move(columns)
            InputConstants.KEY_PAGEUP -> move(-columns * ROWS)
            InputConstants.KEY_PAGEDOWN -> move(columns * ROWS)
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
        if (isOpen) renderPanel(graphics, font, mouseX, mouseY) else if (buttonHovered) renderTooltip(graphics, font)
    }

    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int, shiftDown: Boolean, onPick: Consumer<String>): Boolean {
        if (!EmojiRegistry.enabled() || button != 0) return false
        if (inRect(mouseX, mouseY, buttonX, buttonY, buttonSize, buttonSize)) {
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
        val hovered = inRect(mouseX.toDouble(), mouseY.toDouble(), buttonX, buttonY, buttonSize, buttonSize)
        if (hovered && !buttonHovered) buttonGlyph = randomGlyph()
        buttonHovered = hovered
        val highlighted = hovered || isOpen
        val chatting = ChattingButtonRow.background(highlighted)
        graphics.fill(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, chatting ?: background())
        if (chatting == null && highlighted) {
            graphics.fill(buttonX, buttonY, buttonX + buttonSize, buttonY + buttonSize, HIGHLIGHT)
        }
        val glyph = buttonGlyph ?: defaultGlyph() ?: return
        drawGlyph(graphics, font, glyph, buttonX, buttonY, buttonSize)
    }

    private fun renderTooltip(graphics: PickerGraphics, font: Font) {
        val window = Minecraft.getInstance().window
        val textWidth = font.width(TOOLTIP)
        val x = (buttonX + (buttonSize - textWidth) / 2)
            .coerceIn(TOOLTIP_MARGIN, (window.guiScaledWidth - textWidth - TOOLTIP_MARGIN).coerceAtLeast(TOOLTIP_MARGIN))
        val y = (buttonY - TOOLTIP_HEIGHT - TOOLTIP_GAP)
            .coerceIn(TOOLTIP_MARGIN, (window.guiScaledHeight - TOOLTIP_HEIGHT - 6).coerceAtLeast(TOOLTIP_MARGIN))
        graphics.fill(x - 4, y - 3, x + textWidth + 4, y + TOOLTIP_HEIGHT + 3, TOOLTIP_BACKGROUND)
        graphics.fill(x - 3, y - 4, x + textWidth + 3, y + TOOLTIP_HEIGHT + 4, TOOLTIP_BACKGROUND)
        drawString(graphics, font, TOOLTIP, x, y, SELECTED_TEXT_COLOR)
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

    //? if > 1.8.9 {
    private fun drawGlyph(graphics: PickerGraphics, font: Font, glyph: String, cellX: Int, cellY: Int, cell: Int = CELL) {
        val component = EmojiFont.glyph(glyph, Style.EMPTY)
        val seq = component.visualOrderText
        val scale = if (cell < CELL) (cell - GLYPH_INSET * 2).toFloat() / GLYPH else 1f
        if (scale == 1f) {
            val width = font.width(component)
            drawText(graphics, font, seq, cellX + (cell - width) / 2, cellY + (cell - GLYPH + 1) / 2, -1)
            return
        }
        val inset = (cell - GLYPH * scale) / 2f
        val x = cellX + inset
        val y = cellY + inset + GLYPH_RISE * scale
        val pose = graphics.pose()
        //? if >= 1.21.6 {
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        drawText(graphics, font, seq, 0, 0, -1)
        pose.popMatrix()
        //?} else {
        /*pose.pushPose()
        pose.translate(x.toDouble(), y.toDouble(), 0.0)
        pose.scale(scale, scale, 1f)
        drawText(graphics, font, seq, 0, 0, -1)
        pose.popPose()
        *///?}
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

    //?} else {
    /*private fun drawGlyph(graphics: PickerGraphics, font: Font, glyph: String, cellX: Int, cellY: Int, cell: Int = CELL) {
        val text = EmojiFont.legacyChar(glyph)
        val scale = if (cell < CELL) (cell - GLYPH_INSET * 2).toFloat() / GLYPH else 1f
        if (scale == 1f) {
            drawString(graphics, font, text, cellX + (cell - font.width(text) + 1) / 2, cellY + (cell - GLYPH + 1) / 2, -1)
            return
        }
        val inset = (cell - GLYPH * scale) / 2f
        GlStateManager.pushMatrix()
        GlStateManager.translatef(cellX + inset, cellY + inset + GLYPH_RISE * scale, 0f)
        GlStateManager.scalef(scale, scale, 1f)
        drawString(graphics, font, text, 0, 0, -1)
        GlStateManager.popMatrix()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun drawString(graphics: PickerGraphics, font: Font, text: String, x: Int, y: Int, color: Int) {
        font.drawWithShadow(text, x.toFloat(), y.toFloat(), color)
    }

    *///?}

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

    //? if > 1.8.9 {
    private fun background(): Int = Minecraft.getInstance().options.getBackgroundColor(CHAT_BACKGROUND)
    //?} else {
    /*private fun background(): Int = CHAT_BACKGROUND
    *///?}

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
        const val GLYPH = 9
        const val GLYPH_INSET = 1
        const val GLYPH_RISE = 1f
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
        const val TOOLTIP = "Emoji Picker"
        const val TOOLTIP_HEIGHT = 8
        const val TOOLTIP_GAP = 8
        const val TOOLTIP_MARGIN = 4
        const val TOOLTIP_BACKGROUND = 0xF0100010.toInt()
        const val EMPTY_LABEL = "no emoji"
        const val NO_MATCH = "no matches"
        const val CHAT_BACKGROUND = 0x80000000.toInt()
        const val HIGHLIGHT = 0x40FFFFFF
        const val SELECTION = 0x28FFFFFF
        const val TEXT_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFFAAAAAA.toInt()
        const val SELECTED_TEXT_COLOR = 0xFFFFFF55.toInt()
    }
}
