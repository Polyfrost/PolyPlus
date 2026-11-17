@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.connection

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

//? if >= 26.3 {
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.minecraft.client.gui.screens.GenericMessageScreen
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar

class StatusMessageScreen(shell: ScreenShell, val vanilla: GenericMessageScreen) : ShellScreen(shell, null) {
    private val world = TerrainCard.preparing(vanilla.title)

    override val title: String get() = vanilla.title.string

    override val closeOnEscape: Boolean get() = false

    override val chrome: Boolean get() = false

    @Composable
    override fun Body() {
        val claim = world
        if (claim != null) TerrainBody(claim, "Preparing the world", null)
        else ConnectionCard(shell, title, content = { ProgressBar(null, Modifier.fillMaxWidth()) })
    }
}
//?}
