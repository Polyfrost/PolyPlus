@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import org.polyfrost.polyplus.client.social.PlayerStatusRepository

private val SELECTABLE_STATUSES = listOf(PlayerStatus.Online, PlayerStatus.Offline)
private const val CLOSED_CHEVRON_ANGLE = 180f
private val STATUS_MENU_OFFSET = 22.dp
private val STATUS_MENU_WIDTH = 170.dp

internal fun selfStatusLabel(status: PlayerStatus): String = if (status == PlayerStatus.Offline) "Appear offline" else "Online"

@Composable
internal fun PresenceDot(online: Boolean, size: Dp, modifier: Modifier = Modifier, ring: Color? = null) {
    val circle = OneConfigTheme.current.circleShape
    val dot = modifier.size(size).clip(circle).background(if (online) SocialSuccessColor else MutedText)
    Box(if (ring != null) dot.border(2.dp, ring, circle) else dot)
}

@Composable
internal fun CompactStatusPicker(extra: @Composable ColumnScope.(close: () -> Unit) -> Unit = {}) {
    val status by PlayerStatusRepository.status.collectAsState()
    var open by remember { mutableStateOf(false) }
    val angle by animateFloatAsState(if (open) 0f else CLOSED_CHEVRON_ANGLE)
    val drop = with(LocalDensity.current) { STATUS_MENU_OFFSET.roundToPx() }
    val close = { open = false }
    Box {
        Row(
            Modifier.clickableWithSound { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SocialText(selfStatusLabel(status), fontSize = 12.sp, color = MutedText, maxLines = 1)
            ThemedIcon(SOCIAL_ASSETS + "chevron-up.svg", MutedText, 12.dp, Modifier.rotate(angle))
        }
        if (open) {
            EscapeCloses(close)
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, drop),
                onDismissRequest = close,
                properties = PopupProperties(focusable = true),
            ) {
                MenuSurface(Modifier.width(STATUS_MENU_WIDTH)) {
                    SELECTABLE_STATUSES.forEach { option ->
                        MenuItem(selfStatusLabel(option), null, null, MenuItemSize.Compact, false, option == status, true, null, Modifier, null) {
                            close()
                            PlayerStatusRepository.set(option)
                        }
                    }
                    extra(close)
                }
            }
        }
    }
}

@Composable
internal fun SelfStatusPicker(modifier: Modifier = Modifier) {
    val status by PlayerStatusRepository.status.collectAsState()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PresenceDot(status != PlayerStatus.Offline, 10.dp)
        SocialDropdown(
            label = "Status",
            options = SELECTABLE_STATUSES,
            selected = status,
            labelFor = ::selfStatusLabel,
            modifier = Modifier.weight(1f),
            onSelect = PlayerStatusRepository::set,
        )
    }
}
