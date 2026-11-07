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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.ContextItem
import org.polyfrost.polyplus.client.gui.ContextMenuList
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.platformLabel
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.SparkCompat
import org.polyfrost.polyplus.compat.SparkProfile
import org.polyfrost.polyplus.compat.SparkStats
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PERF_TICK_MILLIS = 500L
private const val PERF_PULSE_NANOS = 1_600_000_000.0
private const val TABULAR = "tnum"
private val CHIP_TEXT = 12.sp
private val BAR_TEXT = 14.sp
private val BAR_DETAIL = 11.sp
private val CARD_TEXT = 13.sp
private val CARD_DETAIL = 12.sp
private val GAUGE_SIZE = 16.dp
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

private fun frameText(view: PerfView): String = view.frameMs?.let { "${millis(it)} ms" } ?: "-- ms"



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

private class ChipSlots(val barFps: Dp, val barFrame: Dp, val barStatus: Dp, val cardFps: Dp, val cardFrame: Dp)

private fun chipStyle(family: FontFamily, weight: FontWeight, color: Color = Color.Unspecified, size: TextUnit = CHIP_TEXT, align: TextAlign = TextAlign.Start) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = family, textAlign = align, fontFeatureSettings = TABULAR)

@Composable
private fun rememberChipSlots(): ChipSlots {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val family = OneConfigTheme.current.fontFamily
    return remember(measurer, density, family) {
        fun widest(template: String, weight: FontWeight, size: TextUnit): Dp {
            val style = chipStyle(family, weight, size = size)
            val px = ('0'..'9').maxOf { digit -> measurer.measure(template.replace('#', digit), style).size.width }
            return with(density) { px.toDp() }
        }
        ChipSlots(
            widest("### FPS", FontWeight.Medium, BAR_TEXT),
            widest("##.# ms", FontWeight.Normal, BAR_DETAIL),
            maxOf(widest("Profiling", FontWeight.Medium, BAR_TEXT), widest("Uploading", FontWeight.Medium, BAR_TEXT)),
            widest("### FPS", FontWeight.Medium, CARD_TEXT),
            widest("##.# ms", FontWeight.Normal, CARD_DETAIL),
        )
    }
}

@Composable
private fun Modifier.growOnlyWidth(): Modifier {
    val widest = remember { intArrayOf(0) }
    return layout { measurable, constraints ->
        val floor = widest[0].coerceAtMost(constraints.maxWidth).coerceAtLeast(constraints.minWidth)
        val placeable = measurable.measure(constraints.copy(minWidth = floor))
        widest[0] = max(widest[0], placeable.width)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

internal fun performanceShown(): Boolean = SparkCompat.installed

@Composable
internal fun PerformanceBarTool(screen: OneClientPauseScreen, glass: Boolean = true) {
    val slots = rememberChipSlots()
    PerfTool(screen) { view, press, menu ->
        BarTile(Modifier.growOnlyWidth(), active = menu, onClick = press, glass = glass) {
            Row(
                Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (val profile = view.profile) {
                    is SparkProfile.Running -> {
                        ProfilingDot()
                        val seconds = ((System.currentTimeMillis() - profile.startedAt) / 1000L).coerceIn(0L, profile.seconds.toLong())
                        BarLines("Profiling", clock(seconds), slots.barStatus, slots.barFrame)
                    }
                    SparkProfile.Uploading -> {
                        ProfilingDot()
                        BarLines("Uploading", "to spark", slots.barStatus, slots.barFrame)
                    }
                    else -> BarLines(fpsText(view), frameText(view), slots.barFps, slots.barFrame)
                }
            }
        }
    }
}

@Composable
internal fun PerformanceCardTool(screen: OneClientPauseScreen) {
    val slots = rememberChipSlots()
    PerfTool(screen, side = true) { view, press, menu ->
        CardRow(active = menu, onClick = press) { lit ->
            when (val profile = view.profile) {
                is SparkProfile.Running -> {
                    Box(Modifier.size(GAUGE_SIZE), contentAlignment = Alignment.Center) { ProfilingDot() }
                    ChipText("Profiling", SocialTextPrimary, FontWeight.Medium, size = CARD_TEXT)
                    Spacer(Modifier.weight(1f))
                    val seconds = ((System.currentTimeMillis() - profile.startedAt) / 1000L).coerceIn(0L, profile.seconds.toLong())
                    ChipText(clock(seconds), MutedText, FontWeight.Normal, size = CARD_DETAIL)
                }
                SparkProfile.Uploading -> {
                    Box(Modifier.size(GAUGE_SIZE), contentAlignment = Alignment.Center) { ProfilingDot() }
                    ChipText("Uploading profile", SocialTextPrimary, FontWeight.Medium, size = CARD_TEXT)
                }
                else -> {
                    Gauge(view.fps, if (lit) SocialTextPrimary else MutedText)
                    ChipText(fpsText(view), SocialTextPrimary, FontWeight.Medium, Modifier.widthIn(min = slots.cardFps), size = CARD_TEXT)
                    Spacer(Modifier.weight(1f))
                    ChipText(frameText(view), MutedText, FontWeight.Normal, Modifier.widthIn(min = slots.cardFrame), TextAlign.End, CARD_DETAIL)
                }
            }
        }
    }
}

@Composable
private fun PerfTool(screen: OneClientPauseScreen, side: Boolean = false, trigger: @Composable (view: PerfView, press: () -> Unit, menu: Boolean) -> Unit) {
    var view by remember { mutableStateOf(readPerf()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PERF_TICK_MILLIS)
            readPerf()?.let { view = it }
        }
    }
    val current = view ?: return
    val refresh: () -> Unit = { readPerf()?.let { view = it } }
    val (interaction, hovered) = rememberSocialHover()
    var menu by remember { mutableStateOf(false) }
    val anchor = rememberToolAnchor(side)
    Box(Modifier.hoverable(interaction)) {
        trigger(current, { menu = !menu }, menu)
        if (hovered && !menu) {
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
                ContextMenuList(menuItems(screen, current, refresh), close)
            }
        }
    }
}

@Composable
private fun BarLines(top: String, bottom: String, topWidth: Dp, bottomWidth: Dp) {
    val width = maxOf(topWidth, bottomWidth)
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        ChipText(top, SocialTextPrimary, FontWeight.Medium, Modifier.widthIn(min = width), size = BAR_TEXT)
        ChipText(bottom, MutedText, FontWeight.Normal, Modifier.widthIn(min = width), size = BAR_DETAIL)
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
            Label("Performance", 13.sp, Modifier.weight(1f), weight = FontWeight.Medium)
            Label("spark", 11.sp, color = MutedText)
        }
        PanelSection("Frame") {
            PanelStat("FPS", view.fps.toString())
            PanelStat("Frame time", view.frameMs?.let { "${millis(it)} ms" } ?: "--")
        }
        if (view.singleplayer) {
            PanelSection("Server") {
                val server = view.stats?.server
                if (view.worldPaused) {
                    Label("Paused while this menu is open", 12.sp, color = MutedText)
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
        Hairline()
        Label(if (view.profiling) "spark is profiling. Click for options." else "Click for spark tools", 11.sp, color = MutedText)
    }
}

@Composable
private fun PanelSection(title: String, rows: @Composable ColumnScope.() -> Unit) {
    Hairline()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(title, 11.sp, color = MutedText, weight = FontWeight.Medium)
        rows()
    }
}

@Composable
private fun PanelStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 12.sp, Modifier.weight(1f), color = MutedText)
        ChipText(value, SocialTextPrimary, FontWeight.Medium, align = TextAlign.End)
    }
}

private fun profileItem(screen: OneClientPauseScreen, seconds: Int, label: String, view: PerfView, refresh: () -> Unit) =
    ContextItem(label, asset("play-circle"), enabled = view.available) {
        if (SparkCompat.startProfile(seconds)) {
            if (ClientPlatform.currentScreen() === screen) ClientPlatform.setScreen(null)
        } else {
            SocialErrors.emit("spark couldn't start the profiler.")
        }
        refresh()
    }

private fun menuItems(screen: OneClientPauseScreen, view: PerfView, refresh: () -> Unit): List<ContextItem> {
    val items = mutableListOf<ContextItem>()
    if (view.profiling) {
        items += ContextItem("Stop and upload now", asset("check-circle"), enabled = view.profile != SparkProfile.Uploading) {
            if (!SparkCompat.stopProfile()) SocialErrors.emit("spark couldn't stop the profiler.")
            refresh()
        }
    } else {
        items += profileItem(screen, SparkCompat.SHORT_PROFILE_SECONDS, "Profile 30 seconds", view, refresh)
        items += profileItem(screen, SparkCompat.LONG_PROFILE_SECONDS, "Profile 2 minutes", view, refresh)
    }
    val health = SparkCompat.healthRunning
    items += ContextItem(if (health) "Making health report" else "Health report", asset("globe-01"), enabled = view.available && !health) {
        if (SparkCompat.healthReport()) {
            SocialNotices.emit("Making a health report")
        } else {
            SocialErrors.emit("spark couldn't make a health report.")
        }
    }
    items += ContextItem("Copy stats", asset("copy-01")) {
        SparkCompat.copy(snapshot(view))
        SocialNotices.emit("Stats copied")
    }
    SparkCompat.lastProfileUrl?.let { url ->
        items += ContextItem("Copy last profile link", asset("copy-01")) {
            SparkCompat.copy(url)
            SocialNotices.emit("Profile link copied")
        }
        items += ContextItem("Open last profile", asset("link-external-01")) { ClientPlatform.openUri(url) }
    }
    SparkCompat.lastHealthUrl?.let { url ->
        items += ContextItem("Open last health report", asset("link-external-01")) { ClientPlatform.openUri(url) }
    }
    return items
}

@Composable
private fun ProfilingDot() {
    val frame by rememberFrame()
    Box(
        Modifier
            .size(DOT_SIZE)
            .graphicsLayer { alpha = (0.7f + 0.3f * cos(frame / PERF_PULSE_NANOS * 2.0 * Math.PI)).toFloat() }
            .background(OneConfigTheme.accent, OneConfigTheme.current.circleShape),
    )
}
