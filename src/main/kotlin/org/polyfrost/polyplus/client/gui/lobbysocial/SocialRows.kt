@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SelectOpenStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectValueAlignment
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialEmojiText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.social.SpecialChatResponses
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState

private val RowHeight = 48.dp
private val NarrowRowHeight = 58.dp
private val NameColumn = 190.dp

@Composable
internal fun ReplyFilterButton(filter: SpecialChatResponses.Filter, scanning: Boolean, onClick: () -> Unit) {
    val active = filter != SpecialChatResponses.Filter.All
    IconButton(
        icon = SOCIAL_ASSETS + if (scanning) "loading-02.svg" else "check-circle.svg",
        contentDescription = when {
            scanning -> "Checking replies..."
            filter == SpecialChatResponses.Filter.Awaiting -> "Showing chats awaiting a reply"
            filter == SpecialChatResponses.Filter.Responded -> "Showing chats that replied"
            else -> "Filter by whether they replied"
        },
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        selected = active,
        tint = if (active) OneConfigTheme.current.accentTextColor else SocialTextPrimary,
        tooltipPlacement = TooltipPlacement.BelowStart,
    )
}

@Composable
internal fun ChatAsPicker(state: SocialPageState, snapshot: SocialSnapshot) {
    Select(
        selected = snapshot.delegations.firstOrNull { it.id == snapshot.actAs },
        options = listOf<SocialPerson?>(null) + snapshot.delegations,
        onSelect = { state.source.chatAs(it?.id) },
        modifier = Modifier.fillMaxWidth(),
        label = { it?.name ?: ClientPlatform.localPlayerName() },
        size = ControlSize.Large,
        leadingLabel = "Chat as",
        valueAlignment = SelectValueAlignment.End,
        openStyle = SelectOpenStyle.Accent,
    )
}

@Composable
internal fun ThreadList(state: SocialPageState, list: SocialListState, snapshot: SocialSnapshot, modifier: Modifier) {
    val activeId = SocialChatLink.active?.id
    val threads = list.threads(snapshot, activeId)
    val paging = list.paging(state.source, snapshot)
    if (threads.isEmpty()) {
        FillWhenEmpty(paging)
        if (LocalSocialLook.current.narrowLists) {
            Box(modifier, contentAlignment = Alignment.Center) {
                Text(list.emptyTitle(snapshot), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body)
            }
        } else {
            EmptyState(list.emptyTitle(snapshot), modifier)
        }
        return
    }
    ScrollFade(modifier, 2.dp, paging = paging) {
        threads.forEach { thread ->
            ThreadRow(thread, activeId == thread.id, { threadMenuItems(state, snapshot, thread) }) { state.source.open(thread) }
        }
    }
}

@Composable
internal fun ThreadRow(thread: SocialThread, selected: Boolean, menu: () -> List<MenuAction> = { emptyList() }, onClick: () -> Unit) {
    val narrow = LocalSocialLook.current.narrowLists
    val theme = OneConfigTheme.current
    val inkDot = socialRowFilled(selected)
    val ink = SelectionDefaults.itemContentColor(inkDot, ItemSelectionStyle.Accent)
    val muted = SelectionDefaults.itemSecondaryColor(inkDot, ItemSelectionStyle.Accent, theme.mutedText)
    val dot: @Composable () -> Unit = {
        if (thread.unread) {
            val base = Modifier.size(8.dp).clip(theme.circleShape)
            Box(if (inkDot) base.background(ink) else base.background(OneConfigTheme.accent))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .contextMenu(entries = menu)
            .socialSelectableRow(selected, onClick, if (thread.unread) "${thread.title}, unread" else thread.title)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThreadFace(thread, 38.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(thread.title, modifier = Modifier.weight(1f), color = ink, style = OneConfigTheme.current.typography.body.copy(fontSize = if (narrow) 16.sp else 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                threadTime(thread)?.let { Text(it, color = muted, style = OneConfigTheme.current.typography.body.copy(fontSize = if (narrow) 12.sp else 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
                if (!narrow) dot()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SocialEmojiText(
                    (if (narrow) thread.brief else thread.preview) ?: "No messages yet",
                    if (narrow) 14.sp else 12.sp,
                    Modifier.weight(1f),
                    color = muted,
                    maxLines = 1,
                )
                if (narrow) dot()
            }
        }
    }
}

@Composable
internal fun Roster(state: SocialPageState, people: List<SocialPerson>, modifier: Modifier, paging: ListPaging? = null) {
    if (people.isEmpty()) {
        FillWhenEmpty(paging)
        EmptyState("No friends match that filter", modifier)
        return
    }
    ScrollFade(modifier.padding(horizontal = 12.dp), 0.dp, paging = paging) { RosterSections(state, people) }
}

@Composable
internal fun RosterSections(state: SocialPageState, people: List<SocialPerson>) {
    rosterSections(people).forEach { (title, members) ->
        SectionHeader("$title ${members.size}", Modifier.padding(start = 10.dp, top = 12.dp, bottom = 6.dp))
        members.forEach { RosterRow(state, it) }
    }
}

@Composable
internal fun RosterRow(state: SocialPageState, person: SocialPerson) {
    val hover = rememberHoverState()
    val selected = state.selected == person.id
    val narrow = LocalSocialLook.current.narrowLists
    val theme = OneConfigTheme.current
    Row(
        socialHover(person.name)
            .fillMaxWidth()
            .height(if (narrow) NarrowRowHeight else RowHeight)
            .contextMenu { friendRowItems(state, person) }
            .hoverable(hover.interactionSource)
            .socialSelectableRow(selected, { state.toggle(person) }, "${person.name}, ${person.status}")
            .padding(horizontal = if (narrow) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (narrow) 10.dp else 12.dp),
    ) {
        val filled = socialRowFilled(selected)
        val muted = SelectionDefaults.itemSecondaryColor(filled, ItemSelectionStyle.Accent, theme.mutedText)
        val nameInk = if (person.online) SelectionDefaults.itemContentColor(filled, ItemSelectionStyle.Accent) else muted
        if (narrow) {
            PersonHead(person, 38.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(person.name, color = nameInk, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(person.status, color = muted, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        } else {
            PersonHead(person, 32.dp)
            Text(person.name, modifier = Modifier.width(NameColumn), color = nameInk, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(person.status, modifier = Modifier.weight(1f), color = muted, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (hover.hovered || selected) RowActions(state, person, SelectionDefaults.itemContentColor(filled, ItemSelectionStyle.Accent))
    }
}

@Composable
private fun RowActions(state: SocialPageState, person: SocialPerson, ink: Color) {
    IconButton(
        icon = SOCIAL_ASSETS + "message-chat-circle.svg",
        contentDescription = "Message",
        onClick = { state.source.message(person) },
        modifier = Modifier.size(34.dp),
        tint = ink,
        tooltipPlacement = TooltipPlacement.BelowStart,
    )
}

@Composable
internal fun RequestList(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val requests = snapshot.requests.filter { state.matches(it.person) }.sortedByDescending { it.incoming }
    val paging = state.paging(snapshot, true, SocialList.Incoming, SocialList.Outgoing)
    if (requests.isEmpty()) {
        FillWhenEmpty(paging)
        EmptyState(if (state.query.isBlank()) "No friend requests" else "No requests match that search", modifier)
        return
    }
    ScrollFade(modifier.padding(12.dp), 0.dp, paging = paging) {
        requests.forEach { request ->
            Row(
                Modifier.fillMaxWidth().height(RowHeight + 8.dp).contextMenu { requestMenuItems(state, request) + blockMenuItem(state.source, request.person) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonHead(request.person, 32.dp)
                Text(request.person.name, modifier = Modifier.width(NameColumn), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(if (request.incoming) "Wants to be friends" else "Request sent", modifier = Modifier.weight(1f), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                RequestActions(state, request)
            }
        }
    }
}

internal fun requestMenuItems(state: SocialPageState, request: SocialRequest): List<MenuAction> = if (request.incoming) {
    listOf(MenuAction("Accept", { state.source.accept(request) }), MenuAction("Decline", { state.source.dismiss(request) }))
} else {
    listOf(MenuAction("Cancel request", { state.source.dismiss(request) }))
}

internal fun blockedMenuItems(state: SocialPageState, person: SocialPerson): List<MenuAction> =
    listOf(MenuAction("Unblock", { state.source.unblock(person) }))

@Composable
private fun RowScope.RequestActions(state: SocialPageState, request: SocialRequest) {
    requestMenuItems(state, request).forEachIndexed { index, item ->
        TextButton(
            text = item.label,
            onClick = item.onClick,
            modifier = Modifier,
            style = if (request.incoming && index == 0) ControlStyle.Primary else ControlStyle.Secondary,
            size = ControlSize.Large,
        )
    }
}

@Composable
internal fun BlockedList(state: SocialPageState, snapshot: SocialSnapshot, modifier: Modifier) {
    val blocked = snapshot.blocked.filter(state::matches)
    val paging = state.paging(snapshot, true, SocialList.Blocked)
    if (blocked.isEmpty()) {
        FillWhenEmpty(paging)
        EmptyState(if (state.query.isBlank()) "You haven't blocked anyone" else "No blocked players match that search", modifier)
        return
    }
    ScrollFade(modifier.padding(12.dp), 0.dp, paging = paging) {
        blocked.forEach { person ->
            Row(
                Modifier.fillMaxWidth().height(RowHeight + 8.dp).contextMenu { blockedMenuItems(state, person) }.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PersonHead(person, 32.dp)
                Text(person.name, modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                blockedMenuItems(state, person).forEach { TextButton(text = it.label, onClick = it.onClick, modifier = Modifier, size = ControlSize.Large) }
            }
        }
    }
}

@Composable
internal fun InboxLine(item: InboxItem) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).contextMenu(entries = item::menuItems),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PersonHead(item.person, 32.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(item.detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        item.acceptLabel?.let { label ->
            IconButton(
                icon = LobbyIcons.TICK,
                contentDescription = label,
                onClick = item.accept,
                modifier = Modifier.size(34.dp),
                style = IconButtonStyle.Filled,
                tooltipPlacement = TooltipPlacement.BelowStart,
            )
        }
        IconButton(icon = LobbyIcons.CLOSE, contentDescription = item.dismissLabel, onClick = item.dismiss, modifier = Modifier.size(34.dp), tint = OneConfigTheme.current.textColor, tooltipPlacement = TooltipPlacement.BelowStart)
    }
}
