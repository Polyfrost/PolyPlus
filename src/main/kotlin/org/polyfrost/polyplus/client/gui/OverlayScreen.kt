// OneConfig's public UI API is still marked experimental
@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.social.execute
*///?}

/** Fullscreen Compose overlay on top of [previousScreen], which it returns to when dismissed. */
// holding the previous screen here instead of in an overlay singleton lets it go once the overlay is replaced by any screen
abstract class OverlayScreen(previousScreen: Screen?) :
    OneConfigComposeScreen(previous = previousScreen, renderMode = OneConfigComposeScreen.RenderMode.Continuous) {
    private var firstFrameDrawn = false

    override val dimBackground: Boolean get() = false

    //? if > 1.8.9 {
    private fun inGame(): Boolean = Minecraft.getInstance().level != null
    //?} else {
    /*private fun inGame(): Boolean = Minecraft.getInstance().world != null
    *///?}

    //? if <26.1 && > 1.8.9 {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) {
            renderPanorama(ctx, tickDelta)
            if (firstFrameDrawn) {
                val gameRenderer = Minecraft.getInstance().gameRenderer
                //? if <1.21.4 {
                gameRenderer.processBlurEffect(tickDelta)
                //?} else {
                /^gameRenderer.processBlurEffect()
                ^///?}
            }
            MenuBackgroundPass.renderInline(ctx)
        }
        super.render(ctx, mouseX, mouseY, tickDelta)
        firstFrameDrawn = true
    }

    override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) return
        super.renderBackground(ctx, mouseX, mouseY, tickDelta)
    }
    *///?} else if > 1.8.9 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) {
            if (MenuPanorama.panoramaReady()) {
                Minecraft.getInstance().gameRenderer
                    //? if >= 26.2 {
                    .panorama()
                    .extractRenderState(ctx, width, height)
                    //?} else {
                    /*.getPanorama()
                    .extractRenderState(ctx, width, height, true)
                    *///?}
            }
            ctx.blurBeforeThisStratum()
            MenuBackgroundPass.renderInline(ctx)
        }
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!inGame()) return
        super.extractBackground(ctx, mouseX, mouseY, tickDelta)
    }
    //?}

    @Composable
    protected abstract fun content(onClose: () -> Unit)

    @Composable
    final override fun Content() {
        ProvidePopupSounds(false) {
            Box(Modifier.fillMaxSize().wrapTabFocus(), propagateMinConstraints = true) { content(::dismissLater) }
        }
    }

    private fun dismissLater() {
        Minecraft.getInstance().execute { dismiss() }
    }
}
