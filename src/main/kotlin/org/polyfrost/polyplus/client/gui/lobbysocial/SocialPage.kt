@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentStep
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentWidth
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.privacy.PrivacyConsent

internal enum class SocialAvailability { Ready, Connecting, SignedOut, Declined }

private const val CONNECT_GRACE_MILLIS = 4000L
private const val POLL_MILLIS = 500L

private fun liveAvailability(deadline: Long): SocialAvailability = when {
    !PrivacyConsent.allowsOnlineServices() -> SocialAvailability.Declined
    PolyConnection.isConnected -> SocialAvailability.Ready
    System.currentTimeMillis() < deadline -> SocialAvailability.Connecting
    else -> SocialAvailability.SignedOut
}

@Composable
internal fun rememberAvailability(): SocialAvailability {
    val availability by produceState(liveAvailability(System.currentTimeMillis() + CONNECT_GRACE_MILLIS)) {
        var deadline = System.currentTimeMillis() + CONNECT_GRACE_MILLIS
        while (true) {
            value = liveAvailability(deadline)
            if (value == SocialAvailability.Ready || value == SocialAvailability.Declined) deadline = System.currentTimeMillis() + CONNECT_GRACE_MILLIS
            delay(POLL_MILLIS)
        }
    }
    return availability
}

@Composable
internal fun rememberSocialPageState(): SocialPageState {
    val source = remember { LiveSocialSource() }
    val state = remember(source) { SocialPageState(source) }
    LaunchedEffect(source) { source.refresh() }
    DisposableEffect(Unit) { onDispose { state.dialog = null } }
    return state
}

@Composable
internal fun SocialDialogHost(state: SocialPageState) {
    state.dialog?.let { dialog -> state.source.Dialog(dialog) { state.dialog = null } }
}

@Composable
internal fun SocialPage(onBack: () -> Unit) {
    val state = rememberSocialPageState()
    val availability = rememberAvailability()
    Box(Modifier.fillMaxSize()) {
        PageScrim()
        if (availability == SocialAvailability.Ready) {
            SocialDirectory(state, state.source.snapshot(), onBack)
        } else {
            BlockedMessage(availability, state.source, onBack, centerColumn())
        }
        SocialToastHost(Modifier.align(Alignment.TopEnd).padding(top = LobbyLayout.Top, end = LobbyLayout.Margin))
        SocialDialogHost(state)
    }
}

@Composable
internal fun BlockedMessage(availability: SocialAvailability, source: SocialSource, onBack: () -> Unit, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (availability) {
            SocialAvailability.Declined -> ConsentStep("Friends, messages and hosted worlds need online services.", Modifier.width(ConsentWidth), onBack) { }
            SocialAvailability.SignedOut -> EmptyState(
                "Not signed in",
                Modifier.width(ConsentWidth).socialSurface(),
                "Friends, messages and hosted worlds need a signed-in Minecraft account and a connection to OneClient.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SocialButton("Back", onClick = onBack)
                    SocialButton("Try again", icon = LobbyIcons.REFRESH, filled = true) {
                        PolyConnection.reconnect()
                        source.refresh()
                    }
                }
            }
            else -> EmptyState("Connecting", Modifier.width(ConsentWidth).socialSurface())
        }
    }
}
