package org.polyfrost.polyplus.nameplate.engine.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.support.contrastRatio
import org.polyfrost.polyplus.nameplate.engine.support.of

class BadgeStylingTest {
    @Test
    fun reserveLeavesTheArtAlone() {
        val resolved = RESERVE_BADGE.resolve(intArrayOf(DARK and 0xFFFFFF))
        assertEquals(ResolvedText.UNSET, resolved.tint)
        assertEquals(Contrast.Decoration.NONE, resolved.decoration)
        assertEquals(BadgeStyling.Placement.Kind.RESERVE, RESERVE_BADGE.placement.kind)
        assertTrue(RESERVE_BADGE.placement.keepsGap)
    }

    @Test
    fun unreadableArtIsOutlinedEvenWithoutDecoration() {
        val resolved = RESERVE_BADGE.resolve(intArrayOf(PASTEL and 0xFFFFFF))
        assertEquals(Contrast.Decoration.OUTLINE, resolved.decoration)
        assertEquals(Contrast.NEAR_BLACK, resolved.decorationRgb)
    }

    @Test
    fun placedBadgesLeaveNoGap() {
        val anchor = object : BadgeStyling.Anchor {
            override fun x(block: TagBlock): Int = block.width - 4

            override fun y(block: TagBlock): Int = -6
        }
        val placement = BadgeStyling.Placement.placed(anchor)
        assertFalse(placement.keepsGap)
        assertSame(anchor, placement.anchor)
        assertTrue(BadgeStyling.Placement.CARRIED.keepsGap)
    }

    @Test
    fun tintsResolveToTheirColour() {
        assertEquals(0x123456, RESERVE_BADGE.withTint(BadgeStyling.Tint.of(0xFF123456.toInt())).resolve(IntArray(0)).tint)

        val palette = Palette.of(Palette.Slot("badge", "Badge", 0xFFAA5500.toInt()))
        val slot = BadgeStyling.Tint.slot(palette.resolve(), palette.ref("badge"))
        assertEquals(BadgeStyling.Tint.Kind.SLOT, slot.kind)
        assertEquals(0xAA5500, RESERVE_BADGE.withTint(slot).resolve(IntArray(0)).tint)
    }

    @Test
    fun fixedDecorationsKeepTheirColour() {
        val shadow = RESERVE_BADGE.withDecoration(BadgeStyling.Decoration.shadow(0x202020)).resolve(IntArray(0))
        assertEquals(Contrast.Decoration.SHADOW, shadow.decoration)
        assertEquals(0x202020, shadow.decorationRgb)

        val outline = RESERVE_BADGE.withDecoration(BadgeStyling.Decoration.outline(0x303030)).resolve(IntArray(0))
        assertEquals(Contrast.Decoration.OUTLINE, outline.decoration)
        assertEquals(0x303030, outline.decorationRgb)
    }

    @Test
    fun autoDecoratesByContrastWithTheArt() {
        val auto = RESERVE_BADGE.withDecoration(BadgeStyling.Decoration.AUTO)
        assertEquals(Contrast.Decoration.NONE, auto.resolve(intArrayOf(DARK and 0xFFFFFF)).decoration)

        val onMid = auto.resolve(intArrayOf(MID and 0xFFFFFF))
        assertEquals(Contrast.Decoration.SHADOW, onMid.decoration)
        assertEquals(Contrast.NEAR_BLACK, onMid.decorationRgb)

        val onPastel = auto.resolve(intArrayOf(PASTEL and 0xFFFFFF))
        assertEquals(Contrast.Decoration.OUTLINE, onPastel.decoration)
        assertEquals(Contrast.NEAR_BLACK, onPastel.decorationRgb)
        assertTrue(contrastRatio(BadgeStyling.ART_RGB, PASTEL) < Contrast.LARGE_TEXT)
    }

    @Test
    fun autoJudgesTheTintNotTheArt() {
        val dark = RESERVE_BADGE.withTint(BadgeStyling.Tint.of(0x101010)).withDecoration(BadgeStyling.Decoration.AUTO)
        assertEquals(Contrast.Decoration.NONE, dark.resolve(intArrayOf(PASTEL and 0xFFFFFF)).decoration)
    }

    private companion object {
        val PASTEL = 0xFFFFC6DC.toInt()
        val DARK = 0xFF101010.toInt()
        val MID = 0xFF707070.toInt()
    }
}
