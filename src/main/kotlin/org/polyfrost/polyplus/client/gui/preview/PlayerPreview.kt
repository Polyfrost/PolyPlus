//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

@Composable
fun PlayerPreview(
    modifier: Modifier = Modifier,
    source: PlayerPreviewSource = PlayerPreviewSource.LocalLive,
    autoSpin: Boolean = true,
    allowDrag: Boolean = true,
) {
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    androidx.compose.runtime.LaunchedEffect(autoSpin) {
        if (autoSpin) {
            while (true) {
                if (!dragging) yaw += AUTO_SPIN_DEG_PER_TICK
                delay(16L)
            }
        }
    }

    val bitmap: ImageBitmap? by produceState(null, source, yaw, pitch, sizePx) {
        value = if (sizePx.width > 0 && sizePx.height > 0) {
            PlayerPreviewRenderer.capture(source, yaw, pitch, sizePx.width, sizePx.height)
        } else {
            null
        }
    }

    Box(
        modifier
            .onSizeChanged { sizePx = it }
            .then(
                if (allowDrag) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, drag ->
                            yaw += drag.x * DRAG_YAW_SENSITIVITY
                            pitch = (pitch + drag.y * DRAG_PITCH_SENSITIVITY).coerceIn(-MAX_PITCH, MAX_PITCH)
                            change.consume()
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

private const val AUTO_SPIN_DEG_PER_TICK = 0.6f
private const val DRAG_YAW_SENSITIVITY = 0.5f
private const val DRAG_PITCH_SENSITIVITY = 0.5f
private const val MAX_PITCH = 45f
//?}
