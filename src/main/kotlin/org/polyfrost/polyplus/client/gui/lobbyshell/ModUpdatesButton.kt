@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.compat.ModUpdateChecker

private const val POLL_MS = 1000L

/** Mod Update Checker's button for the lobby's top bar; 1.8.9 only. */
@Composable
internal fun ModUpdatesButton(screen: Screen) {
    if (PolyPlusMainMenuConfig.hideMainMenuModButtons || !ModUpdateChecker.isPresent) return
    val status by produceState(ModUpdateChecker.status()) {
        while (true) {
            value = ModUpdateChecker.status()
            delay(POLL_MS)
        }
    }
    val tint = if (status.updates > 0) OneConfigTheme.current.warning else Color.Unspecified
    NavIconButton(SOCIAL_ASSETS + "refresh-cw-01.svg", status.label, { ModUpdateChecker.open(screen) }, tint = tint)
}
