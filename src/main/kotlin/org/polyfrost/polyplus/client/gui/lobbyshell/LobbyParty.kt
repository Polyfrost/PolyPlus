@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialSuccessText
import org.polyfrost.polyplus.client.gui.PresenceDot
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialControlBackground
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbysocial.InvitePicker
import org.polyfrost.polyplus.client.gui.lobbysocial.MemberPopoverContent
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
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.party.HostJoinRequests
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.gui.lobbysocial.memberMenuItems
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private val EntryHead = 30.dp
private val OverflowWidth = 32.dp
private val TagInviteSize = 22.dp
private val SeatHeight = 60.dp
private val PartyPopupGap = 14.dp
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
        if (shown.size < cells.size) Text("+${cells.size - shown.size}", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
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
    val hover = rememberHoverState()
    AnchoredPopupButton(
        "Invite to party",
        { _, toggle, triggerModifier ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(triggerModifier)
                    .height(SeatHeight)
                    .rowFrame(false, hover.hovered, SocialFieldShape)
                    .border(1.dp, SocialBorderColor, SocialFieldShape)
                    .hoverable(hover.interactionSource)
                    .oneConfigClickable("Invite to party", toggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                ThemedIcon(PartyIcons.INVITE, OneConfigTheme.current.mutedText, 16.dp)
                Text("Invite", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        },
        modifier,
        placement = Placement.AboveStart,
        gap = PartyPopupGap,
    ) { InvitePicker(party) }
}

@Composable
private fun PartyEntry(host: SocialHost, party: Party, cell: PartyCell, tier: RosterTier, modifier: Modifier = Modifier.widthIn(max = tier.width)) {
    val compact = tier == RosterTier.Compact
    val hover = rememberHoverState()
    val label = if (cell.leader) "${cell.name}, leader, ${cell.detail}" else "${cell.name}, ${cell.detail}"
    AnchoredPopupButton(
        label,
        { _, toggle, triggerModifier ->
            Row(
                modifier
                    .then(triggerModifier)
                    .height(IntrinsicSize.Max)
                    .rowFrame(false, hover.hovered, OneConfigTheme.shape(8.dp))
                    .contextMenu { memberMenuItems(host, party, cell) }
                    .hoverable(hover.interactionSource)
                    .then(socialHover(cell.name))
                    .oneConfigClickable(label, toggle, shape = OneConfigTheme.shape(8.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) {
                EntryHead(cell, Modifier.centerVerticallyExact())
                if (tier != RosterTier.Heads) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(cell.name, color = if (cell.speaking) SocialSuccessText else SocialTextPrimary, style = OneConfigTheme.current.typography.body.copy(fontSize = if (compact) 12.sp else 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        if (!compact) Text(if (cell.leader) "Leader · ${cell.detail}" else cell.detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    }
                }
            }
        },
        placement = Placement.AboveStart,
        gap = PartyPopupGap,
    ) { close -> MemberPopoverContent(host, party, cell, close) }
}

@Composable
private fun EntryHead(cell: PartyCell, modifier: Modifier) {
    Box(modifier) {
        PlayerHead(playerId = seedOf(cell.id), size = EntryHead, modifier = Modifier.then(if (cell.speaking) Modifier.border(1.dp, SocialSuccessColor, headShape(EntryHead)) else Modifier), shape = headShape(EntryHead))
        if (cell.muted) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(16.dp)
                    .clip(OneConfigTheme.shape(5.dp))
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
        if (party?.settings?.voiceMode == PartyVoiceMode.Proximity) Text("Proximity", Modifier.padding(end = 4.dp), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        VoiceButtons(party)
        PartyButtons(party, onVoice)
    }
}

@Composable
internal fun LeaveVoiceButton() {
    NavIconButton(LEAVE_VOICE, "Leave voice", PartyVoice::leave)
}

@Composable
internal fun VoiceButtons(party: Party?, leave: Boolean = true) {
    val voice by PartyVoice.ui.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (party != null && voice.inVoice) {
            if (voice.micBlocked || voice.micSilent) MicBlockedButton(voice.blockedNotice.orEmpty(), voice.micBlocked)
            NavIconButton(
                icon = if (voice.muted) PartyIcons.MIC_OFF else PartyIcons.MIC,
                contentDescription = if (voice.muted) "Unmute" else "Mute",
                onClick = PartyVoice::toggleMute,
                tint = when {
                    voice.muted -> SocialDangerColor
                    voice.talking -> SocialSuccessColor
                    else -> Color.Unspecified
                },
            )
            NavIconButton(
                icon = if (voice.deafened) PartyIcons.HEADPHONES_OFF else PartyIcons.HEADPHONES,
                contentDescription = if (voice.deafened) "Undeafen" else "Deafen",
                onClick = PartyVoice::toggleDeafen,
                tint = if (voice.deafened) SocialDangerColor else Color.Unspecified,
            )
            if (leave) LeaveVoiceButton()
        } else if (party != null && PartyVoiceSettings.voiceAvailable()) {
            TextButton(text = "Join voice", icon = PartyIcons.MIC, onClick = PartyVoice::join, size = ControlSize.Large, style = ControlStyle.Secondary)
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
    val warning = if (blocked) "Can't talk" else "No sound yet"
    PartyPopupButton(WARNING, warning, SocialDangerColor) { close ->
        Column(Modifier.width(PartyPopoverWidth).popup().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (blocked) "Your microphone is blocked" else "We can't hear your microphone", style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(notice, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small)
            TextButton(text = "Try again", modifier = Modifier.fillMaxWidth(), size = ControlSize.Large, onClick = {
                close()
                PartyVoice.retryMic()
            })
        }
    }
}

@Composable
internal fun InviteButton(party: Party?) {
    if (!PartyRepository.canInvite(party)) return
    PartyPopupButton(PartyIcons.INVITE, "Invite to party") { InvitePicker(party) }
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
            Text("Invited", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    } else {
        NavIconButton(PartyIcons.INVITE, "Invite to party", { PartyRepository.invite(player) }, frameSize = TagInviteSize)
    }
}

@Composable
private fun PartyMenuButton(party: Party, onVoice: (() -> Unit)?) {
    PartyPopupButton(PartyIcons.MORE, "Party") { close -> PartyMenu(party, onVoice, close) }
}

@Composable
private fun PartyPopupButton(icon: String, contentDescription: String, tint: Color = Color.Unspecified, content: @Composable (close: () -> Unit) -> Unit) {
    AnchoredPopupButton(
        contentDescription,
        { open, toggle, triggerModifier ->
            NavIconButton(icon, contentDescription, toggle, triggerModifier, tint = tint, active = open, tooltip = if (open) null else contentDescription)
        },
        placement = Placement.AboveEnd,
        gap = PartyPopupGap,
        content = content,
    )
}

private enum class FirstCard { Request, Hosted, Rejoin, LeaderMoved, Invite }

@Composable
internal fun PartyInviteCards(host: SocialHost) {
    val invites by PartyRepository.incomingInvites.collectAsState()
    val hosted = leaderHostedWorld()?.takeUnless { it.dismissed }
    val rejoin = rejoinableWorld()?.takeUnless { hosted?.sessionId == it.sessionId }
    val requests = HostJoinRequests.pending
    if (invites.isEmpty() && LeaderFollow.card == null && rejoin == null && hosted == null && requests.isEmpty()) return
    val shownRequests = requests.takeLast(MAX_INVITE_CARDS)
    val shownInvites = invites.takeLast(MAX_INVITE_CARDS)
    val first = when {
        shownRequests.isNotEmpty() -> FirstCard.Request
        hosted != null -> FirstCard.Hosted
        rejoin != null -> FirstCard.Rejoin
        LeaderFollow.card != null -> FirstCard.LeaderMoved
        else -> FirstCard.Invite
    }
    Popup(popupPositionProvider = rememberAbove(alignEnd = true), properties = PopupProperties(focusable = false)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shownRequests.forEachIndexed { index, request ->
                key(request.id) { HostJoinRequestCard(request, shortcut = first == FirstCard.Request && index == 0) }
            }
            hosted?.let { LeaderHostingCard(it, shortcut = first == FirstCard.Hosted) }
            rejoin?.let { RejoinWorldCard(it, shortcut = first == FirstCard.Rejoin) }
            LeaderMovedCard(host, shortcut = first == FirstCard.LeaderMoved)
            shownInvites.forEachIndexed { index, invite ->
                key(invite.id) { PartyInviteCard(invite, shortcut = first == FirstCard.Invite && index == 0) }
            }
        }
    }
}
