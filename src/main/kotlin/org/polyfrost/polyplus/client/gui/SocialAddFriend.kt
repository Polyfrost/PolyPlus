package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.network.http.PlayersApi
import org.polyfrost.polyplus.client.social.FriendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    SocialModalScrim(onDismiss) {
        ModalPanel(width = 400.dp) {
            SocialText("Add Friend", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
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
