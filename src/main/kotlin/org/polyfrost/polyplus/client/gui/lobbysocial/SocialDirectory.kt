@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.ui.text.style.TextOverflow
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.PanelHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.SelfStatusPicker
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.leftColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.pageTitleStyle
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.centerColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rightColumn
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.ChipDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle

internal const val SEARCH_MAX_LENGTH = 64

@Composable
internal fun BoxScope.SocialDirectory(state: SocialPageState, snapshot: SocialSnapshot, onBack: () -> Unit) {
    FollowSocialChanges(state, snapshot)
    ThreadsColumn(state, snapshot, leftColumn())
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
            IconButton(
                icon = LobbyIcons.PLUS,
                contentDescription = "New group",
                onClick = { state.dialog = SocialDialog.NewGroup },
                modifier = Modifier.size(34.dp),
                tint = OneConfigTheme.current.textColor,
                tooltipPlacement = TooltipPlacement.BelowStart,
            )
        }
        if (list.searching) {
            SearchField(
                query = list.query,
                onQueryChange = { list.query = it.take(SEARCH_MAX_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Search conversations",
                clearable = false,
            )
        }
        SelfStatusPicker(Modifier.fillMaxWidth())
        if (snapshot.delegations.isNotEmpty()) ChatAsPicker(state, snapshot)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.tabs(snapshot).forEach { entry ->
                Box {
                    Chip(label = entry.name, selected = entry == filter, onClick = { list.chosen = entry })
                    if (list.badged(entry, snapshot)) Box(Modifier.align(Alignment.TopEnd).size(6.dp).clip(OneConfigTheme.current.circleShape).background(OneConfigTheme.accent))
                }
            }
        }
        ThreadList(state, list, snapshot, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
internal fun ToggleIconButton(icon: String, active: Boolean, tooltip: String, onClick: () -> Unit) {
    IconButton(
        icon = icon,
        contentDescription = tooltip,
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        selected = active,
        tint = if (active) OneConfigTheme.current.accentTextColor else SocialTextPrimary,
        tooltipPlacement = TooltipPlacement.BelowStart,
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
            else -> Roster(state, visibleFriends(state, snapshot, filter), body, state.paging(snapshot, filter == FriendFilter.All, SocialList.Friends))
        }
    }
}

@Composable
private fun RosterHeader(state: SocialPageState, snapshot: SocialSnapshot, filter: FriendFilter, onBack: () -> Unit, onFilter: (FriendFilter) -> Unit) {
    val requests = snapshot.requests.count { it.incoming }
    PanelHeader(
        "Friends",
        Modifier.height(58.dp).padding(horizontal = 12.dp),
        titleStyle = pageTitleStyle(),
        leading = { BackButton(onBack) },
        titleWeight = false,
        spacing = 8.dp,
        actions = {
            SearchField(
                query = state.query,
                onQueryChange = { state.query = it.take(SEARCH_MAX_LENGTH) },
                modifier = Modifier.weight(1f).padding(start = 2.dp),
                placeholder = "Search friends",
                clearable = false,
            )
            val tabs = FriendFilter.entries.map { entry ->
                val label = when {
                    entry == FriendFilter.Requests && requests > 0 -> "Requests ${snapshot.countLabel(requests, SocialList.Incoming)}"
                    entry == FriendFilter.Blocked && snapshot.blocked.isNotEmpty() -> "Blocked ${snapshot.countLabel(snapshot.blocked.size, SocialList.Blocked)}"
                    else -> entry.name
                }
                TabItem(entry, label)
            }
            TabRow(tabs, filter, onFilter, style = TabStyle.Chips, size = ChipDefaults.size, fill = false)
            TextButton(
                text = "Add friend",
                icon = SOCIAL_ASSETS + "user-plus-01.svg",
                onClick = { state.dialog = SocialDialog.AddFriend },
                modifier = Modifier,
                style = ControlStyle.Secondary,
                enabled = true,
                size = ControlSize.Large,
            )
        },
    )
}

@Composable
private fun InboxList(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val items = inboxItems(snapshot, state.source)
    Column(modifier.panel().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PanelTitle("Waiting on you")
            if (snapshot.waiting > 0) Text(snapshot.countLabel(snapshot.waiting, SocialList.Incoming), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        val paging = listPaging(state.source, snapshot, true, SocialList.Incoming, SocialList.Outgoing)
        if (items.isEmpty()) {
            FillWhenEmpty(paging)
            EmptyState("Nothing waiting", Modifier.weight(1f).fillMaxWidth(), text = "Invites and friend requests show up here.")
            return@Column
        }
        ScrollFade(Modifier.weight(1f).fillMaxWidth(), 0.dp, paging = paging) {
            items.groupBy { it.section }.forEach { (section, entries) ->
                SectionHeader(section, Modifier.padding(top = 6.dp, bottom = 6.dp))
                entries.forEach { InboxLine(it) }
            }
        }
    }
}
