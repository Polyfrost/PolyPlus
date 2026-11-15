@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.pausescreen.MediaPanel
import org.polyfrost.polyplus.client.gui.pausescreen.PauseEvergreenMedia
import org.polyfrost.polyplus.client.gui.pausescreen.PauseMediaTrack
import org.polyfrost.polyplus.client.gui.pausescreen.learnsMenu
import org.polyfrost.polyplus.client.gui.pausescreen.pauseGlass
import org.polyfrost.polyplus.client.gui.pausescreen.rememberMediaTrack
import org.polyfrost.polyplus.compat.Mods
import kotlin.math.roundToInt

enum class LobbyMediaSize(private val label: String) {
    COMPACT("Compact"),
    LARGE("Large");

    override fun toString(): String = label
}

internal const val LOBBY_MEDIA_HINT = "Right-click for options"

internal val LobbyMediaCardWidth = 300.dp

private val CARD_RADIUS = 16.dp

private object LobbyMediaPrefs {
    var revision by mutableIntStateOf(0)

    fun change(apply: () -> Unit) {
        apply()
        PolyPlusLobbyConfig.save()
        revision++
    }
}

internal class LobbyMediaState {
    var track: PauseMediaTrack? = null
    var card = false
    var tile = false
}

@Composable
internal fun rememberLobbyMedia(): PauseMediaTrack? {
    val supported = remember { Mods.evergreenHud && PauseEvergreenMedia.supported() }
    val shown = key(LobbyMediaPrefs.revision) { supported && PolyPlusLobbyConfig.lobbyMediaWidget }
    return if (shown) rememberMediaTrack() else null
}

@Composable
internal fun rememberLobbyMediaLarge(): Boolean = key(LobbyMediaPrefs.revision) { PolyPlusLobbyConfig.lobbyMediaWidgetSize == LobbyMediaSize.LARGE }

internal fun mediaSwapSpec(): AnimationSpec<Float> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

private fun mediaMenuItems(): List<MenuAction> {
    val large = PolyPlusLobbyConfig.lobbyMediaWidgetSize == LobbyMediaSize.LARGE
    val other = if (large) LobbyMediaSize.COMPACT else LobbyMediaSize.LARGE
    return listOf(
        MenuAction(if (large) "Compact tile" else "Large card", {
            LobbyMediaPrefs.change { PolyPlusLobbyConfig.lobbyMediaWidgetSize = other }
        }),
        MenuAction("Hide media widget", {
            LobbyMediaPrefs.change { PolyPlusLobbyConfig.lobbyMediaWidget = false }
        }),
    )
}

@Composable
internal fun Modifier.mediaMenu(): Modifier = contextMenu(entries = ::mediaMenuItems).learnsMenu()

@Composable
internal fun LobbyMediaCard(track: PauseMediaTrack, progress: Float, gap: Dp, modifier: Modifier = Modifier) {
    Column(modifier.growHeight(progress).graphicsLayer { alpha = progress }) {
        Box(Modifier.mediaMenu().clip(OneConfigTheme.shape(CARD_RADIUS)).pauseGlass(CARD_RADIUS)) {
            MediaPanel(track, Motion.reduced, LOBBY_MEDIA_HINT)
        }
        Spacer(Modifier.height(gap))
    }
}

internal fun Modifier.growHeight(progress: Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val shown = (placeable.height * progress).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(placeable.width, shown) { placeable.place(0, shown - placeable.height) }
}

internal fun Modifier.growWidth(progress: Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val shown = (placeable.width * progress).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(shown, placeable.height) { placeable.place(0, 0) }
}
