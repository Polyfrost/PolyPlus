@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.PresenceDot
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialControlBackground
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialSuccessText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.lobbysocial.AbovePopup
import org.polyfrost.polyplus.client.gui.lobbysocial.InvitePicker
import org.polyfrost.polyplus.client.gui.lobbysocial.MemberPopover
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyCell
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyIcons
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyInviteCard
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyMenu
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyPopoverWidth
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.gui.lobbysocial.partyCells
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberAbove
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.gui.lobbysocial.socialHover
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.party.HostJoinRequests
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.gui.lobbysocial.memberMenuItems

private val EntryHead = 30.dp
private val OverflowWidth = 32.dp
private val TagInviteSize = 22.dp
private val SeatHeight = 60.dp
private const val MAX_INVITE_CARDS = 3

private const val LEAVE_VOICE = SOCIAL_ASSETS + "log-out-04.svg"
private const val WARNING = "alert-circle"

private enum class RosterTier(val width: Dp, val gap: Dp) {
    Full(124.dp, 8.dp),
    Compact(76.dp, 6.dp),
    Heads(EntryHead, 6.dp),
    ;

    fun span(count: Int): Dp = width * count + gap * (count - 1).coerceAtLeast(0)
}

@Composable
internal fun PartyRoster(host: SocialHost, party: Party, width: Dp) {
    val cells = partyCells(party)
    val tier = RosterTier.entries.firstOrNull { it.span(cells.size) <= width } ?: RosterTier.Heads
    val slot = tier.width + tier.gap
    val shown = if (tier.span(cells.size) <= width) cells else cells.take(((width - OverflowWidth + tier.gap) / slot).toInt().coerceAtLeast(1))
    Row(Modifier.centerVerticallyExact(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(tier.gap)) {
        shown.forEach { cell -> key(cell.id) { PartyEntry(host, party, cell, tier) } }
        if (shown.size < cells.size) Label("+${cells.size - shown.size}", 12.sp, color = MutedText, weight = FontWeight.Medium)
    }
}

@Composable
internal fun PartySeats(host: SocialHost, party: Party, columns: Int, inviteSeat: Boolean = false) {
    val cells = partyCells(party)
    val free = inviteSeat && PartyRepository.canInvite(party) && cells.size % columns != 0
    val seats: List<PartyCell?> = if (free) cells + null else cells
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        seats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cell ->
                    if (cell == null) {
                        InviteSeat(party, Modifier.weight(1f))
                    } else {
                        key(cell.id) {
                            Box(Modifier.weight(1f).clip(SocialFieldShape).background(SocialControlBackground).padding(10.dp)) {
                                PartyEntry(host, party, cell, RosterTier.Full, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InviteSeat(party: Party, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val (interaction, hovered) = rememberSocialHover()
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(SeatHeight)
                .rowFrame(false, hovered, SocialFieldShape)
                .border(1.dp, SocialBorderColor, SocialFieldShape)
                .hoverable(interaction)
                .clickableWithSound("Invite to party") { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            ThemedIcon(PartyIcons.INVITE, MutedText, 16.dp)
            Label("Invite", 14.sp, color = MutedText)
        }
        if (open) {
            EscapeCloses { open = false }
            AbovePopup(onDismiss = { open = false }) { InvitePicker(party) }
        }
    }
}

@Composable
private fun PartyEntry(host: SocialHost, party: Party, cell: PartyCell, tier: RosterTier, modifier: Modifier = Modifier.widthIn(max = tier.width)) {
    val compact = tier == RosterTier.Compact
    var open by remember { mutableStateOf(false) }
    val (interaction, hovered) = rememberSocialHover()
    Box {
        Row(
            modifier
                .height(IntrinsicSize.Max)
                .rowFrame(false, hovered, ppShape(8.dp))
                .contextMenu { memberMenuItems(host, party, cell) }
                .hoverable(interaction)
                .then(socialHover(cell.name))
                .clickableWithSound(if (cell.leader) "${cell.name}, leader, ${cell.detail}" else "${cell.name}, ${cell.detail}", shape = ppShape(8.dp)) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        ) {
            EntryHead(cell, Modifier.centerVerticallyExact())
            if (tier != RosterTier.Heads) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(cell.name, if (compact) 12.sp else 14.sp, color = if (cell.speaking) SocialSuccessText else SocialTextPrimary, weight = FontWeight.Medium)
                    if (!compact) Label(if (cell.leader) "Leader · ${cell.detail}" else cell.detail, 11.sp, color = MutedText)
                }
            }
        }
        if (open) {
            EscapeCloses { open = false }
            MemberPopover(host, party, cell) { open = false }
        }
    }
}

@Composable
private fun EntryHead(cell: PartyCell, modifier: Modifier) {
    Box(modifier) {
        PlayerHead(seedOf(cell.id), EntryHead, Modifier.then(if (cell.speaking) Modifier.border(1.dp, SocialSuccessColor, headShape(EntryHead)) else Modifier), headShape(EntryHead))
        if (cell.muted) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(16.dp)
                    .clip(ppShape(5.dp))
                    .background(OneConfigTheme.current.popupBackground),
                contentAlignment = Alignment.Center,
            ) {
                LobbyIcon("microphone-off-01", SocialDangerText, 11.dp)
            }
        } else {
            PresenceDot(
                cell.self || cell.member?.online == true,
                10.dp,
                Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp),
                ring = OneConfigTheme.current.componentBackground,
            )
        }
    }
}

@Composable
internal fun PartyControls(host: SocialHost, party: Party?) {
    Box(Modifier.centerVerticallyExact()) { PartyActions(party) }
    PartyInviteCards(host)
}

@Composable
internal fun PartyActions(party: Party?, onVoice: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (party?.settings?.voiceMode == PartyVoiceMode.Proximity) Label("Proximity", 11.sp, Modifier.padding(end = 4.dp), color = MutedText)
        VoiceButtons(party)
        PartyButtons(party, onVoice)
    }
}

@Composable
internal fun LeaveVoiceButton() {
    IconButton(themedIcon(LEAVE_VOICE), "Leave voice", Modifier, PartyVoice::leave)
}

@Composable
internal fun VoiceButtons(party: Party?, leave: Boolean = true) {
    val voice by PartyVoice.ui.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (party != null && voice.inVoice) {
            if (voice.micBlocked || voice.micSilent) MicBlockedButton(voice.blockedNotice.orEmpty(), voice.micBlocked)
            IconButton(
                themedIcon(if (voice.muted) PartyIcons.MIC_OFF else PartyIcons.MIC),
                if (voice.muted) "Unmute" else "Mute",
                null,
                Modifier,
                null,
                when {
                    voice.muted -> SocialDangerColor
                    voice.talking -> SocialSuccessColor
                    else -> SocialTextPrimary
                },
                TooltipPlacement.BelowStart,
                PartyVoice::toggleMute,
            )
            IconButton(
                themedIcon(if (voice.deafened) PartyIcons.HEADPHONES_OFF else PartyIcons.HEADPHONES),
                if (voice.deafened) "Undeafen" else "Deafen",
                null,
                Modifier,
                null,
                if (voice.deafened) SocialDangerColor else SocialTextPrimary,
                TooltipPlacement.BelowStart,
                PartyVoice::toggleDeafen,
            )
            if (leave) LeaveVoiceButton()
        } else if (party != null && PartyVoiceSettings.voiceAvailable()) {
            TextButton("Join voice", themedIcon(PartyIcons.MIC), Modifier.height(38.dp), ButtonStyle.Secondary, true, PartyVoice::join)
        }
    }
}

@Composable
internal fun PartyButtons(party: Party?, onVoice: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InviteButton(party)
        if (party != null) PartyMenuButton(party, onVoice)
    }
}

@Composable
private fun MicBlockedButton(notice: String, blocked: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(themedIcon(WARNING), if (open) null else if (blocked) "Can't talk" else "No sound yet", null, Modifier, null, SocialDangerColor, TooltipPlacement.BelowStart) { open = !open }
        if (open) {
            EscapeCloses { open = false }
            AbovePopup(alignEnd = true, onDismiss = { open = false }) {
                Column(Modifier.width(PartyPopoverWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label(if (blocked) "Your microphone is blocked" else "We can't hear your microphone", 14.sp, weight = FontWeight.Medium)
                    SocialText(notice, 12.sp, color = MutedText)
                    TextButton("Try again", Modifier.fillMaxWidth().height(38.dp)) {
                        open = false
                        PartyVoice.retryMic()
                    }
                }
            }
        }
    }
}

@Composable
internal fun InviteButton(party: Party?) {
    var open by remember { mutableStateOf(false) }
    if (!PartyRepository.canInvite(party)) return
    Box {
        IconButton(themedIcon(PartyIcons.INVITE), if (open) null else "Invite to party", Modifier) { open = !open }
        if (open) {
            EscapeCloses { open = false }
            AbovePopup(alignEnd = true, onDismiss = { open = false }) { InvitePicker(party) }
        }
    }
}

@Composable
internal fun SceneTagInvite(player: String) {
    val party by PartyRepository.party.collectAsState()
    val outgoing by PartyRepository.outgoingInvites.collectAsState()
    val current = party
    if (current != null && (current.members.any { PartyRepository.samePlayer(it.player, player) } || !PartyRepository.canInvite(current))) return
    if (outgoing.any { PartyRepository.samePlayer(it.recipient, player) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ThemedIcon("check-circle", SocialSuccessColor, 14.dp)
            Label("Invited", 12.sp, color = MutedText)
        }
    } else {
        IconButton(themedIcon(PartyIcons.INVITE), "Invite to party", Modifier.size(TagInviteSize)) { PartyRepository.invite(player) }
    }
}

@Composable
private fun PartyMenuButton(party: Party, onVoice: (() -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(themedIcon(PartyIcons.MORE), if (open) null else "Party", Modifier) { open = !open }
        if (open) {
            EscapeCloses { open = false }
            AbovePopup(alignEnd = true, onDismiss = { open = false }) { PartyMenu(party, onVoice) { open = false } }
        }
    }
}

@Composable
internal fun PartyInviteCards(host: SocialHost) {
    val invites by PartyRepository.incomingInvites.collectAsState()
    val hosted = leaderHostedWorld()?.takeUnless { it.dismissed }
    val rejoin = rejoinableWorld()?.takeUnless { hosted?.sessionId == it.sessionId }
    val requests = HostJoinRequests.pending
    if (invites.isEmpty() && LeaderFollow.card == null && rejoin == null && hosted == null && requests.isEmpty()) return
    Popup(popupPositionProvider = rememberAbove(alignEnd = true), properties = PopupProperties(focusable = false)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            requests.takeLast(MAX_INVITE_CARDS).forEach { request -> key(request.id) { HostJoinRequestCard(request) } }
            hosted?.let { LeaderHostingCard(it) }
            rejoin?.let { RejoinWorldCard(it) }
            LeaderMovedCard(host)
            invites.takeLast(MAX_INVITE_CARDS).forEach { invite -> key(invite.id) { PartyInviteCard(invite) } }
        }
    }
}
