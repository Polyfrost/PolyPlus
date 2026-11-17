package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.ping.PING_GLYPH_SIZE
import org.polyfrost.polyplus.client.ping.PingKind
import org.polyfrost.polyplus.client.ping.buildPingGlyphRects
import org.polyfrost.polyplus.client.ping.pingGlyph

class PingGlyphRectsTest {
    private fun coverage(rects: IntArray, n: Int): Array<IntArray> {
        val grid = Array(n) { IntArray(n) }
        for (i in rects.indices step 4) {
            for (y in rects[i + 1] until rects[i + 3]) for (x in rects[i] until rects[i + 2]) grid[y][x]++
        }
        return grid
    }

    @Test
    fun `rects match per-cell fills without overlapping`() {
        for (kind in PingKind.entries) {
            val rows = pingGlyph(kind)
            val n = PING_GLYPH_SIZE + 2
            val expectedFill = Array(n) { BooleanArray(n) }
            val expectedAny = Array(n) { BooleanArray(n) }
            for ((cy, row) in rows.withIndex()) for ((cx, cell) in row.withIndex()) {
                if (cell != '#') continue
                for (y in cy until cy + 3) for (x in cx until cx + 3) expectedAny[y][x] = true
                expectedFill[cy + 1][cx + 1] = true
            }

            val glyph = buildPingGlyphRects(rows)
            val fill = coverage(glyph.fill, n)
            val ink = coverage(glyph.ink, n)
            for (y in 0 until n) for (x in 0 until n) {
                val at = "$kind ($x, $y)"
                assertEquals(if (expectedFill[y][x]) 1 else 0, fill[y][x], at)
                assertEquals(if (expectedAny[y][x] && !expectedFill[y][x]) 1 else 0, ink[y][x], at)
            }
            val cells = rows.sumOf { row -> row.count { it == '#' } }
            assertTrue(glyph.fill.size / 4 + glyph.ink.size / 4 < cells * 2, "$kind not merged")
        }
    }
}
