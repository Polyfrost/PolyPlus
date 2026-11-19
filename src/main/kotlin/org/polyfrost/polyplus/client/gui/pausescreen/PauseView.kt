package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.ui.geometry.Rect
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}

internal interface PauseMenu {
    val entries: List<PauseEntry>
    val more: List<PauseEntry>
    val uncaptured: Int

    fun entry(slot: PauseSlot): PauseEntry?
}

internal interface PauseView {
    val host: Screen?
    val capture: PauseMenu
    val stepBack: Boolean
    val classic: Boolean
    val staged: Boolean
    var moreOpen: Boolean
    var moreLinked: Boolean
    var customizeOpen: Boolean
    val settingsOpen: Boolean
    var playerPreview: Boolean
    var resumeBounds: Rect
    val fromVanilla: Boolean
    val online: Boolean
    val canHost: Boolean

    fun resume()
    fun closeIfCurrent()
    fun resyncMenu()
    fun toggleCustomize()
    fun closeCustomize()
    fun openClassic()
    fun restyle(next: PauseScreenStyle)
    fun openSocial()
    fun openHost()
    fun openWardrobe()
    fun openSettings()
    fun openMods()
    fun closeGameSettings()
}
