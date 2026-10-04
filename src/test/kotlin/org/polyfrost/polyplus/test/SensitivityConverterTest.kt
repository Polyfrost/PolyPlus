package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.SensitivityConverter
import org.polyfrost.polyplus.client.features.SensitivityConverter.PointerSettings
import kotlin.math.pow

class SensitivityConverterTest {
    private fun turnRate(percent: Double) = (percent / 200 * 0.6 + 0.2).pow(3)

    @Test
    fun `default pointer speed leaves the sensitivity alone`() {
        assertEquals(100.0, SensitivityConverter.convert(100.0, 10), 1e-9)
    }

    @Test
    fun `converted sensitivity turns as fast as the old one did with the pointer speed applied`() {
        val multipliers = mapOf(1 to 1 / 32.0, 2 to 1 / 16.0, 3 to 0.125, 6 to 0.5, 9 to 0.875, 10 to 1.0, 11 to 1.25, 14 to 2.0, 19 to 3.25, 20 to 3.5)
        for ((speed, multiplier) in multipliers) {
            for (percent in listOf(0.0, 37.0, 100.0, 200.0)) {
                val converted = SensitivityConverter.convert(percent, speed)
                assertEquals(turnRate(percent) * multiplier, turnRate(converted), 1e-9)
            }
        }
    }

    @Test
    fun `reads the pointer settings out of reg query output`() {
        fun output(sensitivity: Int, speed: Int) = "\r\nHKEY_CURRENT_USER\\Control Panel\\Mouse\r\n    MouseSensitivity    REG_SZ    $sensitivity\r\n    MouseSpeed    REG_SZ    $speed\r\n    MouseThreshold1    REG_SZ    6\r\n\r\n"
        assertEquals(PointerSettings(10, false), SensitivityConverter.parsePointerSettings(output(10, 0)))
        assertEquals(PointerSettings(11, true), SensitivityConverter.parsePointerSettings(output(11, 1)))
        assertEquals(PointerSettings(20, false), SensitivityConverter.parsePointerSettings(output(40, 0)))
        assertNull(SensitivityConverter.parsePointerSettings("ERROR: The system was unable to find the specified registry key or value."))
    }
}
