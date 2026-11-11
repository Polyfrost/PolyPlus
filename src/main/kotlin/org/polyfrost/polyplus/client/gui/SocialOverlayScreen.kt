package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mojang.blaze3d.platform.InputConstants
import kotlin.math.pow
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.oneconfig.internal.ui.components.RetainedVisibility
import org.polyfrost.polyplus.client.gui.gamesocial.SocialWindow
import org.polyfrost.polyplus.client.gui.gamesocial.SocialWindowState
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform

class SocialOverlayScreen(private val previousScreen: Screen?) : OverlayScreen(previousScreen) {
    private val window = SocialWindowState(this, SocialOverlay.consumeAutoHostCurrentWorld())
    private val openedAt = System.currentTimeMillis()

    init {
        if (SocialOverlay.consumeOpenVoice()) window.showVoice()
    }

    //? if > 1.8.9 {
    override fun isPauseScreen(): Boolean = previousScreen?.isPauseScreen == true
    //?} else {
    /*override fun shouldPauseGame(): Boolean = previousScreen?.shouldPauseGame() == true
    *///?}

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        val handled = when (key) {
            InputConstants.KEY_ESCAPE -> window.escape()
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> window.enter()
            else -> false
        }
        return handled || super.handleKeyPressed(key, modifiers)
    }

    override fun blurAlpha(): Float {
        if (!ClientPlatform.inWorld() || !ThemeConfig.enableBackgroundBlur || ThemeConfig.reduceTransparency) return 0f
        if (!ThemeConfig.animations) return 1f
        return ((System.currentTimeMillis() - openedAt).toFloat() / BLUR_OPEN_MILLIS).coerceIn(0f, 1f)
    }

    @Composable
    override fun content(onClose: () -> Unit) {
        SocialWindow(window, onClose)
    }

    private companion object {
        private const val BLUR_OPEN_MILLIS = 250L
    }
}

private val EaseOutExpo = Easing { x -> if (x >= 1f) 1f else 1f - 2f.pow(-10f * x) }

@Composable
internal fun SocialOpenAnimation(content: @Composable () -> Unit) {
    val openMillis = if (ThemeConfig.guiOpenAnimation) (ThemeConfig.animationTime * 1000f).toInt().coerceAtLeast(1) else 1
    RetainedVisibility(
        visible = true,
        enter = tween(openMillis, easing = EaseOutExpo),
        exit = tween(1),
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}
