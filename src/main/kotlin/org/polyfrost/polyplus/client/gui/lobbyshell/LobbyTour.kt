@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.height
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.a11yRegion
import org.polyfrost.polyplus.client.gui.A11yRegion
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import kotlin.math.roundToInt

internal enum class TourAnchor { Play, Wardrobe, Social, Tools }

private class TourStop(val anchor: TourAnchor, val title: String, val text: String)

private const val SCRIM_ALPHA = 0.4f
private const val APPEAR_MILLIS = 450
private const val START_DELAY_MILLIS = 1300
private val HolePad = 8.dp
private val CardGap = 16.dp
private val CardWidth = 340.dp

internal class LobbyTour(online: Boolean) {
    private val stops = buildList {
        add(TourStop(TourAnchor.Play, "Play from here", "Pick Singleplayer or Multiplayer, choose a world or server, then press Play. All opens the full list."))
        if (!LobbyPage.Wardrobe.hidden) {
            val text = if (online) {
                "Try on cosmetics and outfits. Your character in the lobby wears them."
            } else {
                "See your character and switch Minecraft capes. Cosmetics need online services."
            }
            add(TourStop(TourAnchor.Wardrobe, "Wardrobe", text))
        }
        if (online && !LobbyPage.Social.hidden) {
            add(TourStop(TourAnchor.Social, "Social", "Friends and messages live here. Press Enter to open it from the lobby."))
        }
        add(TourStop(TourAnchor.Tools, "Mods and settings", "Mod settings, Minecraft settings and the theme are up here. You can change anything you picked during setup."))
    }

    var index by mutableIntStateOf(0)
        private set

    val last: Boolean get() = index == stops.size - 1

    fun next(): Boolean {
        if (last) return false
        index++
        return true
    }

    fun kicker(): String = "Tour · ${index + 1} of ${stops.size}"

    fun anchor(): TourAnchor = stops[index].anchor

    fun title(): String = stops[index].title

    fun text(): String = stops[index].text
}

internal fun Modifier.tourAnchor(anchors: MutableMap<TourAnchor, Rect>, anchor: TourAnchor): Modifier =
    onGloballyPositioned { anchors[anchor] = it.boundsInRoot() }

@Composable
internal fun TourLayer(tour: LobbyTour, anchors: Map<TourAnchor, Rect>, onEnd: () -> Unit) {
    EscapeCloses(onEnd)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(APPEAR_MILLIS, START_DELAY_MILLIS)) }
    val density = LocalDensity.current
    val pad = with(density) { HolePad.toPx() }
    val gap = with(density) { CardGap.toPx() }
    val margin = with(density) { LobbyLayout.Margin.toPx() / 2f }
    val root = LocalRootSize.current
    val anchor = tour.anchor()
    val hole = anchors[anchor]?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) } ?: return
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = appear.value }) {
        LobbyDarkChrome { Spotlight(hole) }
        key(tour.index) {
            val shown = remember { Animatable(0f) }
            LaunchedEffect(Unit) { shown.animateTo(1f, tween(APPEAR_MILLIS)) }
            Announce("${tour.title()}. ${tour.text()}")
            Column(
                Modifier
                    .placed { size -> cardOffset(anchor, hole, size, root, gap, margin) }
                    .a11yRegion(A11yRegion.Tour)
                    .width(CardWidth)
                    .graphicsLayer { alpha = shown.value }
                    .popup()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label(tour.kicker(), 12.sp, color = MutedText, weight = FontWeight.Medium)
                    PanelTitle(tour.title())
                }
                SocialText(tour.text(), 12.sp, color = MutedText)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton("Skip tour", Modifier.height(38.dp), onEnd)
                    Spacer(Modifier.weight(1f))
                    TextButton(if (tour.last) "Done" else "Next", Modifier.height(38.dp), ButtonStyle.Primary) { if (!tour.next()) onEnd() }
                }
            }
        }
    }
}

private fun cardOffset(anchor: TourAnchor, hole: Rect, size: IntSize, root: Size, gap: Float, margin: Float): IntOffset {
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    var x: Float
    var y: Float
    when (anchor) {
        TourAnchor.Play -> {
            x = hole.left - gap - width
            y = hole.bottom - height
            if (x < margin) {
                x = hole.right - width
                y = hole.top - gap - height
            }
        }
        TourAnchor.Tools -> {
            x = hole.right - width
            y = hole.bottom + gap
        }
        TourAnchor.Wardrobe, TourAnchor.Social -> {
            x = hole.center.x - width / 2f
            y = hole.bottom + gap
        }
    }
    x = x.coerceIn(margin, maxOf(margin, root.width - width - margin))
    y = y.coerceIn(margin, maxOf(margin, root.height - height - margin))
    return IntOffset(x.roundToInt(), y.roundToInt())
}

private fun Modifier.placed(at: (IntSize) -> IntOffset): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(at(IntSize(placeable.width, placeable.height)))
    }
}

@Composable
private fun Spotlight(hole: Rect) {
    val root = LocalRootSize.current
    val scrim = OneConfigTheme.current.pageBackground.copy(alpha = SCRIM_ALPHA)
    val shape = ppShape(16.dp)
    Box(
        Modifier.pixels(Rect(0f, 0f, root.width, root.height)).pointerInput(Unit) { detectTapGestures { } }.drawBehind {
            val cut = Path().apply {
                addOutline(shape.createOutline(hole.size, layoutDirection, this@drawBehind))
                translate(hole.topLeft)
            }
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addPath(cut)
            }
            drawPath(mask, scrim)
        },
    )
    Box(Modifier.pixels(hole).border(1.dp, OneConfigTheme.accent, shape).pointerInput(Unit) { detectTapGestures { } })
}

@Composable
private fun Modifier.pixels(rect: Rect): Modifier {
    val density = LocalDensity.current
    return this
        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
}
