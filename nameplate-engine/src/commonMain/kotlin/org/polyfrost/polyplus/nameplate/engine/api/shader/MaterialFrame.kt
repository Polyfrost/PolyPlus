package org.polyfrost.polyplus.nameplate.engine.api.shader

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette

data class MaterialPlacement(
    val width: Int,
    val height: Int,
    val nameX: Int,
    internal val nameY: Int,
    val nameWidth: Int,
    val prefixWidth: Int,
    val background: PixelRect,
    val lineCount: Int = 1,
)

data class BodyEdge(val topLeft: Int, val topRight: Int, val bottomRight: Int, val bottomLeft: Int, val rings: Int) {
    companion object {
        val SQUARE: BodyEdge = BodyEdge(0, 0, 0, 0, 0)
    }
}

data class MaterialFrame(
    val body: PixelRect,
    val frame: Int,
    val fps: Int,
    val step: Int,
    val palette: ResolvedPalette,
    val params: Map<String, Double> = emptyMap(),
    val placement: MaterialPlacement? = null,
    val edge: BodyEdge = BodyEdge.SQUARE,
)
