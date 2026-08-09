package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polyfrost.polyplus.client.network.http.PlayersApi
import org.polyfrost.polyplus.client.social.FriendsRepository

@Composable
internal fun AddFriendDialog(onDismiss: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val name = username.trim()
        if (name.isEmpty() || sending) return
        sending = true
        error = null
        scope.launch {
            val id = withContext(Dispatchers.IO) { PlayersApi.lookupByUsername(name) }
                .getOrElse {
                    error = "Couldn't look up that player"
                    sending = false
                    return@launch
                }
            if (id == null) {
                error = "No player found with that username"
                sending = false
                return@launch
            }
            FriendsRepository.sendRequest(id)
            sending = false
            onDismiss()
        }
    }

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
                    .width(400.dp)
                    .clip(SocialPanelShape)
                    .background(SocialPopupBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SocialText("Add Friend", fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                SocialText("Enter a username to send a friend request", fontSize = 13.sp, color = SocialTextSecondary)
                SocialTextField(
                    value = username,
                    onValueChange = { username = it; error = null },
                    placeholder = "Username",
                    leadingIcon = SOCIAL_ASSETS + "search-lg.svg",
                    maxLength = 16,
                    onSubmit = { submit() },
                )
                error?.let { SocialText(it, fontSize = 12.sp, color = SocialDangerColor) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SocialButton("Cancel", icon = SOCIAL_ASSETS + "x-close.svg", modifier = Modifier.weight(1f), onClick = onDismiss)
                    SocialButton(
                        if (sending) "Adding..." else "Add",
                        icon = SOCIAL_ASSETS + "user-plus-01.svg",
                        modifier = Modifier.weight(1f),
                        filled = true,
                        enabled = username.isNotBlank() && !sending,
                        onClick = { submit() },
                    )
                }
            }
        }
    }
}
