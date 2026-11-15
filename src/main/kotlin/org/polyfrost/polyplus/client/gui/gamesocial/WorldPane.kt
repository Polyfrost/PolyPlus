@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.polyplus.client.gui.HostFlowState
import org.polyfrost.polyplus.client.gui.HostFlowSteps
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.inviteableGroups
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbysocial.friendRowItems
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.PersonHead
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDialog
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialList
import org.polyfrost.polyplus.client.gui.lobbysocial.listPaging
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPerson
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.WorldAction
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.polyplus.client.gui.lobbysocial.SEARCH_MAX_LENGTH
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader

private val Edge = 24.dp
private val RowHeight = 52.dp
private val RowHead = 38.dp

private class WorldHeading(val title: String, val status: String)

private fun heading(hosting: String?, lan: Boolean, flow: HostFlowState): WorldHeading {
    val name = SocialHosting.worldName
    return when {
        hosting != null -> WorldHeading(name ?: hosting, "Hosting · Invited friends can join")
        !SocialHosting.singleplayer -> WorldHeading("World", "Not in a singleplayer world")
        lan -> WorldHeading(name ?: "This world", "Open on LAN")
        else -> WorldHeading(flow.title, name ?: "This world")
    }
}

@Composable
internal fun WorldPane(window: SocialWindowState, state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier, closable: Boolean = true) {
    val hosting = snapshot.hosting
    val flow = window.hostFlow
    val lan = window.lanOpen && !flow.forFriends
    val back = closable && LocalSocialLook.current.backButton
    val heading = heading(hosting, lan, flow)
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Edge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (back) BackButton(window::closeWorld)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PanelTitle(heading.title)
                Text(heading.status, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            if (hosting != null) TextButton(text = "Stop hosting", onClick = SocialHosting::stop, modifier = Modifier, size = ControlSize.Large)
            if (closable && !back) IconButton(icon = LobbyIcons.CLOSE, contentDescription = "Close world", onClick = window::closeWorld, modifier = Modifier.size(34.dp), tint = OneConfigTheme.current.textColor, tooltipPlacement = TooltipPlacement.BelowStart)
        }
        Divider()
        val body = Modifier.fillMaxWidth().weight(1f)
        when {
            hosting != null -> InviteFriends(state, snapshot, body)
            !SocialHosting.singleplayer -> EmptyState("Nothing to host", body, text = "Open a singleplayer world to host it for friends.")
            lan -> LanStatus(flow, body)
            else -> HostSetup(window, body)
        }
    }
}

@Composable
private fun LanStatus(flow: HostFlowState, modifier: Modifier) {
    EmptyState("Open on your network", modifier, text = "Players on your network can join from Multiplayer. Host for friends to invite people anywhere.") {
        TextButton(text = "Host for friends", onClick = { flow.forFriends = true }, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
    }
}

@Composable
private fun HostSetup(window: SocialWindowState, modifier: Modifier) {
    val friends by FriendsRepository.friends.collectAsState()
    val groups by GroupsRepository.groups.collectAsState()
    val flow = window.hostFlow
    LaunchedEffect(Unit) { P2PSessionManager.retryLoginNow() }
    Box(modifier) {
        HostFlowSteps(flow, window.screen, friends, groups, ChatIdentity.selfId(), window::closeWorld, flow::reset)
    }
}

@Composable
private fun InviteFriends(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val groups by GroupsRepository.groups.collectAsState()
    var query by remember { mutableStateOf("") }
    val invitable = inviteableGroups(groups).filter { query.isBlank() }
    val found = snapshot.friends.filter { it.name.contains(query, ignoreCase = true) }
    val (online, offline) = found.partition { it.online }
    Column(modifier.padding(start = Edge, end = Edge, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SearchField(
            query = query,
            onQueryChange = { query = it.take(SEARCH_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search friends",
            clearable = false,
        )
        val list = Modifier.fillMaxWidth().weight(1f)
        val paging = if (query.isBlank()) listPaging(state.source, snapshot, true, SocialList.Friends) else null
        if (found.isEmpty() && invitable.isEmpty()) {
            NoInvitees(state, query.isBlank(), list)
            return@Column
        }
        ScrollFade(list, 2.dp, paging = paging) {
            PeopleSection("Online ${online.size}", online, state, snapshot)
            if (invitable.isNotEmpty()) {
                SectionHeader("Groups", Modifier.padding(top = 10.dp, bottom = 6.dp))
                invitable.forEach { GroupRow(it) }
            }
            PeopleSection("Offline ${offline.size}", offline, state, snapshot)
        }
    }
}

@Composable
private fun NoInvitees(state: SocialPageState, noFriends: Boolean, modifier: Modifier) {
    if (!noFriends) {
        EmptyState("No friends match that", modifier)
        return
    }
    EmptyState("No friends to invite yet", modifier) {
        TextButton(
            text = "Add friend",
            icon = SOCIAL_ASSETS + "user-plus-01.svg",
            onClick = { state.dialog = SocialDialog.AddFriend },
            modifier = Modifier,
            style = ControlStyle.Primary,
            enabled = true,
            size = ControlSize.Large,
        )
    }
}

@Composable
private fun PeopleSection(title: String, people: List<SocialPerson>, state: SocialPageState, snapshot: SocialSnapshot) {
    if (people.isEmpty()) return
    SectionHeader(title, Modifier.padding(top = 10.dp, bottom = 6.dp))
    people.forEach { person ->
        InviteRow(person.name, person.status, { PersonHead(person, RowHead) }, { friendRowItems(state, person) }) {
            WorldAction(state, snapshot, person, filled = true)
        }
    }
}

@Composable
private fun GroupRow(group: GroupSummary) {
    val self = ChatIdentity.selfId()
    val members = group.members.filterNot { it == self }
    val invite = MenuAction("Invite group", { SocialHosting.inviteGroup(group, self) })
    InviteRow(group.name ?: "Group", "${members.size} members", { SocialGroupAvatar(members, RowHead, ringColor = Color.Transparent, grid = true) }, { listOf(invite) }) {
        TextButton(text = invite.label, onClick = invite.onClick, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
    }
}

@Composable
private fun InviteRow(title: String, detail: String, face: @Composable () -> Unit, menu: () -> List<MenuAction>, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight).contextMenu(entries = menu),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        face()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        action()
    }
}
