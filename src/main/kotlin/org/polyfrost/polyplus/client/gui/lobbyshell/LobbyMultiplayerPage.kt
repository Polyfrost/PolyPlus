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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
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
//? if = 1.8.9
/*import org.polyfrost.polyplus.client.lobby.LobbyMotd*/
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.StringDecomposer
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.gui.ASSETS
import org.polyfrost.polyplus.client.gui.CardSurface
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
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.UUID

private enum class ServerFilter { All, Recent }

private class OnlineFriend(val name: String, val seed: UUID, val realSkin: Boolean, val detail: String)

private val CardGap = 12.dp
private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 2
private const val HERO_FACES = 3
private const val SEPARATOR = " · "

@Composable
internal fun BoxScope.MultiplayerPage(lobby: LobbyShell) {
    val friends = rememberOnlineFriends()
    PageScrim()
    HeroCard(
        lobby,
        friends,
        Modifier
            .padding(start = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
            .width(LobbyLayout.ChatWidth)
            .fillMaxHeight(),
    )
    ServerBrowser(
        lobby,
        onBack = { lobby.show(LobbyPage.Home) },
        onDirect = { lobby.directOpen = true },
        onDelete = { lobby.deleting = it },
        modifier = Modifier
            .padding(start = LobbyLayout.BarStart, end = LobbyLayout.BarEnd, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
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
private fun ServerBrowser(lobby: LobbyShell, onBack: () -> Unit, onDirect: () -> Unit, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val servers = lobby.servers
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ServerFilter.All) }
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
            SocialButton("Add server", onClick = lobby::addServer)
            SocialButton("Direct connect", onClick = onDirect)
            if (PolyPlusMainMenuConfig.realmsSupported() && !PolyPlusMainMenuConfig.hideMainMenuRealms) {
                SocialButton("Realms", onClick = lobby::openRealms)
            }
            SocialIconButton(LobbyIcons.REFRESH, tooltip = "Refresh", onClick = servers::refresh)
        }
        if (shown.isEmpty()) {
            val body = Modifier.fillMaxWidth().weight(1f).panel(16.dp)
            when {
                query.isNotBlank() -> EmptyState("No servers match that search", body)
                filter == ServerFilter.Recent -> EmptyState("No recent servers", body, "Servers you join show up here.")
                else -> EmptyState("No saved servers", body, "Add a server, or use direct connect.") {
                    SocialButton("Add server", icon = LobbyIcons.PLUS, filled = true, onClick = lobby::addServer)
                }
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val rowHeight = (maxHeight - CardGap * (GRID_ROWS - 1)) / GRID_ROWS
                LazyVerticalGrid(
                    GridCells.Fixed(GRID_COLUMNS),
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(CardGap),
                    verticalArrangement = Arrangement.spacedBy(CardGap),
                ) {
                    items(shown, key = { it.key }) { entry -> ServerCard(lobby, entry, canReorder, onDelete, Modifier.height(rowHeight)) }
                }
            }
        }
    }
}

@Composable
internal fun ServerBanner(data: ServerData, revision: Int, height: Dp) {
    @Suppress("UNUSED_EXPRESSION") revision
    val favicon = rememberFavicon(data.iconBytes)
    val ink = CardSurface
    Box(Modifier.fillMaxWidth().height(height).background(OneConfigTheme.accent.copy(alpha = 0.22f))) {
        if (favicon != null) {
            Image(favicon, null, Modifier.fillMaxSize().alpha(0.6f), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, ink))))
    }
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
    val banner = 140.dp
    val icon = 52.dp
    val overhang = 12.dp
    val (interaction, hovered) = rememberSocialHover()
    val selected = lobby.isSelected(data)
    val doubleClick = rememberDoubleClick { lobby.join(data) }
    LaunchedEffect(data) { servers.ping(data) }
    Box(
        modifier
            .cardFrame(selected, hovered)
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

@Composable
private fun CardActions(lobby: LobbyShell, entry: ServerEntry, canReorder: Boolean, onDelete: (ServerEntry) -> Unit, modifier: Modifier) {
    val servers = lobby.servers
    val small = Modifier.size(28.dp)
    val fill = OneConfigTheme.current.popupBackground
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        when (entry.kind) {
            ServerKind.SAVED -> {
                if (canReorder) {
                    MoveArrow(entry.savedIndex > 0, -90f) { servers.move(entry, -1) }
                    MoveArrow(entry.savedIndex < servers.saved.lastIndex, 90f) { servers.move(entry, 1) }
                }
                SocialIconButton(SOCIAL_ASSETS + "edit-02.svg", small, background = fill, tooltip = "Edit") { lobby.editServer(entry) }
                SocialIconButton(LobbyIcons.TRASH, small, background = fill, tooltip = "Delete") { onDelete(entry) }
            }
            ServerKind.UNSAVED -> SocialIconButton(LobbyIcons.PLUS, small, background = fill, tooltip = "Save to list") { servers.saveUnsaved(entry) }
            ServerKind.FEATURED, ServerKind.SPONSORED -> {
                SocialIconButton(LobbyIcons.PLUS, small, background = fill, tooltip = "Save to list") { servers.saveUnsaved(entry) }
                val campaign = entry.featured?.featured
                if (campaign != null && campaign.dismissibleInServerList) {
                    SocialIconButton(LobbyIcons.CLOSE, small, background = fill, tooltip = "Dismiss") { FeaturedServers.dismissMultiplayer(campaign.campaignId) }
                }
            }
            ServerKind.LAN -> Unit
        }
    }
}

@Composable
private fun MoveArrow(enabled: Boolean, rotation: Float, onClick: () -> Unit) {
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

private fun serverMeta(data: ServerData): String {
    val answered = data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE
    return when {
        data.isLan -> "Local network"
        LobbyServers.failed(data) -> "Offline"
        answered -> listOfNotNull(data.players?.let { "%,d online".format(it.online()) }, "${data.ping} ms", Platform.i18n().getUnformattedText(data.version)).joinToString(SEPARATOR)
        else -> "Checking status..."
    }
}

@Composable
private fun MotdText(entry: ServerEntry, revision: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    val data = entry.data
    val muted = MutedText
    val text = when {
        entry.kind == ServerKind.LAN || LobbyServers.failed(data) || data.state() == ServerData.State.PINGING -> AnnotatedString("")
        else -> motdText(data.motd, muted)
    }
    BasicText(
        text,
        maxLines = 2,
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
private fun HeroCard(lobby: LobbyShell, friends: List<OnlineFriend>, modifier: Modifier) {
    val servers = lobby.servers
    val hero = servers.hero
    val banner = 244.dp
    val icon = 72.dp
    val overhang = 18.dp
    if (hero == null) {
        EmptyState("Last played", modifier.cardFrame(selected = false, hovered = false), "The server you last joined shows here.")
        return
    }
    val played = servers.recent.isNotEmpty()
    LaunchedEffect(hero.data) { servers.ping(hero.data) }
    Box(modifier.cardFrame(lobby.isSelected(hero.data), hovered = false).clickableWithSound { lobby.select(hero.data) }) {
        Column(Modifier.fillMaxSize()) {
            ServerBanner(hero.data, servers.revision, banner)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(start = 18.dp, end = 18.dp, top = overhang + 12.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                ServerText(hero, servers.revision, 22)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (friends.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            friends.take(HERO_FACES).forEach { Head(it.seed, 30.dp, realSkin = it.realSkin) }
                            Label("${friends.size} friend${if (friends.size == 1) "" else "s"} online", 12.sp, color = MutedText)
                        }
                    }
                    SocialButton(if (played) "Rejoin" else "Join", modifier = Modifier.fillMaxWidth(), filled = true) { lobby.join(hero.data) }
                }
            }
        }
        Tag(if (played) "Last played" else "Top of your list", TagTone.Accent, Modifier.padding(12.dp))
        Box(Modifier.offset(x = 18.dp, y = banner - icon + overhang)) { ServerIcon(hero.data, servers.revision, icon) }
    }
}

@Composable
private fun DirectConnectDialog(lobby: LobbyShell, onDismiss: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    fun connect() {
        if (lobby.directConnect(address)) onDismiss() else invalid = true
    }
    SocialModalScrim(onDismiss) {
        LaunchedEffect(Unit) { focus.requestFocus() }
        ModalPanel(width = 420.dp) {
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
