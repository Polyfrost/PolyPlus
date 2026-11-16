@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalFoundationApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipChildHover
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import kotlin.math.abs
import kotlin.math.sin
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val READ_MILLIS = 250L
private const val TONE_MILLIS = 450
private const val GLOW_ALPHA = 0.45f
private const val MARQUEE_DELAY = 700
private const val HINT_DWELL_MILLIS = 900L
internal const val MENU_HINT = "Right-click to move or remove"
private val TITLE_SEPARATORS = listOf(" - ", " – ", " — ")
private val DARK_INK = Color(0xFF101114)
private val MARQUEE_SPEED = 22.dp
private const val PANEL_WASH_HEAD = 0.42f
private const val PANEL_WASH_MID = 0.2f
private const val PANEL_WASH_TAIL = 0.06f
private const val WASH_SHIFT_MAX = 0.55f
private const val VIVID_GREY = 0.08f
private const val VIVID_SATURATION = 0.45f
private const val VIVID_LIGHT_MIN = 0.42f
private const val VIVID_LIGHT_MAX = 0.66f
private val PLAYER_PREFIXES = listOf("org.mpris.MediaPlayer2.")
private val PLAYER_NOISE = setOf("com", "org", "net", "io", "app", "apps", "client", "desktop", "player", "android", "media", "instance")
private val PLAYER_NAMES = mapOf(
    "spotify" to "Spotify",
    "spotifymusic" to "Spotify",
    "msedge" to "Edge",
    "edge" to "Edge",
    "chrome" to "Chrome",
    "chromium" to "",
    "cef" to "",
    "firefox" to "Firefox",
    "308046b0af4a39cb" to "Firefox",
    "brave" to "Brave",
    "opera" to "Opera",
    "vivaldi" to "Vivaldi",
    "vlc" to "VLC",
    "zunemusic" to "Media Player",
    "zunevideo" to "Movies & TV",
    "music" to "Music",
    "applemusic" to "Apple Music",
    "itunes" to "iTunes",
    "foobar2000" to "foobar2000",
    "aimp" to "AIMP",
    "mpv" to "mpv",
    "tidal" to "TIDAL",
    "deezer" to "Deezer",
    "youtubemusic" to "YouTube Music",
    "discord" to "Discord",
)

internal data class PauseMediaTrack(
    val title: String,
    val artist: String,
    val player: String,
    val playing: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val sampledAtMs: Long,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canToggle: Boolean,
    val art: PolyBitmap?,
    val artAccent: Int?,
    val artAverage: Int?,
) {
    fun positionAt(nowMs: Long): Long {
        val elapsed = if (playing) (nowMs - sampledAtMs).coerceAtLeast(0L) else 0L
        val position = positionMs + elapsed
        return if (lengthMs > 0) position.coerceAtMost(lengthMs) else position
    }

    val displayTitle: String
        get() {
            val name = artist.trim()
            if (name.isNotEmpty()) {
                for (separator in TITLE_SEPARATORS) {
                    val prefix = name + separator
                    if (title.startsWith(prefix, ignoreCase = true)) {
                        val rest = title.substring(prefix.length).trim()
                        if (rest.isNotEmpty()) return rest
                    }
                }
            }
            return title.ifBlank { playerName }
        }

    val playerName: String
        get() = prettyPlayer(player)
}

internal fun prettyPlayer(raw: String): String {
    var id = raw.trim()
    if (id.isEmpty()) return ""
    id = id.substringAfterLast('!').substringAfterLast('/').substringAfterLast('\\')
    if (id.endsWith(".exe", ignoreCase = true)) id = id.dropLast(4)
    for (prefix in PLAYER_PREFIXES) {
        if (id.startsWith(prefix, ignoreCase = true)) id = id.substring(prefix.length)
    }
    if (' ' !in id && '.' in id) {
        val parts = id.split('.').map { it.substringBefore('_') }.filter { it.isNotBlank() }
        val meaningful = parts.filter { it.lowercase() !in PLAYER_NOISE && !it.lowercase().startsWith("instance") && it.any(Char::isLetter) }
        id = meaningful.firstOrNull { it.lowercase() in PLAYER_NAMES }
            ?: meaningful.lastOrNull()
            ?: parts.firstOrNull()
            ?: id
    } else if (' ' !in id) {
        id = id.substringBefore('_')
    }
    PLAYER_NAMES[id.lowercase()]?.let { return it }
    val words = id.replace('_', ' ').replace('-', ' ').split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return raw.trim()
    return words.joinToString(" ") { word ->
        if (word.all { !it.isLetter() || it.isLowerCase() }) word.replaceFirstChar { it.titlecase() } else word
    }
}

internal class MediaTone(val accent: Color, val average: Color, val presence: Float)

internal enum class MediaGlyph { PREVIOUS, NEXT, PLAY, PAUSE }

private object MediaMenuHint {
    var learned by mutableStateOf(false)
}

internal fun Modifier.learnsMenu(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) MediaMenuHint.learned = true
        }
    }
}

@Composable
internal fun MediaMenuHinted(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TooltipBox(
        if (MediaMenuHint.learned) null else text,
        modifier,
        placement = TooltipPlacement.AboveCenter,
        showOnFocus = false,
        delayMillis = HINT_DWELL_MILLIS,
        hideWhileChildHovered = true,
        content = content,
    )
}

@Composable
internal fun rememberMediaTrack(): PauseMediaTrack? {
    DisposableEffect(Unit) {
        PauseEvergreenMedia.acquire()
        onDispose { PauseEvergreenMedia.release() }
    }
    var track by remember { mutableStateOf(PauseEvergreenMedia.read()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(READ_MILLIS)
            track = PauseEvergreenMedia.read()
        }
    }
    return track
}

@Composable
private fun rememberArt(art: PolyBitmap?): ImageBitmap? =
    remember(art) { art?.let { runCatching { it.asComposeImageBitmap() }.getOrNull() } }

@Composable
internal fun rememberClock(playing: Boolean): State<Long> {
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing) {
        now.longValue = System.currentTimeMillis()
        while (playing) {
            delay(READ_MILLIS)
            now.longValue = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
internal fun rememberTone(track: PauseMediaTrack?): MediaTone {
    val fallback = OneConfigTheme.accent
    val accent = track?.artAccent?.let(::Color) ?: fallback
    val average = track?.artAverage?.let(::Color) ?: accent
    val animatedAccent by Motion.animateColorAsState(accent, tween(TONE_MILLIS))
    val animatedAverage by Motion.animateColorAsState(average, tween(TONE_MILLIS))
    val presence by Motion.animateFloatAsState(if (track != null) 1f else 0f, tween(TONE_MILLIS))
    return MediaTone(animatedAccent, animatedAverage, presence)
}

private fun inkOn(fill: Color): Color = if (fill.luminance() > 0.45f) DARK_INK else Color.White

internal fun mediaClock(ms: Long): String = clock(ms)

private fun clock(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun readableWash(color: Color, lightTheme: Boolean): Color {
    val lum = color.luminance()
    return if (!lightTheme) {
        lerp(color, Color.Black, ((lum - 0.35f) / 0.65f).coerceIn(0f, 1f) * WASH_SHIFT_MAX)
    } else {
        lerp(color, Color.White, ((0.65f - lum) / 0.65f).coerceIn(0f, 1f) * WASH_SHIFT_MAX)
    }
}

private fun hsl(color: Color): FloatArray {
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val lightness = (max + min) / 2f
    val delta = max - min
    if (delta <= 0f) return floatArrayOf(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (max) {
        color.red -> 60f * (((color.green - color.blue) / delta).mod(6f))
        color.green -> 60f * ((color.blue - color.red) / delta + 2f)
        else -> 60f * ((color.red - color.green) / delta + 4f)
    }
    return floatArrayOf(hue, saturation.coerceIn(0f, 1f), lightness)
}

internal fun vivid(color: Color, fallback: Color): Color {
    val (hue, saturation, lightness) = hsl(color)
    val source = if (saturation < VIVID_GREY) hsl(fallback) else floatArrayOf(hue, saturation, lightness)
    return Color.hsl(
        source[0],
        source[1].coerceAtLeast(VIVID_SATURATION),
        lightness.coerceIn(VIVID_LIGHT_MIN, VIVID_LIGHT_MAX),
        color.alpha,
    )
}

@Composable
internal fun BoxScope.PanelWash(tone: MediaTone) {
    if (tone.presence <= 0f) return
    val lightTheme = OneConfigTheme.current.isLight
    val head = readableWash(tone.accent, lightTheme).copy(alpha = PANEL_WASH_HEAD * tone.presence)
    val mid = readableWash(lerp(tone.accent, tone.average, 0.5f), lightTheme).copy(alpha = PANEL_WASH_MID * tone.presence)
    val tail = readableWash(tone.average, lightTheme).copy(alpha = PANEL_WASH_TAIL * tone.presence)
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to head, 0.45f to mid, 1f to tail)))
}

@Composable
internal fun Title(text: String, size: TextUnit, scroll: Boolean) {
    if (scroll) {
        Text(
            text,
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = MARQUEE_DELAY, velocity = MARQUEE_SPEED),
            style = OneConfigTheme.current.typography.body.copy(fontSize = size, fontWeight = FontWeight.Medium),
            maxLines = 1,
            softWrap = false,
        )
    } else {
        Text(text, style = OneConfigTheme.current.typography.body.copy(fontSize = size, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

internal fun DrawScope.equalizer(color: Color, frameNanos: Long) {
    val seconds = frameNanos / 1_000_000_000.0
    val bar = size.width / 4.2f
    val gap = (size.width - bar * 3f) / 2f
    for (i in 0 until 3) {
        val level = if (frameNanos == 0L) {
            STATIC_LEVELS[i]
        } else {
            0.25f + 0.75f * abs(sin(seconds * EQ_SPEEDS[i] + EQ_PHASES[i])).toFloat()
        }
        val h = size.height * level
        drawRoundRect(
            color,
            topLeft = Offset(i * (bar + gap), size.height - h),
            size = Size(bar, h),
            cornerRadius = CornerRadius(bar / 2f),
        )
    }
}

private val STATIC_LEVELS = floatArrayOf(0.55f, 0.9f, 0.7f)
private val EQ_SPEEDS = doubleArrayOf(5.1, 6.7, 4.3)
private val EQ_PHASES = doubleArrayOf(0.0, 1.3, 2.4)

@Composable
internal fun Artwork(art: PolyBitmap?, size: Dp, radius: Dp, tone: MediaTone) {
    val bitmap = rememberArt(art)
    val shape = OneConfigTheme.shape(radius)
    if (bitmap != null) {
        val glow = tone.accent.copy(alpha = GLOW_ALPHA * tone.presence)
        Box(
            Modifier.size(size).drawBehind {
                val center = Offset(this.size.width / 2f, this.size.height * 0.58f)
                val reach = this.size.minDimension * 0.78f
                drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center, reach), reach, center)
            },
        ) {
            Image(bitmap, null, Modifier.matchParentSize().clip(shape), contentScale = ContentScale.Crop)
        }
    } else {
        Box(
            Modifier.size(size).background(SocialTextPrimary.copy(alpha = 0.05f), shape).border(1.dp, SocialBorderColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            PauseGlyph(asset("headphones-02"), OneConfigTheme.current.mutedText, size * 0.42f)
        }
    }
}

internal fun fraction(track: PauseMediaTrack, now: Long): Float =
    if (track.lengthMs > 0) (track.positionAt(now).toFloat() / track.lengthMs).coerceIn(0f, 1f) else 0f

@Composable
internal fun SkipButton(glyph: MediaGlyph, tooltip: String, size: Dp, iconSize: Dp, enabled: Boolean, onClick: () -> Unit) {
    val hoverState = rememberHoverState()
    TooltipChildHover(hoverState.hovered && enabled)
    IconButton(
        contentDescription = tooltip,
        onClick = onClick,
        enabled = enabled,
        tooltip = tooltip.takeIf { enabled },
        tooltipPlacement = TooltipPlacement.AboveCenter,
        interactionSource = hoverState.interactionSource,
        shape = OneConfigTheme.shape(if (size > 28.dp) 10.dp else 8.dp),
        frameSize = size,
        focusRing = false,
    ) { tint ->
        Glyph(glyph, tint, iconSize)
    }
}

@Composable
internal fun PlayButton(playing: Boolean, active: Boolean, tone: MediaTone, size: Dp, iconSize: Dp, enabled: Boolean) {
    val hoverState = rememberHoverState()
    val interaction = hoverState.interactionSource
    val hovered = hoverState.hovered
    val lit = hovered && enabled
    val disabledAlpha = OneConfigTheme.current.disabledAlpha
    TooltipChildHover(lit)
    val base =if (active) vivid(tone.accent, OneConfigTheme.accent) else SocialTextPrimary.copy(alpha = 0.08f)
    val fill = if (lit && active) lerp(base, Color.White, 0.16f) else base
    val ink = if (active) inkOn(fill) else OneConfigTheme.current.mutedText
    val label = if (playing) "Pause" else "Play"
    TooltipBox(label.takeIf { enabled }, placement = TooltipPlacement.AboveCenter) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    alpha = if (enabled) 1f else disabledAlpha
                    val scale = if (lit) 1.06f else 1f
                    scaleX = scale
                    scaleY = scale
                }
                .background(fill, OneConfigTheme.current.circleShape)
                .hoverable(interaction)
                .then(if (enabled) Modifier.oneConfigClickable(label, PauseEvergreenMedia::toggle, indication = false, interactionSource = interaction, focusRing = false) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(if (playing) MediaGlyph.PAUSE else MediaGlyph.PLAY, ink, iconSize)
        }
    }
}

@Composable
private fun Glyph(glyph: MediaGlyph, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        when (glyph) {
            MediaGlyph.PLAY -> triangle(color, 0.22f, 0.86f, 0.1f, 0.9f, pointRight = true)
            MediaGlyph.PAUSE -> {
                pill(color, 0.2f, 0.1f, 0.22f, 0.8f)
                pill(color, 0.58f, 0.1f, 0.22f, 0.8f)
            }
            MediaGlyph.NEXT -> {
                triangle(color, 0.08f, 0.66f, 0.14f, 0.86f, pointRight = true)
                pill(color, 0.72f, 0.14f, 0.16f, 0.72f)
            }
            MediaGlyph.PREVIOUS -> {
                pill(color, 0.12f, 0.14f, 0.16f, 0.72f)
                triangle(color, 0.34f, 0.92f, 0.14f, 0.86f, pointRight = false)
            }
        }
    }
}

private fun DrawScope.triangle(color: Color, left: Float, right: Float, top: Float, bottom: Float, pointRight: Boolean) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        if (pointRight) {
            moveTo(left * w, top * h)
            lineTo(right * w, (top + bottom) / 2f * h)
            lineTo(left * w, bottom * h)
        } else {
            moveTo(right * w, top * h)
            lineTo(left * w, (top + bottom) / 2f * h)
            lineTo(right * w, bottom * h)
        }
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.pill(color: Color, left: Float, top: Float, width: Float, height: Float) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color,
        topLeft = Offset(left * w, top * h),
        size = Size(width * w, height * h),
        cornerRadius = CornerRadius(width * w / 2.5f),
    )
}
