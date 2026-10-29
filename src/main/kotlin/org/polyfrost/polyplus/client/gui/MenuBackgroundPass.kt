package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.minecraft.client.Minecraft

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if = 1.8.9
//import org.lwjgl.input.Mouse

object MenuBackgroundPass {
    private const val PARALLAX_EASE = 0.02f

    private val drawScope = CanvasDrawScope()
    private val inline = InlineCanvas("menu_background")

    private var startNanos = 0L
    private var easedX = 0.5f
    private var easedY = 0.5f

    //? if >= 26.1 {
    @JvmStatic
    fun renderInline(ctx: GuiGraphicsExtractor, panorama: Boolean): Boolean = inline.draw(ctx) { render(it, panorama) }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun renderInline(ctx: GuiGraphics, panorama: Boolean): Boolean = inline.draw(ctx) { render(it, panorama) }
    *///?} else {
    /*@JvmStatic
    fun renderInline(panorama: Boolean): Boolean = inline.draw { render(it, panorama) }
    *///?}

    private fun render(canvas: Canvas, panorama: Boolean) {
        val mc = Minecraft.getInstance()
        val w = mc.window.width
        val h = mc.window.height
        if (w <= 0 || h <= 0) return

        val now = System.nanoTime()
        if (startNanos == 0L) startNanos = now
        val time = (now - startNanos) / 1_000_000_000f

        val screenW = mc.window.screenWidth.coerceAtLeast(1)
        val screenH = mc.window.screenHeight.coerceAtLeast(1)
        //? if > 1.8.9 {
        val targetX = (mc.mouseHandler.xpos() / screenW).toFloat().coerceIn(0f, 1f)
        val targetY = (mc.mouseHandler.ypos() / screenH).toFloat().coerceIn(0f, 1f)
        //?} else {
        /*val targetX = (Mouse.getX().toFloat() / w).coerceIn(0f, 1f)
        val targetY = ((h - Mouse.getY()).toFloat() / h).coerceIn(0f, 1f)
        *///?}
        easedX += (targetX - easedX) * PARALLAX_EASE
        easedY += (targetY - easedY) * PARALLAX_EASE
        val mouse = Offset(easedX, easedY)

        drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            if (panorama) {
                drawPanoramaOverlay()
            } else {
                drawMenuBackground(time, mouse)
            }
        }
    }
}
