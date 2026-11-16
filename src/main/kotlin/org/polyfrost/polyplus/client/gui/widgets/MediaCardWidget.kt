@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.pausescreen.Artwork
import org.polyfrost.polyplus.client.gui.pausescreen.MENU_HINT
import org.polyfrost.polyplus.client.gui.pausescreen.MediaGlyph
import org.polyfrost.polyplus.client.gui.pausescreen.MediaMenuHinted
import org.polyfrost.polyplus.client.gui.pausescreen.PanelWash
import org.polyfrost.polyplus.client.gui.pausescreen.PauseEvergreenMedia
import org.polyfrost.polyplus.client.gui.pausescreen.PauseMediaTrack
import org.polyfrost.polyplus.client.gui.pausescreen.PlayButton
import org.polyfrost.polyplus.client.gui.pausescreen.SkipButton
import org.polyfrost.polyplus.client.gui.pausescreen.TabularLabel
import org.polyfrost.polyplus.client.gui.pausescreen.Title
import org.polyfrost.polyplus.client.gui.pausescreen.asset
import org.polyfrost.polyplus.client.gui.pausescreen.mediaClock
import org.polyfrost.polyplus.client.gui.pausescreen.equalizer
import org.polyfrost.polyplus.client.gui.pausescreen.fraction
import org.polyfrost.polyplus.client.gui.pausescreen.learnsMenu
import org.polyfrost.polyplus.client.gui.pausescreen.rememberClock
import org.polyfrost.polyplus.client.gui.pausescreen.rememberFrame
import org.polyfrost.polyplus.client.gui.pausescreen.rememberMediaTrack
import org.polyfrost.polyplus.client.gui.pausescreen.rememberTone
import org.polyfrost.polyplus.client.gui.pausescreen.vivid
import org.polyfrost.polyplus.compat.Mods

private val CARD_RADIUS = 16.dp
private val CARD_PADDING_X = 12.dp
private val CARD_PADDING_Y = 13.dp
private val TOP_ROW = 64.dp
private val ART_RADIUS = 12.dp
private val ROW_GAP = 10.dp
private val BOTTOM_ROW = 24.dp
private val INNER_GAP = 12.dp
private val PLAY = 36.dp
private val PLAY_ICON = 14.dp
private val SKIP = 28.dp
private val SKIP_ICON = 12.dp
private val PROGRESS = 4.dp
private val PROGRESS_GAP = 8.dp
private const val META_SEPARATOR = " · "

object MediaCardWidget : ShellWidget {
    override val id = "polyplus:media"
    override val title = "Media"
    override val icon = asset("headphones-02")
    override val size = WidgetSize.CARD
    override val surfaces = setOf(WidgetSurface.LOBBY, WidgetSurface.PAUSE)
    override val bleed = true

    override fun isAvailable(surface: WidgetSurface): Boolean = Mods.evergreenHud && PauseEvergreenMedia.supported()

    @Composable
    override fun Content(scope: WidgetScope) = MediaCard(scope.reducedMotion)
}

@Composable
private fun MediaCard(reduced: Boolean) {
    val track = rememberMediaTrack()
    val tone = rememberTone(track)
    val accent = vivid(tone.accent, OneConfigTheme.accent)
    val hover = rememberHoverState()
    val shape = OneConfigTheme.shape(CARD_RADIUS)
    MediaMenuHinted(MENU_HINT, Modifier.fillMaxWidth().height(WidgetSize.CARD.height)) {
        Box(Modifier.fillMaxSize().clip(shape).hoverable(hover.interactionSource).learnsMenu()) {
            PanelWash(tone)
            Column(Modifier.fillMaxSize().padding(horizontal = CARD_PADDING_X, vertical = CARD_PADDING_Y)) {
                Row(Modifier.fillMaxWidth().height(TOP_ROW), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(track?.art, TOP_ROW, ART_RADIUS, tone)
                    Spacer(Modifier.width(INNER_GAP))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (track == null) {
                            Text("Nothing playing", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                            Text("Play something in another app", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                        } else {
                            Title(track.displayTitle, 14.sp, hover.hovered && !reduced)
                            MetaLine(track, accent, reduced)
                        }
                    }
                    Spacer(Modifier.width(INNER_GAP))
                    PlayButton(track?.playing == true, track != null, tone, PLAY, PLAY_ICON, track?.canToggle == true)
                }
                Spacer(Modifier.height(ROW_GAP))
                Row(Modifier.fillMaxWidth().height(BOTTOM_ROW), verticalAlignment = Alignment.CenterVertically) {
                    SkipButton(MediaGlyph.PREVIOUS, "Previous", SKIP, SKIP_ICON, track?.canPrevious == true, PauseEvergreenMedia::previous)
                    Progress(track, accent, Modifier.weight(1f).padding(horizontal = PROGRESS_GAP))
                    SkipButton(MediaGlyph.NEXT, "Next", SKIP, SKIP_ICON, track?.canNext == true, PauseEvergreenMedia::next)
                }
            }
        }
    }
}

@Composable
private fun MetaLine(track: PauseMediaTrack, accent: Color, reduced: Boolean) {
    val meta = listOf(track.artist.trim(), track.playerName).filter { it.isNotBlank() }.distinct().joinToString(META_SEPARATOR)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (meta.isNotEmpty()) Text(meta, Modifier.weight(1f, fill = false), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.body.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        if (track.playing) Equalizer(accent, reduced)
    }
}

@Composable
private fun Equalizer(color: Color, reduced: Boolean) {
    if (reduced) {
        Canvas(Modifier.size(10.dp, 9.dp)) { equalizer(color, 0L) }
        return
    }
    val frame = rememberFrame()
    Canvas(Modifier.size(10.dp, 9.dp)) { equalizer(color, frame.value) }
}

@Composable
private fun Progress(track: PauseMediaTrack?, accent: Color, modifier: Modifier) {
    val muted = OneConfigTheme.current.mutedText
    if (track == null) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            TabularLabel(mediaClock(0L), 10.sp, muted, FontWeight.Normal)
            ProgressBar(0f, Modifier.weight(1f).padding(horizontal = PROGRESS_GAP), tint = accent, thickness = PROGRESS)
            TabularLabel(mediaClock(0L), 10.sp, muted, FontWeight.Normal)
        }
        return
    }
    val now = rememberClock(track.playing)
    val seconds by remember(track) { derivedStateOf { track.positionAt(now.value) / 1000L } }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TabularLabel(mediaClock(seconds * 1000L), 10.sp, muted, FontWeight.Normal)
        ProgressFill(track, now, accent, Modifier.weight(1f).padding(horizontal = PROGRESS_GAP))
        if (track.lengthMs > 0) TabularLabel(mediaClock(track.lengthMs), 10.sp, muted, FontWeight.Normal)
    }
}

@Composable
private fun ProgressFill(track: PauseMediaTrack, now: State<Long>, accent: Color, modifier: Modifier) {
    ProgressBar(fraction(track, now.value), modifier, tint = accent, thickness = PROGRESS)
}
