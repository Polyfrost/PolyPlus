@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.foundation.layout.height
import org.polyfrost.polyplus.client.gui.KitDangerColor
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.polyfrost.compose.host.controller.ControllerButton
import org.polyfrost.compose.host.controller.controllerAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Confirm
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.ModalDialog
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.network.http.responses.MemberState
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyInvite
import org.polyfrost.polyplus.client.network.http.responses.PartyInvitePolicy
import org.polyfrost.polyplus.client.network.http.responses.PartyMember
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.PartyJoin
import org.polyfrost.polyplus.client.party.PartyPresence
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.roundToInt
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.oneconfig.api.ui.v1.components.ChipDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.components.UiSound
import org.polyfrost.oneconfig.api.ui.v1.components.playUiSound
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons

internal val PartyPopoverWidth = 260.dp
private val MenuWidth = 280.dp
private val PickerWidth = 300.dp
private val PartyCardWidth = 300.dp
private val PopoverGap = 14.dp
private const val PERCENT = 100f
private const val SLIDER_MAX_PERCENT = 200f
private const val SLIDER_STEP_PERCENT = 5f

internal object PartyIcons {
    const val MIC = SOCIAL_ASSETS + "microphone-01.svg"
    const val MIC_OFF = SOCIAL_ASSETS + "microphone-off-01.svg"
    const val HEADPHONES = SOCIAL_ASSETS + "headphones-02.svg"
    const val HEADPHONES_OFF = SOCIAL_ASSETS + "headphones-off-02.svg"
    const val INVITE = SOCIAL_ASSETS + "user-plus-01.svg"
    const val MORE = SOCIAL_ASSETS + "dots-vertical.svg"
    const val SETTINGS = SOCIAL_ASSETS + "settings-02.svg"
}

internal class PartyCell(
    val id: String,
    val name: String,
    val self: Boolean,
    val leader: Boolean,
    val member: PartyMember?,
    val state: MemberState?,
    val detail: String,
    val speaking: Boolean,
    val muted: Boolean,
)

internal fun locationLabel(state: MemberState?, online: Boolean): String {
    if (!online) return "Offline"
    val location = state?.location ?: return "Online"
    return when (location.kind) {
        LocationKind.Menu -> "In menu"
        LocationKind.Singleplayer -> "Singleplayer"
        LocationKind.Server -> location.display?.let { "On $it" } ?: "On a server"
        LocationKind.HostedWorld -> "In a hosted world"
        LocationKind.Unknown -> "Online"
    }
}

private fun voiceLabel(member: PartyMember?, state: MemberState?): String? {
    val voice = state?.voice ?: return null
    if (!voice.inVoice) return null
    return when {
        voice.micBlocked -> "Can't talk (mic blocked)"
        member?.voiceHardMuted == true -> "Muted by leader"
        voice.deafened -> "Deafened"
        voice.muted -> "Muted"
        else -> "In voice"
    }
}

@Composable
internal fun partyCells(party: Party): List<PartyCell> {
    val voice by PartyVoice.ui.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val self = PartyRepository.self(party)
    val selfState = PartyPresence.local.let {
        it.copy(voice = it.voice.copy(inVoice = voice.inVoice, muted = voice.muted, deafened = voice.deafened, micBlocked = voice.micBlocked))
    }
    val selfId = self?.player ?: PartyRepository.localId()
    val local = PartyCell(
        id = selfId,
        name = ClientPlatform.localPlayerName(),
        self = true,
        leader = PartyRepository.isSelf(party.leader),
        member = self,
        state = selfState,
        detail = voiceLabel(self, selfState) ?: "You",
        speaking = voice.talking,
        muted = voice.inVoice && (voice.muted || voice.deafened || voice.micBlocked),
    )
    val others = PartyRepository.others(party).map { member ->
        val state = member.state
        PartyCell(
            id = member.player,
            name = names[member.player] ?: PlayerNamesRepository.nameOr(member.player),
            self = false,
            leader = PartyRepository.samePlayer(member.player, party.leader),
            member = member,
            state = state,
            detail = if (!member.online) "Offline" else voiceLabel(member, state) ?: locationLabel(state, true),
            speaking = member.player in voice.speaking,
            muted = state?.voice?.let { it.inVoice && (it.muted || it.deafened || it.micBlocked) } == true || member.voiceHardMuted,
        )
    }
    return listOf(local) + others
}

@Composable
internal fun rememberAbove(alignEnd: Boolean = false): PopupPositionProvider =
    rememberAnchoredPosition(if (alignEnd) Placement.AboveEnd else Placement.AboveStart, PopoverGap)

private fun joinable(cell: PartyCell): String? {
    if (cell.self) return null
    val location = cell.state?.location ?: return null
    if (location.kind != LocationKind.Server) return null
    val display = location.display ?: return null
    if (location.key != null && location.key == PartyPresence.local.location.key) return null
    return display
}

internal fun memberJoinItem(host: SocialHost, cell: PartyCell, onClose: () -> Unit = {}): MenuAction? {
    LeaderFollow.hostedFor(cell.id)?.let { hosted ->
        return MenuAction(
            hosted.status ?: "Join ${cell.name}'s world",
            {
                onClose()
                LeaderFollow.joinHosted()
            },
            icon = SOCIAL_ASSETS + "log-in-04.svg",
            enabled = !hosted.busy,
        )
    }
    val address = joinable(cell) ?: return null
    return MenuAction(
        "Join ${cell.name}",
        {
            onClose()
            host.joinAddress(address)
        },
        icon = SOCIAL_ASSETS + "log-in-04.svg",
    )
}

internal fun memberMuteItem(cell: PartyCell): MenuAction? {
    if (cell.self) return null
    val mutedForMe = cell.id.lowercase() in PartyVoiceSettings.memberMutes
    return MenuAction(
        if (mutedForMe) "Unmute for me" else "Mute for me",
        { PartyVoiceSettings.toggleMute(cell.id) },
        icon = if (mutedForMe) PartyIcons.MIC else PartyIcons.MIC_OFF,
    )
}

internal fun memberLeaderItems(party: Party, cell: PartyCell, onClose: () -> Unit = {}): List<MenuAction> {
    if (!PartyRepository.isLeader(party) || cell.self) return emptyList()
    val hardMuted = cell.member?.voiceHardMuted == true
    return listOf(
        MenuAction(
            "Make leader",
            {
                onClose()
                PartyRepository.makeLeader(cell.id)
            },
        ),
        MenuAction(
            if (hardMuted) "Let them talk" else "Mute their voice for everyone",
            { PartyRepository.setHardMuted(cell.id, !hardMuted) },
        ),
        MenuAction(
            "Remove from party",
            {
                onClose()
                Confirm.ask("Remove ${cell.name}?", "They will be removed from the party.", "Remove", { PartyRepository.kick(cell.id) }, icon = LobbyIcons.CLOSE)
            },
            danger = true,
            dangerColor = KitDangerColor,
        ),
    )
}

internal fun memberSocialItems(cell: PartyCell): List<MenuAction> {
    if (cell.self) return emptyList()
    if (FriendsRepository.blocked.value.any { PartyRepository.samePlayer(it.player, cell.id) }) return emptyList()
    val friend = FriendsRepository.friends.value.any { PartyRepository.samePlayer(it.player, cell.id) }
    val requested = (FriendsRepository.incomingRequests.value + FriendsRepository.outgoingRequests.value).any { PartyRepository.samePlayer(it.player, cell.id) }
    val add = MenuAction(
        "Add friend",
        { FriendsRepository.sendRequest(cell.id) { SocialNotices.emit("Friend request sent to ${cell.name}") } },
        icon = PartyIcons.INVITE,
    ).takeUnless { friend || requested }
    return listOfNotNull(add, blockMenuItem(cell.name) { FriendsRepository.block(cell.id) })
}

internal fun memberMenuItems(host: SocialHost, party: Party, cell: PartyCell): List<MenuAction> =
    listOfNotNull(memberJoinItem(host, cell), memberMuteItem(cell)) + memberLeaderItems(party, cell) + memberSocialItems(cell)

internal fun partyInviteItem(
    friendId: String,
    party: Party? = PartyRepository.party.value,
    outgoing: List<PartyInvite> = PartyRepository.outgoingInvites.value,
): MenuAction? = when {
    party?.members?.any { PartyRepository.samePlayer(it.player, friendId) } == true -> MenuAction("In your party", {}, enabled = false)
    outgoing.any { PartyRepository.samePlayer(it.recipient, friendId) } -> MenuAction("Invited to party", {}, enabled = false)
    PartyRepository.canInvite(party) -> MenuAction("Invite to party", { PartyRepository.invite(friendId) }, icon = PartyIcons.INVITE)
    else -> null
}

@Composable
internal fun MemberPopoverContent(host: SocialHost, party: Party, cell: PartyCell, onClose: () -> Unit) {
    val theme = OneConfigTheme.current
    val join = memberJoinItem(host, cell, onClose)
    val mute = memberMuteItem(cell)
    val joinIcon = join?.icon
    val muteIcon = mute?.icon
    val leaderItems = memberLeaderItems(party, cell, onClose)
    Column(Modifier.width(PartyPopoverWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerHead(seedOf(cell.id), 38.dp, Modifier.then(if (cell.speaking) Modifier.border(1.dp, SocialSuccessColor, headShape(38.dp)) else Modifier), shape = headShape(38.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (cell.leader) "${cell.name} · Leader" else cell.name, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(locationLabel(cell.state, cell.self || cell.member?.online == true), color = theme.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                voiceLabel(cell.member, cell.state)?.let { Text(it, color = theme.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
            }
        }
        join?.let { OptionalIconTextButton(it.label, joinIcon, it.onClick, Modifier.fillMaxWidth(), ControlStyle.Primary, true) }
        mute?.let {
            MemberVolume(cell.id)
            OptionalIconTextButton(it.label, muteIcon, it.onClick, Modifier.fillMaxWidth(), ControlStyle.Secondary, true)
        }
        if (leaderItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                leaderItems.forEach { item ->
                    MenuItem(item.label, item.onClick, size = MenuItemSize.Compact, enabled = item.enabled, danger = item.danger)
                }
            }
        }
    }
}

@Composable
private fun MemberVolume(player: String) {
    val volumes = PartyVoiceSettings.memberVolumes
    val volume = volumes[player.lowercase()] ?: 1f
    VolumeRow(volume * PERCENT) { percent -> PartyVoiceSettings.setVolume(player, percent / PERCENT) }
}

@Composable
private fun VolumeRow(
    percent: Float,
    modifier: Modifier = Modifier,
    label: String? = "Volume",
    onRelease: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        label?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        Slider(
            value = percent,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            range = 0f..SLIDER_MAX_PERCENT,
            step = SLIDER_STEP_PERCENT,
            onValueChangeFinished = { onRelease().also { playUiSound(UiSound.Click) } },
            sound = null,
        )
        Text("${percent.roundToInt()}%", style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
internal fun DeviceVolume(input: Boolean, modifier: Modifier = Modifier.padding(horizontal = 10.dp), label: String? = "Volume") {
    var percent by remember(input) { mutableStateOf(if (input) PartyVoiceSettings.inputVolume() else PartyVoiceSettings.outputVolume()) }
    VolumeRow(percent, modifier, label, PartyVoiceSettings::saveVolumes) { value ->
        percent = value
        if (input) PartyVoiceSettings.setInputVolume(value) else PartyVoiceSettings.setOutputVolume(value)
    }
}

@Composable
internal fun InvitePicker(party: Party?) {
    val friends by FriendsRepository.friends.collectAsState()
    val more by FriendsRepository.friendsPaging.hasMore.collectAsState()
    val loading by FriendsRepository.friendsPaging.loading.collectAsState()
    val outgoing by PartyRepository.outgoingInvites.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val members = party?.members.orEmpty()
    val candidates = friends
        .filter { friend -> members.none { PartyRepository.samePlayer(it.player, friend.player) } }
        .sortedWith(compareByDescending<Friend> { it.online }.thenBy { (names[it.player] ?: it.player).lowercase() })
    Column(Modifier.width(PickerWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Invite to party", modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text("${members.size.coerceAtLeast(1)} of ${PartyRepository.MAX_MEMBERS}", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        if (candidates.isEmpty()) {
            Text("No friends to invite yet", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        val paging = ListPaging(more, loading, friends.size, true, FriendsRepository.friendsPaging::loadMore)
        ScrollFade(Modifier, 6.dp, maxHeight = 280.dp, ink = OneConfigTheme.current.pageBackground, paging = paging) {
            candidates.forEach { friend ->
                val invite = outgoing.firstOrNull { PartyRepository.samePlayer(it.recipient, friend.player) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlayerHead(seedOf(friend.player), 28.dp, Modifier, shape = headShape(28.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(names[friend.player] ?: PlayerNamesRepository.nameOr(friend.player), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        Text(if (friend.online) "Online" else "Offline", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    }
                    if (invite != null) {
                        TextButton(text = "Cancel invite", onClick = { PartyRepository.cancel(invite) }, modifier = Modifier, size = ControlSize.Large)
                    } else {
                        TextButton(text = "Invite", onClick = { PartyRepository.invite(friend.player) }, modifier = Modifier, style = ControlStyle.Primary, size = ControlSize.Large)
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoiceModeChips(party: Party) {
    val leader = PartyRepository.isLeader(party)
    val tabs = listOf(TabItem(PartyVoiceMode.Party, "Everyone"), TabItem(PartyVoiceMode.Proximity, "Proximity"))
    val selected = if (party.settings.voiceMode == PartyVoiceMode.Proximity) PartyVoiceMode.Proximity else PartyVoiceMode.Party
    TabRow(tabs, selected, { if (leader) PartyRepository.setVoiceMode(it) }, style = TabStyle.Chips, size = ChipDefaults.size, fill = false)
}

@Composable
private fun PartyMenuHeading(text: String) {
    Text(text, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
}

@Composable
internal fun PartyMenu(party: Party, onVoice: (() -> Unit)? = null, onClose: () -> Unit) {
    var devices by remember { mutableStateOf<Boolean?>(null) }
    val leader = PartyRepository.isLeader(party)
    Column(Modifier.width(MenuWidth).popup().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val picking = devices
        if (picking != null) {
            DevicePicker(input = picking) { devices = null }
            return@Column
        }
        PartyMenuHeading("Voice")
        VoiceModeChips(party)
        if (leader) {
            PartyMenuHeading("Who can invite")
            val tabs = listOf(TabItem(PartyInvitePolicy.Leader, "Only me"), TabItem(PartyInvitePolicy.Members, "Everyone"))
            val policy = if (party.settings.invitePolicy == PartyInvitePolicy.Members) PartyInvitePolicy.Members else PartyInvitePolicy.Leader
            TabRow(tabs, policy, { PartyRepository.setInvitePolicy(it) }, style = TabStyle.Chips, size = ChipDefaults.size, fill = false)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (onVoice != null) {
                MenuItem(
                    "Voice and devices",
                    {
                        onClose()
                        onVoice()
                    },
                    icon = PartyIcons.SETTINGS,
                    size = MenuItemSize.Regular,
                )
            } else {
                MenuItem("Microphone: ${PartyAudio.deviceLabel(PartyVoiceSettings.inputDevice()).ifBlank { "Default" }}", { devices = true }, icon = PartyIcons.MIC, size = MenuItemSize.Regular)
                DeviceVolume(input = true)
                MenuItem("Speaker: ${PartyAudio.deviceLabel(PartyVoiceSettings.outputDevice()).ifBlank { "Default" }}", { devices = false }, icon = PartyIcons.HEADPHONES, size = MenuItemSize.Regular)
                DeviceVolume(input = false)
            }
            MenuItem(
                "Leave party",
                {
                    onClose()
                    PartyRepository.leave()
                },
                icon = SOCIAL_ASSETS + "x-close.svg",
                size = MenuItemSize.Regular,
            )
            if (leader) {
                MenuItem(
                    "Disband party",
                    {
                        onClose()
                        Confirm.ask("Disband party?", "Everyone will be removed from the party.", "Disband", { PartyRepository.disband() }, icon = LobbyIcons.TRASH)
                    },
                    icon = SOCIAL_ASSETS + "trash-01.svg",
                    size = MenuItemSize.Regular,
                    danger = true,
                    dangerColor = KitDangerColor,
                )
            }
        }
    }
}

@Composable
private fun DevicePicker(input: Boolean, onBack: () -> Unit) {
    PartyMenuHeading(if (input) "Microphone" else "Speaker")
    DeviceOptions(input, onBack)
    TextButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth(), size = ControlSize.Large)
}

@Composable
internal fun DeviceOptions(input: Boolean, onPicked: () -> Unit) {
    val options = remember(input) { if (input) PartyAudio.inputDevices() else PartyAudio.outputDevices() }
    val current = if (input) PartyVoiceSettings.inputDevice() else PartyVoiceSettings.outputDevice()
    ScrollFade(Modifier, 2.dp, maxHeight = 260.dp, ink = OneConfigTheme.current.pageBackground) {
        (listOf("") + options).forEach { option ->
            MenuItem(
                option.ifBlank { "Default" }.let(PartyAudio::deviceLabel),
                {
                    if (input) PartyVoiceSettings.setInputDevice(option) else PartyVoiceSettings.setOutputDevice(option)
                    onPicked()
                },
                size = MenuItemSize.Compact,
                selected = option == current,
            )
        }
    }
}

@Composable
internal fun PartyDialogs() {
    val voice by PartyVoice.ui.collectAsState()
    if (voice.noticeOpen) VoiceNoticeDialog()
    ConfirmLeaveDialog()
    LeaveWorldDialog()
    PartySceneConsent()
}

internal object PartyCardText {
    const val DISMISS = "Dismiss"
    const val JOIN = "Join"
    const val JOIN_PARTY = "Join party"
    const val DECLINE = "Decline"
    const val CHECKING = "Checking..."
    const val ALLOW = "Allow"
    const val DENY = "Deny"
    const val WANTS_TO_JOIN = "Wants to join your world"

    fun needs(version: String?): String = version?.let { "Needs Minecraft $it" } ?: "Needs another Minecraft version"

    fun world(name: String): String = "$name's world"

    fun partySize(size: Int): String = "$size of ${PartyRepository.MAX_MEMBERS}"
}

@Composable
internal fun PartyCard(
    player: String,
    heading: String,
    caption: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    acceptLabel: String,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
    captionFirst: Boolean = false,
    acceptEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    warning: String? = null,
    shortcut: Boolean = false,
) {
    val frame = modifier
        .controllerAction(ControllerButton.Y, acceptLabel, enabled = shortcut && acceptEnabled, requireFocus = false, onPress = onAccept)
        .width(PartyCardWidth)
        .popup()
        .padding(12.dp)
    Column(frame, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlayerHead(seedOf(player), 34.dp, Modifier, shape = headShape(34.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (captionFirst) Text(caption, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(heading, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                if (!captionFirst) Text(caption, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        warning?.let { Text(it, color = SocialDangerText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = dismissLabel, onClick = onDismiss, modifier = Modifier.weight(1f), style = ControlStyle.Secondary, enabled = dismissEnabled, size = ControlSize.Large)
            TextButton(
                text = acceptLabel,
                icon = SOCIAL_ASSETS + "log-in-04.svg",
                onClick = onAccept,
                modifier = Modifier.weight(1f),
                style = ControlStyle.Primary,
                enabled = acceptEnabled,
                size = ControlSize.Large,
            )
        }
    }
}

@Composable
internal fun PartyInviteCard(invite: PartyInvite, modifier: Modifier = Modifier, shortcut: Boolean = false) {
    val names by PlayerNamesRepository.names.collectAsState()
    PartyCard(
        invite.sender,
        names[invite.sender] ?: PlayerNamesRepository.nameOr(invite.sender),
        "Party invite · ${PartyCardText.partySize(invite.partySize)}",
        PartyCardText.DECLINE,
        { PartyRepository.decline(invite) },
        PartyCardText.JOIN_PARTY,
        { PartyRepository.accept(invite) },
        modifier,
        shortcut = shortcut,
    )
}

@Composable
private fun ConfirmLeaveDialog() {
    val pending by PartyRepository.confirmLeave.collectAsState()
    val invite = pending ?: return
    val names by PlayerNamesRepository.names.collectAsState()
    val name = names[invite.sender] ?: PlayerNamesRepository.nameOr(invite.sender)
    ConfirmDialog(
        title = "You're already in a party",
        text = "Leave it and join $name's party?",
        confirmLabel = "Leave and join",
        onConfirm = { PartyRepository.confirmAccept(true) },
        onDismiss = { PartyRepository.confirmAccept(false) },
    )
}

private fun leaveWorldText(pending: PartyJoin.Pending): String = when (pending.here) {
    PartyJoin.Here.SINGLEPLAYER -> "Your world saves first, then you join ${pending.target}."
    PartyJoin.Here.HOSTING -> "Your world saves and closes for everyone in it, then you join ${pending.target}."
    PartyJoin.Here.SERVER -> pending.address?.let { "You leave $it and join ${pending.target}." } ?: "You leave this world and join ${pending.target}."
}

@Composable
private fun LeaveWorldDialog() {
    val pending = PartyJoin.pending ?: return
    val problem = pending.problem
    ModalDialog(
        PartyJoin::cancel,
        title = { DialogTitle(if (problem != null) "Can't join" else "Leave this world?") },
        buttons = {
            TextButton(text = if (problem != null) "Close" else "Cancel", onClick = PartyJoin::cancel)
            if (problem == null) {
                TextButton(
                    text = if (pending.ready) "Leave and join" else "Checking...",
                    onClick = PartyJoin::confirm,
                    style = ControlStyle.Primary,
                    enabled = pending.ready,
                )
            }
        },
    ) {
        Text(problem ?: leaveWorldText(pending), color = if (problem != null) SocialDangerText else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body)
    }
}

@Composable
private fun VoiceNoticeDialog() {
    ConfirmDialog(
        title = "Party voice",
        text = "Voice goes through Epic Online Services. On Linux, and when that isn't available, it goes peer-to-peer through Epic relays. " +
            "Your microphone only turns on when you join voice.",
        confirmLabel = "Join voice",
        onConfirm = PartyVoice::acceptNotice,
        onDismiss = PartyVoice::dismissNotice,
        icon = PartyIcons.MIC,
    )
}

@Composable
internal fun PartyInvitePrompt(friendId: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    val party by PartyRepository.party.collectAsState()
    val outgoing by PartyRepository.outgoingInvites.collectAsState()
    val item = partyInviteItem(friendId, party, outgoing)
    if (item == null) {
        Spacer(Modifier)
        return
    }
    OptionalIconTextButton(
        item.label,
        item.icon.takeUnless { filled },
        item.onClick,
        modifier,
        if (filled && item.enabled) ControlStyle.Primary else ControlStyle.Secondary,
        item.enabled,
    )
}

@Composable
private fun PartySceneConsent() {
    val entry = PartyScene.consentPrompt ?: return
    ConfirmDialog(
        title = "Share this scene?",
        text = "Show your party this spot from ${entry.label}? They'll see the blocks around it.",
        confirmLabel = "Share",
        onConfirm = { PartyScene.answerConsent(true) },
        onDismiss = { PartyScene.answerConsent(false) },
        dismissLabel = "Use a built-in scene",
    )
}

@Composable
private fun OptionalIconTextButton(text: String, icon: String?, onClick: () -> Unit, modifier: Modifier, style: ControlStyle, enabled: Boolean) {
    if (icon != null) {
        TextButton(text = text, icon = icon, onClick = onClick, modifier = modifier, style = style, enabled = enabled, size = ControlSize.Large)
    } else {
        TextButton(text = text, onClick = onClick, modifier = modifier, style = style, enabled = enabled, size = ControlSize.Large)
    }
}
