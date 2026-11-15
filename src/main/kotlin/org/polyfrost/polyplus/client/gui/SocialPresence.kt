@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SelectOpenStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectValueAlignment
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import org.polyfrost.polyplus.client.social.PlayerStatusRepository

private val SELECTABLE_STATUSES = listOf(PlayerStatus.Online, PlayerStatus.Offline)
private const val CLOSED_CHEVRON_ANGLE = 180f
private val STATUS_MENU_WIDTH = 170.dp

internal fun selfStatusLabel(status: PlayerStatus): String = if (status == PlayerStatus.Offline) "Appear offline" else "Online"

@Composable
internal fun PresenceDot(online: Boolean, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val theme = OneConfigTheme.current
    val circle = theme.circleShape
    val dot = modifier.size(size).clip(circle).background(if (online) SocialSuccessColor else theme.mutedText)
    Box(if (ring != null) dot.border(2.dp, ring, circle) else dot)
}

@Composable
internal fun CompactStatusPicker() {
    val status by PlayerStatusRepository.status.collectAsState()
    var open by remember { mutableStateOf(false) }
    val angle by Motion.animateFloatAsState(if (open) 0f else CLOSED_CHEVRON_ANGLE)
    val muted = OneConfigTheme.current.mutedText
    Box {
        Row(
            Modifier.oneConfigClickable("Status, ${selfStatusLabel(status)}", { open = !open }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(selfStatusLabel(status), color = muted, style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
            ThemedIcon(SOCIAL_ASSETS + "chevron-up.svg", muted, 12.dp, Modifier.rotate(angle))
        }
        DropdownMenu(
            expanded = open,
            onDismiss = { open = false },
            modifier = Modifier.width(STATUS_MENU_WIDTH),
        ) {
            SELECTABLE_STATUSES.forEach { option ->
                MenuItem(
                    selfStatusLabel(option),
                    {
                        open = false
                        PlayerStatusRepository.set(option)
                    },
                    size = MenuItemSize.Compact,
                    selected = option == status,
                )
            }
        }
    }
}

@Composable
internal fun SelfStatusPicker(modifier: Modifier = Modifier) {
    val status by PlayerStatusRepository.status.collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PresenceDot(status != PlayerStatus.Offline, 10.dp)
        Select(
            selected = status,
            options = SELECTABLE_STATUSES,
            onSelect = PlayerStatusRepository::set,
            modifier = Modifier.weight(1f),
            label = ::selfStatusLabel,
            size = ControlSize.Large,
            leadingLabel = "Status",
            valueAlignment = SelectValueAlignment.End,
            openStyle = SelectOpenStyle.Accent,
        )
    }
}
