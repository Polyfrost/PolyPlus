package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.molang.MolangValue
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.internal.color.ColorLerp
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.internal.palette.PaletteTrack
import org.polyfrost.polyplus.nameplate.engine.internal.track.Interpolation
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame

internal fun paletteAt(
    bundle: NameplateBundle,
    variant: NameplateBundle.Variant,
    frame: Int,
    molang: MolangFrame? = null,
): ResolvedPalette {
    val tracks = bundle.paletteTracks
    if (tracks.isEmpty()) return variant.resolved

    val local = frame.mod(bundle.loop)
    val base = variant.resolved
    val colors = base.copyColors()
    val stops = base.copyStops()
    for (track in tracks) {
        when (track) {
            is PaletteTrack.Cycle -> rotate(track, variant.palette.slots[track.slot], local, colors)
            is PaletteTrack.Keys -> recolor(track, base, variant.palette.slots[track.slot].size, local, colors, stops, molang)
        }
    }

    return ResolvedPalette(colors, stops)
}

private fun rotate(track: PaletteTrack.Cycle, slot: Palette.Slot, frame: Int, colors: IntArray) {
    val tones = slot.size
    val shift = shift(track.mode, tones, frame / track.every)
    val offset = track.slot * Palette.MAX_TONES
    for (tone in 0 until Palette.MAX_TONES) {
        colors[offset + tone] = slot.tone((minOf(tone, tones - 1) + shift).mod(tones))
    }
}

private fun shift(mode: PaletteTrack.CycleMode, tones: Int, steps: Int): Int {
    if (tones <= 1) return 0

    return when (mode) {
        PaletteTrack.CycleMode.FORWARD -> steps % tones
        PaletteTrack.CycleMode.BACKWARD -> -(steps % tones)
        PaletteTrack.CycleMode.PINGPONG -> {
            val span = tones - 1
            val position = steps % (2 * span)
            if (position <= span) position else 2 * span - position
        }
    }
}

private fun recolor(
    track: PaletteTrack.Keys,
    resolved: ResolvedPalette,
    slotTones: Int,
    frame: Int,
    colors: IntArray,
    stops: Array<IntArray>,
    molang: MolangFrame?,
) {
    val key = Interpolation.keyAt(track.steps, frame)
    val next = key + 1
    val moving = next < track.keyCount && track.easings[next] != PaletteTrack.Easing.STEP
    if (track.isBase(key) && (!moving || track.isBase(next))) return

    val tones = if (track.bases == null) track.toneCount else maxOf(track.toneCount, slotTones)
    val blended = IntArray(tones) { tone ->
        val from = keyColor(track, key, tone, resolved, molang)
        if (!moving) {
            from
        } else {
            val to = keyColor(track, next, tone, resolved, molang)
            blend(from, to, track.easings[next], frame - track.steps[key], track.steps[next] - track.steps[key])
        }
    }
    val offset = track.slot * Palette.MAX_TONES
    for (tone in 0 until Palette.MAX_TONES) {
        colors[offset + tone] = blended[minOf(tone, tones - 1)]
    }
    stops[track.slot] = blended
}

private fun keyColor(track: PaletteTrack.Keys, key: Int, tone: Int, resolved: ResolvedPalette, molang: MolangFrame?): Int {
    if (track.isBase(key, tone)) return resolved.color(track.slot * Palette.MAX_TONES + tone)
    val at = minOf(tone, track.toneCount - 1)
    val expressions = track.molang
    if (expressions != null && molang != null) {
        val index = expressions[key][at]
        if (index >= 0) return MolangValue.argb(molang.slot(index))
    }
    val ref = track.refs[key][at]
    return if (ref >= 0) resolved.color(ref) else track.colors[key][at]
}

private fun blend(from: Int, to: Int, easing: PaletteTrack.Easing, elapsed: Int, span: Int): Int = when (easing) {
    PaletteTrack.Easing.STEP -> from
    PaletteTrack.Easing.LINEAR -> ColorLerp.srgb(from, to, elapsed, span)
    PaletteTrack.Easing.EASE ->
        ColorLerp.srgb(from, to, Interpolation.smoothstep(elapsed.toLong(), span.toLong()).toInt(), Interpolation.EASE_UNITS)
}
