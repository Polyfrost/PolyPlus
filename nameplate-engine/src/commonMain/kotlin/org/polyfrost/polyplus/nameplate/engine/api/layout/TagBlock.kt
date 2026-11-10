package org.polyfrost.polyplus.nameplate.engine.api.layout

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect

class TagBlock private constructor(
    val width: Int,
    val height: Int,
    private val lines: IntArray,
    val nameLine: Int,
    val nameX: Int,
    val nameWidth: Int,
    val badgeX: Int,
    val badgeY: Int,
    prefixWidth: Int? = null,
    background: PixelRect? = null,
) {
    val prefixWidth: Int = prefixWidth ?: (nameX - lines[nameLine * 4])

    val background: PixelRect = background ?: PixelRect.vanillaBackground(width, height)

    fun placed(prefixWidth: Int, background: PixelRect): TagBlock =
        TagBlock(width, height, lines, nameLine, nameX, nameWidth, badgeX, badgeY, prefixWidth, background)

    val lineCount: Int
        get() = lines.size / 4

    val nameLineX: Int
        get() = lines[nameLine * 4]

    val nameLineY: Int
        get() = lines[nameLine * 4 + 1]

    internal val nameLineWidth: Int
        get() = lines[nameLine * 4 + 2] - lines[nameLine * 4]

    val hasBadge: Boolean
        get() = badgeX != NO_BADGE

    fun rect(): PixelRect = PixelRect(0, 0, width, height)

    fun nameRect(): PixelRect = PixelRect(nameX, nameLineY, nameX + nameWidth, nameLineY + GLYPH_HEIGHT)

    enum class Align {
        LEFT,
        RIGHT,
        CENTER,
    }

    companion object {
        private const val LINE_PITCH = 10
        const val GLYPH_HEIGHT = PixelRect.GLYPH_HEIGHT
        const val MAX_LINES = 3
        const val BADGE_ADVANCE = 11

        private const val NO_BADGE = Int.MIN_VALUE

        fun line(textWidth: Int, nameX: Int, nameWidth: Int): TagBlock = TagBlock(
            width = textWidth,
            height = GLYPH_HEIGHT,
            lines = intArrayOf(0, 0, textWidth, GLYPH_HEIGHT),
            nameLine = 0,
            nameX = nameX,
            nameWidth = nameWidth,
            badgeX = NO_BADGE,
            badgeY = 0,
        )

        fun display(
            widths: IntArray,
            count: Int,
            maxWidth: Int,
            align: Align,
            nameLine: Int,
            nameX: Int,
            nameWidth: Int,
            badge: Boolean,
        ): TagBlock {
            val all = count <= MAX_LINES
            val lines = IntArray(count * 4)
            val nameOffset = doubledOffset(align, maxWidth, widths[nameLine])
            var x0 = Int.MAX_VALUE
            var y0 = Int.MAX_VALUE
            var x1 = Int.MIN_VALUE
            var y1 = Int.MIN_VALUE
            for (line in 0 until count) {
                val lineWidth = widths[line]
                if (line != nameLine && (lineWidth <= 0 || !all)) continue

                val relative = doubledOffset(align, maxWidth, lineWidth) - nameOffset
                val i = line * 4
                lines[i] = relative.floorDiv(2)
                lines[i + 1] = (line - nameLine) * LINE_PITCH
                lines[i + 2] = ceilDiv(relative + 2 * lineWidth, 2)
                lines[i + 3] = lines[i + 1] + GLYPH_HEIGHT
                x0 = minOf(x0, lines[i])
                y0 = minOf(y0, lines[i + 1])
                x1 = maxOf(x1, lines[i + 2])
                y1 = maxOf(y1, lines[i + 3])
            }

            var badgeX = NO_BADGE
            var badgeY = 0
            if (badge) {
                badgeX = -BADGE_ADVANCE
                x0 = minOf(x0, badgeX)
            }

            for (i in lines.indices step 4) {
                if (lines[i] != lines[i + 2]) {
                    lines[i] -= x0
                    lines[i + 1] -= y0
                    lines[i + 2] -= x0
                    lines[i + 3] -= y0
                }
            }
            if (badge) {
                badgeX -= x0
                badgeY -= y0
            }

            return TagBlock(x1 - x0, y1 - y0, lines, nameLine, nameX - x0, nameWidth, badgeX, badgeY)
        }

        private fun doubledOffset(align: Align, maxWidth: Int, lineWidth: Int): Int = when (align) {
            Align.LEFT -> 0
            Align.RIGHT -> 2 * (maxWidth - lineWidth)
            Align.CENTER -> maxWidth - lineWidth
        }

        private fun ceilDiv(dividend: Int, divisor: Int): Int = -(-dividend).floorDiv(divisor)
    }
}
