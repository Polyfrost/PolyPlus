@file:OptIn(ExperimentalOneConfigUiApi::class, ExperimentalFoundationApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.api.pause.PauseWidgetContext
import org.polyfrost.polyplus.api.pause.PauseWidgets
import org.polyfrost.polyplus.client.gui.LIGHT_TEXT_LIMIT
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import kotlin.math.abs
import kotlin.math.sin

private const val EVERGREENHUD_ID = "evergreenhud"
private const val READ_MILLIS = 250L
private const val DISABLED_ALPHA = 0.4f
private const val TONE_MILLIS = 450
private const val WASH_HEAD = 0.24f
private const val WASH_TAIL = 0.08f
private const val GLOW_ALPHA = 0.45f
private const val MARQUEE_DELAY = 700
private val TITLE_SEPARATORS = listOf(" - ", " – ", " — ")
private val DARK_INK = Color(0xFF101114)
private val BAR_ART = 38.dp
private val BAR_TEXT_WIDTH = 140.dp
private val BAR_SKIP = 26.dp
private val BAR_SKIP_ICON = 12.dp
private val BAR_PLAY = 30.dp
private val BAR_PLAY_ICON = 13.dp
private val CARD_ART = 56.dp
private val CARD_SKIP = 32.dp
private val CARD_SKIP_ICON = 14.dp
private val CARD_PLAY = 40.dp
private val CARD_PLAY_ICON = 16.dp
private val BAR_PROGRESS = 3.dp
private val CARD_PROGRESS = 4.dp
private val TILE_RADIUS = 14.dp
private val MARQUEE_SPEED = 22.dp
private const val PANEL_WASH_HEAD = 0.42f
private const val PANEL_WASH_MID = 0.2f
private const val PANEL_WASH_TAIL = 0.06f
private const val WASH_SHIFT_MAX = 0.55f
private val PANEL_HEIGHT = 144.dp
private val PANEL_INLINE_WIDTH = 250.dp
private val PANEL_SKIP = 28.dp
private val PANEL_SKIP_ICON = 12.dp
private val PANEL_PLAY = 36.dp
private val PANEL_PLAY_ICON = 14.dp
private val PANEL_PROGRESS = 4.dp
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

internal object PauseMediaWidgets {
    fun register() {
        if (FabricLoader.getInstance().isModLoaded(EVERGREENHUD_ID)) PauseWidgets.register(MediaWidget)
    }
}

internal object MediaWidget : PauseWidget, FullBleedPanel {
    override val id = "polyplus:media"
    override val title = "Media"
    override val icon = asset("headphones-02")

    override fun isAvailable(): Boolean = PauseEvergreenMedia.supported()

    @Composable
    override fun Bar(context: PauseWidgetContext) = MediaBar(context.reducedMotion)

    @Composable
    override fun Card(context: PauseWidgetContext) = MediaCard(context.reducedMotion)

    override val supportsPanel: Boolean get() = true

    @Composable
    override fun Panel(context: PauseWidgetContext) = MediaPanel(context.reducedMotion)
}

private class MediaTone(val accent: Color, val average: Color, val presence: Float)

private enum class MediaGlyph { PREVIOUS, NEXT, PLAY, PAUSE }

@Composable
private fun rememberTrack(): PauseMediaTrack? {
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
private fun rememberNow(playing: Boolean): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playing) {
        now = System.currentTimeMillis()
        while (playing) {
            delay(READ_MILLIS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun rememberTone(track: PauseMediaTrack?, reduced: Boolean): MediaTone {
    val fallback = OneConfigTheme.accent
    val accent = track?.artAccent?.let(::Color) ?: fallback
    val average = track?.artAverage?.let(::Color) ?: accent
    val colorSpec: AnimationSpec<Color> = if (reduced) snap() else tween(TONE_MILLIS)
    val floatSpec: AnimationSpec<Float> = if (reduced) snap() else tween(TONE_MILLIS)
    val animatedAccent by animateColorAsState(accent, colorSpec)
    val animatedAverage by animateColorAsState(average, colorSpec)
    val presence by animateFloatAsState(if (track != null) 1f else 0f, floatSpec)
    return MediaTone(animatedAccent, animatedAverage, presence)
}

private fun inkOn(fill: Color): Color = if (fill.luminance() > 0.45f) DARK_INK else Color.White

private fun clock(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun BoxScope.Wash(tone: MediaTone, horizontal: Boolean) {
    if (tone.presence <= 0f) return
    val head = tone.accent.copy(alpha = WASH_HEAD * tone.presence)
    val tail = tone.average.copy(alpha = WASH_TAIL * tone.presence)
    val stops = arrayOf(0f to head, 0.55f to tail, 1f to Color.Transparent)
    val brush = if (horizontal) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops)
    Box(Modifier.matchParentSize().background(brush, ppShape(TILE_RADIUS)))
}

private fun readableWash(color: Color, text: Color): Color {
    val lum = color.luminance()
    return if (text.luminance() > LIGHT_TEXT_LIMIT) {
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

private fun vivid(color: Color, fallback: Color): Color {
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
private fun BoxScope.PanelWash(tone: MediaTone) {
    if (tone.presence <= 0f) return
    val text = SocialTextPrimary
    val head = readableWash(tone.accent, text).copy(alpha = PANEL_WASH_HEAD * tone.presence)
    val mid = readableWash(lerp(tone.accent, tone.average, 0.5f), text).copy(alpha = PANEL_WASH_MID * tone.presence)
    val tail = readableWash(tone.average, text).copy(alpha = PANEL_WASH_TAIL * tone.presence)
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to head, 0.45f to mid, 1f to tail)))
}

@Composable
private fun PanelArt(art: PolyBitmap?) {
    val bitmap = rememberArt(art)
    val modifier = Modifier.fillMaxHeight().aspectRatio(1f)
    if (bitmap != null) {
        Image(bitmap, null, modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(SocialTextPrimary.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            PauseGlyph(asset("headphones-02"), MutedText, 40.dp)
        }
    }
}

@Composable
private fun PanelMeta(track: PauseMediaTrack, accent: Color, reduced: Boolean) {
    val artist = track.artist.ifBlank { track.playerName }
    val player = track.playerName.takeIf { it.isNotBlank() && track.artist.isNotBlank() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (artist.isNotBlank()) Label(artist, 12.sp, Modifier.weight(1f, fill = false), color = MutedText)
        if (track.playing) {
            if (reduced) Equalizer(accent, 0L) else LiveEqualizer(accent)
        }
        if (player != null) {
            Box(
                Modifier
                    .background(SocialTextPrimary.copy(alpha = 0.08f), ppShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Label(player, 10.sp, color = MutedText, weight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PanelProgress(track: PauseMediaTrack, accent: Color, modifier: Modifier = Modifier) {
    val now = rememberNow(track.playing)
    val elapsed = clock(track.positionAt(now))
    if (track.lengthMs <= 0) {
        Box(modifier) { TabularLabel(elapsed, 11.sp, MutedText, FontWeight.Normal) }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProgressLine(fraction(track, now), accent, PANEL_PROGRESS)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TabularLabel(elapsed, 10.sp, MutedText, FontWeight.Normal)
            Box(Modifier.weight(1f))
            TabularLabel(clock(track.lengthMs), 10.sp, MutedText, FontWeight.Normal)
        }
    }
}

@Composable
private fun MediaPanel(reduced: Boolean) {
    val track = rememberTrack()
    val tone = rememberTone(track, reduced)
    val accent = vivid(tone.accent, OneConfigTheme.accent)
    val (interaction, hovered) = rememberSocialHover()
    Box(Modifier.fillMaxWidth().height(PANEL_HEIGHT).hoverable(interaction)) {
        PanelWash(tone)
        Row(Modifier.fillMaxSize()) {
            PanelArt(track?.art)
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
                val inline = maxWidth >= PANEL_INLINE_WIDTH
                Column(Modifier.fillMaxSize()) {
                    if (track == null) {
                        Label("Nothing playing", 15.sp, color = MutedText, weight = FontWeight.Medium)
                        Label("Play something in another app", 12.sp, Modifier.padding(top = 3.dp), color = MutedText)
                    } else {
                        Title(track.displayTitle, 15.sp, hovered && !reduced)
                        Box(Modifier.padding(top = 3.dp)) { PanelMeta(track, accent, reduced) }
                    }
                    Spacer(Modifier.weight(1f))
                    if (track != null && !inline) PanelProgress(track, accent, Modifier.fillMaxWidth().padding(bottom = 8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (track != null && inline) {
                            PanelProgress(track, accent, Modifier.weight(1f).padding(end = 14.dp))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Controls(track, tone, PANEL_SKIP, PANEL_SKIP_ICON, PANEL_PLAY, PANEL_PLAY_ICON, 6.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBar(reduced: Boolean) {
    val track = rememberTrack()
    val tone = rememberTone(track, reduced)
    BarTile(glass = false) { hovered ->
        Wash(tone, horizontal = true)
        Row(
            Modifier.padding(start = 1.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Artwork(track?.art, BAR_ART, 8.dp, tone)
            Column(Modifier.width(BAR_TEXT_WIDTH)) {
                if (track == null) {
                    Label("Nothing playing", 13.sp, color = MutedText, weight = FontWeight.Medium)
                } else {
                    Title(track.displayTitle, 13.sp, hovered && !reduced)
                    ArtistLine(track, 11.sp, tone.accent, reduced)
                    val now = rememberNow(track.playing)
                    ProgressLine(fraction(track, now), tone.accent, BAR_PROGRESS, Modifier.padding(top = 4.dp))
                }
            }
            Controls(track, tone, BAR_SKIP, BAR_SKIP_ICON, BAR_PLAY, BAR_PLAY_ICON, 2.dp)
        }
    }
}

@Composable
private fun MediaCard(reduced: Boolean) {
    val track = rememberTrack()
    val tone = rememberTone(track, reduced)
    val (interaction, hovered) = rememberSocialHover()
    Box(Modifier.fillMaxWidth().hoverable(interaction)) {
        Wash(tone, horizontal = false)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Artwork(track?.art, CARD_ART, 12.dp, tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (track == null) {
                        Label("Nothing playing", 14.sp, color = MutedText, weight = FontWeight.Medium)
                        Label("Play something in another app", 12.sp, color = MutedText)
                    } else {
                        Title(track.displayTitle, 14.sp, hovered && !reduced)
                        ArtistLine(track, 12.sp, tone.accent, reduced)
                    }
                }
            }
            if (track != null) {
                val now = rememberNow(track.playing)
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ProgressLine(fraction(track, now), tone.accent, CARD_PROGRESS)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TabularLabel(clock(track.positionAt(now)), 11.sp, MutedText, FontWeight.Normal)
                        Box(Modifier.weight(1f))
                        if (track.lengthMs > 0) TabularLabel(clock(track.lengthMs), 11.sp, MutedText, FontWeight.Normal)
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Controls(track, tone, CARD_SKIP, CARD_SKIP_ICON, CARD_PLAY, CARD_PLAY_ICON, 10.dp)
            }
        }
    }
}

@Composable
private fun Title(text: String, size: TextUnit, scroll: Boolean) {
    if (scroll) {
        SocialText(
            text,
            size,
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = MARQUEE_DELAY, velocity = MARQUEE_SPEED),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    } else {
        Label(text, size, weight = FontWeight.Medium)
    }
}

@Composable
private fun ArtistLine(track: PauseMediaTrack, size: TextUnit, accent: Color, reduced: Boolean) {
    val artist = track.artist.ifBlank { track.playerName }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (artist.isNotBlank()) Label(artist, size, Modifier.weight(1f, fill = false), color = MutedText)
        if (track.playing) {
            if (reduced) Equalizer(accent, 0L) else LiveEqualizer(accent)
        }
    }
}

@Composable
private fun LiveEqualizer(color: Color) {
    val frame by rememberFrame()
    Equalizer(color, frame)
}

@Composable
private fun Equalizer(color: Color, frameNanos: Long) {
    Canvas(Modifier.size(10.dp, 9.dp)) {
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
}

private val STATIC_LEVELS = floatArrayOf(0.55f, 0.9f, 0.7f)
private val EQ_SPEEDS = doubleArrayOf(5.1, 6.7, 4.3)
private val EQ_PHASES = doubleArrayOf(0.0, 1.3, 2.4)

@Composable
private fun Artwork(art: PolyBitmap?, size: Dp, radius: Dp, tone: MediaTone) {
    val bitmap = rememberArt(art)
    val shape = ppShape(radius)
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
            PauseGlyph(asset("headphones-02"), MutedText, size * 0.42f)
        }
    }
}

private fun fraction(track: PauseMediaTrack, now: Long): Float =
    if (track.lengthMs > 0) (track.positionAt(now).toFloat() / track.lengthMs).coerceIn(0f, 1f) else 0f

@Composable
private fun ProgressLine(fraction: Float, accent: Color, height: Dp, modifier: Modifier = Modifier) {
    val shape = OneConfigTheme.current.circleShape
    Box(modifier.fillMaxWidth().height(height).clip(shape).background(SocialTextPrimary.copy(alpha = 0.14f))) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).height(height).background(accent, shape))
    }
}

@Composable
private fun Controls(track: PauseMediaTrack?, tone: MediaTone, skip: Dp, skipIcon: Dp, play: Dp, playIcon: Dp, gap: Dp) {
    val playing = track?.playing == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        SkipButton(MediaGlyph.PREVIOUS, "Previous", skip, skipIcon, track?.canPrevious == true, PauseEvergreenMedia::previous)
        PlayButton(playing, track != null, tone, play, playIcon, track?.canToggle == true)
        SkipButton(MediaGlyph.NEXT, "Next", skip, skipIcon, track?.canNext == true, PauseEvergreenMedia::next)
    }
}

@Composable
private fun SkipButton(glyph: MediaGlyph, tooltip: String, size: Dp, iconSize: Dp, enabled: Boolean, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val lit = hovered && enabled
    Box(
        Modifier
            .size(size)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .rowFrame(false, lit, ppShape(if (size > 28.dp) 10.dp else 8.dp))
            .hoverable(interaction)
            .then(if (enabled) Modifier.quietClick(interaction, onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(glyph, if (lit) SocialTextPrimary else SocialTextPrimary.copy(alpha = 0.72f), iconSize)
        if (lit) PauseTooltip(tooltip)
    }
}

@Composable
private fun PlayButton(playing: Boolean, active: Boolean, tone: MediaTone, size: Dp, iconSize: Dp, enabled: Boolean) {
    val (interaction, hovered) = rememberSocialHover()
    val lit = hovered && enabled
    val base = if (active) vivid(tone.accent, OneConfigTheme.accent) else SocialTextPrimary.copy(alpha = 0.08f)
    val fill = if (lit && active) lerp(base, Color.White, 0.16f) else base
    val ink = if (active) inkOn(fill) else MutedText
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                alpha = if (enabled) 1f else DISABLED_ALPHA
                val scale = if (lit) 1.06f else 1f
                scaleX = scale
                scaleY = scale
            }
            .background(fill, OneConfigTheme.current.circleShape)
            .hoverable(interaction)
            .then(if (enabled) Modifier.quietClick(interaction, PauseEvergreenMedia::toggle) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(if (playing) MediaGlyph.PAUSE else MediaGlyph.PLAY, ink, iconSize)
        if (lit) PauseTooltip(if (playing) "Pause" else "Play")
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
