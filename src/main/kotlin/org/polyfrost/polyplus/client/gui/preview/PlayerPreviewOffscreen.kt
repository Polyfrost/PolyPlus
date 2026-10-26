package org.polyfrost.polyplus.client.gui.preview

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
*///?}
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.ContentChangeMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Gradient
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Point
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.polyfrost.oneconfig.internal.ui.SkiaOffscreenTarget
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory

object PlayerPreviewOffscreen {
    private val LOG = LoggerFactory.getLogger("polyplus/preview-offscreen")

    class Entry internal constructor() {
        var source: PlayerPreviewSource = PlayerPreviewSource.LocalLive
        var modelScale = 0.5f
        var verticalAnchor = 0.5f
        var autoSpin = false
        var initialYaw = 0f
        var dragYaw = 0f
        var dragPitch = 0f
        var dragging = false
        // drawn by a vanilla screen through Skia rather than by a Compose screen
        var vanillaHost = false
        var spinAccum = 0f
        var lastSpinNanos = 0L

        var fw = 0f
        var fh = 0f
        var lastDrawNanos = 0L

        internal var offscreen: SkiaOffscreenTarget? = null
        internal var contentWidth = 0
        internal var contentHeight = 0
        internal var hasContent = false
    }

    private const val MAX_DIM = 1024
    private const val SPIN_DEG_PER_SEC = 37.5f
    private const val MAX_SPIN_STEP_SEC = 0.1

    private const val IDLE_NANOS = 250_000_000L
    private const val RELEASE_NANOS = 1_000_000_000L

    private val entries = HashSet<Entry>()
    private val released = ArrayList<SkiaOffscreenTarget>()
    private val blitPaint = Paint()

    fun register(): Entry = Entry().also(entries::add)

    fun unregister(entry: Entry) {
        entries.remove(entry)
        release(entry)
    }

    private fun release(entry: Entry) {
        entry.hasContent = false
        entry.offscreen?.let(released::add)
        entry.offscreen = null
    }

    @JvmStatic
    fun disposeReleased() {
        released.forEach { it.dispose() }
        released.clear()
    }

    @JvmStatic
    fun renderAll(main: RenderTarget) {
        disposeReleased()
        if (entries.isEmpty() || !SkiaCtx.isReady) return
        if (java.lang.Boolean.getBoolean("pp.preview.off")) return
        val composeScreen = ClientPlatform.currentScreen() is ComposeScreen
        val now = System.nanoTime()
        var rendered = false
        for (e in entries) {
            val sinceDraw = now - e.lastDrawNanos
            if ((!composeScreen && !e.vanillaHost) || sinceDraw > RELEASE_NANOS) {
                if (e.offscreen != null) release(e)
                e.lastSpinNanos = 0L
                continue
            }
            if (sinceDraw > IDLE_NANOS) {
                e.lastSpinNanos = 0L
                continue
            }
            val rectW = (e.fw * main.width).toInt()
            val rectH = (e.fh * main.height).toInt()
            if (rectW <= 0 || rectH <= 0) continue
            val fit = minOf(1f, MAX_DIM.toFloat() / maxOf(rectW, rectH))
            val w = (rectW * fit).toInt().coerceAtLeast(1)
            val h = (rectH * fit).toInt().coerceAtLeast(1)

            val dtSec = if (e.lastSpinNanos == 0L) 0.0 else ((now - e.lastSpinNanos) / 1_000_000_000.0).coerceAtMost(MAX_SPIN_STEP_SEC)
            e.lastSpinNanos = now
            if (e.autoSpin && !e.dragging) e.spinAccum += (dtSec * SPIN_DEG_PER_SEC).toFloat()
            val yaw = e.initialYaw + e.dragYaw + e.spinAccum

            val offscreen = e.offscreen ?: SkiaOffscreenTarget().also { e.offscreen = it }
            if (!offscreen.resolveTarget(w, h)) {
                e.hasContent = false
                continue
            }
            val target = offscreen.target ?: continue
            rendered = true
            runCatching {
                //? if >= 1.21.8 {
                val drawn = PlayerPreviewRenderer.renderInto(target, e.source, yaw, e.dragPitch, w, h, e.modelScale, e.verticalAnchor)
                //?} else
                //val drawn = PlayerPreviewRenderer.renderInto(target, e.source, yaw, w, h, e.modelScale, e.verticalAnchor)
                if (!drawn) {
                    e.hasContent = false
                    return@runCatching
                }
                //? if < 1.21.10 && > 1.8.9
                //offscreen.ensureSubmitted()
                e.contentWidth = w
                e.contentHeight = h
                e.hasContent = true
            }.onFailure { LOG.error("[preview] offscreen render failed", it) }
        }
        if (rendered) resetSkiaContext()
    }

    private fun resetSkiaContext() {
        if (SkiaCtx.isVulkanMode) SkiaCtx.directContext.resetAll() else SkiaCtx.directContext.resetGLAll()
    }

    fun draw(entry: Entry, canvas: Canvas, width: Float, height: Float) {
        entry.lastDrawNanos = System.nanoTime()
        if (!entry.hasContent || width <= 0f || height <= 0f) return
        val surface = entry.offscreen?.surface ?: return
        val w = entry.contentWidth
        val h = entry.contentHeight
        if (w <= 0 || h <= 0) return
        surface.notifyContentWillChange(ContentChangeMode.RETAIN)
        canvas.save()
        canvas.clipRect(Rect.makeWH(width, height))
        canvas.scale(width / w, height / h)
        surface.draw(canvas, 0, 0, SamplingMode.LINEAR, blitPaint)
        canvas.restore()
    }

    private const val FADE_STOPS = 12
    private val fadePaint = Paint().apply { blendMode = BlendMode.DST_IN }

    /** [draw] with the same edge and bottom fades PlayerPreview applies, for screens that aren't Compose. */
    fun drawFaded(entry: Entry, canvas: Canvas, x: Float, y: Float, width: Float, height: Float, bottomFadeFraction: Float) {
        if (width <= 0f || height <= 0f) return
        val bounds = Rect.makeWH(width, height)
        canvas.save()
        canvas.translate(x, y)
        canvas.saveLayer(bounds, null)
        draw(entry, canvas, width, height)
        val edge = PlayerPreviewRenderer.EDGE_FADE_FRACTION
        val left = (0..FADE_STOPS).map { it / FADE_STOPS.toFloat() }.map { t -> t * edge to t }
        val stops = left + left.asReversed().map { (pos, t) -> 1f - pos to t }
        fade(canvas, bounds, stops, Point(0f, 0f), Point(width, 0f))
        if (bottomFadeFraction > 0f) {
            val f = bottomFadeFraction.coerceAtMost(1f)
            val bottom = (FADE_STOPS downTo 0).map { it / FADE_STOPS.toFloat() }.map { t -> 1f - t * f to t }
            fade(canvas, bounds, bottom, Point(0f, 0f), Point(0f, height))
        }
        canvas.restore()
        canvas.restore()
    }

    // masks what's already drawn by an alpha gradient; stops are (position, alpha before smoothing)
    private fun fade(canvas: Canvas, bounds: Rect, stops: List<Pair<Float, Float>>, from: Point, to: Point) {
        val colors = stops.map { (_, t) -> Color4f(1f, 1f, 1f, PlayerPreviewRenderer.smooth(t)) }.toTypedArray()
        val positions = stops.map { it.first }.toFloatArray()
        val shader = Shader.makeLinearGradient(from, to, Gradient(Gradient.Colors(colors, positions, FilterTileMode.CLAMP)))
        fadePaint.shader = shader
        canvas.drawRect(bounds, fadePaint)
        fadePaint.shader = null
        shader.close()
    }
}
