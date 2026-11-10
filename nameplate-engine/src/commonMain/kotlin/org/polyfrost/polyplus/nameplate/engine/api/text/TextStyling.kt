package org.polyfrost.polyplus.nameplate.engine.api.text

class TextStyling(
    val color: TextPaint,
    val shadow: TextPaint,
    val outline: TextPaint,
    val formatting: Formatting,
    candidates: IntArray,
    backdropHint: IntArray,
) {
    private val candidates: IntArray = candidates.copyOf()
    private val backdropHint: IntArray = backdropHint.copyOf()

    init {
        require(candidates.isNotEmpty()) { "Auto contrast needs at least one candidate" }
    }

    val needsBackdrop: Boolean
        get() = color.kind == TextPaint.Kind.AUTO ||
            shadow.kind == TextPaint.Kind.AUTO ||
            outline.kind == TextPaint.Kind.AUTO ||
            formatting == Formatting.ADJUST

    fun candidates(): IntArray = candidates.copyOf()

    fun backdropHint(): IntArray = backdropHint.copyOf()

    fun withColor(color: TextPaint): TextStyling =
        TextStyling(color, shadow, outline, formatting, candidates, backdropHint)

    internal fun withShadow(shadow: TextPaint): TextStyling =
        TextStyling(color, shadow, outline, formatting, candidates, backdropHint)

    internal fun withOutline(outline: TextPaint): TextStyling =
        TextStyling(color, shadow, outline, formatting, candidates, backdropHint)

    internal fun withFormatting(formatting: Formatting): TextStyling =
        TextStyling(color, shadow, outline, formatting, candidates, backdropHint)

    internal fun withCandidates(vararg candidates: Int): TextStyling =
        TextStyling(color, shadow, outline, formatting, candidates, backdropHint)

    internal fun withBackdropHint(vararg backdropHint: Int): TextStyling =
        TextStyling(color, shadow, outline, formatting, candidates, backdropHint)

    fun resolve(pixels: IntArray, externalRgb: Int): ResolvedText {
        var glyphColor = ResolvedText.UNSET
        val textRgb: Int
        val minRatio: Double
        when {
            externalRgb != ResolvedText.UNSET -> {
                textRgb = externalRgb and 0xFFFFFF
                minRatio = minRatio(textRgb, pixels)
            }
            color.kind == TextPaint.Kind.AUTO -> {
                val choice = Contrast.choose(pixels, pixels.size, candidates)
                glyphColor = choice.color
                textRgb = glyphColor
                minRatio = choice.minRatio
            }
            color.kind == TextPaint.Kind.FIXED -> {
                glyphColor = color.rgb
                textRgb = glyphColor
                minRatio = minRatio(textRgb, pixels)
            }
            else -> {
                textRgb = Contrast.WHITE
                minRatio = minRatio(textRgb, pixels)
            }
        }

        val decoration = Contrast.decorationFor(minRatio)
        val opposite = Contrast.opposite(textRgb, candidates)

        val outlined = when (outline.kind) {
            TextPaint.Kind.FIXED -> true
            TextPaint.Kind.AUTO -> decoration == Contrast.Decoration.OUTLINE ||
                decoration == Contrast.Decoration.SHADOW && shadow.kind == TextPaint.Kind.NONE
            TextPaint.Kind.NONE -> false
        }
        val outlineRgb = when {
            outline.kind == TextPaint.Kind.FIXED -> outline.rgb
            outlined -> opposite
            else -> ResolvedText.UNSET
        }

        var shadowRgb = 0
        val shadowUse = when {
            outlined -> ResolvedText.ShadowUse.REMOVE
            shadow.kind == TextPaint.Kind.FIXED -> {
                shadowRgb = shadow.rgb
                ResolvedText.ShadowUse.ALWAYS
            }
            shadow.kind == TextPaint.Kind.AUTO -> {
                shadowRgb = opposite
                if (decoration == Contrast.Decoration.NONE) {
                    ResolvedText.ShadowUse.WITH_DROP_SHADOW
                } else {
                    ResolvedText.ShadowUse.ALWAYS
                }
            }
            else -> ResolvedText.ShadowUse.LEAVE
        }

        val drawsShadow = shadowUse == ResolvedText.ShadowUse.ALWAYS ||
            shadowUse == ResolvedText.ShadowUse.WITH_DROP_SHADOW
        val shadowArgb = if (drawsShadow) ResolvedText.OPAQUE or shadowRgb else 0
        val resolvedFormatting = if (formatting == Formatting.ADJUST && pixels.isEmpty()) {
            Formatting.KEEP
        } else {
            formatting
        }
        return ResolvedText(
            color = glyphColor,
            shadow = shadowArgb,
            shadowUse = shadowUse,
            outline = outlineRgb,
            formatting = resolvedFormatting,
            minRatio = minRatio,
            decoration = decoration,
            pixels = if (resolvedFormatting == Formatting.ADJUST) pixels.copyOf() else IntArray(0),
            candidates = candidates,
        )
    }

    enum class Formatting {
        KEEP,
        OVERRIDE,
        ADJUST,
    }

    companion object {
        val VANILLA: TextStyling = TextStyling(
            TextPaint.NONE,
            TextPaint.NONE,
            TextPaint.NONE,
            Formatting.KEEP,
            Contrast.DEFAULT_CANDIDATES,
            IntArray(0),
        )

        val DEFAULT: TextStyling = VANILLA.withFormatting(Formatting.ADJUST)

        private fun minRatio(rgb: Int, pixels: IntArray): Double =
            Contrast.choose(pixels, pixels.size, intArrayOf(rgb)).minRatio
    }
}
