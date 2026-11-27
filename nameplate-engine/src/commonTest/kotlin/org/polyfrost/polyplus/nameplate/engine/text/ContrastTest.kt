package org.polyfrost.polyplus.nameplate.engine.text

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.internal.color.Oklab
import org.polyfrost.polyplus.nameplate.engine.support.contrastRatio

class ContrastTest {
    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21.0, contrastRatio(0x000000, WHITE), 1e-9)
        assertEquals(21.0, contrastRatio(WHITE, 0x000000), 1e-9)
        assertEquals(1.0, contrastRatio(0x336699, 0x336699), 1e-9)
    }

    @Test
    fun grey767676OnWhiteIsAboutFourPointFiveFour() {
        assertEquals(4.54, contrastRatio(0x767676, WHITE), 0.005)
    }

    @Test
    fun luminanceEndpointsAndAlphaIgnored() {
        assertEquals(0.0, Contrast.luminance(0x000000), 1e-12)
        assertEquals(1.0, Contrast.luminance(WHITE), 1e-9)
        assertEquals(Contrast.luminance(0x336699), Contrast.luminance(0x80336699.toInt()), 1e-12)
    }

    @Test
    fun compositeOverBlendsPerChannel() {
        assertEquals(0x000000, Contrast.compositeOver(0x00FFFFFF, 0x000000) and 0xFFFFFF)
        assertEquals(WHITE, Contrast.compositeOver(0xFFFFFFFF.toInt(), 0x000000) and 0xFFFFFF)
        assertEquals(0x808080, Contrast.compositeOver(0x80FFFFFF.toInt(), 0x000000) and 0xFFFFFF)
    }

    @Test
    fun darkBackgroundPicksWhiteWithNoDecoration() {
        val pixels = IntArray(64) { 0x202020 }
        val choice = Contrast.choose(pixels, pixels.size, Contrast.DEFAULT_CANDIDATES)
        assertEquals(WHITE, choice.color)
        assertEquals(NEAR_BLACK, Contrast.opposite(choice.color, Contrast.DEFAULT_CANDIDATES))
        assertEquals(Contrast.Decoration.NONE, choice.decoration)
        assertEquals(contrastRatio(WHITE, 0x202020), choice.minRatio, 1e-9)
    }

    @Test
    fun lightBackgroundPicksNearBlack() {
        val pixels = IntArray(64) { 0xF0F0F0 }
        val choice = Contrast.choose(pixels, pixels.size, Contrast.DEFAULT_CANDIDATES)
        assertEquals(NEAR_BLACK, choice.color)
        assertEquals(WHITE, Contrast.opposite(choice.color, Contrast.DEFAULT_CANDIDATES))
        assertEquals(Contrast.Decoration.NONE, choice.decoration)
    }

    @Test
    fun midGreyBackgroundForcesShadow() {
        val pixels = IntArray(64) { 0x7C7C7C }
        val choice = Contrast.choose(pixels, pixels.size, Contrast.DEFAULT_CANDIDATES)
        assertTrue(choice.minRatio >= 3.0 && choice.minRatio < 4.5, "ratio ${choice.minRatio}")
        assertEquals(Contrast.Decoration.SHADOW, choice.decoration)
        val other = contrastRatio(choice.color, Contrast.opposite(choice.color, Contrast.DEFAULT_CANDIDATES))
        assertTrue(other > 10, "shadow must be the opposite tone, ratio $other")
    }

    @Test
    fun stripedBlackAndWhiteBackgroundForcesOutline() {
        val pixels = stripes(64, 0x000000, WHITE)
        val choice = Contrast.choose(pixels, pixels.size, Contrast.DEFAULT_CANDIDATES)
        assertTrue(choice.minRatio < 3.0, "ratio ${choice.minRatio}")
        assertEquals(Contrast.Decoration.OUTLINE, choice.decoration)
        assertTrue(contrastRatio(choice.color, Contrast.opposite(choice.color, Contrast.DEFAULT_CANDIDATES)) > 10)
    }

    @Test
    fun minimumOverPixelsBeatsAverage() {
        val pixels = IntArray(100)
        pixels[42] = 0xF0F0F0
        val grey = 0x777777
        var whiteAverage = 0.0
        var greyAverage = 0.0
        for (pixel in pixels) {
            whiteAverage += contrastRatio(WHITE, pixel) / pixels.size
            greyAverage += contrastRatio(grey, pixel) / pixels.size
        }

        assertTrue(whiteAverage > 4.5 && whiteAverage > greyAverage, "premise: white wins on average")
        assertTrue(minRatio(WHITE, pixels, pixels.size) < 2.0, "premise: white fails on one pixel")

        val choice = Contrast.choose(pixels, pixels.size, intArrayOf(WHITE, grey))
        assertEquals(grey, choice.color)
        assertEquals(minRatio(grey, pixels, pixels.size), choice.minRatio, 1e-9)
    }

    @Test
    fun countLimitsWhichPixelsAreRead() {
        val pixels = IntArray(8) { if (it < 4) 0x202020 else WHITE }
        val head = Contrast.choose(pixels, 4, Contrast.DEFAULT_CANDIDATES)
        assertEquals(WHITE, head.color)
        assertEquals(Contrast.Decoration.NONE, head.decoration)
    }

    @Test
    fun emptyPixelSetReturnsFirstCandidate() {
        val choice = Contrast.choose(IntArray(0), 0, intArrayOf(0x123456, WHITE))
        assertEquals(0x123456, choice.color)
        assertEquals(Contrast.Decoration.NONE, choice.decoration)
    }

    @Test
    fun adjustKeepsHueAndDarkensToReachFourPointFive() {
        val blue = 0x3A7BD5
        val pixels = IntArray(32) { WHITE }
        assertTrue(contrastRatio(blue, WHITE) < 4.5, "premise")

        val adjusted = Contrast.adjust(blue, pixels, pixels.size)
        assertEquals(Contrast.Decoration.NONE, adjusted.decoration)
        assertTrue(minRatio(adjusted.color, pixels, pixels.size) >= 4.5, "ratio ${minRatio(adjusted.color, pixels, pixels.size)}")
        assertTrue(Oklab.lightness(adjusted.color) < Oklab.lightness(blue))
        assertEquals(0.0, hueDifferenceDegrees(blue, adjusted.color), 2.5)
    }

    @Test
    fun adjustKeepsHueAndLightensOnDarkBackground() {
        val navy = 0x203060
        val pixels = IntArray(32) { 0x101010 }

        val adjusted = Contrast.adjust(navy, pixels, pixels.size)
        assertEquals(Contrast.Decoration.NONE, adjusted.decoration)
        assertTrue(minRatio(adjusted.color, pixels, pixels.size) >= 4.5, "ratio ${minRatio(adjusted.color, pixels, pixels.size)}")
        assertTrue(Oklab.lightness(adjusted.color) > Oklab.lightness(navy))
        assertEquals(0.0, hueDifferenceDegrees(navy, adjusted.color), 2.5)
    }

    @Test
    fun adjustLeavesAnAlreadyReadableColourAlone() {
        val pixels = IntArray(32) { WHITE }
        val adjusted = Contrast.adjust(0x203060, pixels, pixels.size)
        assertEquals(0x203060, adjusted.color and 0xFFFFFF)
        assertEquals(Contrast.Decoration.NONE, adjusted.decoration)
    }

    @Test
    fun adjustFindsALightnessBetweenBlackAndWhitePixels() {
        val pixels = stripes(64, 0x000000, WHITE)

        val adjusted = Contrast.adjust(0xFF4040, pixels, pixels.size)
        assertTrue(minRatio(adjusted.color, pixels, pixels.size) >= 4.5, "ratio ${minRatio(adjusted.color, pixels, pixels.size)}")
        assertEquals(Contrast.Decoration.NONE, adjusted.decoration)
    }

    @Test
    fun adjustReportsOutlineWhenNoLightnessWorks() {
        val pixels = stripes(63, 0x000000, 0x808080, WHITE)

        val adjusted = Contrast.adjust(0xFF4040, pixels, pixels.size)
        assertTrue(minRatio(adjusted.color, pixels, pixels.size) < 3.0, "ratio ${minRatio(adjusted.color, pixels, pixels.size)}")
        assertEquals(Contrast.Decoration.OUTLINE, adjusted.decoration)
        for (level in 0 until 256 step 5) {
            val grey = level * 0x010101
            assertTrue(
                minRatio(grey, pixels, pixels.size) <= minRatio(adjusted.color, pixels, pixels.size) + 0.05,
                "a grey ${grey.toString(16)} beats the reported best",
            )
        }
    }

    @Test
    fun adjustReportsItsOwnRatioHonestlyOnRandomBackdrops() {
        val random = Random(99)
        repeat(200) {
            val pixels = IntArray(1 + random.nextInt(40)) { random.nextInt() and 0xFFFFFF }
            val input = random.nextInt() and 0xFFFFFF
            val adjusted = Contrast.adjust(input, pixels, pixels.size)
            val actual = minRatio(adjusted.color, pixels, pixels.size)
            assertEquals(Contrast.decorationFor(actual), adjusted.decoration)
            assertTrue(actual >= minRatio(input, pixels, pixels.size) - 1e-9, "never worse than the input")
        }
    }

    private fun stripes(count: Int, vararg colors: Int): IntArray = IntArray(count) { colors[it % colors.size] }

    private fun minRatio(color: Int, pixels: IntArray, count: Int): Double {
        var min = Double.MAX_VALUE
        for (i in 0 until count) {
            min = minOf(min, contrastRatio(color, pixels[i]))
        }

        return min
    }

    private fun hueDifferenceDegrees(a: Int, b: Int): Double {
        val ha = atan2(Oklab.b(a), Oklab.a(a))
        val hb = atan2(Oklab.b(b), Oklab.a(b))
        var d = (ha - hb) * 180.0 / PI
        while (d > 180) {
            d -= 360
        }

        while (d < -180) {
            d += 360
        }

        return d
    }

    private companion object {
        const val WHITE = 0xFFFFFF
        const val NEAR_BLACK = 0x1A1A1A
    }
}
