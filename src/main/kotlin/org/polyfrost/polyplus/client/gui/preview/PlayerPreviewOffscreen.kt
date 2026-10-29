package org.polyfrost.polyplus.client.gui.preview

//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
*///?}
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.polyfrost.compose.backend.PolyCanvas
import org.polyfrost.compose.backend.RecordingCanvas
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.internal.ui.OffscreenTarget
import org.polyfrost.oneconfig.internal.ui.TextureRenderer
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
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
        // drawn by a vanilla screen through an inline canvas rather than by a Compose screen
        var vanillaHost = false
        var spinAccum = 0f
        var lastSpinNanos = 0L

        var fw = 0f
        var fh = 0f
        var lastDrawNanos = 0L

        internal var offscreen: OffscreenTarget? = null
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
    private val released = ArrayList<OffscreenTarget>()

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
        if (entries.isEmpty()) return
        if (java.lang.Boolean.getBoolean("pp.preview.off")) return
        val composeScreen = ClientPlatform.currentScreen() is ComposeScreen
        val now = System.nanoTime()
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

            val offscreen = e.offscreen ?: OffscreenTarget().also { e.offscreen = it }
            if (!offscreen.resolve(w, h)) {
                e.hasContent = false
                continue
            }
            val target = offscreen.target ?: continue
            runCatching {
                //? if >= 1.21.8 {
                val drawn = PlayerPreviewRenderer.renderInto(target, e.source, yaw, e.dragPitch, w, h, e.modelScale, e.verticalAnchor)
                //?} else
                //val drawn = PlayerPreviewRenderer.renderInto(target, e.source, yaw, w, h, e.modelScale, e.verticalAnchor)
                if (!drawn) {
                    e.hasContent = false
                    return@runCatching
                }
                e.contentWidth = w
                e.contentHeight = h
                e.hasContent = true
            }.onFailure { LOG.error("[preview] offscreen render failed", it) }
        }
    }

    fun draw(entry: Entry, ctx: RenderContext, width: Float, height: Float) {
        entry.lastDrawNanos = System.nanoTime()
        if (!entry.hasContent || width <= 0f || height <= 0f) return
        val target = entry.offscreen?.target ?: return
        if (entry.contentWidth <= 0 || entry.contentHeight <= 0) return
        val textures = ctx.renderer as? TextureRenderer ?: return
        textures.texture(target, 0f, 0f, width, height, 0f, 1f, 1f, 0f, 1f)
    }

    private val fadeScope = CanvasDrawScope()
    private val edgeFade by lazy { edgeFadeBrush() }

    /** [draw] with the same edge and bottom fades PlayerPreview applies, for screens that aren't Compose. */
    fun drawFaded(entry: Entry, canvas: PolyCanvas, x: Float, y: Float, width: Float, height: Float, bottomFadeFraction: Float) {
        if (width <= 0f || height <= 0f) return
        // the fades only mask what's under them through a recording, which turns them into fade layers on replay
        val recording = RecordingCanvas()
        fadeScope.draw(Density(1f), LayoutDirection.Ltr, recording, Size(width, height)) {
            drawPoly { draw(entry, it, width, height) }
            drawRect(edgeFade, blendMode = BlendMode.DstIn)
            bottomFadeBrush(bottomFadeFraction)?.let { drawRect(it, blendMode = BlendMode.DstIn) }
        }
        canvas.save()
        canvas.translate(x, y)
        recording.replay(canvas)
        canvas.restore()
    }
}
