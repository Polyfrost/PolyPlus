@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtonLayout
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
internal fun DialogHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = SocialTextSecondary, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
    }
}

@Composable
internal fun InviteToGroupDialog(friends: List<Friend>, onInvite: (String) -> Unit, onDismiss: () -> Unit) {
    var invited by remember { mutableStateOf(setOf<String>()) }
    ModalDialog(
        onDismiss,
        title = { DialogTitle("Invite Friends") },
        buttonLayout = DialogButtonLayout.Fill,
        buttons = {
            TextButton(text = "Done", icon = SOCIAL_ASSETS + "x-close.svg", onClick = onDismiss, modifier = Modifier.weight(1f), style = ControlStyle.Primary, size = ControlSize.Large)
        },
    ) {
        if (friends.isEmpty()) {
            DialogHint("All your friends are already in this group", Modifier.height(120.dp))
        } else {
            val scroll = rememberScrollState()
            ScrollContainer(scroll, Modifier.fillMaxWidth().height(280.dp), scrollbar = false) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    friends.forEach { friend ->
                        val done = friend.player in invited
                        Row(
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SocialAvatar(friend.player, 30.dp)
                            Text(PlayerNamesRepository.displayName(friend.player), modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.body)
                            IconButton(
                                icon = if (done) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg",
                                contentDescription = if (done) "Invited" else "Invite",
                                onClick = {
                                    if (!done) {
                                        onInvite(friend.player)
                                        invited = invited + friend.player
                                    }
                                },
                                modifier = Modifier.size(34.dp),
                                tooltipPlacement = TooltipPlacement.BelowStart,
                                tint = SocialTextPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}
