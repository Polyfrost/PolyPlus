@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.packs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellScreen
//? if > 1.8.9 {
import java.nio.file.Path
//?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

internal class PackShellScreen(shell: ScreenShell, parent: Screen?, private val session: PackSession) : ShellScreen(shell, parent) {
    override val title: String get() = session.title

    override val closeOnEscape: Boolean get() = false

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (key != InputConstants.KEY_ESCAPE) return super.handleKeyPressed(key, modifiers)
        if (session.dialog != null) session.dialog = null else back()
        return true
    }

    override fun back() = session.commit()

    //? if > 1.8.9 {
    override fun onFilesDrop(files: List<Path>) = session.drop(files)
    //?}

    @Composable
    override fun Body() {
        DisposableEffect(session) {
            PackDrops.active = session
            onDispose { if (PackDrops.active === session) PackDrops.active = null }
        }
        PackBody(session) { hasSelection ->
            TextButton("Done", session::commit, style = ControlStyle.Primary, enabled = hasSelection)
        }
    }
}
