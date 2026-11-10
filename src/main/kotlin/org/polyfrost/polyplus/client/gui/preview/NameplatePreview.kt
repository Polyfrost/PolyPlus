package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.polyfrost.compose.backend.drawPoly
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction

class NameplatePreviewController {
    private var held by mutableIntStateOf(0)
    private val pulseEnds = LongArray(Reaction.entries.size)

    @Volatile
    private var pulsing = 0L

    private val shots = ArrayList<String>()

    val reactions: Int
        get() {
            val now = System.currentTimeMillis()
            if (now >= pulsing) return held
            var mask = held
            for (reaction in Reaction.entries) {
                if (pulseEnds[reaction.ordinal] > now) mask = mask or reaction.bit
            }
            return mask
        }

    fun isOn(reaction: Reaction): Boolean = held and reaction.bit != 0

    fun toggle(reaction: Reaction) {
        held = held xor reaction.bit
    }

    fun pulse(reaction: Reaction, millis: Long) {
        val end = System.currentTimeMillis() + millis
        pulseEnds[reaction.ordinal] = end
        pulsing = maxOf(pulsing, end)
    }

    fun clear() {
        held = 0
        pulseEnds.fill(0L)
        pulsing = 0L
        shots.clear()
    }

    fun fire(oneShot: String) {
        shots += oneShot
    }

    internal fun drainShots(): List<String> {
        if (shots.isEmpty()) return emptyList()
        val drained = shots.toList()
        shots.clear()
        return drained
    }
}

sealed interface NameplatePreviewScale {
    data object Fit : NameplatePreviewScale

    data class Fixed(val fontPixel: Dp) : NameplatePreviewScale
}

@Composable
fun NameplatePreview(
    cosmeticId: Int?,
    modifier: Modifier = Modifier,
    scale: NameplatePreviewScale = NameplatePreviewScale.Fit,
    controller: NameplatePreviewController? = null,
) {
    val entry = remember { NameplatePreviewOffscreen.register() }
    DisposableEffect(entry) {
        onDispose { NameplatePreviewOffscreen.unregister(entry) }
    }
    val fontPixelPx = with(LocalDensity.current) { (scale as? NameplatePreviewScale.Fixed)?.fontPixel?.toPx() ?: 0f }
    SideEffect {
        entry.cosmeticId = cosmeticId
        entry.controller = controller
        entry.fit = scale is NameplatePreviewScale.Fit
    }

    val frameNanos by produceState(0L) { while (true) withFrameNanos { value = it } }
    Box(
        modifier
            .onGloballyPositioned { coords ->
                var root = coords
                while (true) {
                    root = root.parentLayoutCoordinates ?: break
                }
                val rw = root.size.width.toFloat()
                val rh = root.size.height.toFloat()
                if (rw > 0f && rh > 0f) {
                    entry.fw = coords.size.width / rw
                    entry.fh = coords.size.height / rh
                }
                if (coords.size.height > 0) {
                    entry.fontPixelFraction = fontPixelPx / coords.size.height
                }
            }
            .drawBehind {
                if (frameNanos == 0L) return@drawBehind
                val w = size.width
                val h = size.height
                drawPoly { NameplatePreviewOffscreen.draw(entry, it, w, h) }
            },
    )
}
