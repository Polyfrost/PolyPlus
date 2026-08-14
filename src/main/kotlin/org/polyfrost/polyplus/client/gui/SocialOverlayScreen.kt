//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.gui

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.internal.ui.compose.BlurRenderer
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.social.SocialOverlay
import androidx.compose.runtime.Composable

class SocialOverlayScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private var firstFrameDrawn = false
    private val openedAt = System.currentTimeMillis()

    override fun shouldCloseOnEsc(): Boolean = true

    private fun inGame(): Boolean = Minecraft.getInstance().level != null

    /** Mirrors OneConfig's own fullscreen blur fade-in (see OneConfigUIScreen). */
    private fun worldBlurRadius(): Float {
        val progress = (System.currentTimeMillis() - openedAt).toFloat() / OPEN_ANIMATION_MS
        return FULLSCREEN_BLUR_RADIUS * progress.coerceIn(0f, 1f)
    }

    private companion object {
        private const val FULLSCREEN_BLUR_RADIUS = 8f
        private const val OPEN_ANIMATION_MS = 250L
    }

    //? if <26.1 {
    /*override fun render(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) {
            MenuBackgroundPass.enqueue(true)
            renderPanorama(ctx, tickDelta)
            if (firstFrameDrawn) {
                val gameRenderer = net.minecraft.client.Minecraft.getInstance().gameRenderer
                //? if <1.21.4 {
                /*gameRenderer.processBlurEffect(tickDelta)
                *///?} else {
                gameRenderer.processBlurEffect()
                //?}
            }
        } else if (OneConfigConfig.enableBackgroundBlur) {
            //? if >= 1.21.10 {
            if (SkiaCtx.isDeferredComposeBackend) {
                ctx.nextStratum()
                ctx.blurBeforeThisStratum()
                SkiaCtx.requestBlurSnapshot()
            }
            //?}
            BlurRenderer.drawBlur(worldBlurRadius())
        }
        super.render(ctx, mouseX, mouseY, tickDelta)
        firstFrameDrawn = true
    }

    override fun renderBackground(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) return
        super.renderBackground(ctx, mouseX, mouseY, tickDelta)
    }
    *///?} else {
    override fun extractRenderState(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) {
            MenuBackgroundPass.enqueue(true)
            net.minecraft.client.Minecraft.getInstance().gameRenderer
                //? if >= 26.2 {
                /*.panorama()
                .extractRenderState(ctx, width, height)
                *///?} else {
                .getPanorama()
                .extractRenderState(ctx, width, height, true)
                //?}
            ctx.blurBeforeThisStratum()
        } else if (OneConfigConfig.enableBackgroundBlur) {
            if (SkiaCtx.isDeferredComposeBackend) {
                ctx.nextStratum()
                ctx.blurBeforeThisStratum()
                SkiaCtx.requestBlurSnapshot()
            }
            BlurRenderer.drawBlur(worldBlurRadius())
        }
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }

    override fun extractBackground(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) return
        super.extractBackground(ctx, mouseX, mouseY, tickDelta)
    }
    //?}

    override fun onClose() {
        Minecraft.getInstance().execute { SocialOverlay.close() }
    }

    @Composable
    override fun compose() {
        Theme {
            SocialOverlayContent(screen = this, onClose = { Minecraft.getInstance().execute { SocialOverlay.close() } })
        }
    }
}
//?}
