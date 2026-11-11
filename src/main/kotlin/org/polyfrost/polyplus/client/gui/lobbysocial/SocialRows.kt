@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialDropdown
import org.polyfrost.polyplus.client.gui.SocialEmojiText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.social.SpecialChatResponses
import org.polyfrost.polyplus.client.utils.ClientPlatform

private val RowHeight = 48.dp
private val NarrowRowHeight = 58.dp
private val NameColumn = 190.dp

@Composable
internal fun ReplyFilterButton(filter: SpecialChatResponses.Filter, scanning: Boolean, onClick: () -> Unit) {
    IconButton(
        themedIcon(SOCIAL_ASSETS + if (scanning) "loading-02.svg" else "check-circle.svg"),
        when {
            scanning -> "Checking replies..."
            filter == SpecialChatResponses.Filter.Awaiting -> "Showing chats awaiting a reply"
            filter == SpecialChatResponses.Filter.Responded -> "Showing chats that replied"
            else -> "Filter by whether they replied"
        },
        null,
        Modifier,
        if (filter != SpecialChatResponses.Filter.All) OneConfigTheme.accent else null,
        if (filter != SpecialChatResponses.Filter.All) OneConfigTheme.current.accentTextColor else SocialTextPrimary,
        TooltipPlacement.BelowStart,
        onClick,
    )
}

@Composable
internal fun ChatAsPicker(state: SocialPageState, snapshot: SocialSnapshot) {
    val open = remember { mutableStateOf(false) }
    if (open.value) EscapeCloses { open.value = false }
    SocialDropdown(
        label = "Chat as",
        options = listOf<SocialPerson?>(null) + snapshot.delegations,
        selected = snapshot.delegations.firstOrNull { it.id == snapshot.actAs },
        labelFor = { it?.name ?: ClientPlatform.localPlayerName() },
        modifier = Modifier.fillMaxWidth(),
        open = open,
        onSelect = { state.source.chatAs(it?.id) },
    )
}

@Composable
internal fun ThreadList(state: SocialPageState, list: SocialListState, snapshot: SocialSnapshot, modifier: Modifier) {
    val activeId = SocialChatLink.active?.id
    val threads = list.threads(snapshot, activeId)
    if (threads.isEmpty()) {
        if (LocalSocialLook.current.narrowLists) {
            Box(modifier, contentAlignment = Alignment.Center) { Label(list.emptyTitle(snapshot), 14.sp, color = MutedText) }
        } else {
            EmptyState(list.emptyTitle(snapshot), modifier)
        }
        return
    }
    ScrollFade(modifier, 2.dp) {
        threads.forEach { thread ->
            ThreadRow(thread, activeId == thread.id, { threadMenuItems(state, thread) }) { state.source.open(thread) }
        }
    }
}

@Composable
internal fun ThreadRow(thread: SocialThread, selected: Boolean, menu: () -> List<MenuAction> = { emptyList() }, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val narrow = LocalSocialLook.current.narrowLists
    val dot: @Composable () -> Unit = {
        if (thread.unread) Box(Modifier.size(8.dp).clip(OneConfigTheme.current.circleShape).background(if (socialRowFilled(selected)) socialRowInk(true) else OneConfigTheme.accent))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .socialRow(selected, hovered)
            .contextMenu(menu)
            .hoverable(interaction)
            .clickableWithSound(if (thread.unread) "${thread.title}, unread" else thread.title, selected = selected.takeIf { it }, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThreadFace(thread, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(thread.title, if (narrow) 16.sp else 14.sp, Modifier.weight(1f), color = socialRowInk(selected), weight = FontWeight.Medium)
                threadTime(thread)?.let { Label(it, if (narrow) 12.sp else 11.sp, color = socialRowMuted(selected)) }
                if (!narrow) dot()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SocialEmojiText(
                    (if (narrow) thread.brief else thread.preview) ?: "No messages yet",
                    if (narrow) 14.sp else 12.sp,
                    Modifier.weight(1f),
                    color = socialRowMuted(selected),
                    maxLines = 1,
                )
                if (narrow) dot()
            }
        }
    }
}

@Composable
internal fun Roster(state: SocialPageState, people: List<SocialPerson>, modifier: Modifier) {
    if (people.isEmpty()) {
        EmptyState("No friends match that filter", modifier)
        return
    }
    ScrollFade(modifier.padding(horizontal = 12.dp), 0.dp) { RosterSections(state, people) }
}

@Composable
internal fun RosterSections(state: SocialPageState, people: List<SocialPerson>) {
    rosterSections(people).forEach { (title, members) ->
        SectionHeading("$title ${members.size}", Modifier.padding(start = 10.dp, top = 12.dp, bottom = 6.dp))
        members.forEach { RosterRow(state, it) }
    }
}

@Composable
internal fun RosterRow(state: SocialPageState, person: SocialPerson) {
    val (interaction, hovered) = rememberSocialHover()
    val selected = state.selected == person.id
    val narrow = LocalSocialLook.current.narrowLists
    Row(
        socialHover(person.name)
            .fillMaxWidth()
            .height(if (narrow) NarrowRowHeight else RowHeight)
            .socialRow(selected, hovered)
            .contextMenu { friendRowItems(state, person) }
            .hoverable(interaction)
            .clickableWithSound("${person.name}, ${person.status}", selected = selected.takeIf { it }) { state.toggle(person) }
            .padding(horizontal = if (narrow) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 12.dp),
    ) {
        val nameInk = if (person.online) socialRowInk(selected) else socialRowMuted(selected)
        if (narrow) {
            PersonHead(person, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(person.name, 16.sp, color = nameInk, weight = FontWeight.Medium)
                Label(person.status, 14.sp, color = socialRowMuted(selected))
            }
        } else {
            PersonHead(person, 32.dp)
            Label(person.name, 14.sp, Modifier.width(NameColumn), color = nameInk, weight = FontWeight.Medium)
            Label(person.status, 12.sp, Modifier.weight(1f), color = socialRowMuted(selected))
        }
        if (hovered || selected) RowActions(state, person, socialRowInk(selected))
    }
}

@Composable
private fun RowActions(state: SocialPageState, person: SocialPerson, ink: Color) {
    IconButton(themedIcon(SOCIAL_ASSETS + "message-chat-circle.svg"), "Message", null, Modifier, null, ink, TooltipPlacement.BelowStart) { state.source.message(person) }
}

@Composable
internal fun RequestList(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val requests = snapshot.requests.filter { state.matches(it.person) }.sortedByDescending { it.incoming }
    if (requests.isEmpty()) {
        EmptyState(if (state.query.isBlank()) "No friend requests" else "No requests match that search", modifier)
        return
    }
    ScrollFade(modifier.padding(12.dp), 0.dp) {
        requests.forEach { request ->
            Row(
                Modifier.fillMaxWidth().height(RowHeight + 8.dp).contextMenu { requestMenuItems(state, request) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonHead(request.person, 32.dp)
                Label(request.person.name, 14.sp, Modifier.width(NameColumn), weight = FontWeight.Medium)
                Label(if (request.incoming) "Wants to be friends" else "Request sent", 12.sp, Modifier.weight(1f), color = MutedText)
                RequestActions(state, request)
            }
        }
    }
}

internal fun requestMenuItems(state: SocialPageState, request: SocialRequest): List<MenuAction> = if (request.incoming) {
    listOf(MenuAction("Accept") { state.source.accept(request) }, MenuAction("Decline") { state.source.dismiss(request) })
} else {
    listOf(MenuAction("Cancel request") { state.source.dismiss(request) })
}

internal fun blockedMenuItems(state: SocialPageState, person: SocialPerson): List<MenuAction> =
    listOf(MenuAction("Unblock") { state.source.unblock(person) })

@Composable
private fun RowScope.RequestActions(state: SocialPageState, request: SocialRequest) {
    requestMenuItems(state, request).forEachIndexed { index, item ->
        TextButton(item.label, Modifier.height(38.dp), if (request.incoming && index == 0) ButtonStyle.Primary else ButtonStyle.Secondary, item.onClick)
    }
}

@Composable
internal fun BlockedList(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val blocked = snapshot.blocked.filter(state::matches)
    if (blocked.isEmpty()) {
        EmptyState(if (state.query.isBlank()) "You haven't blocked anyone" else "No blocked players match that search", modifier)
        return
    }
    ScrollFade(modifier.padding(12.dp), 0.dp) {
        blocked.forEach { person ->
            Row(
                Modifier.fillMaxWidth().height(RowHeight + 8.dp).contextMenu { blockedMenuItems(state, person) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonHead(person, 32.dp)
                Label(person.name, 14.sp, Modifier.weight(1f), weight = FontWeight.Medium)
                blockedMenuItems(state, person).forEach { TextButton(it.label, Modifier.height(38.dp), it.onClick) }
            }
        }
    }
}

@Composable
internal fun InboxLine(item: InboxItem) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).contextMenu(item::menuItems),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PersonHead(item.person, 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Label(item.title, 14.sp, weight = FontWeight.Medium)
            Label(item.detail, 12.sp, color = MutedText)
        }
        item.acceptLabel?.let { label ->
            IconButton(
                themedIcon(LobbyIcons.TICK),
                label,
                null,
                Modifier,
                OneConfigTheme.accent,
                OneConfigTheme.current.accentTextColor,
                TooltipPlacement.BelowStart,
                item.accept,
            )
        }
        IconButton(themedIcon(LobbyIcons.CLOSE), item.dismissLabel, Modifier, item.dismiss)
    }
}
