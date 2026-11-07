@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.lobby.LobbyMotd*/
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.StringDecomposer
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.gui.ASSETS
import org.polyfrost.polyplus.client.gui.ContextItem
import org.polyfrost.polyplus.client.gui.ContextMenuHost
import org.polyfrost.polyplus.client.gui.ModalPanel
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.RasterImage
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialModalScrim
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.contextMenu
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.serveredit.ServerPreviewCard
import org.polyfrost.polyplus.client.gui.serveredit.ServerStatusProbe
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID

private enum class ServerFilter { All, Recent }

private class OnlineFriend(val name: String, val seed: UUID, val realSkin: Boolean, val detail: String)

private val CardGap = 12.dp
private val StripHeight = 60.dp
private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 2
private const val HERO_FACES = 3
private const val SEPARATOR = " · "
private const val GRID_ICON = "dots-grid"
private const val LIST_ICON = "align-left"
private const val DIRECT_PING_DEBOUNCE_MILLIS = 500L
private const val PROBE_TICK_MILLIS = 50L
private val DirectPreviewHeight = 220.dp

@Composable
internal fun BoxScope.MultiplayerPage(lobby: LobbyShell) {
    ContextMenuHost {
        val friends = rememberOnlineFriends()
        PageScrim()
        ServerBrowser(
            lobby,
            friends,
            onBack = { lobby.show(LobbyPage.Home) },
            onDirect = { lobby.directOpen = true },
            onDelete = { lobby.deleting = it },
            modifier = Modifier
                .padding(start = LobbyLayout.Margin, end = LobbyLayout.BarEnd, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
                .fillMaxSize(),
        )
        FriendsPanel(
            friends,
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.RightColumnBottom)
                .width(LobbyLayout.UnitWidth)
                .fillMaxHeight(),
        )
        if (lobby.directOpen) DirectConnectDialog(lobby) { lobby.directOpen = false }
        lobby.deleting?.let { entry ->
            DeleteServerDialog(entry, onConfirm = { lobby.forget(entry) }, onDismiss = { lobby.deleting = null })
        }
    }
}

@Composable
private fun rememberOnlineFriends(): List<OnlineFriend> {
    val friends by FriendsRepository.friends.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val online = friends.filter { it.online }
    LaunchedEffect(Unit) { if (PrivacyConsent.allowsOnlineServices()) FriendsRepository.refreshAll() }
    LaunchedEffect(online) { PlayerNamesRepository.resolve(online.map { it.player }) }
    return online.map { OnlineFriend(names[it.player] ?: PlayerNamesRepository.nameOr(it.player), seedOf(it.player), true, "Online") }
}

@Composable
private fun FriendsPanel(friends: List<OnlineFriend>, modifier: Modifier) {
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelTitle("Friends")
        if (friends.isEmpty()) {
            EmptyState("No friends online", Modifier.fillMaxWidth().weight(1f), "Friends show up here when they are online.")
            return@Column
        }
        ScrollFade(Modifier.fillMaxWidth().weight(1f), 8.dp) {
            friends.forEach { friend ->
                Row(
                    lobbyHover(friend.name).fillMaxWidth().card(8.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Head(friend.seed, 38.dp, realSkin = friend.realSkin)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label(friend.name, 14.sp, weight = FontWeight.Medium)
                        Label(friend.detail, 12.sp, color = MutedText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerBrowser(lobby: LobbyShell, friends: List<OnlineFriend>, onBack: () -> Unit, onDirect: () -> Unit, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val servers = lobby.servers
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ServerFilter.All) }
    var listView by remember { mutableStateOf(PolyPlusLobbyConfig.serverListView) }
    val hideAddress = Minecraft.getInstance().options.hideServerAddress
    val base = when (filter) {
        ServerFilter.All -> servers.featured + servers.saved + servers.lan + servers.sponsored
        ServerFilter.Recent -> servers.recent
    }
    val shown = base.filter { query.isBlank() || it.data.name.contains(query.trim(), true) || (!hideAddress && it.data.ip.contains(query.trim(), true)) }
    val canReorder = filter == ServerFilter.All && query.isBlank()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CardGap)) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).panel(16.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackButton(onBack)
            PageTitle("Servers", Modifier.padding(end = 2.dp))
            SocialTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = "Search",
                leadingIcon = LobbyIcons.SEARCH,
            )
            ServerFilter.entries.forEach { entry ->
                Box(Modifier) { Chip(entry.name, selected = entry == filter) { filter = entry } }
            }
            LayoutToggle(listView) {
                listView = it
                PolyPlusLobbyConfig.serverListView = it
                PolyPlusLobbyConfig.save()
            }
            SocialButton("Add server", onClick = lobby::addServer)
            SocialButton("Direct connect", onClick = onDirect)
            if (PolyPlusMainMenuConfig.realmsSupported() && !PolyPlusMainMenuConfig.hideMainMenuRealms) {
                SocialButton("Realms", onClick = lobby::openRealms)
            }
            SocialIconButton(LobbyIcons.REFRESH, tooltip = "Refresh", onClick = servers::refresh)
        }
        LastPlayedStrip(lobby, friends)
        if (shown.isEmpty()) {
            val body = Modifier.fillMaxWidth().weight(1f).panel(16.dp)
            when {
                query.isNotBlank() -> EmptyState("No servers match that search", body)
                filter == ServerFilter.Recent -> EmptyState("No recent servers", body, "Servers you join show up here.")
                else -> EmptyState("No saved servers", body, "Add a server, or use direct connect.") {
                    SocialButton("Add server", icon = LobbyIcons.PLUS, filled = true, onClick = lobby::addServer)
                }
            }
        } else if (listView) {
            ServerList(lobby, shown, canReorder, onDelete, Modifier.fillMaxWidth().weight(1f))
        } else {
            ServerGrid(lobby, shown, canReorder, onDelete, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun LayoutToggle(listView: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        LayoutOption(GRID_ICON, "Grid view", !listView) { onChange(false) }
        LayoutOption(LIST_ICON, "List view", listView) { onChange(true) }
    }
}

@Composable
private fun LayoutOption(icon: String, tooltip: String, selected: Boolean, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    SocialIconButton(
        icon,
        background = if (selected) theme.componentBackground else null,
        tint = if (selected) SocialTextPrimary else MutedText,
        tooltip = tooltip,
        onClick = onClick,
    )
}

@Composable
private fun ServerGrid(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = (maxHeight - CardGap * (GRID_ROWS - 1)) / GRID_ROWS
        val gridState = rememberLazyGridState()
        val viewport = remember(gridState) { GridReorderViewport(gridState) }
        val reorder = rememberServerReorder(lobby, shown, canReorder, viewport)
        Box(Modifier.fillMaxSize().serverReorderContainer(reorder)) {
            LazyVerticalGrid(
                GridCells.Fixed(GRID_COLUMNS),
                Modifier.fillMaxSize(),
                gridState,
                horizontalArrangement = Arrangement.spacedBy(CardGap),
                verticalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                items(reorder.entries, key = { it.key }) { entry ->
                    ServerCard(lobby, entry, canReorder, onDelete, Modifier.height(rowHeight).animateItem().serverReorderItem(reorder, entry.key))
                }
            }
            reorder.overlayEntry?.let { entry -> ServerCard(lobby, entry, canReorder, onDelete, Modifier.serverReorderOverlay(reorder)) }
        }
    }
}

@Composable
internal fun ServerBanner(data: ServerData, revision: Int, height: Dp) {
    @Suppress("UNUSED_EXPRESSION") revision
    ServerBackdrop(data, Modifier.fillMaxWidth().height(height))
}

@Composable
internal fun ServerIcon(data: ServerData, revision: Int, size: Dp) {
    @Suppress("UNUSED_EXPRESSION") revision
    val favicon = rememberFavicon(data.iconBytes)
    val shape = ppShape(if (size > 48.dp) 12.dp else 8.dp)
    val modifier = Modifier.size(size).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape)
    if (favicon != null) {
        Image(favicon, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
    } else {
        RasterImage(ASSETS + if (data.ip.contains("hypixel", true)) "hypixel.png" else "server.png", modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ServerCard(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val revision = servers.revision
    val banner = 104.dp
    val icon = 52.dp
    val overhang = 12.dp
    val (interaction, hovered) = rememberSocialHover()
    val selected = lobby.isSelected(data)
    val doubleClick = rememberDoubleClick { lobby.join(data) }
    LaunchedEffect(data) { servers.ping(data) }
    Box(
        modifier
            .cardFrame(selected, hovered)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, onDelete = onDelete) }
            .hoverable(interaction)
            .clickableWithSound {
                doubleClick()
                lobby.select(data)
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            ServerBanner(data, revision, banner)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = overhang + 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ServerText(entry, revision, 16)
                SocialButton("Join", filled = true) { lobby.join(data) }
            }
        }
        Box(Modifier.offset(x = 12.dp, y = banner - icon + overhang)) { ServerIcon(data, revision, icon) }
        if (hovered) CardActions(lobby, entry, canReorder, onDelete, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

internal class ServerAction(val item: ContextItem, val rotation: Float? = null)

internal fun serverActions(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: ((ServerEntry) -> Unit)?, vertical: Boolean = false): List<ServerAction> {
    val servers = lobby.servers
    val save = ServerAction(ContextItem("Save to list", LobbyIcons.PLUS) { servers.saveUnsaved(entry) })
    return when (entry.kind) {
        ServerKind.SAVED -> listOfNotNull(
            ServerAction(ContextItem(if (vertical) "Move up" else "Move left", enabled = entry.savedIndex > 0) { servers.move(entry, -1) }, if (vertical) 0f else -90f).takeIf { canReorder },
            ServerAction(ContextItem(if (vertical) "Move down" else "Move right", enabled = entry.savedIndex < servers.saved.lastIndex) { servers.move(entry, 1) }, if (vertical) 180f else 90f).takeIf { canReorder },
            ServerAction(ContextItem("Edit", SOCIAL_ASSETS + "edit-02.svg") { lobby.editServer(entry) }),
            onDelete?.let { delete -> ServerAction(ContextItem("Delete", LobbyIcons.TRASH, danger = true) { delete(entry) }) },
        )
        ServerKind.UNSAVED -> listOf(save)
        ServerKind.FEATURED, ServerKind.SPONSORED -> {
            val campaign = entry.featured?.featured?.takeIf { it.dismissibleInServerList }
            listOfNotNull(save, campaign?.let { ServerAction(ContextItem("Dismiss", LobbyIcons.CLOSE) { FeaturedServers.dismissMultiplayer(it.campaignId) }) })
        }
        ServerKind.LAN -> emptyList()
    }
}

internal fun serverMenuItems(
    lobby: LobbyShell,
    entry: ServerEntry,
    canReorder: Boolean = false,
    vertical: Boolean = false,
    onDelete: ((ServerEntry) -> Unit)? = null,
): List<ContextItem> =
    listOf(ContextItem("Join", SOCIAL_ASSETS + "log-in-04.svg") { lobby.join(entry.data) }) + serverActions(lobby, entry, canReorder, onDelete, vertical).map { it.item }

@Composable
private fun CardActions(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val small = Modifier.size(28.dp)
    val fill = OneConfigTheme.current.popupBackground
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        serverActions(lobby, entry, canReorder, onDelete).forEach { action ->
            val item = action.item
            val rotation = action.rotation
            val icon = item.icon
            when {
                rotation != null -> MoveArrow(item.enabled, rotation, item.onClick)
                icon != null -> SocialIconButton(icon, small, background = fill, tooltip = item.label, onClick = item.onClick)
            }
        }
    }
}

@Composable
internal fun MoveArrow(enabled: Boolean, rotation: Float, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    IconButton(
        LobbyIcons.UP,
        Modifier.size(28.dp).clip(SocialFieldShape).background(theme.popupBackground).padding(7.dp).rotate(rotation),
        theme.textColor,
        theme.textColor,
        enabled,
        false,
        onClick,
    )
}

@Composable
private fun ServerText(entry: ServerEntry, revision: Int, titleSize: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    val data = entry.data
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(data.name, titleSize.sp, weight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (data.state() == ServerData.State.INCOMPATIBLE) Tag("Incompatible", TagTone.Danger)
            Label(serverMeta(data), 12.sp, color = if (LobbyServers.failed(data)) SocialDangerText else MutedText)
        }
        MotdText(entry, revision)
    }
}

internal fun serverMeta(data: ServerData): String {
    val answered = data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE
    return when {
        data.isLan -> "Local network"
        LobbyServers.failed(data) -> "Offline"
        answered -> listOfNotNull(data.players?.let { "%,d online".format(it.online()) }, "${data.ping} ms", Platform.i18n().getUnformattedText(data.version)).joinToString(SEPARATOR)
        else -> "Checking status..."
    }
}

@Composable
internal fun MotdText(entry: ServerEntry, revision: Int, maxLines: Int = 2) {
    @Suppress("UNUSED_EXPRESSION") revision
    val data = entry.data
    val muted = MutedText
    val text = when {
        entry.kind == ServerKind.LAN || LobbyServers.failed(data) || data.state() == ServerData.State.PINGING -> AnnotatedString("")
        else -> motdText(data.motd, muted)
    }
    BasicText(
        text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = muted, fontSize = 12.sp, fontFamily = OneConfigTheme.current.fontFamily),
    )
}

internal fun motdText(component: Component?, fallback: Color): AnnotatedString {
    component ?: return AnnotatedString("")
    return buildAnnotatedString {
        val run = StringBuilder()
        var runColor = fallback
        fun flush() {
            if (run.isEmpty()) return
            withStyle(SpanStyle(color = runColor)) { append(run.toString()) }
            run.clear()
        }
        //? if > 1.8.9 {
        StringDecomposer.iterateFormatted(component, Style.EMPTY) { _, style, codepoint ->
            val color = style.color?.let { Color(0xFF000000 or it.value.toLong()) } ?: fallback
            if (color != runColor) {
                flush()
                runColor = color
            }
            run.appendCodePoint(if (codepoint == '\n'.code) ' '.code else codepoint)
            true
        }
        //?} else {
        /*LobbyMotd.forEach(component.string) { value, codepoint ->
            val color = value?.let { Color(0xFF000000 or it.toLong()) } ?: fallback
            if (color != runColor) {
                flush()
                runColor = color
            }
            run.appendCodePoint(if (codepoint == '\n'.code) ' '.code else codepoint)
        }
        *///?}
        flush()
    }
}

@Composable
private fun LastPlayedStrip(lobby: LobbyShell, friends: List<OnlineFriend>) {
    val servers = lobby.servers
    val hero = servers.hero ?: return
    val data = hero.data
    val revision = servers.revision
    val played = servers.recent.isNotEmpty()
    val (interaction, hovered) = rememberSocialHover()
    LaunchedEffect(data) { servers.ping(data) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .cardFrame(lobby.isSelected(data), hovered)
            .serverWash(data, 0.35f)
            .contextMenu { serverMenuItems(lobby, hero) { lobby.deleting = it } }
            .hoverable(interaction)
            .clickableWithSound { lobby.select(data) }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ServerIcon(data, revision, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (played) "Last played" else "Top of your list", TagTone.Accent)
                Label(data.name, 15.sp, Modifier.weight(1f, fill = false), weight = FontWeight.Medium)
            }
            Label(serverMeta(data), 12.sp, color = if (LobbyServers.failed(data)) SocialDangerText else MutedText)
        }
        if (friends.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                friends.take(HERO_FACES).forEach { Head(it.seed, 24.dp, realSkin = it.realSkin) }
                Label("${friends.size} friend${if (friends.size == 1) "" else "s"} online", 12.sp, color = MutedText)
            }
        }
        SocialButton(if (played) "Rejoin" else "Join", filled = true) { lobby.join(data) }
    }
}

@Composable
private fun DirectConnectDialog(lobby: LobbyShell, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val probe = remember { ServerStatusProbe(Minecraft.getInstance(), null) }
    val target = address.trim()
    val valid = ServerAddress.isValidAddress(target)
    fun connect() {
        if (lobby.directConnect(address)) onDismiss() else invalid = true
    }
    DisposableEffect(probe) { onDispose(probe::close) }
    LaunchedEffect(probe) {
        while (isActive) {
            probe.tick()
            delay(PROBE_TICK_MILLIS)
        }
    }
    LaunchedEffect(probe, target) {
        probe.reset(target)
        if (!valid) return@LaunchedEffect
        delay(DIRECT_PING_DEBOUNCE_MILLIS)
        probe.ping()
    }
    SocialModalScrim(onDismiss) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        ModalPanel(width = 440.dp) {
            PanelTitle("Direct connect")
            SocialTextField(
                value = address,
                onValueChange = {
                    address = it
                    invalid = false
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Server address",
                leadingIcon = SOCIAL_ASSETS + "globe-01.svg",
                maxLength = 255,
                invalid = invalid,
                focusRequester = focus,
                onSubmit = ::connect,
            )
            if (invalid) Label("Invalid address", 12.sp, color = SocialDangerText)
            ServerPreviewCard(target.ifEmpty { "Server address" }, valid, probe, Modifier.fillMaxWidth().height(DirectPreviewHeight))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SocialButton("Cancel", onClick = onDismiss)
                SocialButton("Connect", filled = true, enabled = address.isNotBlank(), onClick = ::connect)
            }
        }
    }
}

@Composable
private fun DeleteServerDialog(entry: ServerEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    SocialModalScrim(onDismiss) {
        ModalPanel(width = 420.dp) {
            PanelTitle("Delete ${entry.data.name}?")
            SocialText("It will be removed from your server list.", 14.sp, color = MutedText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                SocialButton("Cancel", onClick = onDismiss)
                DangerButton("Delete", onClick = onConfirm)
            }
        }
    }
}
