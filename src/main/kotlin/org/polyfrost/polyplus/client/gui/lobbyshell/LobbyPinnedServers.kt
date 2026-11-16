@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.gui.ThemedIcon

internal const val PIN_ICON = "star"
internal const val PINNED_ICON = "star-filled"

private val StripHeight = 32.dp
private val ChipIcon = 22.dp
private val MarkSize = 14.dp

@Composable
internal fun PinnedServersStrip(lobby: LobbyShell, modifier: Modifier = Modifier) {
    val shown = lobby.servers.pinned.take(PolyPlusRecentServers.PINNED_MAX)
    if (shown.isEmpty()) return
    Row(
        modifier.fillMaxWidth().height(StripHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PinnedMark()
        shown.forEach { entry -> PinnedChip(lobby, entry, Modifier.weight(1f, fill = false)) }
    }
}

@Composable
private fun PinnedChip(lobby: LobbyShell, entry: ServerEntry, modifier: Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val name = data.shownName
    val hover = rememberHoverState()
    LaunchedEffect(data) { servers.ping(data) }
    TooltipBox(name, modifier, TooltipPlacement.AboveCenter, Dp.Unspecified) {
        Row(
            Modifier
                .height(StripHeight)
                .cardFrame(false, hover.hovered)
                .contextMenu { serverMenuItems(lobby, entry) }
                .hoverable(hover.interactionSource)
                .oneConfigClickable("Join $name", { lobby.join(data) })
                .padding(start = 5.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ServerIcon(data, servers.revision, ChipIcon)
            Text(name, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

@Composable
internal fun PinnedMark() {
    ThemedIcon(PINNED_ICON, OneConfigTheme.accent, MarkSize)
}
