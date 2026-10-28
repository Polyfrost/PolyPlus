package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.screens.Screen

class AccountSwitcherScreen(previousScreen: Screen?) : OverlayScreen(previousScreen) {
    @Composable
    override fun content(onClose: () -> Unit) = AccountSwitcherContent(screen = this, onClose = onClose)
}
