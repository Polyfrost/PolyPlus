package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.screens.Screen

class SocialOverlayScreen(previousScreen: Screen?) : OverlayScreen(previousScreen) {
    @Composable
    override fun content(onClose: () -> Unit) = SocialOverlayContent(screen = this, onClose = onClose)
}
