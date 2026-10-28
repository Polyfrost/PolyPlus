package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import net.minecraft.client.gui.screens.Screen

class AccountSwitcherScreen(previousScreen: Screen?) : OverlayScreen(previousScreen) {
    private var actions: AccountActions? = null

    // a switch can't be cancelled once started, so leaving mid-switch would let it land after a newer one
    override fun canDismiss(): Boolean = actions?.idle != false

    @Composable
    override fun content(onClose: () -> Unit) {
        val actions = rememberAccountActions()
        SideEffect { this.actions = actions }
        AccountSwitcherContent(actions, onClose)
    }
}
