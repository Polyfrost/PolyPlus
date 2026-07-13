package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx

object MenuBackgroundPass {
    private const val PARALLAX_EASE = 0.02f

    private val drawScope = CanvasDrawScope()

    private var startNanos = 0L
    private var easedX = 0.5f
    private var easedY = 0.5f

    fun enqueue(panorama: Boolean) {
        if (!SkiaCtx.isReady) return
        SkiaCtx.queueDraw { render(panorama) }
    }

    private fun render(panorama: Boolean) {
        val mc = Minecraft.getInstance()
        val w = mc.window.width
        val h = mc.window.height
        if (w <= 0 || h <= 0) return

        val now = System.nanoTime()
        if (startNanos == 0L) startNanos = now
        val time = (now - startNanos) / 1_000_000_000f

        val screenW = mc.window.screenWidth.coerceAtLeast(1)
        val screenH = mc.window.screenHeight.coerceAtLeast(1)
        val targetX = (mc.mouseHandler.xpos() / screenW).toFloat().coerceIn(0f, 1f)
        val targetY = (mc.mouseHandler.ypos() / screenH).toFloat().coerceIn(0f, 1f)
        easedX += (targetX - easedX) * PARALLAX_EASE
        easedY += (targetY - easedY) * PARALLAX_EASE
        val mouse = Offset(easedX, easedY)

        val canvas = SkiaCtx.canvas.asComposeCanvas()
        drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            if (panorama) {
                drawPanoramaOverlay()
            } else {
                drawMenuBackground(time, mouse)
            }
        }
    }
}
