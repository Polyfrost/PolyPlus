package org.polyfrost.polyplus.nameplate.engine.text

import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.Contrast
import org.polyfrost.polyplus.nameplate.engine.api.text.TextPaint
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling

internal val RESERVE_BADGE: BadgeStyling =
    BadgeStyling(BadgeStyling.Tint.NONE, BadgeStyling.Decoration.NONE, BadgeStyling.Placement.RESERVE, Contrast.DEFAULT_CANDIDATES)

internal fun BadgeStyling.withTint(tint: BadgeStyling.Tint): BadgeStyling =
    BadgeStyling(tint, decoration, placement, Contrast.DEFAULT_CANDIDATES)

internal fun BadgeStyling.withDecoration(decoration: BadgeStyling.Decoration): BadgeStyling =
    BadgeStyling(tint, decoration, placement, Contrast.DEFAULT_CANDIDATES)

internal fun TextStyling.withShadow(shadow: TextPaint): TextStyling =
    TextStyling(color, shadow, outline, formatting, Contrast.DEFAULT_CANDIDATES, IntArray(0))

internal fun TextStyling.withOutline(outline: TextPaint): TextStyling =
    TextStyling(color, shadow, outline, formatting, Contrast.DEFAULT_CANDIDATES, IntArray(0))

internal fun TextStyling.withFormatting(formatting: TextStyling.Formatting): TextStyling =
    TextStyling(color, shadow, outline, formatting, Contrast.DEFAULT_CANDIDATES, IntArray(0))

internal fun TextStyling.withCandidates(vararg candidates: Int): TextStyling =
    TextStyling(color, shadow, outline, formatting, candidates, IntArray(0))
