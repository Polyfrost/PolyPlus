@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.HoverTooltip
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.TooltipPlacement
import org.polyfrost.polyplus.client.gui.clickableTextWithSound
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import kotlin.math.max
import kotlin.math.min
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize

private const val MIN_SCALE = 0.72f
private const val COMPACT_WIDTH = 1300f
private const val OPAQUE_FLOOR = 0.94f
private const val MENU_REVEAL_NANOS = 160_000_000f
private const val DISABLED_ALPHA = 0.4f

import org.polyfrost.polyplus.client.gui.isMinecraftTheme
internal fun asset(name: String): String = "$SOCIAL_ASSETS$name.svg"

@Composable
internal fun rememberFrame(): State<Long> = produceState(0L) { while (true) withFrameNanos { value = it } }

@Composable
internal fun rememberReveal(): Float {
    val frame by rememberFrame()
    var start by remember { mutableLongStateOf(0L) }
    if (start == 0L && frame != 0L) start = frame
    return if (start == 0L) 0f else ((frame - start) / MENU_REVEAL_NANOS).coerceIn(0f, 1f)
}

@Composable
internal fun PauseRoot(screen: OneClientPauseScreen) {
    var measured by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onSizeChanged { measured = it }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val at = event.changes.firstOrNull()?.position ?: continue
                        val box = size
                        val inside = at.x.isFinite() && at.y.isFinite() && at.x in 0f..box.width.toFloat() && at.y in 0f..box.height.toFloat()
                        if (inside && box.width > 0 && box.height > 0) PauseStage.pointer(at.x / box.width * 2f - 1f, 1f - at.y / box.height * 2f)
                    }
                }
            },
    ) {
        val outer = LocalDensity.current
        val widthPx = if (measured.width > 0) measured.width.toFloat() else maxWidth.value * outer.density
        val heightPx = if (measured.height > 0) measured.height.toFloat() else maxHeight.value * outer.density
        if (!usableSize(widthPx, heightPx)) return@BoxWithConstraints
        val scale = max(min(widthPx / LobbyLayout.DESIGN_WIDTH, heightPx / LobbyLayout.DESIGN_HEIGHT), MIN_SCALE)
        val density = remember(scale, outer.fontScale) { Density(usableScale(scale), usableScale(outer.fontScale)) }
        CompositionLocalProvider(LocalDensity provides density, LocalRootSize provides Size(widthPx, heightPx)) {
            Box(Modifier.fillMaxSize()) {
                val narrow = widthPx / scale < COMPACT_WIDTH
                if (screen.stepBack) StepBackScreen(screen, narrow) else QuickDockScreen(screen, narrow)
                Curtain()
            }
        }
    }
}

@Composable
private fun BoxScope.Curtain() {
    val frame by rememberFrame()
    val shade = if (frame == 0L) 0f else PauseStage.curtain
    if (shade > 0f) Box(Modifier.matchParentSize().background(OneConfigTheme.current.pageBackground.copy(alpha = shade)))
}

@Composable
internal fun Modifier.pauseGlass(radius: Dp = 20.dp): Modifier {
    val shape = ppShape(radius)
    val surface = PanelSurface
    return this
        .background(surface.copy(alpha = max(surface.alpha, OPAQUE_FLOOR)), shape)
        .border(1.dp, SocialBorderColor, shape)
}

@Composable
internal fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LobbyIcon("logo", size = 28.dp)
        SocialText("ONECLIENT", 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = Outfit, maxLines = 1)
    }
}

@Composable
internal fun NavTab(label: String, active: Boolean, unread: Boolean = false, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Box(
        Modifier.height(38.dp).hoverable(interaction).clickableWithSound(onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, 14.sp, color = if (active || hovered) SocialTextPrimary else SocialTextPrimary.copy(alpha = 0.6f), weight = if (isMinecraftTheme) FontWeight.Bold else FontWeight.Medium)
        if (active) Box(Modifier.align(Alignment.BottomCenter).width(27.dp).height(2.dp).background(OneConfigTheme.accent))
        if (unread) {
            Box(Modifier.align(Alignment.CenterEnd).offset(x = 12.dp).size(8.dp).background(OneConfigTheme.accent, OneConfigTheme.current.circleShape))
        }
    }
}

@Composable
internal fun Keycap(text: String) {
    val shape = ppShape(6.dp)
    Box(Modifier.height(22.dp).border(1.dp, SocialBorderColor, shape).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        Label(text, 11.sp, color = MutedText, weight = FontWeight.Medium)
    }
}

@Composable
internal fun Eyebrow(text: String) {
    SocialText(text, 11.sp, color = MutedText, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp, maxLines = 1)
}

@Composable
internal fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(SocialBorderColor))
}

@Composable
internal fun PauseStateLine(state: PauseState) {
    val dot = if (state.paused) MutedText else SocialSuccessColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.paused) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.width(2.dp).height(10.dp).background(dot)) }
            }
        } else {
            Box(Modifier.size(8.dp).background(dot, OneConfigTheme.current.circleShape))
        }
        Label(state.label, 12.sp, color = if (state.paused) MutedText else SocialTextPrimary, weight = FontWeight.Medium)
        state.detail?.let { Label(it, 12.sp, color = MutedText) }
    }
}

@Composable
internal fun rememberPauseState(): PauseState {
    val frame by rememberFrame()
    val state = PauseWorld.state()
    return remember(frame / STATE_NANOS, state.label, state.detail) { state }
}

private const val STATE_NANOS = 100_000_000L

@Composable
internal fun rememberPlace(): PausePlace {
    val frame by rememberFrame()
    return remember(frame / PLACE_NANOS) { PauseWorld.place() }
}

private const val PLACE_NANOS = 1_000_000_000L

@Composable
internal fun PauseGlyph(icon: String, tint: Color = SocialTextPrimary, size: Dp = 18.dp) {
    ThemedIcon(icon, tint, size)
}

@Composable
private fun EntryGlyph(entry: PauseEntry, size: Dp = 18.dp) {
    val sprite = entry.sprite
    if (sprite != null) {
        Image(sprite, entry.label, Modifier.size(size), filterQuality = FilterQuality.None)
    } else {
        PauseGlyph(entry.glyph, size = size)
    }
}

@Composable
internal fun MenuRow(
    icon: String,
    label: String,
    detail: String? = null,
    height: Dp = 44.dp,
    size: TextUnit = 16.sp,
    danger: Boolean = false,
    enabled: Boolean = true,
    tooltip: String? = null,
    onClick: () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val ink = if (danger && hovered) SocialDangerText else SocialTextPrimary
    Box {
        if (tooltip != null && hovered) HoverTooltip(tooltip, TooltipPlacement.ABOVE_CENTER, 320.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .rowFrame(false, hovered && enabled)
                .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
                .hoverable(interaction)
                .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PauseGlyph(icon, ink)
            Label(label, size, Modifier.weight(1f), color = ink, weight = FontWeight.Medium)
            detail?.let { Label(it, 12.sp, color = MutedText) }
        }
    }
}

@Composable
private fun MoreEntry(entry: PauseEntry, onDone: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Box {
        if (entry.tooltip != null && hovered) HoverTooltip(entry.tooltip, TooltipPlacement.ABOVE_CENTER, 320.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .rowFrame(false, hovered && entry.enabled)
                .graphicsLayer { alpha = if (entry.enabled) 1f else DISABLED_ALPHA }
                .hoverable(interaction)
                .then(
                    if (entry.enabled) {
                        Modifier.clickableWithSound {
                            onDone()
                            entry.press()
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EntryGlyph(entry)
            Label(entry.label, 14.sp, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MorePanel(screen: OneClientPauseScreen, modifier: Modifier = Modifier) {
    val entries = screen.capture.more
    val close = { screen.moreOpen = false }
    Column(
        modifier
            .width(300.dp)
            .heightIn(max = 560.dp)
            .pauseGlass()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        entries.groupBy { it.section }.forEach { (section, rows) ->
            Label(section, 11.sp, Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp), color = MutedText, weight = FontWeight.Medium)
            rows.forEach { MoreEntry(it, close) }
        }
        if (entries.isNotEmpty()) Hairline(Modifier.padding(vertical = 4.dp))
        MenuRow("dots-grid", "Classic pause menu", height = 38.dp, size = 14.sp, tooltip = uncapturedHint(screen.capture.uncaptured), onClick = screen::openClassic)
    }
}

private fun uncapturedHint(count: Int): String? = when (count) {
    0 -> null
    1 -> "1 mod control only works here"
    else -> "$count mod controls only work here"
}

@Composable
internal fun BoxScope.MoreDismiss(screen: OneClientPauseScreen) {
    Box(Modifier.matchParentSize().clickableTextWithSound { screen.moreOpen = false })
}
