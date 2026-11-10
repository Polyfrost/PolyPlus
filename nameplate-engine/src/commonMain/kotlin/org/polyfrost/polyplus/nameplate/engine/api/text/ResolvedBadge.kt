package org.polyfrost.polyplus.nameplate.engine.api.text

data class ResolvedBadge(
    val tint: Int,
    val decoration: Contrast.Decoration,
    val decorationRgb: Int,
    internal val minRatio: Double,
) {
    companion object {
        internal val PLAIN: ResolvedBadge = ResolvedBadge(ResolvedText.UNSET, Contrast.Decoration.NONE, 0, 21.0)
    }
}
