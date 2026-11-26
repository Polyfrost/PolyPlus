@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.DropdownMenu
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyUserTheme
import org.polyfrost.polyplus.client.gui.pausescreen.CardRow
import org.polyfrost.polyplus.client.gui.pausescreen.PauseGlyph
import org.polyfrost.polyplus.client.gui.pausescreen.PerformanceWidget
import org.polyfrost.polyplus.client.gui.pausescreen.RecordingWidget
import org.polyfrost.polyplus.client.gui.pausescreen.SceneWidget
import org.polyfrost.polyplus.client.gui.pausescreen.TabularLabel
import org.polyfrost.polyplus.client.gui.pausescreen.asset
import org.polyfrost.polyplus.client.gui.pausescreen.performanceShown
import org.polyfrost.polyplus.client.gui.pausescreen.recordingShown
import org.polyfrost.polyplus.client.gui.pausescreen.sceneShown
import org.polyfrost.polyplus.client.gui.platformLabel
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.SparkCompat
import org.polyfrost.polyplus.compat.SparkLink
import org.polyfrost.polyplus.compat.SparkProfile
import kotlin.math.roundToInt

private const val SPARK_TICK_MILLIS = 500L
private val ROW_INSET = 6.dp
private val ROW_GLYPH = 16.dp

@Composable
private fun LegacyRow(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = ROW_INSET), contentAlignment = Alignment.CenterStart) {
        content()
    }
}

internal object PerformanceShellWidget : ShellWidget {
    override val id = WidgetMigration.PERFORMANCE
    override val title = "Performance"
    override val icon = asset("speedometer")
    override val size = WidgetSize.ROW
    override val surfaces = setOf(WidgetSurface.LOBBY, WidgetSurface.PAUSE)
    override val bleed = true

    override fun isAvailable(surface: WidgetSurface): Boolean = performanceShown()

    @Composable
    override fun Content(scope: WidgetScope) {
        if (scope.surface == WidgetSurface.PAUSE) {
            PerformanceWidget(scope)
        } else {
            LegacyRow { LobbyPerformance() }
        }
    }
}

internal object RecordingShellWidget : ShellWidget {
    override val id = WidgetMigration.RECORDING
    override val title = "Recording"
    override val icon = asset("video-recorder")
    override val size = WidgetSize.BLOCK
    override val surfaces = setOf(WidgetSurface.PAUSE)
    override val bleed = true

    override fun isAvailable(surface: WidgetSurface): Boolean = surface == WidgetSurface.PAUSE && recordingShown()

    @Composable
    override fun Content(scope: WidgetScope) {
        RecordingWidget(scope)
    }
}

internal object SceneShellWidget : ShellWidget {
    override val id = WidgetMigration.SCENE
    override val title = "Scene check"
    override val icon = asset("image-01")
    override val size = WidgetSize.BLOCK
    override val surfaces = setOf(WidgetSurface.PAUSE)
    override val bleed = true

    override fun isAvailable(surface: WidgetSurface): Boolean = surface == WidgetSurface.PAUSE && sceneShown()

    @Composable
    override fun Content(scope: WidgetScope) {
        SceneWidget()
    }
}

private class SparkView(val fps: Int, val profile: SparkProfile, val available: Boolean, val busy: Boolean, val ready: SparkLink?) {
    val profiling: Boolean get() = profile is SparkProfile.Running || profile == SparkProfile.Uploading || busy
}

private fun readSpark(): SparkView = SparkView(
    runCatching { Platform.compatibility().fps() }.getOrDefault(0).coerceAtLeast(0),
    SparkCompat.profile,
    runCatching { SparkCompat.available }.getOrDefault(false),
    runCatching { SparkCompat.busy() }.getOrDefault(false),
    SparkCompat.ready,
)

private fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun millis(value: Double): String = if (value < 100.0) "%.1f".format(value) else value.roundToInt().toString()

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

@Composable
private fun LobbyPerformance() {
    var view by remember { mutableStateOf(readSpark()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SPARK_TICK_MILLIS)
            view = readSpark()
        }
    }
    var menu by remember { mutableStateOf(false) }
    val ready = view.ready.takeIf { !view.profiling }
    val press = {
        if (ready != null) {
            SparkCompat.openReady()
            view = readSpark()
        } else {
            menu = !menu
        }
    }
    Box {
        CardRow(label = if (ready != null) "Open ${ready.label.lowercase()}" else "spark tools", active = menu, onClick = press) { lit ->
            val muted = OneConfigTheme.current.mutedText
            Box(Modifier.size(ROW_GLYPH), contentAlignment = Alignment.Center) {
                PauseGlyph(asset("speedometer"), if (lit) SocialTextPrimary else muted, ROW_GLYPH)
            }
            val profile = view.profile
            when {
                ready != null -> {
                    TabularLabel("${ready.label} ready", 13.sp, OneConfigTheme.accent)
                    Spacer(Modifier.weight(1f))
                    TabularLabel("Click to open", 12.sp, muted, FontWeight.Normal)
                }
                profile is SparkProfile.Running -> {
                    TabularLabel("Profiling", 13.sp, OneConfigTheme.accent)
                    Spacer(Modifier.weight(1f))
                    val seconds = ((System.currentTimeMillis() - profile.startedAt) / 1000L).coerceIn(0L, profile.seconds.toLong())
                    TabularLabel(clock(seconds), 12.sp, muted, FontWeight.Normal)
                }
                profile == SparkProfile.Uploading -> TabularLabel("Uploading profile", 13.sp, OneConfigTheme.accent)
                else -> {
                    TabularLabel("${view.fps} FPS", 13.sp)
                    Spacer(Modifier.weight(1f))
                    TabularLabel(if (view.fps > 0) "${millis(1000.0 / view.fps)} ms" else "-- ms", 12.sp, muted, FontWeight.Normal)
                }
            }
        }
        if (menu) {
            val items = sparkItems(view) { view = readSpark() }
            LobbyUserTheme { DropdownMenu(true, { menu = false }, items, placement = Placement.AboveStart, gap = 10.dp) }
        }
    }
}

private fun profileItem(seconds: Int, label: String, view: SparkView, refresh: () -> Unit) =
    MenuAction(label, {
        if (!SparkCompat.startProfile(seconds)) SocialErrors.emit("spark couldn't start the profiler.")
        refresh()
    }, icon = asset("play-circle"), enabled = view.available)

private fun sparkItems(view: SparkView, refresh: () -> Unit): List<MenuAction> {
    val items = mutableListOf<MenuAction>()
    if (view.profiling) {
        items += MenuAction("Stop and upload now", {
            if (!SparkCompat.stopProfile()) SocialErrors.emit("spark couldn't stop the profiler.")
            refresh()
        }, icon = asset("check-circle"), enabled = view.profile != SparkProfile.Uploading)
    } else {
        items += profileItem(SparkCompat.SHORT_PROFILE_SECONDS, "Profile 30 seconds", view, refresh)
        items += profileItem(SparkCompat.LONG_PROFILE_SECONDS, "Profile 2 minutes", view, refresh)
    }
    val health = SparkCompat.healthRunning
    items += MenuAction(if (health) "Making health report" else "Health report", {
        if (SparkCompat.healthReport()) {
            SocialNotices.emit("Making a health report")
        } else {
            SocialErrors.emit("spark couldn't make a health report.")
        }
    }, icon = asset("globe-01"), enabled = view.available && !health)
    items += MenuAction("Copy stats", {
        SparkCompat.copy(snapshot(view))
        SocialNotices.emit("Stats copied")
    }, icon = asset("copy-01"))
    SparkCompat.lastProfileUrl?.let { url ->
        items += MenuAction("Copy last profile link", {
            SparkCompat.copy(url)
            SocialNotices.emit("Profile link copied")
        }, icon = asset("copy-01"))
        items += MenuAction("Open last profile", { ClientPlatform.openUri(url) }, icon = asset("link-external-01"))
    }
    SparkCompat.lastHealthUrl?.let { url ->
        items += MenuAction("Open last health report", { ClientPlatform.openUri(url) }, icon = asset("link-external-01"))
    }
    return items
}

private fun snapshot(view: SparkView): String {
    val lines = mutableListOf<String>()
    lines += "FPS: ${view.fps}" + (if (view.fps > 0) " (${millis(1000.0 / view.fps)} ms per frame)" else "")
    val stats = runCatching { SparkCompat.stats(false) }.getOrNull()
    val cpu = listOfNotNull(stats?.cpuProcess?.let { "game ${percent(it)}" }, stats?.cpuSystem?.let { "system ${percent(it)}" })
    if (cpu.isNotEmpty()) lines += "CPU: ${cpu.joinToString(", ")}"
    lines += "Minecraft: ${platformLabel()}"
    runCatching { FabricLoader.getInstance().allMods.size }.getOrNull()?.let { lines += "Mods: $it" }
    lines += "Screen: main menu"
    return lines.joinToString("\n")
}
