package org.polyfrost.polyplus.nameplate.engine.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import org.polyfrost.polyplus.nameplate.engine.api.text.TextPaint
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling
import org.polyfrost.polyplus.nameplate.engine.support.contrastRatio

class TextStylingTest {
    @Test
    fun vanillaTouchesNothing() {
        val resolved = TextStyling.VANILLA.resolve(GOLD, NO_EXTERNAL)
        assertFalse(TextStyling.VANILLA.needsBackdrop)
        assertEquals(ResolvedText.UNSET, resolved.color)
        assertEquals(ResolvedText.ShadowUse.LEAVE, resolved.shadowUse)
        assertEquals(ResolvedText.UNSET, resolved.outline)
    }

    @Test
    fun defaultAdjustsFormattingOnly() {
        assertEquals(TextStyling.Formatting.ADJUST, TextStyling.DEFAULT.formatting)
        assertEquals(TextPaint.NONE, TextStyling.DEFAULT.color)
        assertEquals(TextStyling.Formatting.KEEP, TextStyling.DEFAULT.resolve(NOTHING, NO_EXTERNAL).formatting)
    }

    @Test
    fun anyAutoOrAdjustNeedsTheBackdrop() {
        val fixed = TextStyling.VANILLA.withColor(TextPaint.of(0x123456)).withShadow(TextPaint.of(0x000000))
        assertFalse(fixed.needsBackdrop)
        assertTrue(TextStyling.VANILLA.withColor(TextPaint.AUTO).needsBackdrop)
        assertTrue(TextStyling.VANILLA.withShadow(TextPaint.AUTO).needsBackdrop)
        assertTrue(TextStyling.VANILLA.withOutline(TextPaint.AUTO).needsBackdrop)
        assertTrue(TextStyling.VANILLA.withFormatting(TextStyling.Formatting.ADJUST).needsBackdrop)
    }

    @Test
    fun fixedFieldsApplyAsGiven() {
        val resolved = TextStyling.VANILLA
            .withColor(TextPaint.of(0xFF123456.toInt()))
            .withShadow(TextPaint.of(0x23446B))
            .resolve(GOLD, NO_EXTERNAL)

        assertEquals(0x123456, resolved.color)
        assertEquals(ResolvedText.ShadowUse.ALWAYS, resolved.shadowUse)
        assertEquals(0xFF23446B.toInt(), resolved.shadow)
        assertEquals(ResolvedText.UNSET, resolved.outline)
    }

    @Test
    fun autoColourOnABrightBodyPicksDarkWithNoDecoration() {
        val resolved = auto().resolve(GOLD, NO_EXTERNAL)
        assertEquals(NEAR_BLACK, resolved.color)
        assertTrue(resolved.minRatio >= Contrast.READABLE)
        assertEquals(Contrast.Decoration.NONE, resolved.decoration)
        assertEquals(ResolvedText.ShadowUse.WITH_DROP_SHADOW, resolved.shadowUse)
        assertEquals(opaque(WHITE), resolved.shadow)
        assertEquals(ResolvedText.UNSET, resolved.outline)
    }

    @Test
    fun autoBetweenThreeAndFourAndAHalfForcesAShadow() {
        val resolved = auto().resolve(GREY, NO_EXTERNAL)
        assertEquals(WHITE, resolved.color)
        assertEquals(Contrast.Decoration.SHADOW, resolved.decoration)
        assertEquals(ResolvedText.ShadowUse.ALWAYS, resolved.shadowUse)
        assertEquals(opaque(NEAR_BLACK), resolved.shadow)
        assertEquals(ResolvedText.UNSET, resolved.outline)
    }

    @Test
    fun autoBelowThreeOutlinesAndDropsTheShadow() {
        val resolved = auto().resolve(SPLIT, NO_EXTERNAL)
        assertEquals(Contrast.Decoration.OUTLINE, resolved.decoration)
        assertEquals(Contrast.opposite(resolved.color, Contrast.DEFAULT_CANDIDATES), resolved.outline)
        assertEquals(ResolvedText.ShadowUse.REMOVE, resolved.shadowUse)
    }

    @Test
    fun outlineNoneFallsBackToAShadow() {
        val resolved = auto().withOutline(TextPaint.NONE).resolve(SPLIT, NO_EXTERNAL)
        assertEquals(ResolvedText.UNSET, resolved.outline)
        assertEquals(ResolvedText.ShadowUse.ALWAYS, resolved.shadowUse)
    }

    @Test
    fun shadowNoneFallsBackToAnOutline() {
        val resolved = auto().withShadow(TextPaint.NONE).resolve(GREY, NO_EXTERNAL)
        assertEquals(NEAR_BLACK, resolved.outline)
        assertEquals(ResolvedText.ShadowUse.REMOVE, resolved.shadowUse)
    }

    @Test
    fun noneLeavesShadowAndOutlineAlone() {
        val resolved = TextStyling.VANILLA.withColor(TextPaint.AUTO).resolve(SPLIT, NO_EXTERNAL)
        assertEquals(ResolvedText.ShadowUse.LEAVE, resolved.shadowUse)
        assertEquals(ResolvedText.UNSET, resolved.outline)
    }

    @Test
    fun fixedOutlineNeverCombinesWithAShadow() {
        val resolved = TextStyling.VANILLA
            .withShadow(TextPaint.of(0x222222))
            .withOutline(TextPaint.of(0x000000))
            .resolve(GOLD, NO_EXTERNAL)

        assertEquals(0x000000, resolved.outline)
        assertEquals(ResolvedText.ShadowUse.REMOVE, resolved.shadowUse)
    }

    @Test
    fun fixedColourWithAutoShadowIsJudgedAsGiven() {
        val resolved = TextStyling.VANILLA
            .withColor(TextPaint.of(0x777777))
            .withShadow(TextPaint.AUTO)
            .resolve(intArrayOf(0x000000), NO_EXTERNAL)

        assertEquals(0x777777, resolved.color)
        assertEquals(contrastRatio(0x777777, 0x000000), resolved.minRatio, 1e-9)
        assertEquals(ResolvedText.ShadowUse.WITH_DROP_SHADOW, resolved.shadowUse)
        assertEquals(opaque(WHITE), resolved.shadow)
    }

    @Test
    fun customCandidatesAreUsed() {
        val brown = 0x6B4300
        val resolved = auto().withCandidates(brown, WHITE).resolve(GOLD, NO_EXTERNAL)
        assertEquals(brown, resolved.color)
        assertEquals(opaque(WHITE), resolved.shadow)
    }

    @Test
    fun externalColourWinsAndContrastOnlyDecorates() {
        val yellow = 0xFFFF55
        val resolved = auto().resolve(GOLD, yellow)
        assertEquals(ResolvedText.UNSET, resolved.color)
        assertEquals(Contrast.decorationFor(resolved.minRatio), resolved.decoration)
        assertTrue(resolved.minRatio < Contrast.LARGE_TEXT)
        assertEquals(Contrast.opposite(yellow, Contrast.DEFAULT_CANDIDATES), resolved.outline)
    }

    @Test
    fun externalColourAlsoBeatsAFixedColour() {
        val resolved = TextStyling.VANILLA.withColor(TextPaint.of(0x123456)).resolve(GOLD, 0xFFFFFF)
        assertEquals(ResolvedText.UNSET, resolved.color)
    }

    @Test
    fun nothingBehindTheNameNeedsNoDecoration() {
        val resolved = auto().resolve(NOTHING, NO_EXTERNAL)
        assertEquals(WHITE, resolved.color)
        assertEquals(Contrast.Decoration.NONE, resolved.decoration)
        assertEquals(ResolvedText.UNSET, resolved.outline)
    }

    @Test
    fun formattingPolicyIsCarried() {
        for (formatting in TextStyling.Formatting.entries) {
            assertEquals(formatting, TextStyling.VANILLA.withFormatting(formatting).resolve(GOLD, NO_EXTERNAL).formatting)
        }
    }

    @Test
    fun adjustMovesAFormattingColourUntilItReads() {
        val resolved = TextStyling.VANILLA.withFormatting(TextStyling.Formatting.ADJUST).resolve(GOLD, NO_EXTERNAL)
        val gold = 0xFFAA00
        val adjusted = resolved.adjust(gold)
        assertNotEquals(gold, adjusted)
        assertEquals(Contrast.adjust(gold, GOLD, GOLD.size).color, adjusted)
        assertEquals(adjusted, resolved.adjust(gold))

        val readable = 0x000000
        assertEquals(readable, resolved.adjust(readable))
        assertEquals(0, resolved.adjustShadow(readable))
    }

    @Test
    fun stylingNeedsACandidate() {
        assertFailsWith<IllegalArgumentException> { TextStyling.VANILLA.withCandidates() }
    }

    private fun auto(): TextStyling =
        TextStyling.VANILLA.withColor(TextPaint.AUTO).withShadow(TextPaint.AUTO).withOutline(TextPaint.AUTO)

    private fun opaque(rgb: Int): Int = rgb or -0x1000000

    private companion object {
        const val WHITE = 0xFFFFFF
        const val NEAR_BLACK = 0x1A1A1A
        const val NO_EXTERNAL = ResolvedText.UNSET
        val GOLD = intArrayOf(0xFFF6D8, 0xFFE3A0)
        val GREY = intArrayOf(0x777777)
        val SPLIT = intArrayOf(0x000000, 0xFFFFFF)
        val NOTHING = IntArray(0)
    }
}
