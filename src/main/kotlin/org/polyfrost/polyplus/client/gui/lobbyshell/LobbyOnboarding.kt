package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.features.OnboardingFeatures
import org.polyfrost.polyplus.client.gui.OnboardingFlow
import org.polyfrost.polyplus.client.gui.OnboardingHost
import org.polyfrost.polyplus.client.lobby.LobbyFraming
import org.polyfrost.polyplus.compat.ClientConfigSync
import org.polyfrost.polyplus.privacy.PrivacyConsent
import kotlin.math.roundToInt

private const val REFERENCE_ASPECT = 16f / 9f

internal fun onboardingPending(): Boolean =
    !PolyPlusConfig.onboardingCompleted ||
        StartupAccessibility.pending() ||
        PrivacyConsent.needsPrompt() ||
        ClientConfigSync.needsImportRecovery() ||
        OnboardingFeatures.needsModSettingsChoice() ||
        OnboardingFeatures.needsMotionBlurChoice() ||
        OnboardingFeatures.needsGuides()

@Composable
internal fun BoxScope.LobbySetup(lobby: LobbyShell) {
    val root = LocalRootSize.current
    OnboardingFlow(
        OnboardingHost.Lobby,
        Modifier.align(Alignment.CenterStart).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val extra = (root.width - root.height * REFERENCE_ASPECT).toDouble().coerceAtLeast(0.0)
            val x = LobbyLayout.Margin.roundToPx() + (extra * (1.0 + LobbyFraming.SCREEN_X) / 4.0).roundToInt()
            layout(constraints.maxWidth, placeable.height) { placeable.place(x, 0) }
        }.onGloballyPositioned {
            val center = it.boundsInRoot().center
            if (root.width > 0f && root.height > 0f) {
                LobbyFraming.card(center.x / root.width * 2.0 - 1.0, 1.0 - center.y / root.height * 2.0)
            }
        },
    ) { lobby.finishSetup() }
}
