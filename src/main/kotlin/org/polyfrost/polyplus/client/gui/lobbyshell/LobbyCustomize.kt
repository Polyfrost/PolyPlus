@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalFoundationApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.lobby.LobbyMusic
import org.polyfrost.polyplus.client.lobby.LobbyTime
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyScene
import kotlin.math.roundToInt

private const val HIGHLIGHT_MILLIS = 1400
private const val HIGHLIGHT_ALPHA = 0.14f
private const val VOLUME_STEP = 5f
private const val SYNC_MILLIS = 300L

private val TIME_ORDER = listOf(LobbyTime.CAPTURED, LobbyTime.REAL, LobbyTime.THEME, LobbyTime.DAY, LobbyTime.SUNSET, LobbyTime.NIGHT)

internal enum class CustomizePage(val title: String) {
    Home("Customize your lobby"),
    AllScenes("All scenes"),
    ServerScenes("Server scenes"),
    Music("Music"),
    Capture("Capture"),
    Reveal("Opening animation"),
    Widgets("Widgets"),
}

internal class CustomizeState {
    var shown by mutableStateOf(false)
        private set
    private val stack = mutableStateListOf(CustomizePage.Home)
    val page: CustomizePage get() = stack.last()
    val depth: Int get() = stack.size
    var forward by mutableStateOf(true)
        private set
    var homeScroll by mutableStateOf(ScrollState(0))
        private set
    var ambienceRequest by mutableIntStateOf(0)
        private set
    internal var ambienceHandled = 0
    var onOpen: () -> Unit = {}

    fun open(page: CustomizePage = CustomizePage.Home) {
        if (!shown) {
            onOpen()
            stack.clear()
            stack += CustomizePage.Home
            homeScroll = ScrollState(0)
            forward = true
            shown = true
        }
        when {
            page == CustomizePage.Home -> if (stack.size > 1) {
                forward = false
                stack.removeRange(1, stack.size)
            }
            page != this.page -> {
                forward = true
                stack += page
            }
        }
    }

    fun back() {
        if (stack.size <= 1) return close()
        forward = false
        stack.removeAt(stack.lastIndex)
    }

    fun close() {
        shown = false
    }

    fun toggle() = if (shown) close() else open()

    fun focusAmbience() {
        open(CustomizePage.Home)
        ambienceRequest++
    }
}

@Composable
internal fun CustomizePanel(lobby: LobbyShell) {
    val state = lobby.customize
    val open = state.shown && lobby.page == LobbyPage.Home
    val page = state.page
    DockedPanel(open, page.title, state::close, if (page != CustomizePage.Home) state::back else null) {
        PanelPageSwitch(page, state.forward) { shown ->
            when (shown) {
                CustomizePage.Home -> HomePage(lobby, state)
                CustomizePage.AllScenes -> AllScenesPage(lobby, state)
                CustomizePage.ServerScenes -> ServerScenesPage(lobby, state)
                CustomizePage.Music -> MusicPage(lobby, state)
                CustomizePage.Capture -> CapturePage(lobby, state)
                CustomizePage.Reveal -> RevealPage(lobby, state)
                CustomizePage.Widgets -> WidgetsPage(lobby, state)
            }
        }
    }
}

@Composable
private fun HomePage(lobby: LobbyShell, state: CustomizeState) {
    val party by PartyRepository.party.collectAsState()
    PanelPageColumn(scroll = state.homeScroll) {
        PanelSection("Scene", divider = false) { SceneSection(lobby, state) }
        AmbienceSection(state)
        TimeSection(state)
        if (PartyScene.isMember(party)) PartySection()
        PanelSection(null) {
            DrillRow("package-01", "Widgets", null, { state.open(CustomizePage.Widgets) })
        }
        PanelSection("Main menu") {
            DrillRow("logo", "Switch to PolyPlus main menu", null, { state.close(); MainMenuReplacement.switchToPolyPlusMenu() })
            DrillRow("minecraft-block", "Switch to vanilla main menu", null, { state.close(); MainMenuReplacement.switchToVanilla() })
        }
    }
}

@Composable
private fun AmbienceSection(state: CustomizeState) {
    val requester = remember { BringIntoViewRequester() }
    val highlight = remember { Animatable(0f) }
    LaunchedEffect(state.ambienceRequest) {
        if (state.ambienceRequest == state.ambienceHandled) return@LaunchedEffect
        state.ambienceHandled = state.ambienceRequest
        withFrameNanos {}
        requester.bringIntoView()
        if (Motion.reduced) return@LaunchedEffect
        highlight.snapTo(1f)
        highlight.animateTo(0f, tween(HIGHLIGHT_MILLIS))
    }
    var music by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyMusic) }
    var muted by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyMusicMuted) }
    var volume by remember { mutableFloatStateOf(PolyPlusLobbyConfig.lobbyMusicVolume) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SYNC_MILLIS)
            music = PolyPlusLobbyConfig.lobbyMusic
            muted = PolyPlusLobbyConfig.lobbyMusicMuted
            volume = PolyPlusLobbyConfig.lobbyMusicVolume
        }
    }
    val playing = !muted && music != LobbyMusic.OFF
    val accent = OneConfigTheme.accent
    val shape = OneConfigTheme.shape(14.dp)
    PanelSection(
        "Ambience & music",
        Modifier
            .bringIntoViewRequester(requester)
            .background(accent.copy(alpha = HIGHLIGHT_ALPHA * highlight.value), shape),
    ) {
        PanelToggle(
            "Lobby music",
            playing,
            { on ->
                if (on && music == LobbyMusic.OFF) {
                    music = LobbyMusic.MATCH_SCENE
                    PolyPlusLobbyConfig.lobbyMusic = music
                }
                muted = !on
                PolyPlusLobbyConfig.lobbyMusicMuted = muted
                PolyPlusLobbyConfig.save()
            },
            prominent = true,
        )
        PanelSlider(
            "Volume",
            volume,
            {
                volume = it
                PolyPlusLobbyConfig.lobbyMusicVolume = it
            },
            PolyPlusLobbyConfig::save,
            step = VOLUME_STEP,
            enabled = playing && music != LobbyMusic.MINECRAFT,
            valueText = { "${it.roundToInt()}%" },
        )
        DrillRow("headphones-02", "Music", music.toString(), { state.open(CustomizePage.Music) })
    }
}

@Composable
private fun TimeSection(state: CustomizeState) {
    var time by remember { mutableStateOf(PolyPlusLobbyConfig.lobbyTime) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SYNC_MILLIS)
            time = PolyPlusLobbyConfig.lobbyTime
        }
    }
    val options = remember { TIME_ORDER + LobbyTime.entries.filterNot { it in TIME_ORDER } }
    PanelSection("Time of day") {
        Segmented(
            options,
            time,
            {
                time = it
                PolyPlusLobbyConfig.lobbyTime = it
                PolyPlusLobbyConfig.save()
            },
            label = ::timeLabel,
            columns = 3,
        )
        DrillRow("play-circle", "Opening animation", PolyPlusLobbyConfig.lobbyReveal.toString(), { state.open(CustomizePage.Reveal) })
    }
}

private fun timeLabel(time: LobbyTime): String = when (time) {
    LobbyTime.CAPTURED -> "As captured"
    LobbyTime.THEME -> "Match theme"
    LobbyTime.REAL -> "Real time"
    else -> time.toString()
}

@Composable
private fun PartySection() {
    val keepOwn = PartyScene.keepOwnBackdrop
    PanelSection("Party") {
        PanelToggle(
            "Keep my own backdrop",
            keepOwn,
            PartyScene::setKeepOwnBackdrop,
            description = "Use your own scene and time in parties",
        )
        if (!keepOwn) PanelHint(PartyScene.followedLabel()?.let { "Following your leader: $it" } ?: "Following your party leader's scene")
    }
}
