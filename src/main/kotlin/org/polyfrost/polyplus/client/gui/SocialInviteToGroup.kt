@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
private fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
        SocialText(text, fontSize = 13.sp, color = SocialTextSecondary)
    }
}

@Composable
internal fun InviteToGroupDialog(friends: List<Friend>, onInvite: (String) -> Unit, onDismiss: () -> Unit) {
    var invited by remember { mutableStateOf(setOf<String>()) }
    ModalDialog(onDismiss) {
        DialogTitle("Invite Friends")
        if (friends.isEmpty()) {
            EmptyHint("All your friends are already in this group", Modifier.height(120.dp))
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                friends.forEach { friend ->
                    val done = friend.player in invited
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SocialAvatar(friend.player, 30.dp)
                        SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        IconButton(
                            themedIcon(if (done) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg"),
                            if (done) "Invited" else "Invite",
                            null,
                            Modifier,
                            null,
                            SocialTextPrimary,
                            TooltipPlacement.BelowStart,
                        ) {
                            if (!done) {
                                onInvite(friend.player)
                                invited = invited + friend.player
                            }
                        }
                    }
                }
            }
        }
        TextButton("Done", themedIcon(SOCIAL_ASSETS + "x-close.svg"), Modifier.fillMaxWidth().height(38.dp), ButtonStyle.Primary, true, onDismiss)
    }
}
