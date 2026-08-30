package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupMessageSessionInvite
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.social.SpecialChatRepository
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ConversationView(
    group: GroupSummary,
    selfId: String,
    messages: List<GroupMessage>,
    incomingInvites: List<SessionInvite>,
    mutedGroups: Set<Int>,
    renameOverrides: Map<Int, String>,
    onToggleMute: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onInviteToGroup: () -> Unit,
    onLeaveGroup: () -> Unit,
) {
    val title = renameOverrides[group.id] ?: conversationDisplayTitle(group, selfId)
    val invitesShownInline = messages.mapNotNull { it.sessionInvite?.id }.toSet()
    val relevantInvites = incomingInvites.filter {
        it.sender in group.members && it.id !in invitesShownInline && it.status == "pending"
    }
    val specialStatus by SpecialChatRepository.status.collectAsState()
    val isSpecialGroup = specialStatus?.groupId == group.id
    val canConvertToNormal = group.special && specialStatus?.isSpecialChatTarget == true

    val latestMessageId = messages.lastOrNull { !GroupsRepository.isPending(it.id) }?.id
    LaunchedEffect(group.id, latestMessageId) {
        if (latestMessageId != null) GroupsRepository.markRead(group.id, latestMessageId)
    }

    Column(Modifier.fillMaxSize()) {
        ConversationHeader(
            group = group,
            title = title,
            selfId = selfId,
            muted = group.id in mutedGroups,
            canConvertToNormal = canConvertToNormal,
            onToggleMute = { onToggleMute(group.id) },
            onRename = { onRename(group.id, it) },
            onInvite = onInviteToGroup,
            onLeave = onLeaveGroup,
            onInviteToSession = {
                val sessionId = P2PSessionManager.currentSessionId
                if (sessionId == null) {
                    SocialErrors.emit("Host a world first to invite people to your session")
                } else {
                    val invited = group.members.filterNot { it == selfId }
                    invited.forEach { member -> SessionsRepository.invite(sessionId, member) }
                    SocialErrors.emit("Invited the group to your session")
                }
            },
            onConvertToNormal = { GroupsRepository.claimSpecialChatGroup(group.id) },
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
        relevantInvites.forEach { invite -> WorldInviteBanner(invite) }
        MessageTimeline(Modifier.weight(1f), messages, selfId, group.id, incomingInvites, isGroup = group.kind == GroupKind.Group)

        val cooldownRemaining = (if (isSpecialGroup) specialStatus?.cooldownUntil else null)
            ?.let { rememberCooldownRemaining(it) } ?: 0L
        MessageComposer(
            placeholder = "Message ${title}...",
            disabledReason = if (cooldownRemaining > 0) "Next Special Chat message available in ${formatCooldown(cooldownRemaining)}" else null,
        ) { content ->
            GroupsRepository.sendMessage(group.id, content)
            if (isSpecialGroup) SpecialChatRepository.refreshTargets()
        }
    }
}

@Composable
private fun rememberCooldownRemaining(cooldownUntil: String): Long {
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

private fun formatCooldown(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

@Composable
private fun WorldInviteBanner(invite: SessionInvite) {
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
        SocialButton("Decline", modifier = Modifier.height(34.dp), onClick = { SessionsRepository.decline(invite) })
        SocialButton("Join", icon = SOCIAL_ASSETS + "log-in-04.svg", filled = true, modifier = Modifier.height(34.dp), onClick = { SessionsRepository.accept(invite) })
    }
}

@Composable
private fun ConversationHeader(
    group: GroupSummary,
    title: String,
    selfId: String,
    muted: Boolean,
    canConvertToNormal: Boolean,
    onToggleMute: () -> Unit,
    onRename: (String) -> Unit,
    onInvite: () -> Unit,
    onLeave: () -> Unit,
    onInviteToSession: () -> Unit,
    onConvertToNormal: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf(title) }

    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (group.kind == GroupKind.Group) {
            SocialGroupAvatar(group.members.filterNot { it == selfId }, 34.dp, ringColor = SocialWindowBackground)
        } else {
            val other = group.members.firstOrNull { it != selfId } ?: selfId
            SocialAvatar(other, 34.dp)
        }

        if (renaming) {
            SocialTextField(
                value = renameValue,
                onValueChange = { renameValue = it },
                modifier = Modifier.weight(1f),
                placeholder = "Group name",
                onSubmit = { renaming = false; if (renameValue.isNotBlank()) onRename(renameValue.trim()) },
            )
        } else {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                SocialText(title, fontSize = 16.sp, maxLines = 1)
            }
        }

        if (muted) Icon(SOCIAL_ASSETS + "volume-x.svg", SocialTextSecondary, Modifier.size(16.dp))

        if (group.kind == GroupKind.Group) {
            Box {
                SocialIconButton(SOCIAL_ASSETS + "dots-vertical.svg", tooltip = "Group options", onClick = { menuOpen = !menuOpen })
                if (menuOpen) {
                    androidx.compose.ui.window.Popup(
                        alignment = Alignment.TopEnd,
                        offset = androidx.compose.ui.unit.IntOffset(0, 44),
                        onDismissRequest = { menuOpen = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
                    ) {
                        GroupOverflowMenu(
                            onInvite = { menuOpen = false; onInvite() },
                            onInviteToSession = { menuOpen = false; onInviteToSession() },
                            onRename = { menuOpen = false; renameValue = title; renaming = true },
                            onMute = { menuOpen = false; onToggleMute() },
                            onLeave = { menuOpen = false; onLeave() },
                            onConvertToNormal = { menuOpen = false; onConvertToNormal() },
                            muted = muted,
                            canConvertToNormal = canConvertToNormal,
                            canLeave = !group.special,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupOverflowMenu(
    onInvite: () -> Unit,
    onInviteToSession: () -> Unit,
    onRename: () -> Unit,
    onMute: () -> Unit,
    onLeave: () -> Unit,
    onConvertToNormal: () -> Unit,
    muted: Boolean,
    canConvertToNormal: Boolean,
    canLeave: Boolean,
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        OverflowMenuItem(SOCIAL_ASSETS + "user-plus-01.svg", "Invite Friends", onClick = onInvite)
        OverflowMenuItem(SOCIAL_ASSETS + "log-in-04.svg", "Invite Group to Session", onClick = onInviteToSession)
        OverflowMenuItem(SOCIAL_ASSETS + "edit-02.svg", "Rename Group", onClick = onRename)
        OverflowMenuItem(SOCIAL_ASSETS + "volume-x.svg", if (muted) "Unmute Group" else "Mute Group", onClick = onMute)
        if (canConvertToNormal) {
            OverflowMenuItem(SOCIAL_ASSETS + "check.svg", "Convert to Normal Chat", onClick = onConvertToNormal)
        }
        if (canLeave) {
            OverflowMenuItem(SOCIAL_ASSETS + "x-close.svg", "Leave Group", color = SocialDangerColor, onClick = onLeave)
        }
    }
}

@Composable
private fun OverflowMenuItem(icon: String, label: String, color: androidx.compose.ui.graphics.Color = SocialTextPrimary, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val background by androidx.compose.animation.animateColorAsState(if (hovered) SocialHoverOverlay else androidx.compose.ui.graphics.Color.Transparent)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(SocialFieldShape)
            .background(background)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, color, Modifier.size(15.dp))
        SocialText(label, fontSize = 13.sp, color = color)
    }
}

@Composable
private fun MessageTimeline(
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
    var contentHeight by remember(conversationId) { mutableStateOf(0) }
    LaunchedEffect(conversationId, contentHeight) {
        scrollState.scrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .onSizeChanged { contentHeight = it.height },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        var lastDate: String? = null
        var lastSender: String? = null
        messages.forEach { message ->
            val date = dateSeparatorFor(message.sentAt)
            if (date != lastDate) {
                DateSeparator(date)
                lastDate = date
                lastSender = null
            }
            MessageBubble(
                message,
                outgoing = message.sender == selfId,
                incomingInvites = incomingInvites,
                showSenderGutter = isGroup && message.sender != selfId,
                showSender = message.sender != lastSender,
            )
            lastSender = message.sender
        }
        Spacer(Modifier.height(4.dp))
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
        modifier = Modifier.fillMaxWidth().alpha(if (pending) 0.55f else 1f),
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                SocialText(if (pending) "Sending..." else timeFor(message.sentAt), fontSize = 10.sp, color = SocialTextSecondary)
            }
            Box(
                modifier = Modifier
                    .clip(ppShapeOf(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (outgoing) 14.dp else 3.dp, bottomEnd = if (outgoing) 3.dp else 14.dp))
                    .background(if (outgoing) Accent.copy(alpha = 0.85f) else SocialCardBackground)
                    .border(
                        SocialBorderWidth,
                        if (outgoing) Accent else SocialBorderColor,
                        ppShapeOf(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (outgoing) 14.dp else 3.dp, bottomEnd = if (outgoing) 3.dp else 14.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                SocialText(message.content, fontSize = 14.sp, color = if (outgoing) androidx.compose.ui.graphics.Color.White else SocialTextPrimary)
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
            SocialButton("Decline", modifier = Modifier.height(34.dp), onClick = { SessionsRepository.decline(live) })
            SocialButton(
                if (live.status == "accepted") "Re-join" else "Join",
                icon = SOCIAL_ASSETS + "log-in-04.svg",
                filled = true,
                modifier = Modifier.height(34.dp),
                onClick = { SessionsRepository.accept(live) },
            )
        }
    }
}

@Composable
internal fun MessageComposer(placeholder: String, disabledReason: String? = null, onSend: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    val disabled = disabledReason != null
    val trySend = {
        if (!disabled && value.isNotBlank()) {
            onSend(value.trim())
            value = ""
        }
    }
    Column(Modifier.fillMaxWidth()) {
        if (disabledReason != null) {
            SocialText(disabledReason, fontSize = 11.sp, color = SocialTextSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SocialTextField(
                value = value,
                onValueChange = { if (!disabled) value = it },
                modifier = Modifier.weight(1f),
                placeholder = if (disabled) "Message unavailable" else placeholder,
                leadingIcon = SOCIAL_ASSETS + "edit-02.svg",
                maxLength = 500,
                onSubmit = trySend,
            )
            SocialIconButton(
                SOCIAL_ASSETS + "chevron-right.svg",
                background = if (!disabled && value.isNotBlank()) Accent.asSocialSelected else null,
                tint = if (!disabled && value.isNotBlank()) Accent else SocialTextSecondary,
                tooltip = "Send",
                onClick = trySend,
            )
        }
    }
}

@Composable
internal fun conversationDisplayTitle(group: GroupSummary, selfId: String): String {
    if (group.kind == GroupKind.Group) {
        group.name?.let { return it }
        val others = group.members.filterNot { it == selfId }
        return if (others.isEmpty()) "Group" else others.map { PlayerNamesRepository.displayName(it) }.joinToString()
    }
    val other = group.members.firstOrNull { it != selfId } ?: return "You"
    return PlayerNamesRepository.displayName(other)
}

private fun dateSeparatorFor(sentAt: String): String = runCatching {
    val instant = Instant.parse(sentAt)
    DateTimeFormatter.ofPattern("MMM d, yyyy")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault(sentAt.take(10))

internal fun timeFor(sentAt: String): String = runCatching {
    val instant = Instant.parse(sentAt)
    DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}.getOrDefault("")
