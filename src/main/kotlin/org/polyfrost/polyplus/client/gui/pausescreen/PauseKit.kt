@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPopup
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.trackScreenFocus
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.oneconfig.internal.ui.components.liquidGlass
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.lobbyshell.ModLogo
import org.polyfrost.polyplus.client.gui.lobbyshell.MoreMenuPanel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberModMenu
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.clickableTextWithSound
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.playButtonClickSound
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayTogetherHooks
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.party.PartyJoin
import net.minecraft.client.multiplayer.ServerData
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
private const val DANGER_REST_TINT = 0.1f
private const val DANGER_HOVER_TINT = 0.2f
private val MOD_LOGO_SIZE = 16.dp
private val MENU_ICON_SIZE = 18.dp

internal fun asset(name: String): String = "$SOCIAL_ASSETS$name.svg"

internal object PauseHost : SocialHost {
    override fun join(data: ServerData) = PartyJoin.server(data)
}

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
            .trackScreenFocus()
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
                SocialToastHost(Modifier.align(Alignment.TopCenter).padding(top = if (screen.stepBack) LobbyLayout.Top else 24.dp))
                PlayTogetherHooks(PauseHost)
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
    val glass = liquidGlassTheme
    return this
        .then(if (glass) Modifier.liquidGlass(shape, frosted = true) else Modifier)
        .background(if (glass) surface else surface.copy(alpha = max(surface.alpha, OPAQUE_FLOOR)), shape)
        .border(1.dp, SocialBorderColor, shape)
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
internal fun MenuRow(
    icon: String,
    label: String,
    detail: String? = null,
    height: Dp = 44.dp,
    size: TextUnit = 16.sp,
    danger: Boolean = false,
    enabled: Boolean = true,
    tooltip: String? = null,
    logo: ImageBitmap? = null,
    onClick: () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val ink = if (danger) SocialDangerText else SocialTextPrimary
    Box {
        if (tooltip != null && hovered) TooltipPopup(tooltip, TooltipPlacement.AboveCenter, 320.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (danger) Modifier.background(SocialDangerText.copy(alpha = if (hovered && enabled) DANGER_HOVER_TINT else DANGER_REST_TINT), SocialFieldShape) else Modifier)
                .rowFrame(false, hovered && enabled)
                .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
                .hoverable(interaction)
                .then(if (enabled) Modifier.clickableWithSound(label, onClick = onClick) else Modifier)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (logo != null) ModMenuLogo(logo, ink) else PauseGlyph(icon, ink)
            Label(label, size, Modifier.weight(1f), color = ink, weight = FontWeight.Medium)
            detail?.let { Label(it, 12.sp, color = MutedText) }
        }
    }
}

@Composable
private fun MoreEntry(entry: PauseEntry, onDone: () -> Unit) {
    val sprite = entry.sprite
    MenuItem(
        entry.label,
        themedIcon(entry.glyph),
        null,
        MenuItemSize.Regular,
        false,
        false,
        entry.enabled,
        entry.tooltip,
        Modifier,
        sprite?.let { { Image(it, null, Modifier.size(MENU_ICON_SIZE), filterQuality = FilterQuality.None) } },
    ) {
        onDone()
        entry.press()
    }
}

@Composable
internal fun MorePanel(screen: OneClientPauseScreen, modifier: Modifier = Modifier, routes: Boolean = false) {
    val modMenu = rememberModMenu()
    val entries = screen.capture.more.filterNot { modMenu != null && it.modMenu }
    val close = { screen.moreOpen = false }
    val game = if (routes) listOfNotNull(screen.capture.entry(PauseSlot.ADVANCEMENTS), screen.capture.entry(PauseSlot.STATS)) else emptyList()
    val mods = modMenu.takeIf { routes }
    MoreMenuPanel(Modifier.heightIn(max = 560.dp).pauseGlass().verticalScroll(rememberScrollState()), modifier) {
        game.forEach { MoreEntry(it, close) }
        mods?.let { (button, logo) ->
            MenuItem("Mods", themedIcon(button.icon), null, MenuItemSize.Regular, false, false, true, button.tooltip, Modifier, logo?.let { { ModMenuLogo(it, SocialTextPrimary) } }) {
                close()
                button.onClick(screen)
            }
        }
        if ((game.isNotEmpty() || mods != null) && entries.isNotEmpty()) Hairline(Modifier.padding(vertical = 4.dp))
        entries.groupBy { it.section }.forEach { (section, rows) ->
            Label(section, 11.sp, Modifier.padding(start = 10.dp, top = 6.dp, bottom = 2.dp), color = MutedText, weight = FontWeight.Medium)
            rows.forEach { MoreEntry(it, close) }
        }
        if (entries.isNotEmpty()) Hairline(Modifier.padding(vertical = 4.dp))
        MenuItem("Classic pause menu", themedIcon("dots-grid"), null, MenuItemSize.Regular, false, false, true, uncapturedHint(screen.capture.uncaptured), Modifier, null, screen::openClassic)
    }
}

@Composable
private fun ModMenuLogo(logo: ImageBitmap, tint: Color) {
    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { ModLogo(logo, MOD_LOGO_SIZE, tint) }
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

internal val POPUP_EDGE = 20.dp
private val SIDE_GAP = 14.dp
private val BAR_INSET = 6.dp
private val BAR_INNER_RADIUS = 14.dp

@Composable
internal fun BarTile(
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    glass: Boolean = true,
    content: @Composable BoxScope.(hovered: Boolean) -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .height(LobbyLayout.BarHeight)
            .widthIn(min = LobbyLayout.BarHeight)
            .then(if (glass) Modifier.pauseGlass() else Modifier)
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.quietClick(interaction, onClick) else Modifier)
            .padding(BAR_INSET),
        contentAlignment = Alignment.Center,
    ) {
        if (onClick != null) Box(Modifier.matchParentSize().rowFrame(false, hovered || active, ppShape(BAR_INNER_RADIUS)))
        content(hovered)
        if (tooltip != null && hovered) TooltipPopup(tooltip, TooltipPlacement.AboveCenter)
    }
}

private val CARD_ROW = 34.dp

@Composable
internal fun CardRow(
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.(lit: Boolean) -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val lit = (hovered || active) && onClick != null
    Box {
        Row(
            modifier
                .fillMaxWidth()
                .height(CARD_ROW)
                .rowFrame(false, lit, ppShape(10.dp))
                .hoverable(interaction)
                .then(if (onClick != null) Modifier.quietClick(interaction, onClick) else Modifier)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content(lit)
        }
        if (tooltip != null && hovered) TooltipPopup(tooltip, TooltipPlacement.AboveCenter)
    }
}

@Composable
internal fun TabularLabel(
    text: String,
    size: TextUnit,
    color: Color = SocialTextPrimary,
    weight: FontWeight = FontWeight.Medium,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(color = color, fontSize = size, fontWeight = weight, fontFamily = OneConfigTheme.current.fontFamily, textAlign = align, fontFeatureSettings = "tnum"),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

private class SideAnchor(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val x = (anchorBounds.left - gap - popupContentSize.width).coerceIn(margin, maxX)
        return IntOffset(x, anchorBounds.top.coerceIn(margin, maxY))
    }
}

@Composable
internal fun rememberToolAnchor(side: Boolean): PopupPositionProvider {
    val density = LocalDensity.current
    val gap = with(density) { SIDE_GAP.roundToPx() }
    val margin = with(density) { POPUP_EDGE.roundToPx() }
    val rise = rememberRiseAnchor()
    val beside = remember(gap, margin) { SideAnchor(gap, margin) }
    return if (side) beside else rise
}

internal fun Modifier.quietClick(interaction: MutableInteractionSource, onClick: () -> Unit): Modifier =
    clickable(interactionSource = interaction, indication = null) {
        playButtonClickSound()
        onClick()
    }

private class RiseAnchor(private val gap: Int, private val centered: Boolean) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val above = anchorBounds.top - gap - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.bottom + gap
        val x = if (centered) anchorBounds.center.x - popupContentSize.width / 2 else anchorBounds.left
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }
}

@Composable
internal fun rememberRiseAnchor(gap: Dp = 10.dp, centered: Boolean = false): PopupPositionProvider {
    val px = with(LocalDensity.current) { gap.roundToPx() }
    return remember(px, centered) { RiseAnchor(px, centered) }
}

@Composable
internal fun popupMaxHeight(margin: Dp = 16.dp): Dp {
    val root = LocalRootSize.current
    val density = LocalDensity.current
    if (root.height <= 0f) return Dp.Infinity
    return (with(density) { root.height.toDp() } - margin * 2).coerceAtLeast(120.dp)
}
