@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.HostFlowState
import org.polyfrost.polyplus.client.gui.HostFlowSteps
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.inviteableGroups
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.PersonHead
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.SectionHeading
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDialog
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPerson
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.WorldAction
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository

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
                Label(heading.status, 12.sp, color = MutedText)
            }
            if (hosting != null) SocialButton("Stop hosting", onClick = SocialHosting::stop)
            if (closable && !back) SocialIconButton(LobbyIcons.CLOSE, tooltip = "Close world", onClick = window::closeWorld)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
        val body = Modifier.fillMaxWidth().weight(1f)
        when {
            hosting != null -> InviteFriends(state, snapshot, body)
            !SocialHosting.singleplayer -> EmptyState("Nothing to host", body, "Open a singleplayer world to host it for friends.")
            lan -> LanStatus(flow, body)
            else -> HostSetup(window, body)
        }
    }
}

@Composable
private fun LanStatus(flow: HostFlowState, modifier: Modifier) {
    EmptyState("Open on your network", modifier, "Players on your network can join from Multiplayer. Host for friends to invite people anywhere.") {
        SocialButton("Host for friends", filled = true) { flow.forFriends = true }
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
        SocialTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search friends",
            leadingIcon = LobbyIcons.SEARCH,
        )
        val list = Modifier.fillMaxWidth().weight(1f)
        if (found.isEmpty() && invitable.isEmpty()) {
            NoInvitees(state, query.isBlank(), list)
            return@Column
        }
        ScrollFade(list, 2.dp) {
            PeopleSection("Online ${online.size}", online, state, snapshot)
            if (invitable.isNotEmpty()) {
                SectionHeading("Groups", Modifier.padding(top = 10.dp, bottom = 6.dp))
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
        SocialButton("Add friend", SOCIAL_ASSETS + "user-plus-01.svg", filled = true) { state.dialog = SocialDialog.AddFriend }
    }
}

@Composable
private fun PeopleSection(title: String, people: List<SocialPerson>, state: SocialPageState, snapshot: SocialSnapshot) {
    if (people.isEmpty()) return
    SectionHeading(title, Modifier.padding(top = 10.dp, bottom = 6.dp))
    people.forEach { person ->
        InviteRow(person.name, person.status, { PersonHead(person, RowHead) }) {
            WorldAction(state, snapshot, person, filled = true)
        }
    }
}

@Composable
private fun GroupRow(group: GroupSummary) {
    val self = ChatIdentity.selfId()
    val members = group.members.filterNot { it == self }
    InviteRow(group.name ?: "Group", "${members.size} members", { SocialGroupAvatar(members, RowHead, ringColor = Color.Transparent, grid = true) }) {
        SocialButton("Invite group", filled = true) { SocialHosting.inviteGroup(group, self) }
    }
}

@Composable
private fun InviteRow(title: String, detail: String, face: @Composable () -> Unit, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        face()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(title, 14.sp, weight = FontWeight.Medium)
            Label(detail, 12.sp, color = MutedText)
        }
        action()
    }
}
