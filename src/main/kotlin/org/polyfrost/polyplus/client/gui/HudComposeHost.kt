@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.FrameRecomposer
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.SingleComposeSceneRenderingScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineExceptionHandler
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.backend.PolyCanvas
import org.polyfrost.compose.backend.PolyCompose
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.internal.ui.compose.ComposeSceneContextImpl
import org.polyfrost.oneconfig.internal.ui.compose.ComposeSupport
import org.polyfrost.oneconfig.internal.ui.compose.RenderThreadDispatcher
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen
import org.polyfrost.oneconfig.internal.ui.compose.sceneDensity
import org.polyfrost.polyplus.client.ping.PingGraphics

internal val hudBackdropGlass: Boolean
    get() {
        //? if >= 1.21.8 && < 1.21.11 {
        /*return false
        *///?} else {
        return runCatching { Platform.screen().supportsBackdropGlass() }.getOrDefault(false)
        //?}
    }

internal val hudPixelRatio: Float
    get() = runCatching { Platform.screen().pixelRatio() }.getOrDefault(1f).takeIf { it > 0f } ?: 1f

internal class HudComposeHost(textureName: String, private val content: @Composable () -> Unit) {
    private val inline = InlineCanvas(textureName)
    private var scene: ComposeScene? = null
    private var recomposer: FrameRecomposer? = null
    private var scope: SingleComposeSceneRenderingScope? = null
    private var failures = 0

    val density: Float get() = runCatching { sceneDensity() }.getOrDefault(1f)

    fun draw(graphics: PingGraphics?): Boolean {
        val w = Platform.screen().windowWidth()
        val h = Platform.screen().windowHeight()
        if (w <= 0 || h <= 0) return false
        val scene = ensure() ?: return false
        val recomposer = recomposer ?: return false
        val scope = scope ?: return false
        return try {
            scene.density = Density(density)
            scene.size = IntSize(w, h)
            val nanos = System.nanoTime()
            recomposer.performFrame(nanos)
            scene.measureAndLayout()
            val ratio = (Platform.screen().viewportWidth().toFloat() / w).coerceAtLeast(0.01f)
            val block: (PolyCanvas) -> Unit = { canvas ->
                canvas.save()
                if (ratio != 1f) canvas.scale(ratio, ratio)
                try {
                    with(scope) { scene.render(recomposer, canvas, nanos) }
                } finally {
                    canvas.restore()
                }
            }
            //? if > 1.8.9 {
            graphics != null && inline.draw(graphics, block)
            //?} else {
            /*inline.draw(block)
            *///?}
        } catch (t: Throwable) {
            LOGGER.error("Failed to draw a HUD Compose layer", t)
            failures++
            close()
            false
        }
    }

    fun close() {
        val oldScene = scene
        val oldRecomposer = recomposer
        scene = null
        recomposer = null
        scope = null
        runCatching { oldScene?.close() }
        runCatching { oldRecomposer?.close() }
    }

    private fun ensure(): ComposeScene? {
        scene?.let { return it }
        if (failures >= MAX_FAILURES || !ComposeSupport.isAvailable) return null
        return try {
            OneConfigUIScreen.stopWarmUp()
            PolyCompose.install()
            val handler = CoroutineExceptionHandler { _, t -> LOGGER.error("HUD Compose layer failed", t) }
            val newRecomposer = FrameRecomposer(RenderThreadDispatcher + handler) {}
            val newScope = SingleComposeSceneRenderingScope {}
            val newScene = CanvasLayersComposeScene(
                frameRecomposer = newRecomposer,
                platformContext = ComposeSceneContextImpl.platformContext,
                invalidateLayout = newScope::onSceneInvalidation,
                invalidateDraw = newScope::onSceneInvalidation,
            )
            newScene.setContent { content() }
            recomposer = newRecomposer
            scope = newScope
            scene = newScene
            newScene
        } catch (t: Throwable) {
            LOGGER.error("Failed to start a HUD Compose layer", t)
            failures++
            null
        }
    }

    private companion object {
        val LOGGER = LogManager.getLogger("polyplus/hud-compose")
        const val MAX_FAILURES = 3
    }
}
