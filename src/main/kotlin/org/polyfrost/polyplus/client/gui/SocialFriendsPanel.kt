package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.network.http.responses.BlockedPlayer
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.FriendRequest
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository

@Composable
internal fun FriendsManagementView(
    friends: List<Friend>,
    incomingRequests: List<FriendRequest>,
    outgoingRequests: List<FriendRequest>,
    onOpenConversation: (Int) -> Unit,
) {
    var showBlocked by remember { mutableStateOf(false) }
    val blocked by FriendsRepository.blocked.collectAsState()

    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SocialText("Friend List", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                SocialText(
                    "Blocked (${blocked.size})",
                    fontSize = 12.sp,
                    color = SocialTextSecondary,
                    modifier = Modifier.clickableTextWithSound { showBlocked = true },
                )
            }
            Spacer(Modifier.height(10.dp))
            if (friends.isEmpty()) {
                EmptyHint("No friends yet. Add one from the toolbar above.")
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    friends.forEach { friend ->
                        FriendCard(
                            friend = friend,
                            onMessage = {
                                GroupsRepository.openDirectMessage(friend.player) { result ->
                                    result.onSuccess { onOpenConversation(it.id) }
                                }
                            },
                            onRemove = { FriendsRepository.removeFriend(friend.player) },
                            onBlock = { FriendsRepository.block(friend.player) },
                        )
                    }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(SocialBorderColor))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SocialText("Friend Requests", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            if (incomingRequests.isEmpty() && outgoingRequests.isEmpty()) {
                EmptyHint("No pending requests")
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    incomingRequests.forEach { request ->
                        RequestCard(
                            request = request,
                            incoming = true,
                            onAccept = { FriendsRepository.acceptRequest(request.id) },
                            onDismiss = { FriendsRepository.declineRequest(request.id) },
                        )
                    }
                    outgoingRequests.forEach { request ->
                        RequestCard(
                            request = request,
                            incoming = false,
                            onAccept = null,
                            onDismiss = { FriendsRepository.cancelRequest(request.id) },
                        )
                    }
                }
            }
        }
    }

    if (showBlocked) {
        BlockedPlayersDialog(blocked = blocked, onUnblock = { FriendsRepository.unblock(it) }, onDismiss = { showBlocked = false })
    }
}

@Composable
private fun BlockedPlayersDialog(blocked: List<BlockedPlayer>, onUnblock: (String) -> Unit, onDismiss: () -> Unit) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialScrim)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(SocialPanelShape)
                    .background(SocialPopupBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SocialText("Blocked Players", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (blocked.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        SocialText("You haven't blocked anyone", fontSize = 13.sp, color = SocialTextSecondary)
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        blocked.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(SocialPanelShape)
                                    .background(SocialCardBackground)
                                    .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SocialAvatar(entry.player, 28.dp)
                                SocialText(PlayerNamesRepository.displayName(entry.player), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                SocialButton("Unblock", onClick = { onUnblock(entry.player) })
                            }
                        }
                    }
                }
                SocialButton("Close", icon = SOCIAL_ASSETS + "x-close.svg", modifier = Modifier.fillMaxWidth(), filled = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
        SocialText(text, fontSize = 13.sp, color = SocialTextSecondary)
    }
}

@Composable
private fun FriendCard(friend: Friend, onMessage: () -> Unit, onRemove: () -> Unit, onBlock: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val (interaction, hovered) = rememberSocialHover()
    val border by animateColorAsState(if (hovered) SocialHoverBorder else SocialBorderColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(SocialPanelShape)
            .background(SocialCardBackground)
            .border(SocialBorderWidth, border, SocialPanelShape)
            .hoverable(interaction)
            .clickableWithSound(onMessage)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            SocialAvatar(friend.player, 40.dp)
            if (friend.online) {
                Box(
                    Modifier.size(11.dp).clip(LocalTheme.current.circleShape).background(SocialSuccessColor).align(Alignment.BottomEnd),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 14.sp)
            SocialText(if (friend.online) "Online" else "Offline", fontSize = 12.sp, color = if (friend.online) SocialSuccessColor else SocialTextSecondary)
        }
        Box {
            SocialIconButton(SOCIAL_ASSETS + "dots-vertical.svg", tooltip = "Friend options", onClick = { menuOpen = !menuOpen })
            if (menuOpen) {
                Popup(
                    alignment = Alignment.TopEnd,
                    offset = androidx.compose.ui.unit.IntOffset(0, 42),
                    onDismissRequest = { menuOpen = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        modifier = Modifier
                            .width(180.dp)
                            .clip(SocialPanelShape)
                            .background(SocialPopupBackground)
                            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        FriendMenuItem("Remove Friend") { menuOpen = false; onRemove() }
                        FriendMenuItem("Block", color = SocialDangerColor) { menuOpen = false; onBlock() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendMenuItem(label: String, color: Color = SocialTextPrimary, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val background by animateColorAsState(if (hovered) SocialHoverOverlay else Color.Transparent)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(SocialFieldShape)
            .background(background)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        SocialText(label, fontSize = 13.sp, color = color)
    }
}

@Composable
internal fun InviteToGroupDialog(friends: List<Friend>, onInvite: (String) -> Unit, onDismiss: () -> Unit) {
    var invited by remember { mutableStateOf(setOf<String>()) }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialScrim)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(420.dp)
                    .clip(SocialPanelShape)
                    .background(SocialPopupBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SocialText("Invite Friends", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (friends.isEmpty()) {
                    EmptyHint("All your friends are already in this group")
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().height(280.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        friends.forEach { friend ->
                            val done = friend.player in invited
                            Row(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                SocialAvatar(friend.player, 30.dp)
                                SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 14.sp, modifier = Modifier.weight(1f))
                                SocialIconButton(
                                    icon = if (done) SOCIAL_ASSETS + "check-circle.svg" else SOCIAL_ASSETS + "user-plus-01.svg",
                                    tint = if (done) SocialSuccessColor else SocialTextPrimary,
                                    onClick = {
                                        if (!done) {
                                            onInvite(friend.player)
                                            invited = invited + friend.player
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                SocialButton("Done", icon = SOCIAL_ASSETS + "x-close.svg", modifier = Modifier.fillMaxWidth(), filled = true, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RequestCard(request: FriendRequest, incoming: Boolean, onAccept: (() -> Unit)?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(SocialPanelShape)
            .background(SocialCardBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SocialAvatar(request.player, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(PlayerNamesRepository.displayName(request.player), fontSize = 14.sp)
            SocialText(if (incoming) "Incoming request" else "Outgoing request", fontSize = 12.sp, color = SocialTextSecondary)
        }
        if (incoming && onAccept != null) {
            SocialIconButton(SOCIAL_ASSETS + "check-circle.svg", tint = SocialSuccessColor, tooltip = "Accept", onClick = onAccept)
        }
        SocialIconButton(
            SOCIAL_ASSETS + "x-close.svg",
            tint = SocialTextSecondary,
            tooltip = if (incoming) "Decline" else "Cancel request",
            onClick = onDismiss,
        )
    }
}
