@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtonLayout
import org.polyfrost.oneconfig.api.ui.v1.components.TextPromptDialog
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore

@Composable
internal fun RenameSceneDialog(entry: LobbySceneEntry, onDismiss: () -> Unit) {
    TextPromptDialog(
        title = "Rename capture",
        initial = entry.label,
        onConfirm = {
            LobbySceneStore.rename(entry, it)
            onDismiss()
        },
        onDismiss = onDismiss,
        validate = { if (it.isBlank()) "Enter a name." else null },
        subtitle = "Pick a name for this scene.",
        placeholder = entry.label,
        maxLength = LobbySceneStore.MAX_LABEL_LENGTH,
        width = 400.dp,
        buttonLayout = DialogButtonLayout.Fill,
        confirmIcon = LobbyIcons.TICK,
        dismissIcon = SOCIAL_ASSETS + "x-close.svg",
    )
}
