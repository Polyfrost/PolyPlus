@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ui.components.liquidGlass
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

data class RadialWheelEntry(
    val icon: String?,
    val label: String,
    val sublabel: String? = null,
    val enabled: Boolean = true,
    val dot: Color? = null,
    val drawIcon: (DrawScope.(Size, Color) -> Unit)? = null,
)

private const val CANCEL_HINT = "Right click to cancel"

private const val WINDOW_SHARE = 0.34f
private const val HUB_SHARE = 0.44f
private const val HIDDEN_SCALE = 0.9f
private const val HOVER_SCALE = 0.06f
private const val SURFACE_ALPHA = 0.78f
private const val HUB_ALPHA = 0.88f
private const val EMPTY_SURFACE_ALPHA = 0.75f
private const val SHADOW_ALPHA = 0.22f
private const val HALO_ALPHA = 0.05f
private const val HALO_RINGS = 6
private const val ARC_STEP_DEG = 3.0
private const val HOVER_MILLIS = 110
private const val MAX_GLASS_RADIUS_PX = 255f
private const val HINT_ALPHA = 0.85f
private const val GLYPH_EM = 10
private const val CURSOR_SHADOW_ALPHA = 0.35f
private const val CURSOR_OUTLINE_ALPHA = 0.85f
private const val QUARTER = PI / 2
private const val EIGHTH = PI / 4

private val MIN_DIAMETER = 240.dp
private val MAX_DIAMETER = 340.dp
private val WEDGE_GAP = 3.dp
private val HUB_GAP = 8.dp
private val WEDGE_CORNER = 8.dp
private val HOVER_POP = 3.dp
private val ICON_SIZE = 22.dp
private val BELOW_GAP = 14.dp
private val HALO_STEP = 1.5.dp
private val SHADOW_DROP = 2.dp
private val CURSOR_DOT = 4.dp
private val CURSOR_RING = 1.5.dp
private val CURSOR_SHADOW = 7.dp
private const val CURSOR_ARM_UNITS = 3
private const val DEAD_ZONE_SHARE = 0.45f
private const val KNOB_OMEGA = 95f
private const val MAX_SPRING_STEP = 0.1f
private const val SETTLE_EPSILON = 1e-4f
private const val BAND_ALPHA = 0.55f
private const val BAND_THIN = 0.35f
private const val BAND_PIXEL_ROOT = 4f
private const val BAND_PIXEL_TIP = 2f
private val BAND_ROOT = 7.dp
private val BAND_TIP = 2.5.dp

private val PopEasing = CubicBezierEasing(0.0f, 0.55f, 0.45f, 1.0f)

@Composable
fun RadialWheel(
    entries: List<RadialWheelEntry>,
    session: WheelSession,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    below: (@Composable () -> Unit)? = null,
) {
    val reduced = Motion.reduced
    val visibility = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(session.closing) {
        val target = if (session.closing) 0f else 1f
        if (Motion.reduced) visibility.snapTo(target)
        else visibility.animateTo(target, tween(WheelOverlay.FADE_MILLIS, easing = PopEasing))
    }
    val square = isMinecraftTheme
    val glass = liquidGlassTheme && hudBackdropGlass
    val ratio = hudPixelRatio
    val shape: Shape = if (square) RectangleShape else CircleShape

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val count = entries.size
        val sector = if (count == 0) 0f else 360f / count
        val diameter = (min(maxWidth, maxHeight) * WINDOW_SHARE).coerceIn(MIN_DIAMETER, MAX_DIAMETER)
        val hub = diameter * HUB_SHARE
        val outer = diameter / 2
        val inner = hub / 2 + HUB_GAP
        val hubHalf = hub.value / 2f
        val active = session.selected
        SideEffect {
            val flags = entries.map { it.enabled }
            val dead = hubHalf * DEAD_ZONE_SHARE
            if (session.radius != outer.value || session.square != square || session.deadZone != dead || session.enabled != flags) {
                session.radius = outer.value
                session.square = square
                session.deadZone = dead
                session.enabled = flags
                session.steer(Offset.Zero)
            }
        }
        Box(
            Modifier.size(diameter).graphicsLayer {
                val progress = visibility.value
                val scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * progress
                scaleX = scale
                scaleY = scale
                alpha = progress
            },
            contentAlignment = Alignment.Center,
        ) {
            val pops = hoverPops(entries, active, reduced)
            if (glass && fitsGlass(outer, ratio)) Box(Modifier.size(diameter).liquidGlass(shape, frosted = true))
            WheelRing(entries, pops, sector, outer, inner, glass, square)
            entries.forEachIndexed { i, entry ->
                WheelSlot(entry, i == active, sector, i, pops[i], outer, inner, square, ratio)
            }
            WheelHub(entries.getOrNull(active), title, subtitle, hub, shape, glass && fitsGlass(hub / 2, ratio), square, ratio)
            WheelCursor(session, square, hub / 2, outer, Modifier.matchParentSize())
        }
        if (below != null) {
            Box(Modifier.offset(y = outer + BELOW_GAP).graphicsLayer { alpha = visibility.value }) { below() }
        }
    }
}

@Composable
private fun hoverPops(entries: List<RadialWheelEntry>, active: Int, reduced: Boolean): List<State<Float>> = entries.indices.map { i ->
    animateFloatAsState(
        if (i == active) 1f else 0f,
        if (reduced) snap() else tween(HOVER_MILLIS),
    )
}

@Composable
private fun fitsGlass(radius: Dp, ratio: Float): Boolean = with(LocalDensity.current) { radius.toPx() * ratio <= MAX_GLASS_RADIUS_PX }

@Composable
private fun wheelFont(size: TextUnit, square: Boolean, ratio: Float): TextUnit {
    if (!square) return size
    val density = LocalDensity.current
    val pxPerSp = density.density * density.fontScale * ratio
    val scale = max(1, (size.value * pxPerSp / GLYPH_EM).roundToInt())
    return (scale * GLYPH_EM / pxPerSp).sp
}

private fun midAngle(index: Int, sector: Float): Double = Math.toRadians(index * sector - 90.0)

private fun squareReach(angle: Double): Float = max(abs(cos(angle)), abs(sin(angle))).toFloat()

@Composable
private fun WheelRing(entries: List<RadialWheelEntry>, pops: List<State<Float>>, sector: Float, outer: Dp, inner: Dp, glass: Boolean, square: Boolean) {
    val theme = OneConfigTheme.current
    val accent = OneConfigTheme.accent
    val surface = if (glass) PanelSurface else theme.popupBackground.copy(alpha = SURFACE_ALPHA)
    val empty = surface.copy(alpha = surface.alpha * EMPTY_SURFACE_ALPHA)
    val border = theme.borderColor
    val shadow = theme.shadowColor
    Spacer(
        Modifier.size(outer * 2).drawWithCache {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerPx = if (square) outer.toPx().roundToInt().toFloat() else outer.toPx()
            val innerPx = if (square) inner.toPx().roundToInt().toFloat() else inner.toPx()
            val drop = Offset(0f, SHADOW_DROP.toPx())
            val halo = HALO_STEP.toPx()
            val haloStroke = Stroke(halo)
            val line = if (square) max(1f, 1.dp.toPx().roundToInt().toFloat()) else 1.dp.toPx()
            val stroke = Stroke(line)
            val gap = if (entries.size > 1) WEDGE_GAP.toPx() else 0f
            val pop = HOVER_POP.toPx()
            val shadowFill = shadow.copy(alpha = SHADOW_ALPHA)
            val haloColor = shadow.copy(alpha = HALO_ALPHA)
            val paths = List(entries.size) { i ->
                val mid = midAngle(i, sector)
                val start = mid - Math.toRadians(sector / 2.0)
                val end = mid + Math.toRadians(sector / 2.0)
                if (square) squareWedgePath(center, innerPx, outerPx, start, end, gap / 2f)
                else wedgePath(center, innerPx, outerPx, start, end, gap / 2f, WEDGE_CORNER.toPx())
            }
            val shifts = List(entries.size) { i ->
                val mid = midAngle(i, sector)
                Offset((cos(mid) * pop).toFloat(), (sin(mid) * pop).toFloat())
            }
            onDrawBehind {
                if (square) {
                    if (!glass) drawRect(shadowFill, center + drop - Offset(outerPx, outerPx), Size(outerPx * 2, outerPx * 2))
                    for (ring in 1..HALO_RINGS) {
                        val r = outerPx + ring * halo
                        drawRect(haloColor, center + drop - Offset(r, r), Size(r * 2, r * 2), style = haloStroke)
                    }
                } else {
                    if (!glass) drawCircle(shadowFill, outerPx, center + drop)
                    for (ring in 1..HALO_RINGS) {
                        drawCircle(haloColor, outerPx + ring * halo, center + drop, style = haloStroke)
                    }
                }
                for (i in paths.indices) {
                    val p = pops[i].value
                    val fill = if (entries[i].enabled) lerp(surface, accent, p) else empty
                    val shift = shifts[i]
                    translate(shift.x * p, shift.y * p) {
                        drawPath(paths[i], fill)
                        drawPath(paths[i], if (p > 0.5f) accent else border, style = stroke)
                    }
                }
            }
        },
    )
}

private fun wedgePath(center: Offset, inner: Float, outer: Float, start: Double, end: Double, halfGap: Float, corner: Float): Path {
    fun polar(r: Float, a: Double) = Offset(center.x + (cos(a) * r).toFloat(), center.y + (sin(a) * r).toFloat())
    fun startEdge(r: Float) = polar(r, start + asin((halfGap / r).coerceAtMost(1f).toDouble()))
    fun endEdge(r: Float) = polar(r, end - asin((halfGap / r).coerceAtMost(1f).toDouble()))

    val outerStart = start + asin((halfGap / outer).toDouble())
    val outerEnd = end - asin((halfGap / outer).toDouble())
    val innerStart = start + asin((halfGap / inner).coerceAtMost(1f).toDouble())
    val innerEnd = end - asin((halfGap / inner).coerceAtMost(1f).toDouble())
    val innerArc = (innerEnd - innerStart).coerceAtLeast(0.0) * inner
    val c = corner.coerceAtMost((outer - inner) / 3f).coerceAtMost((innerArc / 3.0).toFloat()).coerceAtLeast(0f)

    val path = Path()
    fun arc(r: Float, from: Double, to: Double) {
        val steps = max(1, ceil(Math.toDegrees(abs(to - from)) / ARC_STEP_DEG).toInt())
        for (s in 1..steps) {
            val point = polar(r, from + (to - from) * s / steps)
            path.lineTo(point.x, point.y)
        }
    }

    val begin = startEdge(inner + c)
    path.moveTo(begin.x, begin.y)
    startEdge(outer - c).let { path.lineTo(it.x, it.y) }
    val outerA = outerStart + c / outer
    val outerB = outerEnd - c / outer
    startEdge(outer).let { control -> polar(outer, outerA).let { path.quadraticTo(control.x, control.y, it.x, it.y) } }
    arc(outer, outerA, outerB)
    endEdge(outer).let { control -> endEdge(outer - c).let { path.quadraticTo(control.x, control.y, it.x, it.y) } }
    endEdge(inner + c).let { path.lineTo(it.x, it.y) }
    val innerB = innerEnd - c / inner
    val innerA = innerStart + c / inner
    endEdge(inner).let { control -> polar(inner, innerB).let { path.quadraticTo(control.x, control.y, it.x, it.y) } }
    arc(inner, innerB, innerA)
    startEdge(inner).let { control -> path.quadraticTo(control.x, control.y, begin.x, begin.y) }
    path.close()
    return path
}

private fun squareWedgePath(center: Offset, inner: Float, outer: Float, start: Double, end: Double, halfGap: Float): Path {
    fun cut(half: Float, angle: Double, shift: Float): Offset {
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        val nx = -dy * shift
        val ny = dx * shift
        var t = Float.MAX_VALUE
        if (abs(dx) > 1e-6f) t = minOf(t, ((if (dx > 0f) half else -half) - nx) / dx)
        if (abs(dy) > 1e-6f) t = minOf(t, ((if (dy > 0f) half else -half) - ny) / dy)
        return Offset(center.x + nx + dx * t, center.y + ny + dy * t)
    }
    fun corner(half: Float, angle: Double) =
        Offset(center.x + sign(cos(angle)).toFloat() * half, center.y + sign(sin(angle)).toFloat() * half)

    val first = floor((start - EIGHTH) / QUARTER).toInt() + 1
    val corners = generateSequence(first) { it + 1 }.map { EIGHTH + it * QUARTER }.takeWhile { it < end }.toList()

    val path = Path()
    cut(outer, start, halfGap).let { path.moveTo(it.x, it.y) }
    for (a in corners) corner(outer, a).let { path.lineTo(it.x, it.y) }
    cut(outer, end, -halfGap).let { path.lineTo(it.x, it.y) }
    cut(inner, end, -halfGap).let { path.lineTo(it.x, it.y) }
    for (a in corners.asReversed()) corner(inner, a).let { path.lineTo(it.x, it.y) }
    cut(inner, start, halfGap).let { path.lineTo(it.x, it.y) }
    path.close()
    return path
}

@Composable
private fun WheelSlot(
    entry: RadialWheelEntry,
    hovered: Boolean,
    sector: Float,
    index: Int,
    pop: State<Float>,
    outer: Dp,
    inner: Dp,
    square: Boolean,
    ratio: Float,
) {
    val theme = OneConfigTheme.current
    val ink = when {
        hovered -> theme.accentTextColor
        entry.enabled -> theme.textColor
        else -> theme.textColorSecondary
    }
    val mid = midAngle(index, sector)
    val dirX = cos(mid).toFloat()
    val dirY = sin(mid).toFloat()
    val base = (outer + inner) / 2
    val radius = if (square) base / squareReach(mid) else base
    val chord = base * (2.0 * sin(PI / max(2.0, 360.0 / sector))).toFloat()
    val width = min(chord - WEDGE_GAP * 2 - 10.dp, (outer - inner) * 1.4f).coerceAtLeast(40.dp)
    val labelSize = wheelFont(13.sp, square, ratio)
    Column(
        modifier = Modifier
            .offset {
                val r = radius.toPx()
                IntOffset((dirX * r).roundToInt(), (dirY * r).roundToInt())
            }
            .width(width)
            .graphicsLayer {
                val p = pop.value
                val shift = HOVER_POP.toPx() * p
                translationX = dirX * shift
                translationY = dirY * shift
                val scale = 1f + HOVER_SCALE * p
                scaleX = scale
                scaleY = scale
                alpha = if (entry.enabled) 1f else DISABLED_ALPHA + 0.15f
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        val draw = entry.drawIcon
        if (draw != null) WheelIcon(draw, ink, square) else entry.icon?.let { ThemedIcon(it, ink, ICON_SIZE) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            entry.dot?.let { color ->
                Box(Modifier.size(6.dp).clip(theme.circleShape).background(color))
            }
            SocialText(
                entry.label,
                labelSize,
                modifier = Modifier.widthIn(max = width),
                color = ink,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WheelIcon(draw: DrawScope.(Size, Color) -> Unit, ink: Color, square: Boolean) {
    val origin = remember { FloatArray(2) }
    Spacer(
        Modifier
            .size(ICON_SIZE)
            .then(if (square) Modifier.onPlaced { val at = it.positionInRoot(); origin[0] = at.x; origin[1] = at.y } else Modifier)
            .drawBehind {
                if (square) {
                    translate(origin[0].roundToInt() - origin[0], origin[1].roundToInt() - origin[1]) { draw(size, ink) }
                } else {
                    draw(size, ink)
                }
            },
    )
}

@Composable
private fun WheelHub(
    hovered: RadialWheelEntry?,
    title: String,
    subtitle: String?,
    hub: Dp,
    shape: Shape,
    glass: Boolean,
    square: Boolean,
    ratio: Float,
) {
    val theme = OneConfigTheme.current
    val surface = if (glass) PanelSurface else theme.popupBackground.copy(alpha = HUB_ALPHA)
    val headline = hovered?.label ?: title
    val detail = if (hovered != null) hovered.sublabel else subtitle
    Box(
        modifier = Modifier
            .size(hub)
            .then(if (glass) Modifier.liquidGlass(shape, frosted = true) else Modifier)
            .clip(shape)
            .background(surface)
            .border(1.dp, theme.borderColor, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SocialText(
                headline,
                wheelFont(14.sp, square, ratio),
                color = theme.textColor,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail != null) {
                SocialText(
                    detail,
                    wheelFont(11.sp, square, ratio),
                    color = theme.textColorSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SocialText(
                CANCEL_HINT,
                wheelFont(9.sp, square, ratio),
                color = theme.textColorSecondary.copy(alpha = theme.textColorSecondary.alpha * HINT_ALPHA),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private class KnobSpring {
    var position by mutableStateOf(Offset.Zero)
    private var velocity = Offset.Zero

    fun step(target: Offset, dt: Float) {
        val y = position - target
        if (y.getDistanceSquared() < SETTLE_EPSILON && velocity.getDistanceSquared() < SETTLE_EPSILON) {
            if (position != target) position = target
            velocity = Offset.Zero
            return
        }
        val decay = exp(-KNOB_OMEGA * dt)
        val drive = velocity + y * KNOB_OMEGA
        position = target + (y + drive * dt) * decay
        velocity = (velocity - drive * (KNOB_OMEGA * dt)) * decay
    }
}

@Composable
private fun WheelCursor(session: WheelSession, square: Boolean, hubRadius: Dp, outer: Dp, modifier: Modifier) {
    val accent = OneConfigTheme.accent
    val shadow = OneConfigTheme.current.shadowColor
    val outline = Color.White.copy(alpha = CURSOR_OUTLINE_ALPHA)
    val band = accent.copy(alpha = accent.alpha * BAND_ALPHA)
    val reduced = Motion.reduced
    val knob = remember(session) { KnobSpring() }
    val path = remember { Path() }
    LaunchedEffect(session, reduced) {
        if (reduced) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            withFrameNanos {
                val now = System.nanoTime()
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, MAX_SPRING_STEP)
                last = now
                knob.step(if (session.closing) Offset.Zero else session.pointer, dt)
            }
        }
    }
    Spacer(
        modifier.drawBehind {
            val at = if (reduced) session.pointer else knob.position
            val center = Offset(size.width / 2f, size.height / 2f)
            val tip = Offset(at.x.dp.toPx(), at.y.dp.toPx())
            val hubPx = hubRadius.toPx()
            val stretch = if (reduced) 0f else ((tip.getDistance() - hubPx) / (outer.toPx() - hubPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
            if (square) drawPixelBand(path, center, tip, hubPx, stretch, band) else drawRoundBand(path, center, tip, hubPx, stretch, band)
            val x = center.x + tip.x
            val y = center.y + tip.y
            if (square) drawPixelCursor(x, y, accent, shadow) else drawRoundCursor(x, y, accent, outline, shadow)
        },
    )
}

private fun bandStart(tip: Offset, hubPx: Float, square: Boolean): Offset? {
    val length = tip.getDistance()
    if (length < 1e-3f) return null
    val dir = tip / length
    val edge = if (square) hubPx / max(abs(dir.x), abs(dir.y)) else hubPx
    return if (length > edge) dir * edge else null
}

private fun DrawScope.drawRoundBand(path: Path, center: Offset, tip: Offset, hubPx: Float, stretch: Float, color: Color) {
    val start = bandStart(tip, hubPx, false) ?: return
    val dir = tip / tip.getDistance()
    val normal = Offset(-dir.y, dir.x)
    val root = BAND_ROOT.toPx() * (1f - BAND_THIN * stretch) / 2f
    val end = BAND_TIP.toPx() / 2f
    val a = center + start
    val b = center + tip
    path.reset()
    path.moveTo(a.x + normal.x * root, a.y + normal.y * root)
    path.lineTo(b.x + normal.x * end, b.y + normal.y * end)
    path.lineTo(b.x - normal.x * end, b.y - normal.y * end)
    path.lineTo(a.x - normal.x * root, a.y - normal.y * root)
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawPixelBand(path: Path, center: Offset, tip: Offset, hubPx: Float, stretch: Float, color: Color) {
    val start = bandStart(tip, hubPx, true) ?: return
    val u = max(1f, 1.dp.toPx().roundToInt().toFloat())
    val a = center + start
    val b = center + tip
    val length = (b - a).getDistance()
    val steps = max(1, (length / u).toInt())
    val root = BAND_PIXEL_ROOT * (1f - BAND_THIN * stretch)
    path.reset()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val units = max(1, (root + (BAND_PIXEL_TIP - root) * t).roundToInt())
        val side = units * u
        val p = a + (b - a) * t
        val x = ((p.x - side / 2f) / u).roundToInt() * u
        val y = ((p.y - side / 2f) / u).roundToInt() * u
        path.addRect(Rect(x, y, x + side, y + side))
    }
    drawPath(path, color)
}

private fun DrawScope.drawRoundCursor(x: Float, y: Float, fill: Color, outline: Color, shadow: Color) {
    val center = Offset(x, y)
    val dot = CURSOR_DOT.toPx()
    val ring = CURSOR_RING.toPx()
    drawCircle(shadow.copy(alpha = CURSOR_SHADOW_ALPHA * 0.5f), CURSOR_SHADOW.toPx(), center + Offset(0f, ring))
    drawCircle(shadow.copy(alpha = CURSOR_SHADOW_ALPHA), dot + ring * 2f, center + Offset(0f, ring * 0.5f))
    drawCircle(outline, dot + ring, center)
    drawCircle(fill, dot, center)
}

private fun DrawScope.drawPixelCursor(x: Float, y: Float, fill: Color, shadow: Color) {
    val u = max(1f, 1.dp.toPx().roundToInt().toFloat())
    val cx = (x / u).roundToInt() * u
    val cy = (y / u).roundToInt() * u
    val arm = CURSOR_ARM_UNITS * u
    fun cross(ox: Float, oy: Float, thick: Float, reach: Float, color: Color) {
        drawRect(color, Offset(ox - reach, oy - thick / 2f), Size(reach * 2, thick))
        drawRect(color, Offset(ox - thick / 2f, oy - reach), Size(thick, reach * 2))
    }
    cross(cx + u, cy + u, u * 4, arm + u * 2, shadow.copy(alpha = CURSOR_SHADOW_ALPHA))
    cross(cx, cy, u * 4, arm + u * 2, Color.Black.copy(alpha = CURSOR_OUTLINE_ALPHA))
    cross(cx, cy, u * 2, arm + u, Color.White)
    drawRect(fill, Offset(cx - u, cy - u), Size(u * 2, u * 2))
}
