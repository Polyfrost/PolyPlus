@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.MenuCustomItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuHeading
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSeparator
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.ToastDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.liquidGlass
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberAnchoredPosition
import org.polyfrost.oneconfig.api.ui.v1.components.popupMaxHeight
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.trackScreenFocus
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.lobbyshell.LINKED_MOD_CAPTION
import org.polyfrost.polyplus.client.gui.lobbyshell.ModLogo
import org.polyfrost.polyplus.client.gui.lobbyshell.decodeModLogo
import org.polyfrost.polyplus.client.gui.lobbyshell.icon
import org.polyfrost.polyplus.client.gui.lobbyshell.label
import org.polyfrost.polyplus.compat.LinkedModMenus
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberModMenu
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayTogetherHooks
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.party.PartyJoin
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import kotlin.math.max
import kotlin.math.min
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize

private const val MIN_SCALE = 0.72f
private const val COMPACT_WIDTH = 1300f
private const val OPAQUE_FLOOR = 0.94f
private const val DANGER_REST_TINT = 0.1f
private const val DANGER_HOVER_TINT = 0.2f
private const val MENU_REVEAL_NANOS = 160_000_000f
private val MOD_LOGO_SIZE = 16.dp
private val MENU_ICON_SIZE = 18.dp
private val MORE_MENU_WIDTH = 300.dp
private val MORE_MENU_MAX_HEIGHT = 560.dp

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
                SocialToastHost(Modifier.padding(top = (if (screen.stepBack) LobbyLayout.Top else 24.dp) - ToastDefaults.margin))
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
    val shape = OneConfigTheme.shape(radius)
    val surface = PanelSurface
    val glass = liquidGlassTheme
    return this
        .then(if (glass) Modifier.liquidGlass(shape, frosted = true) else Modifier)
        .background(if (glass) surface else surface.copy(alpha = max(surface.alpha, OPAQUE_FLOOR)), shape)
        .border(1.dp, SocialBorderColor, shape)
}

@Composable
internal fun PauseStateLine(state: PauseState) {
    val dot = if (state.paused) OneConfigTheme.current.mutedText else SocialSuccessColor
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.paused) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) { Box(Modifier.width(2.dp).height(10.dp).background(dot)) }
            }
        } else {
            Box(Modifier.size(8.dp).background(dot, OneConfigTheme.current.circleShape))
        }
        Text(
            state.label,
            color = if (state.paused) OneConfigTheme.current.mutedText else SocialTextPrimary,
            style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        state.detail?.let {
            Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
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
    val hover = rememberHoverState()
    val interaction = hover.interactionSource
    val hovered = hover.hovered
    val dangerInk = if (danger) SocialDangerText else null
    val ink = dangerInk ?: SocialTextPrimary
    val disabledAlpha = OneConfigTheme.current.disabledAlpha
    TooltipBox(tooltip, placement = TooltipPlacement.AboveCenter, maxWidth = 320.dp, delayMillis = 0) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .then(if (dangerInk != null && liquidGlassTheme) Modifier.liquidGlass(SocialFieldShape, frosted = false) else Modifier)
                .then(if (dangerInk != null) Modifier.background(dangerInk.copy(alpha = if (hovered && enabled) DANGER_HOVER_TINT else DANGER_REST_TINT), SocialFieldShape) else Modifier)
                .rowFrame(false, hovered && enabled)
                .graphicsLayer { alpha = if (enabled) 1f else disabledAlpha }
                .hoverable(interaction)
                .oneConfigClickable(label, onClick, enabled = enabled, indication = false, interactionSource = interaction)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (logo != null) ModMenuLogo(logo, ink) else PauseGlyph(icon, ink)
            Text(
                label,
                Modifier.weight(1f),
                ink,
                OneConfigTheme.current.typography.body.copy(fontSize = size, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            detail?.let {
                Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
    }
}

@Composable
private fun MoreEntry(entry: PauseEntry, onDone: () -> Unit) {
    val sprite = entry.sprite
    MenuItem(
        label = entry.label,
        onClick = {
            onDone()
            entry.press()
        },
        icon = entry.glyph,
        size = MenuItemSize.Regular,
        enabled = entry.enabled,
        tooltip = entry.tooltip,
        leading = sprite?.let { { Image(it, null, Modifier.size(MENU_ICON_SIZE), filterQuality = FilterQuality.None) } },
    )
}

private fun pauseLinkedEntries(): List<LinkedModMenus.Entry> =
    LinkedModMenus.entries.filter { it != LinkedModMenus.Entry.Accounts && it != LinkedModMenus.Entry.FriendsWorlds }

@Composable
private fun LinkedEntry(entry: LinkedModMenus.Entry, onDone: () -> Unit) {
    MenuItem(
        label = entry.label,
        onClick = {
            onDone()
            LinkedModMenus.open(entry)
        },
        icon = entry.icon,
        size = MenuItemSize.Regular,
    )
}

@Composable
internal fun LinkedModRow(screen: OneClientPauseScreen, height: Dp) {
    val linked = remember { pauseLinkedEntries() }
    if (linked.isEmpty()) return
    val logo = remember { decodeModLogo(LinkedModMenus.logo) }
    val open = screen.moreOpen && screen.moreLinked
    Box(Modifier.focusRequester(rememberMoreTrigger(open))) {
        MenuRow(asset("link-external-01"), LinkedModMenus.displayName ?: LINKED_MOD_CAPTION, height = height, logo = logo) {
            screen.moreLinked = true
            screen.moreOpen = !open
        }
        if (open) MorePopup(screen, rememberToolAnchor(true))
    }
}

@Composable
internal fun rememberMoreTrigger(open: Boolean): FocusRequester {
    val trigger = remember { FocusRequester() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            shown = true
            return@LaunchedEffect
        }
        if (!shown) return@LaunchedEffect
        shown = false
        if (!A11y.keyboardMode) return@LaunchedEffect
        withFrameNanos { }
        runCatching { trigger.requestFocus() }
    }
    return trigger
}

@Composable
internal fun MorePopup(screen: OneClientPauseScreen, anchor: PopupPositionProvider, routes: Boolean = false) {
    Popup(popupPositionProvider = anchor, onDismissRequest = { screen.moreOpen = false }, properties = PopupProperties(focusable = true)) {
        MorePanel(
            screen,
            Modifier
                .heightIn(max = popupMaxHeight(POPUP_EDGE))
                .blockPointer()
                .trackScreenFocus()
                .focusGroup(),
            routes,
        )
    }
}

@Composable
internal fun MorePanel(screen: OneClientPauseScreen, modifier: Modifier = Modifier, routes: Boolean = false) {
    val linked = remember { pauseLinkedEntries() }
    val close = { screen.moreOpen = false }
    val menu = modifier.width(MORE_MENU_WIDTH).heightIn(max = MORE_MENU_MAX_HEIGHT)
    if (screen.moreLinked && linked.isNotEmpty()) {
        MenuList(linked.map { entry -> MenuCustomItem { LinkedEntry(entry, close) } }, {}, menu, MenuItemSize.Regular, surface = MoreSurface, tabNavigation = true)
        return
    }
    val modMenu = rememberModMenu()
    val entries = screen.capture.more.filterNot { modMenu != null && it.modMenu }
    val game = if (routes) listOfNotNull(screen.capture.entry(PauseSlot.ADVANCEMENTS), screen.capture.entry(PauseSlot.STATS)) else emptyList()
    val mods = modMenu
    val routed = linked.takeIf { routes }.orEmpty()
    val items = buildList<MenuEntry> {
        game.forEach { entry -> add(MenuCustomItem { MoreEntry(entry, close) }) }
        mods?.let { (button, logo) ->
            add(
                MenuCustomItem {
                    MenuItem(
                        label = "Mods",
                        onClick = {
                            close()
                            button.onClick(screen)
                        },
                        icon = button.icon,
                        size = MenuItemSize.Regular,
                        tooltip = button.tooltip,
                        leading = logo?.let { { ModMenuLogo(it, SocialTextPrimary) } },
                    )
                },
            )
        }
        if (routed.isNotEmpty()) {
            if (game.isNotEmpty() || mods != null) add(MenuSeparator)
            add(MenuHeading(LinkedModMenus.displayName ?: LINKED_MOD_CAPTION))
            routed.forEach { entry -> add(MenuCustomItem { LinkedEntry(entry, close) }) }
        }
        if ((game.isNotEmpty() || mods != null || routed.isNotEmpty()) && entries.isNotEmpty()) add(MenuSeparator)
        entries.groupBy { it.section }.forEach { (section, rows) ->
            add(MenuHeading(section))
            rows.forEach { entry -> add(MenuCustomItem { MoreEntry(entry, close) }) }
        }
        if (entries.isNotEmpty()) add(MenuSeparator)
        add(
            MenuCustomItem {
                MenuItem(
                    label = "Classic pause menu",
                    onClick = screen::openClassic,
                    icon = "dots-grid",
                    size = MenuItemSize.Regular,
                    tooltip = uncapturedHint(screen.capture.uncaptured),
                )
            },
        )
    }
    MenuList(items, {}, menu, MenuItemSize.Regular, surface = MoreSurface, tabNavigation = true)
}

private val MoreSurface: @Composable (Modifier) -> Modifier = { it.pauseGlass() }

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
    Box(Modifier.matchParentSize().notFocusable().oneConfigClickable(null, { screen.moreOpen = false }, indication = false, pointerIcon = null))
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
    label: String? = tooltip,
    content: @Composable BoxScope.(hovered: Boolean) -> Unit,
) {
    val hover = rememberHoverState()
    val interaction = hover.interactionSource
    val hovered = hover.hovered
    TooltipBox(tooltip, placement = TooltipPlacement.AboveCenter, delayMillis = 0) {
        Box(
            modifier
                .height(LobbyLayout.BarHeight)
                .widthIn(min = LobbyLayout.BarHeight)
                .then(if (glass) Modifier.pauseGlass() else Modifier)
                .hoverable(interaction)
                .then(if (onClick != null) Modifier.oneConfigClickable(label, onClick, indication = false, interactionSource = interaction, focusRing = false) else Modifier)
                .padding(BAR_INSET),
            contentAlignment = Alignment.Center,
        ) {
            if (onClick != null) Box(Modifier.matchParentSize().rowFrame(false, hovered || active, OneConfigTheme.shape(BAR_INNER_RADIUS)))
            content(hovered)
        }
    }
}

private val CARD_ROW = 34.dp

@Composable
internal fun CardRow(
    modifier: Modifier = Modifier,
    tooltip: String? = null,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    label: String? = tooltip,
    content: @Composable RowScope.(lit: Boolean) -> Unit,
) {
    val hover = rememberHoverState()
    val interaction = hover.interactionSource
    val hovered = hover.hovered
    val lit = (hovered || active) && onClick != null
    TooltipBox(tooltip, placement = TooltipPlacement.AboveCenter, delayMillis = 0) {
        Row(
            modifier
                .fillMaxWidth()
                .height(CARD_ROW)
                .rowFrame(false, lit, OneConfigTheme.shape(10.dp))
                .hoverable(interaction)
                .then(if (onClick != null) Modifier.oneConfigClickable(label, onClick, indication = false, interactionSource = interaction, focusRing = false) else Modifier)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content(lit)
        }
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
    Text(
        text,
        modifier,
        color,
        OneConfigTheme.typography.body.copy(fontSize = size, fontWeight = weight, fontFeatureSettings = "tnum"),
        maxLines = 1,
        overflow = TextOverflow.Clip,
        softWrap = false,
        textAlign = align,
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

@Composable
internal fun rememberRiseAnchor(gap: Dp = 10.dp, centered: Boolean = false): PopupPositionProvider =
    rememberAnchoredPosition(if (centered) Placement.AboveCenter else Placement.AboveStart, gap)
