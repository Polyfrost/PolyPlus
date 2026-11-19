@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.pausescreen.PauseView
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.screens.ShellPanel
import org.polyfrost.polyplus.client.gui.screens.shellModalSize

private val EnterLift = 24.dp
private val EdgeMargin = 28.dp

@Composable
internal fun BoxScope.PauseSettingsLayer(screen: PauseView, progress: Float, topInset: Dp) {
    val open = screen.settingsOpen
    if (!open && progress <= 0f) return
    if (open) EscapeCloses(screen::closeGameSettings)
    Box(Modifier.matchParentSize().padding(top = topInset)) {
        Box(
            Modifier
                .matchParentSize()
                .notFocusable()
                .clickable(enabled = open, indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = screen::closeGameSettings),
        )
        LobbyDarkChrome {
            ShellPanel(
                "Settings",
                screen::closeGameSettings,
                ScreenShell.InGame,
                Modifier
                    .align(Alignment.Center)
                    .padding(EdgeMargin)
                    .shellModalSize()
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * EnterLift.toPx()
                    }
                    .notFocusable()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                close = true,
            ) {
                SettingsBody({ screen.host })
            }
        }
    }
}
