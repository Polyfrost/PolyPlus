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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.polyplus.client.gui.ConfirmPrompt
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyUserTheme
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.MessageComposer
import org.polyfrost.polyplus.client.gui.MessageTimeline
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.WorldInviteBanner
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
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
    Row(
        Modifier.fillMaxWidth().height(if (look.surface == SocialSurface.None) 64.dp else 58.dp).padding(horizontal = look.paneEdge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (look.backButton) BackButton(SocialChatLink::close)
        ThreadFace(thread, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PanelTitle(thread.title)
            val status = subtitle ?: thread.people.firstOrNull()?.takeIf { !thread.group && it.presenceKnown }?.status
            status?.let { Label(it, 12.sp, color = MutedText) }
        }
        actions(group)
        if (thread.group) {
            GroupMenu(state, group)
        }
        trailing()
    }
}

internal fun groupMenuItems(
    state: SocialPageState,
    group: GroupSummary,
    specialTarget: Boolean = SpecialChatRepository.status.value?.isSpecialChatTarget == true,
): List<MenuAction> {
    val self = ChatIdentity.selfId()
    val convertible = group.special && specialTarget
    return listOfNotNull(
        MenuAction("Invite Friends", SOCIAL_ASSETS + "user-plus-01.svg") { state.dialog = SocialDialog.InviteToGroup(group.id) },
        MenuAction("Invite Group to Session", SOCIAL_ASSETS + "log-in-04.svg") { SocialHosting.inviteGroup(group, self) },
        MenuAction("Convert to Normal Chat", SOCIAL_ASSETS + "check.svg") { GroupsRepository.claimSpecialChatGroup(group.id) }.takeIf { convertible },
        MenuAction("Leave Group", SOCIAL_ASSETS + "x-close.svg", danger = true) {
            ConfirmPrompt.ask("Leave ${group.name?.takeIf { it.isNotBlank() } ?: "this group"}?", "You will stop getting its messages until someone adds you back.", "Leave", SOCIAL_ASSETS + "x-close.svg") {
                if (SocialChatLink.active?.id == group.id) SocialChatLink.close()
                GroupsRepository.removeMember(group.id, self)
            }
        }.takeIf { !group.special },
    )
}

internal fun threadMenuItems(state: SocialPageState, thread: SocialThread): List<MenuAction> {
    val open = MenuAction("Open chat", SOCIAL_ASSETS + "message-chat-circle.svg") { state.source.open(thread) }
    val group = GroupsRepository.groups.value.firstOrNull { it.id == thread.id }
    if (thread.group && !thread.party && group != null) return listOf(open) + groupMenuItems(state, group)
    return listOf(open)
}

@Composable
private fun GroupMenu(state: SocialPageState, group: GroupSummary) {
    val status by SpecialChatRepository.status.collectAsState()
    AnchoredPopupButton(themedIcon(LobbyIcons.MORE), "Group options") { close ->
        LobbyUserTheme { MenuList(groupMenuItems(state, group, status?.isSpecialChatTarget == true), close) }
    }
}
