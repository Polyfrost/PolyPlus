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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.delay

@Composable
fun PlayerPreview(
    modifier: Modifier = Modifier,
    source: PlayerPreviewSource = PlayerPreviewSource.LocalLive,
    autoSpin: Boolean = true,
    allowDrag: Boolean = true,
    bottomFade: Brush? = null,
    modelScale: Float = 0.5f,
    verticalAnchor: Float = 0.5f,
    initialYaw: Float = 0f,
    previewKey: Any = source,
    live: Boolean = false,
    bottomFadeFraction: Float = 0f,
) {
    //? if < 1.21.5 || >= 1.21.8 {
    if (live) {
        PlayerPreviewLive(modifier, source, autoSpin, allowDrag, modelScale, verticalAnchor, initialYaw, previewKey, bottomFadeFraction)
        return
    }
    //?}
    PlayerPreviewBitmap(modifier, source, autoSpin, allowDrag, bottomFade, modelScale, verticalAnchor, initialYaw, previewKey)
}

//? if < 1.21.5 || >= 1.21.8 {
@Composable
private fun PlayerPreviewLive(
    modifier: Modifier,
    source: PlayerPreviewSource,
    autoSpin: Boolean,
    allowDrag: Boolean,
    modelScale: Float,
    verticalAnchor: Float,
    initialYaw: Float,
    previewKey: Any,
    bottomFadeFraction: Float,
) {
    val entry = remember { PlayerPreviewOverlay.register() }
    androidx.compose.runtime.DisposableEffect(entry) {
        onDispose {
            PlayerPreviewOverlay.reportBounds(entry, 0f, 0f, 0f, 0f, visible = false)
            PlayerPreviewOverlay.unregister(entry.id)
        }
    }

    remember(previewKey, initialYaw) {
        entry.dragYaw = 0f; entry.dragPitch = 0f
        entry.spinAccum = 0f; entry.lastSpinNanos = 0L
    }

    androidx.compose.runtime.SideEffect {
        entry.source = source
        entry.modelScale = modelScale
        entry.verticalAnchor = verticalAnchor
        entry.autoSpin = autoSpin
        entry.initialYaw = initialYaw
        entry.previewKey = previewKey
        entry.allowDrag = allowDrag
        entry.bottomFade = bottomFadeFraction
    }

    val holeModifier: Modifier = Modifier

    val dragModifier: Modifier =
        if (allowDrag) {
            Modifier.pointerInput(entry) {
                detectDragGestures(
                    onDragStart = { entry.dragging = true },
                    onDragEnd = { entry.dragging = false },
                    onDragCancel = { entry.dragging = false },
                ) { change, drag ->
                    entry.dragYaw -= drag.x * LIVE_DRAG_YAW_SENSITIVITY
                    entry.dragPitch = (entry.dragPitch + drag.y * LIVE_DRAG_PITCH_SENSITIVITY)
                        .coerceIn(-LIVE_MAX_PITCH, LIVE_MAX_PITCH)
                    change.consume()
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier.then(holeModifier).then(dragModifier).onGloballyPositioned { coords ->
            var root = coords
            while (true) { root = root.parentLayoutCoordinates ?: break }
            val rw = root.size.width.toFloat()
            val rh = root.size.height.toFloat()
            val b = coords.boundsInWindow()
            if (rw > 0f && rh > 0f && b.width > 0f && b.height > 0f) {
                PlayerPreviewOverlay.reportBounds(entry, b.left / rw, b.top / rh, b.width / rw, b.height / rh, visible = true)
            } else {
                PlayerPreviewOverlay.reportBounds(entry, 0f, 0f, 0f, 0f, visible = false)
            }
        },
    )
}
//?}

@Composable
private fun PlayerPreviewBitmap(
    modifier: Modifier = Modifier,
    source: PlayerPreviewSource = PlayerPreviewSource.LocalLive,
    autoSpin: Boolean = true,
    allowDrag: Boolean = true,
    bottomFade: Brush? = null,
    modelScale: Float = 0.5f,
    verticalAnchor: Float = 0.5f,
    initialYaw: Float = 0f,
    previewKey: Any = source,
) {
    var yaw by remember(previewKey, initialYaw) { mutableFloatStateOf(initialYaw) }
    var pitch by remember(previewKey) { mutableFloatStateOf(0f) }
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

    val bitmap: ImageBitmap? by produceState(null, source, yaw, pitch, sizePx, modelScale, verticalAnchor, previewKey) {
        if (sizePx.width > 0 && sizePx.height > 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                var attempts = 0
                while (attempts < CAPTURE_POLL_ATTEMPTS) {
                    val bmp = PlayerPreviewRenderer.capture(source, yaw, pitch, sizePx.width, sizePx.height, modelScale, verticalAnchor, previewKey)
                    if (bmp != null) value = bmp
                    delay(CAPTURE_POLL_INTERVAL_MS)
                    attempts++
                }
            }
        } else {
            value = null
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
                            yaw -= drag.x * DRAG_YAW_SENSITIVITY
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
            val imageModifier = if (bottomFade != null) {
                Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(bottomFade, blendMode = BlendMode.SrcAtop)
                    }
            } else {
                Modifier.fillMaxSize()
            }
            Image(bmp, contentDescription = null, modifier = imageModifier, contentScale = ContentScale.Fit)
        }
    }
}

private const val CAPTURE_POLL_ATTEMPTS = 30
private const val CAPTURE_POLL_INTERVAL_MS = 16L

private const val AUTO_SPIN_DEG_PER_TICK = 0.6f
private const val DRAG_YAW_SENSITIVITY = 0.5f
private const val DRAG_PITCH_SENSITIVITY = 0.5f
private const val MAX_PITCH = 45f

private const val LIVE_DRAG_YAW_SENSITIVITY = 0.5f
private const val LIVE_DRAG_PITCH_SENSITIVITY = 0.5f
private const val LIVE_MAX_PITCH = 45f
//?}
