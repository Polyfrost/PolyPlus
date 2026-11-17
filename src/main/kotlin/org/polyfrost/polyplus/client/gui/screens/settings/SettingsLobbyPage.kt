package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyShell
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellPanel
import org.polyfrost.polyplus.client.utils.ClientPlatform

@Composable
internal fun SettingsLobbyPage(lobby: LobbyShell) {
    Box(Modifier.fillMaxSize()) {
        PageScrim()
        ShellPanel(
            "Settings",
            { lobby.show(LobbyPage.Home) },
            ScreenShell.Lobby,
            Modifier
                .padding(start = LobbyLayout.Margin, end = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
                .fillMaxSize(),
        ) {
            SettingsBody({ ClientPlatform.currentScreen() })
        }
    }
}
