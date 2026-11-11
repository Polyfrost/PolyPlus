@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.height
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
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.polyplus.client.network.http.PlayersApi
import org.polyfrost.polyplus.client.network.http.SocialApi
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.SocialErrors
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
            withContext(Dispatchers.IO) { SocialApi.sendRequest(id) }
                .onSuccess {
                    FriendsRepository.refreshAll()
                    SocialNotices.emit("Friend request sent to $name")
                    sending = false
                    onDismiss()
                }
                .onFailure {
                    SocialErrors.emit("Couldn't send friend request", it)
                    sending = false
                }
        }
    }

    ModalDialog(onDismiss, 400.dp, true) {
        DialogTitle("Add friend")
        SocialText("Enter a username to send a friend request", fontSize = 13.sp, color = SocialTextSecondary)
        TextField(
            value = username,
            onValueChange = { username = it; error = null },
            modifier = Modifier.height(38.dp),
            placeholder = "Username",
            leadingIcon = themedIcon(SOCIAL_ASSETS + "search-lg.svg"),
            maxLength = 16,
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
            onSubmit = { submit() },
        )
        error?.let { SocialText(it, fontSize = 12.sp, color = SocialDangerColor) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Cancel", themedIcon(SOCIAL_ASSETS + "x-close.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, true, onDismiss)
            TextButton(
                if (sending) "Sending…" else "Send request",
                themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"),
                Modifier.weight(1f).height(38.dp),
                ButtonStyle.Primary,
                username.isNotBlank() && !sending,
            ) { submit() }
        }
    }
}
