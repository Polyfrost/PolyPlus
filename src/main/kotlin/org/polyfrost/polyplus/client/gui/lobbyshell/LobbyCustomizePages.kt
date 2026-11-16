@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.KitDangerColor
import org.polyfrost.polyplus.client.gui.widgets.WidgetListEditor
import org.polyfrost.polyplus.client.gui.widgets.WidgetSurface
import org.polyfrost.polyplus.client.lobby.LobbyCaptureFrequency
import org.polyfrost.polyplus.client.lobby.LobbyCaptureView
import org.polyfrost.polyplus.client.lobby.LobbyMusic
import org.polyfrost.polyplus.client.lobby.LobbyPacks
import org.polyfrost.polyplus.client.lobby.LobbyReveal
import org.polyfrost.polyplus.client.lobby.LobbySceneEntry
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import kotlin.math.roundToInt

private const val VOLUME_STEP = 5f
private const val DEFAULT_VOLUME = 100f
private const val LIMIT_MAX = 100f
private const val LIMIT_STEP = 5f

@Composable
internal fun MusicPage(lobby: LobbyShell, state: CustomizeState) {
    var resets by remember { mutableIntStateOf(0) }
    key(resets) {
        PanelPageColumn {
            MusicOptions()
            PanelSection(null) {
                ResetRow({
                    PolyPlusLobbyConfig.lobbyMusic = LobbyMusic.MATCH_SCENE
                    PolyPlusLobbyConfig.lobbyMusicMuted = false
                    PolyPlusLobbyConfig.lobbyMusicVolume = DEFAULT_VOLUME
                    PolyPlusLobbyConfig.muteLobbyMusicUnfocused = false
                    PolyPlusLobbyConfig.save()
                    resets++
                })
            }
        }
    }
}

@Composable
private fun MusicOptions() {
    var music by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyMusic) }
    var muted by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyMusicMuted) }
    var volume by remember { mutableFloatStateOf(PolyPlusLobbyConfig.lobbyMusicVolume) }
    var unfocused by remember { mutableStateOf(PolyPlusLobbyConfig.muteLobbyMusicUnfocused) }
    val off = muted || music == LobbyMusic.OFF
    PanelSection("Track", divider = false) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            LobbyMusic.entries.filter { it != LobbyMusic.OFF }.forEach { track ->
                ChoiceRow(track.toString(), !off && music == track, musicDetail(track)) {
                    music = track
                    muted = false
                    PolyPlusLobbyConfig.lobbyMusic = track
                    PolyPlusLobbyConfig.lobbyMusicMuted = false
                    PolyPlusLobbyConfig.save()
                }
            }
            ChoiceRow("Off", off, null) {
                muted = true
                PolyPlusLobbyConfig.lobbyMusicMuted = true
                PolyPlusLobbyConfig.save()
            }
        }
    }
    PanelSection("Playback") {
        if (music == LobbyMusic.MINECRAFT) {
            PanelHint("The game's own music uses its music volume.")
        } else {
            PanelSlider(
                "Volume",
                volume,
                {
                    volume = it
                    PolyPlusLobbyConfig.lobbyMusicVolume = it
                },
                PolyPlusLobbyConfig::save,
                step = VOLUME_STEP,
                enabled = !off,
                valueText = { "${it.roundToInt()}%" },
            )
        }
        PanelToggle(
            "Mute when the game is unfocused",
            unfocused,
            {
                unfocused = it
                PolyPlusLobbyConfig.muteLobbyMusicUnfocused = it
                PolyPlusLobbyConfig.save()
            },
            description = "Fades out while the game is in the background",
            enabled = !off && music != LobbyMusic.MINECRAFT,
        )
    }
}

private fun musicDetail(music: LobbyMusic): String? = when (music) {
    LobbyMusic.MATCH_SCENE -> "Follows the scene and time of day"
    LobbyMusic.MINECRAFT -> "The game's own menu music"
    else -> null
}

@Composable
internal fun CapturePage(lobby: LobbyShell, state: CustomizeState) {
    var resets by remember { mutableIntStateOf(0) }
    var confirming by remember { mutableStateOf<List<LobbySceneEntry>?>(null) }
    key(resets) {
        PanelPageColumn {
            val enabled = LobbySceneStore.captureEnabled
            PanelSection(null, divider = false) {
                PanelToggle(
                    "Save scenes",
                    enabled,
                    { LobbySceneStore.captureEnabled = it },
                    description = "Turn off to stop every capture, even by hand",
                    prominent = true,
                )
                PanelHint("Capture spots from the pause menu.")
            }
            if (enabled) CaptureOptions()
            PanelSection("Storage") {
                key(LobbyPacks.revision) { StorageSummary { confirming = it } }
            }
            PanelSection(null) {
                ResetRow({
                    LobbySceneStore.captureEnabled = true
                    PolyPlusLobbyConfig.lobbyCaptureFrequency = LobbyCaptureFrequency.NORMAL
                    PolyPlusLobbyConfig.lobbyCaptureView = LobbyCaptureView.BEST
                    PolyPlusLobbyConfig.sceneCaptureLimit = 0f
                    LobbySceneStore.captureLimit = 0
                    PolyPlusLobbyConfig.save()
                    resets++
                })
            }
        }
    }
    confirming?.let { entries ->
        ConfirmDialog(
            title = "Delete ${plural(entries.size, "automatic scene")}?",
            text = "Favourites, manual captures and the scene you picked stay. This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            icon = LobbyIcons.TRASH,
            danger = true,
            confirmEnabled = true,
            onConfirm = {
                confirming = null
                removeScenes(entries)
            },
            onDismiss = { confirming = null },
        )
    }
}

@Composable
private fun CaptureOptions() {
    var frequency by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyCaptureFrequency) }
    var view by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyCaptureView) }
    var limit by remember { mutableFloatStateOf(PolyPlusLobbyConfig.sceneCaptureLimit) }
    PanelSection("Save automatically") {
        Segmented(
            LobbyCaptureFrequency.entries,
            frequency,
            {
                frequency = it
                PolyPlusLobbyConfig.lobbyCaptureFrequency = it
                PolyPlusLobbyConfig.save()
            },
        )
        if (frequency == LobbyCaptureFrequency.OFF) PanelHint("Only spots you capture by hand are saved.")
    }
    PanelSection("View") {
        Segmented(
            LobbyCaptureView.entries,
            view,
            {
                view = it
                PolyPlusLobbyConfig.lobbyCaptureView = it
                PolyPlusLobbyConfig.save()
            },
        )
        PanelHint("Use the best view around you, or a random good one.")
    }
    PanelSection("Automatic scene limit") {
        PanelSlider(
            "Keep",
            limit,
            {
                limit = it
                PolyPlusLobbyConfig.sceneCaptureLimit = it
                LobbySceneStore.captureLimit = it.roundToInt()
            },
            {
                PolyPlusLobbyConfig.save()
                LobbySceneStore.pruneAutomatic()
            },
            range = 0f..LIMIT_MAX,
            step = LIMIT_STEP,
            valueText = { if (it <= 0f) "No limit" else "${it.roundToInt()}" },
        )
        PanelHint("The oldest automatic scenes go first.")
    }
}

@Composable
private fun StorageSummary(onDelete: (List<LobbySceneEntry>) -> Unit) {
    val all = LobbySceneStore.captures
    val clearable = LobbySceneStore.clearable
    PanelHint(storageSummary(all))
    PanelHint("Favourites, manual captures and the scene you picked are never removed to make room.")
    TextButton(
        text = "Delete automatic scenes",
        onClick = { onDelete(clearable) },
        modifier = Modifier.fillMaxWidth(),
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

@Composable
internal fun RevealPage(lobby: LobbyShell, state: CustomizeState) {
    var reveal by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyReveal) }
    val choose: (LobbyReveal) -> Unit = {
        reveal = it
        PolyPlusLobbyConfig.lobbyReveal = it
        PolyPlusLobbyConfig.save()
    }
    PanelPageColumn {
        PanelSection(null, divider = false) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                LobbyReveal.entries.forEach { option ->
                    ChoiceRow(option.toString(), reveal == option, revealDetail(option)) { choose(option) }
                }
            }
            PanelHint("Plays each time the menu opens.")
            if (Motion.reduced) PanelHint("Reduced motion is on, so the scene shows without an animation.")
        }
        PanelSection(null) {
            ResetRow({ choose(LobbyReveal.RANDOM) })
        }
    }
}

private fun revealDetail(reveal: LobbyReveal): String? = when (reveal) {
    LobbyReveal.RANDOM -> "A different one each time"
    else -> null
}

@Composable
internal fun WidgetsPage(lobby: LobbyShell, state: CustomizeState) {
    PanelPageColumn {
        PanelHint("Turn widgets on and drag to reorder them.")
        WidgetListEditor(WidgetSurface.LOBBY)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, detail: String?, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    val shape = OneConfigTheme.shape(CustomizeKit.RowRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (detail == null) CustomizeKit.RowHeight - 6.dp else CustomizeKit.RowHeight + 6.dp)
            .selectableRow(selected, { if (!selected) onClick() }, label, shape, true, ItemSelectionStyle.Accent, A11yRole.RadioButton)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = SelectionDefaults.itemContentColor(selected, ItemSelectionStyle.Accent),
                style = theme.typography.body.copy(fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            if (detail != null) {
                Text(detail, color = theme.textColorSecondary, style = theme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        if (selected) LobbyIcon("check", OneConfigTheme.accent, 16.dp) else Spacer(Modifier.size(16.dp))
    }
}
