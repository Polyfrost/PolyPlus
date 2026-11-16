@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import org.polyfrost.polyplus.client.gui.widgets.WidgetScope
import org.polyfrost.polyplus.client.gui.widgets.WidgetSize
import org.polyfrost.polyplus.compat.replay.ReplayProvider
import org.polyfrost.polyplus.compat.replay.ReplayProviders
import kotlin.math.cos
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val TICK_MILLIS = 250L
private const val DISCARD_ARM_MILLIS = 3000L
private const val PULSE_NANOS = 1_600_000_000.0
private val LIVE_CONTROL = 32.dp
private val BLOCK_ICON = 44.dp
private val BLOCK_ICON_RADIUS = 12.dp
private val BLOCK_GLYPH = 20.dp
private val BLOCK_GAP = 12.dp
private val START_PILL = 28.dp
private val TIME_TEXT = 18.sp
private const val ICON_WASH = 0.08f

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
internal fun RecordingWidget(scope: WidgetScope) {
    val recording = rememberRecording()
    when (val view = recording.view) {
        null -> WidgetFace(WidgetSize.BLOCK.height) {
            BlockLayout(asset("video-recorder"), false, "Record", "Not available right now")
        }
        is IdleView -> StartTool(scope::closeHost, view.starters) { press, choosing ->
            WidgetFace(WidgetSize.BLOCK.height, label = "Start recording", active = choosing, onClick = press) { lit ->
                val detail = view.starters.singleOrNull()?.let { "Save a replay with ${it.name}" } ?: "Pick what records this session"
                BlockLayout(asset("video-recorder"), lit, "Record", detail) { StartPill() }
            }
        }
        is LiveView -> WidgetFace(WidgetSize.BLOCK.height) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = WIDGET_INSET),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BLOCK_GAP),
            ) {
                Box(Modifier.size(BLOCK_ICON).background(SocialDangerText.copy(alpha = ICON_WASH), OneConfigTheme.shape(BLOCK_ICON_RADIUS)), contentAlignment = Alignment.Center) {
                    RecordingDot(view.paused)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RecordingTime(view)
                    Text(view.provider.name, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
                LiveControls(scope::closeHost, view, recording.refresh, LIVE_CONTROL)
            }
        }
    }
}

@Composable
internal fun BlockLayout(
    icon: String,
    lit: Boolean,
    title: String,
    detail: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    val muted = OneConfigTheme.current.mutedText
    Row(
        Modifier.fillMaxWidth().padding(horizontal = WIDGET_INSET),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BLOCK_GAP),
    ) {
        Box(Modifier.size(BLOCK_ICON).background(SocialTextPrimary.copy(alpha = ICON_WASH), OneConfigTheme.shape(BLOCK_ICON_RADIUS)), contentAlignment = Alignment.Center) {
            PauseGlyph(icon, if (lit) SocialTextPrimary else muted, BLOCK_GLYPH)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OneConfigTheme.current.typography.label.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(detail, color = muted, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

@Composable
private fun StartPill() {
    Box(
        Modifier.height(START_PILL).background(OneConfigTheme.accent, OneConfigTheme.current.shapes.pill).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("Start", color = OneConfigTheme.current.accentTextColor, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun StartTool(
    closeHost: () -> Unit,
    starters: List<ReplayProvider>,
    trigger: @Composable (press: () -> Unit, choosing: Boolean) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    val start: (ReplayProvider) -> Unit = { provider ->
        runCatching { provider.start() }
        closeHost()
    }
    Box(Modifier.fillMaxWidth()) {
        trigger({ if (starters.size == 1) start(starters.first()) else choosing = !choosing }, choosing)
        if (choosing && starters.size > 1) {
            val close = { choosing = false }
            Popup(
                popupPositionProvider = rememberWidgetAnchor(),
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
private fun RecordingTime(live: LiveView) {
    val ink = if (live.paused) OneConfigTheme.current.mutedText else SocialTextPrimary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        live.elapsedSeconds?.let { TabularLabel(elapsedText(it), TIME_TEXT, ink) }
        Text(if (live.paused) "Paused" else "Recording", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun LiveControls(closeHost: () -> Unit, live: LiveView, refresh: () -> Unit, size: Dp) {
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
            closeHost()
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
