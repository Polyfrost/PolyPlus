package org.polyfrost.polyplus.nameplate.engine.internal.canvas

internal class PixelSprite(rows: List<String>, palette: Map<Char, Int>) {
    private val rows: List<String> = rows.toList()
    private val palette: Map<Char, Int> = palette.toMap()

    init {
        require(this.rows.isNotEmpty()) { "Sprites need at least one row" }
        for (row in this.rows) {
            require(row.length == this.rows[0].length) { "Sprite rows must all be the same width" }
            for (c in row) {
                require(c == TRANSPARENT || c in this.palette) { "Sprite uses '$c', which is not in its palette" }
            }
        }
    }

    val width: Int
        get() = rows[0].length

    val height: Int
        get() = rows.size

    fun cell(x: Int, y: Int): Char = rows[y][x]

    fun color(char: Char): Int = palette.getValue(char)

    companion object {
        const val TRANSPARENT = '.'

        fun of(palette: Map<Char, Int>, vararg rows: String): PixelSprite = PixelSprite(rows.asList(), palette)
    }
}
