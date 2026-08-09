package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
internal fun CreateGroupDialog(friends: List<Friend>, onCreate: (name: String, members: List<String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialScrim)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(SocialPanelShape)
                    .background(SocialPopupBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SocialText("New Group", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                SocialTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Group name",
                    leadingIcon = SOCIAL_ASSETS + "edit-02.svg",
                    maxLength = 32,
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
                            val border by animateColorAsState(if (checked) Accent else if (hovered) SocialHoverBorder else SocialBorderColor)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .clip(SocialFieldShape)
                                    .background(if (checked) Accent.asSocialSelected else SocialControlBackground)
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
                                SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                if (checked) {
                                    Icon(
                                        SOCIAL_ASSETS + "check-circle.svg",
                                        SocialSuccessColor,
                                        Modifier.height(16.dp).width(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialButton("Cancel", icon = SOCIAL_ASSETS + "x-close.svg", modifier = Modifier.weight(1f), onClick = onDismiss)
                    SocialButton(
                        "Create",
                        icon = SOCIAL_ASSETS + "user-plus-01.svg",
                        modifier = Modifier.weight(1f),
                        filled = true,
                        enabled = name.isNotBlank() && selected.isNotEmpty(),
                        onClick = { onCreate(name.trim(), selected.toList()) },
                    )
                }
            }
        }
    }
}
