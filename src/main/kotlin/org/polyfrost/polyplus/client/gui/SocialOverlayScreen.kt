package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.client.gui.gamesocial.SocialWindow
import org.polyfrost.polyplus.client.gui.gamesocial.SocialWindowState
import org.polyfrost.polyplus.client.social.SocialOverlay

class SocialOverlayScreen(private val previousScreen: Screen?) : OverlayScreen(previousScreen) {
    private val window = SocialWindowState(this, SocialOverlay.consumeAutoHostCurrentWorld())

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

    @Composable
    override fun content(onClose: () -> Unit) {
        SocialWindow(window, onClose)
    }
}
