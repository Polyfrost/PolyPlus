@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.ThemedIcon

internal const val MINECRAFT_ICON = SOCIAL_ASSETS + "minecraft/minecraft-block.svg"

internal val LocalSettingsOwner = staticCompositionLocalOf { SettingsOwner.Minecraft }

@Composable
internal fun SettingsPageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    owner: SettingsOwner = LocalSettingsOwner.current,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val theme = OneConfigTheme.current
    Row(modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OwnerEyebrow(owner)
            Text(title, style = theme.typography.title, maxLines = 1, softWrap = false)
            Text(subtitle, color = theme.mutedText, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        actions()
    }
}

@Composable
internal fun OwnerEyebrow(owner: SettingsOwner) {
    val theme = OneConfigTheme.current
    Row(Modifier.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        owner.icon?.let { icon ->
            if (owner.tinted) {
                ThemedIcon(icon, theme.mutedText, 12.dp)
            } else {
                Icon(icon, null, Modifier.size(12.dp), fitAspectRatio = true)
            }
        }
        Text(owner.name, color = theme.mutedText, style = theme.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
    }
}
