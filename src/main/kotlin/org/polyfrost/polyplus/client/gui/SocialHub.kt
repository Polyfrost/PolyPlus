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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.social.SpecialChatRepository
import org.polyfrost.polyplus.client.social.SpecialChatResponses

@Composable
fun SocialOverlayContent(screen: Screen, onClose: () -> Unit) {
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

    val visibleGroups = remember(groups, searchQuery, unreadOnly, specialFilter, respondedChats, selectedGroupId, names) {
        groups.filter { group ->
            (!unreadOnly || group.unread || group.id == selectedGroupId) &&
                (
                    !group.special || specialFilter == SpecialChatResponses.Filter.All ||
                        (group.id in respondedChats) == (specialFilter == SpecialChatResponses.Filter.Responded)
                    ) &&
                (searchQuery.isBlank() || rawConversationTitle(group, selfId, names).contains(searchQuery, ignoreCase = true))
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

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity) {
        Density(density = baseDensity.density * SOCIAL_UI_SCALE, fontScale = baseDensity.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SocialWindowScrim)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onClose() },
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
            SocialHeaderToolbar(
                selfId = selfId,
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                unreadOnly = unreadOnly,
                onToggleUnreadOnly = { unreadOnly = !unreadOnly },
                specialFilter = specialFilter.takeIf { isSpecialChatTarget && tab == SocialTab.Special },
                scanningResponses = scanningResponses,
                onCycleSpecialFilter = {
                    specialFilter = when (specialFilter) {
                        SpecialChatResponses.Filter.All -> SpecialChatResponses.Filter.Awaiting
                        SpecialChatResponses.Filter.Awaiting -> SpecialChatResponses.Filter.Responded
                        SpecialChatResponses.Filter.Responded -> SpecialChatResponses.Filter.All
                    }
                },
                onSearchQueryChange = { searchQuery = it },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) searchQuery = ""
                },
                onBack = onClose,
                onAddFriend = { showAddFriend = true },
                onNewGroup = { showCreateGroup = true },
                onHostWorld = {
                    hostingCurrentWorld = Minecraft.getInstance().singleplayerServer != null
                    hostFlow = HostFlowStep.SelectWorld
                },
                hostedSessionId = hostedSessionId,
                onInviteToSession = { showInviteToSession = true },
                incomingInvites = incomingInvites,
                onOpenInviteConversation = { player ->
                    GroupsRepository.openDirectMessage(player) { result -> result.onSuccess { openConversation(it.id) } }
                },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
            Row(Modifier.fillMaxWidth().weight(1f)) {
                SocialSidebar(
                    modifier = Modifier.fillMaxHeight().width(320.dp),
                    tab = tab,
                    onTabChange = { newTab -> tab = newTab },
                    groups = visibleGroups,
                    showSpecialTab = isSpecialChatTarget,
                    isFiltered = searchQuery.isNotBlank() || unreadOnly || specialFilter != SpecialChatResponses.Filter.All,
                    friends = friends,
                    selfId = selfId,
                    selectedGroupId = selectedGroupId,
                    onSelectGroup = ::openConversation,
                    onSelectFriend = { player ->
                        GroupsRepository.openDirectMessage(player) { result -> result.onSuccess { openConversation(it.id) } }
                    },
                    onAddFriend = { showAddFriend = true },
                )
                Box(Modifier.width(1.dp).fillMaxHeight().background(SocialBorderColor))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when (tab) {
                        SocialTab.Chat, SocialTab.Special -> {
                            val group = groups.firstOrNull { it.id == selectedGroupId }
                                ?.takeIf { tab != SocialTab.Chat || !isSpecialChatTarget || !it.special }
                            if (group != null) {
                                val messages by GroupsRepository.messagesFlow(group.id).collectAsState()
                                ConversationView(
                                    group = group,
                                    selfId = selfId,
                                    messages = messages,
                                    incomingInvites = incomingInvites,
                                    mutedGroups = mutedGroups.value,
                                    renameOverrides = renameOverrides.value,
                                    onToggleMute = { id ->
                                        mutedGroups.value = if (id in mutedGroups.value) mutedGroups.value - id else mutedGroups.value + id
                                    },
                                    onRename = { id, newName -> renameOverrides.value = renameOverrides.value + (id to newName) },
                                    onInviteToGroup = { inviteToGroupId = group.id },
                                    onLeaveGroup = {
                                        GroupsRepository.removeMember(group.id, selfId)
                                        selectedGroupId = null
                                    },
                                )
                            } else {
                                EmptyConversationHint(
                                    friendCount = friends.size,
                                    onAddFriend = { showAddFriend = true },
                                )
                            }
                        }
                        SocialTab.Global -> GlobalChatView(selfId = selfId)
                        SocialTab.Friends -> FriendsManagementView(
                            friends = friends,
                            incomingRequests = incomingRequests,
                            outgoingRequests = outgoingRequests,
                            onOpenConversation = ::openConversation,
                        )
                    }
                }
            }
        }

        if (showAddFriend) {
            AddFriendDialog(onDismiss = { showAddFriend = false })
        }
        if (showCreateGroup) {
            CreateGroupDialog(
                friends = friends,
                onCreate = { name, members ->
                    showCreateGroup = false
                    GroupsRepository.createGroup(name, members) { result -> result.onSuccess { openConversation(it.id) } }
                },
                onDismiss = { showCreateGroup = false },
            )
        }
        hostFlow?.let {
            HostWorldFlow(
                screen = screen,
                friends = friends,
                groups = groups,
                selfId = selfId,
                hostingCurrent = hostingCurrentWorld,
                onDismiss = { hostFlow = null },
            )
        }
        inviteToGroupId?.let { groupId ->
            val group = groups.firstOrNull { it.id == groupId }
            if (group != null) {
                InviteToGroupDialog(
                    friends = friends.filterNot { it.player in group.members },
                    onInvite = { player -> GroupsRepository.addMember(groupId, player) },
                    onDismiss = { inviteToGroupId = null },
                )
            } else {
                inviteToGroupId = null
            }
        }
        if (showInviteToSession) {
            val sessionId = hostedSessionId
            if (sessionId != null) {
                InviteToGroupDialog(
                    friends = friends,
                    onInvite = { player -> SessionsRepository.invite(sessionId, player) },
                    onDismiss = { showInviteToSession = false },
                )
            } else {
                showInviteToSession = false
            }
        }

        SocialToastHost(Modifier.align(Alignment.TopEnd).padding(20.dp))
    }
    }
}

private const val SOCIAL_UI_SCALE = 1.3f

@Composable
private fun EmptyConversationHint(friendCount: Int = -1, onAddFriend: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (friendCount == 0 && onAddFriend != null) {
                SocialText("Welcome to Social!", fontSize = 15.sp)
                SocialText("Add a friend to start chatting", fontSize = 12.sp, color = SocialTextSecondary)
                Spacer(Modifier.height(12.dp))
                SocialButton("Add Friend", icon = SOCIAL_ASSETS + "user-plus-01.svg", filled = true, onClick = onAddFriend)
            } else {
                SocialText("Select a conversation", fontSize = 15.sp, color = SocialTextSecondary)
                SocialText("Pick a friend or group from the left to start chatting", fontSize = 12.sp, color = SocialTextSecondary)
            }
        }
    }
}

@Composable
private fun SocialHeaderToolbar(
    selfId: String,
    searchOpen: Boolean,
    searchQuery: String,
    unreadOnly: Boolean,
    onToggleUnreadOnly: () -> Unit,
    specialFilter: SpecialChatResponses.Filter?,
    scanningResponses: Boolean,
    onCycleSpecialFilter: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onBack: () -> Unit,
    onAddFriend: () -> Unit,
    onNewGroup: () -> Unit,
    onHostWorld: () -> Unit,
    hostedSessionId: String?,
    onInviteToSession: () -> Unit,
    incomingInvites: List<SessionInvite>,
    onOpenInviteConversation: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialIconButton(SOCIAL_ASSETS + "x-close.svg", tooltip = "Close", onClick = onBack)
        SocialIconButton(SOCIAL_ASSETS + "user-plus-01.svg", tooltip = "Add Friend", onClick = onAddFriend)
        SocialIconButton(SOCIAL_ASSETS + "plus.svg", tooltip = "New Group", onClick = onNewGroup)
        if (hostedSessionId == null) {
            SocialIconButton(SOCIAL_ASSETS + "log-in-04.svg", tooltip = "Host World", onClick = onHostWorld)
        }
        if (hostedSessionId != null) {
            SocialIconButton(
                SOCIAL_ASSETS + "user-plus-01.svg",
                tooltip = "Invite Friends to World",
                background = Accent.asSocialSelected,
                tint = Accent,
                onClick = onInviteToSession,
            )
        }
        WorldInvitesButton(incomingInvites, onOpenInviteConversation)

        if (searchOpen) {
            SocialTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search conversations...",
                leadingIcon = SOCIAL_ASSETS + "search-lg.svg",
                modifier = Modifier.weight(1f).width(260.dp),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }

        if (specialFilter != null) {
            SocialIconButton(
                SOCIAL_ASSETS + if (scanningResponses) "loading-02.svg" else "check-circle.svg",
                tooltip = when {
                    scanningResponses -> "Checking replies..."
                    specialFilter == SpecialChatResponses.Filter.Awaiting -> "Showing chats awaiting a reply"
                    specialFilter == SpecialChatResponses.Filter.Responded -> "Showing chats that replied"
                    else -> "Filter by whether they replied"
                },
                background = if (specialFilter != SpecialChatResponses.Filter.All) Accent.asSocialSelected else null,
                tint = when (specialFilter) {
                    SpecialChatResponses.Filter.Awaiting -> SocialWarnColor
                    SpecialChatResponses.Filter.Responded -> Accent
                    else -> SocialTextPrimary
                },
                onClick = onCycleSpecialFilter,
            )
        }

        SocialIconButton(
            SOCIAL_ASSETS + "message-chat-circle.svg",
            tooltip = if (unreadOnly) "Show all conversations" else "Show unread only",
            background = if (unreadOnly) Accent.asSocialSelected else null,
            tint = if (unreadOnly) Accent else SocialTextPrimary,
            onClick = onToggleUnreadOnly,
        )

        SocialIconButton(
            SOCIAL_ASSETS + "search-lg.svg",
            tooltip = if (searchOpen) "Close search" else "Search conversations",
            background = if (searchOpen) Accent.asSocialSelected else null,
            onClick = onToggleSearch,
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialAvatar(selfId, 30.dp)
            SocialText(localPlayerName(), fontSize = 14.sp)
        }
    }
}

@Composable
private fun WorldInvitesButton(invites: List<SessionInvite>, onOpenConversation: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val unanswered = invites.count { it.status == "pending" }
    Box {
        SocialIconButton(
            SOCIAL_ASSETS + "bell-01.svg",
            tooltip = "World invites",
            background = if (unanswered > 0) SocialWarnColor.copy(alpha = 0.18f) else null,
            tint = if (unanswered > 0) SocialWarnColor else SocialTextPrimary,
            onClick = { open = !open },
        )
        if (unanswered > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .clip(LocalTheme.current.circleShape)
                    .background(SocialWarnColor)
                    .padding(horizontal = 4.dp),
            ) {
                SocialText(unanswered.toString(), fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (open) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 44),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(320.dp)
                        .clip(SocialPanelShape)
                        .background(SocialPopupBackground)
                        .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SocialText("World Invites", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (invites.isEmpty()) {
                        SocialText("No world invites", fontSize = 12.sp, color = SocialTextSecondary)
                    } else {
                        invites.forEach { invite ->
                            val (interaction, hovered) = rememberSocialHover()
                            val rowBackground by animateColorAsState(if (hovered) SocialHoverOverlay else Color.Transparent)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(SocialFieldShape)
                                    .background(rowBackground)
                                    .hoverable(interaction)
                                    .clickableWithSound { onOpenConversation(invite.sender); open = false }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SocialAvatar(invite.sender, 28.dp)
                                SocialText(PlayerNamesRepository.displayName(invite.sender), fontSize = 13.sp, modifier = Modifier.weight(1f))
                                SocialIconButton(SOCIAL_ASSETS + "x-close.svg", tooltip = "Decline", onClick = { SessionsRepository.decline(invite) })
                                SocialIconButton(
                                    SOCIAL_ASSETS + "check.svg",
                                    tint = Color.White,
                                    tooltip = if (invite.status == "accepted") "Re-join" else "Join",
                                    onClick = { open = false; SessionsRepository.accept(invite) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun localPlayerName(): String = runCatching { Minecraft.getInstance().user.name }.getOrDefault("Player")

internal fun rawConversationTitle(group: GroupSummary, selfId: String, names: Map<String, String>): String {
    if (group.kind == GroupKind.Group) {
        group.name?.let { return it }
        val others = group.members.filterNot { it == selfId }
        return if (others.isEmpty()) "Group" else others.joinToString { names[it] ?: it.take(8) }
    }
    val other = group.members.firstOrNull { it != selfId } ?: return "You"
    return names[other] ?: other.take(8)
}
