@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentStep
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentWidth
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberOnlineConsent
import org.polyfrost.polyplus.client.gui.themedIcon

private const val PLAYER_SYNC_MILLIS = 1_500L

@Composable
internal fun BoxScope.MarketplacePage(onBack: () -> Unit, onWardrobe: () -> Unit) {
    val state = remember { MarketplaceState() }
    var consent by rememberOnlineConsent()
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(state, consent, attempt) {
        if (!consent) return@LaunchedEffect
        PolyPlusClient.refreshCosmeticsIfNeeded()
        state.load()
        while (true) {
            delay(PLAYER_SYNC_MILLIS)
            state.syncPlayer()
        }
    }
    PageScrim()
    when {
        !consent -> Box(centerColumn(), contentAlignment = Alignment.Center) {
            ConsentStep("The Marketplace needs online services.", Modifier.width(ConsentWidth), onBack) { consent = true }
        }
        state.phase == MarketPhase.Failed ->
            EmptyState("Couldn't reach the store", centerColumn().panel(), "Check your connection and try again.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("Back", Modifier.height(38.dp), ButtonStyle.Secondary, onBack)
                    TextButton("Try again", themedIcon(LobbyIcons.REFRESH), Modifier.height(38.dp), ButtonStyle.Primary, true) { attempt++ }
                }
            }
        state.phase == MarketPhase.Loading -> EmptyState("Loading the store", centerColumn().panel())
        else -> MarketplaceLayout(state, onBack, onWardrobe)
    }
}
