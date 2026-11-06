@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialMenuItem
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.Head
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.destinationLabel
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PlayTogether
import org.polyfrost.polyplus.client.party.ServerCheck
import org.polyfrost.polyplus.client.social.ChatIdentity

private const val GROUP_FACE_SCALE = 0.68f
private const val FADE_HEIGHT = 32

internal val MENU_OFFSET = 40.dp
private val MENU_WIDTH = 170.dp

@Composable
internal fun ScrollFade(modifier: Modifier, spacing: Dp = 6.dp, content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    val framed = LocalSocialLook.current.surface == SocialSurface.None
    val ink = if (framed) PanelSurface else OneConfigTheme.current.pageBackground
    val clear = if (framed) ink.copy(alpha = 0f) else Color.Transparent
    Box(modifier) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
        if (scroll.canScrollBackward) {
            Box(Modifier.fillMaxWidth().height(FADE_HEIGHT.dp).background(Brush.verticalGradient(listOf(ink, clear))))
        }
        if (scroll.canScrollForward) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(FADE_HEIGHT.dp).background(Brush.verticalGradient(listOf(clear, ink))))
        }
    }
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
        Head(person.seed, size, realSkin = person.realSkin)
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
        second == null -> Head(first.seed, size, realSkin = first.realSkin)
        else -> Box(Modifier.size(size)) {
            Head(first.seed, size * GROUP_FACE_SCALE, realSkin = first.realSkin)
            Head(second.seed, size * GROUP_FACE_SCALE, Modifier.align(Alignment.BottomEnd), realSkin = second.realSkin)
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
            "Party, ${party.invite.partySize} of ${PartyRepository.MAX_MEMBERS}",
            "Join party",
            { PartyRepository.accept(party.invite) },
            "Decline",
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
        if (incompatible) card.needs?.let { "Needs Minecraft $it" } ?: "Needs another Minecraft version" else "${card.name} is playing here",
        when {
            incompatible -> null
            card.checking -> "Checking..."
            else -> "Join"
        },
        LeaderFollow::requestJoin,
        "Dismiss",
        LeaderFollow::dismiss,
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
        "Join".takeIf { PlayTogether.canJoinLate(play) },
        PlayTogether::joinLate,
        "Not now",
        PlayTogether::cancel,
    )
}

internal fun partyMoveItems(): List<InboxItem> {
    val play = PlayTogether.active?.destination?.address
    val moved = leaderMovedItem()?.takeUnless { LeaderFollow.card?.address == play }
    return listOfNotNull(moved, playTogetherItem())
}

@Composable
internal fun FriendMenu(state: SocialPageState, person: SocialPerson) {
    var open by remember { mutableStateOf(false) }
    val drop = with(LocalDensity.current) { MENU_OFFSET.roundToPx() }
    Box {
        SocialIconButton(LobbyIcons.MORE, tooltip = "More") { open = !open }
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, drop),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    Modifier.width(MENU_WIDTH).popup().padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SocialMenuItem("Remove friend") {
                        open = false
                        state.selected = null
                        state.source.remove(person)
                    }
                    SocialMenuItem("Block", color = SocialDangerText) {
                        open = false
                        state.selected = null
                        state.source.block(person)
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorldAction(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson, modifier: Modifier = Modifier, filled: Boolean = false) {
    if (snapshot.hosting == null) return
    SocialButton(
        if (person.worldInvited) "Invited to world" else "Invite to world",
        icon = (SOCIAL_ASSETS + "globe-01.svg").takeUnless { filled },
        modifier = modifier,
        filled = filled,
        enabled = !person.worldInvited,
    ) { state.source.inviteToWorld(person) }
}

@Composable
internal fun FriendDetailPanel(state: SocialPageState, snapshot: SocialSnapshot, person: SocialPerson, modifier: Modifier) {
    val groups = snapshot.threads.filter { thread -> thread.group && thread.people.any { it.id == person.id } }
    Column(modifier.socialSurface().padding(LocalSocialLook.current.paneEdge), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonHead(person, 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PanelTitle(person.name)
                Label(person.status, 14.sp, color = if (person.online) SocialTextPrimary else MutedText)
            }
            FriendMenu(state, person)
            SocialIconButton(LobbyIcons.CLOSE, tooltip = "Close") { state.selected = null }
        }
        person.since?.let { Label("Friends since $it", 12.sp, color = MutedText) }
        SocialButton(
            "Message",
            icon = SOCIAL_ASSETS + "message-chat-circle.svg",
            modifier = Modifier.fillMaxWidth(),
            filled = true,
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
        SocialButton(
            "Add friend",
            icon = SOCIAL_ASSETS + "user-plus-01.svg",
            filled = true,
            onClick = { state.dialog = SocialDialog.AddFriend },
        )
    }
}
