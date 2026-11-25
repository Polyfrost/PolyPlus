package org.polyfrost.polyplus.client.gui.vanillamenu

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.utils.ClientPlatform

/** With "Vanilla Singleplayer And Multiplayer" on, the lobby opens the vanilla style lists instead of its own pages. */
internal object VanillaListScreens {
    @JvmStatic
    fun enabled(): Boolean = PolyPlusMainMenuConfig.vanillaListScreens

    fun open(page: LobbyPage, parent: Screen): Boolean {
        if (!enabled()) return false
        val next: Screen = when (page) {
            LobbyPage.Singleplayer -> SelectWorldScreen(parent)
            LobbyPage.Multiplayer -> JoinMultiplayerScreen(parent)
            else -> return false
        }
        ClientPlatform.setScreen(next)
        return true
    }
}
