@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.components.LinkText
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.lobby.LobbyMusic
import kotlin.math.roundToInt

private val POPOVER_WIDTH = 260.dp
private val POPOVER_GAP = 12.dp
private val POPOVER_RADIUS = 16.dp
private const val OPEN_DELAY_MILLIS = 250L
private const val CLOSE_DELAY_MILLIS = 220L
private const val SYNC_MILLIS = 300L
private const val VOLUME_STEP = 5f

private class MusicView(val music: LobbyMusic, val muted: Boolean, val volume: Float, val ambience: Float, val ambienceOn: Boolean) {
    val silent: Boolean get() = muted || music == LobbyMusic.OFF
}

private fun readMusic(): MusicView =
    MusicView(
        PolyPlusLobbyConfig.lobbyMusic,
        PolyPlusLobbyConfig.lobbyMusicMuted,
        PolyPlusLobbyConfig.lobbyMusicVolume,
        OneConfigConfig.uiAmbienceVolume,
        OneConfigConfig.enableUIAmbience,
    )

private fun setAmbience(volume: Float) {
    OneConfigConfig.uiAmbienceVolume = volume
    UiSounds.refreshAmbience()
}

private fun saveAmbience() {
    runCatching { OneConfigConfig.INSTANCE?.save() }
}

private fun toggleMusic() {
    if (PolyPlusLobbyConfig.lobbyMusic == LobbyMusic.OFF) {
        PolyPlusLobbyConfig.lobbyMusic = LobbyMusic.MATCH_SCENE
        PolyPlusLobbyConfig.lobbyMusicMuted = false
    } else {
        PolyPlusLobbyConfig.lobbyMusicMuted = !PolyPlusLobbyConfig.lobbyMusicMuted
    }
    PolyPlusLobbyConfig.save()
    A11y.announce(if (PolyPlusLobbyConfig.lobbyMusicMuted) "Music muted" else "Music on")
}

@Composable
internal fun LobbyMusicButton(lobby: LobbyShell, first: Boolean, last: Boolean) {
    var view by remember { mutableStateOf(readMusic()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SYNC_MILLIS)
            if (!dragging) view = readMusic()
        }
    }
    val tileHover = rememberHoverState()
    val popHover = rememberHoverState()
    val wanted = tileHover.hovered || popHover.hovered || dragging
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(wanted) {
        delay(if (wanted) OPEN_DELAY_MILLIS else CLOSE_DELAY_MILLIS)
        open = wanted
    }
    val settings by rememberUpdatedState {
        open = false
        lobby.openAmbience()
    }
    Box(
        Modifier
            .hoverable(tileHover.interactionSource)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.forEach { it.consume() }
                            settings()
                        }
                    }
                }
            },
    ) {
        DockTile(
            if (view.silent) "Unmute music" else "Mute music",
            DockTileWidth,
            first,
            last,
            active = open,
            onClick = {
                toggleMusic()
                view = readMusic()
            },
        ) {
            if (view.silent) LobbyIcon("volume-x", OneConfigTheme.current.mutedText) else LobbyIcon("volume-max")
        }
        if (open) {
            Popup(
                popupPositionProvider = rememberAnchoredPosition(Placement.AboveCenter, POPOVER_GAP),
                properties = PopupProperties(focusable = false),
            ) {
                LobbyUserTheme {
                    MusicPopover(
                        view,
                        Modifier.hoverable(popHover.interactionSource),
                        onVolume = {
                            dragging = true
                            PolyPlusLobbyConfig.lobbyMusicVolume = it
                            view = MusicView(view.music, view.muted, it, view.ambience, view.ambienceOn)
                        },
                        onVolumeDone = {
                            dragging = false
                            PolyPlusLobbyConfig.save()
                        },
                        onAmbience = {
                            dragging = true
                            setAmbience(it)
                            view = MusicView(view.music, view.muted, view.volume, it, view.ambienceOn)
                        },
                        onAmbienceDone = {
                            dragging = false
                            saveAmbience()
                        },
                        onSettings = settings,
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicPopover(
    view: MusicView,
    modifier: Modifier,
    onVolume: (Float) -> Unit,
    onVolumeDone: () -> Unit,
    onAmbience: (Float) -> Unit,
    onAmbienceDone: () -> Unit,
    onSettings: () -> Unit,
) {
    var volume by remember { mutableFloatStateOf(view.volume) }
    var ambience by remember { mutableFloatStateOf(view.ambience) }
    val mode = when {
        view.music == LobbyMusic.OFF -> "Off"
        view.muted -> "Muted"
        else -> view.music.toString()
    }
    Column(
        modifier
            .width(POPOVER_WIDTH)
            .popup(OneConfigTheme.shape(POPOVER_RADIUS))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Music", style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(mode, Modifier.padding(start = 12.dp), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        PanelSlider(
            "Lobby music",
            volume,
            {
                volume = it
                onVolume(it)
            },
            onVolumeDone,
            step = VOLUME_STEP,
            enabled = !view.silent && view.music != LobbyMusic.MINECRAFT,
            valueText = { "${it.roundToInt()}%" },
        )
        PanelSlider(
            "OneConfig ambience",
            ambience,
            {
                ambience = it
                onAmbience(it)
            },
            onAmbienceDone,
            step = VOLUME_STEP,
            enabled = !view.muted && view.ambienceOn,
            valueText = { "${it.roundToInt()}%" },
        )
        LinkText("Music settings…", onSettings)
    }
}
