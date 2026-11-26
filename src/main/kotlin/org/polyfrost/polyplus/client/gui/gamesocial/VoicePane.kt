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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.Switch
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbysocial.DeviceVolume
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.ScrollFade
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.party.NoiseSuppressor
import org.polyfrost.polyplus.client.party.PartyAudio
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.party.VoiceInputMode

private val Edge = 24.dp
private val ControlWidth = 300.dp
private val ControlHeight = 32.dp
private val LabelRoom = 140.dp
private val MeterHeight = 8.dp

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
                Text(voiceStatus(party, voice.inVoice), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
            if (party != null) VoiceButtons(party)
            if (!back) IconButton(icon = LobbyIcons.CLOSE, contentDescription = "Close voice settings", onClick = window::closeVoice, modifier = Modifier.size(34.dp), tint = OneConfigTheme.current.textColor, tooltipPlacement = TooltipPlacement.BelowStart)
        }
        Divider()
        val tabs = remember { listOf(TabItem(VoiceTab.Settings, "Voice"), TabItem(VoiceTab.Soundboard, "Soundboard")) }
        TabRow(tabs, window.voiceTab, { window.voiceTab = it }, Modifier.padding(horizontal = Edge, vertical = 8.dp), style = TabStyle.Underline, fill = false)
        DisposableEffect(Unit) { onDispose { PartyVoice.setMicTest(false) } }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val control = (maxWidth - Edge * 2 - LabelRoom).coerceAtMost(ControlWidth)
            ScrollFade(Modifier.fillMaxSize().padding(start = Edge, end = Edge, top = 12.dp), 28.dp) {
                when (window.voiceTab) {
                    VoiceTab.Settings -> VoiceSettings(party, voice, control)
                    VoiceTab.Soundboard -> SoundboardSettings(control)
                }
                Spacer(Modifier.height(Edge))
            }
        }
    }
}

@Composable
private fun VoiceSettings(party: Party?, voice: PartyVoice.Ui, control: Dp) {
    var inputMode by remember { mutableStateOf(PartyVoiceSettings.inputMode()) }
    var sensitivity by remember { mutableStateOf(PartyVoiceSettings.sensitivity()) }
    var automatic by remember { mutableStateOf(PartyVoiceSettings.autoSensitivity()) }
    var suppressing by remember { mutableStateOf(PartyVoiceSettings.noiseSuppression()) }
    var strength by remember { mutableStateOf(PartyVoiceSettings.noiseStrength()) }
    var echo by remember { mutableStateOf(PartyVoiceSettings.echoCancellation()) }
    val rnnoise = NoiseSuppressor.available
    Section("Devices") {
        SettingRow("Microphone") { DeviceSelect(true, control) }
        SettingRow("Microphone volume") { VolumeControl(true, control) }
        SettingRow("Speaker") { DeviceSelect(false, control) }
        SettingRow("Speaker volume") { VolumeControl(false, control) }
    }
    Section("Talking") {
        if (party != null) SettingRow("Voice mode") { ModeControl(party, control) }
        SettingRow("Input") {
            InputControl(inputMode, control) {
                inputMode = it
                PartyVoiceSettings.setInputMode(it)
            }
        }
        if (inputMode == VoiceInputMode.PUSH_TO_TALK) {
            SettingRow("Mic level") { Box(Modifier.width(control)) { LevelMeter(null) } }
        } else {
            SettingRow("Automatic sensitivity") {
                Switch(
                    checked = automatic && rnnoise,
                    onCheckedChange = {
                        automatic = it
                        PartyVoiceSettings.setAutoSensitivity(it)
                    },
                    enabled = rnnoise,
                )
            }
            SettingRow(if (automatic && rnnoise) "Mic level" else "Sensitivity", height = if (automatic && rnnoise) 40.dp else 56.dp) {
                Column(Modifier.width(control), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!(automatic && rnnoise)) {
                        Slider(
                            value = sensitivity,
                            onValueChange = {
                                sensitivity = it
                                PartyVoiceSettings.setSensitivity(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            range = 0f..100f,
                            step = 1f,
                            onValueChangeFinished = { PolyPlusConfig.save() },
                            contentDescription = "Voice activity sensitivity",
                        )
                    }
                    LevelMeter(if (automatic && rnnoise) null else sensitivity / 100f)
                }
            }
            if (inputMode == VoiceInputMode.PUSH_TO_MUTE && !PolyPlusConfig.voicePushToMuteKeybind.isBound) {
                VoiceHint("Set a Push to Mute key in PolyPlus keybinds.")
            }
        }
    }
    Section("Sound processing") {
        SettingRow("Noise suppression") {
            Switch(
                checked = suppressing && rnnoise,
                onCheckedChange = {
                    suppressing = it
                    PartyVoiceSettings.setNoiseSuppression(it)
                },
                enabled = rnnoise,
            )
        }
        if (suppressing && rnnoise) {
            SettingRow("Strength") {
                PercentSlider(strength, 100f, control, "Noise suppression strength") {
                    strength = it
                    PartyVoiceSettings.setNoiseStrength(it)
                }
            }
        }
        if (!rnnoise) VoiceHint("Noise suppression and automatic sensitivity don't work on this computer.")
        SettingRow("Echo cancellation") {
            Switch(
                checked = echo,
                onCheckedChange = {
                    echo = it
                    PartyVoiceSettings.setEchoCancellation(it)
                },
            )
        }
        VoiceHint(
            if (echo) {
                "Filters your party's voices and sounds out of your mic, so they don't hear themselves through your speakers."
            } else {
                "Turn this on if you use speakers and your party hears themselves."
            },
        )
    }
    Section("Mic test") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(
                text = if (voice.micTest) "Stop test" else "Test mic",
                onClick = { PartyVoice.setMicTest(!voice.micTest) },
                active = voice.micTest,
            )
            VoiceHint(
                when {
                    voice.micTestFailed -> "Couldn't open your microphone. Check the device above and try again."
                    voice.micTest -> "Talk and you'll hear yourself. Wear headphones so you don't get an echo."
                    else -> "Hear what your party hears, with noise suppression and your volume applied."
                },
            )
        }
    }
}

@Composable
private fun LevelMeter(threshold: Float?) {
    val level by PartyVoice.inputLevel.collectAsState()
    val position = PartyVoiceSettings.levelPosition(level)
    val open = threshold == null || position >= threshold
    BoxWithConstraints(Modifier.fillMaxWidth().height(MeterHeight), contentAlignment = Alignment.CenterStart) {
        ProgressBar(
            position,
            Modifier.fillMaxWidth(),
            tint = if (open) Color.Unspecified else OneConfigTheme.current.mutedText,
            thickness = MeterHeight,
        )
        if (threshold != null) {
            Box(
                Modifier
                    .offset(x = (maxWidth * threshold - 1.dp).coerceAtLeast(0.dp))
                    .width(2.dp)
                    .height(MeterHeight + 6.dp)
                    .background(OneConfigTheme.current.textColor),
            )
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
internal fun Section(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title, Modifier.padding(bottom = 4.dp))
        rows()
    }
}

@Composable
internal fun SettingRow(label: String, height: Dp = 40.dp, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        control()
    }
}

@Composable
private fun DeviceSelect(input: Boolean, width: Dp) {
    val device = if (input) PartyVoiceSettings.inputDevice() else PartyVoiceSettings.outputDevice()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var scans by remember { mutableStateOf(0) }
    LaunchedEffect(pressed) { if (pressed) scans++ }
    val options = remember(input, device, scans) {
        val devices = listOf("") + if (input) PartyAudio.inputDevices() else PartyAudio.outputDevices()
        if (device in devices) devices else devices + device
    }
    Select(
        selected = device,
        options = options,
        onSelect = { option -> if (input) PartyVoiceSettings.setInputDevice(option) else PartyVoiceSettings.setOutputDevice(option) },
        modifier = Modifier.width(width),
        label = { PartyAudio.deviceLabel(it).ifBlank { "Default" } },
        interactionSource = source,
        contentDescription = if (input) "Microphone" else "Speaker",
    )
}

@Composable
private fun VolumeControl(input: Boolean, width: Dp) {
    DeviceVolume(input, Modifier.width(width).height(ControlHeight), label = null)
}

@Composable
private fun ModeControl(party: Party, width: Dp) {
    val tabs = remember { listOf(TabItem(PartyVoiceMode.Party, "Everyone"), TabItem(PartyVoiceMode.Proximity, "Proximity")) }
    val mode = if (party.settings.voiceMode == PartyVoiceMode.Proximity) PartyVoiceMode.Proximity else PartyVoiceMode.Party
    TabRow(tabs, mode, { PartyRepository.setVoiceMode(it) }, Modifier.width(width), style = TabStyle.Segmented, enabled = PartyRepository.isLeader(party))
}

@Composable
private fun InputControl(mode: VoiceInputMode, width: Dp, onSelect: (VoiceInputMode) -> Unit) {
    val tabs = remember { listOf(TabItem(VoiceInputMode.VOICE_ACTIVITY, "Voice activity"), TabItem(VoiceInputMode.PUSH_TO_TALK, "Push to talk"), TabItem(VoiceInputMode.PUSH_TO_MUTE, "Push to mute")) }
    TabRow(tabs, mode, onSelect, Modifier.width(width), style = TabStyle.Segmented)
}
