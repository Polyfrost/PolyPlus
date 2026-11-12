@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.lobby.LobbyMotd*/
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ContextMenuHost
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtons
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.gui.ASSETS
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.RasterImage
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.legibleOn
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialAvailability
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberAvailability
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.serveredit.DriveServerProbe
import org.polyfrost.polyplus.client.gui.serveredit.ServerPreviewCard
import org.polyfrost.polyplus.client.gui.serveredit.ServerStatusProbe
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.Locale
import java.util.UUID

private enum class ServerFilter(val label: String) {
    All("All"),
    Recent("Recent"),
}

private class OnlineFriend(val name: String, val seed: UUID, val realSkin: Boolean, val detail: String)

private const val HERO_FACES = 3
private const val SEPARATOR = " · "
private const val GRID_ICON = "dots-grid"
private const val LIST_ICON = "align-left"
private val DirectPreviewHeight = 220.dp

@Composable
internal fun BoxScope.MultiplayerPage(lobby: LobbyShell) {
    ContextMenuHost {
        val availability = rememberAvailability()
        val friends = rememberOnlineFriends(availability)
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
            availability,
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
private fun rememberOnlineFriends(availability: SocialAvailability): List<OnlineFriend> {
    val friends by FriendsRepository.friends.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val online = friends.filter { it.online }
    val allowed = availability != SocialAvailability.Declined
    LaunchedEffect(allowed) { if (allowed && PrivacyConsent.allowsOnlineServices()) FriendsRepository.refreshAll() }
    LaunchedEffect(online) { PlayerNamesRepository.resolve(online.map { it.player }) }
    return online.map { OnlineFriend(names[it.player] ?: PlayerNamesRepository.nameOr(it.player), seedOf(it.player), true, "Online") }
}

@Composable
private fun FriendsPanel(friends: List<OnlineFriend>, availability: SocialAvailability, modifier: Modifier) {
    var consenting by remember { mutableStateOf(false) }
    if (availability == SocialAvailability.Declined && consenting) {
        ConsentStep("Seeing which friends are online needs online services.", modifier, { consenting = false }) { consenting = false }
        return
    }
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelTitle("Friends")
        val body = Modifier.fillMaxWidth().weight(1f)
        when (availability) {
            SocialAvailability.Declined -> {
                EmptyState("Online services are off", body, "Turn them on to see which friends are online.") {
                    TextButton("Turn on", Modifier.height(38.dp), ButtonStyle.Primary) { consenting = true }
                }
                return@Column
            }
            SocialAvailability.SignedOut -> {
                EmptyState("Not signed in", body, "Friends need a signed-in Minecraft account and a connection to OneClient.") {
                    TextButton("Try again", themedIcon(LobbyIcons.REFRESH), Modifier.height(38.dp), ButtonStyle.Primary, true) {
                        PolyConnection.reconnect()
                        FriendsRepository.refreshAll()
                    }
                }
                return@Column
            }
            SocialAvailability.Connecting -> if (friends.isEmpty()) {
                EmptyState("Connecting", body)
                return@Column
            }
            SocialAvailability.Ready -> Unit
        }
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
                    PlayerHead(friend.seed, 38.dp, Modifier, headShape(38.dp))
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageGrid.Gap)) {
        PageHeader(
            "Servers",
            query,
            { query = it },
            "Search servers",
            onBack,
            filters = { TabRow(ServerFilter.entries.map { TabItem(it, it.label) }, filter, Modifier) { filter = it } },
        ) {
            LayoutToggle(listView) {
                listView = it
                PolyPlusLobbyConfig.serverListView = it
                PolyPlusLobbyConfig.save()
            }
            TextButton("Add server", Modifier.height(38.dp), lobby::addServer)
            TextButton("Direct connect", Modifier.height(38.dp), onDirect)
            if (PolyPlusMainMenuConfig.realmsSupported() && !PolyPlusMainMenuConfig.hideMainMenuRealms) {
                TextButton("Realms", Modifier.height(38.dp), lobby::openRealms)
            }
            IconButton(themedIcon(LobbyIcons.REFRESH), "Refresh", Modifier, servers::refresh)
        }
        ServerLastPlayedStrip(lobby, friends)
        if (shown.isEmpty()) {
            val body = Modifier.fillMaxWidth().weight(1f).panel(16.dp)
            when {
                query.isNotBlank() -> NoMatchState("servers", filter.takeIf { it != ServerFilter.All }?.label?.lowercase(Locale.ROOT), query, body)
                filter == ServerFilter.Recent -> EmptyState("No recent servers", body, "Servers you join show up here.")
                else -> EmptyState("No saved servers", body, "Add a server, or use direct connect.") {
                    TextButton("Add server", themedIcon(LobbyIcons.PLUS), Modifier.height(38.dp), ButtonStyle.Primary, true, lobby::addServer)
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
    IconButton(
        themedIcon(icon),
        tooltip,
        null,
        Modifier,
        if (selected) theme.componentBackground else null,
        if (selected) SocialTextPrimary else MutedText,
        TooltipPlacement.BelowStart,
        onClick,
    )
}

@Composable
private fun ServerGrid(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val rowHeight = PageGrid.rowHeight(maxHeight)
        val gridState = rememberLazyGridState()
        val viewport = remember(gridState) { GridReorderViewport(gridState) }
        val reorder = rememberServerReorder(lobby, shown, canReorder, viewport)
        Box(Modifier.fillMaxSize().serverReorderContainer(reorder)) {
            LazyVerticalGrid(
                GridCells.Fixed(PageGrid.COLUMNS),
                Modifier.fillMaxSize(),
                gridState,
                horizontalArrangement = Arrangement.spacedBy(PageGrid.Gap),
                verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            ) {
                items(reorder.entries, key = { it.key }) { entry ->
                    ServerCard(lobby, entry, canReorder, onDelete, Modifier.height(rowHeight).animateItem(placementSpec = Motion.itemPlacementSpec()).serverReorderItem(reorder, entry.key))
                }
            }
            reorder.overlayEntry?.let { entry -> ServerCard(lobby, entry, canReorder, onDelete, Modifier.serverReorderOverlay(reorder)) }
        }
    }
}

@Composable
internal fun ServerBanner(data: ServerData, revision: Int, height: Dp) {
    @Suppress("UNUSED_EXPRESSION") revision
    ServerRevisions.of(data)
    ServerBackdrop(data, Modifier.fillMaxWidth().height(height))
}

@Composable
internal fun ServerIcon(data: ServerData, revision: Int, size: Dp) {
    @Suppress("UNUSED_EXPRESSION") revision
    ServerRevisions.of(data)
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
    val onClick = rememberCardClick({ lobby.join(data) }) { lobby.select(data) }
    LaunchedEffect(data) { servers.ping(data) }
    Box(
        modifier
            .cardFrame(selected, hovered)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, onDelete = onDelete) }
            .hoverable(interaction)
            .clickableWithSound(data.name, selected = selected.takeIf { it }, shape = ppShape(16.dp), onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize()) {
            ServerBanner(data, revision, banner)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = overhang + 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ServerText(entry, revision, 16)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton("Join", Modifier.height(38.dp), ButtonStyle.Primary) { lobby.join(data) }
                    ServerMoreMenu(lobby, entry, canReorder, onDelete, vertical = false)
                }
            }
        }
        Box(Modifier.offset(x = 12.dp, y = banner - icon + overhang)) { ServerIcon(data, revision, icon) }
        if (hovered) CardActions(lobby, entry, canReorder, onDelete, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

internal class ServerAction(val item: MenuAction, val rotation: Float? = null)

internal fun serverActions(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: ((ServerEntry) -> Unit)?, vertical: Boolean = false): List<ServerAction> {
    val servers = lobby.servers
    val save = ServerAction(MenuAction("Save to list", LobbyIcons.PLUS) { servers.saveUnsaved(entry) })
    return when (entry.kind) {
        ServerKind.SAVED -> listOfNotNull(
            ServerAction(MenuAction(if (vertical) "Move up" else "Move left", enabled = entry.savedIndex > 0) { servers.move(entry, -1) }, if (vertical) 0f else -90f).takeIf { canReorder },
            ServerAction(MenuAction(if (vertical) "Move down" else "Move right", enabled = entry.savedIndex < servers.saved.lastIndex) { servers.move(entry, 1) }, if (vertical) 180f else 90f).takeIf { canReorder },
            ServerAction(MenuAction("Edit", SOCIAL_ASSETS + "edit-02.svg") { lobby.editServer(entry) }),
            onDelete?.let { delete -> ServerAction(MenuAction("Delete", LobbyIcons.TRASH, danger = true) { delete(entry) }) },
        )
        ServerKind.UNSAVED -> listOf(save)
        ServerKind.FEATURED, ServerKind.SPONSORED -> {
            val campaign = entry.featured?.featured?.takeIf { it.dismissibleInServerList }
            listOfNotNull(save, campaign?.let { ServerAction(MenuAction("Dismiss", LobbyIcons.CLOSE) { FeaturedServers.dismissMultiplayer(it.campaignId) }) })
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
): List<MenuAction> =
    listOf(MenuAction("Join", SOCIAL_ASSETS + "log-in-04.svg") { lobby.join(entry.data) }) +
        serverActions(lobby, entry, canReorder, onDelete, vertical).map { it.item }

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
                icon != null -> IconButton(themedIcon(icon), item.label, null, small, fill, SocialTextPrimary, TooltipPlacement.BelowStart, item.onClick)
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
    ServerRevisions.of(entry.data)
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
internal fun MotdText(entry: ServerEntry, revision: Int, modifier: Modifier = Modifier, reserve: Boolean = false) {
    @Suppress("UNUSED_EXPRESSION") revision
    ServerRevisions.of(entry.data)
    val data = entry.data
    val hidden = entry.kind == ServerKind.LAN || LobbyServers.failed(data) || data.state() == ServerData.State.PINGING
    MotdLines(if (hidden) null else data.motd, modifier, reserve)
}

@Composable
internal fun MotdLines(motd: Component?, modifier: Modifier = Modifier, reserve: Boolean = false) {
    val theme = OneConfigTheme.current
    val muted = MutedText
    val parsed = remember(motd, muted, theme.textColor, theme.popupBackground) { motdLines(motd, muted, theme.textColor, theme.popupBackground) }
    val lines = if (reserve) parsed + List(VANILLA_MOTD_LINES - parsed.size) { AnnotatedString("") } else parsed
    if (lines.isEmpty()) return
    val style = TextStyle(
        color = muted,
        fontSize = MOTD_SIZE,
        fontFamily = theme.fontFamily,
        lineHeight = MOTD_LINE_HEIGHT,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widest = remember(lines, style) { lines.maxOf { measurer.measure(it, style, softWrap = false, maxLines = 1).size.width } }
        val available = constraints.maxWidth
        val fitted = if (widest <= available) style else style.copy(fontSize = style.fontSize * (available.toFloat() / widest))
        Column {
            lines.forEach { BasicText(it, style = fitted, softWrap = false, maxLines = 1, overflow = TextOverflow.Clip) }
        }
    }
}

private class MotdLineBuilder(private val fallback: Color, private val ink: Color, private val surface: Color) {
    private val builder = AnnotatedString.Builder()
    private val run = StringBuilder()
    private var runStyle: SpanStyle? = null

    fun add(color: Int?, bold: Boolean, italic: Boolean, underlined: Boolean, struck: Boolean, codepoint: Int) {
        val decorations = listOfNotNull(TextDecoration.Underline.takeIf { underlined }, TextDecoration.LineThrough.takeIf { struck })
        val next = SpanStyle(
            color = legibleOn(color?.let { Color(0xFF000000 or it.toLong()) } ?: fallback, ink, surface),
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        )
        if (next != runStyle) flush()
        runStyle = next
        run.appendCodePoint(codepoint)
    }

    private fun flush() {
        val style = runStyle ?: return
        if (run.isEmpty()) return
        builder.withStyle(style) { append(run.toString()) }
        run.clear()
    }

    fun build(): AnnotatedString {
        flush()
        return builder.toAnnotatedString()
    }
}

internal fun motdLines(component: Component?, fallback: Color, ink: Color, surface: Color): List<AnnotatedString> {
    component ?: return emptyList()
    //? if > 1.8.9 {
    return Minecraft.getInstance().font.split(component, VANILLA_MOTD_WIDTH).take(VANILLA_MOTD_LINES).map { line ->
        val builder = MotdLineBuilder(fallback, ink, surface)
        line.accept { _, style, codepoint ->
            builder.add(style.color?.value, style.isBold, style.isItalic, style.isUnderlined, style.isStrikethrough, codepoint)
            true
        }
        builder.build()
    }
    //?} else {
    /*return Minecraft.getInstance().textRenderer.split(component.string, VANILLA_MOTD_WIDTH).take(VANILLA_MOTD_LINES).map { line ->
        val builder = MotdLineBuilder(fallback, ink, surface)
        LobbyMotd.forEach(line) { value, codepoint -> builder.add(value, false, false, false, false, codepoint) }
        builder.build()
    }
    *///?}
}

private const val VANILLA_MOTD_WIDTH = 271
private const val VANILLA_MOTD_LINES = 2
private val MOTD_SIZE = 12.sp
private val MOTD_LINE_HEIGHT = 17.sp

@Composable
private fun ServerLastPlayedStrip(lobby: LobbyShell, friends: List<OnlineFriend>) {
    val servers = lobby.servers
    val hero = servers.hero ?: return
    val data = hero.data
    val revision = servers.revision
    ServerRevisions.of(data)
    val played = servers.recent.isNotEmpty()
    LaunchedEffect(data) { servers.ping(data) }
    LastPlayedStrip(
        tag = if (played) "Last played" else "Top of your list",
        name = data.name,
        selected = lobby.isSelected(data),
        onClick = rememberCardClick({ lobby.join(data) }) { lobby.select(data) },
        menu = { serverMenuItems(lobby, hero) { lobby.deleting = it } },
        actionLabel = if (played) "Rejoin" else "Join",
        onAction = { lobby.join(data) },
        wash = Modifier.serverWash(data, 0.35f),
        thumb = { ServerIcon(data, revision, 40.dp) },
        trailing = {
            if (friends.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    friends.take(HERO_FACES).forEach { PlayerHead(it.seed, 24.dp, Modifier, headShape(24.dp)) }
                    Label("${friends.size} friend${if (friends.size == 1) "" else "s"} online", 12.sp, color = MutedText)
                }
            }
        },
    ) {
        Label(serverMeta(data), 12.sp, color = if (LobbyServers.failed(data)) SocialDangerText else MutedText)
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
    DriveServerProbe(probe, target, valid)
    ModalDialog(onDismiss, 440.dp, true) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        DialogTitle("Direct connect")
        TextField(
            value = address,
            onValueChange = {
                address = it
                invalid = false
            },
            modifier = Modifier.fillMaxWidth().height(38.dp),
            placeholder = "Server address",
            leadingIcon = themedIcon(SOCIAL_ASSETS + "globe-01.svg"),
            maxLength = 255,
            invalid = invalid,
            enabled = true,
            singleLine = true,
            framed = true,
            fontSize = 14.sp,
            label = null,
            masked = false,
            focusRequester = focus,
            interactionSource = null,
            trailing = null,
            onSubmit = ::connect,
        )
        if (invalid) Label("Invalid address", 12.sp, color = SocialDangerText)
        ServerPreviewCard(target.ifEmpty { "Server address" }, valid, probe, Modifier.fillMaxWidth().height(DirectPreviewHeight))
        DialogButtons {
            TextButton("Cancel", Modifier.height(38.dp), onDismiss)
            TextButton("Connect", null, Modifier.height(38.dp), ButtonStyle.Primary, address.isNotBlank(), ::connect)
        }
    }
}

@Composable
private fun DeleteServerDialog(entry: ServerEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Delete ${entry.data.name}?",
        text = "It will be removed from your server list.",
        confirmLabel = "Delete",
        dismissLabel = "Cancel",
        icon = themedIcon(LobbyIcons.TRASH),
        danger = true,
        confirmEnabled = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
