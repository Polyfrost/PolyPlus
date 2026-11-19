@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.onboarding.live

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
//? if > 1.8.9 {
import com.mojang.blaze3d.pipeline.RenderTarget
//?} else {
/*import net.minecraft.client.render.pipeline.RenderTarget
*///?}
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.internal.ui.OffscreenTarget
import org.polyfrost.oneconfig.internal.ui.TextureRenderer
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory

internal interface RenderBlocksContent {
    fun render(renderer: RenderBlocksRenderer, target: RenderTarget, width: Int, height: Int, aspect: Float, nowNanos: Long): Boolean

    fun release(): List<AutoCloseable>
}

internal class RenderBlocksSlot(private val label: String, private val content: RenderBlocksContent) {
    var widthFraction = 0f
    var heightFraction = 0f
    private var mainHeight = 0
    private var lastDrawNanos = 0L
    private var offscreen: OffscreenTarget? = null
    private var renderer: RenderBlocksRenderer? = null
    private var contentWidth = 0
    private var contentHeight = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var pendingSince = 0L
    private var requestedWidth = 0
    private var requestedHeight = 0
    private var hasContent = false
    private var failed = false
    private val frame = LivePreviewFrame(::renderFrame)
    private val stale = ArrayList<OffscreenTarget>()

    fun attach() {
        LivePreviewFrames.register(frame)
    }

    fun detach() {
        LivePreviewFrames.unregister(frame)
        release()
    }

    private fun release() {
        hasContent = false
        val target = offscreen
        val owned = listOfNotNull(renderer) + content.release()
        offscreen = null
        renderer = null
        targetWidth = 0
        targetHeight = 0
        stale.forEach { retire(it) }
        stale.clear()
        retire(target, owned)
    }

    private fun retire(target: OffscreenTarget?, owned: List<AutoCloseable> = emptyList()) {
        if (target == null && owned.isEmpty()) return
        ClientPlatform.runOnMain {
            LivePreviewFrames.later(RETIRE_FRAMES) {
                liveRetire {
                    target?.dispose()
                    owned.forEach { it.close() }
                }
            }
        }
    }

    private fun targetFor(w: Int, h: Int, now: Long): OffscreenTarget? {
        val current = offscreen
        if (current != null && w == targetWidth && h == targetHeight) {
            pendingSince = 0L
            return current
        }
        if (current != null && hasContent) {
            if (pendingSince == 0L || w != requestedWidth || h != requestedHeight) {
                requestedWidth = w
                requestedHeight = h
                pendingSince = now
            }
            if (now - pendingSince < RESIZE_SETTLE_NANOS) return current
        }
        pendingSince = 0L
        val next = OffscreenTarget()
        if (!next.resolve(w, h)) {
            next.dispose()
            return current
        }
        offscreen = next
        targetWidth = w
        targetHeight = h
        if (current != null) stale += current
        if (stale.size > MAX_STALE) retire(stale.removeAt(0))
        return next
    }

    private fun renderFrame(main: RenderTarget) {
        if (failed) return
        val now = System.nanoTime()
        val sinceDraw = now - lastDrawNanos
        if (sinceDraw > RELEASE_NANOS) {
            if (offscreen != null) release()
            return
        }
        if (sinceDraw > IDLE_NANOS) return
        val rectW = (widthFraction * main.width).toInt()
        mainHeight = main.height
        val rectH = (heightFraction * main.height).toInt()
        if (rectW <= 0 || rectH <= 0) return
        val fit = minOf(1f, MAX_DIM.toFloat() / maxOf(rectW, rectH))
        val w = (rectW * fit).toInt().coerceAtLeast(1)
        val h = (rectH * fit).toInt().coerceAtLeast(1)
        val target = targetFor(w, h, now)
        if (target == null) {
            hasContent = false
            return
        }
        val fbo = target.target ?: return
        runCatching {
            val gpu = renderer ?: RenderBlocksRenderer(label).also { renderer = it }
            hasContent = content.render(gpu, fbo, targetWidth, targetHeight, rectW.toFloat() / rectH, now)
            contentWidth = targetWidth
            contentHeight = targetHeight
        }.onFailure {
            hasContent = false
            failed = true
            LOG.error("[render-blocks] offscreen render failed, preview disabled", it)
        }
    }

    val showing: Boolean get() = hasContent

    fun pixelScale(drawHeight: Float): Float {
        val framebufferHeight = heightFraction * mainHeight
        return if (framebufferHeight > 0f) drawHeight / framebufferHeight else 1f
    }

    fun draw(ctx: RenderContext, width: Float, height: Float) {
        lastDrawNanos = System.nanoTime()
        if (!hasContent || width <= 0f || height <= 0f || contentWidth <= 0 || contentHeight <= 0) return
        val target = offscreen?.target ?: return
        val textures = ctx.renderer as? TextureRenderer ?: return
        textures.texture(target, 0f, 0f, width, height, 0f, 1f, 1f, 0f, 1f)
    }

    private companion object {
        val LOG = LoggerFactory.getLogger("polyplus/render-blocks")
        const val MAX_DIM = 1024
        const val IDLE_NANOS = 250_000_000L
        const val RELEASE_NANOS = 1_000_000_000L
        const val RESIZE_SETTLE_NANOS = 150_000_000L
        const val RETIRE_FRAMES = 3
        const val MAX_STALE = 6
    }
}

@Composable
internal fun RenderBlocksCanvas(slot: RenderBlocksSlot, modifier: Modifier, overlay: (DrawScope.(Float) -> Unit)? = null) {
    DisposableEffect(slot) {
        slot.attach()
        onDispose { slot.detach() }
    }
    val frameNanos by produceState(0L) { while (true) withFrameNanos { value = it } }
    Box(
        modifier
            .onGloballyPositioned { coords ->
                var root = coords
                while (true) {
                    root = root.parentLayoutCoordinates ?: break
                }
                val rw = root.size.width.toFloat()
                val rh = root.size.height.toFloat()
                if (rw > 0f && rh > 0f) {
                    slot.widthFraction = coords.size.width / rw
                    slot.heightFraction = coords.size.height / rh
                }
            }
            .drawBehind {
                if (frameNanos == 0L) return@drawBehind
                val w = size.width
                val h = size.height
                drawPoly { slot.draw(it, w, h) }
                if (slot.showing) overlay?.invoke(this, slot.pixelScale(h))
            },
    )
}
