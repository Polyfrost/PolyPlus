// OneConfig's public UI API is still marked experimental
@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.polyplus.client.gui.AccountSwitcherContent
import org.polyfrost.polyplus.client.gui.HostWorldFlow
import org.polyfrost.polyplus.client.gui.OverlayScreen
import org.polyfrost.polyplus.client.gui.rememberAccountActions
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform

/** The OneClient panels the vanilla title screen buttons open, drawn over the panorama like the social menu. */
internal class MenuPanelScreen(back: Screen?, private val panel: Panel) : OverlayScreen(back) {
    enum class Panel { Accounts, Notifications, HostWorld }

    @Composable
    override fun content(onClose: () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            // a click next to the panel closes it, like the popups in the PolyPlus menu
            Box(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
            when (panel) {
                Panel.Accounts -> Box(Modifier.align(Alignment.Center)) {
                    AccountSwitcherContent(rememberAccountActions(), onClose)
                }
                Panel.Notifications -> Box(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 12.dp)) {
                    NotificationsCenter()
                }
                Panel.HostWorld -> {
                    LaunchedEffect(Unit) {
                        FriendsRepository.refreshAll()
                        GroupsRepository.refreshGroups()
                    }
                    val friends by FriendsRepository.friends.collectAsState()
                    val groups by GroupsRepository.groups.collectAsState()
                    HostWorldFlow(previous ?: this@MenuPanelScreen, friends, groups, ChatIdentity.selfId(), onDismiss = onClose)
                }
            }
        }
    }

    companion object {
        fun open(panel: Panel) = ClientPlatform.setScreen(MenuPanelScreen(ClientPlatform.currentScreen(), panel))
    }
}
