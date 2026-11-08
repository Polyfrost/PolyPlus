@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
internal fun CreateGroupDialog(friends: List<Friend>, onCreate: (name: String, members: List<String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    ModalDialog(onDismiss) {
        DialogTitle("New Group")
        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.height(38.dp),
            placeholder = "Group name",
            leadingIcon = themedIcon(SOCIAL_ASSETS + "edit-02.svg"),
            maxLength = 32,
            invalid = false,
            enabled = true,
            singleLine = true,
            framed = true,
            fontSize = 14.sp,
            label = null,
            masked = false,
            focusRequester = null,
            interactionSource = null,
            trailing = null,
            onSubmit = null,
        )
        SocialText("Members", fontSize = 12.sp, color = SocialTextSecondary)
        if (friends.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                SocialText("Add some friends first", fontSize = 13.sp, color = SocialTextSecondary)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().height(240.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                friends.forEach { friend ->
                    val checked = friend.player in selected
                    val (interaction, hovered) = rememberSocialHover()
                    val border by animateColorAsState(if (checked) OneConfigTheme.accent else if (hovered) SocialHoverBorder else SocialBorderColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(SocialFieldShape)
                            .background(if (checked) OneConfigTheme.accent else SocialOffStateBackground)
                            .border(SocialBorderWidth, border, SocialFieldShape)
                            .hoverable(interaction)
                            .clickableWithSound {
                                selected = if (checked) selected - friend.player else selected + friend.player
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SocialAvatar(friend.player, 26.dp)
                        SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 13.sp, color = if (checked) OneConfigTheme.current.accentTextColor else SocialTextPrimary, modifier = Modifier.weight(1f))
                        if (checked) {
                            ThemedIcon(
                                SOCIAL_ASSETS + "check.svg",
                                OneConfigTheme.current.accentTextColor,
                                16.dp,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Cancel", themedIcon(SOCIAL_ASSETS + "x-close.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, true, onDismiss)
            TextButton(
                "Create",
                themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"),
                Modifier.weight(1f).height(38.dp),
                ButtonStyle.Primary,
                name.isNotBlank() && selected.isNotEmpty(),
            ) { onCreate(name.trim(), selected.toList()) }
        }
    }
}
