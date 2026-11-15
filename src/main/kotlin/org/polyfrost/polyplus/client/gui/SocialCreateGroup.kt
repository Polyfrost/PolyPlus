@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtonLayout
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
internal fun CreateGroupDialog(friends: List<Friend>, onCreate: (name: String, members: List<String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }

    ModalDialog(
        onDismiss,
        title = { DialogTitle("New Group") },
        buttonLayout = DialogButtonLayout.Fill,
        buttons = {
            TextButton(text = "Cancel", icon = SOCIAL_ASSETS + "x-close.svg", onClick = onDismiss, modifier = Modifier.weight(1f), size = ControlSize.Large)
            TextButton(
                text = "Create",
                icon = SOCIAL_ASSETS + "user-plus-01.svg",
                onClick = { onCreate(name.trim(), selected.toList()) },
                modifier = Modifier.weight(1f),
                style = ControlStyle.Primary,
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                size = ControlSize.Large,
            )
        },
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            size = ControlSize.Large,
            placeholder = "Group name",
            leadingIcon = SOCIAL_ASSETS + "edit-02.svg",
            maxLength = 32,
            fontSize = 14.sp,
        )
        SectionHeader("Members")
        if (friends.isEmpty()) {
            DialogHint("Add some friends first", Modifier.height(120.dp))
        } else {
            val scroll = rememberScrollState()
            ScrollContainer(scroll, Modifier.fillMaxWidth().height(240.dp), scrollbar = false) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    friends.forEach { friend ->
                        val checked = friend.player in selected
                        val displayName = PlayerNamesRepository.displayName(friend.player)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .selectableRow(checked, { selected = if (checked) selected - friend.player else selected + friend.player }, displayName, shape = SocialFieldShape)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            SocialAvatar(friend.player, 26.dp)
                            val ink = SelectionDefaults.itemContentColor(checked)
                            Text(displayName, modifier = Modifier.weight(1f), color = ink, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
                            if (checked) {
                                ThemedIcon(SOCIAL_ASSETS + "check.svg", if (SelectionDefaults.itemStyle == ItemSelectionStyle.Accent) ink else OneConfigTheme.accent, 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
