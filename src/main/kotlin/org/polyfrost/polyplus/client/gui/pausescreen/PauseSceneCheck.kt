@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessText
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.popup
import org.polyfrost.polyplus.client.lobby.LobbyCapture
import org.polyfrost.polyplus.client.lobby.LobbySceneScore
import org.polyfrost.polyplus.client.lobby.LobbySpotAccess
import org.polyfrost.polyplus.client.lobby.LobbySpotAction
import org.polyfrost.polyplus.client.lobby.LobbySpotState
import kotlin.math.roundToInt
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.ProgressBar
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.popupMaxHeight
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

private const val ACCESS_TICK_MILLIS = 500L
private const val FULL_SCORE = 100
private const val GREAT_SCORE = 80
private const val DISTANCE_SHOWN = 5
private const val WEAK_SHARE = 0.75
private const val MIN_GAIN = 1
private const val TIP_COUNT = 2
private const val STALE_ALPHA = 0.55f
private val SCENE_PANEL_WIDTH = 320.dp
private val SCENE_PANEL_MAX = 520.dp
private val RING_SIZE = 54.dp
private val RING_STROKE = 5.dp
private val METER_HEIGHT = 3.dp
private val BADGE_DOT = 8.dp

private enum class Tone { PASS, FAIL, NEUTRAL }

private class Verdict(val text: String, val tone: Tone)

private class Tip(val text: String, val gain: Int)

private fun readAccess(): LobbySpotAccess = runCatching { LobbyCapture.spotAccess() }.getOrDefault(LobbySpotAccess.Hidden(""))

internal fun sceneShown(): Boolean = readAccess() !is LobbySpotAccess.Hidden

private class SceneTrigger(val blocked: String?, val spot: LobbySpotState, val open: Boolean, val press: (() -> Unit)?)

@Composable
private fun SceneTool(side: Boolean = false, trigger: @Composable (SceneTrigger) -> Unit) {
    var access by remember { mutableStateOf(readAccess()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(ACCESS_TICK_MILLIS)
            access = readAccess()
        }
    }
    val current = access
    if (current is LobbySpotAccess.Hidden) return
    var open by remember { mutableStateOf(false) }
    val blocked = (current as? LobbySpotAccess.Blocked)?.reason
    val spot = LobbyCapture.spot
    Box {
        trigger(SceneTrigger(blocked, spot, open && blocked == null, if (blocked == null) ({ open = !open }) else null))
        if (open && blocked == null) {
            val close = { open = false }
            Popup(
                popupPositionProvider = rememberToolAnchor(side),
                onDismissRequest = close,
                properties = PopupProperties(focusable = true),
            ) {
                EscapeCloses(close)
                ScenePanel(spot)
            }
        }
    }
}

@Composable
internal fun SceneBarTool(glass: Boolean = true) {
    SceneTool { scene ->
        BarTile(tooltip = scene.blocked ?: if (scene.open) null else "Scene check", label = "Scene check", active = scene.open, onClick = scene.press, glass = glass) { hovered ->
            val ink = if ((hovered || scene.open) && scene.blocked == null) SocialTextPrimary else OneConfigTheme.current.mutedText
            val disabledAlpha = OneConfigTheme.current.disabledAlpha
            Box(Modifier.graphicsLayer { alpha = if (scene.blocked != null) disabledAlpha else 1f }) {
                PauseGlyph(asset("image-01"), ink, 20.dp)
            }
            SceneBadge(scene.spot, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
internal fun SceneCardTool() {
    SceneTool(side = true) { scene ->
        CardRow(tooltip = scene.blocked, label = "Scene check", active = scene.open, onClick = scene.press) { lit ->
            val usable = scene.blocked == null
            PauseGlyph(asset("image-01"), if (lit) SocialTextPrimary else OneConfigTheme.current.mutedText, 16.dp)
            Text("Scene", color = if (usable) SocialTextPrimary else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Spacer(Modifier.weight(1f))
            if (usable) sceneDetail(scene.spot)?.let { (text, color) -> TabularLabel(text, 12.sp, color) }
        }
    }
}

@Composable
private fun sceneDetail(spot: LobbySpotState): Pair<String, Color>? = when (spot) {
    is LobbySpotState.Waiting -> "Waiting" to OneConfigTheme.current.mutedText
    is LobbySpotState.Surveying -> "${(spot.progress * FULL_SCORE).roundToInt()}%" to OneConfigTheme.current.mutedText
    is LobbySpotState.Saving -> "Saving" to OneConfigTheme.current.mutedText
    is LobbySpotState.Done -> spot.report.breakdown?.total?.let { it.toString() to toneColor(if (spot.report.passed) Tone.PASS else Tone.FAIL) }
    else -> null
}

@Composable
private fun SceneBadge(spot: LobbySpotState, modifier: Modifier) {
    if (working(spot)) {
        Box(modifier.padding(2.dp).size(BADGE_DOT).background(OneConfigTheme.accent, OneConfigTheme.current.circleShape))
        return
    }
    val done = spot as? LobbySpotState.Done ?: return
    val total = done.report.breakdown?.total ?: return
    Badge(
        total.toString(),
        modifier.background(PanelSurface.copy(alpha = 1f), OneConfigTheme.current.shapes.pill),
        if (done.report.passed) BadgeTone.Accent else BadgeTone.Danger,
        style = BadgeStyle.Hairline,
    )
}

private fun working(spot: LobbySpotState): Boolean =
    spot is LobbySpotState.Waiting || spot is LobbySpotState.Surveying || spot is LobbySpotState.Saving

@Composable
private fun ScenePanel(spot: LobbySpotState) {
    var last by remember { mutableStateOf(spot as? LobbySpotState.Done) }
    LaunchedEffect(spot) { if (spot is LobbySpotState.Done) last = spot }
    val shown = spot as? LobbySpotState.Done ?: last.takeIf { working(spot) }
    val stale = shown != null && shown !== spot
    val verdict = verdict(spot, shown)
    Column(Modifier.width(SCENE_PANEL_WIDTH).heightIn(max = min(popupMaxHeight(POPUP_EDGE), SCENE_PANEL_MAX)).popup()) {
        val scroll = rememberScrollState()
        ScrollContainer(scroll, Modifier.weight(1f, fill = false), scrollbar = false) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Header(shown, verdict)
                if (shown != null) {
                    Column(Modifier.graphicsLayer { alpha = if (stale) STALE_ALPHA else 1f }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        shown.report.breakdown?.let { Categories(it) }
                        Requirements(shown)
                    }
                }
            }
        }
        Divider()
        Actions(spot, shown)
    }
}

@Composable
private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.PASS -> OneConfigTheme.accent
    Tone.FAIL -> SocialDangerText
    Tone.NEUTRAL -> OneConfigTheme.current.mutedText
}

private fun extraScene(report: LobbySceneScore.Report): Boolean =
    report.checks.any { !it.blocking && it.state == LobbySceneScore.CheckState.FAILED }

private fun mainReason(done: LobbySpotState.Done): String {
    val failed = done.report.checks.firstOrNull { it.blocking && it.state == LobbySceneScore.CheckState.FAILED }
    val reason = failed?.reason ?: failed?.label ?: done.message
    return if (reason.length > 1 && reason[1].isLowerCase()) reason.replaceFirstChar { it.lowercaseChar() } else reason
}

private fun verdict(spot: LobbySpotState, shown: LobbySpotState.Done?): Verdict {
    if (spot is LobbySpotState.Blocked) return Verdict(spot.reason, Tone.FAIL)
    if (shown == null) {
        return when (spot) {
            is LobbySpotState.Waiting -> Verdict("Waiting for the current capture to finish", Tone.NEUTRAL)
            is LobbySpotState.Surveying -> Verdict("Looking around this spot", Tone.NEUTRAL)
            is LobbySpotState.Saving -> Verdict("Saving this scene", Tone.NEUTRAL)
            else -> Verdict("See how this spot scores as a lobby backdrop", Tone.NEUTRAL)
        }
    }
    val report = shown.report
    val total = report.breakdown?.total ?: 0
    return when {
        shown.saved -> Verdict(if (extraScene(report)) "Saved as an extra scene" else "Saved to your lobby scenes", Tone.PASS)
        report.passed && shown.action == LobbySpotAction.CAPTURE -> Verdict(shown.message, Tone.FAIL)
        report.passed && total >= GREAT_SCORE -> Verdict("Great spot", Tone.PASS)
        report.passed -> Verdict("Good spot", Tone.PASS)
        else -> Verdict("Not good enough yet: ${mainReason(shown)}", Tone.FAIL)
    }
}

@Composable
private fun Header(shown: LobbySpotState.Done?, verdict: Verdict) {
    val total = shown?.report?.breakdown?.total
    val tone = toneColor(verdict.tone)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ScoreRing(total, tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Scene check", Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.label, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                shown?.let { ProfileTag(it.report.profile.label) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (shown?.saved == true) PauseGlyph(asset("check-circle"), tone, 13.dp)
                Text(verdict.text, Modifier.weight(1f), color = if (verdict.tone == Tone.NEUTRAL) OneConfigTheme.current.mutedText else tone, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun ScoreRing(total: Int?, tone: Color) {
    val track = SocialBorderColor
    val fraction = (total ?: 0).coerceIn(0, FULL_SCORE) / FULL_SCORE.toFloat()
    Box(Modifier.size(RING_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val inset = stroke / 2f
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            if (fraction > 0f) drawArc(tone, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(total?.toString() ?: "--", color = if (total == null) OneConfigTheme.current.mutedText else SocialTextPrimary, style = OneConfigTheme.current.typography.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun ProfileTag(text: String) {
    Badge(text, style = BadgeStyle.Hairline)
}

private fun share(category: LobbySceneScore.Category): Double = if (category.max > 0.0) category.points / category.max else 1.0

private fun gain(category: LobbySceneScore.Category, breakdown: LobbySceneScore.Breakdown): Int =
    if (breakdown.max > 0.0) ((category.max - category.points) / breakdown.max * FULL_SCORE).roundToInt() else 0

private fun weakest(breakdown: LobbySceneScore.Breakdown): List<LobbySceneScore.Category> =
    breakdown.categories
        .filter { share(it) < WEAK_SHARE && gain(it, breakdown) >= MIN_GAIN }
        .sortedByDescending { it.max - it.points }
        .take(TIP_COUNT)

private fun tipFor(category: LobbySceneScore.Category): String = when (category.key) {
    "flat" -> "Stand somewhere flatter"
    "sky" -> "Move out from under trees and roofs"
    "variety" -> "More kinds of blocks in view"
    "relief" -> "Hills or slopes nearby add interest"
    "built" -> "Build something nearby to score higher"
    "lights" -> "Add some lights"
    "dry" -> "Move away from big bodies of water"
    "room" -> "Try a bigger room"
    "cave" -> "Find a cave with pools, glow or plants"
    else -> "Improve ${category.label.lowercase()} to score higher"
}

@Composable
private fun Categories(breakdown: LobbySceneScore.Breakdown) {
    val weak = weakest(breakdown)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        breakdown.categories.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                pair.forEach { Meter(it, it in weak) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val distance = if (breakdown.max > 0.0) (breakdown.distanceCost / breakdown.max * FULL_SCORE).roundToInt() else 0
        if (distance >= DISTANCE_SHOWN) Footnote("The best spot is a few steps from you, which costs $distance points")
    }
    if (weak.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Ways to score higher", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            weak.map { Tip(tipFor(it), gain(it, breakdown)) }.forEach { TipRow(it) }
        }
    }
}

@Composable
private fun RowScope.Meter(category: LobbySceneScore.Category, weak: Boolean) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(category.label, Modifier.weight(1f), color = if (weak) SocialTextPrimary else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption.copy(fontWeight = if (weak) FontWeight.Medium else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text("${category.points.roundToInt()}/${category.max.roundToInt()}", style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        ProgressBar(share(category).toFloat(), tint = if (weak) OneConfigTheme.current.warning else OneConfigTheme.accent, trackColor = SocialBorderColor, thickness = METER_HEIGHT)
    }
}

@Composable
private fun TipRow(tip: Tip) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(6.dp).background(OneConfigTheme.current.warning, OneConfigTheme.current.circleShape))
        Text(tip.text, Modifier.weight(1f), style = OneConfigTheme.current.typography.small)
        Text("+${tip.gain}", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun Requirements(done: LobbySpotState.Done) {
    val checks = done.report.checks
    if (checks.isEmpty()) return
    val failing = checks.filter { it.blocking && it.state == LobbySceneScore.CheckState.FAILED }
    val unchecked = checks.any { it.blocking && it.state == LobbySceneScore.CheckState.UNCHECKED }
    val notes = if (done.saved) emptyList() else checks.filter { !it.blocking && it.state == LobbySceneScore.CheckState.FAILED }.mapNotNull { it.reason }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when {
            failing.isNotEmpty() -> failing.forEach { FailRow(it) }
            !unchecked -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PauseGlyph(asset("check-circle"), SocialSuccessText, 13.dp)
                Text("All requirements met", style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        notes.forEach { Footnote(it) }
    }
}

@Composable
private fun FailRow(check: LobbySceneScore.Check) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            PauseGlyph(asset("alert-triangle"), SocialDangerText, 13.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(check.label, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            check.reason?.let { Text(it, color = SocialDangerText, style = OneConfigTheme.current.typography.caption) }
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(text, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption)
}

@Composable
private fun Actions(spot: LobbySpotState, shown: LobbySpotState.Done?) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        when (spot) {
            is LobbySpotState.Surveying -> Progress("Looking around this spot", spot.progress)
            is LobbySpotState.Saving -> Progress("Saving this scene", spot.progress)
            else -> {
                val enabled = spot !is LobbySpotState.Waiting
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PanelButton("Capture this spot", enabled, true) { LobbyCapture.captureSpot() }
                    PanelButton(if (shown == null) "Check this spot" else "Check again", enabled, false) { LobbyCapture.checkSpot() }
                }
            }
        }
    }
}

@Composable
private fun Progress(text: String, progress: Float) {
    Column(Modifier.fillMaxWidth().height(28.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text("${(progress.coerceIn(0f, 1f) * FULL_SCORE).roundToInt()}%", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        ProgressBar(progress)
    }
}

@Composable
private fun RowScope.PanelButton(text: String, enabled: Boolean, primary: Boolean, onClick: () -> Unit) {
    TextButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.weight(1f),
        style = if (primary) ControlStyle.Primary else ControlStyle.Secondary,
        size = ControlSize.Compact,
        enabled = enabled,
    )
}
