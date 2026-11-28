@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.awt.Color as AwtColor
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.polyfrost.compose.host.controller.ControllerAxes
import org.polyfrost.compose.host.controller.Controllers
import org.polyfrost.compose.host.controller.controllerArrows
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.focusRing
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition
import org.polyfrost.oneconfig.api.ui.v1.keybind.trackTextInputFocus
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.focusableOnController
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost

@Composable
internal fun AuraSwatchRow(
    selectedColor: Int?,
    customOpen: Boolean,
    onToggleCustom: () -> Unit,
    onCommit: (Int?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        AuraDot(
            color = Color(DEFAULT_AURA_ARGB),
            isSelected = selectedColor == null,
            label = "Default aura colour",
            onClick = { onCommit(null) },
        )
        for (color in AURA_PRESETS) {
            AuraDot(
                color = Color(color),
                isSelected = selectedColor == color,
                label = "Aura colour ${auraHex(color)}",
                onClick = { onCommit(color) },
            )
        }
        val isCustom = selectedColor != null && selectedColor !in AURA_PRESETS
        AuraDot(
            color = if (isCustom) Color(selectedColor!!) else OneConfigTheme.current.componentBackground,
            isSelected = isCustom || customOpen,
            label = "Custom aura colour",
            onClick = onToggleCustom,
        ) {
            if (!isCustom) {
                Text("+", color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.body.copy(fontSize = 11.sp))
            }
        }
    }
}

@Composable
private fun AuraDot(
    color: Color,
    isSelected: Boolean,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    val shape = OneConfigTheme.shape(6.dp)
    Box(
        modifier = Modifier.size(18.dp)
            .clip(shape)
            .oneConfigClickable(label, onClick, selected = isSelected, shape = shape, indication = false, sound = null),
        contentAlignment = Alignment.Center,
    ) {
        Checkerboard(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(color))
        Box(
            Modifier.fillMaxSize().border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) OneConfigTheme.accent else OneConfigTheme.current.borderColor,
                shape = shape,
            ),
        )
        content()
    }
}

private val AURA_COLORS: List<Int> = listOf(
    0xFFFF4444.toInt(),
    0xFFFF9E3D.toInt(),
    0xFFF4D03F.toInt(),
    0xFF2ECC71.toInt(),
    0xFF1ABC9C.toInt(),
    0xFF3DA5FF.toInt(),
    0xFF6C5CE7.toInt(),
    0xFFB05CFF.toInt(),
    0xFFFF6FD8.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF3A3F43.toInt(),
    0xFF9BA2A6.toInt(),
)

private const val DEFAULT_AURA_ARGB: Int = -1

private val AURA_PRESETS: List<Int> = AURA_COLORS.filter { it != DEFAULT_AURA_ARGB }

private fun auraHex(argb: Int): String =
    "#%02X%02X%02X".format((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

private fun argbToHsb(argb: Int): FloatArray =
    AwtColor.RGBtoHSB((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, null)
        .also { it[0] *= 360f }

private fun hsbToColor(hue: Float, saturation: Float, brightness: Float): Color =
    Color.hsv(hue, saturation, brightness)

private fun argbHex(argb: Int): String =
    "%02X%02X%02X".format((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

private fun hexToArgb(hex: String, alpha: Float): Int? {
    val h = hex.removePrefix("#").trim()
    if (h.length != 6) return null
    return try {
        ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or Integer.parseInt(h, 16)
    } catch (_: Exception) {
        null
    }
}

private fun Color.toArgb(alpha: Float): Int =
    ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or
        ((red.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
        ((green.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
        (blue.coerceIn(0f, 1f) * 255f).roundToInt()

private const val PAD_STEP = 0.05f
private const val HUE_STEP = 10f

private fun Modifier.arrowAdjust(
    vertical: Boolean,
    shape: Shape,
    onDone: () -> Unit,
    onStep: (dx: Float, dy: Float) -> Boolean,
): Modifier = composed {
    var editing by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    val done by rememberUpdatedState(onDone)
    val step by rememberUpdatedState(onStep)

    fun stopEditing() {
        editing = false
        if (!dirty) return
        dirty = false
        done()
    }

    DisposableEffect(Unit) { onDispose { if (dirty) done() } }

    onFocusChanged { if (!it.isFocused && editing) stopEditing() }
        .then(if (editing) Modifier.controllerArrows(if (vertical) ControllerAxes.Both else ControllerAxes.Horizontal) else Modifier)
        .onKeyEvent { event ->
            when (event.key) {
                Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyUp) {
                        if (editing) stopEditing() else editing = true
                    }
                    true
                }
                Key.Escape -> {
                    if (!editing) return@onKeyEvent false
                    if (event.type == KeyEventType.KeyDown) stopEditing()
                    true
                }
                else -> {
                    if (!editing || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val dx = when (event.key) {
                        Key.DirectionLeft -> -1f
                        Key.DirectionRight -> 1f
                        else -> 0f
                    }
                    val dy = when (event.key) {
                        Key.DirectionUp -> if (vertical) -1f else 0f
                        Key.DirectionDown -> if (vertical) 1f else 0f
                        else -> 0f
                    }
                    if (dx == 0f && dy == 0f) return@onKeyEvent false
                    if (step(dx, dy)) dirty = true
                    true
                }
            }
        }
        .focusRing(shape, editing && Controllers.isActive)
        .focusableOnController()
}

private class AuraPickerState(argb: Int) {
    var hue by mutableFloatStateOf(0f)
    var saturation by mutableFloatStateOf(0f)
    var brightness by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)

    var lastEmitted: Int? = null

    init {
        applyFrom(argb)
    }

    fun applyFrom(argb: Int) {
        val hsb = argbToHsb(argb)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        alpha = ((argb ushr 24) and 0xFF) / 255f
    }

    fun currentArgb(): Int = hsbToColor(hue, saturation, brightness).toArgb(alpha)
}

@Composable
private fun Checkerboard(modifier: Modifier, cell: Dp = 4.dp) {
    Box(
        modifier = modifier.drawBehind {
            val step = cell.toPx()
            val cols = ceil(size.width / step).toInt()
            val rows = ceil(size.height / step).toInt()
            for (x in 0..cols) {
                for (y in 0..rows) {
                    drawRect(
                        color = if ((x + y) % 2 == 0) Color(0xFF5F6568) else Color(0xFF3D4245),
                        topLeft = Offset(x * step, y * step),
                        size = Size(step, step),
                    )
                }
            }
        },
    )
}

@Composable
internal fun AuraColorPopup(
    selectedColor: Int?,
    onPreview: (Int) -> Unit,
    onCommit: (Int?) -> Unit,
    onDismiss: () -> Unit,
    gap: Dp = 4.dp,
) {
    EscapeCloses(onDismiss)
    Popup(
        popupPositionProvider = rememberAnchoredPosition(Placement.BelowEnd, gap),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AuraCustomPopover(selectedColor, onPreview, onCommit)
    }
}

@Composable
private fun AuraCustomPopover(
    selectedColor: Int?,
    onPreview: (Int) -> Unit,
    onCommit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { AuraPickerState(selectedColor ?: 0xFFFFFFFF.toInt()) }
    var hexText by remember { mutableStateOf(argbHex(state.currentArgb())) }
    var hexPending by remember { mutableStateOf(false) }
    LaunchedEffect(selectedColor) {
        if (selectedColor != null && selectedColor != state.lastEmitted) {
            state.applyFrom(selectedColor)
            hexText = argbHex(selectedColor)
        }
    }

    fun emitPreview() {
        val argb = state.currentArgb()
        state.lastEmitted = argb
        hexText = argbHex(argb)
        onPreview(argb)
    }

    fun emitCommit() {
        val argb = state.currentArgb()
        state.lastEmitted = argb
        hexPending = false
        onCommit(argb)
    }

    fun commitHex() {
        if (hexPending) emitCommit()
    }

    val latestCommitHex by rememberUpdatedState<() -> Unit> { commitHex() }
    DisposableEffect(Unit) { onDispose { latestCommitHex() } }

    val hue = state.hue
    val saturation = state.saturation
    val brightness = state.brightness
    val alpha = state.alpha
    val current = hsbToColor(hue, saturation, brightness)
    val theme = OneConfigTheme.current
    val paneShape = OneConfigTheme.shape(16.dp)
    val barShape = OneConfigTheme.shape(6.dp)
    val handleShape = OneConfigTheme.shape(5.dp)

    Column(
        modifier = modifier.width(190.dp)
            .sceneFrost(16.dp)
            .clip(paneShape)
            .background(theme.popupBackground)
            .border(1.dp, theme.borderColor, paneShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Aura Color", color = theme.textColorSecondary, style = theme.typography.body.copy(fontSize = 12.sp))

        var paneSize by remember { mutableStateOf(Size.Zero) }
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp)
                .clip(barShape)
                .onSizeChanged { paneSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .arrowAdjust(vertical = true, barShape, ::emitCommit) { dx, dy ->
                    val nextSaturation = (state.saturation + dx * PAD_STEP).coerceIn(0f, 1f)
                    val nextBrightness = (state.brightness - dy * PAD_STEP).coerceIn(0f, 1f)
                    val changed = nextSaturation != state.saturation || nextBrightness != state.brightness
                    state.saturation = nextSaturation
                    state.brightness = nextBrightness
                    if (changed) emitPreview()
                    changed
                }
                .drawWithCache {
                    val hueColor = hsbToColor(hue, 1f, 1f)
                    val horizontal = Brush.horizontalGradient(listOf(Color.White, hueColor))
                    val vertical = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    onDrawBehind {
                        drawRect(horizontal)
                        drawRect(vertical)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float, y: Float) {
                            state.saturation = (x / paneSize.width).coerceIn(0f, 1f)
                            state.brightness = 1f - (y / paneSize.height).coerceIn(0f, 1f)
                            emitPreview()
                        }
                        update(down.position.x, down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    update(ch.position.x, ch.position.y)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        emitCommit()
                    }
                },
        ) {
            val selX = (saturation * paneSize.width).coerceIn(0f, paneSize.width)
            val selY = ((1f - brightness) * paneSize.height).coerceIn(0f, paneSize.height)
            Box(
                modifier = Modifier
                    .offset { IntOffset((selX - 7.dp.toPx()).roundToInt(), (selY - 7.dp.toPx()).roundToInt()) }
                    .size(14.dp)
                    .clip(barShape)
                    .background(current)
                    .border(2.dp, Color.White, barShape),
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(14.dp)
                .clip(barShape)
                .drawWithCache {
                    val gradient = Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                    )
                    onDrawBehind { drawRect(gradient) }
                }
                .arrowAdjust(vertical = false, barShape, ::emitCommit) { dx, _ ->
                    val next = (state.hue + dx * HUE_STEP).coerceIn(0f, 360f)
                    val changed = next != state.hue
                    state.hue = next
                    if (changed) emitPreview()
                    changed
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float) {
                            state.hue = ((x / size.width) * 360f).coerceIn(0f, 360f)
                            emitPreview()
                        }
                        update(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    update(ch.position.x)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        emitCommit()
                    }
                },
        ) {
            var barWidth by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxWidth().height(14.dp).onSizeChanged { barWidth = it.width.toFloat() })
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset(((hue / 360f) * barWidth - 5.dp.toPx()).roundToInt().coerceAtLeast(0), 0) }
                    .size(10.dp)
                    .clip(handleShape)
                    .background(Color.White)
                    .border(1.dp, Color.White.copy(0.5f), handleShape),
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(14.dp)
                .clip(barShape)
                .arrowAdjust(vertical = false, barShape, ::emitCommit) { dx, _ ->
                    val next = (state.alpha + dx * PAD_STEP).coerceIn(0f, 1f)
                    val changed = next != state.alpha
                    state.alpha = next
                    if (changed) emitPreview()
                    changed
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float) {
                            state.alpha = (x / size.width).coerceIn(0f, 1f)
                            emitPreview()
                        }
                        update(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    update(ch.position.x)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        emitCommit()
                    }
                },
        ) {
            Checkerboard(Modifier.fillMaxWidth().height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(14.dp)
                    .drawWithCache {
                        val gradient = Brush.horizontalGradient(listOf(current.copy(alpha = 0f), current))
                        onDrawBehind { drawRect(gradient) }
                    },
            )
            var alphaBarWidth by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxWidth().height(14.dp).onSizeChanged { alphaBarWidth = it.width.toFloat() })
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((alpha * alphaBarWidth - 5.dp.toPx()).roundToInt().coerceAtLeast(0), 0) }
                    .size(10.dp)
                    .clip(handleShape)
                    .background(Color.White)
                    .border(1.dp, Color.White.copy(0.5f), handleShape),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(28.dp).clip(handleShape)) {
                Checkerboard(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(current.copy(alpha = alpha)))
                Box(Modifier.fillMaxSize().border(1.dp, theme.borderColor, handleShape))
            }
            BasicTextField(
                value = hexText,
                onValueChange = { input ->
                    val filtered = input.filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }.take(6)
                    hexText = filtered
                    hexToArgb(filtered, state.alpha)?.let { argb ->
                        state.applyFrom(argb)
                        state.lastEmitted = argb
                        hexPending = true
                        onPreview(argb)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commitHex() }),
                singleLine = true,
                textStyle = TextStyle(
                    color = theme.textColor,
                    fontSize = 12.sp,
                    fontFamily = theme.fontFamily,
                ),
                cursorBrush = SolidColor(theme.textColor),
                modifier = Modifier.weight(1f)
                    .clip(handleShape)
                    .background(theme.chipBackground)
                    .border(1.dp, theme.borderColor, handleShape)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .onFocusChanged { if (!it.isFocused) commitHex() }
                    .trackTextInputFocus(),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("#", color = theme.textColorSecondary, style = theme.typography.body.copy(fontSize = 12.sp))
                        inner()
                    }
                },
            )
        }
    }
}
