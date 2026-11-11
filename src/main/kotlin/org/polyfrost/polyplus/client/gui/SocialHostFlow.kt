@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
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
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ModalPanel
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusClient
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

    SocialModalScrim(onDismiss) {
        HostFlowSteps(state, screen, friends, groups, selfId, onDismiss, onDismiss)
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
private fun HostStepFrame(state: HostFlowState, height: Dp, content: @Composable ColumnScope.() -> Unit) {
    if (state.inline) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        return
    }
    ModalPanel(560.dp, height, PaddingValues(24.dp), 14.dp, Modifier) {
        DialogTitle(state.title)
        content()
    }
}

@Composable
internal fun inviteableGroups(groups: List<GroupSummary>): List<GroupSummary> {
    val specialChatGroupId = SpecialChatRepository.status.collectAsState().value?.groupId
    return groups.filter { it.kind == GroupKind.Group && it.members.size > 1 && it.id != specialChatGroupId }
}

@Composable
private fun WorldSelectionModal(state: HostFlowState, onCancel: () -> Unit, onNext: () -> Unit) {
    var query by remember { mutableStateOf("") }

    HostStepFrame(state, 620.dp) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().height(38.dp),
            placeholder = "Search worlds...",
            leadingIcon = themedIcon(SOCIAL_ASSETS + "search-lg.svg"),
            maxLength = 64,
            invalid = false,
            enabled = true,
            singleLine = true,
            framed = true,
            fontSize = 14.sp,
            label = null,
            masked = false,
            focusRequester = null,
            interactionSource = null,
            trailing = null,
            onSubmit = null,
        )
        TextButton(
            label = "Create New World",
            icon = themedIcon(SOCIAL_ASSETS + "user-plus-01.svg"),
            modifier = Modifier.fillMaxWidth().height(38.dp),
            style = ButtonStyle.Secondary,
            enabled = true,
            onClick = {
                onCancel()
                openSelectWorldScreen()
            },
        )

        val worlds = state.worlds
        val filtered = worlds?.filter { it.name.contains(query, ignoreCase = true) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                worlds == null -> CenteredHint("Loading worlds...")
                filtered.isNullOrEmpty() -> CenteredHint("No worlds found")
                else -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filtered.forEach { entry ->
                        WorldCard(entry, entry.id == state.selected?.id) {
                            state.selected = entry
                            state.gameMode = entry.gameMode
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Cancel", themedIcon(SOCIAL_ASSETS + "x-close.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, true, onCancel)
            TextButton(
                label = "Next",
                icon = themedIcon(SOCIAL_ASSETS + "chevron-right.svg"),
                modifier = Modifier.weight(1f).height(38.dp),
                style = ButtonStyle.Primary,
                enabled = state.selected != null,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SocialText(text, fontSize = 14.sp, color = SocialTextSecondary)
    }
}

@Composable
private fun WorldCard(entry: HostWorldManager.HostWorldEntry, selected: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val border by animateColorAsState(if (selected) OneConfigTheme.accent else if (hovered) SocialHoverBorder else SocialBorderColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(SocialPanelShape)
            .background(if (selected) OneConfigTheme.accent.asSocialSelected else SocialCardBackground)
            .border(SocialBorderWidth, border, SocialPanelShape)
            .hoverable(interaction)
            .clickableWithSound(onClick)
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
                modifier = Modifier.size(64.dp).clip(ppShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(64.dp).clip(ppShape(6.dp)).background(SocialCardBackground))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(entry.name, fontSize = 14.sp)
            SocialText(socialGameModeLabel(entry.gameMode) + " · " + entry.versionName, fontSize = 12.sp, color = SocialTextSecondary)
            SocialText(socialCompatLabel(entry.compat), fontSize = 11.sp, color = socialCompatColor(entry.compat))
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

    HostStepFrame(state, 520.dp) {
        SocialText(
            "Choose how ${state.selected?.name ?: "this world"} will be hosted for your friends.",
            fontSize = 14.sp,
            color = SocialTextSecondary,
        )

        if (!published) {
            FormRow("Host to") {
                SocialDropdown(
                    label = "Visibility",
                    options = listOf(HostMode.FRIENDS, HostMode.LAN),
                    selected = state.hostMode,
                    labelFor = { socialHostModeLabel(it) },
                    modifier = Modifier.width(220.dp),
                    onSelect = { state.hostMode = it },
                )
            }
            FormRow("Game Mode") {
                SocialDropdown(
                    label = "Mode",
                    options = listOf(GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE, GameType.SPECTATOR),
                    selected = state.gameMode,
                    labelFor = { socialGameModeLabel(it) },
                    modifier = Modifier.width(220.dp),
                    onSelect = { state.gameMode = it },
                )
            }
            FormRow("Cheats") {
                Box(Modifier.width(220.dp)) {
                    ToggleRow("Allow Cheats", state.allowCheats, Modifier) { state.allowCheats = !state.allowCheats }
                }
            }
        }
        if (lan) {
            //? if > 1.8.9 {
            FormRow("Port") {
                TextField(
                    value = state.port,
                    onValueChange = { value -> state.port = value.filter { it.isDigit() } },
                    modifier = Modifier.width(220.dp).height(38.dp),
                    placeholder = "Automatic",
                    leadingIcon = null,
                    maxLength = 5,
                    invalid = false,
                    enabled = true,
                    singleLine = true,
                    framed = true,
                    fontSize = 14.sp,
                    label = null,
                    masked = false,
                    focusRequester = null,
                    interactionSource = null,
                    trailing = null,
                    onSubmit = null,
                )
            }
            if (!portValid) {
                SocialText("Port must be between 1 and 65535.", fontSize = 12.sp, color = SocialDangerColor)
            }
            //?}
        } else {
            FormRow("Private Relay") {
                Box(Modifier.width(220.dp)) {
                    ToggleRow("Private Relay", state.privateRelay, Modifier) { state.privateRelay = !state.privateRelay }
                }
            }
            FormRow("Resource Pack") {
                Box(Modifier.width(220.dp)) {
                    ToggleRow("Share", state.autoShareResourcePack, Modifier) { state.autoShareResourcePack = !state.autoShareResourcePack }
                }
            }
        }

        Box(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.skipsSelection) {
                TextButton("Close", themedIcon(SOCIAL_ASSETS + "x-close.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, true, onBack)
            } else {
                TextButton("Back", themedIcon(SOCIAL_ASSETS + "chevron-left.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, true, onBack)
            }
            TextButton(
                label = if (lan) "Host on LAN" else "Next",
                icon = themedIcon(SOCIAL_ASSETS + (if (lan) "log-in-04.svg" else "chevron-right.svg")),
                modifier = Modifier.weight(1f).height(38.dp),
                style = ButtonStyle.Primary,
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
        SocialText(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
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

    HostStepFrame(state, 620.dp) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().height(38.dp),
            placeholder = "Search friends...",
            leadingIcon = themedIcon(SOCIAL_ASSETS + "search-lg.svg"),
            maxLength = 64,
            invalid = false,
            enabled = true,
            singleLine = true,
            framed = true,
            fontSize = 14.sp,
            label = null,
            masked = false,
            focusRequester = null,
            interactionSource = null,
            trailing = null,
            onSubmit = null,
        )

        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (online.isNotEmpty()) {
                SectionLabel("Online")
                online.forEach { friend -> FriendInviteRow(friend, friend.player in state.invitees) { toggled -> state.invitees = if (toggled) state.invitees + friend.player else state.invitees - friend.player } }
            }
            if (inviteableGroups.isNotEmpty() && query.isBlank()) {
                SectionLabel("Groups")
                inviteableGroups.forEach { group -> GroupInviteRow(group, selfId, state.invitees) { members -> state.invitees = state.invitees + members } }
            }
            if (offline.isNotEmpty()) {
                SectionLabel("Offline")
                offline.forEach { friend -> FriendInviteRow(friend, friend.player in state.invitees) { toggled -> state.invitees = if (toggled) state.invitees + friend.player else state.invitees - friend.player } }
            }
            if (filtered.isEmpty() && inviteableGroups.isEmpty()) {
                CenteredHint("No friends found")
            }
        }

        when (val status = eosStatus) {
            EosStatus.Connecting -> SocialText("Connecting to Poly+ multiplayer services...", fontSize = 12.sp, color = SocialTextSecondary)
            is EosStatus.Failed -> SocialText(status.reason + " You can still host on LAN.", fontSize = 12.sp, color = SocialWarnColor)
            EosStatus.Ready -> {}
        }
        state.hostError?.let { SocialText(it, fontSize = 12.sp, color = SocialDangerColor) }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("Back", themedIcon(SOCIAL_ASSETS + "chevron-left.svg"), Modifier.weight(1f).height(38.dp), ButtonStyle.Secondary, !state.hosting, onBack)
            TextButton(
                label = if (state.hosting) "Hosting..." else "Host World",
                icon = themedIcon(SOCIAL_ASSETS + "log-in-04.svg"),
                modifier = Modifier.weight(1f).height(38.dp),
                style = ButtonStyle.Primary,
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
            )
        }
        if (eosStatus !is EosStatus.Failed && !state.hosting && !published) {
            SocialText(
                "Host on LAN instead",
                fontSize = 11.sp,
                color = SocialTextSecondary,
                modifier = Modifier.clickableWithSound {
                    val port = state.port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                    if (state.hostingCurrent) {
                        HostWorldManager.hostCurrentWorldLan(state.gameMode, state.allowCheats, port)
                    } else {
                        val entry = state.selected ?: return@clickableWithSound
                        HostWorldManager.host(screen, entry, state.gameMode, state.allowCheats, port)
                    }
                    onHosted()
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SocialText(text, fontSize = 11.sp, color = SocialTextSecondary)
        Box(Modifier.weight(1f).height(1.dp).background(SocialBorderColor))
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
            SocialText(group.name ?: "Group", fontSize = 14.sp)
            SocialText("${members.size} members", fontSize = 11.sp, color = SocialTextSecondary)
        }
        IconButton(
            iconName = themedIcon(if (allInvited) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg"),
            tooltip = if (allInvited) "Everyone invited" else "Invite everyone in this group",
            label = null,
            modifier = Modifier,
            background = if (allInvited) OneConfigTheme.accent.asSocialSelected else null,
            tint = if (allInvited) Color.White else SocialTextPrimary,
            placement = TooltipPlacement.BelowStart,
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
        SocialText(PlayerNamesRepository.displayName(friend.player), fontSize = 14.sp, modifier = Modifier.weight(1f))
        IconButton(
            iconName = themedIcon(if (invited) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg"),
            tooltip = if (invited) "Invited" else "Invite",
            label = null,
            modifier = Modifier,
            background = if (invited) OneConfigTheme.accent.asSocialSelected else null,
            tint = if (invited) Color.White else SocialTextPrimary,
            placement = TooltipPlacement.BelowStart,
            onClick = { onToggle(!invited) },
        )
    }
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
    HostWorldManager.Compat.OLDER -> SocialWarnColor
    HostWorldManager.Compat.NEWER -> SocialDangerColor
    HostWorldManager.Compat.INCOMPATIBLE -> SocialDangerColor
}
