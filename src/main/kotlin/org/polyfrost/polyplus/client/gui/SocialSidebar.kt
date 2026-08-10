package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

internal enum class SocialTab { Chat, Global, Friends }

@Composable
internal fun SocialSidebar(
    modifier: Modifier,
    tab: SocialTab,
    onTabChange: (SocialTab) -> Unit,
    groups: List<GroupSummary>,
    isSearching: Boolean,
    friends: List<Friend>,
    selfId: String,
    selectedGroupId: Int?,
    onSelectGroup: (Int) -> Unit,
    onSelectFriend: (String) -> Unit,
    onAddFriend: () -> Unit,
) {
    Column(
        modifier = modifier.background(SocialSidebarBackground),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SidebarTabButton("Chat", tab == SocialTab.Chat, Modifier.weight(1f)) { onTabChange(SocialTab.Chat) }
            // Global chat is disabled for now.
            // SidebarTabButton("Global", tab == SocialTab.Global, Modifier.weight(1f)) { onTabChange(SocialTab.Global) }
            SidebarTabButton("Friends", tab == SocialTab.Friends, Modifier.weight(1f)) { onTabChange(SocialTab.Friends) }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
        when (tab) {
            SocialTab.Chat -> ConversationList(
                groups,
                isSearching,
                selfId,
                selectedGroupId,
                onSelectGroup,
                onAddFriend,
            )
            SocialTab.Global -> GlobalChatSidebarInfo()
            SocialTab.Friends -> FriendQuickList(friends, onSelectFriend, onAddFriend)
        }
    }
}

@Composable
private fun SidebarTabButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val background by androidx.compose.animation.animateColorAsState(if (selected) Accent.asSocialSelected else if (hovered) SocialHoverOverlay else Color.Transparent)
    val border by androidx.compose.animation.animateColorAsState(if (selected) Accent else if (hovered) SocialHoverBorder else SocialBorderColor)
    val textColor by androidx.compose.animation.animateColorAsState(if (selected) Accent else SocialTextSecondary)
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(SocialFieldShape)
            .background(background)
            .border(SocialBorderWidth, border, SocialFieldShape)
            .hoverable(interaction)
            .clickableWithSound(onClick),
        contentAlignment = Alignment.Center,
    ) {
        SocialText(label, fontSize = 13.sp, color = textColor)
    }
}

@Composable
private fun ConversationList(
    groups: List<GroupSummary>,
    isSearching: Boolean,
    selfId: String,
    selectedGroupId: Int?,
    onSelectGroup: (Int) -> Unit,
    onAddFriend: () -> Unit,
) {
    if (groups.isEmpty()) {
        if (isSearching) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                SocialText("No conversations match your search", fontSize = 13.sp, color = SocialTextSecondary)
            }
        } else {
            SidebarEmptyState(
                message = "No conversations yet",
                actionLabel = "Add a friend to get started",
                onAction = onAddFriend,
            )
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        groups.sortedByDescending { it.lastMessage?.sentAt ?: "" }.forEach { group ->
            ConversationRow(group, selfId, group.id == selectedGroupId) { onSelectGroup(group.id) }
        }
    }
}

@Composable
private fun ConversationRow(group: GroupSummary, selfId: String, selected: Boolean, onClick: () -> Unit) {
    val title = conversationDisplayTitle(group, selfId)
    val (interaction, hovered) = rememberSocialHover()
    val border by androidx.compose.animation.animateColorAsState(
        when {
            selected -> Accent
            hovered -> SocialHoverBorder
            else -> SocialBorderColor
        },
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(SocialPanelShape)
            .background(if (selected) Accent.asSocialSelected else SocialCardBackground)
            .border(SocialBorderWidth, border, SocialPanelShape)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (group.kind == GroupKind.Group) {
            val ringColor = if (selected) Accent.asSocialSelected else SocialCardBackground
            SocialGroupAvatar(group.members.filterNot { it == selfId }, 38.dp, ringColor = ringColor)
        } else {
            val other = group.members.firstOrNull { it != selfId } ?: selfId
            SocialAvatar(other, 38.dp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SocialText(title, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                if (group.unread) {
                    Box(Modifier.size(8.dp).clip(LocalTheme.current.circleShape).background(Accent))
                }
            }
            SocialText(
                group.lastMessage?.content ?: "No messages yet",
                fontSize = 12.sp,
                color = SocialTextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SidebarEmptyState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SocialText(message, fontSize = 13.sp, color = SocialTextSecondary)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            SocialButton(actionLabel, icon = SOCIAL_ASSETS + "user-plus-01.svg", filled = true, onClick = onAction)
        }
    }
}

@Composable
private fun GlobalChatSidebarInfo() {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(44.dp).clip(LocalTheme.current.circleShape).background(SocialControlBackground), contentAlignment = Alignment.Center) {
            org.polyfrost.oneconfig.internal.ui.components.Icon(SOCIAL_ASSETS + "image-01.svg", Accent, Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        SocialText("Global Chat", fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        SocialText("A public channel visible to every Poly+ player online.", fontSize = 12.sp, color = SocialTextSecondary)
    }
}

@Composable
private fun FriendQuickList(friends: List<Friend>, onSelectFriend: (String) -> Unit, onAddFriend: () -> Unit) {
    if (friends.isEmpty()) {
        SidebarEmptyState(message = "No friends yet", actionLabel = "Add a friend", onAction = onAddFriend)
        return
    }
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        friends.sortedWith(compareByDescending<Friend> { it.online }.thenBy { it.player }).forEach { friend ->
            val (interaction, hovered) = rememberSocialHover()
            val background by androidx.compose.animation.animateColorAsState(if (hovered) SocialHoverOverlay else Color.Transparent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SocialFieldShape)
                    .background(background)
                    .hoverable(interaction)
                    .clickableWithSound { onSelectFriend(friend.player) }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    SocialAvatar(friend.player, 32.dp)
                    if (friend.online) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(LocalTheme.current.circleShape)
                                .background(SocialSuccessColor)
                                .align(Alignment.BottomEnd),
                        )
                    }
                }
                SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 13.sp)
            }
        }
    }
}

