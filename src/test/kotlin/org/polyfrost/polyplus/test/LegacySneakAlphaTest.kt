package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.plusrenderer.Nameplates
import org.polyfrost.polyplus.client.nameplate.material.NameplatePrograms
import org.polyfrost.polyplus.client.nameplate.material.NameplateShader
import org.polyfrost.polyplus.client.nameplate.render.LegacySneakAlpha
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList
import kotlin.math.abs

class LegacySneakAlphaTest {
    private val background = NameplateDrawList.BAND_BACKGROUND
    private val overText = NameplateDrawList.BAND_OVER_TEXT

    private fun plate(vararg rects: IntArray): NameplateDrawList = NameplateDrawList().apply {
        for (r in rects) rect(r[0], r[1], r[2], r[3], r[4], r[5])
    }

    private fun box(argb: Int, band: Int = background, x0: Int = -1, y0: Int = -1, x1: Int = 41, y1: Int = 8) =
        intArrayOf(x0, y0, x1, y1, argb, band)

    private fun alpha(list: NameplateDrawList, rowAlpha: Int = 0xFF, plateAlpha: Float = 1f, vanilla: Boolean = false) =
        LegacySneakAlpha.of(list, -1, -1, 41, 8, rowAlpha, plateAlpha, vanilla)

    @Test
    fun `the score maps from the vanilla alpha to the readable one`() {
        assertEquals(0x20, LegacySneakAlpha.fromScore(0f))
        assertEquals(0x7F, LegacySneakAlpha.fromScore(1f))
        assertEquals(0x20, LegacySneakAlpha.fromScore(-1f))
        assertEquals(0x7F, LegacySneakAlpha.fromScore(3f))
        var last = 0
        for (step in 0..10) {
            val next = LegacySneakAlpha.fromScore(step / 10f)
            assertTrue(next >= last)
            last = next
        }
    }

    @Test
    fun `a clear plate keeps the vanilla sneaking alpha`() {
        assertEquals(LegacySneakAlpha.CLEAR, alpha(plate()))
        assertEquals(LegacySneakAlpha.CLEAR, alpha(plate(), vanilla = true))
    }

    @Test
    fun `a plate as dark and clear as the vanilla box keeps the vanilla alpha`() {
        assertEquals(LegacySneakAlpha.CLEAR, alpha(plate(box(0x40000000))))
    }

    @Test
    fun `an opaque bright plate is fully readable`() {
        assertEquals(LegacySneakAlpha.READABLE, alpha(plate(box(0xFFFFFFFF.toInt()))))
    }

    @Test
    fun `an opaque dark plate is raised too`() {
        assertEquals(LegacySneakAlpha.READABLE, alpha(plate(box(0xFF000000.toInt()))))
    }

    @Test
    fun `a see-through bright plate lands in between`() {
        val value = alpha(plate(box(0x60FFFFFF)))
        assertTrue(value > LegacySneakAlpha.CLEAR && value < LegacySneakAlpha.READABLE, "alpha $value")
    }

    @Test
    fun `the plate opacity setting lowers the result`() {
        val full = alpha(plate(box(0xFF000000.toInt())))
        val dimmed = alpha(plate(box(0xFF000000.toInt())), plateAlpha = 0.7f)
        assertTrue(dimmed < full, "$dimmed vs $full")
        assertTrue(dimmed > LegacySneakAlpha.CLEAR)
    }

    @Test
    fun `a plate covering half the name counts for less`() {
        val whole = alpha(plate(box(0x80FFFFFF.toInt())))
        val half = alpha(plate(box(0x80FFFFFF.toInt(), x1 = 20)))
        assertTrue(half < whole, "$half vs $whole")
        assertTrue(half > LegacySneakAlpha.CLEAR)
    }

    @Test
    fun `parts away from the name or drawn over it are ignored`() {
        assertEquals(LegacySneakAlpha.CLEAR, alpha(plate(box(0xFFFFFFFF.toInt(), x0 = 50, x1 = 90))))
        assertEquals(LegacySneakAlpha.CLEAR, alpha(plate(box(0xFFFFFFFF.toInt(), y0 = 9, y1 = 20))))
        assertEquals(LegacySneakAlpha.CLEAR, alpha(plate(box(0xFFFFFFFF.toInt(), band = overText))))
    }

    private fun shader(additive: Boolean) =
        NameplateShader("m", 0, "", "", emptyArray(), 1, additive, !additive, emptyArray(), true)

    private fun withMaterial(additive: Boolean, opacity: Int = 0xFF, band: Int = background): NameplateDrawList = NameplateDrawList().apply {
        val programs = NameplatePrograms(arrayOf(shader(additive)), arrayOf(Any()), emptyArray(), true)
        val word = band or (opacity shl Nameplates.MAT_OPACITY_SHIFT)
        material(programs, 0, -1, -1, 41, 8, 0f, 0f, 1f, 1f, word, IntArray(1), 0, 0, 0, 1)
    }

    @Test
    fun `an opaque material plate reads as readable`() {
        assertEquals(LegacySneakAlpha.READABLE, alpha(withMaterial(additive = false)))
    }

    @Test
    fun `glow and over text materials don't count toward the plate`() {
        assertEquals(LegacySneakAlpha.CLEAR, alpha(withMaterial(additive = true)))
        assertEquals(LegacySneakAlpha.CLEAR, alpha(withMaterial(additive = false, band = overText)))
    }

    @Test
    fun `a faint material plate stays near the vanilla alpha`() {
        val read = alpha(withMaterial(additive = false, opacity = 0x40))
        assertTrue(read < LegacySneakAlpha.CLEAR + 8, "alpha $read")
    }

    @Test
    fun `a plate already faded by the last alpha reads the same as unfaded`() {
        val argb = 0x50808080
        val unfaded = alpha(plate(box(argb)))
        for (row in intArrayOf(0x20, 0x40, 0x7F)) {
            val faded = (((argb ushr 24) * row + 127) / 255 shl 24) or (argb and 0xFFFFFF)
            val read = alpha(plate(box(faded)), rowAlpha = row)
            assertTrue(abs(read - unfaded) <= 3, "row $row read $read vs $unfaded")
        }
        assertTrue(unfaded > LegacySneakAlpha.CLEAR && unfaded < LegacySneakAlpha.READABLE, "alpha $unfaded")
    }
}
