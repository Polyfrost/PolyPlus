package org.polyfrost.polyplus.nameplate.engine.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock

class TagBlockTest {
    @Test
    fun aVanillaTagIsItsOwnLine() {
        val block = TagBlock.line(37, 4, 30)
        assertEquals(PixelRect(0, 0, 37, 8), block.rect())
        assertEquals(1, block.lineCount)
        assertEquals(0, block.nameLineX)
        assertEquals(0, block.nameLineY)
        assertEquals(37, block.nameLineWidth)
        assertEquals(4, block.nameX)
        assertEquals(30, block.nameWidth)
        assertFalse(block.hasBadge)
    }

    @Test
    fun cubeCraftRankAboveAWiderNameIsCentredOnIt() {
        val block = TagBlock.display(intArrayOf(35, 48), 2, 48, TagBlock.Align.CENTER, 1, 0, 48, false)
        assertEquals(PixelRect(0, 0, 48, 18), block.rect())
        assertEquals(0, block.nameLineX)
        assertEquals(10, block.nameLineY)
    }

    @Test
    fun aRankWiderThanTheNameWidensTheBlock() {
        val block = TagBlock.display(intArrayOf(35, 20), 2, 35, TagBlock.Align.CENTER, 1, 0, 20, false)
        assertEquals(PixelRect(0, 0, 36, 18), block.rect())
        assertEquals(8, block.nameLineX)
        assertEquals(8, block.nameX)
    }

    @Test
    fun leftAndRightAlignment() {
        val left = TagBlock.display(intArrayOf(35, 48), 2, 48, TagBlock.Align.LEFT, 1, 0, 48, false)
        assertEquals(PixelRect(0, 0, 48, 18), left.rect())
        assertEquals(0, left.nameLineX)

        val right = TagBlock.display(intArrayOf(35, 48), 2, 48, TagBlock.Align.RIGHT, 1, 0, 48, false)
        assertEquals(PixelRect(0, 0, 48, 18), right.rect())
        assertEquals(0, right.nameLineX)
    }

    @Test
    fun linesAreTenPixelsApartAndEmptyLinesAreLeftOut() {
        val block = TagBlock.display(intArrayOf(30, 0, 40), 3, 40, TagBlock.Align.CENTER, 2, 0, 40, false)
        assertEquals(PixelRect(0, 0, 40, 28), block.rect())
        assertEquals(20, block.nameLineY)
    }

    @Test
    fun moreThanThreeLinesDecorateOnlyTheNameLine() {
        val block = TagBlock.display(intArrayOf(30, 30, 40, 30), 4, 40, TagBlock.Align.CENTER, 2, 3, 30, false)
        assertEquals(PixelRect(0, 0, 40, 8), block.rect())
        assertEquals(4, block.lineCount)
        assertEquals(3, block.nameX)
    }

    @Test
    fun theBadgeGoesLeftOfTheNameLineAndWidensTheBlock() {
        val block = TagBlock.display(intArrayOf(35, 48), 2, 48, TagBlock.Align.CENTER, 1, 0, 48, true)
        assertEquals(PixelRect(0, 0, 59, 18), block.rect())
        assertTrue(block.hasBadge)
        assertEquals(0, block.badgeX)
        assertEquals(10, block.badgeY)
        assertEquals(11, block.nameLineX)
    }

    @Test
    fun aBadgeLeftOfANarrowNameStaysUnderAWideRank() {
        val block = TagBlock.display(intArrayOf(60, 20), 2, 60, TagBlock.Align.CENTER, 1, 0, 20, true)
        assertEquals(PixelRect(0, 0, 60, 18), block.rect())
        assertEquals(9, block.badgeX)
        assertEquals(20, block.nameLineX)
    }

    @Test
    fun aCentredLineOneOddPixelNarrowerRoundsOutward() {
        val block = TagBlock.display(intArrayOf(41, 40), 2, 41, TagBlock.Align.CENTER, 1, 0, 40, false)
        assertEquals(PixelRect(0, 0, 42, 18), block.rect())
        assertEquals(1, block.nameLineX)
    }
}
