@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.world

import com.mojang.blaze3d.platform.InputConstants
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen

abstract class WorldShellScreen(shell: ScreenShell) : ShellScreen(shell, null) {
    override val closeOnEscape: Boolean get() = false

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (key != InputConstants.KEY_ESCAPE) return super.handleKeyPressed(key, modifiers)
        back()
        return true
    }
}
