package org.polyfrost.polyplus.client.gui.lobbyshell

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.polyplus.client.gui.ContextMenuList
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.contextMenu
import org.polyfrost.polyplus.client.gui.lobbysocial.MENU_OFFSET
import org.polyfrost.polyplus.client.gui.rememberSocialHover

private val RowHeight = 56.dp
private val RowGap = 6.dp
private val RowIcon = 36.dp
private val PlayersWidth = 104.dp
private val PingWidth = 56.dp

@Composable
internal fun ServerList(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val viewport = remember(listState) { ListReorderViewport(listState) }
    val reorder = rememberServerReorder(lobby, shown, canReorder, viewport)
    Box(modifier.fillMaxSize().serverReorderContainer(reorder)) {
        LazyColumn(Modifier.fillMaxSize(), listState, verticalArrangement = Arrangement.spacedBy(RowGap)) {
            items(reorder.entries, key = { it.key }) { entry ->
                ServerRow(lobby, entry, canReorder, onDelete, Modifier.animateItem().serverReorderItem(reorder, entry.key))
            }
        }
        reorder.overlayEntry?.let { entry -> ServerRow(lobby, entry, canReorder, onDelete, Modifier.serverReorderOverlay(reorder)) }
    }
}

@Composable
internal fun ServerRow(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier = Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val revision = servers.revision
    val (interaction, hovered) = rememberSocialHover()
    val selected = lobby.isSelected(data)
    val doubleClick = rememberDoubleClick { lobby.join(data) }
    LaunchedEffect(data) { servers.ping(data) }
    val failed = LobbyServers.failed(data)
    Row(
        modifier
            .fillMaxWidth()
            .height(RowHeight)
            .cardFrame(selected, hovered)
            .serverWash(data, 0.22f)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, vertical = true, onDelete = onDelete) }
            .hoverable(interaction)
            .clickableWithSound {
                doubleClick()
                lobby.select(data)
            }
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
        SocialButton("Join", filled = true) { lobby.join(data) }
        ServerMoreMenu(lobby, entry, canReorder, onDelete)
    }
}

@Composable
private fun ServerMoreMenu(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val drop = with(LocalDensity.current) { MENU_OFFSET.roundToPx() }
    Box {
        SocialIconButton(LobbyIcons.MORE, tooltip = "More") { open = !open }
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, drop),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ContextMenuList(serverMenuItems(lobby, entry, canReorder, vertical = true, onDelete = onDelete)) { open = false }
            }
        }
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
