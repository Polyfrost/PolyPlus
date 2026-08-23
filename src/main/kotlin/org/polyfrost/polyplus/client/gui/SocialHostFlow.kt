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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.Difficulty
import net.minecraft.world.level.GameType
import org.jetbrains.skia.Image
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.p2p.EosStatus
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SpecialChatRepository

internal sealed class HostFlowStep {
    object SelectWorld : HostFlowStep()
    object Configure : HostFlowStep()
    object InviteFriends : HostFlowStep()
}

private class HostFlowState(val hostingCurrent: Boolean) {
    var step by mutableStateOf<HostFlowStep>(if (hostingCurrent) HostFlowStep.Configure else HostFlowStep.SelectWorld)
    var worlds by mutableStateOf<List<HostWorldManager.HostWorldEntry>?>(null)
    var selected by mutableStateOf<HostWorldManager.HostWorldEntry?>(null)
    var gameMode by mutableStateOf(GameType.SURVIVAL)
    var difficulty by mutableStateOf(Difficulty.NORMAL)
    var allowCheats by mutableStateOf(false)
    var privateRelay by mutableStateOf(true)
    var autoShareResourcePack by mutableStateOf(false)
    var invitees by mutableStateOf<Set<String>>(emptySet())
    var hostError by mutableStateOf<String?>(null)
    var hosting by mutableStateOf(false)
}

@Composable
internal fun HostWorldFlow(
    screen: Screen,
    friends: List<Friend>,
    groups: List<GroupSummary>,
    selfId: String,
    hostingCurrent: Boolean = false,
    onDismiss: () -> Unit,
) {
    val state = remember { HostFlowState(hostingCurrent) }

    DisposableEffect(Unit) {
        org.polyfrost.polyplus.client.gui.preview.PlayerPreviewDim.push()
        onDispose { org.polyfrost.polyplus.client.gui.preview.PlayerPreviewDim.pop() }
    }

    LaunchedEffect(Unit) {
        if (state.hostingCurrent) return@LaunchedEffect
        val loaded = HostWorldManager.loadWorlds()
        state.worlds = loaded
        state.selected = loaded.firstOrNull()
        state.selected?.let { state.gameMode = it.gameMode }
    }

    Popup(alignment = Alignment.Center, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SocialScrim)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            when (state.step) {
                HostFlowStep.SelectWorld -> WorldSelectionModal(
                    state = state,
                    onCancel = onDismiss,
                    onNext = { state.step = HostFlowStep.Configure },
                )
                HostFlowStep.Configure -> WorldConfigurationModal(
                    state = state,
                    onBack = { if (state.hostingCurrent) onDismiss() else state.step = HostFlowStep.SelectWorld },
                    onNext = { state.step = HostFlowStep.InviteFriends },
                )
                HostFlowStep.InviteFriends -> InviteFriendsModal(
                    state = state,
                    friends = friends,
                    groups = groups,
                    selfId = selfId,
                    screen = screen,
                    onBack = { state.step = HostFlowStep.Configure },
                    onHosted = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ModalPanel(width: Dp, height: Dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(SocialPanelShape)
            .background(SocialPopupBackground)
            .border(SocialBorderWidth, SocialBorderColor, SocialPanelShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun WorldSelectionModal(state: HostFlowState, onCancel: () -> Unit, onNext: () -> Unit) {
    var query by remember { mutableStateOf("") }

    ModalPanel(width = 560.dp, height = 620.dp) {
        SocialText("Select world to host", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        SocialTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search worlds...",
            leadingIcon = SOCIAL_ASSETS + "search-lg.svg",
        )
        SocialButton(
            "Create New World",
            icon = SOCIAL_ASSETS + "user-plus-01.svg",
            modifier = Modifier.fillMaxWidth(),
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
            SocialButton("Cancel", icon = SOCIAL_ASSETS + "x-close.svg", modifier = Modifier.weight(1f), onClick = onCancel)
            SocialButton(
                "Next",
                icon = SOCIAL_ASSETS + "chevron-right.svg",
                modifier = Modifier.weight(1f),
                filled = true,
                enabled = state.selected != null,
                onClick = onNext,
            )
        }
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SocialText(text, fontSize = 13.sp, color = SocialTextSecondary)
    }
}

@Composable
private fun WorldCard(entry: HostWorldManager.HostWorldEntry, selected: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val border by animateColorAsState(if (selected) Accent else if (hovered) SocialHoverBorder else SocialBorderColor)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(SocialPanelShape)
            .background(if (selected) Accent.asSocialSelected else SocialCardBackground)
            .border(SocialBorderWidth, border, SocialPanelShape)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bitmap = remember(entry.iconBytes) {
            entry.iconBytes?.let { bytes ->
                runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
            }
        }
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(ppShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(64.dp).clip(ppShape(6.dp)).background(SocialCardBackground))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(entry.name, fontSize = 15.sp)
            SocialText(socialGameModeLabel(entry.gameMode) + " · " + entry.versionName, fontSize = 12.sp, color = SocialTextSecondary)
            SocialText(socialCompatLabel(entry.compat), fontSize = 11.sp, color = socialCompatColor(entry.compat))
        }
    }
}

@Composable
private fun WorldConfigurationModal(state: HostFlowState, onBack: () -> Unit, onNext: () -> Unit) {
    ModalPanel(width = 560.dp, height = 520.dp) {
        SocialText("Configure world settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        SocialText(
            "Choose how ${state.selected?.name ?: "this world"} will be hosted for your friends.",
            fontSize = 13.sp,
            color = SocialTextSecondary,
        )

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
        FormRow("Difficulty") {
            SocialDropdown(
                label = "Difficulty",
                options = listOf(Difficulty.PEACEFUL, Difficulty.EASY, Difficulty.NORMAL, Difficulty.HARD),
                selected = state.difficulty,
                labelFor = { socialDifficultyLabel(it) },
                modifier = Modifier.width(220.dp),
                onSelect = { state.difficulty = it },
            )
        }
        FormRow("Cheats") {
            Box(Modifier.width(220.dp)) {
                SocialToggle("Allow Cheats", state.allowCheats) { state.allowCheats = !state.allowCheats }
            }
        }
        FormRow("Private Relay") {
            Box(Modifier.width(220.dp)) {
                SocialToggle("Private Relay", state.privateRelay) { state.privateRelay = !state.privateRelay }
            }
        }
        FormRow("Resource Pack") {
            Box(Modifier.width(260.dp)) {
                SocialToggle("Send yours to joining players", state.autoShareResourcePack) { state.autoShareResourcePack = !state.autoShareResourcePack }
            }
        }

        Box(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SocialButton("Back", icon = SOCIAL_ASSETS + "chevron-right.svg", modifier = Modifier.weight(1f), onClick = onBack)
            SocialButton("Next", icon = SOCIAL_ASSETS + "chevron-right.svg", modifier = Modifier.weight(1f), filled = true, onClick = onNext)
        }
    }
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
    val filtered = friends.filter { PlayerNamesRepository.displayName(it.player).contains(query, ignoreCase = true) }
    val online = filtered.filter { it.online }
    val offline = filtered.filter { !it.online }
    val specialChatGroupId = SpecialChatRepository.status.collectAsState().value?.groupId
    val inviteableGroups = groups.filter { it.kind == GroupKind.Group && it.members.size > 1 && it.id != specialChatGroupId }

    ModalPanel(width = 560.dp, height = 620.dp) {
        SocialText("Invite friends to world", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        SocialTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search friends...",
            leadingIcon = SOCIAL_ASSETS + "search-lg.svg",
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
            SocialButton("Back", icon = SOCIAL_ASSETS + "chevron-right.svg", modifier = Modifier.weight(1f), enabled = !state.hosting, onClick = onBack)
            SocialButton(
                if (state.hosting) "Hosting..." else "Host World",
                icon = SOCIAL_ASSETS + "log-in-04.svg",
                modifier = Modifier.weight(1f),
                filled = true,
                enabled = (state.hostingCurrent || state.selected != null) && !state.hosting,
                onClick = {
                    state.hosting = true
                    state.hostError = null
                    if (state.hostingCurrent) {
                        if (eosStatus is EosStatus.Failed) {
                            HostWorldManager.hostCurrentWorldLan(state.gameMode, state.allowCheats)
                            onHosted()
                        } else {
                            HostWorldManager.hostCurrentWorldViaP2P(
                                state.gameMode,
                                state.allowCheats,
                                state.privateRelay,
                                state.autoShareResourcePack,
                                onFailure = {
                                    state.hosting = false
                                    state.hostError = "Unable to start hosting: ${it.message}"
                                },
                                onHosted = { sessionId ->
                                    state.invitees.forEach { player -> SessionsRepository.invite(sessionId, player) }
                                },
                            )
                            onHosted()
                        }
                        return@SocialButton
                    }
                    val entry = state.selected ?: return@SocialButton
                    if (eosStatus is EosStatus.Failed) {
                        HostWorldManager.host(screen, entry, state.gameMode, state.allowCheats)
                        onHosted()
                    } else {
                        HostWorldManager.hostViaP2P(
                            screen,
                            entry,
                            state.gameMode,
                            state.allowCheats,
                            state.privateRelay,
                            state.autoShareResourcePack,
                            onFailure = {
                                state.hosting = false
                                state.hostError = "Unable to start hosting: ${it.message}"
                            },
                            onHosted = { sessionId ->
                                state.invitees.forEach { player -> SessionsRepository.invite(sessionId, player) }
                            },
                        )
                        onHosted()
                    }
                },
            )
        }
        if (eosStatus == EosStatus.Ready && !state.hosting) {
            SocialText(
                "Host on LAN instead",
                fontSize = 11.sp,
                color = SocialTextSecondary,
                modifier = Modifier.clickableWithSound {
                    if (state.hostingCurrent) {
                        HostWorldManager.hostCurrentWorldLan(state.gameMode, state.allowCheats)
                    } else {
                        val entry = state.selected ?: return@clickableWithSound
                        HostWorldManager.host(screen, entry, state.gameMode, state.allowCheats)
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
        SocialIconButton(
            icon = if (allInvited) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg",
            tint = if (allInvited) Color.White else SocialTextPrimary,
            background = if (allInvited) Accent.asSocialSelected else null,
            tooltip = if (allInvited) "Everyone invited" else "Invite everyone in this group",
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
        SocialIconButton(
            icon = if (invited) SOCIAL_ASSETS + "check.svg" else SOCIAL_ASSETS + "user-plus-01.svg",
            tint = if (invited) Color.White else SocialTextPrimary,
            background = if (invited) Accent.asSocialSelected else null,
            tooltip = if (invited) "Invited" else "Invite",
            onClick = { onToggle(!invited) },
        )
    }
}

private fun openSelectWorldScreen() {
    val mc = net.minecraft.client.Minecraft.getInstance()
    val target = net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(net.minecraft.client.gui.screens.TitleScreen())
    //? if >= 26.2 {
    mc.gui.setScreen(target)
    //?} else {
    /*mc.setScreen(target)
    *///?}
}

private fun socialGameModeLabel(mode: GameType): String = mode.getName().replaceFirstChar { it.uppercase() }

private fun socialDifficultyLabel(difficulty: Difficulty): String = when (difficulty) {
    Difficulty.PEACEFUL -> "Peaceful"
    Difficulty.EASY -> "Easy"
    Difficulty.NORMAL -> "Normal"
    Difficulty.HARD -> "Hard"
}

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
