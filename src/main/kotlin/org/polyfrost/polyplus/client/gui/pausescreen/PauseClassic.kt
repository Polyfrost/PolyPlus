@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.StatusDot
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.socialDotLabel
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.GroupsRepository

private val CLASSIC_WIDTH = 408.dp
private val CLASSIC_BUTTON = 40.dp
private val CLASSIC_GAP = 8.dp
private val CLASSIC_EXTRAS_GAP = 24.dp
private val CLASSIC_EXTRA = 36.dp
private val CLASSIC_TITLE = 64.dp
private val CLASSIC_MARGIN = 20.dp
private val CLASSIC_CUSTOMIZE_DROP = 46.dp
private const val CLASSIC_ROWS = 5
private const val CLASSIC_DIM = 0.35f
private const val CLASSIC_RISE = 16f
private const val DANGER_REST = 0.1f
private const val DANGER_HOVER = 0.2f

private class ClassicLayout(val help: List<PauseEntry>, val beside: PauseEntry?) {
    val hidden: Set<Int> = (help + listOfNotNull(beside)).mapTo(HashSet()) { it.key }
}

private fun classicLayout(entries: List<PauseEntry>): ClassicLayout {
    val game = entries.indexOfLast { it.slot == PauseSlot.ADVANCEMENTS || it.slot == PauseSlot.STATS }
    val options = entries.indexOfFirst { it.slot == PauseSlot.OPTIONS }
    val help = if (game >= 0 && options > game) entries.subList(game + 1, options).filter { it.slot == PauseSlot.MORE }.take(2) else emptyList()
    val beside = if (options < 0 || entries.any { it.slot == PauseSlot.WORLD }) null else entries.getOrNull(options + 1)?.takeIf { it.slot == PauseSlot.MORE }
    return ClassicLayout(help, beside)
}

@Composable
internal fun BoxScope.ClassicScreen(screen: PauseView, narrow: Boolean, away: Float = 0f) {
    val animate = rememberLive { PolyPlusPauseConfig.classicOpenAnimation }
    val reveal = (if (animate) rememberReveal() else 1f) * (1f - away)
    val rise = if (Motion.reduced) 0f else (1f - reveal) * CLASSIC_RISE
    val widgetsUp = rememberLive { PolyPlusPauseConfig.classicWidgetCorner } == PauseWidgetCorner.TOP_RIGHT
    val place = rememberPlace()
    val state = rememberPauseState()
    val layout = classicLayout(screen.capture.entries)
    Box(Modifier.matchParentSize().graphicsLayer { alpha = reveal }.background(OneConfigTheme.current.pageBackground.copy(alpha = CLASSIC_DIM)))
    BoxWithConstraints(Modifier.matchParentSize()) {
        val block = CLASSIC_TITLE + CLASSIC_BUTTON * CLASSIC_ROWS + CLASSIC_GAP * (CLASSIC_ROWS - 1) + CLASSIC_EXTRAS_GAP + CLASSIC_EXTRA
        val top = ((maxHeight - block) / 2).coerceAtLeast(CLASSIC_MARGIN)
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = top)
                .width(CLASSIC_WIDTH)
                .graphicsLayer {
                    alpha = reveal
                    translationY = rise * density
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ClassicHeader(place.title, state, Modifier.height(CLASSIC_TITLE))
            ClassicGrid(screen, layout)
            Spacer(Modifier.height(CLASSIC_EXTRAS_GAP))
            ClassicExtras(screen, layout)
        }
        PauseWidgetColumn(
            screen,
            pauseWidgetWidth(narrow),
            (maxHeight - CLASSIC_MARGIN * 2 - if (widgetsUp) 0.dp else CLASSIC_CUSTOMIZE_DROP).coerceAtLeast(0.dp),
            Modifier
                .align(if (widgetsUp) Alignment.TopEnd else Alignment.BottomStart)
                .padding(
                    top = if (widgetsUp) CLASSIC_MARGIN else 0.dp,
                    bottom = if (widgetsUp) 0.dp else CLASSIC_MARGIN,
                    start = if (widgetsUp) 0.dp else CLASSIC_MARGIN,
                    end = if (widgetsUp) CLASSIC_MARGIN else 0.dp,
                )
                .graphicsLayer {
                    alpha = reveal
                    translationY = (if (widgetsUp) -rise else rise) * density
                }
                .blockPointer(),
        )
    }
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(top = CLASSIC_MARGIN, start = CLASSIC_MARGIN)
            .graphicsLayer {
                alpha = reveal
                translationY = -rise * density
            }
            .blockPointer()
            .pauseGlass(12.dp)
            .padding(3.dp),
    ) {
        CustomizeButton(screen)
    }
    if (screen.moreOpen) MoreDismiss(screen)
    if (screen.customizeOpen) {
        CustomizeDismiss(screen)
        PauseCustomizePanel(screen, Modifier.align(Alignment.TopStart).padding(top = CLASSIC_MARGIN + CLASSIC_CUSTOMIZE_DROP, start = CLASSIC_MARGIN))
    }
}

@Composable
private fun ClassicHeader(title: String, state: PauseState, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Text(
            title,
            color = SocialTextPrimary,
            style = OneConfigTheme.current.typography.title.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        PauseStateLine(state)
    }
}

@Composable
private fun ClassicGrid(screen: PauseView, layout: ClassicLayout) {
    val capture = screen.capture
    Column(Modifier.fillMaxWidth().blockPointer(), verticalArrangement = Arrangement.spacedBy(CLASSIC_GAP)) {
        ClassicButton(
            capture.entry(PauseSlot.RESUME)?.label ?: "Back to Game",
            Modifier.fillMaxWidth().onGloballyPositioned { screen.resumeBounds = it.boundsInRoot() },
            accent = true,
            onClick = screen::resume,
        )
        ClassicRow {
            EntryButton(capture.entry(PauseSlot.ADVANCEMENTS))
            EntryButton(capture.entry(PauseSlot.STATS))
        }
        ClassicRow {
            if (layout.help.isNotEmpty()) {
                layout.help.forEach { EntryButton(it) }
            } else {
                ClassicButton("Mods", Modifier.weight(1f), onClick = screen::openMods)
                MoreButton(screen, layout, Modifier.weight(1f))
            }
        }
        ClassicRow {
            EntryButton(capture.entry(PauseSlot.OPTIONS))
            val world = capture.entry(PauseSlot.WORLD) ?: layout.beside
            if (world != null) {
                EntryButton(world)
            } else if (screen.canHost) {
                ClassicButton(PauseWorldAccess.hostLabel, Modifier.weight(1f), onClick = screen::openHost)
            }
        }
        capture.entry(PauseSlot.QUIT)?.let {
            ClassicButton(it.label, Modifier.fillMaxWidth(), enabled = it.enabled, tooltip = it.tooltip, danger = true, onClick = it.press)
        }
    }
}

@Composable
private fun ClassicRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(CLASSIC_BUTTON), horizontalArrangement = Arrangement.spacedBy(CLASSIC_GAP), content = content)
}

@Composable
private fun RowScope.EntryButton(entry: PauseEntry?) {
    if (entry == null) return
    ClassicButton(entry.label, Modifier.weight(1f), enabled = entry.enabled, tooltip = entry.tooltip, onClick = entry.press)
}

@Composable
private fun ClassicExtras(screen: PauseView, layout: ClassicLayout) {
    val groups by GroupsRepository.groups.collectAsState()
    val invites by PartyRepository.incomingInvites.collectAsState()
    val hostInGrid = screen.capture.entry(PauseSlot.WORLD) == null && layout.beside == null
    Row(Modifier.wrapContentWidth(unbounded = true).blockPointer(), horizontalArrangement = Arrangement.spacedBy(CLASSIC_GAP), verticalAlignment = Alignment.CenterVertically) {
        if (screen.online) {
            ExtraButton(asset("message-chat-circle"), "Social", badge = socialDotLabel(groups.any { it.unread }, invites.isNotEmpty()), onClick = screen::openSocial)
        }
        ExtraButton(asset("diamond-01"), "Wardrobe", onClick = screen::openWardrobe)
        if (screen.canHost && !hostInGrid) ExtraButton(asset("log-in-04"), "Host", onClick = screen::openHost)
        if (layout.help.isNotEmpty()) {
            ExtraButton("settings", "Mods", onClick = screen::openMods)
            Box(Modifier.focusRequester(rememberMoreTrigger(screen.moreOpen && !screen.moreLinked))) {
                ExtraButton(asset("dots-vertical"), "More", active = screen.moreOpen) { toggleMore(screen) }
                if (screen.moreOpen) MorePopup(screen, rememberToolAnchor(true), routes = true, game = false, hidden = layout.hidden)
            }
        }
    }
}

@Composable
private fun MoreButton(screen: PauseView, layout: ClassicLayout, modifier: Modifier) {
    Box(modifier.focusRequester(rememberMoreTrigger(screen.moreOpen && !screen.moreLinked))) {
        ClassicButton("More", Modifier.fillMaxWidth()) { toggleMore(screen) }
        if (screen.moreOpen) MorePopup(screen, rememberToolAnchor(true), routes = true, game = false, hidden = layout.hidden)
    }
}

private fun toggleMore(screen: PauseView) {
    screen.moreLinked = false
    screen.moreOpen = !screen.moreOpen
}

@Composable
private fun ClassicButton(
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    tooltip: String? = null,
    accent: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val hover = rememberHoverState()
    val interaction = hover.interactionSource
    val lit = hover.hovered && enabled
    val shape = OneConfigTheme.shape(10.dp)
    val disabledAlpha = OneConfigTheme.current.disabledAlpha
    val ink = when {
        accent -> OneConfigTheme.current.accentTextColor
        danger -> SocialDangerText
        else -> SocialTextPrimary
    }
    TooltipBox(tooltip, modifier, placement = TooltipPlacement.AboveCenter, delayMillis = 0) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CLASSIC_BUTTON)
                .then(if (accent) Modifier.clip(shape).background(OneConfigTheme.accent, shape) else Modifier.pauseGlass(10.dp))
                .then(if (danger) Modifier.background(SocialDangerText.copy(alpha = if (lit) DANGER_HOVER else DANGER_REST), shape) else Modifier)
                .rowFrame(false, lit, shape)
                .graphicsLayer { alpha = if (enabled) 1f else disabledAlpha }
                .hoverable(interaction)
                .oneConfigClickable(label, onClick, enabled = enabled, indication = false, interactionSource = interaction)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = ink,
                style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ExtraButton(
    icon: String,
    label: String,
    active: Boolean = false,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val hover = rememberHoverState()
    val interaction = hover.interactionSource
    val shape = OneConfigTheme.shape(10.dp)
    Box {
        Row(
            Modifier
                .height(CLASSIC_EXTRA)
                .pauseGlass(10.dp)
                .rowFrame(false, hover.hovered || active, shape)
                .hoverable(interaction)
                .oneConfigClickable(badge?.let { "$label, $it" } ?: label, onClick, indication = false, interactionSource = interaction)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PauseGlyph(icon, SocialTextPrimary, 16.dp)
            Text(label, color = SocialTextPrimary, style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium), maxLines = 1, softWrap = false)
        }
        if (badge != null) StatusDot(BadgeTone.Accent, Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp), size = 8.dp)
    }
}
