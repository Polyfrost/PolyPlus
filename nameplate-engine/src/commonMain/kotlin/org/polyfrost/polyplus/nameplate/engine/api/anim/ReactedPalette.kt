package org.polyfrost.polyplus.nameplate.engine.api.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.paletteAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tagFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.TriggeredPalette
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

fun reactedPalette(
    bundle: NameplateBundle,
    variant: NameplateBundle.Variant,
    palette: ResolvedPalette,
    frame: Int,
    seed: Int,
    state: TagState,
): ResolvedPalette {
    val triggers = bundle.triggers
    if (state.isIdle || !triggers.hasPalette) return palette

    val views = triggers.paletteViews ?: TriggeredPalette.compile(bundle, triggers).also { triggers.paletteViews = it }
    val phased = tagFrame(bundle, frame, seed)
    val time = phased - phaseOffset(bundle, seed)
    val loopTime = phased.mod(bundle.loop)
    val slotCount = bundle.palette.slots.size
    val winners = IntArray(slotCount) { NO_WINNER }
    val locals = IntArray(slotCount)
    val starts = IntArray(slotCount)
    var found = false
    if (!state.isReduced) {
        for (i in 0 until state.oneShotCount) {
            val index = state.oneShot(i)
            val shot = triggers.oneShots.getOrNull(index) ?: continue
            val view = views.getOrNull(index) ?: continue
            val start = state.start(i)
            val local = time - start
            if (local !in 0 until shot.length) continue
            for (slot in view.slots) {
                val winner = winners[slot]
                if (winner == NO_WINNER || start > starts[slot] || (start == starts[slot] && index < winner)) {
                    winners[slot] = index
                    locals[slot] = local
                    starts[slot] = start
                    found = true
                }
            }
        }
    }
    for ((w, reaction) in triggers.whiles.withIndex()) {
        val index = triggers.oneShots.size + w
        val view = views.getOrNull(index) ?: continue
        if (!state.inputs.has(reaction.reaction) || (state.isReduced && reaction.dropsWhenReduced)) continue
        for (slot in view.slots) {
            if (winners[slot] == NO_WINNER) {
                winners[slot] = index
                locals[slot] = if (state.isReduced) 0 else loopTime
                found = true
            }
        }
    }
    if (!found) return palette

    val colors = palette.copyColors()
    val stops = palette.copyStops()
    for (slot in 0 until slotCount) {
        val winner = winners[slot]
        if (winner == NO_WINNER) continue
        val source = paletteAt(checkNotNull(views[winner]).tracks, variant, locals[slot])
        val sourceColors = source.copyColors()
        val offset = slot * Palette.MAX_TONES
        sourceColors.copyInto(colors, offset, offset, offset + Palette.MAX_TONES)
        val sourceStops = source.copyStops()
        if (slot < stops.size && slot < sourceStops.size) {
            stops[slot] = sourceStops[slot]
        }
    }
    return ResolvedPalette(colors, stops)
}

private const val NO_WINNER = -1
