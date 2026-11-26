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

// Mod Update Checker's button, for the lobby and the v1 main menu; 1.8.9 only.

internal const val MOD_UPDATES_ICON = SOCIAL_ASSETS + "refresh-cw-01.svg"

private const val POLL_MS = 1000L

/** The checker's live status, or null when its button shouldn't show. */
@Composable
internal fun rememberModUpdates(): ModUpdateChecker.Status? {
    if (PolyPlusMainMenuConfig.hideMainMenuModButtons || !ModUpdateChecker.isPresent) return null
    val status by produceState(ModUpdateChecker.status()) {
        while (true) {
            value = ModUpdateChecker.status()
            delay(POLL_MS)
        }
    }
    return status
}

/** Highlights the button while updates are waiting. */
@Composable
internal fun ModUpdateChecker.Status.tint(): Color? = OneConfigTheme.current.warning.takeIf { updates > 0 }

@Composable
internal fun ModUpdatesButton(screen: Screen) {
    val status = rememberModUpdates() ?: return
    NavIconButton(MOD_UPDATES_ICON, status.label, { ModUpdateChecker.open(screen) }, tint = status.tint() ?: Color.Unspecified)
}
