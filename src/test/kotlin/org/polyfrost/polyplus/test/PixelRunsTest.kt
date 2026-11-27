package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.emotes.duo.PixelRuns

class PixelRunsTest {
    private fun rects(runs: PixelRuns): List<List<Int>> {
        val out = ArrayList<List<Int>>()
        runs.forEach { x0, y0, x1, y1 -> out += listOf(x0, y0, x1, y1) }
        return out
    }

    @Test
    fun `a horizontal line becomes one rectangle`() {
        val runs = PixelRuns()
        for (x in 10..209) runs.add(x, 5)
        assertEquals(listOf(listOf(10, 5, 210, 6)), rects(runs))
    }

    @Test
    fun `lines drawn backwards and vertically merge too`() {
        val runs = PixelRuns()
        for (x in 20 downTo 0) runs.add(x, 3)
        for (y in 4..30) runs.add(0, y)
        for (y in 2 downTo -5) runs.add(50, y)
        assertEquals(listOf(listOf(0, 3, 21, 4), listOf(0, 4, 1, 31), listOf(50, -5, 51, 3)), rects(runs))
    }

    @Test
    fun `repeated pixels are drawn once`() {
        val runs = PixelRuns()
        runs.add(1, 1)
        runs.add(1, 1)
        runs.add(2, 2)
        runs.add(2, 2)
        runs.add(3, 3)
        assertEquals(3, runs.size)
    }

    @Test
    fun `growing past the initial capacity keeps every pixel`() {
        val runs = PixelRuns()
        for (i in 0 until 1_000) runs.add(i, i)
        assertEquals(1_000, runs.size)
        val all = rects(runs)
        assertEquals(listOf(999, 999, 1_000, 1_000), all.last())
        runs.clear()
        assertEquals(0, runs.size)
    }
}
