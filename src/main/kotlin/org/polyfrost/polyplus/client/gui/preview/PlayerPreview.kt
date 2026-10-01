package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun PlayerPreview(
    modifier: Modifier = Modifier,
    source: PlayerPreviewSource = PlayerPreviewSource.LocalLive,
    autoSpin: Boolean = true,
    allowDrag: Boolean = true,
    modelScale: Float = 0.5f,
    verticalAnchor: Float = 0.5f,
    initialYaw: Float = 0f,
    previewKey: Any = source,
    live: Boolean = false,
    bottomFadeFraction: Float = 0f,
) {
    if (live) {
        PlayerPreviewLive(modifier, source, autoSpin, allowDrag, modelScale, verticalAnchor, initialYaw, previewKey, bottomFadeFraction)
        return
    }
    PlayerPreviewBitmap(modifier, source, autoSpin, allowDrag, modelScale, verticalAnchor, initialYaw, previewKey)
}

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
    val entry = remember { PlayerPreviewOffscreen.register() }
    DisposableEffect(entry) {
        onDispose { PlayerPreviewOffscreen.unregister(entry) }
    }

    remember(previewKey, initialYaw) {
        entry.dragYaw = 0f; entry.dragPitch = 0f
        entry.spinAccum = 0f; entry.lastSpinNanos = 0L
    }

    SideEffect {
        entry.source = source
        entry.modelScale = modelScale
        entry.verticalAnchor = verticalAnchor
        entry.autoSpin = autoSpin
        entry.initialYaw = initialYaw
    }

    val frameNanos by produceState(0L) { while (true) withFrameNanos { value = it } }
    val edgeFade = remember { edgeFadeBrush() }
    val bottomFade = remember(bottomFadeFraction) { bottomFadeBrush(bottomFadeFraction) }

    val dragModifier: Modifier =
        if (allowDrag) {
            Modifier.pointerInput(entry) {
                detectDragGestures(
                    onDragStart = { entry.dragging = true },
                    onDragEnd = { entry.dragging = false },
                    onDragCancel = { entry.dragging = false },
                ) { change, drag ->
                    entry.dragYaw -= drag.x * DRAG_YAW_SENSITIVITY
                    entry.dragPitch = (entry.dragPitch + drag.y * DRAG_PITCH_SENSITIVITY)
                        .coerceIn(-MAX_PITCH, MAX_PITCH)
                    change.consume()
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier.then(dragModifier)
            .onGloballyPositioned { coords ->
                var root = coords
                while (true) { root = root.parentLayoutCoordinates ?: break }
                val rw = root.size.width.toFloat()
                val rh = root.size.height.toFloat()
                if (rw > 0f && rh > 0f) {
                    entry.fw = coords.size.width / rw
                    entry.fh = coords.size.height / rh
                }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                // reading frameNanos invalidates this draw every frame so the newest offscreen render shows
                if (frameNanos == 0L) return@drawBehind
                drawIntoCanvas { PlayerPreviewOffscreen.draw(entry, it.skiaCanvas, size.width, size.height) }
                drawRect(edgeFade, blendMode = BlendMode.DstIn)
                bottomFade?.let { drawRect(it, blendMode = BlendMode.DstIn) }
            },
    )
}

private const val FADE_STOPS = 12

private fun fadeStop(position: Float, t: Float): Pair<Float, Color> =
    position to Color.White.copy(alpha = PlayerPreviewRenderer.smooth(t))

private fun edgeFadeBrush(): Brush {
    val edge = PlayerPreviewRenderer.EDGE_FADE_FRACTION
    val left = (0..FADE_STOPS).map { i -> (i / FADE_STOPS.toFloat()).let { fadeStop(it * edge, it) } }
    val right = left.asReversed().map { (x, c) -> 1f - x to c }
    return Brush.horizontalGradient(*(left + right).toTypedArray())
}

private fun bottomFadeBrush(fraction: Float): Brush? {
    if (fraction <= 0f) return null
    val f = fraction.coerceAtMost(1f)
    val stops = (FADE_STOPS downTo 0).map { i -> (i / FADE_STOPS.toFloat()).let { fadeStop(1f - it * f, it) } }
    return Brush.verticalGradient(*stops.toTypedArray())
}

@Composable
private fun PlayerPreviewBitmap(
    modifier: Modifier = Modifier,
    source: PlayerPreviewSource = PlayerPreviewSource.LocalLive,
    autoSpin: Boolean = true,
    allowDrag: Boolean = true,
    modelScale: Float = 0.5f,
    verticalAnchor: Float = 0.5f,
    initialYaw: Float = 0f,
    previewKey: Any = source,
) {
    var yaw by remember(previewKey, initialYaw) { mutableFloatStateOf(initialYaw) }
    var pitch by remember(previewKey) { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var sizePx by remember { mutableStateOf(IntSize.Zero) }

    DisposableEffect(previewKey) {
        PlayerPreviewRenderer.retain(previewKey)
        onDispose { PlayerPreviewRenderer.release(previewKey) }
    }

    LaunchedEffect(autoSpin) {
        if (autoSpin) {
            while (true) {
                if (!dragging) yaw += AUTO_SPIN_DEG_PER_TICK
                delay(16L)
            }
        }
    }

    val bitmap: ImageBitmap? by produceState(null, source, yaw, pitch, sizePx, modelScale, verticalAnchor, previewKey) {
        if (sizePx.width > 0 && sizePx.height > 0) {
            withContext(Dispatchers.Default) {
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
            Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

private const val CAPTURE_POLL_ATTEMPTS = 30
private const val CAPTURE_POLL_INTERVAL_MS = 16L

private const val AUTO_SPIN_DEG_PER_TICK = 0.6f
private const val DRAG_YAW_SENSITIVITY = 0.5f
private const val DRAG_PITCH_SENSITIVITY = 0.5f
private const val MAX_PITCH = 45f
