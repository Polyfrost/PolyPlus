@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbysocial

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.fadingEdges
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.OnboardingSlider
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialMenuItem
import org.polyfrost.polyplus.client.gui.SocialModalScrim
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.Head
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.MoreRow
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.swallowClicks
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

internal val PartyPopoverWidth = 260.dp
private val MenuWidth = 280.dp
private val PickerWidth = 300.dp
private val InviteCardWidth = 300.dp
private val DialogWidth = 380.dp
private const val POPOVER_GAP = 14
private const val PERCENT = 100f
private const val SLIDER_MAX_PERCENT = 200f
private const val SLIDER_STEP_PERCENT = 5f
private const val SLIDER_WIDTH = 130f

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

private class AbovePosition(private val gap: Int, private val alignEnd: Boolean = false) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (alignEnd) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val clampedX = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val above = anchorBounds.top - popupContentSize.height - gap
        val below = (anchorBounds.bottom + gap).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(clampedX, if (above >= 0) above else below)
    }
}

@Composable
internal fun rememberAbove(alignEnd: Boolean = false): PopupPositionProvider {
    val density = LocalDensity.current
    return remember(density, alignEnd) { AbovePosition(with(density) { POPOVER_GAP.dp.roundToPx() }, alignEnd) }
}

@Composable
internal fun AbovePopup(alignEnd: Boolean = false, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(popupPositionProvider = rememberAbove(alignEnd), onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        content()
    }
}

private fun joinable(cell: PartyCell): String? {
    if (cell.self) return null
    val location = cell.state?.location ?: return null
    if (location.kind != LocationKind.Server) return null
    val display = location.display ?: return null
    if (location.key != null && location.key == PartyPresence.local.location.key) return null
    return display
}

@Composable
internal fun MemberPopover(host: SocialHost, party: Party, cell: PartyCell, onClose: () -> Unit) {
    val leader = PartyRepository.isLeader(party)
    AbovePopup(onDismiss = onClose) {
        Column(Modifier.width(PartyPopoverWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Head(seedOf(cell.id), 38.dp, realSkin = true, speaking = cell.speaking)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(if (cell.leader) "${cell.name} · Leader" else cell.name, 14.sp, weight = FontWeight.Medium)
                    Label(locationLabel(cell.state, cell.self || cell.member?.online == true), 12.sp, color = MutedText)
                    voiceLabel(cell.member, cell.state)?.let { Label(it, 12.sp, color = MutedText) }
                }
            }
            joinable(cell)?.let { address ->
                SocialButton("Join ${cell.name}", SOCIAL_ASSETS + "log-in-04.svg", Modifier.fillMaxWidth(), filled = true) {
                    onClose()
                    host.joinAddress(address)
                }
            }
            if (!cell.self) {
                MemberVolume(cell.id)
                val mutedForMe = cell.id.lowercase() in PartyVoiceSettings.memberMutes
                SocialButton(if (mutedForMe) "Unmute for me" else "Mute for me", if (mutedForMe) PartyIcons.MIC else PartyIcons.MIC_OFF, Modifier.fillMaxWidth()) {
                    PartyVoiceSettings.toggleMute(cell.id)
                }
            }
            if (leader && !cell.self) {
                val hardMuted = cell.member?.voiceHardMuted == true
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SocialMenuItem("Make leader") {
                        onClose()
                        PartyRepository.makeLeader(cell.id)
                    }
                    SocialMenuItem(if (hardMuted) "Let them talk" else "Mute their voice for everyone") {
                        PartyRepository.setHardMuted(cell.id, !hardMuted)
                    }
                    SocialMenuItem("Remove from party", color = SocialDangerText) {
                        onClose()
                        PartyRepository.kick(cell.id)
                    }
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
    sliderWidth: Float = SLIDER_WIDTH,
    onRelease: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        label?.let { Label(it, 12.sp, color = MutedText) }
        Box(Modifier.weight(1f)) {
            OnboardingSlider(percent / SLIDER_MAX_PERCENT, sliderWidth, thumbSize = 15.dp, trackHeight = 5.dp, onRelease = onRelease) { progress ->
                onChange((progress * SLIDER_MAX_PERCENT / SLIDER_STEP_PERCENT).roundToInt() * SLIDER_STEP_PERCENT)
            }
        }
        Label("${percent.roundToInt()}%", 12.sp)
    }
}

@Composable
internal fun DeviceVolume(input: Boolean, modifier: Modifier = Modifier.padding(horizontal = 10.dp), label: String? = "Volume", sliderWidth: Float = SLIDER_WIDTH) {
    var percent by remember(input) { mutableStateOf(if (input) PartyVoiceSettings.inputVolume() else PartyVoiceSettings.outputVolume()) }
    VolumeRow(percent, modifier, label, sliderWidth, PartyVoiceSettings::saveVolumes) { value ->
        percent = value
        if (input) PartyVoiceSettings.setInputVolume(value) else PartyVoiceSettings.setOutputVolume(value)
    }
}

@Composable
internal fun InvitePicker(party: Party?) {
    val friends by FriendsRepository.friends.collectAsState()
    val outgoing by PartyRepository.outgoingInvites.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val members = party?.members.orEmpty()
    val candidates = friends
        .filter { friend -> members.none { PartyRepository.samePlayer(it.player, friend.player) } }
        .sortedWith(compareByDescending<Friend> { it.online }.thenBy { (names[it.player] ?: it.player).lowercase() })
    Column(Modifier.width(PickerWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Invite to party", 15.sp, Modifier.weight(1f), weight = FontWeight.Medium)
            Label("${members.size.coerceAtLeast(1)} of ${PartyRepository.MAX_MEMBERS}", 12.sp, color = MutedText)
        }
        if (candidates.isEmpty()) {
            Label("No friends to invite yet", 12.sp, color = MutedText)
        }
        val scroll = rememberScrollState()
        Column(Modifier.heightIn(max = 280.dp).fadingEdges(scroll, OneConfigTheme.current.pageBackground, 32.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            candidates.forEach { friend ->
                val invite = outgoing.firstOrNull { PartyRepository.samePlayer(it.recipient, friend.player) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Head(seedOf(friend.player), 28.dp, realSkin = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Label(names[friend.player] ?: PlayerNamesRepository.nameOr(friend.player), 14.sp, weight = FontWeight.Medium)
                        Label(if (friend.online) "Online" else "Offline", 11.sp, color = MutedText)
                    }
                    if (invite != null) {
                        SocialButton("Cancel invite", enabled = true) { PartyRepository.cancel(invite) }
                    } else {
                        SocialButton("Invite", filled = true) { PartyRepository.invite(friend.player) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VoiceModeChips(party: Party) {
    val leader = PartyRepository.isLeader(party)
    val mode = party.settings.voiceMode
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("Everyone", selected = mode != PartyVoiceMode.Proximity) { if (leader) PartyRepository.setVoiceMode(PartyVoiceMode.Party) }
        Chip("Proximity", selected = mode == PartyVoiceMode.Proximity) { if (leader) PartyRepository.setVoiceMode(PartyVoiceMode.Proximity) }
    }
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
        Label("Voice", 12.sp, color = MutedText, weight = FontWeight.Medium)
        VoiceModeChips(party)
        if (leader) {
            Label("Who can invite", 12.sp, color = MutedText, weight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val policy = party.settings.invitePolicy
                Chip("Only me", selected = policy != PartyInvitePolicy.Members) { PartyRepository.setInvitePolicy(PartyInvitePolicy.Leader) }
                Chip("Everyone", selected = policy == PartyInvitePolicy.Members) { PartyRepository.setInvitePolicy(PartyInvitePolicy.Members) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (onVoice != null) {
                MoreRow(PartyIcons.SETTINGS, "Voice and devices") {
                    onClose()
                    onVoice()
                }
            } else {
                MoreRow(PartyIcons.MIC, "Microphone: ${PartyAudio.deviceLabel(PartyVoiceSettings.inputDevice()).ifBlank { "Default" }}") { devices = true }
                DeviceVolume(input = true)
                MoreRow(PartyIcons.HEADPHONES, "Speaker: ${PartyAudio.deviceLabel(PartyVoiceSettings.outputDevice()).ifBlank { "Default" }}") { devices = false }
                DeviceVolume(input = false)
            }
            MoreRow(SOCIAL_ASSETS + "x-close.svg", "Leave party") {
                onClose()
                PartyRepository.leave()
            }
            if (leader) {
                SocialMenuItem("Disband party", SOCIAL_ASSETS + "trash-01.svg", color = SocialDangerText, height = 38.dp, horizontalPadding = 10.dp) {
                    onClose()
                    PartyRepository.disband()
                }
            }
        }
    }
}

@Composable
private fun DevicePicker(input: Boolean, onBack: () -> Unit) {
    Label(if (input) "Microphone" else "Speaker", 12.sp, color = MutedText, weight = FontWeight.Medium)
    DeviceOptions(input, onBack)
    SocialButton("Back", modifier = Modifier.fillMaxWidth(), onClick = onBack)
}

@Composable
internal fun DeviceOptions(input: Boolean, onPicked: () -> Unit) {
    val options = remember(input) { if (input) PartyAudio.inputDevices() else PartyAudio.outputDevices() }
    val current = if (input) PartyVoiceSettings.inputDevice() else PartyVoiceSettings.outputDevice()
    val scroll = rememberScrollState()
    Column(Modifier.heightIn(max = 260.dp).fadingEdges(scroll, OneConfigTheme.current.pageBackground, 32.dp).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (listOf("") + options).forEach { option ->
            SocialMenuItem(option.ifBlank { "Default" }.let(PartyAudio::deviceLabel), selected = option == current) {
                if (input) PartyVoiceSettings.setInputDevice(option) else PartyVoiceSettings.setOutputDevice(option)
                onPicked()
            }
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

@Composable
internal fun PartyInviteCard(invite: PartyInvite, modifier: Modifier = Modifier) {
    val names by PlayerNamesRepository.names.collectAsState()
    val name = names[invite.sender] ?: PlayerNamesRepository.nameOr(invite.sender)
    Column(modifier.width(InviteCardWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Head(seedOf(invite.sender), 34.dp, realSkin = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(name, 14.sp, weight = FontWeight.Medium)
                Label("Party invite · ${invite.partySize} of ${PartyRepository.MAX_MEMBERS}", 12.sp, color = MutedText)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Decline", modifier = Modifier.weight(1f)) { PartyRepository.decline(invite) }
            SocialButton("Join party", modifier = Modifier.weight(1f), filled = true) { PartyRepository.accept(invite) }
        }
    }
}

@Composable
private fun ConfirmLeaveDialog() {
    val pending by PartyRepository.confirmLeave.collectAsState()
    val invite = pending ?: return
    val names by PlayerNamesRepository.names.collectAsState()
    val name = names[invite.sender] ?: PlayerNamesRepository.nameOr(invite.sender)
    SocialModalScrim({ PartyRepository.confirmAccept(false) }) {
        EscapeCloses { PartyRepository.confirmAccept(false) }
        Column(Modifier.width(DialogWidth).swallowClicks().popup().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelTitle("You're already in a party")
            SocialText("Leave it and join $name's party?", 14.sp, color = MutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton("Cancel", modifier = Modifier.weight(1f)) { PartyRepository.confirmAccept(false) }
                SocialButton("Leave and join", modifier = Modifier.weight(1f), filled = true) { PartyRepository.confirmAccept(true) }
            }
        }
    }
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
    SocialModalScrim(PartyJoin::cancel) {
        EscapeCloses(PartyJoin::cancel)
        Column(Modifier.width(DialogWidth).swallowClicks().popup().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelTitle(if (problem != null) "Can't join" else "Leave this world?")
            SocialText(problem ?: leaveWorldText(pending), 14.sp, color = if (problem != null) SocialDangerText else MutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton(if (problem != null) "Close" else "Cancel", modifier = Modifier.weight(1f), onClick = PartyJoin::cancel)
                if (problem == null) {
                    SocialButton(if (pending.ready) "Leave and join" else "Checking...", modifier = Modifier.weight(1f), filled = true, enabled = pending.ready, onClick = PartyJoin::confirm)
                }
            }
        }
    }
}

@Composable
private fun VoiceNoticeDialog() {
    SocialModalScrim(PartyVoice::dismissNotice) {
        EscapeCloses(PartyVoice::dismissNotice)
        Column(Modifier.width(DialogWidth).swallowClicks().popup().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelTitle("Party voice")
            SocialText(
                "Voice goes through Epic Online Services. On Linux, and when that isn't available, it goes peer-to-peer through Epic relays. " +
                    "Your microphone only turns on when you join voice.",
                14.sp,
                color = MutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton("Cancel", modifier = Modifier.weight(1f), onClick = PartyVoice::dismissNotice)
                SocialButton("Join voice", PartyIcons.MIC, Modifier.weight(1f), filled = true, onClick = PartyVoice::acceptNotice)
            }
        }
    }
}

@Composable
internal fun PartyInvitePrompt(friendId: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    val party by PartyRepository.party.collectAsState()
    val outgoing by PartyRepository.outgoingInvites.collectAsState()
    val current = party
    when {
        current?.members?.any { PartyRepository.samePlayer(it.player, friendId) } == true ->
            SocialButton("In your party", modifier = modifier, enabled = false)
        outgoing.any { PartyRepository.samePlayer(it.recipient, friendId) } ->
            SocialButton("Invited to party", modifier = modifier, enabled = false)
        PartyRepository.canInvite(current) ->
            SocialButton("Invite to party", PartyIcons.INVITE.takeUnless { filled }, modifier, filled = filled) { PartyRepository.invite(friendId) }
        else -> Spacer(Modifier)
    }
}

@Composable
private fun PartySceneConsent() {
    val entry = PartyScene.consentPrompt ?: return
    SocialModalScrim({ PartyScene.answerConsent(false) }) {
        EscapeCloses { PartyScene.answerConsent(false) }
        Column(Modifier.width(DialogWidth).swallowClicks().popup().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelTitle("Share this scene?")
            SocialText("Show your party this spot from ${entry.label}? They'll see the blocks around it.", 14.sp, color = MutedText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton("Use a built-in scene", modifier = Modifier.weight(1f)) { PartyScene.answerConsent(false) }
                SocialButton("Share", modifier = Modifier.weight(1f), filled = true) { PartyScene.answerConsent(true) }
            }
        }
    }
}
