@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.LeaveVoiceButton
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyIcons
import org.polyfrost.polyplus.client.gui.lobbysocial.partyCells
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.oneconfig.api.ui.v1.components.Divider

@Composable
internal fun VoiceStrip(window: SocialWindowState, modifier: Modifier = Modifier) {
    val voice by PartyVoice.ui.collectAsState()
    val current by PartyRepository.party.collectAsState()
    val party = current
    if (party == null || !voice.inVoice) return
    Column(modifier.fillMaxWidth()) {
        Divider(Modifier.padding(horizontal = 8.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("In voice", style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(stripDetail(party), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            VoiceButtons(party, leave = false)
            IconButton(icon = PartyIcons.SETTINGS, contentDescription = "Voice and devices", onClick = window::showVoice, modifier = Modifier.size(34.dp), tint = OneConfigTheme.current.textColor, tooltipPlacement = TooltipPlacement.BelowStart)
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
