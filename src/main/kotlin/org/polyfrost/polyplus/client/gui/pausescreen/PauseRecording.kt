@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.compat.replay.ReplayProvider
import org.polyfrost.polyplus.compat.replay.ReplayProviders
import kotlin.math.cos
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val TICK_MILLIS = 250L
private const val DISCARD_ARM_MILLIS = 3000L
private const val PULSE_NANOS = 1_600_000_000.0
private val BAR_CONTROL = 32.dp
private val CARD_CONTROL = 24.dp
private val CARD_GLYPH = 16.dp

private sealed interface RecordingView

private data class IdleView(val starters: List<ReplayProvider>) : RecordingView

private data class LiveView(
    val provider: ReplayProvider,
    val paused: Boolean,
    val elapsedSeconds: Long?,
    val canPause: Boolean,
    val canDiscard: Boolean,
) : RecordingView

private class RecordingHandle(val view: RecordingView?, val refresh: () -> Unit)

private inline fun <T> safe(fallback: T, block: () -> T): T = runCatching(block).getOrDefault(fallback)

private fun readView(): RecordingView? = runCatching {
    val available = ReplayProviders.available
    if (available.isEmpty() || ReplayProviders.inReplay) return null
    val provider = ReplayProviders.recordingProvider
    val state = provider?.let { safe(null) { it.recording } }
    if (provider != null && state != null) {
        LiveView(
            provider,
            state.paused,
            state.elapsedMillis?.div(1000L),
            safe(false) { provider.canPause },
            safe(false) { provider.canDiscard },
        )
    } else {
        available.filter { safe(false) { it.canStart } }.takeIf { it.isNotEmpty() }?.let(::IdleView)
    }
}.getOrNull()

private fun elapsedText(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun recordingShown(): Boolean = readView() != null

@Composable
private fun rememberRecording(): RecordingHandle {
    var view by remember { mutableStateOf(readView()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MILLIS)
            view = readView()
        }
    }
    return RecordingHandle(view) { view = readView() }
}

@Composable
internal fun RecordingBarTool(screen: OneClientPauseScreen, glass: Boolean = true) {
    val recording = rememberRecording()
    when (val view = recording.view ?: return) {
        is IdleView -> StartTool(screen, view.starters) { press, choosing ->
            BarTile(tooltip = if (choosing) null else "Start recording", label = "Start recording", active = choosing, onClick = press, glass = glass) { hovered ->
                PauseGlyph(asset("video-recorder"), if (hovered || choosing) SocialTextPrimary else OneConfigTheme.current.mutedText, 20.dp)
            }
        }
        is LiveView -> BarTile(glass = glass) {
            Row(
                Modifier.padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecordingDot(view.paused)
                RecordingTime(view, 14.sp)
                LiveControls(screen, view, recording.refresh, BAR_CONTROL)
            }
        }
    }
}

@Composable
internal fun RecordingCardTool(screen: OneClientPauseScreen) {
    val recording = rememberRecording()
    when (val view = recording.view ?: return) {
        is IdleView -> StartTool(screen, view.starters, side = true) { press, choosing ->
            CardRow(label = "Start recording", active = choosing, onClick = press) { lit ->
                Box(Modifier.size(CARD_GLYPH), contentAlignment = Alignment.Center) {
                    PauseGlyph(asset("video-recorder"), if (lit) SocialTextPrimary else OneConfigTheme.current.mutedText, CARD_GLYPH)
                }
                Text("Record", style = OneConfigTheme.current.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        is LiveView -> CardRow {
            Box(Modifier.size(CARD_GLYPH), contentAlignment = Alignment.Center) { RecordingDot(view.paused) }
            Text(if (view.paused) "Paused" else "Rec", color = if (view.paused) OneConfigTheme.current.mutedText else SocialTextPrimary, style = OneConfigTheme.current.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            view.elapsedSeconds?.let { TabularLabel(elapsedText(it), 13.sp, if (view.paused) OneConfigTheme.current.mutedText else SocialTextPrimary) }
            Spacer(Modifier.weight(1f))
            LiveControls(screen, view, recording.refresh, CARD_CONTROL)
        }
    }
}

@Composable
private fun StartTool(
    screen: OneClientPauseScreen,
    starters: List<ReplayProvider>,
    side: Boolean = false,
    trigger: @Composable (press: () -> Unit, choosing: Boolean) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val start: (ReplayProvider) -> Unit = { provider ->
        runCatching { provider.start() }
        screen.closeIfCurrent()
    }
    Box {
        trigger({ if (starters.size == 1) start(starters.first()) else choosing = !choosing }, choosing)
        if (choosing && starters.size > 1) {
            val close = { choosing = false }
            Popup(
                popupPositionProvider = rememberToolAnchor(side),
                onDismissRequest = close,
                properties = PopupProperties(focusable = true),
            ) {
                EscapeCloses(close)
                MenuList(starters.map { provider -> MenuAction("Record with ${provider.name}", asset("video-recorder")) { start(provider) } }, close)
            }
        }
    }
}

@Composable
private fun RecordingTime(live: LiveView, size: TextUnit) {
    val ink = if (live.paused) OneConfigTheme.current.mutedText else SocialTextPrimary
    val seconds = live.elapsedSeconds
    if (seconds != null) {
        TabularLabel(elapsedText(seconds), size, ink)
        if (live.paused) Text("Paused", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body.copy(fontSize = size), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    } else {
        Text(if (live.paused) "Paused" else "Recording", color = ink, style = OneConfigTheme.current.typography.body.copy(fontSize = size, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun LiveControls(screen: OneClientPauseScreen, live: LiveView, refresh: () -> Unit, size: Dp) {
    val provider = live.provider
    var armed by remember(provider) { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(DISCARD_ARM_MILLIS)
            armed = false
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (live.canPause) {
            ChipButton(
                asset(if (live.paused) "play-circle" else "pause-circle"),
                if (live.paused) "Resume recording" else "Pause recording",
                size,
            ) {
                runCatching { provider.setPaused(!live.paused) }
                refresh()
            }
        }
        ChipButton(asset("check-circle"), provider.finishLabel, size) {
            runCatching { provider.finish() }
            screen.closeIfCurrent()
        }
        if (live.canDiscard) {
            ChipButton(asset("trash-01"), if (armed) "Click again to discard" else "Discard recording", size, danger = armed) {
                if (armed) {
                    armed = false
                    runCatching { provider.discard() }
                    refresh()
                } else {
                    armed = true
                }
            }
        }
    }
}

@Composable
private fun RecordingDot(paused: Boolean) {
    val frame by rememberFrame()
    val dot = SocialDangerText
    val reduced = Motion.reduced
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer { alpha = if (paused) 0.5f else if (reduced) 1f else (0.7f + 0.3f * cos(frame / PULSE_NANOS * 2.0 * Math.PI)).toFloat() }
            .background(dot, OneConfigTheme.current.circleShape),
    )
}

@Composable
private fun ChipButton(icon: String, tooltip: String, size: Dp, danger: Boolean = false, onClick: () -> Unit) {
    val controlSize = if (size > 24.dp) ControlSize.Regular else ControlSize.Compact
    IconButton(
        contentDescription = tooltip,
        onClick = onClick,
        size = controlSize,
        danger = danger,
        dangerColor = SocialDangerText,
        tooltipPlacement = TooltipPlacement.AboveCenter,
        active = danger,
        frameSize = size,
    ) { tint ->
        PauseGlyph(icon, tint, IconButtonDefaults.iconSize(controlSize))
    }
}
