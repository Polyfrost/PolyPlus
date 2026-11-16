@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.pausescreen.asset
import org.polyfrost.polyplus.client.gui.pausescreen.rememberFrame
import java.time.Duration
import java.time.LocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val CLOCK_RADIUS = 16.dp
private val CLOCK_PADDING_X = 16.dp
private val CLOCK_PADDING_Y = 14.dp
private val CLOCK_TIME_TEXT = 32.sp
private val CLOCK_TIME_LINE = 36.sp
private val CLOCK_TIME_TRACKING = (-0.8).sp
private val CLOCK_MARKER_TEXT = 12.sp
private val CLOCK_MARKER_RAISE = 6.dp
private val CLOCK_DATE_TEXT = 12.sp
private val SKY_WIDTH = 112.dp
private val SKY_HEIGHT = 50.dp
private val SKY_TOP = 12.dp
private val SKY_END = 12.dp
private val SKY_GAP = 8.dp
private val SKY_DOT = 1.1.dp
private val SKY_HORIZON = 1.dp
private val SUN_RADIUS = 5.dp
private val SUN_HALO = 13.dp
private val MOON_RADIUS = 5.5.dp
private val STAR_RADIUS = 0.9.dp
private val SECONDS_THICKNESS = 2.dp
private val SECONDS_BOTTOM = 5.dp
private const val SKY_DOTS = 15
private const val CLOCK_FADE_MILLIS = 180
private const val CLOCK_DATE_SKELETON = "MMMEd"
private val WEEKDAY = Regex("([Ec])\\1*[.]?")
private const val CLOCK_DATE_FALLBACK = "EEEE, d MMM"
private const val CLOCK_ICON = "clock"
private const val CLOCK_ICON_FALLBACK = "moon-star"

private const val SUNRISE = 360f
private const val SUNSET = 1080f
private const val DAY_MINUTES = 1440f
private const val WASH_HEAD = 0.42f
private const val WASH_MID = 0.18f
private const val WASH_TAIL = 0.04f
private const val WASH_SHIFT_MAX = 0.55f
private const val ARC_TRACK_ALPHA = 0.16f
private const val ARC_TRAVELED_ALPHA = 0.34f
private const val HORIZON_ALPHA = 0.1f
private const val HALO_ALPHA = 0.38f
private const val SECONDS_TRACK_ALPHA = 0.06f
private const val SECONDS_FILL_ALPHA = 0.45f

private val NIGHT_HEAD = Color(0xFF34408C)
private val NIGHT_TAIL = Color(0xFF1C2150)
private val DAWN_HEAD = Color(0xFFF4A27E)
private val DAWN_TAIL = Color(0xFF9A86C8)
private val DAY_HEAD = Color(0xFF68B4F0)
private val DAY_TAIL = Color(0xFFA6D6F2)
private val DUSK_HEAD = Color(0xFFF0804F)
private val DUSK_TAIL = Color(0xFFB15E9C)
private val SUN_LOW = Color(0xFFFF8C4A)
private val SUN_HIGH = Color(0xFFFFC94D)
private val MOON_DARK_THEME = Color(0xFFE7EAF6)
private val MOON_LIGHT_THEME = Color(0xFF5E6A9C)

private class SkyStop(val minute: Float, val head: Color, val tail: Color)

private class SkyTone(val head: Color, val tail: Color)

private val SKY = listOf(
    SkyStop(0f, NIGHT_HEAD, NIGHT_TAIL),
    SkyStop(270f, NIGHT_HEAD, NIGHT_TAIL),
    SkyStop(SUNRISE, DAWN_HEAD, DAWN_TAIL),
    SkyStop(510f, DAY_HEAD, DAY_TAIL),
    SkyStop(960f, DAY_HEAD, DAY_TAIL),
    SkyStop(SUNSET, DUSK_HEAD, DUSK_TAIL),
    SkyStop(1185f, NIGHT_HEAD, NIGHT_TAIL),
    SkyStop(DAY_MINUTES, NIGHT_HEAD, NIGHT_TAIL),
)

private val STARS = listOf(Offset(0.18f, 0.22f), Offset(0.42f, 0.08f), Offset(0.7f, 0.3f), Offset(0.88f, 0.12f))

object ClockWidget : ShellWidget {
    override val id = WidgetMigration.CLOCK
    override val title = "Clock"
    override val icon: String by lazy { clockIcon() }
    override val size = WidgetSize.BLOCK
    override val surfaces = setOf(WidgetSurface.LOBBY, WidgetSurface.PAUSE)
    override val bleed = true

    @Composable
    override fun Content(scope: WidgetScope) = ClockCard(scope.reducedMotion)
}

private fun clockIcon(): String {
    val path = asset(CLOCK_ICON)
    val found = runCatching { ClockWidget::class.java.getResource("/$path") != null }.getOrDefault(false)
    return if (found) path else asset(CLOCK_ICON_FALLBACK)
}

private class ClockFormats(locale: Locale) {
    val time: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val date: DateTimeFormatter = runCatching {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(CLOCK_DATE_SKELETON, IsoChronology.INSTANCE, locale)
        DateTimeFormatter.ofPattern(pattern.replace(WEEKDAY) { it.groupValues[1].repeat(4) }, locale)
    }.getOrElse { DateTimeFormatter.ofPattern(CLOCK_DATE_FALLBACK, locale) }
    val marker: DateTimeFormatter? = runCatching {
        val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale)
        if ('a' in pattern) DateTimeFormatter.ofPattern("a", locale) else null
    }.getOrNull()
}

private class TimeParts(val digits: String, val marker: String, val markerFirst: Boolean)

private fun timeParts(formats: ClockFormats, minute: LocalDateTime): TimeParts {
    val text = plainSpaces(runCatching { formats.time.format(minute) }.getOrDefault(""))
    val marker = formats.marker?.let { plainSpaces(runCatching { it.format(minute) }.getOrDefault("")).trim() }.orEmpty()
    val at = if (marker.isEmpty()) -1 else text.indexOf(marker)
    if (at < 0) return TimeParts(text.trim(), "", false)
    val digits = text.removeRange(at, at + marker.length).trim()
    if (digits.isEmpty()) return TimeParts(text.trim(), "", false)
    return TimeParts(digits, marker, at < text.length / 2)
}

private fun plainSpaces(text: String): String = text.replace('\u202F', ' ').replace('\u00A0', ' ')

private fun minuteNow(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

@Composable
private fun rememberMinute(): State<LocalDateTime> {
    val minute = remember { mutableStateOf(minuteNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            minute.value = now.truncatedTo(ChronoUnit.MINUTES)
            val next = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(Duration.between(now, next).toMillis().coerceAtLeast(1L))
        }
    }
    return minute
}

private fun minuteOfDay(time: LocalDateTime): Float = time.hour * 60f + time.minute

private fun skyAt(minute: Float): SkyTone {
    val index = SKY.indexOfLast { it.minute <= minute }.coerceIn(0, SKY.size - 2)
    val from = SKY[index]
    val to = SKY[index + 1]
    val t = ((minute - from.minute) / (to.minute - from.minute)).coerceIn(0f, 1f)
    return SkyTone(lerp(from.head, to.head, t), lerp(from.tail, to.tail, t))
}

private fun nightAmount(minute: Float): Float = when {
    minute < 270f -> 1f
    minute < SUNRISE -> 1f - (minute - 270f) / (SUNRISE - 270f)
    minute < SUNSET -> 0f
    minute < 1185f -> (minute - SUNSET) / (1185f - SUNSET)
    else -> 1f
}

private fun readable(color: Color, lightTheme: Boolean): Color {
    val lum = color.luminance()
    return if (!lightTheme) {
        lerp(color, Color.Black, ((lum - 0.35f) / 0.65f).coerceIn(0f, 1f) * WASH_SHIFT_MAX)
    } else {
        lerp(color, Color.White, ((0.65f - lum) / 0.65f).coerceIn(0f, 1f) * WASH_SHIFT_MAX)
    }
}

@Composable
private fun ClockCard(reduced: Boolean) {
    val formats = remember { ClockFormats(Locale.getDefault(Locale.Category.FORMAT)) }
    val minute = rememberMinute().value
    val parts = remember(minute) { timeParts(formats, minute) }
    val date = remember(minute.toLocalDate()) { plainSpaces(runCatching { formats.date.format(minute) }.getOrDefault("")) }
    val ofDay = minuteOfDay(minute)
    val lightTheme = OneConfigTheme.current.isLight
    val sky = remember(ofDay, lightTheme) { skyAt(ofDay).let { SkyTone(readable(it.head, lightTheme), readable(it.tail, lightTheme)) } }
    val day = ofDay >= SUNRISE && ofDay < SUNSET
    val progress = if (day) (ofDay - SUNRISE) / (SUNSET - SUNRISE) else ((ofDay - SUNSET).mod(DAY_MINUTES)) / (DAY_MINUTES - (SUNSET - SUNRISE))
    val glyph = if (day) lerp(SUN_LOW, SUN_HIGH, sin(PI * progress).toFloat()) else if (lightTheme) MOON_LIGHT_THEME else MOON_DARK_THEME
    val ink = SocialTextPrimary
    val muted = OneConfigTheme.current.mutedText
    Box(Modifier.fillMaxWidth().height(WidgetSize.BLOCK.height).clip(OneConfigTheme.shape(CLOCK_RADIUS))) {
        SkyWash(sky)
        SkyArc(progress, day, glyph, ink, nightAmount(ofDay), Modifier.align(Alignment.TopEnd).padding(top = SKY_TOP, end = SKY_END).size(SKY_WIDTH, SKY_HEIGHT))
        Crossfade(
            parts to date,
            Modifier.fillMaxSize().padding(start = CLOCK_PADDING_X, end = SKY_END + SKY_WIDTH + SKY_GAP, top = CLOCK_PADDING_Y, bottom = CLOCK_PADDING_Y),
            if (reduced) snap() else tween(CLOCK_FADE_MILLIS),
            "Clock",
        ) { (shownParts, shownDate) ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                TimeLine(shownParts, ink, muted)
                ClockText(shownDate, CLOCK_DATE_TEXT, muted, FontWeight.Normal)
            }
        }
        if (!reduced) SecondsLine(glyph, ink, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = CLOCK_PADDING_X, end = CLOCK_PADDING_X, bottom = SECONDS_BOTTOM).height(SECONDS_THICKNESS))
    }
}

@Composable
private fun BoxScope.SkyWash(sky: SkyTone) {
    val head = sky.head.copy(alpha = WASH_HEAD)
    val mid = lerp(sky.head, sky.tail, 0.5f).copy(alpha = WASH_MID)
    val tail = sky.tail.copy(alpha = WASH_TAIL)
    Canvas(Modifier.matchParentSize()) {
        drawRect(Brush.linearGradient(0f to head, 0.5f to mid, 1f to tail, start = Offset(size.width, 0f), end = Offset(size.width * 0.2f, size.height)))
    }
}

@Composable
private fun TimeLine(parts: TimeParts, ink: Color, muted: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (parts.marker.isNotEmpty() && parts.markerFirst) Marker(parts.marker, muted)
        Text(
            parts.digits,
            color = ink,
            style = OneConfigTheme.typography.body.copy(
                fontSize = CLOCK_TIME_TEXT,
                lineHeight = CLOCK_TIME_LINE,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = CLOCK_TIME_TRACKING,
                fontFeatureSettings = "tnum",
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
        if (parts.marker.isNotEmpty() && !parts.markerFirst) Marker(parts.marker, muted)
    }
}

@Composable
private fun Marker(text: String, color: Color) {
    ClockText(text, CLOCK_MARKER_TEXT, color, FontWeight.Medium, Modifier.padding(top = CLOCK_MARKER_RAISE))
}

@Composable
private fun ClockText(text: String, size: TextUnit, color: Color, weight: FontWeight, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        color = color,
        style = OneConfigTheme.typography.body.copy(fontSize = size, fontWeight = weight, fontFeatureSettings = "tnum"),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
    )
}

@Composable
private fun SkyArc(progress: Float, day: Boolean, glyph: Color, ink: Color, night: Float, modifier: Modifier) {
    Canvas(modifier) {
        val inset = SUN_HALO.toPx() * 0.6f
        val horizon = size.height - SKY_HORIZON.toPx()
        val cx = size.width / 2f
        val rx = cx - inset
        val ry = horizon - inset
        fun point(t: Float): Offset {
            val angle = PI * t
            return Offset(cx - rx * cos(angle).toFloat(), horizon - ry * sin(angle).toFloat())
        }
        if (night > 0f) {
            val star = glyph.copy(alpha = 0.35f * night)
            STARS.forEach { drawCircle(star, STAR_RADIUS.toPx(), Offset(it.x * size.width, it.y * size.height + inset * 0.3f)) }
        }
        drawLine(ink.copy(alpha = HORIZON_ALPHA), Offset(0f, horizon), Offset(size.width, horizon), SKY_HORIZON.toPx(), StrokeCap.Round)
        val dot = SKY_DOT.toPx()
        for (i in 0..SKY_DOTS) {
            val t = i / SKY_DOTS.toFloat()
            drawCircle(ink.copy(alpha = if (t <= progress) ARC_TRAVELED_ALPHA else ARC_TRACK_ALPHA), dot, point(t))
        }
        val at = point(progress.coerceIn(0f, 1f))
        if (day) sun(at, glyph) else moon(at, glyph)
    }
}

private fun DrawScope.sun(at: Offset, color: Color) {
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = HALO_ALPHA), color.copy(alpha = 0f)), at, SUN_HALO.toPx()), SUN_HALO.toPx(), at)
    drawCircle(color, SUN_RADIUS.toPx(), at)
}

private fun DrawScope.moon(at: Offset, color: Color) {
    val radius = MOON_RADIUS.toPx()
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = HALO_ALPHA * 0.5f), color.copy(alpha = 0f)), at, SUN_HALO.toPx()), SUN_HALO.toPx(), at)
    val bite = Offset(at.x + radius * 0.55f, at.y - radius * 0.35f)
    val cut = Path().apply { addOval(Rect(bite, radius * 0.9f)) }
    clipPath(cut, ClipOp.Difference) { drawCircle(color, radius, at) }
}

@Composable
private fun SecondsLine(glyph: Color, ink: Color, modifier: Modifier) {
    val frame = rememberFrame()
    Canvas(modifier) {
        frame.value
        val seconds = (System.currentTimeMillis() % 60_000L) / 60_000f
        val y = size.height / 2f
        val stroke = size.height
        drawLine(ink.copy(alpha = SECONDS_TRACK_ALPHA), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        if (seconds > 0f) drawLine(glyph.copy(alpha = SECONDS_FILL_ALPHA), Offset(0f, y), Offset(size.width * seconds, y), stroke, StrokeCap.Round)
    }
}
