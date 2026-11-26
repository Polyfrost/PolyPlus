@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.api.TinyFdApi
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.utils.v1.Multithreading
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PartyVoiceSettings
import org.polyfrost.polyplus.client.party.soundboard.SoundDecoder
import org.polyfrost.polyplus.client.party.soundboard.Soundboard
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.nio.file.Path
import kotlin.math.roundToInt

private const val PLAY_ICON = "assets/polyplus/mainmenu/play-circle.svg"
private const val STOP_ICON = "assets/polyplus/mainmenu/stop-circle.svg"
private const val STAR = "star"
private const val STAR_FILLED = "star-filled"
private const val FOLDER = "folder"
private const val SOUNDS_ON = "assets/polyplus/mainmenu/volume-max.svg"
private const val SOUNDS_OFF = "assets/polyplus/mainmenu/volume-x.svg"
private val SoundVolumeWidth = 132.dp

@Composable
internal fun ColumnScope.SoundboardSettings(control: Dp) {
    val sounds by Soundboard.sounds.collectAsState()
    val playing by Soundboard.playing.collectAsState()
    val problem by Soundboard.problem.collectAsState()
    val voice by PartyVoice.ui.collectAsState()
    val party by PartyRepository.party.collectAsState()
    val blocked = party?.let { PartyRepository.self(it)?.soundboardMuted } == true
    var notice by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Soundboard.Sound?>(null) }
    LaunchedEffect(Unit) { Soundboard.reload() }

    fun addSound() {
        if (picking) return
        picking = true
        PolyPlusClient.SCOPE.launch {
            val picked = withContext(Dispatchers.IO) { runCatching { pickSound() }.getOrNull() }
            val error = picked?.let { Soundboard.add(it) }
            ClientPlatform.runOnMain {
                picking = false
                notice = error
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val status = when {
            blocked -> "Your party leader muted your soundboard. Only you will hear these."
            !voice.inVoice -> "You're not in voice, so only you will hear these."
            !voice.soundsReady -> if (voice.deafened) "Undeafen to play sounds for your party." else "Your party can't hear sounds right now."
            else -> null
        }
        status?.let { VoiceHint(it) }
        (notice ?: problem)?.let { message ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, Modifier.weight(1f), color = OneConfigTheme.current.warning, style = OneConfigTheme.current.typography.small)
                IconButton(icon = LobbyIcons.CLOSE, contentDescription = "Dismiss", onClick = {
                    notice = null
                    Soundboard.clearProblem()
                }, modifier = Modifier.size(28.dp), tooltipPlacement = TooltipPlacement.BelowCenter)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = if (picking) "Choosing..." else "Add sound", onClick = ::addSound, style = ControlStyle.Primary, enabled = !picking)
            TextButton(text = "Open folder", onClick = { Multithreading.submit { DesktopHelper.open(Soundboard.folder.toFile()) } })
            IconButton(icon = LobbyIcons.REFRESH, contentDescription = "Reload sounds", onClick = Soundboard::reload, modifier = Modifier.size(34.dp), tooltipPlacement = TooltipPlacement.BelowCenter)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                if (playing.isNotEmpty()) TextButton(text = "Stop all", onClick = Soundboard::stopAll)
            }
        }
    }

    Section("Volume") {
        SettingRow("Your party hears") {
            PercentSlider(PartyVoiceSettings.soundboardVolume(), 200f, control, "Soundboard volume for your party", PartyVoiceSettings::setSoundboardVolume)
        }
        SettingRow("You hear") {
            PercentSlider(PartyVoiceSettings.soundboardMonitor(), 100f, control, "Soundboard volume for you", PartyVoiceSettings::setSoundboardMonitor)
        }
        SettingRow("Other people's sounds") {
            PercentSlider(PartyVoiceSettings.soundboardOthers(), 200f, control, "Volume of your party's sounds", PartyVoiceSettings::setSoundboardOthers)
        }
    }

    Section(if (sounds.isEmpty()) "Sounds" else "Sounds (${sounds.size})") {
        if (sounds.isEmpty()) {
            EmptyState(
                title = "No sounds yet",
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                icon = FOLDER,
                text = "Add an ${SoundDecoder.FORMATS} file up to ${SoundDecoder.MAX_SECONDS} seconds long, or drop files into your sounds folder.",
            )
        } else {
            val ordered = remember(sounds, Soundboard.favorites) { Soundboard.ordered(sounds) }
            ordered.forEach { sound -> SoundRow(sound, sound.id in playing) { removing = sound } }
        }
        if (!PolyPlusConfig.soundboardWheelKeybind.isBound) {
            VoiceHint("Set a Soundboard key in PolyPlus keybinds to play sounds from a wheel while you play. Starred sounds come first.")
        }
    }

    party?.let { current ->
        val others = PartyRepository.others(current)
        if (others.isNotEmpty()) {
            val names by PlayerNamesRepository.names.collectAsState()
            Section("Party members' sounds") {
                others.forEach { member ->
                    MemberSoundRow(member.player, names[member.player] ?: PlayerNamesRepository.nameOr(member.player), member.soundboardMuted)
                }
                VoiceHint(
                    if (PartyRepository.isLeader(current)) {
                        "These only change what you hear. To mute someone's sounds for everyone, use their menu in the party list."
                    } else {
                        "These only change what you hear."
                    },
                )
            }
        }
    }

    removing?.let { sound ->
        ConfirmDialog(
            title = "Delete ${sound.name}?",
            text = "This removes the file from your sounds folder.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            icon = LobbyIcons.TRASH,
            danger = true,
            onConfirm = {
                Soundboard.remove(sound)
                removing = null
            },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun SoundRow(sound: Soundboard.Sound, playing: Boolean, onRemove: () -> Unit) {
    val favorite = Soundboard.isFavorite(sound.id)
    var volume by remember(sound.id) { mutableStateOf(Soundboard.volumeOf(sound.id) * 100f) }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(
            icon = if (playing) STOP_ICON else PLAY_ICON,
            contentDescription = if (playing) "Stop ${sound.name}" else "Play ${sound.name}",
            onClick = { Soundboard.toggle(sound) },
            modifier = Modifier.size(34.dp),
            active = playing,
            tooltipPlacement = TooltipPlacement.BelowStart,
        )
        Text(
            sound.name,
            Modifier.weight(1f),
            style = OneConfigTheme.current.typography.body.copy(fontWeight = if (playing) FontWeight.Medium else FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        IconButton(
            icon = if (favorite) STAR_FILLED else STAR,
            contentDescription = if (favorite) "Unstar" else "Star",
            onClick = { Soundboard.toggleFavorite(sound.id) },
            modifier = Modifier.size(30.dp),
            selected = favorite,
            tooltipPlacement = TooltipPlacement.BelowCenter,
        )
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                Soundboard.setVolume(sound.id, it / 100f)
            },
            modifier = Modifier.width(SoundVolumeWidth),
            range = 0f..Soundboard.MAX_VOLUME * 100f,
            step = 5f,
            onValueChangeFinished = Soundboard::save,
            contentDescription = "${sound.name} volume",
        )
        Text("${volume.roundToInt()}%", Modifier.width(40.dp), style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
        IconButton(
            icon = LobbyIcons.TRASH,
            contentDescription = "Delete ${sound.name}",
            onClick = onRemove,
            modifier = Modifier.size(30.dp),
            danger = true,
            tooltipPlacement = TooltipPlacement.BelowEnd,
        )
    }
}

@Composable
private fun MemberSoundRow(player: String, name: String, leaderMuted: Boolean) {
    val muted = PartyVoiceSettings.isSoundMuted(player)
    var volume by remember(player) { mutableStateOf(PartyVoiceSettings.soundVolumeOf(player) * 100f) }
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(name, style = OneConfigTheme.current.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            if (leaderMuted) Text("Muted by the party leader", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                PartyVoiceSettings.setSoundVolume(player, it / 100f)
            },
            modifier = Modifier.width(SoundVolumeWidth),
            range = 0f..200f,
            step = 5f,
            enabled = !muted,
            contentDescription = "Volume of $name's sounds",
        )
        Text("${volume.roundToInt()}%", Modifier.width(40.dp), style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
        IconButton(
            icon = if (muted) SOUNDS_OFF else SOUNDS_ON,
            contentDescription = if (muted) "Unmute $name's sounds" else "Mute $name's sounds",
            onClick = { PartyVoiceSettings.toggleSoundMute(player) },
            modifier = Modifier.size(30.dp),
            selected = muted,
            tooltipPlacement = TooltipPlacement.BelowEnd,
        )
    }
}

@Composable
internal fun PercentSlider(start: Float, max: Float, width: Dp, description: String, onChange: (Float) -> Unit) {
    var percent by remember { mutableStateOf(start) }
    Row(Modifier.width(width), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Slider(
            value = percent,
            onValueChange = {
                percent = it
                onChange(it)
            },
            modifier = Modifier.weight(1f),
            range = 0f..max,
            step = 5f,
            onValueChangeFinished = { PolyPlusConfig.save() },
            contentDescription = description,
        )
        Text("${percent.roundToInt()}%", Modifier.width(40.dp), style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false)
    }
}

@Composable
internal fun VoiceHint(text: String) {
    Text(text, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small)
}

private fun pickSound(): Path? = TinyFdApi.getInstance().openFileSelector(
    "Choose a sound",
    null,
    SoundDecoder.EXTENSIONS.map { "*.$it" }.toTypedArray(),
    "Sound files",
)
