package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.utils.ClientPlatform

class UnlimitedFramerateTest {
    private fun highest(accepts: (Int) -> Boolean) = ClientPlatform.highestAccepted(260, 10_000, accepts)

    @Test
    fun `finds the top of the option range`() {
        for (top in listOf(260, 1001, 1500, 10_000)) {
            assertEquals(top, highest { it in 10..top })
        }
    }

    @Test
    fun `clamps a range above the ceiling`() {
        assertEquals(10_000, highest { it in 10..1_000_000 })
    }

    @Test
    fun `stays on the floor when nothing is accepted`() {
        assertEquals(260, highest { false })
    }
}
