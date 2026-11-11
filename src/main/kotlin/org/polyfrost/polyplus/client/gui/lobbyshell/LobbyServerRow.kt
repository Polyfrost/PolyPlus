@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.rememberReorderState
import org.polyfrost.oneconfig.api.ui.v1.components.reorderItem
import org.polyfrost.oneconfig.api.ui.v1.components.withItemMoved
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.themedIcon

private val RowHeight = 56.dp
private val RowGap = 6.dp
private val RowIcon = 36.dp
private val PlayersWidth = 104.dp
private val PingWidth = 56.dp

@Composable
internal fun ServerList(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val saved = shown.filter { it.kind == ServerKind.SAVED }
    var dragged by remember { mutableStateOf<List<ServerEntry>?>(null) }
    val draggedKey = remember { arrayOfNulls<String>(1) }
    val order = dragged?.takeIf { canReorder && it.size == saved.size } ?: saved
    val entries = if (order === saved) shown else order.iterator().let { moved -> shown.map { if (it.kind == ServerKind.SAVED) moved.next() else it } }
    val currentOrder by rememberUpdatedState(order)
    val reorder = rememberReorderState(
        Orientation.Vertical,
        { from, to ->
            if (draggedKey[0] == null) draggedKey[0] = currentOrder.getOrNull(from)?.key
            currentOrder.withItemMoved(from, to)?.let { dragged = it }
        },
        {
            val moved = dragged
            val key = draggedKey[0]
            dragged = null
            draggedKey[0] = null
            val target = moved?.indexOfFirst { it.key == key } ?: -1
            if (moved != null && target >= 0) lobby.servers.moveTo(moved[target], target)
        },
    )
    Box(modifier.fillMaxSize().reorderAutoScroll(reorder, listState)) {
        LazyColumn(Modifier.fillMaxSize(), listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
            items(entries, key = { it.key }) { entry ->
                val index = order.indexOf(entry)
                val drag = if (index >= 0) Modifier.reorderItem(reorder, entry.key, index, canReorder) else Modifier
                ServerRow(lobby, entry, canReorder, onDelete, Modifier.animateItem(placementSpec = Motion.itemPlacementSpec()), drag)
            }
        }
    }
}

@Composable
internal fun ServerRow(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier, drag: Modifier = Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val revision = servers.revision
    ServerRevisions.of(data)
    val (interaction, hovered) = rememberSocialHover()
    val selected = lobby.isSelected(data)
    val doubleClick = rememberDoubleClick { lobby.join(data) }
    LaunchedEffect(data) { servers.ping(data) }
    val failed = LobbyServers.failed(data)
    LobbyHighlight(ppShape(16.dp)) { Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, vertical = true, onDelete = onDelete) }
            .hoverable(interaction)
            .clickableWithSound("${data.name}, ${serverPlayers(data)}", selected = selected.takeIf { it }, shape = ppShape(16.dp)) {
                doubleClick()
                lobby.select(data)
            }
            .then(drag)
            .cardFrame(selected, hovered)
            .serverWash(data, 0.22f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerIcon(data, revision, RowIcon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(data.name, 14.sp, Modifier.weight(1f, fill = false), weight = FontWeight.Medium)
                if (data.state() == ServerData.State.INCOMPATIBLE) Tag("Incompatible", TagTone.Danger)
            }
            MotdText(entry, revision, maxLines = 1)
        }
        Label(serverPlayers(data), 12.sp, Modifier.width(PlayersWidth), color = if (failed) SocialDangerText else MutedText)
        Label(serverPing(data), 12.sp, Modifier.width(PingWidth), color = MutedText)
        TextButton("Join", Modifier.height(38.dp), ButtonStyle.Primary) { lobby.join(data) }
        ServerMoreMenu(lobby, entry, canReorder, onDelete)
    } }
}

@Composable
internal fun ServerMoreMenu(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, vertical: Boolean = true) {
    AnchoredPopupButton(themedIcon(LobbyIcons.MORE), "More") { close ->
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
