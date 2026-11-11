package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

private const val GLOW_STEPS = 12

private val PanoramaBlackFlat = Color(0xFF11171C)
private val PanoramaBlackGradient = Color(0xFF10161B)
private val PanoramaGlowBlue = Color(0xFF1878F1)

private class GlowSpot(
    val ax: Float,
    val ay: Float,
    val radiusF: Float,
    val alpha: Float,
)

private val GlowMask = listOf(
    GlowSpot(0.177f, -0.080f, 0.401f, 0.15f),
    GlowSpot(-0.117f, 1.023f, 0.401f, 0.15f),
    GlowSpot(1.024f, 0.190f, 0.322f, 0.10f),
    GlowSpot(0.699f, 1.119f, 0.295f, 0.10f),
)

object MenuBackgroundPass {
    private val drawScope = CanvasDrawScope()
    private val inline = InlineCanvas("menu_background")

    //? if >= 26.1 {
    @JvmStatic
    fun renderInline(ctx: GuiGraphicsExtractor): Boolean = inline.draw(ctx) { render(it) }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun renderInline(ctx: GuiGraphics): Boolean = inline.draw(ctx) { render(it) }
    *///?} else {
    /*@JvmStatic
    fun renderInline(): Boolean = inline.draw { render(it) }
    *///?}

    private fun render(canvas: Canvas) {
        val mc = Minecraft.getInstance()
        val w = mc.window.width
        val h = mc.window.height
        if (w <= 0 || h <= 0) return
        drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            drawPanoramaOverlay()
        }
    }
}

private fun softRadial(color: Color, alpha: Float, center: Offset, radius: Float): Brush {
    val stops = Array(GLOW_STEPS + 1) { i ->
        val t = i.toFloat() / GLOW_STEPS
        val s = 1f - t
        t to color.copy(alpha = alpha * s * s * (3f - 2f * s))
    }
    return Brush.radialGradient(colorStops = stops, center = center, radius = radius)
}

private fun DrawScope.drawPanoramaOverlay() {
    if (PolyPlusMainMenuConfig.hidePanoramaOverlay) return
    drawPanoramaBlackBackground()
    drawPanoramaGlowMask()
}

private fun DrawScope.drawPanoramaBlackBackground() {
    drawRect(PanoramaBlackFlat.copy(alpha = 0.10f))
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.199f to Color.Transparent,
                0.960f to PanoramaBlackGradient.copy(alpha = 0.25f),
            ),
        ),
    )
}

private fun DrawScope.drawPanoramaGlowMask() {
    for (g in GlowMask) {
        val center = Offset(size.width * g.ax, size.height * g.ay)
        val radius = size.width * g.radiusF
        drawCircle(brush = softRadial(PanoramaGlowBlue, g.alpha, center, radius), radius = radius, center = center)
    }
}
