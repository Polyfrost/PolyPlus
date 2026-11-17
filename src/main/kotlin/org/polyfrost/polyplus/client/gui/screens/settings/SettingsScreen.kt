package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.runtime.Composable
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen

class SettingsScreen(shell: ScreenShell, parent: Screen?) : ShellScreen(shell, parent) {
    override val title: String get() = "Settings"

    @Composable
    override fun Body() {
        SettingsBody({ this })
    }
}
