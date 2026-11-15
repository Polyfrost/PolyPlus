@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.world.level.GameType
//?} else {
/*import net.minecraft.client.gui.screen.menu.world.SelectWorldScreen
import net.minecraft.world.WorldSettings.GameMode as GameType
import org.polyfrost.polyplus.client.social.setScreen
*///?}
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.LinkText
import org.polyfrost.oneconfig.api.ui.v1.components.SearchField
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SelectOpenStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectValueAlignment
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.selectableCard
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.gui.lobbysocial.SEARCH_MAX_LENGTH
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.p2p.EosStatus
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SpecialChatRepository
import org.polyfrost.polyplus.client.social.toUserMessage
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog

internal enum class HostFlowStep { SelectWorld, Configure, InviteFriends }

internal enum class HostMode {
    FRIENDS,
    LAN,
}

internal class HostFlowState(val hostingCurrent: Boolean, preselected: Boolean, val inline: Boolean = false) {
    val skipsSelection = hostingCurrent || preselected
    var step by mutableStateOf(if (skipsSelection) HostFlowStep.Configure else HostFlowStep.SelectWorld)
    var hostMode by mutableStateOf(HostMode.FRIENDS)
    var port by mutableStateOf("")
    var worlds by mutableStateOf<List<HostWorldManager.HostWorldEntry>?>(null)
    var selected by mutableStateOf<HostWorldManager.HostWorldEntry?>(null)
    var gameMode by mutableStateOf(GameType.SURVIVAL)
    var allowCheats by mutableStateOf(false)
    var privateRelay by mutableStateOf(true)
    var autoShareResourcePack by mutableStateOf(false)
    var invitees by mutableStateOf<Set<String>>(emptySet())
    var hostError by mutableStateOf<String?>(null)
    var hosting by mutableStateOf(false)
    var forFriends by mutableStateOf(false)

    val title: String
        get() = when (step) {
            HostFlowStep.SelectWorld -> "Select world to host"
            HostFlowStep.Configure -> "Configure world settings"
            HostFlowStep.InviteFriends -> "Invite friends to world"
        }

    fun reset() {
        step = if (skipsSelection) HostFlowStep.Configure else HostFlowStep.SelectWorld
        hostMode = HostMode.FRIENDS
        invitees = emptySet()
        hostError = null
        hosting = false
        forFriends = false
    }
}

@Composable
internal fun HostWorldFlow(
    screen: Screen,
    friends: List<Friend>,
    groups: List<GroupSummary>,
    selfId: String,
    hostingCurrent: Boolean = false,
    worldId: String? = null,
    onDismiss: () -> Unit,
) {
    val state = remember { HostFlowState(hostingCurrent, worldId != null) }

    LaunchedEffect(Unit) {
        P2PSessionManager.retryLoginNow()
        if (state.hostingCurrent) return@LaunchedEffect
        val loaded = HostWorldManager.loadWorlds()
        state.worlds = loaded
        state.selected = if (worldId == null) loaded.firstOrNull() else loaded.firstOrNull { it.id == worldId }
        if (worldId != null && state.selected == null) {
            onDismiss()
            return@LaunchedEffect
        }
        state.selected?.let { state.gameMode = it.gameMode }
    }

    ModalDialog(
        onDismiss,
        modifier = Modifier.height(if (state.step == HostFlowStep.Configure) 520.dp else 620.dp),
        width = 560.dp,
        title = { DialogTitle(state.title) },
        padding = PaddingValues(24.dp),
        spacing = 14.dp,
        scrimColor = SocialScrim,
        scrollBody = false,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HostFlowSteps(state, screen, friends, groups, selfId, onDismiss, onDismiss)
        }
    }
}

@Composable
internal fun HostFlowSteps(
    state: HostFlowState,
    screen: Screen,
    friends: List<Friend>,
    groups: List<GroupSummary>,
    selfId: String,
    onDismiss: () -> Unit,
    onHosted: () -> Unit,
) {
    when (state.step) {
        HostFlowStep.SelectWorld -> WorldSelectionModal(
            state = state,
            onCancel = onDismiss,
            onNext = { state.step = HostFlowStep.Configure },
        )
        HostFlowStep.Configure -> WorldConfigurationModal(
            state = state,
            screen = screen,
            onBack = { if (state.skipsSelection) onDismiss() else state.step = HostFlowStep.SelectWorld },
            onNext = { state.step = HostFlowStep.InviteFriends },
            onHosted = onHosted,
        )
        HostFlowStep.InviteFriends -> InviteFriendsModal(
            state = state,
            friends = friends,
            groups = groups,
            selfId = selfId,
            screen = screen,
            onBack = { state.step = HostFlowStep.Configure },
            onHosted = onHosted,
        )
    }
}

@Composable
private fun HostStepFrame(state: HostFlowState, content: @Composable ColumnScope.() -> Unit) {
    if (state.inline) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
internal fun inviteableGroups(groups: List<GroupSummary>): List<GroupSummary> {
    val specialChatGroupId = SpecialChatRepository.status.collectAsState().value?.groupId
    return groups.filter { it.kind == GroupKind.Group && it.members.size > 1 && it.id != specialChatGroupId }
}

@Composable
private fun WorldSelectionModal(state: HostFlowState, onCancel: () -> Unit, onNext: () -> Unit) {
    var query by remember { mutableStateOf("") }

    HostStepFrame(state) {
        SearchField(
            query = query,
            onQueryChange = { query = it.take(SEARCH_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search worlds...",
            clearable = false,
        )
        TextButton(
            text = "Create New World",
            icon = SOCIAL_ASSETS + "user-plus-01.svg",
            modifier = Modifier.fillMaxWidth(),
            style = ControlStyle.Secondary,
            enabled = true,
            onClick = {
                onCancel()
                openSelectWorldScreen()
            },
            size = ControlSize.Large,
        )

        val worldScroll = rememberScrollState()
        val worlds = state.worlds
        val filtered = worlds?.filter { it.name.contains(query, ignoreCase = true) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                worlds == null -> CenteredHint("Loading worlds...")
                filtered.isNullOrEmpty() -> CenteredHint("No worlds found")
                else -> ScrollContainer(worldScroll, Modifier.fillMaxSize(), scrollbar = false) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        filtered.forEach { entry ->
                            WorldCard(entry, entry.id == state.selected?.id) {
                                state.selected = entry
                                state.gameMode = entry.gameMode
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "Cancel",
                icon = SOCIAL_ASSETS + "x-close.svg",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                style = ControlStyle.Secondary,
                enabled = true,
                size = ControlSize.Large,
            )
            TextButton(
                text = "Next",
                icon = SOCIAL_ASSETS + "chevron-right.svg",
                modifier = Modifier.weight(1f),
                style = ControlStyle.Primary,
                enabled = state.selected != null,
                onClick = onNext,
                size = ControlSize.Large,
            )
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = SocialTextSecondary, style = OneConfigTheme.current.typography.body)
    }
}

@Composable
private fun WorldCard(entry: HostWorldManager.HostWorldEntry, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(SocialPanelShape)
            .background(SocialCardBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .selectableCard(selected, onClick, entry.name, SocialPanelShape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bitmap = remember(entry.iconBytes) {
            entry.iconBytes?.let { bytes ->
                runCatching { PolyBitmap.decode(bytes)!!.asComposeImageBitmap() }.getOrNull()
            }
        }
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(OneConfigTheme.shape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(64.dp).clip(OneConfigTheme.shape(6.dp)).background(SocialCardBackground))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.name, style = OneConfigTheme.current.typography.body)
            Text(socialGameModeLabel(entry.gameMode) + " · " + entry.versionName, color = SocialTextSecondary, style = OneConfigTheme.current.typography.small)
            Text(socialCompatLabel(entry.compat), color = socialCompatColor(entry.compat), style = OneConfigTheme.current.typography.caption)
        }
    }
}

@Composable
private fun WorldConfigurationModal(
    state: HostFlowState,
    screen: Screen,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onHosted: () -> Unit,
) {
    val published = state.hostingCurrent && SocialHosting.lanOpen
    val lan = state.hostMode == HostMode.LAN && !published
    val portValid = state.port.isBlank() || state.port.trim().toIntOrNull()?.let { it in 1..65535 } == true

    HostStepFrame(state) {
        Text(
            "Choose how ${state.selected?.name ?: "this world"} will be hosted for your friends.",
            color = SocialTextSecondary,
            style = OneConfigTheme.current.typography.body,
        )

        if (!published) {
            FormRow("Host to") {
                Select(
                    selected = state.hostMode,
                    options = listOf(HostMode.FRIENDS, HostMode.LAN),
                    onSelect = { state.hostMode = it },
                    modifier = Modifier.width(220.dp),
                    label = { socialHostModeLabel(it) },
                    size = ControlSize.Large,
                    leadingLabel = "Visibility",
                    valueAlignment = SelectValueAlignment.End,
                    openStyle = SelectOpenStyle.Accent,
                )
            }
            FormRow("Game Mode") {
                Select(
                    selected = state.gameMode,
                    options = listOf(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR),
                    onSelect = { state.gameMode = it },
                    modifier = Modifier.width(220.dp),
                    label = { socialGameModeLabel(it) },
                    size = ControlSize.Large,
                    leadingLabel = "Mode",
                    valueAlignment = SelectValueAlignment.End,
                    openStyle = SelectOpenStyle.Accent,
                )
            }
            FormRow("Cheats") {
                Box(Modifier.width(220.dp)) {
                    ToggleRow(label = "Allow Cheats", checked = state.allowCheats, onCheckedChange = { state.allowCheats = it }, modifier = Modifier)
                }
            }
        }
        if (lan) {
            //? if > 1.8.9 {
            FormRow("Port") {
                TextField(
                    value = state.port,
                    onValueChange = { value -> state.port = value.filter { it.isDigit() } },
                    modifier = Modifier.width(220.dp),
                    size = ControlSize.Large,
                    placeholder = "Automatic",
                    maxLength = 5,
                    fontSize = 14.sp,
                )
            }
            if (!portValid) {
                Text("Port must be between 1 and 65535.", color = SocialDangerText, style = OneConfigTheme.current.typography.small)
            }
            //?}
        } else {
            FormRow("Private Relay") {
                Box(Modifier.width(220.dp)) {
                    ToggleRow(label = "Private Relay", checked = state.privateRelay, onCheckedChange = { state.privateRelay = it }, modifier = Modifier)
                }
            }
            FormRow("Resource Pack") {
                Box(Modifier.width(220.dp)) {
                    ToggleRow(label = "Share", checked = state.autoShareResourcePack, onCheckedChange = { state.autoShareResourcePack = it }, modifier = Modifier)
                }
            }
        }

        Box(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.skipsSelection) {
                TextButton(
                    text = "Close",
                    icon = SOCIAL_ASSETS + "x-close.svg",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    style = ControlStyle.Secondary,
                    enabled = true,
                    size = ControlSize.Large,
                )
            } else {
                TextButton(
                    text = "Back",
                    icon = SOCIAL_ASSETS + "chevron-left.svg",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    style = ControlStyle.Secondary,
                    enabled = true,
                    size = ControlSize.Large,
                )
            }
            TextButton(
                text = if (lan) "Host on LAN" else "Next",
                icon = SOCIAL_ASSETS + (if (lan) "log-in-04.svg" else "chevron-right.svg"),
                modifier = Modifier.weight(1f),
                style = ControlStyle.Primary,
                enabled = !lan || portValid,
                onClick = {
                    if (!lan) {
                        onNext()
                        return@TextButton
                    }
                    val port = state.port.trim().toIntOrNull()
                    if (state.hostingCurrent) {
                        HostWorldManager.hostCurrentWorldLan(state.gameMode, state.allowCheats, port)
                    } else {
                        val entry = state.selected ?: return@TextButton
                        HostWorldManager.host(screen, entry, state.gameMode, state.allowCheats, port)
                    }
                    onHosted()
                },
                size = ControlSize.Large,
            )
        }
    }
}

private fun socialHostModeLabel(mode: HostMode): String = when (mode) {
    HostMode.FRIENDS -> "Friends (Poly+)"
    HostMode.LAN -> "LAN"
}

@Composable
private fun FormRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.body)
        content()
    }
}

@Composable
private fun InviteFriendsModal(
    state: HostFlowState,
    friends: List<Friend>,
    groups: List<GroupSummary>,
    selfId: String,
    screen: Screen,
    onBack: () -> Unit,
    onHosted: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val eosStatus by P2PSessionManager.status.collectAsState()
    val published = state.hostingCurrent && SocialHosting.lanOpen
    val filtered = friends.filter { PlayerNamesRepository.displayName(it.player).contains(query, ignoreCase = true) }
    val online = filtered.filter { it.online }
    val offline = filtered.filter { !it.online }
    val inviteableGroups = inviteableGroups(groups)

    HostStepFrame(state) {
        SearchField(
            query = query,
            onQueryChange = { query = it.take(SEARCH_MAX_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Search friends...",
            clearable = false,
        )

        val friendScroll = rememberScrollState()
        ScrollContainer(friendScroll, Modifier.weight(1f).fillMaxWidth(), scrollbar = false) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (online.isNotEmpty()) {
                    SectionHeader("Online", rule = true)
                    online.forEach { friend -> FriendInviteRow(friend, friend.player in state.invitees) { toggled -> state.invitees = if (toggled) state.invitees + friend.player else state.invitees - friend.player } }
                }
                if (inviteableGroups.isNotEmpty() && query.isBlank()) {
                    SectionHeader("Groups", rule = true)
                    inviteableGroups.forEach { group -> GroupInviteRow(group, selfId, state.invitees) { members -> state.invitees = state.invitees + members } }
                }
                if (offline.isNotEmpty()) {
                    SectionHeader("Offline", rule = true)
                    offline.forEach { friend -> FriendInviteRow(friend, friend.player in state.invitees) { toggled -> state.invitees = if (toggled) state.invitees + friend.player else state.invitees - friend.player } }
                }
                if (filtered.isEmpty() && inviteableGroups.isEmpty()) {
                    CenteredHint("No friends found")
                }
            }
        }

        when (val status = eosStatus) {
            EosStatus.Connecting -> Text("Connecting to Poly+ multiplayer services...", color = SocialTextSecondary, style = OneConfigTheme.current.typography.small)
            is EosStatus.Failed -> Text(status.reason + " You can still host on LAN.", color = OneConfigTheme.current.warning, style = OneConfigTheme.current.typography.small)
            EosStatus.Ready -> {}
        }
        state.hostError?.let { Text(it, color = SocialDangerText, style = OneConfigTheme.current.typography.small) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = "Back",
                icon = SOCIAL_ASSETS + "chevron-left.svg",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                style = ControlStyle.Secondary,
                enabled = !state.hosting,
                size = ControlSize.Large,
            )
            TextButton(
                text = if (state.hosting) "Hosting..." else "Host World",
                icon = SOCIAL_ASSETS + "log-in-04.svg",
                modifier = Modifier.weight(1f),
                style = ControlStyle.Primary,
                // P2P hosting fails outright until the login finishes, so leave only the LAN option until then
                enabled = (state.hostingCurrent || state.selected != null) && !state.hosting && eosStatus != EosStatus.Connecting,
                onClick = {
                    state.hosting = true
                    state.hostError = null
                    if (eosStatus is EosStatus.Failed) {
                        if (state.hostingCurrent) {
                            HostWorldManager.hostCurrentWorldLan(state.gameMode, state.allowCheats)
                        } else {
                            HostWorldManager.host(screen, state.selected ?: return@TextButton, state.gameMode, state.allowCheats)
                        }
                        onHosted()
                        return@TextButton
                    }
                    val invite = { sessionId: String -> SocialHosting.invite(sessionId, state.invitees) }
                    PolyPlusClient.SCOPE.launch {
                        val result = if (state.hostingCurrent) {
                            HostWorldManager.hostCurrentWorldViaP2P(state.gameMode, state.allowCheats, state.privateRelay, state.autoShareResourcePack, invite)
                        } else {
                            val entry = state.selected ?: return@launch
                            HostWorldManager.hostViaP2P(screen, entry, state.gameMode, state.allowCheats, state.privateRelay, state.autoShareResourcePack, invite)
                        }
                        result
                            .onSuccess { onHosted() }
                            .onFailure {
                                state.hosting = false
                                state.hostError = it.toUserMessage("Couldn't start hosting. Check your internet connection and try again.")
                            }
                    }
                },
                size = ControlSize.Large,
            )
        }
        if (eosStatus !is EosStatus.Failed && !state.hosting && !published) {
            LinkText(
                text = "Host on LAN instead",
                onClick = {
                    val port = state.port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                    if (state.hostingCurrent) {
                        HostWorldManager.hostCurrentWorldLan(state.gameMode, state.allowCheats, port)
                    } else {
                        val entry = state.selected ?: return@LinkText
                        HostWorldManager.host(screen, entry, state.gameMode, state.allowCheats, port)
                    }
                    onHosted()
                },
            )
        }
    }
}

@Composable
private fun GroupInviteRow(group: GroupSummary, selfId: String, invitees: Set<String>, onInviteAll: (List<String>) -> Unit) {
    val members = group.members.filterNot { it == selfId }
    val allInvited = members.isNotEmpty() && members.all { it in invitees }
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialGroupAvatar(members, 32.dp, ringColor = SocialPopupBackground)
        Column(Modifier.weight(1f)) {
            Text(group.name ?: "Group", style = OneConfigTheme.current.typography.body)
            Text("${members.size} members", color = SocialTextSecondary, style = OneConfigTheme.current.typography.caption)
        }
        InviteToggleButton(
            invited = allInvited,
            label = if (allInvited) "Everyone invited" else "Invite everyone in this group",
            onClick = { if (!allInvited) onInviteAll(members) },
        )
    }
}

@Composable
private fun FriendInviteRow(friend: Friend, invited: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SocialAvatar(friend.player, 32.dp)
        Text(PlayerNamesRepository.displayName(friend.player), modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.body)
        InviteToggleButton(invited, if (invited) "Invited" else "Invite") { onToggle(!invited) }
    }
}

@Composable
private fun InviteToggleButton(invited: Boolean, label: String, onClick: () -> Unit) {
    val shape = IconButtonDefaults.shape(IconButtonStyle.Ghost)
    IconButton(
        icon = if (invited) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg",
        contentDescription = label,
        onClick = onClick,
        modifier = if (invited) Modifier.size(34.dp).clip(shape).background(OneConfigTheme.accent.asSocialSelected) else Modifier.size(34.dp),
        tooltipPlacement = TooltipPlacement.BelowStart,
        tint = SocialTextPrimary,
        shape = shape,
    )
}

private fun openSelectWorldScreen() {
    val mc = Minecraft.getInstance()
    val target = SelectWorldScreen(TitleScreen())
    //? if >= 26.2 {
    mc.gui.setScreen(target)
    //?} else {
    /*mc.setScreen(target)
    *///?}
}

//? if > 1.8.9 {
private fun socialGameModeLabel(mode: GameType): String = mode.getName().replaceFirstChar { it.uppercase() }
//?} else
//private fun socialGameModeLabel(mode: GameType): String = mode.key.replaceFirstChar { it.uppercase() }

private fun socialCompatLabel(compat: HostWorldManager.Compat): String = when (compat) {
    HostWorldManager.Compat.CURRENT -> "Compatible"
    HostWorldManager.Compat.OLDER -> "Needs backup/conversion"
    HostWorldManager.Compat.NEWER -> "Created on a newer version"
    HostWorldManager.Compat.INCOMPATIBLE -> "Incompatible"
}

@Composable
private fun socialCompatColor(compat: HostWorldManager.Compat) = when (compat) {
    HostWorldManager.Compat.CURRENT -> SocialTextSecondary
    HostWorldManager.Compat.OLDER -> OneConfigTheme.current.warning
    HostWorldManager.Compat.NEWER -> SocialDangerText
    HostWorldManager.Compat.INCOMPATIBLE -> SocialDangerText
}
