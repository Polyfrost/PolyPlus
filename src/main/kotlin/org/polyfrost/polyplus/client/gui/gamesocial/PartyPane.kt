@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialGroupAvatar
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LeaveVoiceButton
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyButtons
import org.polyfrost.polyplus.client.gui.lobbyshell.PartySeats
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayTogetherBanner
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbysocial.ConversationBody
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyIcons
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.VoiceModeChips
import org.polyfrost.polyplus.client.network.http.responses.LocationKind
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.party.PartyPresence
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PlayTogether
import org.polyfrost.polyplus.client.social.GroupsRepository

private const val SEAT_COLUMNS = 4
private const val NARROW_SEAT_COLUMNS = 2

private val NarrowPane = 560.dp
private val Edge = 24.dp

@Composable
internal fun PartyPane(
    window: SocialWindowState,
    host: SocialHost,
    state: SocialPageState,
    snapshot: SocialSnapshot,
    party: Party,
    reconnecting: Boolean,
    modifier: Modifier,
) {
    val thread = snapshot.threads.firstOrNull { it.party && it.id == party.chatGroupId }
    LaunchedEffect(thread?.id) { thread?.let(state.source::open) }
    BoxWithConstraints(modifier) {
        val narrow = maxWidth < NarrowPane
        Column(Modifier.fillMaxSize()) {
            RoomHeader(window, party, narrow)
            Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
            PlayTogetherBanner(Modifier.padding(start = Edge, end = Edge, top = 16.dp))
            Box(Modifier.padding(start = Edge, end = Edge, top = 16.dp)) {
                PartySeats(host, party, if (narrow) NARROW_SEAT_COLUMNS else SEAT_COLUMNS, inviteSeat = true)
            }
            RoomVoice(window, party)
            Box(Modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
            RoomChat(thread?.id, snapshot, reconnecting)
        }
    }
}

@Composable
private fun ColumnScope.RoomChat(threadId: Int?, snapshot: SocialSnapshot, reconnecting: Boolean) {
    val groups by GroupsRepository.groups.collectAsState()
    val thread = snapshot.threads.firstOrNull { it.id == threadId }
    val group = groups.firstOrNull { it.id == threadId }
    val chat = SocialChatLink.active?.takeIf { it.id == threadId }
    if (thread == null || group == null || chat == null) {
        Spacer(Modifier.weight(1f))
        return
    }
    ConversationBody(chat, thread, group, "Message your party", "Reconnecting".takeIf { reconnecting })
}

@Composable
private fun RoomHeader(window: SocialWindowState, party: Party, narrow: Boolean) {
    val back = LocalSocialLook.current.backButton
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Edge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (back) BackButton(window::closeParty)
        SocialGroupAvatar(party.members.map { it.player }, 34.dp, ringColor = Color.Transparent, grid = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PanelTitle("Party")
            Label("${party.members.size} of ${PartyRepository.MAX_MEMBERS} · ${party.members.count { it.online }} online", 12.sp, color = MutedText)
        }
        BringPartyButton(party)
        if (!narrow) SocialButton("Leave", onClick = { PartyRepository.leave() })
        PartyButtons(party, window::showVoice)
        if (!back) SocialIconButton(LobbyIcons.CLOSE, tooltip = "Close party", onClick = window::closeParty)
    }
}

@Composable
private fun BringPartyButton(party: Party) {
    val here = PartyPresence.local.location
    if (!PartyRepository.isLeader(party) || here.kind != LocationKind.Server) return
    val away = PartyRepository.others(party).any { it.online && it.state?.location?.key != here.key }
    val play = PlayTogether.active
    val running = play != null && play.stage != PlayTogether.Stage.DONE
    if (!away && !running) return
    SocialButton(if (running) "Bringing party" else "Bring party here", filled = true, enabled = !running, onClick = PlayTogether::bringHere)
}

@Composable
private fun RoomVoice(window: SocialWindowState, party: Party) {
    val voice by PartyVoice.ui.collectAsState()
    Row(
        Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label("Voice", 14.sp, weight = FontWeight.Medium)
        VoiceModeChips(party)
        Spacer(Modifier.weight(1f))
        if (!voice.inVoice || window.singlePane) VoiceButtons(party, leave = false)
        SocialIconButton(PartyIcons.SETTINGS, tooltip = "Voice and devices", onClick = window::showVoice)
        if (voice.inVoice && window.singlePane) LeaveVoiceButton()
    }
}
