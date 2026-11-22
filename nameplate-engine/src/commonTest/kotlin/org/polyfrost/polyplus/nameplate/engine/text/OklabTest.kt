package org.polyfrost.polyplus.nameplate.engine.text

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.internal.color.Oklab

class OklabTest {
    @Test
    fun roundTripsPrimariesAndGreys() {
        val samples = intArrayOf(
            0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0x00FFFF, 0xFF00FF,
            0x808080, 0x1A1A1A, 0x767676, 0x3A7BD5,
        )
        for (rgb in samples) {
            assertRoundTrip(rgb)
        }
    }

    @Test
    fun roundTripsRandomColours() {
        val random = Random(1234)
        repeat(2000) {
            assertRoundTrip(random.nextInt() and 0xFFFFFF)
        }
    }

    @Test
    fun whiteIsLightnessOneAndNeutral() {
        assertEquals(1.0, Oklab.lightness(0xFFFFFF), 1e-3)
        assertEquals(0.0, Oklab.a(0xFFFFFF), 1e-3)
        assertEquals(0.0, Oklab.b(0xFFFFFF), 1e-3)
        assertEquals(0.0, Oklab.lightness(0x000000), 1e-9)
    }

    private fun assertRoundTrip(rgb: Int) {
        val back = Oklab.toRgb(Oklab.lightness(rgb), Oklab.a(rgb), Oklab.b(rgb))
        for (shift in 0..16 step 8) {
            val expected = (rgb shr shift) and 0xFF
            val actual = (back shr shift) and 0xFF
            assertTrue(abs(expected - actual) <= 1, "round trip of ${rgb.toString(16)} gave ${back.toString(16)}")
        }
    }
}
