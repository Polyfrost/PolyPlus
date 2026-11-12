@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPopup
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.pausescreen.TabularLabel
import org.polyfrost.polyplus.client.gui.pausescreen.rememberRiseAnchor
import org.polyfrost.polyplus.client.gui.platformLabel
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.SparkCompat
import org.polyfrost.polyplus.compat.SparkProfile
import kotlin.math.roundToInt

private val DOCK_RADIUS = 20.dp
private val DOCK_INSET = 6.dp
private val DOCK_GAP = 3.dp
private val TILE_RADIUS = 14.dp
private val SCENE_TILE = LobbyLayout.BarHeight - DOCK_INSET * 2
private val SPARK_TILE = 84.dp
private const val SPARK_TICK_MILLIS = 500L

@Composable
internal fun LobbyDock(lobby: LobbyShell) {
    if (lobby.page != LobbyPage.Home) return
    val spark = SparkCompat.installed
    Row(Modifier.height(LobbyLayout.BarHeight).panel(DOCK_RADIUS), verticalAlignment = Alignment.CenterVertically) {
        DockTile("Change scene", SCENE_TILE, first = true, last = !spark, active = lobby.sceneOpen, onClick = { lobby.sceneOpen = !lobby.sceneOpen }) {
            LobbyIcon("image-01")
        }
        if (spark) SparkTile(first = false, last = true)
    }
}

@Composable
private fun DockTile(
    label: String,
    width: Dp,
    first: Boolean,
    last: Boolean,
    active: Boolean = false,
    tooltip: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val shape = ppShape(TILE_RADIUS)
    Box(
        Modifier
            .fillMaxHeight()
            .hoverable(interaction)
            .clickableWithSound(label, shape = shape, onClick = onClick)
            .padding(start = if (first) DOCK_INSET else DOCK_GAP, end = if (last) DOCK_INSET else DOCK_GAP, top = DOCK_INSET, bottom = DOCK_INSET)
            .width(width),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.matchParentSize().rowFrame(false, hovered || active, shape))
        content()
        if (tooltip && hovered && !active) TooltipPopup(label, TooltipPlacement.AboveCenter)
    }
}

private class SparkView(val fps: Int, val profile: SparkProfile, val available: Boolean, val busy: Boolean) {
    val profiling: Boolean get() = profile is SparkProfile.Running || profile == SparkProfile.Uploading || busy
}

private fun readSpark(): SparkView = SparkView(
    runCatching { Platform.compatibility().fps() }.getOrDefault(0).coerceAtLeast(0),
    SparkCompat.profile,
    runCatching { SparkCompat.available }.getOrDefault(false),
    runCatching { SparkCompat.busy() }.getOrDefault(false),
)

@Composable
private fun SparkTile(first: Boolean, last: Boolean) {
    var view by remember { mutableStateOf(readSpark()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SPARK_TICK_MILLIS)
            view = readSpark()
        }
    }
    var menu by remember { mutableStateOf(false) }
    val anchor = rememberRiseAnchor()
    Box {
        DockTile("spark tools", SPARK_TILE, first, last, active = menu, onClick = { menu = !menu }) {
            when (val profile = view.profile) {
                is SparkProfile.Running -> {
                    val seconds = ((System.currentTimeMillis() - profile.startedAt) / 1000L).coerceIn(0L, profile.seconds.toLong())
                    SparkLines("Profiling", clock(seconds), true)
                }
                SparkProfile.Uploading -> SparkLines("Uploading", "to spark", true)
                else -> SparkLines("${view.fps} FPS", if (view.fps > 0) "${millis(1000.0 / view.fps)} ms" else "-- ms", false)
            }
        }
        if (menu) {
            val close = { menu = false }
            EscapeCloses(close)
            Popup(popupPositionProvider = anchor, onDismissRequest = close, properties = PopupProperties(focusable = true)) {
                LobbyUserTheme { MenuList(sparkItems(view) { view = readSpark() }, close) }
            }
        }
    }
}

@Composable
private fun SparkLines(top: String, bottom: String, live: Boolean) {
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        TabularLabel(top, 13.sp, if (live) OneConfigTheme.accent else SocialTextPrimary, FontWeight.Medium)
        TabularLabel(bottom, 11.sp, MutedText, FontWeight.Normal)
    }
}

@Composable
private fun icon(name: String): String = themedIcon("$SOCIAL_ASSETS$name.svg")

private fun clock(seconds: Long): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun millis(value: Double): String = if (value < 100.0) "%.1f".format(value) else value.roundToInt().toString()

private fun percent(value: Double): String = "${(value * 100).roundToInt()}%"

@Composable
private fun profileItem(seconds: Int, label: String, view: SparkView, refresh: () -> Unit) =
    MenuAction(label, icon("play-circle"), enabled = view.available) {
        if (!SparkCompat.startProfile(seconds)) SocialErrors.emit("spark couldn't start the profiler.")
        refresh()
    }

@Composable
private fun sparkItems(view: SparkView, refresh: () -> Unit): List<MenuAction> {
    val items = mutableListOf<MenuAction>()
    if (view.profiling) {
        items += MenuAction("Stop and upload now", icon("check-circle"), enabled = view.profile != SparkProfile.Uploading) {
            if (!SparkCompat.stopProfile()) SocialErrors.emit("spark couldn't stop the profiler.")
            refresh()
        }
    } else {
        items += profileItem(SparkCompat.SHORT_PROFILE_SECONDS, "Profile 30 seconds", view, refresh)
        items += profileItem(SparkCompat.LONG_PROFILE_SECONDS, "Profile 2 minutes", view, refresh)
    }
    val health = SparkCompat.healthRunning
    items += MenuAction(if (health) "Making health report" else "Health report", icon("globe-01"), enabled = view.available && !health) {
        if (SparkCompat.healthReport()) {
            SocialNotices.emit("Making a health report")
        } else {
            SocialErrors.emit("spark couldn't make a health report.")
        }
    }
    items += MenuAction("Copy stats", icon("copy-01")) {
        SparkCompat.copy(snapshot(view))
        SocialNotices.emit("Stats copied")
    }
    SparkCompat.lastProfileUrl?.let { url ->
        items += MenuAction("Copy last profile link", icon("copy-01")) {
            SparkCompat.copy(url)
            SocialNotices.emit("Profile link copied")
        }
        items += MenuAction("Open last profile", icon("link-external-01")) { ClientPlatform.openUri(url) }
    }
    SparkCompat.lastHealthUrl?.let { url ->
        items += MenuAction("Open last health report", icon("link-external-01")) { ClientPlatform.openUri(url) }
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
