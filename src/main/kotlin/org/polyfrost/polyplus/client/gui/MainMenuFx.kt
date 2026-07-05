package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun rememberFxTime(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> time.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return time
}

@Composable
fun rememberParallaxOffset(target: State<Offset>, ease: Float = 0.06f): State<Offset> {
    val smooth = remember { mutableStateOf(target.value) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val t = target.value
            val s = smooth.value
            smooth.value = Offset(s.x + (t.x - s.x) * ease, s.y + (t.y - s.y) * ease)
        }
    }
    return smooth
}

private val GlowA = Color(0xFF1878F1)
private val GlowB = Color(0xFF24D3EE)
private val GlowC = Color(0xFF6D5EF6)
private val FxPageBackground = Color(0xFF11171C)

fun coolGlowColor(phase: Float): Color {
    val p = phase - floor(phase)
    return when {
        p < 1f / 3f -> lerp(GlowA, GlowB, p * 3f)
        p < 2f / 3f -> lerp(GlowB, GlowC, (p - 1f / 3f) * 3f)
        else -> lerp(GlowC, GlowA, (p - 2f / 3f) * 3f)
    }
}

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

fun DrawScope.drawMenuBackground(time: Float, mouse: Offset) {
    drawRect(FxPageBackground)
    val mdx = (mouse.x - 0.5f) * size.width
    val mdy = (mouse.y - 0.5f) * size.height
    for (b in Blobs) {
        val cx = size.width * (b.ax + sin(time * b.sx + b.phi) * b.driftX) + mdx * b.par
        val cy = size.height * (b.ay + cos(time * b.sy + b.psi) * b.driftY) + mdy * b.par
        val radius = size.width * b.rF * (1f + 0.12f * sin(time * 0.6f + b.phi))
        val alpha = (b.baseAlpha * (0.75f + 0.25f * sin(time * 0.9f + b.psi))).coerceIn(0f, 1f)
        val color = coolGlowColor(time * 0.05f + b.hueOffset)
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
    drawAurora(time)
    drawParticles(time, mouse)
    drawVignette(time)
}

private fun DrawScope.drawAurora(time: Float) {
    val sweep = sin(time * 0.08f) * 0.5f + 0.5f
    val cx = size.width * (sweep * 1.4f - 0.2f)
    val col = coolGlowColor(time * 0.05f + 0.2f)
    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.5f to col.copy(alpha = 0.06f),
                1f to Color.Transparent,
            ),
            start = Offset(cx - size.width * 0.3f, 0f),
            end = Offset(cx + size.width * 0.3f, size.height),
        ),
    )
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
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = r,
            ),
            radius = r,
            center = center,
        )
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
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PanoramaGlowBlue.copy(alpha = g.alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.drawVignette(time: Float) {
    val radius = size.maxDimension * (0.78f + 0.02f * sin(time * 0.5f))
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Transparent, FxPageBackground.copy(alpha = 0.55f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = radius,
        ),
    )
}
