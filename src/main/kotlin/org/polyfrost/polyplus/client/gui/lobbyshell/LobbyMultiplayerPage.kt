@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.polyplus.client.gui.KitDangerColor
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.reorderAutoScroll
import org.polyfrost.oneconfig.api.ui.v1.components.reorderContainer
import org.polyfrost.oneconfig.api.ui.v1.components.reorderOverlay
import org.polyfrost.oneconfig.api.ui.v1.components.reorderableItem
import org.polyfrost.oneconfig.api.ui.v1.theme.legibleOn
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import org.polyfrost.oneconfig.api.ui.v1.components.MenuHeading
import org.polyfrost.oneconfig.api.ui.v1.components.MenuRadioGroup
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import androidx.compose.ui.focus.focusProperties
import org.polyfrost.polyplus.client.featured.ServerFolders
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.DialogButtons
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.ASSETS
import org.polyfrost.polyplus.client.gui.RasterImage
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialAvailability
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberAvailability
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.gui.serveredit.DriveServerProbe
import org.polyfrost.polyplus.client.gui.serveredit.ServerPreviewCard
import org.polyfrost.polyplus.client.gui.serveredit.ServerStatusProbe
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.Locale
import java.util.UUID
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle

private enum class ServerFilter(val label: String) {
    All("All"),
    Recent("Recent"),
}

private class OnlineFriend(val name: String, val seed: UUID, val realSkin: Boolean, val detail: String)

private const val SEPARATOR = " · "
private const val GRID_ICON = "dots-grid"
private const val LIST_ICON = "align-left"
private val DirectPreviewHeight = 220.dp

@Composable
internal fun BoxScope.MultiplayerPage(lobby: LobbyShell) {
    val availability = rememberAvailability()
    val friends = rememberOnlineFriends(availability)
    LaunchedEffect(Unit) { LobbyRealms.refresh() }
    PageScrim()
    ServerBrowser(
        lobby,
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
    FolderPromptDialog(lobby.servers)
}

@Composable
private fun FolderPromptDialog(servers: LobbyServers) {
    val close = { servers.folderPrompt = null }
    when (val prompt = servers.folderPrompt) {
        is MoveFolderPrompt -> FolderNameDialog(
            title = "Move to folder",
            initial = ServerFolders.folderOf(prompt.entry.data.ip).orEmpty(),
            confirmLabel = "Move",
            onDismiss = close,
            suggestions = ServerFolders.names(),
        ) { servers.setFolder(prompt.entry, it) }
        is RenameFolderPrompt -> FolderNameDialog("Rename folder", prompt.folder, "Rename", close) { servers.renameFolder(prompt.folder, it) }
        is DeleteFolderPrompt -> ConfirmDialog(
            title = "Delete ${prompt.folder}?",
            text = "The servers in it stay in your list.",
            confirmLabel = "Delete folder",
            dismissLabel = "Cancel",
            icon = LobbyIcons.TRASH,
            danger = true,
            confirmEnabled = true,
            onConfirm = {
                servers.deleteFolder(prompt.folder)
                close()
            },
            onDismiss = close,
        )
        null -> Unit
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    suggestions: List<String> = emptyList(),
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    fun confirm() {
        if (name.isBlank()) return
        onConfirm(name.trim())
        onDismiss()
    }
    ModalDialog(onDismiss = onDismiss, width = 400.dp, dismissOnScrim = true) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        DialogTitle(title)
        TextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Folder name",
            leadingIcon = FOLDER_ICON,
            maxLength = FOLDER_NAME_MAX,
            size = ControlSize.Large,
            fontSize = 14.sp,
            focusRequester = focus,
            onSubmit = ::confirm,
            trailing = if (suggestions.isEmpty()) null else {
                {
                    AnchoredPopupButton(
                        icon = "down",
                        contentDescription = "Pick a folder",
                        modifier = Modifier.focusProperties { canFocus = false },
                        placement = Placement.BelowEnd,
                    ) { closeMenu ->
                        MenuList(suggestions.map { folder -> MenuAction(folder, { name = folder }, checked = folder.equals(name.trim(), ignoreCase = true)) }, closeMenu)
                    }
                }
            },
        )
        DialogButtons {
            TextButton(text = "Cancel", onClick = onDismiss, size = ControlSize.Large)
            TextButton(text = confirmLabel, onClick = ::confirm, size = ControlSize.Large, style = ControlStyle.Primary, enabled = name.isNotBlank())
        }
    }
}

internal const val FOLDER_ICON = "folder"
internal const val FOLDER_NAME_MAX = 32

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
                    TextButton(text = "Turn on", onClick = { consenting = true }, size = ControlSize.Large, style = ControlStyle.Primary)
                }
                return@Column
            }
            SocialAvailability.SignedOut -> {
                EmptyState("Not signed in", body, "Friends need a signed-in Minecraft account and a connection to OneClient.") {
                    TextButton(
                        text = "Try again",
                        icon = LobbyIcons.REFRESH,
                        onClick = {
                            PolyConnection.reconnect()
                            FriendsRepository.refreshAll()
                        },
                        style = ControlStyle.Primary,
                        size = ControlSize.Large,
                    )
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
                    PlayerHead(playerId = friend.seed, size = 38.dp, modifier = Modifier, shape = headShape(38.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(friend.name, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        Text(friend.detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerBrowser(lobby: LobbyShell, onBack: () -> Unit, onDirect: () -> Unit, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val servers = lobby.servers
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ServerFilter.All) }
    var listView by remember { mutableStateOf(PolyPlusLobbyConfig.serverListView) }
    val hideAddress = Minecraft.getInstance().options.hideServerAddress
    val base = when (filter) {
        ServerFilter.All -> servers.featured + servers.saved + servers.lan + servers.sponsored
        ServerFilter.Recent -> servers.recent
    }
    val shown = base.filter { query.isBlank() || it.data.shownName.contains(query.trim(), true) || (!hideAddress && it.data.ip.contains(query.trim(), true)) }
    val canReorder = filter == ServerFilter.All && query.isBlank()
    @Suppress("UNUSED_EXPRESSION") servers.folderRevision
    val layout = if (canReorder) servers.layout(shown) else null
    val needle = query.trim()
    val realms = if (filter == ServerFilter.All && LobbyRealms.shown()) {
        LobbyRealms.realms.filter { needle.isEmpty() || it.name.contains(needle, true) || it.owner.contains(needle, true) }
    } else {
        null
    }
    val realmsShown = realms != null && (needle.isEmpty() || realms.isNotEmpty())
    val sectioned = realmsShown || layout?.slots?.any { it is FolderSlot } == true
    val slots = layout?.slots?.let { if (sectioned) it.withGroupHeader(SERVERS_GROUP) else it }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PageGrid.Gap)) {
        PageHeader(
            "Servers",
            query,
            { query = it },
            "Search servers",
            onBack,
            filters = { UnderlineTabs(ServerFilter.entries.map { TabItem(it, it.label) }, filter) { filter = it } },
        ) {
            TextButton(text = "Add server", icon = LobbyIcons.PLUS, onClick = lobby::addServer, size = ControlSize.Large, style = ControlStyle.Primary)
            if (filter == ServerFilter.All) SortMenu(servers)
            ServersMenu(lobby, listView, onDirect, remember(lobby) { lobby.serverListModButtons() }) {
                listView = it
                PolyPlusLobbyConfig.serverListView = it
                PolyPlusLobbyConfig.save()
            }
        }
        if (realms != null && realmsShown) RealmsSection(lobby, realms, listView, Modifier.fillMaxWidth().padding(bottom = GroupGap / 2))
        if (shown.isEmpty()) {
            val body = Modifier.fillMaxWidth().weight(1f).panel(16.dp)
            when {
                query.isNotBlank() -> NoMatchState("servers", filter.takeIf { it != ServerFilter.All }?.label?.lowercase(Locale.ROOT), query, body)
                filter == ServerFilter.Recent -> EmptyState("No recent servers", body, "Servers you join show up here.")
                else -> EmptyState("No saved servers", body, "Add a server, or use direct connect.") {
                    TextButton(text = "Add server", icon = LobbyIcons.PLUS, onClick = lobby::addServer, size = ControlSize.Large, style = ControlStyle.Primary)
                }
            }
        } else if (slots != null && ServerFolders.sort() == ServerFolders.Sort.CUSTOM) {
            DraggableServerSlots(
                servers,
                slots,
                grid = !listView,
                minCell = SERVER_CARD_MIN_WIDTH,
                gap = if (listView) SERVER_ROW_GAP else PageGrid.Gap,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { slot, placed, handle ->
                if (listView) ServerRow(lobby, slot.entry, canReorder, onDelete, placed, handle)
                else ServerCard(lobby, slot.entry, canReorder, onDelete, placed, handle)
            }
        } else if (layout != null && slots != null && (layout.grouped || sectioned)) {
            if (listView) GroupedServerList(lobby, slots, canReorder, onDelete, Modifier.fillMaxWidth().weight(1f))
            else GroupedServerGrid(lobby, slots, canReorder, onDelete, Modifier.fillMaxWidth().weight(1f))
        } else {
            if (realmsShown) GroupHeader(GroupSlot(SERVERS_GROUP, shown.size))
            if (listView) ServerList(lobby, shown, canReorder, onDelete, Modifier.fillMaxWidth().weight(1f))
            else ServerGrid(lobby, shown, canReorder, onDelete, Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun SortMenu(servers: LobbyServers) {
    @Suppress("UNUSED_EXPRESSION") servers.folderRevision
    val sort = ServerFolders.sort()
    NavPopupButton(LobbyIcons.FILTER, "Sort: ${sortLabel(sort)}", gap = MENU_GAP) { close ->
        LobbyUserTheme {
            val entries = listOf(
                MenuHeading("Sort saved servers"),
                MenuRadioGroup(ServerFolders.Sort.entries, sort, ::sortLabel, servers::setSort),
            )
            MenuList(entries, close, Modifier.width(SORT_MENU_WIDTH.dp), MenuItemSize.Regular)
        }
    }
}

private fun sortLabel(sort: ServerFolders.Sort): String = when (sort) {
    ServerFolders.Sort.CUSTOM -> "Custom"
    ServerFolders.Sort.A_Z -> "A–Z"
    ServerFolders.Sort.Z_A -> "Z–A"
}

private const val SORT_MENU_WIDTH = 220

@Composable
private fun ServersMenu(lobby: LobbyShell, listView: Boolean, onDirect: () -> Unit, modButtons: List<ServerListModButton>, onLayout: (Boolean) -> Unit) {
    val realms = PolyPlusMainMenuConfig.realmsSupported() && !PolyPlusMainMenuConfig.hideMainMenuRealms
    NavPopupButton(SOCIAL_ASSETS + "dots-vertical.svg", "More", gap = MENU_GAP) { close ->
        LobbyUserTheme {
            val entries = buildList<MenuEntry> {
                add(MenuAction("Direct connect", onDirect, icon = SOCIAL_ASSETS + "globe-01.svg", size = MenuItemSize.Regular))
                if (realms) add(MenuAction("Realms", lobby::openRealms, icon = REALM_ICON, badge = LobbyRealms.invites.takeIf { it > 0 }?.toString(), size = MenuItemSize.Regular))
                add(
                    MenuAction(
                        "Refresh",
                        {
                            lobby.servers.refresh()
                            LobbyRealms.refresh(force = true)
                        },
                        icon = LobbyIcons.REFRESH,
                        size = MenuItemSize.Regular,
                    ),
                )
                if (listView) add(MenuAction("Show as grid", { onLayout(false) }, icon = GRID_ICON, size = MenuItemSize.Regular))
                else add(MenuAction("Show as list", { onLayout(true) }, icon = LIST_ICON, size = MenuItemSize.Regular))
                modButtons.forEach { button ->
                    add(MenuAction(button.label, button.press, detail = button.mod, enabled = button.enabled, size = MenuItemSize.Regular))
                }
            }
            MenuList(entries, close, Modifier.width(MENU_WIDTH.dp), MenuItemSize.Regular)
        }
    }
}

private const val MENU_WIDTH = 260
private val MENU_GAP = 8.dp

@Composable
private fun ServerGrid(lobby: LobbyShell, shown: List<ServerEntry>, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val gridState = rememberLazyGridState()
    val reorder = rememberServerReorder(lobby, shown, canReorder, gridState)
    ScrollContainer(gridState, modifier, scrollbar = false) {
        Box(Modifier.fillMaxSize().reorderContainer(reorder.state).reorderAutoScroll(reorder.state)) {
            LazyVerticalGrid(
                GridCells.Adaptive(SERVER_CARD_MIN_WIDTH),
                Modifier.fillMaxSize(),
                gridState,
                horizontalArrangement = Arrangement.spacedBy(PageGrid.Gap),
                verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            ) {
                itemsIndexed(reorder.entries, key = { _, entry -> entry.key }) { index, entry ->
                    val drag = Modifier.reorderableItem(reorder.state, entry.key, index, canReorder && entry.kind == ServerKind.SAVED)
                    ServerCard(lobby, entry, canReorder, onDelete, Modifier.animateItem(placementSpec = Motion.itemPlacementSpec()).then(drag))
                }
            }
            reorder.overlayEntry?.let { entry -> ServerCard(lobby, entry, canReorder, onDelete, Modifier.reorderOverlay(reorder.state)) }
        }
    }
}

@Composable
private fun GroupedServerGrid(lobby: LobbyShell, slots: List<ServerSlot>, canMove: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val gridState = rememberLazyGridState()
    ScrollContainer(gridState, modifier, scrollbar = false) {
        LazyVerticalGrid(
            GridCells.Adaptive(SERVER_CARD_MIN_WIDTH),
            Modifier.fillMaxSize(),
            gridState,
            horizontalArrangement = Arrangement.spacedBy(PageGrid.Gap),
            verticalArrangement = Arrangement.spacedBy(PageGrid.Gap),
        ) {
            itemsIndexed(slots, key = { _, slot -> slot.key }, span = { _, slot -> if (slot is EntrySlot) GridItemSpan(1) else GridItemSpan(maxLineSpan) }) { index, slot ->
                val placed = Modifier.animateItem(placementSpec = Motion.itemPlacementSpec()).groupGap(slot, index)
                when (slot) {
                    is EntrySlot -> ServerCard(lobby, slot.entry, canMove, onDelete, placed)
                    is FolderSlot -> FolderHeader(lobby.servers, slot, placed)
                    is SectionSlot -> SectionLabel(slot.label, placed)
                    is GroupSlot -> GroupHeader(slot, placed)
                }
            }
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
    val shape = OneConfigTheme.shape(if (size > 48.dp) 12.dp else 8.dp)
    val modifier = Modifier.size(size).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape)
    if (favicon != null) {
        Image(favicon, null, modifier, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
    } else {
        RasterImage(ASSETS + if (data.ip.contains("hypixel", true)) "hypixel.png" else "server.png", modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun ServerCard(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier, drag: Modifier = Modifier) {
    val servers = lobby.servers
    val data = entry.data
    val revision = servers.revision
    val hover = rememberHoverState()
    val selected = lobby.isSelected(data)
    val onClick = rememberCardClick({ lobby.join(data) }) { lobby.select(data) }
    LaunchedEffect(data) { servers.ping(data) }
    Box(
        modifier
            .cardFrame(selected, hover.hovered)
            .contextMenu { serverMenuItems(lobby, entry, canReorder, onDelete = onDelete, folders = true) }
            .hoverable(hover.interactionSource)
            .oneConfigClickable(data.shownName, onClick, selected = selected.takeIf { it }, shape = OneConfigTheme.shape(16.dp))
            .then(drag),
    ) {
        Row(
            Modifier.fillMaxWidth().serverWash(data, SERVER_CARD_WASH).padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ServerIcon(data, revision, SERVER_CARD_ICON)
            Box(Modifier.weight(1f)) { ServerText(entry, revision, servers.isPinned(data)) }
            ServerMoreMenu(lobby, entry, canReorder, onDelete, vertical = false)
        }
    }
}

@Composable
private fun RealmsSection(lobby: LobbyShell, realms: List<RealmEntry>, listView: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (listView) REALM_ROW_GAP else PageGrid.Gap)) {
        RealmsHeader(lobby, LobbyRealms.realms.size)
        if (LobbyRealms.collapsed || realms.isEmpty()) return@Column
        Column(
            Modifier.fillMaxWidth().heightIn(max = REALMS_MAX_HEIGHT).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (listView) REALM_ROW_GAP else PageGrid.Gap),
        ) {
            if (listView) {
                realms.forEach { realm -> key(realm.key) { RealmRow(lobby, realm) } }
            } else {
                RealmCardGrid(lobby, realms)
            }
        }
    }
}

@Composable
private fun RealmCardGrid(lobby: LobbyShell, realms: List<RealmEntry>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = PageGrid.Gap
        val columns = ((maxWidth + gap) / (SERVER_CARD_MIN_WIDTH + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            realms.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { realm -> key(realm.key) { RealmCard(lobby, realm, Modifier.weight(1f)) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RealmCard(lobby: LobbyShell, realm: RealmEntry, modifier: Modifier) {
    val hover = rememberHoverState()
    val selected = lobby.isSelected(realm)
    val onClick = rememberCardClick({ lobby.playRealm(realm) }) { lobby.select(realm) }
    Box(
        modifier
            .cardFrame(selected, hover.hovered)
            .contextMenu { realmMenuItems(lobby, realm) }
            .hoverable(hover.interactionSource)
            .oneConfigClickable(realm.name, onClick, selected = selected.takeIf { it }, shape = OneConfigTheme.shape(16.dp)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RealmHead(realm, SERVER_CARD_ICON)
            Box(Modifier.weight(1f)) { RealmText(realm, lobby.servers.isRealmPinned(realm.id)) }
            RealmMoreMenu(lobby, realm)
        }
    }
}

@Composable
private fun RealmText(realm: RealmEntry, pinned: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(realm.name, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            if (pinned) PinnedMark()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RealmStatusBadge(realm)
            val warning = realm.warning
            Text(
                warning ?: realm.detail,
                color = if (warning != null) SocialDangerText else OneConfigTheme.current.mutedText,
                style = OneConfigTheme.current.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        Box(Modifier.fillMaxWidth().height(REALM_MOTD_HEIGHT)) {
            Text(realm.motd, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val REALMS_MAX_HEIGHT = 236.dp
private val REALM_ROW_GAP = 6.dp
private val REALM_MOTD_HEIGHT = 34.dp

private val SERVER_CARD_MIN_WIDTH = 320.dp
private val SERVER_ROW_GAP = 6.dp
private val SERVER_CARD_ICON = 56.dp
private const val SERVER_CARD_WASH = 0.35f

internal class ServerAction(val item: MenuAction, val rotation: Float? = null)

internal fun serverActions(
    lobby: LobbyShell,
    entry: ServerEntry,
    canReorder: Boolean,
    onDelete: ((ServerEntry) -> Unit)?,
    vertical: Boolean = false,
    folders: Boolean = false,
): List<ServerAction> {
    val servers = lobby.servers
    val canMove = canReorder && entry.kind == ServerKind.SAVED && ServerFolders.sort() == ServerFolders.Sort.CUSTOM
    val siblings = if (canMove) servers.folderSiblings(entry) else emptyList()
    val position = siblings.indexOfFirst { it.data === entry.data }
    val save = ServerAction(MenuAction("Save to list", { servers.saveUnsaved(entry) }, icon = LobbyIcons.PLUS))
    val pinned = servers.isPinned(entry.data)
    val pin = ServerAction(
        MenuAction(
            if (pinned) "Unpin from home" else "Pin to home",
            { servers.setPinned(entry.data, !pinned) },
            icon = if (pinned) PINNED_ICON else PIN_ICON,
            enabled = pinned || servers.canPin(),
        ),
    )
    return when (entry.kind) {
        ServerKind.SAVED -> listOfNotNull(
            ServerAction(MenuAction(if (vertical) "Move up" else "Move left", { servers.moveInFolder(entry, -1) }, enabled = position > 0), if (vertical) 0f else -90f).takeIf { canMove },
            ServerAction(MenuAction(if (vertical) "Move down" else "Move right", { servers.moveInFolder(entry, 1) }, enabled = position in 0 until siblings.lastIndex), if (vertical) 180f else 90f).takeIf { canMove },
            pin,
            ServerAction(MenuAction("Move to folder…", { servers.folderPrompt = MoveFolderPrompt(entry) }, icon = FOLDER_ICON)).takeIf { folders },
            ServerAction(MenuAction("Remove from folder", { servers.setFolder(entry, "") }, icon = LobbyIcons.CLOSE)).takeIf { folders && ServerFolders.folderOf(entry.data.ip) != null },
            ServerAction(MenuAction("Edit", { lobby.editServer(entry) }, icon = SOCIAL_ASSETS + "edit-02.svg")),
            onDelete?.let { delete -> ServerAction(MenuAction("Delete", { delete(entry) }, icon = LobbyIcons.TRASH, danger = true, dangerColor = KitDangerColor)) },
        )
        ServerKind.UNSAVED -> listOf(save, pin)
        ServerKind.FEATURED, ServerKind.SPONSORED -> {
            val campaign = entry.featured?.featured?.takeIf { it.dismissibleInServerList }
            listOfNotNull(save, pin, campaign?.let { ServerAction(MenuAction("Dismiss", { FeaturedServers.dismissMultiplayer(it.campaignId) }, icon = LobbyIcons.CLOSE)) })
        }
        ServerKind.LAN -> emptyList()
    }
}

internal fun folderMenuItems(servers: LobbyServers, slot: FolderSlot): List<MenuAction> {
    val custom = ServerFolders.sort() == ServerFolders.Sort.CUSTOM
    val index = slot.order.indexOf(slot.folder)
    return listOfNotNull(
        MenuAction("Move up", { servers.moveFolder(slot.folder, -1, slot.order) }, enabled = index > 0).takeIf { custom },
        MenuAction("Move down", { servers.moveFolder(slot.folder, 1, slot.order) }, enabled = index in 0 until slot.order.lastIndex).takeIf { custom },
        MenuAction("Rename", { servers.folderPrompt = RenameFolderPrompt(slot.folder) }, icon = SOCIAL_ASSETS + "edit-02.svg"),
        MenuAction("Delete folder", { servers.folderPrompt = DeleteFolderPrompt(slot.folder) }, icon = LobbyIcons.TRASH, danger = true, dangerColor = KitDangerColor),
    )
}

internal fun serverMenuItems(
    lobby: LobbyShell,
    entry: ServerEntry,
    canReorder: Boolean = false,
    vertical: Boolean = false,
    onDelete: ((ServerEntry) -> Unit)? = null,
    folders: Boolean = false,
): List<MenuAction> =
    listOf(MenuAction("Join", { lobby.join(entry.data) }, icon = SOCIAL_ASSETS + "log-in-04.svg")) +
        serverActions(lobby, entry, canReorder, onDelete, vertical, folders).map { it.item }

@Composable
private fun ServerText(entry: ServerEntry, revision: Int, pinned: Boolean) {
    @Suppress("UNUSED_EXPRESSION") revision
    ServerRevisions.of(entry.data)
    val data = entry.data
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(data.shownName, Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            if (pinned) PinnedMark()
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (data.state() == ServerData.State.INCOMPATIBLE) DangerTag("Incompatible".uppercase())
            PlayerSampleTooltip(data, Modifier.weight(1f, fill = false)) {
                Text(serverMeta(data), color = if (LobbyServers.failed(data)) SocialDangerText else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        MotdText(entry, revision, reserve = true)
    }
}

internal fun serverMeta(data: ServerData): String {
    val answered = data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE
    return when {
        data.isLan -> "Local network"
        LobbyServers.failed(data) -> "Offline"
        answered -> listOfNotNull(data.players?.let { "%,d online".format(it.online()) }, "${data.ping} ms").joinToString(SEPARATOR)
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
    val muted = theme.mutedText
    val surface = theme.popupBackground.compositeOver(theme.pageSurface)
    val parsed = remember(motd, muted, surface) { motdLines(motd, muted, surface) }
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

private class MotdLineBuilder(private val fallback: Color, private val surface: Color) {
    private val builder = AnnotatedString.Builder()
    private val run = StringBuilder()
    private var runStyle: SpanStyle? = null

    fun add(color: Int?, bold: Boolean, italic: Boolean, underlined: Boolean, struck: Boolean, codepoint: Int) {
        val decorations = listOfNotNull(TextDecoration.Underline.takeIf { underlined }, TextDecoration.LineThrough.takeIf { struck })
        val next = SpanStyle(
            color = (color?.let { Color(0xFF000000 or it.toLong()) } ?: fallback).legibleOn(surface),
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

internal fun motdLines(component: Component?, fallback: Color, surface: Color): List<AnnotatedString> {
    component ?: return emptyList()
    //? if > 1.8.9 {
    return Minecraft.getInstance().font.split(component, VANILLA_MOTD_WIDTH).take(VANILLA_MOTD_LINES).map { line ->
        val builder = MotdLineBuilder(fallback, surface)
        line.accept { _, style, codepoint ->
            builder.add(style.color?.value, style.isBold, style.isItalic, style.isUnderlined, style.isStrikethrough, codepoint)
            true
        }
        builder.build()
    }
    //?} else {
    /*return Minecraft.getInstance().textRenderer.split(component.string, VANILLA_MOTD_WIDTH).take(VANILLA_MOTD_LINES).map { line ->
        val builder = MotdLineBuilder(fallback, surface)
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
    ModalDialog(onDismiss = onDismiss, width = 440.dp, dismissOnScrim = true) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        DialogTitle("Direct connect")
        TextField(
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
            size = ControlSize.Large,
            fontSize = 14.sp,
            focusRequester = focus,
            onSubmit = ::connect,
        )
        if (invalid) Text("Invalid address", color = SocialDangerText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        ServerPreviewCard(target.ifEmpty { "Server address" }, valid, probe, Modifier.fillMaxWidth().height(DirectPreviewHeight))
        DialogButtons {
            TextButton(text = "Cancel", onClick = onDismiss, size = ControlSize.Large)
            TextButton(text = "Connect", onClick = ::connect, size = ControlSize.Large, style = ControlStyle.Primary, enabled = address.isNotBlank())
        }
    }
}

@Composable
private fun DeleteServerDialog(entry: ServerEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = "Delete ${entry.data.shownName}?",
        text = "It will be removed from your server list.",
        confirmLabel = "Delete",
        dismissLabel = "Cancel",
        icon = LobbyIcons.TRASH,
        danger = true,
        confirmEnabled = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
