@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens

import androidx.compose.runtime.Composable
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.polyplus.client.gui.screens.world.WorldShellScreen
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if > 1.8.9 {
import java.nio.file.Path
import org.polyfrost.polyplus.client.gui.screens.packs.PackDrops
//?}

interface ScreenHost {
    val shell: ScreenShell
    fun back()
    fun open(screen: Screen?)
}

abstract class ShellScreen(
    final override val shell: ScreenShell,
    val parent: Screen?,
) : OneConfigComposeScreen(previous = parent), ScreenHost {
    abstract val title: String

    open val chrome: Boolean get() = true

    open val revealLift: Boolean get() = true

    internal val switchable: Boolean get() = chrome && !(this is WorldShellScreen && shell == ScreenShell.Lobby)

    internal val switchHint: Boolean by lazy { switchable && VanillaScreenHint.claim() }

    override val pausesGame: Boolean get() = shell == ScreenShell.InGame

    override val blurBackground: Boolean get() = shell == ScreenShell.InGame

    override val dimBackground: Boolean get() = shell == ScreenShell.InGame

    override val panoramaBackground: Boolean get() = shell != ScreenShell.Lobby || !LobbyScene.showing()

    //? if > 1.8.9 {
    override fun onFilesDrop(files: List<Path>) {
        if (!PackDrops.drop(files)) super.onFilesDrop(files)
    }
    //?}

    override fun back() = ClientPlatform.setScreen(parent)

    override fun open(screen: Screen?) = ClientPlatform.setScreen(screen)

    @Composable
    abstract fun Body()

    @Composable
    override fun Content() {
        ShellFrame(this, title) { Body() }
    }
}
