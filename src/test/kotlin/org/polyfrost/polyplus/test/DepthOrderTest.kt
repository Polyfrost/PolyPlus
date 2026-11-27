package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.render.DepthOrder

class DepthOrderTest {
    @Test
    fun `quads sort farthest first in a perspective view`() {
        val distances = floatArrayOf(4f, 0.25f, 81f, 9f, 0f)
        val keys = LongArray(distances.size) { DepthOrder.key(-distances[it], it) }
        keys.sort()
        assertEquals(listOf(2, 3, 0, 1, 4), keys.map(DepthOrder::index))
    }

    @Test
    fun `quads sort from the most negative eye depth in an orthographic view`() {
        val depths = floatArrayOf(-1f, 2f, -300f, 0.5f, -0f)
        val keys = LongArray(depths.size) { DepthOrder.key(depths[it], it) }
        keys.sort()
        assertEquals(listOf(2, 0, 4, 3, 1), keys.map(DepthOrder::index))
    }
}
