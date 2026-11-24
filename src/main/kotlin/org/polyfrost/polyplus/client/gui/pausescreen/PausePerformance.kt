@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import org.polyfrost.compose.host.controller.Controllers
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.widgets.WidgetScope
import org.polyfrost.polyplus.client.gui.widgets.WidgetSize
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.platformLabel
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.SparkCompat
import org.polyfrost.polyplus.compat.SparkProfile
import org.polyfrost.polyplus.compat.SparkStats
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private const val PERF_TICK_MILLIS = 500L
private const val PERF_PULSE_NANOS = 1_600_000_000.0
private const val TABULAR = "tnum"
private val CHIP_TEXT = 12.sp
private val MAIN_TEXT = 14.sp
private val DETAIL_TEXT = 11.sp
private val GAUGE_SIZE = 22.dp
private val GAUGE_STROKE = 2.dp
private const val GAUGE_FULL_FPS = 240f
private const val GAUGE_START = 135f
private const val GAUGE_SWEEP = 270f
private val DOT_SIZE = 8.dp
private val PANEL_WIDTH = 232.dp

private class PerfView(
    val fps: Int,
    val frameMs: Double?,
    val stats: SparkStats?,
    val singleplayer: Boolean,
    val worldPaused: Boolean,
    val profile: SparkProfile,
    val busy: Boolean,
    val available: Boolean,
) {
    val showServer: Boolean get() = singleplayer && !worldPaused
    val profiling: Boolean get() = profile is SparkProfile.Running || profile == SparkProfile.Uploading || busy
}

private fun readPerf(): PerfView? = runCatching {
    if (!SparkCompat.installed) return null
    val singleplayer = PauseWorldAccess.singleplayer
    val worldPaused = singleplayer && !SocialHosting.lanOpen
    val fps = runCatching { Platform.compatibility().fps() }.getOrDefault(0).coerceAtLeast(0)
    PerfView(
        fps,
        if (fps > 0) 1000.0 / fps else null,
        runCatching { SparkCompat.stats(singleplayer && !worldPaused) }.getOrNull(),
        singleplayer,
        worldPaused,
        SparkCompat.profile,
        runCatching { SparkCompat.busy() }.getOrDefault(false),
        runCatching { SparkCompat.available }.getOrDefault(false),
    )
}.getOrNull()

private fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun one(value: Double): String = "%.1f".format(value)

private fun millis(value: Double): String = if (value < 100.0) one(value) else value.roundToInt().toString()

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

private fun fpsText(view: PerfView): String = "${view.fps} FPS"

private fun frameText(view: PerfView): String = view.frameMs?.let { "${millis(it)} ms per frame" } ?: "-- ms per frame"

private fun modCount(): Int? = runCatching { FabricLoader.getInstance().allMods.size }.getOrNull()

private fun snapshot(view: PerfView): String {
    val lines = mutableListOf<String>()
    lines += "FPS: ${view.fps}" + (view.frameMs?.let { " (${millis(it)} ms per frame)" } ?: "")
    val server = view.stats?.server
    if (view.showServer && server != null) {
        val tps = listOfNotNull(server.tps10s?.let { "${one(it)} (10s)" }, server.tps1m?.let { "${one(it)} (1m)" })
        if (tps.isNotEmpty()) lines += "TPS: ${tps.joinToString(", ")}"
        val mspt = listOfNotNull(server.msptMean?.let { "${millis(it)} avg" }, server.msptMax?.let { "${millis(it)} max" })
        if (mspt.isNotEmpty()) lines += "MSPT: ${mspt.joinToString(", ")} (10s)"
    }
    val cpu = listOfNotNull(
        view.stats?.cpuProcess?.let { "game ${percent(it)}" },
        view.stats?.cpuSystem?.let { "system ${percent(it)}" },
    )
    if (cpu.isNotEmpty()) lines += "CPU: ${cpu.joinToString(", ")}"
    lines += "Minecraft: ${platformLabel()}"
    modCount()?.let { lines += "Mods: $it" }
    return lines.joinToString("\n")
}

private fun chipStyle(family: FontFamily, weight: FontWeight, color: Color = Color.Unspecified, size: TextUnit = CHIP_TEXT, align: TextAlign = TextAlign.Start) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align, fontFeatureSettings = TABULAR)

internal fun performanceShown(): Boolean = SparkCompat.installed

@Composable
internal fun PerformanceWidget(scope: WidgetScope) {
    PerfTool(scope::closeHost) { view, press, menu ->
        WidgetFace(WidgetSize.ROW.height, label = "Performance and spark tools", active = menu, onClick = press) { lit ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = WIDGET_INSET),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val muted = OneConfigTheme.current.mutedText
                when (val profile = view.profile) {
                    is SparkProfile.Running -> {
                        Box(Modifier.size(GAUGE_SIZE), contentAlignment = Alignment.Center) { ProfilingDot() }
                        StatLines("Profiling", "Click for options", Modifier.weight(1f))
                        val seconds = ((System.currentTimeMillis() - profile.startedAt) / 1000L).coerceIn(0L, profile.seconds.toLong())
                        ChipText(clock(seconds), SocialTextPrimary, FontWeight.Medium, size = MAIN_TEXT)
                    }
                    SparkProfile.Uploading -> {
                        Box(Modifier.size(GAUGE_SIZE), contentAlignment = Alignment.Center) { ProfilingDot() }
                        StatLines("Uploading", "Sending the profile", Modifier.weight(1f))
                    }
                    else -> {
                        Gauge(view.fps, if (lit) SocialTextPrimary else muted)
                        StatLines(fpsText(view), frameText(view), Modifier.weight(1f))
                        sideStat(view)?.let { (value, label) -> StatLines(value, label, align = TextAlign.End) }
                    }
                }
            }
        }
    }
}

private fun sideStat(view: PerfView): Pair<String, String>? {
    val tps = view.stats?.server?.tps10s
    if (view.showServer && tps != null) return one(tps) to "TPS"
    return view.stats?.cpuProcess?.let { percent(it) to "CPU" }
}

@Composable
private fun StatLines(top: String, bottom: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp), horizontalAlignment = if (align == TextAlign.End) Alignment.End else Alignment.Start) {
        ChipText(top, SocialTextPrimary, FontWeight.Medium, align = align, size = MAIN_TEXT)
        ChipText(bottom, OneConfigTheme.current.mutedText, FontWeight.Normal, align = align, size = DETAIL_TEXT)
    }
}

@Composable
private fun PerfTool(closeHost: () -> Unit, trigger: @Composable (view: PerfView, press: () -> Unit, menu: Boolean) -> Unit) {
    var view by remember { mutableStateOf(readPerf()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PERF_TICK_MILLIS)
            readPerf()?.let { view = it }
        }
    }
    val current = view ?: return
    val refresh: () -> Unit = { readPerf()?.let { view = it } }
    val hoverState = rememberHoverState()
    val interaction = hoverState.interactionSource
    val hovered = hoverState.hovered
    var menu by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val anchor = rememberWidgetAnchor()
    Box(Modifier.fillMaxWidth().hoverable(interaction).onFocusChanged { focused = it.hasFocus }) {
        trigger(current, { menu = !menu }, menu)
        if ((hovered || focused && Controllers.isActive) && !menu) {
            Popup(popupPositionProvider = anchor, properties = PopupProperties(focusable = false, clippingEnabled = false)) {
                PerfPanel(current)
            }
        }
        if (menu) {
            val close = { menu = false }
            Popup(
                popupPositionProvider = anchor,
                onDismissRequest = close,
                properties = PopupProperties(focusable = true),
            ) {
                EscapeCloses(close)
                MenuList(menuItems(closeHost, current, refresh), close)
            }
        }
    }
}

@Composable
private fun Gauge(fps: Int, tint: Color) {
    val fraction = (fps / GAUGE_FULL_FPS).coerceIn(0f, 1f)
    Canvas(Modifier.size(GAUGE_SIZE)) {
        val stroke = GAUGE_STROKE.toPx()
        val inset = stroke / 2f + stroke / 2f
        val arc = Size(size.width - inset * 2f, size.height - inset * 2f)
        drawArc(tint, GAUGE_START, GAUGE_SWEEP, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * fraction).toDouble())
        val reach = arc.width / 2f - stroke * 1.5f
        val tip = Offset(center.x + (reach * cos(angle)).toFloat(), center.y + (reach * sin(angle)).toFloat())
        drawLine(tint, center, tip, stroke, StrokeCap.Round)
        drawCircle(tint, stroke, center)
    }
}

@Composable
private fun ChipText(text: String, color: Color, weight: FontWeight, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, size: TextUnit = CHIP_TEXT) {
    BasicText(
        text,
        modifier,
        style = chipStyle(OneConfigTheme.current.fontFamily, weight, color, size, align),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun PerfPanel(view: PerfView) {
    Column(
        Modifier.width(PANEL_WIDTH).popup().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Performance", Modifier.weight(1f), style = OneConfigTheme.current.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text("spark", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        PanelSection("Frame") {
            PanelStat("FPS", view.fps.toString())
            PanelStat("Frame time", view.frameMs?.let { "${millis(it)} ms" } ?: "--")
        }
        if (view.singleplayer) {
            PanelSection("Server") {
                val server = view.stats?.server
                if (view.worldPaused) {
                    Text("Paused while this menu is open", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                } else {
                    PanelStat("TPS, 10s", server?.tps10s?.let(::one) ?: "--")
                    PanelStat("TPS, 1m", server?.tps1m?.let(::one) ?: "--")
                    PanelStat("MSPT average", server?.msptMean?.let { "${millis(it)} ms" } ?: "--")
                    PanelStat("MSPT max", server?.msptMax?.let { "${millis(it)} ms" } ?: "--")
                }
            }
        }
        val cpuProcess = view.stats?.cpuProcess
        val cpuSystem = view.stats?.cpuSystem
        if (cpuProcess != null || cpuSystem != null) {
            PanelSection("CPU") {
                PanelStat("Game", cpuProcess?.let(::percent) ?: "--")
                PanelStat("System", cpuSystem?.let(::percent) ?: "--")
            }
        }
        Divider()
        Text(if (view.profiling) "spark is profiling. Click for options." else "Click for spark tools", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun PanelSection(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Divider()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        rows()
    }
}

@Composable
private fun PanelStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        ChipText(value, SocialTextPrimary, FontWeight.Medium, align = TextAlign.End)
    }
}

private fun profileItem(closeHost: () -> Unit, seconds: Int, label: String, view: PerfView, refresh: () -> Unit) =
    MenuAction(label, asset("play-circle"), enabled = view.available) {
        if (SparkCompat.startProfile(seconds)) {
            closeHost()
        } else {
            SocialErrors.emit("spark couldn't start the profiler.")
        }
        refresh()
    }

private fun menuItems(closeHost: () -> Unit, view: PerfView, refresh: () -> Unit): List<MenuAction> {
    val items = mutableListOf<MenuAction>()
    if (view.profiling) {
        items += MenuAction("Stop and upload now", asset("check-circle"), enabled = view.profile != SparkProfile.Uploading) {
            if (!SparkCompat.stopProfile()) SocialErrors.emit("spark couldn't stop the profiler.")
            refresh()
        }
    } else {
        items += profileItem(closeHost, SparkCompat.SHORT_PROFILE_SECONDS, "Profile 30 seconds", view, refresh)
        items += profileItem(closeHost, SparkCompat.LONG_PROFILE_SECONDS, "Profile 2 minutes", view, refresh)
    }
    val health = SparkCompat.healthRunning
    items += MenuAction(if (health) "Making health report" else "Health report", asset("globe-01"), enabled = view.available && !health) {
        if (SparkCompat.healthReport()) {
            SocialNotices.emit("Making a health report")
        } else {
            SocialErrors.emit("spark couldn't make a health report.")
        }
    }
    items += MenuAction("Copy stats", asset("copy-01")) {
        SparkCompat.copy(snapshot(view))
        SocialNotices.emit("Stats copied")
    }
    SparkCompat.lastProfileUrl?.let { url ->
        items += MenuAction("Copy last profile link", asset("copy-01")) {
            SparkCompat.copy(url)
            SocialNotices.emit("Profile link copied")
        }
        items += MenuAction("Open last profile", asset("link-external-01")) { ClientPlatform.openUri(url) }
    }
    SparkCompat.lastHealthUrl?.let { url ->
        items += MenuAction("Open last health report", asset("link-external-01")) { ClientPlatform.openUri(url) }
    }
    return items
}

@Composable
private fun ProfilingDot() {
    val frame by rememberFrame()
    val reduced = Motion.reduced
    Box(
        Modifier
            .size(DOT_SIZE)
            .graphicsLayer { alpha = if (reduced) 1f else (0.7f + 0.3f * cos(frame / PERF_PULSE_NANOS * 2.0 * Math.PI)).toFloat() }
            .background(OneConfigTheme.accent, OneConfigTheme.current.circleShape),
    )
}
