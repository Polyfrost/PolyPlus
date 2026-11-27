package org.polyfrost.polyplus.nameplate.engine.palette

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.color.ColorLerp
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette
import org.polyfrost.polyplus.nameplate.engine.support.of
import org.polyfrost.polyplus.nameplate.engine.support.with

class ColorLerpTest {
    @Test
    fun lerpFloorsEachChannelLikeVanilla() {
        assertEquals(0xFF5A3B1D.toInt(), ColorLerp.srgb(0xFF604020.toInt(), 0xFF201008.toInt(), 1, 11))
        assertEquals(0xFF604020.toInt(), ColorLerp.srgb(0xFF604020.toInt(), 0xFF201008.toInt(), 0, 11))
        assertEquals(0xFF201008.toInt(), ColorLerp.srgb(0xFF604020.toInt(), 0xFF201008.toInt(), 11, 11))
        assertEquals(0x7FFF7F00, ColorLerp.srgb(0x00FFFF00, 0xFFFF0000.toInt(), 1, 2))
    }

    @Test
    fun alongWalksEverySegment() {
        val stops = intArrayOf(0xFF000000.toInt(), 0xFF800000.toInt(), 0xFF800080.toInt())
        assertEquals(stops[0], ColorLerp.along(stops, 0, 5))
        assertEquals(stops[1], ColorLerp.along(stops, 2, 5))
        assertEquals(stops[2], ColorLerp.along(stops, 4, 5))
        assertEquals(0xFF400000.toInt(), ColorLerp.along(stops, 1, 5))
        assertEquals(stops[0], ColorLerp.along(stops, 0, 1))
    }

    @Test
    fun blendRefsResolveAgainstEachPalettesStops() {
        val dark = Palette.of(Palette.Slot("fill", "Fill", 0xFF000000.toInt(), 0xFF00FF00.toInt()))
        val light = dark.with("fill", 0xFFFFFFFF.toInt(), 0xFF0000FF.toInt())
        val ref = ResolvedPalette.blendRef(0, 3, 4)
        assertEquals(0xFF00FF00.toInt(), dark.resolve().color(ref))
        assertEquals(0xFF0000FF.toInt(), light.resolve().color(ref))
        assertEquals(ColorLerp.along(dark.slots[0].stops(), 1, 4), dark.resolve().color(ResolvedPalette.blendRef(0, 1, 4)))
        assertFailsWith<IllegalArgumentException> { ResolvedPalette.blendRef(0, 4, 4) }
        assertFailsWith<IllegalArgumentException> { ResolvedPalette.blendRef(0, 0, 2000) }
    }

    @Test
    fun imagesWithBlendRefsResolve() {
        val palette = Palette.of(Palette.Slot("fill", "Fill", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        val canvas = PixelGrid().paletteCanvas()
        canvas.roundedRect(PixelRect(0, 0, 2, 3), 0) { _, y -> ResolvedPalette.blendRef(0, y, 3) }
        val resolved = canvas.compile().resolve(palette.resolve())
        assertContentEquals(
            intArrayOf(0, 0, 2, 1, 0xFF000000.toInt(), 0, 1, 2, 2, 0xFF7F7F7F.toInt(), 0, 2, 2, 3, 0xFFFFFFFF.toInt()),
            resolved,
        )
    }
}
