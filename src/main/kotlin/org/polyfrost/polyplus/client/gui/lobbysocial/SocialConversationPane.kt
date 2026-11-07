@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.polyplus.client.gui.ContextItem
import org.polyfrost.polyplus.client.gui.ContextMenuList
import org.polyfrost.polyplus.client.gui.MessageComposer
import org.polyfrost.polyplus.client.gui.MessageTimeline
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialTextField
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.WorldInviteBanner
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SpecialChatRepository

private const val INVITE_PENDING = "pending"

@Composable
internal fun ConversationPane(
    state: SocialPageState,
    conversation: ChatConversation,
    thread: SocialThread,
    modifier: Modifier,
    subtitle: String? = null,
    offlineReason: String? = null,
    actions: @Composable RowScope.(GroupSummary) -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val groups by GroupsRepository.groups.collectAsState()
    val group = groups.firstOrNull { it.id == thread.id } ?: return
    val look = LocalSocialLook.current
    val framed = look.surface == SocialSurface.None
    if (look.backButton) EscapeCloses { if (SocialPageState.dialogOpen) SocialPageState.closeDialog() else SocialChatLink.close() }
    Column(modifier.socialSurface()) {
        ConversationHeader(state, thread, group, subtitle, actions, trailing)
        if (framed) Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
        ConversationBody(conversation, thread, group, if (framed) "Message ${thread.title}" else "Message ${thread.title}...", offlineReason)
    }
}

@Composable
internal fun ColumnScope.ConversationBody(
    conversation: ChatConversation,
    thread: SocialThread,
    group: GroupSummary,
    placeholder: String,
    offlineReason: String? = null,
) {
    val messages by GroupsRepository.messagesFlow(thread.id).collectAsState()
    val invites by SessionsRepository.incomingInvites.collectAsState()
    val inline = messages.mapNotNull { it.sessionInvite?.id }.toSet()
    val banners = invites.filter { it.sender in group.members && it.id !in inline && it.status == INVITE_PENDING }
    val cooldown = cooldownReason(thread.id)
    if (LocalSocialLook.current.inviteBanner) banners.forEach { WorldInviteBanner(it) }
    MessageTimeline(Modifier.weight(1f), messages, ChatIdentity.selfId(), thread.id, invites, thread.group)
    key(thread.id) {
        MessageComposer(placeholder, offlineReason ?: cooldown, conversation::send)
    }
}

@Composable
private fun ConversationHeader(
    state: SocialPageState,
    thread: SocialThread,
    group: GroupSummary,
    subtitle: String?,
    actions: @Composable RowScope.(GroupSummary) -> Unit,
    trailing: @Composable RowScope.() -> Unit,
) {
    val look = LocalSocialLook.current
    var renaming by remember(thread.id) { mutableStateOf(false) }
    var draft by remember(thread.id) { mutableStateOf("") }
    val muted = thread.id in SocialChatLink.muted
    val renameFocus = remember { FocusRequester() }
    if (renaming && !SocialPageState.dialogOpen) EscapeCloses { renaming = false }
    LaunchedEffect(renaming) { if (renaming) renameFocus.requestFocus() }
    Row(
        Modifier.fillMaxWidth().height(if (look.surface == SocialSurface.None) 64.dp else 58.dp).padding(horizontal = look.paneEdge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (look.backButton) BackButton(SocialChatLink::close)
        ThreadFace(thread, 34.dp)
        if (renaming) {
            SocialTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = thread.title,
                focusRequester = renameFocus,
                onSubmit = {
                    renaming = false
                    if (draft.isNotBlank()) SocialChatLink.rename(thread.id, draft.trim())
                },
            )
        } else {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PanelTitle(thread.title)
                val status = subtitle ?: thread.people.firstOrNull()?.takeIf { !thread.group && it.presenceKnown }?.status
                status?.let { Label(it, 12.sp, color = MutedText) }
            }
        }
        if (muted) ThemedIcon(SOCIAL_ASSETS + "volume-x.svg", MutedText, 16.dp)
        actions(group)
        if (thread.group) {
            GroupMenu(state, group) {
                draft = ""
                renaming = true
            }
        }
        trailing()
    }
}

internal fun groupMenuItems(
    state: SocialPageState,
    group: GroupSummary,
    onRename: (() -> Unit)? = null,
    specialTarget: Boolean = SpecialChatRepository.status.value?.isSpecialChatTarget == true,
): List<ContextItem> {
    val self = ChatIdentity.selfId()
    val muted = group.id in SocialChatLink.muted
    val convertible = group.special && specialTarget
    return listOfNotNull(
        ContextItem("Invite Friends", SOCIAL_ASSETS + "user-plus-01.svg") { state.dialog = SocialDialog.InviteToGroup(group.id) },
        ContextItem("Invite Group to Session", SOCIAL_ASSETS + "log-in-04.svg") { SocialHosting.inviteGroup(group, self) },
        onRename?.let { ContextItem("Rename Group", SOCIAL_ASSETS + "edit-02.svg", onClick = it) },
        ContextItem(if (muted) "Unmute Group" else "Mute Group", SOCIAL_ASSETS + "volume-x.svg") { SocialChatLink.toggleMute(group.id) },
        ContextItem("Convert to Normal Chat", SOCIAL_ASSETS + "check.svg") { GroupsRepository.claimSpecialChatGroup(group.id) }.takeIf { convertible },
        ContextItem("Leave Group", SOCIAL_ASSETS + "x-close.svg", danger = true) {
            if (SocialChatLink.active?.id == group.id) SocialChatLink.close()
            GroupsRepository.removeMember(group.id, self)
        }.takeIf { !group.special },
    )
}

internal fun threadMenuItems(state: SocialPageState, thread: SocialThread): List<ContextItem> {
    val open = ContextItem("Open chat", SOCIAL_ASSETS + "message-chat-circle.svg") { state.source.open(thread) }
    val group = GroupsRepository.groups.value.firstOrNull { it.id == thread.id }
    if (thread.group && !thread.party && group != null) return listOf(open) + groupMenuItems(state, group)
    val muted = thread.id in SocialChatLink.muted
    return listOf(open, ContextItem(if (muted) "Unmute chat" else "Mute chat", SOCIAL_ASSETS + "volume-x.svg") { SocialChatLink.toggleMute(thread.id) })
}

@Composable
private fun GroupMenu(state: SocialPageState, group: GroupSummary, onRename: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val status by SpecialChatRepository.status.collectAsState()
    val drop = with(LocalDensity.current) { MENU_OFFSET.roundToPx() }
    val canRename = LocalSocialLook.current.rename
    Box {
        SocialIconButton(LobbyIcons.MORE, tooltip = "Group options") { open = !open }
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, drop),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                ContextMenuList(groupMenuItems(state, group, onRename.takeIf { canRename }, status?.isSpecialChatTarget == true)) { open = false }
            }
        }
    }
}
