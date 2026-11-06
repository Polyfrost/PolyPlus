@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialBorderWidth
import org.polyfrost.polyplus.client.gui.SocialDropdownBackground
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.EscapeCloses
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.lobbysocial.DeviceOptions
import org.polyfrost.polyplus.client.gui.lobbysocial.DeviceVolume
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.gui.lobbysocial.SectionHeading
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.party.VoiceInputMode

private val Edge = 24.dp
private val ControlWidth = 300.dp
private val ControlHeight = 32.dp
private val LabelRoom = 140.dp
private val PercentRoom = 50.dp
private val MenuDrop = 34.dp
private const val OPEN_CHEVRON = 180f

private class Choice<T>(val label: String, val value: T)

@Composable
internal fun VoicePane(window: SocialWindowState, modifier: Modifier) {
    val current by PartyRepository.party.collectAsState()
    val voice by PartyVoice.ui.collectAsState()
    val party = current
    val back = LocalSocialLook.current.backButton
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = Edge),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (back) BackButton(window::closeVoice)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PanelTitle("Voice and devices")
                Label(voiceStatus(party, voice.inVoice), 12.sp, color = MutedText)
            }
            if (party != null) VoiceButtons(party)
            if (!back) SocialIconButton(LobbyIcons.CLOSE, tooltip = "Close voice settings", onClick = window::closeVoice)
        }
        PaneRule()
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val control = (maxWidth - Edge * 2 - LabelRoom).coerceAtMost(ControlWidth)
            ScrollFade(Modifier.fillMaxSize().padding(start = Edge, end = Edge, top = Edge), 28.dp) {
                Section("Devices") {
                    SettingRow("Microphone") { DeviceSelect(true, control) }
                    SettingRow("Microphone volume") { VolumeControl(true, control) }
                    SettingRow("Speaker") { DeviceSelect(false, control) }
                    SettingRow("Speaker volume") { VolumeControl(false, control) }
                }
                Section("Talking") {
                    if (party != null) SettingRow("Voice mode") { ModeControl(party, control) }
                    SettingRow("Input") { InputControl(control) }
                }
                Spacer(Modifier.height(Edge))
            }
        }
    }
}

private fun voiceStatus(party: Party?, inVoice: Boolean): String = when {
    !PartyVoiceSettings.voiceAvailable() -> "Voice isn't available here"
    party == null -> "Join a party to use voice"
    inVoice -> "Connected"
    else -> "Not in voice"
}

@Composable
private fun Section(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(title, Modifier.padding(bottom = 4.dp))
        rows()
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 14.sp, Modifier.weight(1f))
        control()
    }
}

@Composable
private fun DeviceSelect(input: Boolean, width: Dp) {
    var open by remember { mutableStateOf(false) }
    val (interaction, hovered) = rememberSocialHover()
    val drop = with(LocalDensity.current) { MenuDrop.roundToPx() }
    val device = if (input) PartyVoiceSettings.inputDevice() else PartyVoiceSettings.outputDevice()
    val accent = OneConfigTheme.accent
    val fill by animateColorAsState(if (open) accent else SocialDropdownBackground)
    val edge by animateColorAsState(
        when {
            open -> accent
            hovered -> SocialHoverBorder
            else -> SocialBorderColor
        },
    )
    val ink = if (open) OneConfigTheme.current.accentTextColor else null
    Box {
        Row(
            Modifier
                .width(width)
                .height(ControlHeight)
                .clip(SocialFieldShape)
                .background(fill)
                .border(SocialBorderWidth, edge, SocialFieldShape)
                .hoverable(interaction)
                .clickableWithSound { open = !open }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Label(PartyAudio.deviceLabel(device).ifBlank { "Default" }, 14.sp, Modifier.weight(1f), color = ink ?: SocialTextPrimary)
            ThemedIcon(SOCIAL_ASSETS + "chevron-up.svg", ink ?: MutedText, 14.dp, if (open) Modifier else Modifier.rotate(OPEN_CHEVRON))
        }
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, drop),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(Modifier.width(width).popup().padding(6.dp)) { DeviceOptions(input) { open = false } }
            }
        }
    }
}

@Composable
private fun VolumeControl(input: Boolean, width: Dp) {
    DeviceVolume(input, Modifier.width(width).height(ControlHeight), label = null, sliderWidth = (width - PercentRoom).value)
}

@Composable
private fun <T> Segments(options: List<Choice<T>>, selected: T, width: Dp, enabled: Boolean = true, onSelect: (T) -> Unit) {
    SegmentTrack(Modifier.width(width).height(ControlHeight), enabled) {
        options.forEach { option ->
            SegmentButton(option.label, option.value == selected, Modifier.weight(1f).fillMaxHeight(), 12.sp, enabled, onClick = { onSelect(option.value) })
        }
    }
}

@Composable
private fun ModeControl(party: Party, width: Dp) {
    val options = remember { listOf(Choice("Everyone", PartyVoiceMode.Party), Choice("Proximity", PartyVoiceMode.Proximity)) }
    val mode = if (party.settings.voiceMode == PartyVoiceMode.Proximity) PartyVoiceMode.Proximity else PartyVoiceMode.Party
    Segments(options, mode, width, PartyRepository.isLeader(party)) { PartyRepository.setVoiceMode(it) }
}

@Composable
private fun InputControl(width: Dp) {
    val options = remember { listOf(Choice("Voice activity", VoiceInputMode.VOICE_ACTIVITY), Choice("Push to talk", VoiceInputMode.PUSH_TO_TALK)) }
    var mode by remember { mutableStateOf(PartyVoiceSettings.inputMode()) }
    Segments(options, mode, width) {
        mode = it
        PartyVoiceSettings.setInputMode(it)
    }
}
