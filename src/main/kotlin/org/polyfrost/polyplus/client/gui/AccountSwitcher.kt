package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.shell.ShellState.playerName
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.social.SpecialChatRepository
import org.polyfrost.polyplus.client.social.SpecialChatResponses
import java.time.LocalTime
import javax.swing.GroupLayout
//? if = 1.8.9
//import org.polyfrost.polyplus.client.social.singleplayerServer

@Composable
fun AccountSwitcherContent(screen: Screen, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(SocialTab.Chat) }
    var selectedGroupId by remember { mutableStateOf<Int?>(null) }
    var showAddFriend by remember { mutableStateOf(false) }
    var hostFlow by remember { mutableStateOf<HostFlowStep?>(null) }
    var hostingCurrentWorld by remember { mutableStateOf(false) }
    var inviteToGroupId by remember { mutableStateOf<Int?>(null) }
    var showInviteToSession by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var specialFilter by remember { mutableStateOf(SpecialChatResponses.Filter.All) }
    val mutedGroups = remember { mutableStateOf(setOf<Int>()) }
    val renameOverrides = remember { mutableStateOf(mapOf<Int, String>()) }

    val selfId = remember { runCatching { Minecraft.getInstance().user.profileId.toString() }.getOrDefault("") }
    val groups by GroupsRepository.groups.collectAsState()
    val friends by FriendsRepository.friends.collectAsState()
    val incomingRequests by FriendsRepository.incomingRequests.collectAsState()
    val outgoingRequests by FriendsRepository.outgoingRequests.collectAsState()
    val incomingInvites by SessionsRepository.incomingInvites.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val hostedSessionId by P2PSessionManager.currentSessionIdFlow.collectAsState()
    val isSpecialChatTarget = SpecialChatRepository.status.collectAsState().value?.isSpecialChatTarget == true
    val respondedChats by SpecialChatResponses.responded.collectAsState()
    val scanningResponses by SpecialChatResponses.scanning.collectAsState()

    val visibleGroups =
        remember(groups, searchQuery, unreadOnly, specialFilter, respondedChats, selectedGroupId, names) {
            groups.filter { group ->
                (!unreadOnly || group.unread || group.id == selectedGroupId) &&
                        (
                                !group.special || specialFilter == SpecialChatResponses.Filter.All ||
                                        (group.id in respondedChats) == (specialFilter == SpecialChatResponses.Filter.Responded)
                                ) &&
                        (searchQuery.isBlank() || conversationTitle(group, selfId) { names[it] ?: it.take(8) }.contains(
                            searchQuery,
                            ignoreCase = true
                        ))
            }
        }

    LaunchedEffect(specialFilter, groups) {
        if (specialFilter != SpecialChatResponses.Filter.All) SpecialChatResponses.refresh(groups, selfId)
    }

    LaunchedEffect(Unit) {
        if (SocialOverlay.consumeAutoHostCurrentWorld()) {
            hostingCurrentWorld = true
            hostFlow = HostFlowStep.SelectWorld
        }
    }

    fun openConversation(groupId: Int) {
        selectedGroupId = groupId
        val special = groups.firstOrNull { it.id == groupId }?.special == true
        tab = if (isSpecialChatTarget && special) SocialTab.Special else SocialTab.Chat
        GroupsRepository.loadMessages(groupId)
    }

    var friendBaseline by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(friends) {
        val currentIds = friends.map { it.player }.toSet()
        val previous = friendBaseline
        if (previous != null) {
            val added = currentIds - previous
            added.firstOrNull()?.let { newFriend ->
                GroupsRepository.openDirectMessage(newFriend) { result -> result.onSuccess { openConversation(it.id) } }
            }
        }
        friendBaseline = currentIds
    }

    CompositionLocalProvider(LocalDensity provides scaledDensity(SOCIAL_UI_SCALE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialWindowScrim)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .widthIn(max = 1180.dp)
                    .fillMaxHeight(0.86f)
                    .heightIn(max = 760.dp)
                    .clip(SocialWindowShape)
                    .socialGlow()
                    .background(SocialWindowBackground)
                    .border(SocialBorderWidth, SocialBorderColor, SocialWindowShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {


                Row(
                    modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SocialText(text = "Accounts", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    SocialIconButton(SOCIAL_ASSETS + "x-close.svg", tooltip = "Close", onClick = onClose)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    PlayerPreview(
                        Modifier.width(320.dp).fillMaxHeight(),
                        source = PlayerPreviewSource.LocalLive,
                        initialYaw = 180f + 22.9f,
                        live = true,
                        modelScale = 0.35F
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(SocialBorderColor))
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        Column(verticalArrangement = Arrangement.Top) {
                            Row(Modifier.padding(24.dp)) {
                                AccountButton(
                                    enabled = true,
                                    onClick = onClose
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            ) {
                                AddAccountButton(
                                    icon = ASSETS + "globe-01.svg",
                                    text = "Add Microsoft account",
                                    enabled = true,
                                    onClick = onClose,
                                )
                                Spacer(Modifier.padding(12.dp))
                                AddAccountButton(
                                    icon = ASSETS + "plus.svg",
                                    text = "Add offline account",
                                    enabled = true,
                                    onClick = onClose,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val selfId = remember { runCatching { Minecraft.getInstance().user.profileId.toString() }.getOrDefault("") }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .clip(ppShape(8.dp))
            .background(if (hovered && enabled) LocalTheme.current.componentBackground.copy(alpha = 0.4f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .fillMaxWidth()
            .border(1.dp, LocalTheme.current.borderColor, ppShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(selfId, 36.dp)
        Column(verticalArrangement = Arrangement.SpaceEvenly) {
            SocialText(
                playerName(),
                fontSize = 20.sp,
                color = LocalTheme.current.textColor,
                textAlign = TextAlign.Right
            )
            SocialText(
                selfId,
                fontSize = 13.sp,
                color = LocalTheme.current.textColorSecondary,
                textAlign = TextAlign.Right
            )
        }
    }
}

private const val SOCIAL_UI_SCALE = 1.3f