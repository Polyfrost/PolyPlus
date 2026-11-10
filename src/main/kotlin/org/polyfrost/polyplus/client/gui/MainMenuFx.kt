package org.polyfrost.polyplus.client.gui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

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

fun DrawScope.drawMenuBackground(time: Float, mouse: Offset) {
    drawRect(FxPageBackground)
    val mdx = (mouse.x - 0.5f) * size.width
    val mdy = (mouse.y - 0.5f) * size.height
    for (b in Blobs) {
        val cx = size.width * (b.ax + sin(time * b.sx + b.phi) * b.driftX) + mdx * b.par
        val cy = size.height * (b.ay + cos(time * b.sy + b.psi) * b.driftY) + mdy * b.par
        val radius = size.width * b.rF * (1f + 0.12f * sin(time * 0.6f + b.phi))
        val alpha = (b.baseAlpha * (0.75f + 0.25f * sin(time * 0.9f + b.psi))).coerceIn(0f, 1f)
        coolGlowLayers(time * 0.05f + b.hueOffset, alpha) { color, a -> drawSoftGlow(color, Offset(cx, cy), radius, a) }
    }
    drawAurora(time)
    drawParticles(time, mouse)
    drawVignette()
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
        val v = (i * 73856093) xor (salt * 19349663)
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

private fun DrawScope.drawParticles(time: Float, mouse: Offset) {
    val mdx = (mouse.x - 0.5f) * size.width
    val mdy = (mouse.y - 0.5f) * size.height
    for (p in Particles) {
        var y = (p.seedY - time * p.speed) % 1f
        if (y < 0f) y += 1f
        val twinkle = 0.5f + 0.5f * sin(time * p.twSpeed + p.phase)
        val alpha = 0.35f * twinkle
        val par = p.size * 0.0006f
        val center = Offset(size.width * p.x + mdx * par, size.height * y + mdy * par)
        val r = p.size * 3f
        drawSoftGlow(Color.White, center, r, alpha)
    }
}

private val PanoramaBlackFlat = Color(0xFF11171C)
private val PanoramaBlackGradient = Color(0xFF10161B)
private val PanoramaGlowBlue = Color(0xFF1878F1)

private class GlowSpot(
    val ax: Float,
    val ay: Float,
    val radiusF: Float,
    val alpha: Float,
)

private val GlowMask = listOf(
    GlowSpot(0.177f, -0.080f, 0.401f, 0.15f),
    GlowSpot(-0.117f, 1.023f, 0.401f, 0.15f),
    GlowSpot(1.024f, 0.190f, 0.322f, 0.10f),
    GlowSpot(0.699f, 1.119f, 0.295f, 0.10f),
)

fun DrawScope.drawPanoramaOverlay() {
    if (PolyPlusMainMenuConfig.hidePanoramaOverlay) return
    drawPanoramaBlackBackground()
    drawPanoramaGlowMask()
}

private fun DrawScope.drawPanoramaBlackBackground() {
    drawRect(PanoramaBlackFlat.copy(alpha = 0.10f))
    drawRect(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.199f to Color.Transparent,
                0.960f to PanoramaBlackGradient.copy(alpha = 0.25f),
            ),
        ),
    )
}

private fun DrawScope.drawPanoramaGlowMask() {
    for (g in GlowMask) {
        val center = Offset(size.width * g.ax, size.height * g.ay)
        val radius = size.width * g.radiusF
        drawSoftGlow(PanoramaGlowBlue, center, radius, g.alpha)
    }
}

private val VignetteStops = Array(GLOW_STEPS + 1) { i ->
    val t = i.toFloat() / GLOW_STEPS
    val s = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
    t to FxPageBackground.copy(alpha = 0.55f * s * s * (3f - 2f * s))
}

private fun DrawScope.drawVignette() = drawRadial(VignetteStops, center, size.maxDimension * 0.78f, 1f)
