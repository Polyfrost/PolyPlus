@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.polyfrost.oneconfig.api.platform.v1.DesktopHelper
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

private val WHEEL_DIAMETER = 420.dp
private val INNER_DIAMETER = 190.dp
private val WHEEL_RADIUS = WHEEL_DIAMETER / 2
private val INNER_RADIUS = INNER_DIAMETER / 2

private const val RING_STROKE_W = 1f

private const val SLOT_COUNT = 8
private const val SECTOR_DEG = 360f / SLOT_COUNT

private const val SCROLL_NOTCH = 8f
private const val MAC_SCROLL_NOTCH = 2f

private val PopInEasing = CubicBezierEasing(0.0f, 0.55f, 0.45f, 1.0f)

private data class WheelSlot(val id: Int, val name: String)

class EmoteWheelScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private val logger = LogManager.getLogger("polyplus/emote-wheel")

    //? if > 1.8.9 {
    override fun shouldCloseOnEsc(): Boolean = true

    //? if <26.1 {
    /*override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    *///?} else {
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) = Unit
    //?}

    override fun onClose() {
        Minecraft.getInstance().execute { super.onClose() }
    }
    //?}

    @Composable
    override fun compose() {
        ProvideOneConfigTheme {
            EmoteWheelContent(
                logger = logger,
                onRelease = { emoteId ->
                    if (emoteId != null) {
                        PolyPlusClient.SCOPE.launch {
                            CosmeticService.playEmote(emoteId)
                                .onFailure {
                                    logger.error("Failed to play emote {}", emoteId, it)
                                }
                        }
                    }
                    closeWheel()
                },
            )
        }
    }

    private fun closeWheel() {
        //? if > 1.8.9 {
        Minecraft.getInstance().execute {
        //?} else {
        /*Minecraft.getInstance().tell {
        *///?}
            val mc = Minecraft.getInstance()
            //? if >= 26.2 {
            if (mc.gui.screen() === this) mc.gui.setScreen(null)
            //?} elif > 1.8.9 {
            /*if (mc.screen === this) mc.setScreen(null)
            *///?} else {
            /*if (mc.screen === this) mc.openScreen(null)
            *///?}
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EmoteWheelContent(logger: Logger, onRelease: (Int?) -> Unit) {
    val wheelIndex = EmoteWheels.activeIndex
    val wheelCount = EmoteWheels.wheelCount
    val slots = remember(wheelIndex, wheelCount) {
        EmoteWheels.slots(wheelIndex).map { id ->
            id?.let { emoteId -> CosmeticCatalog.getEmoteDefinition(emoteId)?.let { WheelSlot(it.id, it.name) } }
        }
    }

    var mouseXRoot by remember { mutableFloatStateOf(0f) }
    var mouseYRoot by remember { mutableFloatStateOf(0f) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    var wheelBounds by remember { mutableStateOf(Rect.Zero) }

    val popIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        popIn.animateTo(1f, animationSpec = tween(180, easing = PopInEasing))
    }

    val wheelRadiusPx = with(LocalDensity.current) { WHEEL_RADIUS.toPx() }
    val innerRadiusPx = with(LocalDensity.current) { INNER_RADIUS.toPx() }

    val hoveredSlot by remember(mouseXRoot, mouseYRoot, popIn.value) {
        derivedStateOf {
            if (popIn.value <= 0.01f) return@derivedStateOf -1
            val cx = wheelBounds.center.x
            val cy = wheelBounds.center.y
            val dx = mouseXRoot - cx
            val dy = mouseYRoot - cy
            val dist = sqrt(dx * dx + dy * dy)
            if (dist in innerRadiusPx * popIn.value..wheelRadiusPx * popIn.value) {
                val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + SECTOR_DEG / 2f + 360f) % 360f
                (angle / SECTOR_DEG).toInt().coerceIn(0, SLOT_COUNT - 1)
            } else {
                -1
            }
        }
    }

    val currentSlots by rememberUpdatedState(slots)
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos {}
            if (!EmoteWheelKeybind.isHeld()) {
                onRelease(currentSlots.getOrNull(hoveredSlot)?.id)
                break
            }
        }
    }
    val scrollAccum = remember { FloatArray(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootPos = it.positionInRoot() }
            .onPointerEvent(PointerEventType.Move) { event ->
                val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                mouseXRoot = pos.x + rootPos.x
                mouseYRoot = pos.y + rootPos.y
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: return@onPointerEvent
                val notch = if (DesktopHelper.isMac) MAC_SCROLL_NOTCH else SCROLL_NOTCH
                scrollAccum[0] += delta
                while (abs(scrollAccum[0]) >= notch) {
                    val step = if (scrollAccum[0] > 0f) 1 else -1
                    EmoteWheels.cycle(step)
                    scrollAccum[0] -= step * notch
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(WHEEL_DIAMETER), contentAlignment = Alignment.Center) {
            WheelCanvas(
                popInValue = popIn.value,
                hoveredSlot = hoveredSlot,
                slots = slots,
                onLayout = { wheelBounds = it },
            )

            val hoveredName = slots.getOrNull(hoveredSlot)?.name
            if (hoveredName == null && wheelCount > 1) {
                BasicText(
                    text = "Wheel ${wheelIndex + 1} of $wheelCount",
                    style = TextStyle(
                        color = OneConfigTheme.current.textColorSecondary,
                        fontSize = 12.sp,
                        fontFamily = OneConfigTheme.current.fontFamily,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            if (hoveredName != null) {
                Box(
                    modifier = Modifier.widthIn(max = INNER_DIAMETER - 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = hoveredName,
                        style = TextStyle(
                            color = OneConfigTheme.current.textColor,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = OneConfigTheme.current.fontFamily,
                            textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun WheelCanvas(
    popInValue: Float,
    hoveredSlot: Int,
    slots: List<WheelSlot?>,
    onLayout: (Rect) -> Unit,
) {
    val accent = OneConfigTheme.accent
    val filledColor = OneConfigTheme.current.chipBackground
    val emptyColor = OneConfigTheme.current.componentBackground
    val ringColor = OneConfigTheme.current.borderColor
    Canvas(
        modifier = Modifier
            .size(WHEEL_DIAMETER)
            .onGloballyPositioned { onLayout(it.boundsInRoot()) },
    ) {
        if (popInValue <= 0.01f) return@Canvas

        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = WHEEL_RADIUS.toPx()
        val innerR = INNER_RADIUS.toPx()
        val midR = (outerR + innerR) / 2f
        val thickness = outerR - innerR

        scale(popInValue) {
            val arcTL = Offset(cx - midR, cy - midR)
            val arcSize = Size(midR * 2, midR * 2)
            val center = Offset(cx, cy)

            for (i in 0 until SLOT_COUNT) {
                val startDeg = -90f - SECTOR_DEG / 2f + i * SECTOR_DEG
                val hasEmote = slots.getOrNull(i) != null
                val isHovered = i == hoveredSlot && hasEmote

                drawArc(
                    color = if (isHovered) accent else if (hasEmote) filledColor else emptyColor,
                    startAngle = startDeg, sweepAngle = SECTOR_DEG, useCenter = false,
                    topLeft = arcTL, size = arcSize, style = Stroke(thickness),
                )
            }

            drawCircle(color = ringColor, radius = outerR + RING_STROKE_W / 2f, center = center, style = Stroke(RING_STROKE_W))
            drawCircle(color = ringColor, radius = innerR - RING_STROKE_W / 2f, center = center, style = Stroke(RING_STROKE_W))
        }
    }
}
