package org.polyfrost.polyplus.client.ping

internal const val PING_GLYPH_SIZE = 9

internal class PingGlyphRects(val ink: IntArray, val fill: IntArray)

private val cache by lazy { PingKind.entries.map { buildPingGlyphRects(pingGlyph(it)) } }

internal fun pingGlyphRects(kind: PingKind): PingGlyphRects = cache[kind.ordinal]

internal fun buildPingGlyphRects(rows: Array<String>): PingGlyphRects {
    val n = PING_GLYPH_SIZE + 2
    fun filled(x: Int, y: Int): Boolean {
        if (x < 1 || y < 1 || x > n - 2 || y > n - 2) return false
        return rows[y - 1][x - 1] == '#'
    }
    fun outlined(x: Int, y: Int): Boolean {
        if (filled(x, y)) return false
        for (dy in -1..1) for (dx in -1..1) if (filled(x + dx, y + dy)) return true
        return false
    }
    return PingGlyphRects(rects(n, ::outlined), rects(n, ::filled))
}

private fun rects(n: Int, set: (Int, Int) -> Boolean): IntArray {
    val out = ArrayList<IntArray>()
    var open = HashMap<Long, IntArray>()
    for (y in 0 until n) {
        val next = HashMap<Long, IntArray>()
        var x = 0
        while (x < n) {
            if (!set(x, y)) {
                x++
                continue
            }
            val start = x
            while (x < n && set(x, y)) x++
            val key = (start.toLong() shl 32) or x.toLong()
            next[key] = open[key]?.also { it[3] = y + 1 } ?: intArrayOf(start, y, x, y + 1).also(out::add)
        }
        open = next
    }
    return out.flatMap { it.asList() }.toIntArray()
}

internal fun pingGlyph(kind: PingKind): Array<String> = when (kind) {
    PingKind.GO -> GLYPH_GO
    PingKind.ENEMY -> GLYPH_ENEMY
    PingKind.LOOK -> GLYPH_LOOK
    PingKind.LOOT -> GLYPH_LOOT
    PingKind.DANGER -> GLYPH_DANGER
}

private val GLYPH_GO = arrayOf(
    "#########",
    "#########",
    ".#######.",
    ".#######.",
    "..#####..",
    "..#####..",
    "...###...",
    "...###...",
    "....#....",
)

private val GLYPH_ENEMY = arrayOf(
    "##.....##",
    "###...###",
    ".###.###.",
    "..#####..",
    "...###...",
    "..#####..",
    ".###.###.",
    "###...###",
    "##.....##",
)

private val GLYPH_LOOK = arrayOf(
    "..#####..",
    ".#######.",
    "##.....##",
    "##.###.##",
    "##.###.##",
    "##.###.##",
    "##.....##",
    ".#######.",
    "..#####..",
)

private val GLYPH_LOOT = arrayOf(
    "....#....",
    "...###...",
    "..#####..",
    ".#######.",
    "#########",
    ".#######.",
    "..#####..",
    "...###...",
    "....#....",
)

private val GLYPH_DANGER = arrayOf(
    "...###...",
    "...###...",
    "...###...",
    "...###...",
    "...###...",
    "...###...",
    ".........",
    "...###...",
    "...###...",
)
