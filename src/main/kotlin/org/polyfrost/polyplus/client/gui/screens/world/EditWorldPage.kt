@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.world

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.lobbyshell.relativeDayText
import org.polyfrost.polyplus.client.gui.screens.ScreenShell

private const val MAX_NAME = 128

class EditWorldShell(shell: ScreenShell, private val draft: EditWorldDraft) : WorldShellScreen(shell) {
    private var name by mutableStateOf(draft.name)

    override val title: String get() = "Edit world"

    override fun back() = draft.cancel()

    @Composable
    override fun Body() {
        EditWorldContent(draft, name) { name = it }
    }
}

@Composable
fun EditWorldContent(draft: EditWorldDraft, name: String, onName: (String) -> Unit) {
    var hasIcon by remember(draft) { mutableStateOf(draft.hasIcon) }
    val icon = if (hasIcon) draft.icon else null
    val valid = name.isNotBlank()
    val save = { if (valid) draft.save(name) }
    WorldContentColumn {
        PanelPageColumn(Modifier.weight(1f)) {
            EditHeader(draft, name, onName, icon, valid, save)
            SectionSpace()
            WorldBlock("Files") {
                ActionGroup(
                    listOfNotNull(
                        WorldAction("link-external-01", "Open world folder", "Opens \"${draft.folder}\" in your file browser.", onClick = draft::openFolder),
                        if (draft.canBackup) WorldAction("link-external-01", "Open backups folder", "Where backups of all your worlds are kept.", onClick = draft::openBackups) else null,
                        if (!draft.canResetIcon) null else WorldAction("image-01", "Reset icon", if (hasIcon) "Go back to no icon. A new one is taken next time you play." else "This world has no icon.", enabled = hasIcon) {
                            Confirm.ask(
                                "Reset world icon?",
                                "The current icon is removed. A new one is taken the next time you play.",
                                "Reset icon",
                                {
                                    draft.resetIcon()
                                    hasIcon = false
                                },
                                style = ControlStyle.Primary,
                                icon = SOCIAL_ASSETS + "image-01.svg",
                            )
                        },
                    ),
                )
            }
            val maintenance = listOfNotNull(
                if (draft.canBackup) WorldAction("package-01", "Back up world", "Saves a zip copy to the backups folder.", onClick = draft::backup) else null,
                if (draft.canOptimize) WorldAction("refresh-cw-01", "Optimize world", "Upgrades every chunk to this version. This can take a while.", onClick = draft::optimize) else null,
            )
            if (maintenance.isNotEmpty()) {
                SectionSpace()
                WorldBlock("Maintenance") {
                    ActionGroup(maintenance)
                }
            }
            SectionSpace()
            WorldBlock("Danger zone") {
                ActionGroup(
                    listOf(
                        WorldAction("trash-01", "Delete world", if (draft.canBackup) "Removes the world from this computer. Backups are kept." else "Removes the world from this computer.", danger = true) {
                            Confirm.ask(
                                "Delete \"${name.ifBlank { draft.name }}\"?",
                                if (draft.canBackup) "The world is deleted for good. This can't be undone, but backups you made are kept." else "The world is deleted for good. This can't be undone.",
                                "Delete world",
                                draft::delete,
                                style = ControlStyle.Danger,
                                icon = SOCIAL_ASSETS + "trash-01.svg",
                            )
                        },
                    ),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton("Cancel", draft::cancel, style = ControlStyle.Ghost, size = ControlSize.Large)
            Spacer(Modifier.weight(1f))
            TextButton("Save", save, style = ControlStyle.Primary, size = ControlSize.Large, enabled = valid)
        }
    }
}

@Composable
private fun EditHeader(
    draft: EditWorldDraft,
    name: String,
    onName: (String) -> Unit,
    icon: ImageBitmap?,
    valid: Boolean,
    save: () -> Unit,
) {
    val theme = OneConfigTheme.current
    Row(
        Modifier.fillMaxWidth().card(WorldKit.CardRadius).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WorldTile(name, icon, 88.dp, hardcore = draft.hardcore)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = name,
                onValueChange = onName,
                modifier = Modifier.fillMaxWidth(),
                label = "World name",
                invalid = !valid,
                maxLength = MAX_NAME,
                onSubmit = save,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.mode?.let { Badge(it, tone = if (draft.hardcore) BadgeTone.Danger else BadgeTone.Accent) }
                if (draft.commands) Badge("Commands on")
                val played = relativeDayText(draft.lastPlayed)?.let { "Last played $it" } ?: "Not played yet"
                val meta = listOfNotNull(played, draft.version).joinToString("  ·  ")
                Text(meta, Modifier.weight(1f, fill = false), color = theme.textColorSecondary, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
    }
}
