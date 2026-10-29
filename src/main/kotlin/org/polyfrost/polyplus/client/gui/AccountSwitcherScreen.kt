package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.Screen

class AccountSwitcherScreen(previousScreen: Screen?) : OverlayScreen(previousScreen) {
    private var actions: AccountActions? = null

    // launcher calls keep running once the screen is gone, so a stale switch could land after a newer one
    override fun canDismiss(): Boolean = actions?.idle != false

    // Compose popups don't consume Escape here, so it would otherwise close the whole screen instead of the popup
    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        val actions = actions
        if (key != InputConstants.KEY_ESCAPE || actions == null) return super.handleKeyPressed(key, modifiers)
        when {
            actions.loginSession != null -> actions.cancelLogin()
            actions.offlineEntry -> if (actions.idle) actions.cancelOfflineEntry()
            else -> return super.handleKeyPressed(key, modifiers)
        }
        return true
    }

    @Composable
    override fun content(onClose: () -> Unit) {
        val actions = rememberAccountActions()
        SideEffect { this.actions = actions }
        AccountSwitcherContent(actions, onClose)
    }
}
