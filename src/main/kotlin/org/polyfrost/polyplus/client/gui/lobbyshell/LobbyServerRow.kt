@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.reorderAutoScroll
import org.polyfrost.oneconfig.api.ui.v1.components.reorderContainer
import org.polyfrost.oneconfig.api.ui.v1.components.reorderOverlay
import org.polyfrost.oneconfig.api.ui.v1.components.reorderableItem
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import androidx.compose.ui.text.style.TextOverflow

private val RowHeight = 64.dp
private val MORE_MENU_GAP = 6.dp
private val RowGap = 6.dp
private val RowIcon = 36.dp
private val PlayersWidth = 104.dp
private val PingWidth = 56.dp

@Composable
internal fun ServerList(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val reorder = rememberServerReorder(lobby, shown, canReorder, listState)
    ScrollContainer(listState, modifier.fillMaxSize(), scrollbar = false) {
        Box(Modifier.fillMaxSize().reorderContainer(reorder.state).reorderAutoScroll(reorder.state)) {
            LazyColumn(Modifier.fillMaxSize(), listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
                itemsIndexed(reorder.entries, key = { _, entry -> entry.key }) { index, entry ->
                    val drag = Modifier.reorderableItem(reorder.state, entry.key, index, canReorder && entry.kind == ServerKind.SAVED)
                    ServerRow(lobby, entry, canReorder, onDelete, Modifier.animateItem(placementSpec = Motion.itemPlacementSpec()), drag)
                }
            }
            reorder.overlayEntry?.let { entry -> ServerRow(lobby, entry, canReorder, onDelete, Modifier.reorderOverlay(reorder.state)) }
        }
    }
}

@Composable
internal fun ServerRow(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier, drag: Modifier = Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val revision = servers.revision
    ServerRevisions.of(data)
    val hover = rememberHoverState()
    val selected = lobby.isSelected(data)
    val doubleClick = rememberDoubleClick { lobby.join(data) }
    LaunchedEffect(data) { servers.ping(data) }
    val failed = LobbyServers.failed(data)
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, vertical = true, onDelete = onDelete) }
            .hoverable(hover.interactionSource)
            .oneConfigClickable(
                "${data.shownName}, ${serverPlayers(data)}",
                {
                    doubleClick()
                    lobby.select(data)
                },
                selected = selected.takeIf { it },
                shape = OneConfigTheme.shape(16.dp),
            )
            .then(drag)
            .cardFrame(selected, hover.hovered)
            .serverWash(data, 0.22f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerIcon(data, revision, RowIcon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(data.shownName, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                if (servers.isPinned(data)) PinnedMark()
                if (data.state() == ServerData.State.INCOMPATIBLE) DangerTag("Incompatible".uppercase())
            }
            MotdText(entry, revision)
        }
        Text(serverPlayers(data), Modifier.width(PlayersWidth), color = if (failed) SocialDangerText else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(serverPing(data), Modifier.width(PingWidth), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        TextButton(text = "Join", onClick = { lobby.join(data) }, size = ControlSize.Large, style = ControlStyle.Primary)
        ServerMoreMenu(lobby, entry, canReorder, onDelete)
    }
}

@Composable
internal fun ServerMoreMenu(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, vertical: Boolean = true) {
    NavPopupButton(LobbyIcons.MORE, "More", gap = MORE_MENU_GAP) { close ->
        LobbyUserTheme { MenuList(serverMenuItems(lobby, entry, canReorder, vertical = vertical, onDelete = onDelete), close) }
    }
}

private fun answered(data: ServerData): Boolean =
    data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE

private fun serverPlayers(data: ServerData): String = when {
    data.isLan -> "Local network"
    LobbyServers.failed(data) -> "Offline"
    answered(data) -> data.players?.let { "%,d online".format(it.online()) }.orEmpty()
    else -> "Checking..."
}

private fun serverPing(data: ServerData): String = when {
    data.isLan || LobbyServers.failed(data) -> ""
    answered(data) -> "${data.ping} ms"
    else -> ""
}
