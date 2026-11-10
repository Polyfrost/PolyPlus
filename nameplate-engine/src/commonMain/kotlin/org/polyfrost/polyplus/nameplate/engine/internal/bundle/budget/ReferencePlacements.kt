package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling

internal object ReferencePlacements {
    const val NARROW = 16
    const val MEDIUM = 96
    val WIDEST: Int = PixelGrid.WINDOW.x1 - BundleLimits.MAX_DECORATION_MARGIN

    val WIDEST_LINE: TagBlock = TagBlock.line(WIDEST, 0, WIDEST)

    val ALL: List<TagBlock> = listOf(
        TagBlock.line(NARROW, 0, NARROW),
        TagBlock.line(MEDIUM, 0, MEDIUM),
        WIDEST_LINE,
        lines(MEDIUM, 2),
        lines(WIDEST, 2),
        lines(MEDIUM, 3),
        lines(WIDEST, 3),
    )

    val BADGED: List<Pair<TagBlock, PixelRect>> = intArrayOf(NARROW, MEDIUM).flatMap { width ->
        val line = TagBlock.line(width + TagBlock.BADGE_ADVANCE, TagBlock.BADGE_ADVANCE, width)
        val display = TagBlock.display(intArrayOf(width / 2, width), 2, width, TagBlock.Align.CENTER, 1, 0, width, true)
        listOf(
            line to PixelRect(0, line.nameLineY, BadgeStyling.CELLS_WIDE, line.nameLineY + BadgeStyling.CELLS_HIGH),
            display to PixelRect(
                display.badgeX,
                display.badgeY,
                display.badgeX + BadgeStyling.CELLS_WIDE,
                display.badgeY + BadgeStyling.CELLS_HIGH,
            ),
        )
    }

    private fun lines(width: Int, count: Int): TagBlock {
        val nameLine = count / 2
        val widths = IntArray(count) { if (it == nameLine) width else width / 2 }
        return TagBlock.display(widths, count, width, TagBlock.Align.CENTER, nameLine, 0, width, false)
    }
}
