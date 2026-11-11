@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore

@Composable
internal fun RenameSceneDialog(entry: LobbySceneEntry, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(entry.label) }
    val focus = remember { FocusRequester() }

    fun save() {
        if (name.isBlank()) return
        LobbySceneStore.rename(entry, name)
        onDismiss()
    }

    EscapeCloses(onDismiss)
    ModalDialog(onDismiss, 400.dp, true) {
        DialogTitle("Rename capture")
        SocialText("Pick a name for this scene.", fontSize = 13.sp, color = SocialTextSecondary)
        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.height(38.dp),
            placeholder = entry.label,
            leadingIcon = null,
            maxLength = LobbySceneStore.MAX_LABEL_LENGTH,
            invalid = false,
            enabled = true,
            singleLine = true,
            framed = true,
            fontSize = 14.sp,
            label = null,
            masked = false,
            focusRequester = focus,
            interactionSource = null,
            trailing = null,
            onSubmit = { save() },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Cancel", themedIcon(SOCIAL_ASSETS + "x-close.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, true, onDismiss)
            TextButton("Save", themedIcon(LobbyIcons.TICK), Modifier.weight(1f).height(38.dp), ButtonStyle.Primary, name.isNotBlank()) { save() }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
