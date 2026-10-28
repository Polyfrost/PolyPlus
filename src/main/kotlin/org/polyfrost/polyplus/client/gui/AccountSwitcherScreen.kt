package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import net.minecraft.client.gui.screens.Screen

class AccountSwitcherScreen(previousScreen: Screen?) : OverlayScreen(previousScreen) {
    private var actions: AccountActions? = null

    // launcher calls keep running once the screen is gone, so a stale switch could land after a newer one;
    // a pending Microsoft sign-in is safe to leave since it's cancelled with the screen, and Escape must reach its popup
    override fun canDismiss(): Boolean = actions?.let { it.idle || it.loginSession != null } ?: true

    @Composable
    override fun content(onClose: () -> Unit) {
        val actions = rememberAccountActions()
        SideEffect { this.actions = actions }
        AccountSwitcherContent(actions, onClose)
    }
}
