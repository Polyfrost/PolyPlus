@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.SelfStatusPicker
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rightColumn

private fun threadsColumn(): Modifier = Modifier
    .padding(start = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.PageBottom)
    .width(LobbyLayout.ChatWidth)
    .fillMaxHeight()

@Composable
internal fun BoxScope.SocialDirectory(state: SocialPageState, snapshot: SocialSnapshot, onBack: () -> Unit) {
    FollowSocialChanges(state, snapshot)
    ThreadsColumn(state, snapshot, threadsColumn())
    val conversation = state.conversation(snapshot)
    if (conversation != null) {
        ConversationPane(state, conversation.chat, conversation.thread, centerColumn())
    } else {
        RosterPane(state, snapshot, onBack, centerColumn())
    }
    val side = rightColumn()
    state.friend(snapshot)?.let { FriendDetailPanel(state, snapshot, it.person, side) } ?: InboxList(state, snapshot, side)
}

@Composable
private fun ThreadsColumn(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val list = rememberSocialListState(state.source, snapshot)
    val filter = list.filter(snapshot)
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelTitle("Messages", Modifier.weight(1f))
            if (filter == ThreadFilter.Special) ReplyFilterButton(list.replies, snapshot.scanningReplies, list::cycleReplies)
            ToggleIconButton(
                SOCIAL_ASSETS + "message-chat-circle.svg",
                list.unreadOnly,
                if (list.unreadOnly) "Show all conversations" else "Show unread only",
                list::toggleUnread,
            )
            ToggleIconButton(LobbyIcons.SEARCH, list.searching, if (list.searching) "Close search" else "Search conversations", list::toggleSearch)
            SocialIconButton(LobbyIcons.PLUS, tooltip = "New group") {
                state.dialog = SocialDialog.NewGroup
            }
        }
        if (list.searching) {
            SocialTextField(
                value = list.query,
                onValueChange = { list.query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search conversations",
                leadingIcon = LobbyIcons.SEARCH,
            )
        }
        SelfStatusPicker(Modifier.fillMaxWidth())
        if (snapshot.delegations.isNotEmpty()) ChatAsPicker(state, snapshot)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.tabs(snapshot).forEach { entry ->
                Box {
                    Chip(entry.name, selected = entry == filter) { list.chosen = entry }
                    if (list.badged(entry, snapshot)) Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(OneConfigTheme.current.circleShape).background(OneConfigTheme.accent))
                }
            }
        }
        ThreadList(state, list, snapshot, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
internal fun ToggleIconButton(icon: String, active: Boolean, tooltip: String, onClick: () -> Unit) {
    SocialIconButton(
        icon,
        background = if (active) OneConfigTheme.accent else null,
        tint = if (active) OneConfigTheme.current.accentTextColor else SocialTextPrimary,
        tooltip = tooltip,
        onClick = onClick,
    )
}

@Composable
private fun RosterPane(state: SocialPageState, snapshot: SocialSnapshot, onBack: () -> Unit, modifier: Modifier) {
    var filter by remember { mutableStateOf(FriendFilter.All) }
    Column(modifier.panel()) {
        RosterHeader(state, snapshot, filter, onBack) { filter = it }
        val body = Modifier.fillMaxWidth().weight(1f)
        when {
            filter == FriendFilter.Requests -> RequestList(state, snapshot, body)
            filter == FriendFilter.Blocked -> BlockedList(state, snapshot, body)
            snapshot.friends.isEmpty() -> NoFriendsMessage(state, body)
            else -> Roster(state, visibleFriends(state, snapshot, filter), body)
        }
    }
}

@Composable
private fun RosterHeader(state: SocialPageState, snapshot: SocialSnapshot, filter: FriendFilter, onBack: () -> Unit, onFilter: (FriendFilter) -> Unit) {
    val requests = snapshot.requests.count { it.incoming }
    Row(
        Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackButton(onBack)
        PageTitle("Friends", Modifier.padding(end = 2.dp))
        SocialTextField(
            value = state.query,
            onValueChange = { state.query = it },
            modifier = Modifier.weight(1f),
            placeholder = "Search friends",
            leadingIcon = LobbyIcons.SEARCH,
        )
        FriendFilter.entries.forEach { entry ->
            val label = when {
                entry == FriendFilter.Requests && requests > 0 -> "Requests $requests"
                entry == FriendFilter.Blocked && snapshot.blocked.isNotEmpty() -> "Blocked ${snapshot.blocked.size}"
                else -> entry.name
            }
            Box(Modifier) { Chip(label, selected = entry == filter) { onFilter(entry) } }
        }
        SocialButton("Add friend", icon = SOCIAL_ASSETS + "user-plus-01.svg", onClick = { state.dialog = SocialDialog.AddFriend })
    }
}

@Composable
private fun InboxList(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val items = inboxItems(snapshot, state.source)
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelTitle("Waiting on you")
            if (snapshot.waiting > 0) Label(snapshot.waiting.toString(), 14.sp, color = MutedText)
        }
        if (items.isEmpty()) {
            EmptyState("Nothing waiting", Modifier.weight(1f).fillMaxWidth(), "Invites and friend requests show up here.")
            return@Column
        }
        ScrollFade(Modifier.weight(1f).fillMaxWidth(), 0.dp) {
            items.groupBy { it.section }.forEach { (section, entries) ->
                SectionHeading(section, Modifier.padding(top = 6.dp, bottom = 6.dp))
                entries.forEach { InboxLine(it) }
            }
        }
    }
}
