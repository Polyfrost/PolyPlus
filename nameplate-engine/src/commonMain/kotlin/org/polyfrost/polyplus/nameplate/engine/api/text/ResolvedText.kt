package org.polyfrost.polyplus.nameplate.engine.api.text

class ResolvedText internal constructor(
    val color: Int,
    val shadow: Int,
    val shadowUse: ShadowUse,
    val outline: Int,
    val formatting: TextStyling.Formatting,
    internal val minRatio: Double,
    val decoration: Contrast.Decoration,
    private val pixels: IntArray,
    private val candidates: IntArray,
) {
    private val adjusted = HashMap<Int, Contrast.Adjusted>()

    fun adjust(rgb: Int): Int = adjusted(rgb).color

    fun adjustShadow(rgb: Int): Int {
        val result = adjusted(rgb)
        if (result.decoration == Contrast.Decoration.NONE) return 0

        return OPAQUE or Contrast.opposite(result.color, candidates)
    }

    private fun adjusted(rgb: Int): Contrast.Adjusted {
        val key = rgb and 0xFFFFFF
        return adjusted.getOrPut(key) { Contrast.adjust(key, pixels, pixels.size) }
    }

    enum class ShadowUse {
        LEAVE,
        ALWAYS,
        WITH_DROP_SHADOW,
        REMOVE,
    }

    companion object {
        const val UNSET = -1

        internal const val OPAQUE = -0x1000000

        val VANILLA: ResolvedText = ResolvedText(
            color = UNSET,
            shadow = 0,
            shadowUse = ShadowUse.LEAVE,
            outline = UNSET,
            formatting = TextStyling.Formatting.KEEP,
            minRatio = 21.0,
            decoration = Contrast.Decoration.NONE,
            pixels = IntArray(0),
            candidates = Contrast.DEFAULT_CANDIDATES,
        )
    }
}
