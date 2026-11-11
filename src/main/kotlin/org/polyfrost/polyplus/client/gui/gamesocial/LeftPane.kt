@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.concentric
import org.polyfrost.polyplus.client.gui.CompactStatusPicker
import org.polyfrost.polyplus.client.gui.DISABLED_ALPHA
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.SocialControlBackground
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
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
import org.polyfrost.polyplus.client.gui.lobbysocial.SectionHeading
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDialog
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialListState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPerson
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.ThreadFilter
import org.polyfrost.polyplus.client.gui.lobbysocial.ThreadList
import org.polyfrost.polyplus.client.gui.lobbysocial.ThreadRow
import org.polyfrost.polyplus.client.gui.lobbysocial.threadMenuItems
import org.polyfrost.polyplus.client.gui.lobbysocial.ToggleIconButton
import org.polyfrost.polyplus.client.gui.lobbysocial.partyCells
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberSocialListState
import org.polyfrost.polyplus.client.gui.lobbysocial.requestItems
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.lobbysocial.socialRow
import org.polyfrost.polyplus.client.gui.lobbysocial.socialRowInk
import org.polyfrost.polyplus.client.gui.lobbysocial.socialRowMuted
import org.polyfrost.polyplus.client.gui.lobbysocial.visibleFriends
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PlayerStatus
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.PlayerStatusRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform

private val Inset = 8.dp
private val ChipHeight = 34.dp
private val ChipFace = 24.dp
private val TabHeight = 34.dp
private val TrackInset = 2.dp

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
            list.query,
            {
                list.query = it
                state.query = it
            },
            inset.fillMaxWidth().height(38.dp),
            "Search friends and chats",
            themedIcon(LobbyIcons.SEARCH),
            64,
            false,
            true,
            true,
            true,
            14.sp,
            null,
            false,
            null,
            null,
            { SearchAction(list, snapshot, tab) },
            null,
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
            list.query.isNotBlank() -> SearchResults(state, list, snapshot, body)
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
            Label(name, 16.sp, weight = FontWeight.Medium)
            if (reconnecting) {
                Label("Reconnecting", 12.sp, color = MutedText)
            } else {
                CompactStatusPicker { close -> ChatAsItems(state, snapshot, close) }
            }
        }
        InvitesBell(invites)
        IconButton(themedIcon(LobbyIcons.CLOSE), "Close", Modifier, onClose)
    }
}

@Composable
private fun ChatAsItems(state: SocialPageState, snapshot: SocialSnapshot, close: () -> Unit) {
    if (snapshot.delegations.isEmpty()) return
    SectionHeading("Chat as", Modifier.padding(start = Inset, top = Inset))
    (listOf<SocialPerson?>(null) + snapshot.delegations).forEach { person ->
        val selected = person?.id == snapshot.actAs
        MenuItem(
            person?.name ?: ClientPlatform.localPlayerName(),
            null,
            null,
            MenuItemSize.Compact,
            false,
            selected,
            true,
            null,
            Modifier,
            null,
        ) {
            close()
            state.source.chatAs(person?.id)
        }
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
    val (interaction, hovered) = rememberSocialHover()
    Row(
        modifier
            .height(ChipHeight)
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .socialRow(selected, hovered && onClick != null)
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.clickableWithSound(onClick) else Modifier)
            .padding(start = Inset, end = Inset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        face()
        Label(title, 14.sp, color = socialRowInk(selected), weight = FontWeight.Medium)
        detail?.let { Label(it, 12.sp, Modifier.weight(1f), color = socialRowMuted(selected)) }
        if (unread) Box(Modifier.size(Inset).clip(OneConfigTheme.current.circleShape).background(OneConfigTheme.accent))
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
    val drop = with(LocalDensity.current) { (ChipHeight + 2.dp).roundToPx() }
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
                    ThemedIcon(SOCIAL_ASSETS + "users-01.svg", MutedText, 16.dp)
                } else {
                    SocialGroupAvatar(party.members.map { it.player }, ChipFace, ringColor = Color.Transparent, grid = true)
                }
            },
        )
        if (picking) {
            EscapeCloses { picking = false }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, drop),
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
        face = { ThemedIcon(SOCIAL_ASSETS + "globe-01.svg", MutedText, 16.dp) },
    )
}

@Composable
private fun Tabs(list: SocialListState, snapshot: SocialSnapshot, tab: LeftTab, modifier: Modifier, onSelect: (LeftTab) -> Unit) {
    SegmentTrack(modifier.fillMaxWidth()) {
        LeftTab.entries.filter { it != LeftTab.Special || snapshot.specialTarget }.forEach { entry ->
            val count = when (entry) {
                LeftTab.Chats -> list.unread(snapshot)
                LeftTab.Friends -> snapshot.requests.count { it.incoming }
                LeftTab.Groups -> 0
                LeftTab.Special -> snapshot.threads.count { it.special && it.unread }
            }
            val selected = entry == tab
            SegmentButton(entry.label, selected, Modifier.weight(1f).height(TabHeight), onClick = { onSelect(entry) }) {
                when {
                    count == 0 -> Unit
                    selected -> Label(count.toString(), 10.sp, color = OneConfigTheme.current.accentTextColor)
                    else -> CountPill(count)
                }
            }
        }
    }
}

@Composable
internal fun SegmentTrack(modifier: Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .clip(SocialFieldShape)
            .background(SocialControlBackground)
            .padding(TrackInset),
        horizontalArrangement = Arrangement.spacedBy(TrackInset),
        content = content,
    )
}

@Composable
internal fun SegmentButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    fontSize: TextUnit = 14.sp,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val (interaction, hovered) = rememberSocialHover()
    val active = enabled && hovered
    val ink = when {
        selected -> OneConfigTheme.current.accentTextColor
        active -> SocialTextPrimary
        else -> MutedText
    }
    Row(
        modifier
            .rowFrame(selected, active, SocialFieldShape.concentric(TrackInset))
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Label(label, fontSize, color = ink, weight = FontWeight.Medium)
        trailing()
    }
}

@Composable
private fun AddFriendButton(state: SocialPageState, modifier: Modifier = Modifier) {
    TextButton("Add friend", themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"), modifier.height(38.dp), ButtonStyle.Primary, true) { state.dialog = SocialDialog.AddFriend }
}

@Composable
private fun SearchResults(state: SocialPageState, list: SocialListState, snapshot: SocialSnapshot, modifier: Modifier) {
    val threads = list.matching(snapshot)
    val friends = visibleFriends(state, snapshot, FriendFilter.All)
    if (threads.isEmpty() && friends.isEmpty()) {
        EmptyState("No friends or chats match that", modifier)
        return
    }
    val activeId = SocialChatLink.active?.id
    ScrollFade(modifier, 2.dp) {
        if (threads.isNotEmpty()) SectionHeading("Chats", Modifier.padding(start = Inset, top = 6.dp))
        threads.forEach { thread -> ThreadRow(thread, activeId == thread.id, { threadMenuItems(state, thread) }) { state.source.open(thread) } }
        if (friends.isNotEmpty()) SectionHeading("Friends", Modifier.padding(start = Inset, top = 6.dp))
        friends.forEach { RosterRow(state, it) }
    }
}

@Composable
private fun GroupsBody(state: SocialPageState, list: SocialListState, snapshot: SocialSnapshot, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Inset)) {
        TextButton("New group", themedIcon(LobbyIcons.PLUS), Modifier.padding(horizontal = Inset).fillMaxWidth().height(38.dp), ButtonStyle.Secondary, true) { state.dialog = SocialDialog.NewGroup }
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
    ScrollFade(modifier, 0.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Inset), horizontalArrangement = Arrangement.spacedBy(Inset)) {
            AddFriendButton(state, Modifier.weight(1f))
            if (snapshot.blocked.isNotEmpty()) TextButton("Blocked ${snapshot.blocked.size}", Modifier.height(38.dp), window::showBlocked)
        }
        if (requests.isNotEmpty()) {
            SectionHeading("Requests ${requests.size}", Modifier.padding(start = Inset, top = 12.dp, bottom = 6.dp))
            requests.forEach { Box(Modifier.padding(horizontal = Inset)) { InboxLine(it) } }
        }
        RosterSections(state, visibleFriends(state, snapshot, FriendFilter.All))
    }
}
