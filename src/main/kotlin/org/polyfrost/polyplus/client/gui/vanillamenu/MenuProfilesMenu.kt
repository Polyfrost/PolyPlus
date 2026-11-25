@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.vanillamenu

//? if >= 26.1 {
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPlayerMenu
import org.polyfrost.polyplus.client.gui.lobbyshell.MenuRow
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.lobby.LobbySceneStore

private const val HIDE_NAME = "lobby.hideName"
private const val POSE = "lobby.pose"

/** Saved layout profiles: every menu layout plus the lobby character settings, so a reset can be undone any time. */
internal object MenuProfiles {
    fun save(name: String): Boolean =
        MenuLayoutStore.saveProfile(name, mapOf(HIDE_NAME to LobbySceneStore.hideName().toString(), POSE to LobbySceneStore.pose()))

    fun load(name: String): Boolean {
        val extra = MenuLayoutStore.loadProfile(name) ?: return false
        extra[HIDE_NAME]?.let { LobbySceneStore.setHideName(it.toBoolean()) }
        extra[POSE]?.let { LobbySceneStore.setPose(it) }
        LobbyPlayerMenu.hideName = LobbySceneStore.hideName()
        LobbyPlayerMenu.pose = LobbySceneStore.pose()
        MenuLayoutEditor.selection.clear()
        return true
    }
}

/** The small menu under the editor bar's "Profiles" button. */
@Composable
internal fun BoxScope.ProfilesDropdown(onClose: () -> Unit) {
    MenuLayoutStore.revision.intValue
    val theme = OneConfigTheme.current
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val profiles = MenuLayoutStore.profiles()
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = 112.dp)
            .width(300.dp)
            .panel(12.dp)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Save current layout", color = theme.textColorSecondary, style = theme.typography.small)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = name,
                onValueChange = { name = MenuLayoutStore.profileName(it) },
                modifier = Modifier.weight(1f),
                size = ControlSize.Compact,
                placeholder = "Profile name",
                maxLength = 32,
                fontSize = 13.sp,
            )
            TextButton(
                text = "Save",
                onClick = {
                    val clean = MenuLayoutStore.profileName(name)
                    status = if (MenuProfiles.save(clean)) "Saved \"$clean\"" else "Type a name first"
                    if (clean.isNotEmpty()) name = ""
                },
                style = ControlStyle.Primary,
                size = ControlSize.Compact,
                enabled = name.isNotBlank(),
            )
        }
        status?.let { Text(it, color = theme.textColorSecondary, style = theme.typography.small) }
        Text("Saved profiles", color = theme.textColorSecondary, style = theme.typography.small)
        if (profiles.isEmpty()) {
            Text("None yet. Save one above, then click it here to put it back on.", color = theme.textColorSecondary, style = theme.typography.small)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (profile in profiles) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(Modifier.weight(1f)) {
                        MenuRow(profile, "Load") {
                            if (MenuProfiles.load(profile)) onClose() else status = "Couldn't read \"$profile\""
                        }
                    }
                    TextButton(
                        text = "Delete",
                        onClick = {
                            MenuLayoutStore.deleteProfile(profile)
                            status = "Deleted \"$profile\""
                        },
                        style = ControlStyle.Secondary,
                        size = ControlSize.Compact,
                    )
                }
            }
        }
    }
}
//?}
