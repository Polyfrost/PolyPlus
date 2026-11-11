@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.polyplus.client.lobby.GlassRegistry
import org.polyfrost.polyplus.client.lobby.LobbyGlass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

//? if = 1.8.9
//import org.lwjgl.input.Mouse

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

private const val PARALLAX_EASE = 0.02f
private const val BLUR_SPREAD = 2.6f
private const val MIN_SPOT_ALPHA = 1f / 255f
private val MenuBlur = 40.dp

private val GlowA = Color(0xFF1878F1)
private val GlowB = Color(0xFF24D3EE)
private val GlowC = Color(0xFF6D5EF6)
private val FxPageBackground = Color(0xFF11171C)

private class Blob(
    val ax: Float, val ay: Float,
    val rF: Float, val baseAlpha: Float,
    val sx: Float, val sy: Float,
    val phi: Float, val psi: Float,
    val driftX: Float, val driftY: Float,
    val hueOffset: Float,
    val par: Float,
)

private val Blobs = listOf(
    Blob(0.177f, -0.08f, 0.60f, 0.16f, 0.13f, 0.11f, 0.0f, 1.3f, 0.05f, 0.06f, 0.00f, 0.0030f),
    Blob(-0.116f, 1.02f, 0.60f, 0.16f, 0.09f, 0.14f, 2.1f, 0.4f, 0.06f, 0.05f, 0.33f, 0.0045f),
    Blob(1.024f, 0.19f, 0.55f, 0.12f, 0.12f, 0.10f, 1.0f, 3.0f, 0.05f, 0.07f, 0.66f, 0.0060f),
    Blob(0.699f, 1.12f, 0.50f, 0.12f, 0.10f, 0.13f, 4.0f, 2.0f, 0.06f, 0.05f, 0.15f, 0.0075f),
    Blob(0.500f, -0.15f, 0.45f, 0.10f, 0.15f, 0.09f, 3.3f, 1.7f, 0.07f, 0.05f, 0.50f, 0.0090f),
    Blob(0.900f, 0.85f, 0.45f, 0.10f, 0.08f, 0.12f, 5.0f, 2.6f, 0.05f, 0.06f, 0.80f, 0.0110f),
)

private const val GLOW_STEPS = 12

private val GlowPalette = arrayOf(GlowA, GlowB, GlowC)

private inline fun coolGlowLayers(phase: Float, alpha: Float, draw: (Color, Float) -> Unit) {
    val p = (phase - floor(phase)) * GlowPalette.size
    val i = p.toInt().coerceAtMost(GlowPalette.size - 1)
    val f = p - i
    if (f < 1f) draw(GlowPalette[i], alpha * (1f - f))
    if (f > 0f) draw(GlowPalette[(i + 1) % GlowPalette.size], alpha * f)
}

private fun DrawScope.drawRadial(stops: Array<Pair<Float, Color>>, center: Offset, radius: Float, alpha: Float) {
    translate(center.x, center.y) {
        drawCircle(Brush.radialGradient(colorStops = stops, center = Offset.Zero, radius = radius), radius, Offset.Zero, alpha)
    }
}

private fun DrawScope.drawSoftGlow(color: Color, center: Offset, radius: Float, alpha: Float) {
    val stops = Array(GLOW_STEPS + 1) { i ->
        val t = i.toFloat() / GLOW_STEPS
        val s = 1f - t
        t to color.copy(alpha = s * s * (3f - 2f * s))
    }
    drawRadial(stops, center, radius, alpha)
}

internal class MenuGlass : GlassRegistry {
    val panels = mutableStateMapOf<Any, LobbyGlass.Panel>()

    override fun put(key: Any, panel: LobbyGlass.Panel) {
        if (panels[key] != panel) panels[key] = panel
    }

    override fun putFloating(key: Any, panel: LobbyGlass.Panel) = put(key, panel)

    override fun remove(key: Any) {
        panels.remove(key)
    }
}

private val MenuFxStartNanos = System.nanoTime()
private val MenuFxMouse = floatArrayOf(0.5f, 0.5f)

private fun menuFxTime(nanos: Long): Float = (nanos - MenuFxStartNanos) / 1_000_000_000f

private fun easedMenuMouse(): Offset {
    val target = if (Motion.reduced) Offset(0.5f, 0.5f) else menuMouse()
    MenuFxMouse[0] += (target.x - MenuFxMouse[0]) * PARALLAX_EASE
    MenuFxMouse[1] += (target.y - MenuFxMouse[1]) * PARALLAX_EASE
    return Offset(MenuFxMouse[0], MenuFxMouse[1])
}

@Composable
internal fun MenuBackdrop(modifier: Modifier, glass: MenuGlass) {
    var time by remember { mutableFloatStateOf(0f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val frosted = !OneConfigTheme.current.isMinecraftStyle
    LaunchedEffect(Unit) {
        if (Motion.reduced) return@LaunchedEffect
        while (true) withFrameNanos { time = menuFxTime(it) }
    }
    Canvas(modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val mouse = easedMenuMouse()
        drawMenuBackground(time, mouse)
        if (frosted && ThemeConfig.enableBackgroundBlur) drawFrost(glass.panels.values, origin, time, mouse)
    }
}

internal object MenuFxPass {
    private val drawScope = CanvasDrawScope()
    private val inline = InlineCanvas("menu_fx")

    //? if >= 26.1 {
    @JvmStatic
    fun renderInline(ctx: GuiGraphicsExtractor): Boolean = inline.draw(ctx) { render(it) }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun renderInline(ctx: GuiGraphics): Boolean = inline.draw(ctx) { render(it) }
    *///?} else {
    /*@JvmStatic
    fun renderInline(): Boolean = inline.draw { render(it) }
    *///?}

    private fun render(canvas: ComposeCanvas) {
        val mc = Minecraft.getInstance()
        val w = mc.window.width
        val h = mc.window.height
        if (w <= 0 || h <= 0) return
        val time = if (Motion.reduced) 0f else menuFxTime(System.nanoTime())
        val mouse = easedMenuMouse()
        drawScope.draw(Density(1f), LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
            drawMenuBackground(time, mouse)
        }
    }
}

private fun DrawScope.drawFrost(panels: Collection<LobbyGlass.Panel>, origin: Offset, time: Float, mouse: Offset) {
    if (panels.isEmpty()) return
    val blur = MenuBlur.toPx()
    val clip = Path()
    for (panel in panels) {
        val centerX = panel.centerX * size.width - origin.x
        val centerY = panel.centerY * size.height - origin.y
        val halfWidth = panel.halfWidth * size.width
        val halfHeight = panel.halfHeight * size.height
        if (halfWidth <= 0f || halfHeight <= 0f) continue
        val bounds = Rect(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
        val radius = (panel.radius * size.height).coerceIn(0f, minOf(halfWidth, halfHeight))
        clip.reset()
        clip.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        clipPath(clip) { drawMenuBackground(time, mouse, blur, bounds) }
    }
}

private fun menuMouse(): Offset {
    val mc = Minecraft.getInstance()
    //? if > 1.8.9 {
    val screenW = mc.window.screenWidth.coerceAtLeast(1)
    val screenH = mc.window.screenHeight.coerceAtLeast(1)
    val x = (mc.mouseHandler.xpos() / screenW).toFloat()
    val y = (mc.mouseHandler.ypos() / screenH).toFloat()
    //?} else {
    /*val w = mc.window.width.coerceAtLeast(1)
    val h = mc.window.height.coerceAtLeast(1)
    val x = Mouse.getX().toFloat() / w
    val y = (h - Mouse.getY()).toFloat() / h
    *///?}
    return Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

fun DrawScope.drawMenuBackground(time: Float, mouse: Offset, blur: Float = 0f, within: Rect = Rect.Zero) {
    drawRect(FxPageBackground)
    val mdx = (mouse.x - 0.5f) * size.width
    val mdy = (mouse.y - 0.5f) * size.height
    for (b in Blobs) {
        val cx = size.width * (b.ax + sin(time * b.sx + b.phi) * b.driftX) + mdx * b.par
        val cy = size.height * (b.ay + cos(time * b.sy + b.psi) * b.driftY) + mdy * b.par
        val radius = size.width * b.rF * (1f + 0.12f * sin(time * 0.6f + b.phi))
        val alpha = (b.baseAlpha * (0.75f + 0.25f * sin(time * 0.9f + b.psi))).coerceIn(0f, 1f)
        coolGlowLayers(time * 0.05f + b.hueOffset, alpha) { color, a -> drawSoftSpot(color, a, Offset(cx, cy), radius, blur, within) }
    }
    drawAurora(time)
    drawParticles(time, mouse, blur, within)
    drawVignette()
}

private fun DrawScope.drawSoftSpot(color: Color, alpha: Float, center: Offset, radius: Float, blur: Float, within: Rect) {
    if (blur <= 0f) {
        drawSoftGlow(color, center, radius, alpha)
        return
    }
    val spread = sqrt(radius * radius + BLUR_SPREAD * BLUR_SPREAD * blur * blur)
    val faded = alpha * (radius / spread) * (radius / spread)
    if (faded < MIN_SPOT_ALPHA || !within.inflate(spread).contains(center)) return
    drawSoftGlow(color, center, spread, faded)
}

private fun DrawScope.drawAurora(time: Float) {
    val sweep = sin(time * 0.08f) * 0.5f + 0.5f
    val cx = size.width * (sweep * 1.4f - 0.2f)
    coolGlowLayers(time * 0.05f + 0.2f, 0.06f) { col, a ->
        drawRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to col,
                    1f to Color.Transparent,
                ),
                start = Offset(cx - size.width * 0.3f, 0f),
                end = Offset(cx + size.width * 0.3f, size.height),
            ),
            alpha = a,
        )
    }
}

private const val PARTICLE_COUNT = 44

private class Particle(
    val x: Float, val seedY: Float, val speed: Float,
    val size: Float, val phase: Float, val twSpeed: Float,
)

private val Particles: List<Particle> = (0 until PARTICLE_COUNT).map { i ->
    fun h(salt: Int): Float {
        var v = i * 73856093 + salt * 19349663
        v = v xor (v ushr 16)
        v *= 0x85EBCA6B.toInt()
        v = v xor (v ushr 13)
        v *= 0xC2B2AE35.toInt()
        v = v xor (v ushr 16)
        return (v and 0xFFFF) / 65535f
    }
    Particle(
        x = h(1),
        seedY = h(2),
        speed = 0.010f + h(3) * 0.030f,
        size = 1.2f + h(4) * 2.6f,
        phase = h(5) * (2f * PI.toFloat()),
        twSpeed = 0.8f + h(6) * 1.8f,
    )
}

private fun DrawScope.drawParticles(time: Float, mouse: Offset, blur: Float, within: Rect) {
    val mdx = (mouse.x - 0.5f) * size.width
    val mdy = (mouse.y - 0.5f) * size.height
    for (p in Particles) {
        var y = (p.seedY - time * p.speed) % 1f
        if (y < 0f) y += 1f
        val twinkle = 0.5f + 0.5f * sin(time * p.twSpeed + p.phase)
        val alpha = 0.35f * twinkle
        val par = p.size * 0.0006f
        val center = Offset(size.width * p.x + mdx * par, size.height * y + mdy * par)
        drawSoftSpot(Color.White, alpha, center, p.size * 3f, blur, within)
    }
}

private val VignetteStops = Array(GLOW_STEPS + 1) { i ->
    val t = i.toFloat() / GLOW_STEPS
    val s = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
    t to FxPageBackground.copy(alpha = 0.55f * s * s * (3f - 2f * s))
}

private fun DrawScope.drawVignette() = drawRadial(VignetteStops, center, size.maxDimension * 0.78f, 1f)
