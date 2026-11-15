@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.features.BadMods
import org.polyfrost.polyplus.client.utils.ClientPlatform

class BadModsScreen(parent: Screen?) :
    OneConfigComposeScreen(previous = parent, renderMode = OneConfigComposeScreen.RenderMode.Continuous) {
    @Composable
    override fun Content() {
        val close = { BadMods.dismiss(); dismiss() }
        ProvidePopupSounds(false) {
            ModalDialog(onDismiss = close, width = 480.dp, dismissOnScrim = true, scrollBody = false) {
                DialogBody {
                    BadModsContent(BadMods.warnings, onDismiss = close, onDisable = { BadMods.ignoreShown(); dismiss() })
                }
            }
        }
    }
}

@Composable
internal fun BadModsDialog() {
    val warnings = BadMods.warnings
    if (warnings.isEmpty()) return
    ModalDialog(onDismiss = BadMods::dismiss, width = 480.dp, dismissOnScrim = true, scrollBody = false) {
        DialogBody {
            BadModsContent(warnings, onDismiss = BadMods::dismiss, onDisable = BadMods::ignoreShown)
        }
    }
}

@Composable
private fun ColumnScope.DialogBody(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    ScrollContainer(scroll, Modifier.weight(1f, fill = false), scrollbar = false) {
        Column(Modifier.fillMaxWidth().padding(4.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun ColumnScope.BadModsContent(warnings: List<BadMods.Warning>, onDismiss: () -> Unit, onDisable: () -> Unit) {
    DialogTitle(if (warnings.size == 1) "Problematic mod detected" else "${warnings.size} problematic mods detected")
    Text(
        "These mods are known to cause crashes or conflicts with OneClient. We recommend removing them.",
        color = SocialTextSecondary,
        style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal),
    )
    val scroll = rememberScrollState()
    ScrollContainer(scroll, Modifier.fillMaxWidth().heightIn(max = 320.dp), scrollbar = false) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            warnings.forEach { BadModCard(it) }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(text = "Don't show again", onClick = onDisable, modifier = Modifier.weight(1f), size = ControlSize.Large)
        TextButton(text = "OK", onClick = onDismiss, modifier = Modifier.weight(1f), style = ControlStyle.Primary, size = ControlSize.Large)
    }
}

@Composable
private fun BadModCard(warning: BadMods.Warning) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(warning.name, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.SemiBold))
        Text(warning.message, color = SocialTextSecondary, style = OneConfigTheme.current.typography.small)
        warning.alternative?.let { (title, slug) ->
            TextButton(
                text = "Get $title",
                onClick = { ClientPlatform.openUri("https://modrinth.com/mod/$slug") },
                modifier = Modifier.fillMaxWidth(),
                size = ControlSize.Large,
            )
        }
    }
}
