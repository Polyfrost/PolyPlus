@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSurface
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.emoji.EmojiRegistry
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialTimeline
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupMessageSessionInvite
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

private const val CHAT_VISIBILITY_POLL_MILLIS = 250L

private val TimelineBottomSlop = 12.dp

internal val LocalComposerFocus = staticCompositionLocalOf<FocusRequester?> { null }

@Composable
internal fun rememberCooldownRemaining(cooldownUntil: String): Long {
    var remaining by remember(cooldownUntil) { mutableStateOf(secondsUntil(cooldownUntil)) }
    LaunchedEffect(cooldownUntil) {
        while (true) {
            remaining = secondsUntil(cooldownUntil)
            if (remaining <= 0) break
            delay(1000)
        }
    }
    return remaining
}

private fun secondsUntil(instant: String): Long = runCatching {
    Duration.between(Instant.now(), Instant.parse(instant)).seconds
}.getOrDefault(0L).coerceAtLeast(0L)

internal fun formatCooldown(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

@Composable
internal fun WorldInviteBanner(invite: SessionInvite) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(SocialPanelShape)
            .background(SocialWarnColor.copy(alpha = 0.12f))
            .border(SocialBorderWidth, SocialWarnColor, SocialPanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(invite.sender, 32.dp)
        Column(Modifier.weight(1f)) {
            SocialText("${PlayerNamesRepository.displayName(invite.sender)} invited you to their world", fontSize = 13.sp)
            SocialText("Session invite", fontSize = 11.sp, color = SocialTextSecondary)
        }
        TextButton("Decline", Modifier.height(34.dp)) { SessionsRepository.decline(invite) }
        TextButton("Join", themedIcon(SOCIAL_ASSETS + "log-in-04.svg"), Modifier.height(34.dp), ButtonStyle.Primary, true) { SessionsRepository.accept(invite) }
    }
}

@Composable
internal fun GroupOverflowMenu(
    onInvite: () -> Unit,
    onInviteToSession: () -> Unit,
    onRename: (() -> Unit)?,
    onMute: () -> Unit,
    onLeave: () -> Unit,
    onConvertToNormal: () -> Unit,
    muted: Boolean,
    canConvertToNormal: Boolean,
    canLeave: Boolean,
) {
    MenuSurface(Modifier.width(200.dp)) {
        MenuItem("Invite Friends", themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null, onInvite)
        MenuItem("Invite Group to Session", themedIcon(SOCIAL_ASSETS + "log-in-04.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null, onInviteToSession)
        if (onRename != null) MenuItem("Rename Group", themedIcon(SOCIAL_ASSETS + "edit-02.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null, onRename)
        MenuItem(if (muted) "Unmute Group" else "Mute Group", themedIcon(SOCIAL_ASSETS + "volume-x.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null, onMute)
        if (canConvertToNormal) {
            MenuItem("Convert to Normal Chat", themedIcon(SOCIAL_ASSETS + "check.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null, onConvertToNormal)
        }
        if (canLeave) {
            MenuItem("Leave Group", themedIcon(SOCIAL_ASSETS + "x-close.svg"), null, MenuItemSize.Regular, true, false, true, null, Modifier, null, onLeave)
        }
    }
}

@Composable
internal fun MessageTimeline(
    modifier: Modifier,
    messages: List<GroupMessage>,
    selfId: String,
    conversationId: Int,
    incomingInvites: List<SessionInvite>,
    isGroup: Boolean,
) {
    if (messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SocialText("No messages yet. Say hello!", fontSize = 13.sp, color = SocialTextSecondary)
        }
        return
    }
    val scrollState = remember(conversationId) { ScrollState(0) }
    var pinned by remember(conversationId) { mutableStateOf(true) }
    val bottomSlop = with(LocalDensity.current) { TimelineBottomSlop.roundToPx() }
    val maxScroll = scrollState.maxValue
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.drop(1).collect { value ->
            pinned = value >= scrollState.maxValue - bottomSlop
        }
    }
    LaunchedEffect(conversationId, maxScroll) {
        if (pinned || scrollState.value >= maxScroll - bottomSlop) {
            pinned = true
            scrollState.scrollTo(maxScroll)
        }
    }
    ConversationReadEffect(conversationId, messages, pinned)
    val flat = LocalSocialLook.current.timeline == SocialTimeline.Rows
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = if (flat) 24.dp else 16.dp, vertical = if (flat) 16.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var lastDate: String? = null
        var lastSender: String? = null
        messages.forEach { message ->
            val date = dateSeparatorFor(message.sentAt)
            if (date != lastDate) {
                if (flat) DayLabel(message.sentAt) else DateSeparator(date)
                lastDate = date
                lastSender = null
            }
            if (flat) {
                MessageRow(message, message.sender == selfId, incomingInvites, showSender = message.sender != lastSender)
            } else {
                MessageBubble(
                    message,
                    outgoing = message.sender == selfId,
                    incomingInvites = incomingInvites,
                    showSenderGutter = isGroup && message.sender != selfId,
                    showSender = message.sender != lastSender,
                )
            }
            lastSender = message.sender
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun ConversationReadEffect(groupId: Int, messages: List<GroupMessage>, atLatest: Boolean) {
    val showing by produceState(false) {
        while (true) {
            value = SocialOverlay.showsChat()
            delay(CHAT_VISIBILITY_POLL_MILLIS)
        }
    }
    val groups by GroupsRepository.groups.collectAsState()
    val seen = atLatest && showing
    val latest = messages.lastOrNull { !GroupsRepository.isPending(it.id) }?.id
    val unread = groups.any { it.id == groupId && it.unread }
    DisposableEffect(groupId) {
        GroupsRepository.conversationShown(groupId)
        onDispose { GroupsRepository.conversationHidden(groupId) }
    }
    DisposableEffect(groupId, seen) {
        if (seen) GroupsRepository.readingStarted(groupId)
        onDispose { if (seen) GroupsRepository.readingStopped(groupId) }
    }
    LaunchedEffect(groupId, seen, latest, unread) {
        if (seen && latest != null) GroupsRepository.markRead(groupId, latest)
    }
}

@Composable
private fun DateSeparator(date: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(SocialBorderColor))
        SocialText(date, fontSize = 11.sp, color = SocialTextSecondary)
        Box(Modifier.weight(1f).height(1.dp).background(SocialBorderColor))
    }
}

@Composable
private fun DayLabel(sentAt: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        SocialText(dayLabelFor(sentAt), fontSize = 12.sp, color = SocialTextSecondary)
    }
}

@Composable
private fun MessageRow(message: GroupMessage, outgoing: Boolean, incomingInvites: List<SessionInvite>, showSender: Boolean) {
    val invite = message.sessionInvite
    if (invite != null) {
        InviteMessageCard(message, invite, outgoing, incomingInvites)
        return
    }
    val pending = GroupsRepository.isPending(message.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showSender) 16.dp else 0.dp)
            .graphicsLayer { alpha = if (pending) 0.55f else 1f },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(32.dp)) {
            if (showSender) SocialAvatar(message.sender, 32.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showSender) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SocialText(PlayerNamesRepository.displayName(message.sender), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    SocialText(if (pending) "Sending..." else clockFor(message.sentAt), fontSize = 12.sp, color = SocialTextSecondary)
                }
            }
            SocialEmojiText(message.content, fontSize = 16.sp, color = SocialTextPrimary)
        }
    }
}

@Composable
private fun MessageBubble(
    message: GroupMessage,
    outgoing: Boolean,
    incomingInvites: List<SessionInvite>,
    showSenderGutter: Boolean = false,
    showSender: Boolean = true,
) {
    val invite = message.sessionInvite
    if (invite != null) {
        InviteMessageCard(message, invite, outgoing, incomingInvites)
        return
    }

    val pending = GroupsRepository.isPending(message.id)
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (pending) 0.55f else 1f },
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (showSenderGutter) {
            Box(Modifier.width(28.dp).padding(top = if (showSender) 14.dp else 0.dp)) {
                if (showSender) SocialAvatar(message.sender, 28.dp)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showSenderGutter && showSender) {
                    SocialText(
                        PlayerNamesRepository.displayName(message.sender),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                SocialText(if (pending) "Sending..." else timeFor(message.sentAt), fontSize = 10.sp, color = SocialTextSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(ppShapeOf(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (outgoing) 14.dp else 3.dp, bottomEnd = if (outgoing) 3.dp else 14.dp))
                    .background(if (outgoing) OneConfigTheme.accent.copy(alpha = 0.85f) else SocialCardBackground)
                    .border(
                        SocialBorderWidth,
                        if (outgoing) OneConfigTheme.accent else SocialBorderColor,
                        ppShapeOf(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (outgoing) 14.dp else 3.dp, bottomEnd = if (outgoing) 3.dp else 14.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                SocialEmojiText(message.content, fontSize = 14.sp, color = if (outgoing) OneConfigTheme.current.accentTextColor else SocialTextPrimary)
            }
        }
    }
}

@Composable
private fun InviteMessageCard(
    message: GroupMessage,
    invite: GroupMessageSessionInvite,
    outgoing: Boolean,
    incomingInvites: List<SessionInvite>,
) {
    val live = incomingInvites.firstOrNull { it.id == invite.id }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(SocialPanelShape)
            .background(SocialWarnColor.copy(alpha = 0.12f))
            .border(SocialBorderWidth, SocialWarnColor, SocialPanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(message.sender, 32.dp)
        Column(Modifier.weight(1f)) {
            SocialText(
                if (outgoing) "You invited them to your world" else "${PlayerNamesRepository.displayName(message.sender)} invited you to their world",
                fontSize = 13.sp,
            )
            SocialText(
                when (invite.status) {
                    "accepted" -> "Accepted"
                    "declined" -> "Declined"
                    "expired" -> "Expired"
                    else -> "Session invite"
                },
                fontSize = 11.sp,
                color = SocialTextSecondary,
            )
        }
        if (!outgoing && live != null) {
            TextButton("Decline", Modifier.height(34.dp)) { SessionsRepository.decline(live) }
            TextButton(
                if (live.status == "accepted") "Re-join" else "Join",
                themedIcon(SOCIAL_ASSETS + "log-in-04.svg"),
                Modifier.height(34.dp),
                ButtonStyle.Primary,
                true,
            ) { SessionsRepository.accept(live) }
        }
    }
}

@Composable
internal fun MessageComposer(placeholder: String, disabledReason: String? = null, onSend: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    val disabled = disabledReason != null
    val flat = LocalSocialLook.current.timeline == SocialTimeline.Rows
    val ready = !disabled && value.isNotBlank()
    val trySend = {
        if (!disabled && value.isNotBlank()) {
            onSend(EmojiRegistry.toShortcodes(value.trim()))
            value = ""
        }
    }
    val emoji: @Composable () -> Unit = {
        EmojiPickerButton { alias ->
            if (!disabled) {
                val insert = ":$alias:"
                if (value.length + insert.length <= 500) value += insert
            }
        }
    }
    val focus = LocalComposerFocus.current ?: remember { FocusRequester() }
    Column(Modifier.fillMaxWidth()) {
        if (disabledReason != null) {
            SocialText(disabledReason, fontSize = 11.sp, color = SocialTextSecondary, modifier = Modifier.padding(horizontal = if (flat) 24.dp else 12.dp, vertical = 4.dp))
        }
        if (flat) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                    .clip(SocialFieldShape)
                    .background(SocialControlBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialFieldShape)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                emoji()
                TextField(
                    value = value,
                    onValueChange = { if (!disabled) value = it },
                    modifier = Modifier.weight(1f).height(34.dp).padding(start = 6.dp, end = 12.dp),
                    placeholder = if (disabled) "Message unavailable" else placeholder,
                    leadingIcon = null,
                    maxLength = 500,
                    invalid = false,
                    enabled = true,
                    singleLine = true,
                    framed = false,
                    fontSize = 14.sp,
                    label = null,
                    masked = false,
                    focusRequester = focus,
                    interactionSource = null,
                    trailing = null,
                    onSubmit = trySend,
                )
                IconButton(
                    themedIcon(SOCIAL_ASSETS + "chevron-right.svg"),
                    "Send",
                    null,
                    Modifier,
                    if (disabled) null else OneConfigTheme.accent,
                    if (disabled) SocialTextSecondary else OneConfigTheme.current.accentTextColor,
                    TooltipPlacement.BelowStart,
                    trySend,
                )
            }
            return@Column
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = value,
                onValueChange = { if (!disabled) value = it },
                modifier = Modifier.weight(1f).height(38.dp),
                placeholder = if (disabled) "Message unavailable" else placeholder,
                leadingIcon = themedIcon(SOCIAL_ASSETS + "edit-02.svg"),
                maxLength = 500,
                invalid = false,
                enabled = true,
                singleLine = true,
                framed = true,
                fontSize = 14.sp,
                label = null,
                masked = false,
                focusRequester = focus,
                interactionSource = null,
                trailing = null,
                onSubmit = trySend,
            )
            emoji()
            IconButton(
                themedIcon(SOCIAL_ASSETS + "chevron-right.svg"),
                "Send",
                null,
                Modifier,
                if (ready) OneConfigTheme.accent.asSocialSelected else null,
                if (ready) OneConfigTheme.accent else SocialTextSecondary,
                TooltipPlacement.BelowStart,
                trySend,
            )
        }
    }
}

internal inline fun conversationTitle(group: GroupSummary, selfId: String, nameOf: (String) -> String): String {
    if (group.kind == GroupKind.Party) return "Party"
    if (group.kind == GroupKind.Group) {
        val name = group.name
        if (name != null) return name
        val others = group.members.filterNot { it == selfId }
        if (others.isEmpty()) return "Group"
        val joined = StringBuilder()
        for (id in others) {
            if (joined.isNotEmpty()) joined.append(", ")
            joined.append(nameOf(id))
        }
        return joined.toString()
    }
    val other = group.members.firstOrNull { it != selfId } ?: return "You"
    return nameOf(other)
}

private fun dateSeparatorFor(sentAt: String): String = runCatching {
    val instant = Instant.parse(sentAt)
    DateTimeFormatter.ofPattern("MMM d, yyyy")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(sentAt.take(10))

private fun dayLabelFor(sentAt: String): String = runCatching {
    val sent = Instant.parse(sentAt).atZone(ZoneId.systemDefault())
    when (ChronoUnit.DAYS.between(sent.toLocalDate(), LocalDate.now(sent.zone))) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d, yyyy").format(sent)
    }
}.getOrDefault(sentAt.take(10))

private fun clockFor(sentAt: String): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(sentAt))
}.getOrDefault("")

internal fun timeFor(sentAt: String): String = runCatching {
    val instant = Instant.parse(sentAt)
    DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault("")
