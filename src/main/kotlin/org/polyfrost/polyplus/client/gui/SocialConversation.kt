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
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
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
    val warning = OneConfigTheme.current.warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(SocialPanelShape)
            .background(warning.copy(alpha = 0.12f))
            .border(SocialBorderWidth, warning, SocialPanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(invite.sender, 32.dp)
        Column(Modifier.weight(1f)) {
            Text("${PlayerNamesRepository.displayName(invite.sender)} invited you to their world", style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
            Text("Session invite", color = SocialTextSecondary, style = OneConfigTheme.current.typography.caption)
        }
        TextButton(text = "Decline", onClick = { SessionsRepository.decline(invite) }, modifier = Modifier.height(34.dp))
        TextButton(
            text = "Join",
            icon = SOCIAL_ASSETS + "log-in-04.svg",
            onClick = { SessionsRepository.accept(invite) },
            modifier = Modifier.height(34.dp),
            style = ControlStyle.Primary,
            enabled = true,
        )
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
            Text("No messages yet. Say hello!", color = SocialTextSecondary, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal))
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
    ScrollContainer(scrollState, modifier.fillMaxWidth(), scrollbar = false) {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (flat) 24.dp else 16.dp, vertical = if (flat) 16.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Divider(Modifier.weight(1f))
        Text(date, color = SocialTextSecondary, style = OneConfigTheme.current.typography.caption)
        Divider(Modifier.weight(1f))
    }
}

@Composable
private fun DayLabel(sentAt: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(dayLabelFor(sentAt), color = SocialTextSecondary, style = OneConfigTheme.current.typography.small)
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
    val pendingAlpha = OneConfigTheme.current.disabledAlpha
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showSender) 16.dp else 0.dp)
            .graphicsLayer { alpha = if (pending) pendingAlpha else 1f },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(32.dp)) {
            if (showSender) SocialAvatar(message.sender, 32.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showSender) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(PlayerNamesRepository.displayName(message.sender), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
                    Text(if (pending) "Sending..." else clockFor(message.sentAt), color = SocialTextSecondary, style = OneConfigTheme.current.typography.small)
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
    val pendingAlpha = OneConfigTheme.current.disabledAlpha
    Row(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (pending) pendingAlpha else 1f },
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
                    Text(
                        PlayerNamesRepository.displayName(message.sender),
                        style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Text(if (pending) "Sending..." else timeFor(message.sentAt), color = SocialTextSecondary, style = OneConfigTheme.current.typography.body.copy(fontSize = 10.sp))
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
    val warning = OneConfigTheme.current.warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(SocialPanelShape)
            .background(warning.copy(alpha = 0.12f))
            .border(SocialBorderWidth, warning, SocialPanelShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(message.sender, 32.dp)
        Column(Modifier.weight(1f)) {
            Text(
                if (outgoing) "You invited them to your world" else "${PlayerNamesRepository.displayName(message.sender)} invited you to their world",
                style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Normal),
            )
            Text(
                when (invite.status) {
                    "accepted" -> "Accepted"
                    "declined" -> "Declined"
                    "expired" -> "Expired"
                    else -> "Session invite"
                },
                color = SocialTextSecondary,
                style = OneConfigTheme.current.typography.caption,
            )
        }
        if (!outgoing && live != null) {
            TextButton(text = "Decline", onClick = { SessionsRepository.decline(live) }, modifier = Modifier.height(34.dp))
            TextButton(
                text = if (live.status == "accepted") "Re-join" else "Join",
                icon = SOCIAL_ASSETS + "log-in-04.svg",
                onClick = { SessionsRepository.accept(live) },
                modifier = Modifier.height(34.dp),
                style = ControlStyle.Primary,
                enabled = true,
            )
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
    val fieldPlaceholder = if (disabled) "Message unavailable" else placeholder
    Column(Modifier.fillMaxWidth()) {
        if (disabledReason != null) {
            Text(disabledReason, modifier = Modifier.padding(horizontal = if (flat) 24.dp else 12.dp, vertical = 4.dp), color = SocialTextSecondary, style = OneConfigTheme.current.typography.caption)
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
                    placeholder = fieldPlaceholder,
                    maxLength = 500,
                    framed = false,
                    fontSize = 14.sp,
                    focusRequester = focus,
                    onSubmit = trySend,
                )
                IconButton(
                    icon = SOCIAL_ASSETS + "chevron-right.svg",
                    contentDescription = "Send",
                    onClick = trySend,
                    style = if (disabled) IconButtonStyle.Ghost else IconButtonStyle.Filled,
                    tooltipPlacement = TooltipPlacement.BelowStart,
                    restTint = SocialTextSecondary,
                    frameSize = 34.dp,
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
                modifier = Modifier.weight(1f),
                size = ControlSize.Large,
                placeholder = fieldPlaceholder,
                leadingIcon = SOCIAL_ASSETS + "edit-02.svg",
                maxLength = 500,
                fontSize = 14.sp,
                focusRequester = focus,
                onSubmit = trySend,
            )
            emoji()
            val sendShape = IconButtonDefaults.shape(IconButtonStyle.Ghost)
            IconButton(
                icon = SOCIAL_ASSETS + "chevron-right.svg",
                contentDescription = "Send",
                onClick = trySend,
                modifier = if (ready) Modifier.clip(sendShape).background(OneConfigTheme.accent.asSocialSelected) else Modifier,
                tooltipPlacement = TooltipPlacement.BelowStart,
                tint = if (ready) OneConfigTheme.accent else SocialTextSecondary,
                shape = sendShape,
                frameSize = 34.dp,
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
