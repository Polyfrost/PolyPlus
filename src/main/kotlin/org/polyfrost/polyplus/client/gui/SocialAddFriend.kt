@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

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
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtonLayout
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
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

    ModalDialog(
        onDismiss = onDismiss,
        width = 400.dp,
        title = { DialogTitle("Add friend") },
        buttonLayout = DialogButtonLayout.Fill,
        buttons = {
            TextButton(text = "Cancel", icon = SOCIAL_ASSETS + "x-close.svg", onClick = onDismiss, modifier = Modifier.weight(1f), size = ControlSize.Large)
            TextButton(
                text = if (sending) "Sending…" else "Send request",
                icon = SOCIAL_ASSETS + "user-plus-01.svg",
                onClick = { submit() },
                modifier = Modifier.weight(1f),
                style = ControlStyle.Primary,
                enabled = username.isNotBlank() && !sending,
                size = ControlSize.Large,
            )
        },
    ) {
        Text("Enter a username to send a friend request", color = SocialTextSecondary, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
        TextField(
            value = username,
            onValueChange = { username = it; error = null },
            size = ControlSize.Large,
            placeholder = "Username",
            leadingIcon = SOCIAL_ASSETS + "search-lg.svg",
            maxLength = 16,
            fontSize = 14.sp,
            onSubmit = { submit() },
        )
        error?.let { Text(it, color = SocialDangerText, style = OneConfigTheme.current.typography.small) }
    }
}
