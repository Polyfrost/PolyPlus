package org.polyfrost.polyplus.nameplate.engine.api.text

import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette

class BadgeStyling(
    val tint: Tint,
    val decoration: Decoration,
    val placement: Placement,
    candidates: IntArray,
) {
    private val candidates: IntArray = candidates.copyOf()

    init {
        require(candidates.isNotEmpty()) { "Auto decoration needs at least one candidate" }
    }

    fun resolve(pixels: IntArray): ResolvedBadge {
        val tintRgb = if (tint.kind == Tint.Kind.NONE) ResolvedText.UNSET else tint.rgb
        val badgeRgb = if (tintRgb == ResolvedText.UNSET) ART_RGB else tintRgb
        val choice = Contrast.choose(pixels, pixels.size, intArrayOf(badgeRgb))
        if (decoration.kind != Decoration.Kind.OUTLINE && choice.minRatio < Contrast.LARGE_TEXT) {
            return ResolvedBadge(tintRgb, Contrast.Decoration.OUTLINE, Contrast.opposite(badgeRgb, candidates))
        }
        return when (decoration.kind) {
            Decoration.Kind.NONE -> ResolvedBadge(tintRgb, Contrast.Decoration.NONE, 0)
            Decoration.Kind.SHADOW -> ResolvedBadge(tintRgb, Contrast.Decoration.SHADOW, decoration.rgb)
            Decoration.Kind.OUTLINE -> ResolvedBadge(tintRgb, Contrast.Decoration.OUTLINE, decoration.rgb)
            Decoration.Kind.AUTO -> ResolvedBadge(tintRgb, choice.decoration, Contrast.opposite(badgeRgb, candidates))
        }
    }

    class Tint private constructor(val kind: Kind, val rgb: Int) {
        enum class Kind {
            NONE,
            FIXED,
            SLOT,
        }

        companion object {
            val NONE: Tint = Tint(Kind.NONE, 0)

            fun of(rgb: Int): Tint = Tint(Kind.FIXED, rgb and 0xFFFFFF)

            fun slot(palette: ResolvedPalette, ref: Int): Tint = Tint(Kind.SLOT, palette.color(ref) and 0xFFFFFF)
        }
    }

    class Decoration private constructor(val kind: Kind, val rgb: Int) {
        enum class Kind {
            NONE,
            SHADOW,
            OUTLINE,
            AUTO,
        }

        companion object {
            val NONE: Decoration = Decoration(Kind.NONE, 0)
            internal val AUTO: Decoration = Decoration(Kind.AUTO, 0)

            fun shadow(rgb: Int): Decoration = Decoration(Kind.SHADOW, rgb and 0xFFFFFF)

            fun outline(rgb: Int): Decoration = Decoration(Kind.OUTLINE, rgb and 0xFFFFFF)
        }
    }

    class Placement private constructor(val kind: Kind, val anchor: Anchor?) {
        val keepsGap: Boolean
            get() = kind != Kind.PLACED

        enum class Kind {
            RESERVE,
            CARRIED,
            PLACED,
        }

        companion object {
            internal val RESERVE: Placement = Placement(Kind.RESERVE, null)
            internal val CARRIED: Placement = Placement(Kind.CARRIED, null)

            fun placed(anchor: Anchor): Placement = Placement(Kind.PLACED, anchor)
        }
    }

    interface Anchor {
        fun x(block: TagBlock): Int

        fun y(block: TagBlock): Int
    }

    companion object {
        const val ART_RGB = 0xD6D6D6
        const val CELLS_WIDE = 9
        const val CELLS_HIGH = 8
    }
}
