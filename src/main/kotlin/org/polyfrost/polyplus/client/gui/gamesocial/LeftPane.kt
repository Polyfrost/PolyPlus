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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.CompactStatusPicker
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.SocialControlBackground
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbysocial.FriendFilter
import org.polyfrost.polyplus.client.gui.lobbysocial.InboxItem
import org.polyfrost.polyplus.client.gui.lobbysocial.InboxLine
import org.polyfrost.polyplus.client.gui.lobbysocial.InvitePicker
import org.polyfrost.polyplus.client.gui.lobbysocial.NoFriendsMessage
import org.polyfrost.polyplus.client.gui.lobbysocial.PersonHead
import org.polyfrost.polyplus.client.gui.lobbysocial.ReplyFilterButton
import org.polyfrost.polyplus.client.gui.lobbysocial.RosterRow
import org.polyfrost.polyplus.client.gui.lobbysocial.RosterSections
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.ChatAsPicker
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDialog
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialList
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialListState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPerson
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.ThreadFilter
import org.polyfrost.polyplus.client.gui.lobbysocial.ThreadList
import org.polyfrost.polyplus.client.gui.lobbysocial.ThreadRow
import org.polyfrost.polyplus.client.gui.lobbysocial.threadMenuItems
import org.polyfrost.polyplus.client.gui.lobbysocial.ToggleIconButton
import org.polyfrost.polyplus.client.gui.lobbysocial.paging
import org.polyfrost.polyplus.client.gui.lobbysocial.partyCells
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberSocialListState
import org.polyfrost.polyplus.client.gui.lobbysocial.requestItems
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.lobbysocial.socialRowFilled
import org.polyfrost.polyplus.client.gui.lobbysocial.socialSelectableRow
import org.polyfrost.polyplus.client.gui.lobbysocial.visibleFriends
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.PlayerStatusRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.liquidGlass
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition

private val Inset = 8.dp
private val ChipHeight = 34.dp
private val ChipFace = 24.dp

private enum class LeftTab(val label: String, val filter: ThreadFilter?) {
    Chats("Chats", ThreadFilter.All),
    Friends("Friends", null),
    Groups("Groups", ThreadFilter.Groups),
    Special("Special", ThreadFilter.Special),
}

@Composable
internal fun LeftPane(
    window: SocialWindowState,
    state: SocialPageState,
    snapshot: SocialSnapshot,
    invites: List<InboxItem>,
    reconnecting: Boolean,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val list = rememberSocialListState(state.source, snapshot, hideParty = true)
    var chosen by remember { mutableStateOf(LeftTab.Chats) }
    val tab = chosen.takeUnless { it == LeftTab.Special && !snapshot.specialTarget } ?: LeftTab.Chats
    Column(modifier.padding(start = Inset, end = Inset, top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val inset = Modifier.padding(horizontal = Inset)
        SelfHeader(state, snapshot, reconnecting, onClose, inset, invites)
        TextField(
            value = list.query,
            onValueChange = {
                list.query = it
                state.query = it
            },
            modifier = inset.fillMaxWidth().height(38.dp),
            placeholder = "Search friends and chats",
            leadingIcon = LobbyIcons.SEARCH,
            maxLength = 64,
            fontSize = 14.sp,
            trailing = { SearchAction(list, snapshot, tab) },
        )
        Row(inset.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Inset)) {
            PartyChip(window, snapshot, Modifier.weight(1f))
            WorldChip(window, snapshot, Modifier.weight(1f))
        }
        Tabs(list, snapshot, tab, inset) { entry ->
            chosen = entry
            entry.filter?.let { list.chosen = it }
        }
        val body = Modifier.fillMaxWidth().weight(1f)
        when {
            list.query.isNotBlank() -> SearchResults(state, list, snapshot, tab != LeftTab.Friends, body)
            tab == LeftTab.Friends -> FriendsBody(window, state, snapshot, body)
            tab == LeftTab.Groups -> GroupsBody(state, list, snapshot, body)
            else -> ThreadList(state, list, snapshot, body)
        }
        VoiceStrip(window)
    }
}

@Composable
internal fun SelfHeader(
    state: SocialPageState,
    snapshot: SocialSnapshot,
    reconnecting: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    invites: List<InboxItem> = emptyList(),
) {
    val status by PlayerStatusRepository.status.collectAsState()
    val selfId = ChatIdentity.selfId()
    val name = snapshot.delegations.firstOrNull { it.id == snapshot.actAs }?.name ?: ClientPlatform.localPlayerName()
    val self = remember(selfId, name, status) {
        SocialPerson(selfId, name, seedOf(selfId), realSkin = true, online = status != PlayerStatus.Offline)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Inset)) {
        PersonHead(self, 40.dp)
        Column(Modifier.weight(1f).padding(start = 2.dp)) {
            Text(name, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            if (reconnecting) {
                Text("Reconnecting", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            } else {
                CompactStatusPicker()
            }
            if (snapshot.delegations.isNotEmpty()) ChatAsPicker(state, snapshot)
        }
        InvitesBell(invites)
        IconButton(
            icon = LobbyIcons.CLOSE,
            contentDescription = "Close",
            onClick = onClose,
            modifier = Modifier.size(34.dp),
            tint = OneConfigTheme.current.textColor,
            tooltipPlacement = TooltipPlacement.BelowStart,
        )
    }
}

@Composable
private fun SearchAction(list: SocialListState, snapshot: SocialSnapshot, tab: LeftTab) {
    when (tab) {
        LeftTab.Friends -> Unit
        LeftTab.Special -> ReplyFilterButton(list.replies, snapshot.scanningReplies, list::cycleReplies)
        else -> ToggleIconButton(
            LobbyIcons.FILTER,
            list.unreadOnly,
            if (list.unreadOnly) "Show all conversations" else "Show unread only",
            list::toggleUnread,
        )
    }
}

@Composable
private fun PinnedChip(
    title: String,
    detail: String?,
    modifier: Modifier,
    selected: Boolean = false,
    unread: Boolean = false,
    onClick: (() -> Unit)? = null,
    face: @Composable () -> Unit,
) {
    val theme = OneConfigTheme.current
    Row(
        modifier
            .height(ChipHeight)
            .then(if (liquidGlassTheme) Modifier.liquidGlass(SocialFieldShape, frosted = false) else Modifier)
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .then(if (onClick != null) Modifier.socialSelectableRow(selected, onClick, listOfNotNull(title, detail).joinToString(", "), shape = SocialFieldShape) else Modifier)
            .padding(start = Inset, end = Inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        face()
        val filled = socialRowFilled(selected)
        Text(title, color = SelectionDefaults.itemContentColor(filled, ItemSelectionStyle.Accent), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        detail?.let { Text(it, modifier = Modifier.weight(1f), color = SelectionDefaults.itemSecondaryColor(filled, ItemSelectionStyle.Accent, theme.mutedText), style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        if (unread) {
            val dot = Modifier.size(Inset).clip(theme.circleShape)
            Box(if (filled) dot.background(SelectionDefaults.itemContentColor(true, ItemSelectionStyle.Accent)) else dot.background(OneConfigTheme.accent))
        }
    }
}

@Composable
private fun partyDetail(party: Party): String {
    val talking = partyCells(party).firstOrNull { it.speaking }?.name
    return talking?.let { "$it talking" } ?: "${party.members.size} of ${PartyRepository.MAX_MEMBERS}"
}

@Composable
private fun PartyChip(window: SocialWindowState, snapshot: SocialSnapshot, modifier: Modifier) {
    val current by PartyRepository.party.collectAsState()
    val party = current
    var picking by remember { mutableStateOf(false) }
    val thread = snapshot.threads.firstOrNull { it.party && it.id == party?.chatGroupId }
    Box(modifier) {
        PinnedChip(
            title = if (party == null) "Start a party" else "Party",
            detail = party?.let { partyDetail(it) },
            modifier = Modifier.fillMaxWidth(),
            selected = window.view == WindowView.Party,
            unread = thread?.unread == true,
            onClick = { if (party == null) picking = !picking else window.showParty() },
            face = {
                if (party == null) {
                    ThemedIcon(SOCIAL_ASSETS + "users-01.svg", OneConfigTheme.current.mutedText, 16.dp)
                } else {
                    SocialGroupAvatar(party.members.map { it.player }, ChipFace, ringColor = Color.Transparent, grid = true)
                }
            },
        )
        if (picking) {
            EscapeCloses { picking = false }
            Popup(
                popupPositionProvider = rememberAnchoredPosition(Placement.BelowStart, 2.dp),
                onDismissRequest = { picking = false },
                properties = PopupProperties(focusable = true),
            ) { InvitePicker(party) }
        }
    }
}

@Composable
private fun WorldChip(window: SocialWindowState, snapshot: SocialSnapshot, modifier: Modifier) {
    val hosting = snapshot.hosting
    if (hosting == null && !SocialHosting.singleplayer) return
    PinnedChip(
        title = "World",
        detail = when {
            hosting != null -> "Hosting"
            window.lanOpen -> "On LAN"
            else -> "Private"
        },
        modifier = modifier,
        selected = window.view == WindowView.World,
        onClick = { window.showWorld() },
        face = { ThemedIcon(SOCIAL_ASSETS + "globe-01.svg", OneConfigTheme.current.mutedText, 16.dp) },
    )
}

@Composable
private fun Tabs(list: SocialListState, snapshot: SocialSnapshot, tab: LeftTab, modifier: Modifier, onSelect: (LeftTab) -> Unit) {
    val tabs = LeftTab.entries.filter { it != LeftTab.Special || snapshot.specialTarget }.map { entry ->
        val count = when (entry) {
            LeftTab.Chats -> list.unread(snapshot)
            LeftTab.Friends -> snapshot.requests.count { it.incoming }
            LeftTab.Groups -> 0
            LeftTab.Special -> snapshot.threads.count { it.special && it.unread }
        }
        TabItem(entry, entry.label, badge = count.takeIf { it > 0 }?.toString())
    }
    TabRow(tabs, tab, onSelect, modifier.fillMaxWidth(), style = TabStyle.Segmented, size = ControlSize.Large, slide = true)
}

@Composable
private fun AddFriendButton(state: SocialPageState, modifier: Modifier = Modifier) {
    TextButton(
        text = "Add friend",
        icon = SOCIAL_ASSETS + "user-plus-01.svg",
        onClick = { state.dialog = SocialDialog.AddFriend },
        modifier = modifier,
        style = ControlStyle.Primary,
        enabled = true,
        size = ControlSize.Large,
    )
}

@Composable
private fun SearchResults(state: SocialPageState, list: SocialListState, snapshot: SocialSnapshot, filtered: Boolean, modifier: Modifier) {
    val threads = list.matching(snapshot, SocialChatLink.active?.id, filtered)
    val friends = visibleFriends(state, snapshot, FriendFilter.All)
    if (threads.isEmpty() && friends.isEmpty()) {
        EmptyState("No friends or chats match that", modifier)
        return
    }
    val activeId = SocialChatLink.active?.id
    ScrollFade(modifier, 2.dp) {
        if (threads.isNotEmpty()) SectionHeader("Chats", Modifier.padding(start = Inset, top = 6.dp))
        threads.forEach { thread -> ThreadRow(thread, activeId == thread.id, { threadMenuItems(state, snapshot, thread) }) { state.source.open(thread) } }
        if (friends.isNotEmpty()) SectionHeader("Friends", Modifier.padding(start = Inset, top = 6.dp))
        friends.forEach { RosterRow(state, it) }
    }
}

@Composable
private fun GroupsBody(state: SocialPageState, list: SocialListState, snapshot: SocialSnapshot, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Inset)) {
        TextButton(
            text = "New group",
            icon = LobbyIcons.PLUS,
            onClick = { state.dialog = SocialDialog.NewGroup },
            modifier = Modifier.padding(horizontal = Inset).fillMaxWidth(),
            style = ControlStyle.Secondary,
            enabled = true,
            size = ControlSize.Large,
        )
        ThreadList(state, list, snapshot, Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun FriendsBody(window: SocialWindowState, state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val requests = requestItems(snapshot, state.source)
    if (snapshot.friends.isEmpty() && requests.isEmpty() && snapshot.blocked.isEmpty()) {
        NoFriendsMessage(state, modifier)
        return
    }
    ScrollFade(modifier, 0.dp, paging = state.paging(snapshot, true, SocialList.Incoming, SocialList.Outgoing, SocialList.Friends)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Inset), horizontalArrangement = Arrangement.spacedBy(Inset)) {
            AddFriendButton(state, Modifier.weight(1f))
            if (snapshot.blocked.isNotEmpty()) TextButton(text = "Blocked ${snapshot.countLabel(snapshot.blocked.size, SocialList.Blocked)}", onClick = window::showBlocked, modifier = Modifier, size = ControlSize.Large)
        }
        if (requests.isNotEmpty()) {
            SectionHeader("Requests ${snapshot.countLabel(requests.size, SocialList.Incoming, SocialList.Outgoing)}", Modifier.padding(start = Inset, top = 12.dp, bottom = 6.dp))
            requests.forEach { Box(Modifier.padding(horizontal = Inset)) { InboxLine(it) } }
        }
        RosterSections(state, visibleFriends(state, snapshot, FriendFilter.All))
    }
}
