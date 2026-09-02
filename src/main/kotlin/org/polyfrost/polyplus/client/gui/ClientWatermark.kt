package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.graphics.toArgb
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.polyplus.client.PolyPlusConfig
import kotlin.math.ceil
import kotlin.math.roundToInt

//? if >= 26.1 {
private typealias Graphics = net.minecraft.client.gui.GuiGraphicsExtractor
//?} else {
/*private typealias Graphics = net.minecraft.client.gui.GuiGraphics
*///?}

object ClientWatermark {
    private const val LOGO_PATH = "/assets/polyplus/mainmenu/logo.svg"
    private const val WORDMARK_PATH = "/assets/polyplus/brand/oneclient.svg"

    private const val LOGO_ART_SIZE = 144f
    private const val WORDMARK_ART_WIDTH = 159f
    private const val WORDMARK_ART_HEIGHT = 19f

    private const val LOGO_SIZE = 17f
    private const val GAP = 5f
    private const val WORDMARK_HEIGHT = 11f
    private const val WORDMARK_WIDTH = WORDMARK_HEIGHT * (WORDMARK_ART_WIDTH / WORDMARK_ART_HEIGHT)

    private const val WIDTH = LOGO_SIZE + GAP + WORDMARK_WIDTH
    private const val HEIGHT = LOGO_SIZE

    private const val OVERSAMPLE = 4

    private const val SHADOW_OFFSET = 0.5f
    private const val SHADOW_COLOR = 0x33000000.toInt()

    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 4f

    const val POSITION_TOP_LEFT = 0
    const val POSITION_TOP_RIGHT = 1
    const val POSITION_BOTTOM_LEFT = 2
    const val POSITION_BOTTOM_RIGHT = 3
    const val POSITION_TOP_CENTER = 4
    const val POSITION_BOTTOM_CENTER = 5
    const val POSITION_LEFT_CENTER = 6
    const val POSITION_RIGHT_CENTER = 7

    private val logo by lazy { svg(LOGO_PATH) }
    private val wordmark by lazy { svg(WORDMARK_PATH) }

    private var raster: Image? = null
    private var rasterWidth = 0
    private var rasterHeight = 0

    @JvmStatic
    fun visibleOn(screen: Screen): Boolean =
        PolyPlusConfig.watermarkEnabled && (screen is PauseScreen || screen is AbstractContainerScreen<*>)

    @JvmStatic
    fun render(graphics: Graphics, screen: Screen) {
        if (!visibleOn(screen)) return
        if (!SkiaCtx.isReady) return
        SkiaCtx.drawComposeBlit(graphics) { draw() }
    }

    private fun draw() {
        val window = Minecraft.getInstance().window
        val guiScaled = window.guiScaledWidth
        if (guiScaled <= 0 || window.width <= 0) return

        val pixels = window.width.toFloat() / guiScaled *
            (PolyPlusConfig.watermarkScale / 100f).coerceIn(MIN_SCALE, MAX_SCALE)
        val width = ceil(WIDTH * pixels).toInt()
        val height = ceil(HEIGHT * pixels).toInt()
        if (width <= 0 || height <= 0) return

        val mark = raster(width, height)

        val offsetX = PolyPlusConfig.watermarkOffsetX * pixels
        val offsetY = PolyPlusConfig.watermarkOffsetY * pixels
        val position = PolyPlusConfig.watermarkPosition
        val x = when (position) {
            POSITION_TOP_RIGHT, POSITION_BOTTOM_RIGHT, POSITION_RIGHT_CENTER -> window.width - width - offsetX
            POSITION_TOP_CENTER, POSITION_BOTTOM_CENTER -> (window.width - width) / 2f
            else -> offsetX
        }
        val y = when (position) {
            POSITION_BOTTOM_LEFT, POSITION_BOTTOM_RIGHT, POSITION_BOTTOM_CENTER -> window.height - height - offsetY
            POSITION_LEFT_CENTER, POSITION_RIGHT_CENTER -> (window.height - height) / 2f
            else -> offsetY
        }

        val shadow = (SHADOW_OFFSET * pixels).roundToInt().coerceAtLeast(1)
        val canvas = SkiaCtx.canvas
        blit(canvas, mark, x.roundToInt(), y.roundToInt() + shadow, SHADOW_COLOR)
        blit(canvas, mark, x.roundToInt(), y.roundToInt(), color())
    }

    private fun blit(canvas: Canvas, mark: Image, x: Int, y: Int, color: Int) {
        canvas.drawImageRect(
            mark,
            Rect.makeWH(mark.width.toFloat(), mark.height.toFloat()),
            Rect.makeXYWH(x.toFloat(), y.toFloat(), mark.width.toFloat(), mark.height.toFloat()),
            SamplingMode.DEFAULT,
            tint(color),
            true,
        )
    }

    private fun raster(width: Int, height: Int): Image {
        raster?.let { if (rasterWidth == width && rasterHeight == height) return it }

        var image = render(width * OVERSAMPLE, height * OVERSAMPLE)
        var factor = OVERSAMPLE
        while (factor > 1) {
            factor /= 2
            val halved = halve(image, width * factor, height * factor)
            image.close()
            image = halved
        }

        raster?.close()
        raster = image
        rasterWidth = width
        rasterHeight = height
        return image
    }

    private fun render(width: Int, height: Int): Image {
        val surface = Surface.makeRasterN32Premul(width, height)
        return try {
            drawMark(surface.canvas, width / WIDTH)
            surface.makeImageSnapshot()
        } finally {
            surface.close()
        }
    }

    private fun halve(image: Image, width: Int, height: Int): Image {
        val surface = Surface.makeRasterN32Premul(width, height)
        return try {
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
                Rect.makeWH(width.toFloat(), height.toFloat()),
                SamplingMode.LINEAR,
                null,
                true,
            )
            surface.makeImageSnapshot()
        } finally {
            surface.close()
        }
    }

    private fun drawMark(canvas: Canvas, pixels: Float) {
        val logoSize = LOGO_SIZE * pixels
        draw(logo, canvas, LOGO_ART_SIZE, LOGO_ART_SIZE, logoSize, logoSize)

        val wordmarkHeight = WORDMARK_HEIGHT * pixels
        val saved = canvas.save()
        canvas.translate((LOGO_SIZE + GAP) * pixels, (logoSize - wordmarkHeight) / 2f)
        draw(wordmark, canvas, WORDMARK_ART_WIDTH, WORDMARK_ART_HEIGHT, WORDMARK_WIDTH * pixels, wordmarkHeight)
        canvas.restoreToCount(saved)
    }

    private fun draw(dom: SVGDOM, canvas: Canvas, artWidth: Float, artHeight: Float, width: Float, height: Float) {
        val saved = canvas.save()
        canvas.scale(width / artWidth, height / artHeight)
        dom.setContainerSize(artWidth, artHeight)
        dom.render(canvas)
        canvas.restoreToCount(saved)
    }

    private fun tint(color: Int): Paint = Paint().apply {
        colorFilter = ColorFilter.makeBlend(color, BlendMode.SRC_IN)
    }

    private fun color(): Int =
        if (PolyPlusConfig.watermarkAccentColor) Accent.toArgb() else PolyPlusConfig.watermarkColor.argb

    private fun svg(path: String): SVGDOM {
        val bytes = ClientWatermark::class.java.getResourceAsStream(path)!!.use { it.readBytes() }
        return SVGDOM(Data.makeFromBytes(bytes))
    }
}
