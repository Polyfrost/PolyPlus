@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LeaveVoiceButton
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyIcons
import org.polyfrost.polyplus.client.gui.lobbysocial.partyCells
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice

@Composable
internal fun VoiceStrip(window: SocialWindowState, modifier: Modifier = Modifier) {
    val voice by PartyVoice.ui.collectAsState()
    val current by PartyRepository.party.collectAsState()
    val party = current
    if (party == null || !voice.inVoice) return
    Column(modifier.fillMaxWidth()) {
        PaneRule(Modifier.padding(horizontal = 8.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("In voice", 14.sp, weight = FontWeight.Medium)
                Label(stripDetail(party), 12.sp, color = MutedText)
            }
            VoiceButtons(party, leave = false)
            IconButton(themedIcon(PartyIcons.SETTINGS), "Voice and devices", Modifier, window::showVoice)
            LeaveVoiceButton()
        }
    }
}

@Composable
private fun stripDetail(party: Party): String {
    val talking = partyCells(party).firstOrNull { it.speaking }?.name
    return when {
        talking != null -> "$talking talking"
        party.settings.voiceMode == PartyVoiceMode.Proximity -> "Proximity"
        else -> "Everyone"
    }
}
