@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import org.polyfrost.polyplus.client.gui.KitDangerColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SectionHeader
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleControl
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.lobby.LobbyCaptureFrequency
import org.polyfrost.polyplus.client.lobby.LobbyCaptureView
import org.polyfrost.polyplus.client.lobby.LobbyMusic
import org.polyfrost.polyplus.client.lobby.LobbyPacks
import org.polyfrost.polyplus.client.lobby.LobbyReveal
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.lobby.LobbyTime
import kotlin.math.roundToInt

private const val VOLUME_MAX = 100f
private const val VOLUME_STEP = 5f
private const val LIMIT_MAX = 100f
private const val LIMIT_STEP = 5f

@Composable
private fun SectionColumn(content: @Composable ColumnScope.() -> Unit) {
    val scroll = rememberScrollState()
    ScrollContainer(scroll, Modifier.fillMaxSize(), scrollbar = false, fadingEdges = true) {
        Column(Modifier.fillMaxWidth().padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun <T> SettingSelect(label: String, options: List<T>, selected: T, onSelect: (T) -> Unit) {
    Select(selected, options, onSelect, Modifier.fillMaxWidth(), size = ControlSize.Large, leadingLabel = label)
}

@Composable
internal fun MusicSection() {
    SectionColumn {
        MusicPicker()
        SectionHeader("Time and reveal")
        LookPickers()
    }
}

@Composable
private fun MusicPicker() {
    var music by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyMusic) }
    SettingSelect("Play", LobbyMusic.entries, music) {
        music = it
        PolyPlusLobbyConfig.lobbyMusic = it
        PolyPlusLobbyConfig.save()
    }
    musicDetail(music)?.let { SceneNote(it) }
    if (music != LobbyMusic.OFF && music != LobbyMusic.MINECRAFT) {
        MusicVolume()
        var muted by remember { mutableStateOf(PolyPlusLobbyConfig.muteLobbyMusicUnfocused) }
        ToggleRow(
            "Mute when unfocused",
            muted,
            {
                muted = it
                PolyPlusLobbyConfig.muteLobbyMusicUnfocused = it
                PolyPlusLobbyConfig.save()
            },
            Modifier.fillMaxWidth(),
            description = "Fades out while the game is in the background",
            control = ToggleControl.Switch,
            framed = false,
        )
    }
}

@Composable
private fun MusicVolume() {
    var percent by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyMusicVolume) }
    Slider(
        value = percent,
        onValueChange = {
            percent = it
            PolyPlusLobbyConfig.lobbyMusicVolume = it
        },
        modifier = Modifier.fillMaxWidth(),
        range = 0f..VOLUME_MAX,
        step = VOLUME_STEP,
        label = "Volume",
        valueText = { "${it.roundToInt()}%" },
        onValueChangeFinished = PolyPlusLobbyConfig::save,
        sound = null,
    )
}

private fun musicDetail(music: LobbyMusic): String? = when (music) {
    LobbyMusic.MATCH_SCENE -> "Follows the scene and time of day"
    LobbyMusic.MINECRAFT -> "The game's own menu music"
    LobbyMusic.OFF -> "No music in the lobby"
    else -> null
}

@Composable
private fun LookPickers() {
    var time by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyTime) }
    var reveal by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyReveal) }
    SettingSelect("Time of day", LobbyTime.entries, time) {
        time = it
        PolyPlusLobbyConfig.lobbyTime = it
        PolyPlusLobbyConfig.save()
    }
    SettingSelect("Reveal", LobbyReveal.entries, reveal) {
        reveal = it
        PolyPlusLobbyConfig.lobbyReveal = it
        PolyPlusLobbyConfig.save()
    }
    SceneNote("The reveal plays each time the menu opens")
}

@Composable
internal fun CaptureSection(state: SceneManagerState) {
    SectionColumn {
        val enabled = LobbySceneStore.captureEnabled
        ToggleRow(
            "Save scenes",
            enabled,
            { LobbySceneStore.captureEnabled = it },
            Modifier.fillMaxWidth(),
            description = "Turn off to stop every capture, even by hand",
            control = ToggleControl.Switch,
            framed = false,
        )
        if (enabled) CaptureOptions()
        SectionHeader("Storage")
        key(LobbyPacks.revision) { StorageSummary(state) }
    }
}

@Composable
private fun CaptureOptions() {
    var frequency by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyCaptureFrequency) }
    var view by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyCaptureView) }
    SettingSelect("Save automatically", LobbyCaptureFrequency.entries, frequency) {
        frequency = it
        PolyPlusLobbyConfig.lobbyCaptureFrequency = it
        PolyPlusLobbyConfig.save()
    }
    if (frequency == LobbyCaptureFrequency.OFF) SceneNote("Only spots you capture by hand are saved")
    SettingSelect("View", LobbyCaptureView.entries, view) {
        view = it
        PolyPlusLobbyConfig.lobbyCaptureView = it
        PolyPlusLobbyConfig.save()
    }
    CaptureLimit()
}

@Composable
private fun CaptureLimit() {
    var limit by remember { mutableStateOf(PolyPlusLobbyConfig.sceneCaptureLimit) }
    Slider(
        value = limit,
        onValueChange = {
            limit = it
            PolyPlusLobbyConfig.sceneCaptureLimit = it
            LobbySceneStore.captureLimit = it.roundToInt()
        },
        modifier = Modifier.fillMaxWidth(),
        range = 0f..LIMIT_MAX,
        step = LIMIT_STEP,
        label = "Scene limit",
        valueText = { if (it <= 0f) "No limit" else "${it.roundToInt()}" },
        onValueChangeFinished = {
            PolyPlusLobbyConfig.save()
            LobbySceneStore.pruneAutomatic()
        },
        sound = null,
    )
    SceneNote("How many automatic captures to keep. The oldest go first.")
}

@Composable
private fun StorageSummary(state: SceneManagerState) {
    val all = LobbySceneStore.captures
    val clearable = LobbySceneStore.clearable
    SceneNote(storageSummary(all))
    SceneNote("Favourites, manual captures and the scene you picked are never removed to make room.")
    TextButton(
        text = "Delete automatic",
        onClick = {
            state.pending = SceneDeletion(
                "Delete ${plural(clearable.size, "automatic capture")}?",
                "Favourites, manual captures and the scene you picked are kept. This can't be undone.",
                clearable,
            )
        },
        size = ControlSize.Regular,
        style = ControlStyle.DangerFilled,
        dangerColor = KitDangerColor,
        enabled = clearable.isNotEmpty(),
    )
}

private fun storageSummary(all: List<LobbySceneEntry>): String {
    if (all.isEmpty()) return "No saved scenes yet."
    val favourites = all.count { it.favourite }.takeIf { it > 0 }?.let { plural(it, "favourite") }
    val unavailable = all.count { !LobbySceneStore.usable(it) }.takeIf { it > 0 }?.let { "$it unavailable" }
    val counts = listOfNotNull(plural(all.size, "saved scene"), favourites, unavailable).joinToString(", ")
    return sceneSize(all.sumOf { it.bytes })?.let { "$counts. Using $it." } ?: "$counts."
}
