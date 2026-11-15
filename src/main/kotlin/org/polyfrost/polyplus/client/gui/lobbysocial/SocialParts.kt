@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.foundation.layout.height
import org.polyfrost.polyplus.client.gui.KitDangerColor
import androidx.compose.ui.text.style.TextOverflow
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyUserTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.components.fadingEdges
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.destinationLabel
import org.polyfrost.polyplus.client.party.HostJoinRequests
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PlayTogether
import org.polyfrost.polyplus.client.party.ServerCheck
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader

private const val GROUP_FACE_SCALE = 0.68f
private const val FADE_HEIGHT = 32
private val NEAR_END = 160.dp

@Composable
internal fun ScrollFade(
    modifier: Modifier,
    spacing: Dp = 6.dp,
    maxHeight: Dp = Dp.Unspecified,
    ink: Color = if (LocalSocialLook.current.surface == SocialSurface.None) PanelSurface else OneConfigTheme.current.pageBackground,
    paging: ListPaging? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val bounds = if (maxHeight == Dp.Unspecified) modifier.fillMaxSize() else modifier.heightIn(max = maxHeight)
    ScrollContainer(scroll, bounds.fadingEdges(scroll, ink, FADE_HEIGHT.dp), scrollbar = false) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
            content()
            if (paging?.loading == true) PageLoadingRow()
        }
    }
    if (paging != null) PageOnScroll(scroll, paging)
}

internal class ListPaging(val more: Boolean, val loading: Boolean, val loaded: Int, val fill: Boolean, val load: () -> Unit) {
    val ready: Boolean
        get() = more && !loading
}

private enum class ScrollReach { Away, Short, End }

@Composable
private fun PageOnScroll(scroll: ScrollState, paging: ListPaging) {
    val reach = with(LocalDensity.current) { NEAR_END.toPx() }
    val current by rememberUpdatedState(paging)
    val position by remember(scroll, reach) {
        derivedStateOf {
            val max = scroll.maxValue
            when {
                max == Int.MAX_VALUE -> ScrollReach.Away
                max == 0 -> ScrollReach.Short
                max - scroll.value <= reach -> ScrollReach.End
                else -> ScrollReach.Away
            }
        }
    }
    LaunchedEffect(position, paging.more, paging.loading, paging.loaded, paging.fill) {
        val wanted = position == ScrollReach.End || (position == ScrollReach.Short && current.fill)
        if (wanted && current.ready) current.load()
    }
}

@Composable
internal fun FillWhenEmpty(paging: ListPaging?) {
    if (paging == null) return
    val current by rememberUpdatedState(paging)
    LaunchedEffect(paging.more, paging.loading, paging.loaded, paging.fill) {
        if (current.fill && current.ready) current.load()
    }
}

@Composable
internal fun PageLoadingRow() {
    Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
        Text("Loading more...", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

internal fun listPaging(source: SocialSource, snapshot: SocialSnapshot, fill: Boolean, vararg lists: SocialList): ListPaging {
    val next = lists.firstOrNull { it in snapshot.paging.more }
    return ListPaging(
        more = next != null,
        loading = lists.any { it in snapshot.paging.loading },
        loaded = lists.sumOf { snapshot.loaded(it) },
        fill = fill,
    ) { next?.let(source::loadMore) }
}

@Composable
internal fun PersonHead(person: SocialPerson, size: Dp, modifier: Modifier = Modifier) {
    val dot = size / 4
    val circle = OneConfigTheme.current.circleShape
    val invited = person.pendingInvite && LocalSocialLook.current.narrowLists
    Box(modifier.size(size)) {
        PlayerHead(person.seed, size, Modifier, shape = headShape(size))
        if (person.online || invited) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = dot / 3, y = dot / 3)
                    .size(dot)
                    .clip(circle)
                    .background(if (invited) OneConfigTheme.accent else SocialSuccessColor)
                    .border(2.dp, OneConfigTheme.current.componentBackground, circle),
            )
        }
    }
}

@Composable
internal fun ThreadFace(thread: SocialThread, size: Dp) {
    if (thread.group && thread.people.size > 1 && LocalSocialLook.current.groupFace == SocialGroupFace.Grid) {
        SocialGroupAvatar(thread.people.map { it.id } + ChatIdentity.selfId(), size, ringColor = Color.Transparent, grid = true)
        return
    }
    val people = thread.people.take(2)
    val first = people.firstOrNull()
    val second = people.getOrNull(1)
    when {
        first == null -> Box(Modifier.size(size).clip(OneConfigTheme.shape(size / 5)).background(OneConfigTheme.current.componentBackground))
        !thread.group -> PersonHead(first, size)
        second == null -> PlayerHead(first.seed, size, Modifier, shape = headShape(size))
        else -> Box(Modifier.size(size)) {
            PlayerHead(first.seed, size * GROUP_FACE_SCALE, Modifier, shape = headShape(size * GROUP_FACE_SCALE))
            PlayerHead(second.seed, size * GROUP_FACE_SCALE, Modifier.align(Alignment.BottomEnd), shape = headShape(size * GROUP_FACE_SCALE))
        }
    }
}

internal class InboxItem(
    val key: String,
    val section: String,
    val person: SocialPerson,
    val title: String,
    val detail: String,
    val acceptLabel: String?,
    val accept: () -> Unit,
    val dismissLabel: String,
    val dismiss: () -> Unit,
    val brief: String = detail,
    val more: () -> List<MenuAction> = { emptyList() },
) {
    val verb: String?
        get() = acceptLabel?.substringBefore(' ')
}

internal fun inboxItems(snapshot: SocialSnapshot, source: SocialSource): List<InboxItem> =
    inviteItems(snapshot, source) + requestItems(snapshot, source)

internal fun inviteItems(snapshot: SocialSnapshot, source: SocialSource): List<InboxItem> {
    val parties = snapshot.partyInvites.map { party ->
        InboxItem(
            "party-${party.invite.id}",
            "Party invites",
            party.host,
            party.host.name,
            "Party, ${PartyCardText.partySize(party.invite.partySize)}",
            PartyCardText.JOIN_PARTY,
            { PartyRepository.accept(party.invite) },
            PartyCardText.DECLINE,
            { PartyRepository.decline(party.invite) },
        )
    }
    val worlds = snapshot.worlds.map { world ->
        InboxItem(
            "world-${world.id}",
            "Hosted worlds",
            world.host,
            world.name ?: "${world.host.name}'s world",
            if (world.accepted) "You joined this world" else "Invited by ${world.host.name}",
            if (world.accepted) "Re-join world" else "Join world",
            { source.join(world) },
            "Decline",
            { source.decline(world) },
            if (world.accepted) "You joined this world" else listOfNotNull(world.host.name, world.build).joinToString(" · "),
        )
    }
    return parties + worlds
}

internal fun requestItems(snapshot: SocialSnapshot, source: SocialSource): List<InboxItem> =
    snapshot.requests.sortedByDescending { it.incoming }.map { request ->
        InboxItem(
            "request-${request.id}",
            "Friend requests",
            request.person,
            request.person.name,
            if (request.incoming) "Wants to be friends" else "Request sent",
            if (request.incoming) "Accept" else null,
            { source.accept(request) },
            if (request.incoming) "Decline" else "Cancel request",
            { source.dismiss(request) },
            more = { listOf(blockMenuItem(source, request.person)) },
        )
    }

private fun leaderMovedItem(): InboxItem? {
    val card = LeaderFollow.card ?: return null
    val leader = SocialPerson(card.leader, card.name, seedOf(card.leader), realSkin = true, online = true)
    val incompatible = card.check == ServerCheck.Result.INCOMPATIBLE
    return InboxItem(
        "leader-${card.key}",
        "Party",
        leader,
        card.address,
        if (incompatible) PartyCardText.needs(card.needs) else "${card.name} is playing here",
        when {
            incompatible -> null
            card.checking -> PartyCardText.CHECKING
            else -> PartyCardText.JOIN
        },
        LeaderFollow::requestJoin,
        PartyCardText.DISMISS,
        LeaderFollow::dismiss,
    )
}

private fun leaderHostedItem(): InboxItem? {
    val hosted = LeaderFollow.hosted?.takeUnless { it.here || it.dismissed } ?: return null
    val leader = SocialPerson(hosted.leader, hosted.name, seedOf(hosted.leader), realSkin = true, online = true)
    return InboxItem(
        "hosted-${hosted.sessionId}",
        "Party",
        leader,
        PartyCardText.world(hosted.name),
        "${hosted.name} is hosting a world",
        hosted.status ?: PartyCardText.JOIN,
        LeaderFollow::joinHosted,
        PartyCardText.DISMISS,
        LeaderFollow::dismissHosted,
    )
}

private fun playTogetherItem(): InboxItem? {
    val play = PlayTogether.active?.takeUnless { it.leading } ?: return null
    val leader = SocialPerson(play.leader, play.leaderName, seedOf(play.leader), realSkin = true, online = true)
    return InboxItem(
        "play-${play.id}",
        "Party",
        leader,
        destinationLabel(play.destination.kind, play.destination.name),
        "${play.leaderName} started Play together",
        PartyCardText.JOIN.takeIf { PlayTogether.canJoinLate(play) },
        PlayTogether::joinLate,
        "Not now",
        PlayTogether::cancel,
    )
}

internal fun partyMoveItems(): List<InboxItem> {
    val play = PlayTogether.active?.destination?.address
    val moved = leaderMovedItem()?.takeUnless { LeaderFollow.card?.address == play }
    return hostJoinItems() + listOfNotNull(moved, leaderHostedItem(), playTogetherItem())
}

private fun hostJoinItems(): List<InboxItem> = HostJoinRequests.pending.map { request ->
    InboxItem(
        "join-${request.id}",
        "Party",
        SocialPerson(request.player, request.name, seedOf(request.player), realSkin = true, online = true),
        request.name,
        PartyCardText.WANTS_TO_JOIN,
        PartyCardText.ALLOW.takeUnless { request.answering },
        { HostJoinRequests.allow(request.id) },
        PartyCardText.DENY,
        { HostJoinRequests.deny(request.id) },
    )
}

internal fun InboxItem.menuItems(): List<MenuAction> = listOfNotNull(
    acceptLabel?.let { MenuAction(it, accept) },
    MenuAction(dismissLabel, dismiss),
) + more()

internal fun blockMenuItem(name: String, onBlock: () -> Unit): MenuAction = MenuAction("Block", {
    Confirm.ask("Block $name?", "You won't see their messages or requests. You can unblock them later.", "Block", onBlock, icon = LobbyIcons.CLOSE)
}, danger = true, dangerColor = KitDangerColor)

internal fun blockMenuItem(source: SocialSource, person: SocialPerson, onBlocked: () -> Unit = {}): MenuAction = blockMenuItem(person.name) {
    onBlocked()
    source.block(person)
}

internal fun friendMenuItems(state: SocialPageState, person: SocialPerson): List<MenuAction> = listOf(
    MenuAction("Remove friend", {
        Confirm.ask("Remove ${person.name}?", "They will be removed from your friends list.", "Remove friend", {
            state.selected = null
            state.source.remove(person)
        }, icon = LobbyIcons.CLOSE)
    }),
    blockMenuItem(state.source, person) { state.selected = null },
)

internal fun friendshipMenuItem(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson): MenuAction {
    val request = snapshot.requestWith(person.id)
    return when {
        request == null -> MenuAction("Add friend", { state.source.addFriend(person) }, icon = SOCIAL_ASSETS + "user-plus-01.svg")
        request.incoming -> MenuAction("Accept friend request", { state.source.accept(request) }, icon = SOCIAL_ASSETS + "user-plus-01.svg")
        else -> MenuAction("Friend request sent", {}, icon = SOCIAL_ASSETS + "user-plus-01.svg", enabled = false)
    }
}

internal fun personMenuItems(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson): List<MenuAction> = when {
    snapshot.isBlocked(person.id) -> blockedMenuItems(state, person)
    snapshot.isFriend(person.id) -> listOfNotNull(
        partyInviteItem(person.id).takeIf { person.online },
        worldInviteItem(state, person),
    ) + friendMenuItems(state, person)
    else -> listOf(friendshipMenuItem(state, snapshot, person), blockMenuItem(state.source, person))
}

internal fun worldInviteItem(state: SocialPageState, person: SocialPerson, hosting: Boolean = P2PSessionManager.currentSessionId != null): MenuAction? {
    if (!hosting) return null
    return MenuAction(
        if (person.worldInvited) "Invited to world" else "Invite to world",
        { state.source.inviteToWorld(person) },
        icon = SOCIAL_ASSETS + "globe-01.svg",
        enabled = !person.worldInvited,
    )
}

internal fun friendRowItems(state: SocialPageState, person: SocialPerson): List<MenuAction> = listOfNotNull(
    MenuAction("Message", { state.source.message(person) }, icon = SOCIAL_ASSETS + "message-chat-circle.svg"),
    partyInviteItem(person.id).takeIf { person.online },
    worldInviteItem(state, person),
) + friendMenuItems(state, person)

@Composable
internal fun FriendMenu(state: SocialPageState, person: SocialPerson) {
    AnchoredPopupButton(LobbyIcons.MORE, "More") { close -> LobbyUserTheme { MenuList(friendMenuItems(state, person), close) } }
}

@Composable
internal fun WorldAction(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson, modifier: Modifier = Modifier, filled: Boolean = false) {
    val item = worldInviteItem(state, person, snapshot.hosting != null) ?: return
    val icon = item.icon.takeUnless { filled }
    val style = if (filled) ControlStyle.Primary else ControlStyle.Secondary
    if (icon != null) {
        TextButton(text = item.label, icon = icon, onClick = item.onClick, modifier = modifier, style = style, enabled = item.enabled, size = ControlSize.Large)
    } else {
        TextButton(text = item.label, onClick = item.onClick, modifier = modifier, style = style, enabled = item.enabled, size = ControlSize.Large)
    }
}

@Composable
internal fun FriendDetailPanel(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson, modifier: Modifier) {
    val groups = snapshot.allThreads.filter { thread -> thread.group && thread.people.any { it.id == person.id } }
    Column(modifier.socialSurface().padding(LocalSocialLook.current.paneEdge), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonHead(person, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelTitle(person.name)
                Text(person.status, color = if (person.online) SocialTextPrimary else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            FriendMenu(state, person)
            IconButton(icon = LobbyIcons.CLOSE, contentDescription = "Close", onClick = { state.selected = null }, modifier = Modifier.size(34.dp), tint = OneConfigTheme.current.textColor, tooltipPlacement = TooltipPlacement.BelowStart)
        }
        person.since?.let { Text("Friends since $it", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        TextButton(
            text = "Message",
            icon = SOCIAL_ASSETS + "message-chat-circle.svg",
            onClick = { state.source.message(person) },
            modifier = Modifier.fillMaxWidth(),
            style = ControlStyle.Primary,
            enabled = true,
            size = ControlSize.Large,
        )
        WorldAction(state, snapshot, person, Modifier.fillMaxWidth())
        if (person.online) PartyInvitePrompt(person.id, Modifier.fillMaxWidth())
        if (groups.isNotEmpty()) SectionHeader("Shared groups")
        ScrollFade(Modifier.weight(1f).fillMaxWidth(), 6.dp) {
            groups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThreadFace(group, 24.dp)
                    Text(group.title, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
            }
        }
    }
}

@Composable
internal fun NoFriendsMessage(state: SocialPageState, modifier: Modifier) {
    EmptyState("No friends yet", modifier.fillMaxSize(), text = "Add a friend by username to see when they are online.") {
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
