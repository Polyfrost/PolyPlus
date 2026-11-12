@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.components.fadingEdges
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.ConfirmPrompt
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.destinationLabel
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.party.HostJoinRequests
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PlayTogether
import org.polyfrost.polyplus.client.party.ServerCheck
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager

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
    Column(
        bounds.fadingEdges(scroll, ink, FADE_HEIGHT.dp).verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
        if (paging?.loading == true) PageLoadingRow()
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
        Label("Loading more...", 12.sp, color = MutedText)
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
internal fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Label(text.uppercase(), 11.sp, modifier, color = MutedText, weight = FontWeight.Medium)
}

@Composable
internal fun PersonHead(person: SocialPerson, size: Dp, modifier: Modifier = Modifier) {
    val dot = size / 4
    val circle = OneConfigTheme.current.circleShape
    val invited = person.pendingInvite && LocalSocialLook.current.narrowLists
    Box(modifier.size(size)) {
        PlayerHead(person.seed, size, Modifier, headShape(size))
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
        first == null -> Box(Modifier.size(size).clip(ppShape(size / 5)).background(OneConfigTheme.current.componentBackground))
        !thread.group -> PersonHead(first, size)
        second == null -> PlayerHead(first.seed, size, Modifier, headShape(size))
        else -> Box(Modifier.size(size)) {
            PlayerHead(first.seed, size * GROUP_FACE_SCALE, Modifier, headShape(size * GROUP_FACE_SCALE))
            PlayerHead(second.seed, size * GROUP_FACE_SCALE, Modifier.align(Alignment.BottomEnd), headShape(size * GROUP_FACE_SCALE))
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
    acceptLabel?.let { MenuAction(it, onClick = accept) },
    MenuAction(dismissLabel, onClick = dismiss),
) + more()

internal fun blockMenuItem(name: String, onBlock: () -> Unit): MenuAction = MenuAction("Block", danger = true) {
    ConfirmPrompt.ask("Block $name?", "You won't see their messages or requests. You can unblock them later.", "Block", SOCIAL_ASSETS + "x-close.svg", onBlock)
}

internal fun blockMenuItem(source: SocialSource, person: SocialPerson, onBlocked: () -> Unit = {}): MenuAction = blockMenuItem(person.name) {
    onBlocked()
    source.block(person)
}

internal fun friendMenuItems(state: SocialPageState, person: SocialPerson): List<MenuAction> = listOf(
    MenuAction("Remove friend") {
        ConfirmPrompt.ask("Remove ${person.name}?", "They will be removed from your friends list.", "Remove friend", SOCIAL_ASSETS + "x-close.svg") {
            state.selected = null
            state.source.remove(person)
        }
    },
    blockMenuItem(state.source, person) { state.selected = null },
)

internal fun friendshipMenuItem(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson): MenuAction {
    val request = snapshot.requestWith(person.id)
    return when {
        request == null -> MenuAction("Add friend", SOCIAL_ASSETS + "user-plus-01.svg") { state.source.addFriend(person) }
        request.incoming -> MenuAction("Accept friend request", SOCIAL_ASSETS + "user-plus-01.svg") { state.source.accept(request) }
        else -> MenuAction("Friend request sent", SOCIAL_ASSETS + "user-plus-01.svg", enabled = false) {}
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
        SOCIAL_ASSETS + "globe-01.svg",
        enabled = !person.worldInvited,
    ) { state.source.inviteToWorld(person) }
}

internal fun friendRowItems(state: SocialPageState, person: SocialPerson): List<MenuAction> = listOfNotNull(
    MenuAction("Message", SOCIAL_ASSETS + "message-chat-circle.svg") { state.source.message(person) },
    partyInviteItem(person.id).takeIf { person.online },
    worldInviteItem(state, person),
) + friendMenuItems(state, person)

@Composable
internal fun FriendMenu(state: SocialPageState, person: SocialPerson) {
    AnchoredPopupButton(themedIcon(LobbyIcons.MORE), "More") { close -> LobbyUserTheme { MenuList(friendMenuItems(state, person), close) } }
}

@Composable
internal fun WorldAction(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson, modifier: Modifier = Modifier, filled: Boolean = false) {
    val item = worldInviteItem(state, person, snapshot.hosting != null) ?: return
    TextButton(
        item.label,
        item.icon.takeUnless { filled }?.let { themedIcon(it) },
        modifier.height(38.dp),
        if (filled) ButtonStyle.Primary else ButtonStyle.Secondary,
        item.enabled,
        item.onClick,
    )
}

@Composable
internal fun FriendDetailPanel(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson, modifier: Modifier) {
    val groups = snapshot.allThreads.filter { thread -> thread.group && thread.people.any { it.id == person.id } }
    Column(modifier.socialSurface().padding(LocalSocialLook.current.paneEdge), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonHead(person, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelTitle(person.name)
                Label(person.status, 14.sp, color = if (person.online) SocialTextPrimary else MutedText)
            }
            FriendMenu(state, person)
            IconButton(themedIcon(LobbyIcons.CLOSE), "Close", Modifier) { state.selected = null }
        }
        person.since?.let { Label("Friends since $it", 12.sp, color = MutedText) }
        TextButton(
            "Message",
            themedIcon(SOCIAL_ASSETS + "message-chat-circle.svg"),
            Modifier.fillMaxWidth().height(38.dp),
            ButtonStyle.Primary,
            true,
        ) { state.source.message(person) }
        WorldAction(state, snapshot, person, Modifier.fillMaxWidth())
        if (person.online) PartyInvitePrompt(person.id, Modifier.fillMaxWidth())
        if (groups.isNotEmpty()) SectionHeading("Shared groups")
        ScrollFade(Modifier.weight(1f).fillMaxWidth(), 6.dp) {
            groups.forEach { group ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThreadFace(group, 24.dp)
                    Label(group.title, 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun NoFriendsMessage(state: SocialPageState, modifier: Modifier) {
    EmptyState("No friends yet", modifier.fillMaxSize(), "Add a friend by username to see when they are online.") {
        TextButton(
            "Add friend",
            themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"),
            Modifier.height(38.dp),
            ButtonStyle.Primary,
            true,
        ) { state.dialog = SocialDialog.AddFriend }
    }
}
