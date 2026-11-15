@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.StatusDot
import org.polyfrost.oneconfig.api.ui.v1.components.KeyCap
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.lobbyshell.InviteButton
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyInviteCards
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.socialDotLabel
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

//? if = 1.8.9 {
/*import net.minecraft.client.render.Window
*///?}

private const val HUD_GUI_PIXELS = 48
private const val RISE_PIXELS = 24f
private val DOCK_HEIGHT = 72.dp
private val CARD_MARGIN = 20.dp
private val CARD_WIDTH = 236.dp
private val DOCK_ITEM_WIDTH = 64.dp
private val DOCK_ITEM_HEIGHT = 56.dp
private val CUSTOMIZE_DROP = 46.dp

private fun guiScale(): Int {
    //? if > 1.8.9 {
    return Minecraft.getInstance().window.guiScale.toInt()
    //?} else {
    /*return Window(Minecraft.getInstance()).scale
    *///?}
}

@Composable
internal fun BoxScope.QuickDockScreen(screen: OneClientPauseScreen, narrow: Boolean) {
    val reveal = rememberReveal()
    val rise = if (Motion.reduced) 0f else (1f - reveal) * RISE_PIXELS
    val place = rememberPlace()
    val state = rememberPauseState()
    val lift = with(LocalDensity.current) { (HUD_GUI_PIXELS * guiScale()).toDp() }
    val party by PartyRepository.party.collectAsState()
    val groups by GroupsRepository.groups.collectAsState()
    val invites by PartyRepository.incomingInvites.collectAsState()
    Box(Modifier.fillMaxSize().notFocusable().oneConfigClickable(null, { if (screen.moreOpen) screen.moreOpen = false else screen.resume() }, indication = false, pointerIcon = null))
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = lift + 12.dp)
            .graphicsLayer {
                alpha = reveal
                translationY = rise * density
            },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterHorizontally)
                .blockPointer()
                .height(30.dp)
                .pauseGlass(8.dp)
                .padding(start = 6.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyCap("Esc")
            Text("or click anywhere to resume", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Divider(Modifier.height(14.dp), vertical = true)
            PauseStateLine(state)
        }
        Row(
            Modifier.blockPointer().height(DOCK_HEIGHT).pauseGlass().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 2.dp else 4.dp),
        ) {
            val current = party
            if (current != null || screen.online) {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (current != null) {
                        PlayerHead(playerId = ClientPlatform.localPlayerUuid(), size = 30.dp, modifier = Modifier, shape = headShape(30.dp))
                        VoiceButtons(current)
                    }
                    if (screen.online) {
                        InviteButton(current)
                        PartyInviteCards(PauseHost)
                    }
                }
                DockRule()
            }
            DockResume(screen, place.title, narrow)
            DockRule()
            if (screen.online) DockItem(asset("message-chat-circle"), "Social", badge = socialDotLabel(groups.any { it.unread }, invites.isNotEmpty()), onClick = screen::openSocial)
            DockItem(asset("diamond-01"), "Wardrobe", onClick = screen::openWardrobe)
            screen.capture.entry(PauseSlot.OPTIONS)?.let { DockItem(it.glyph, "Options", enabled = it.enabled, onClick = it.press) }
            screen.capture.entry(PauseSlot.WORLD)?.let { DockItem(it.glyph, "World", enabled = it.enabled, onClick = it.press) }
            if (screen.canHost) DockItem(asset("log-in-04"), "Host", onClick = screen::openHost)
            Box(Modifier.focusRequester(rememberMoreTrigger(screen.moreOpen))) {
                DockItem(asset("dots-vertical"), "More", active = screen.moreOpen) { screen.moreLinked = false; screen.moreOpen = !screen.moreOpen }
                if (screen.moreOpen) MorePopup(screen, rememberRiseAnchor(centered = true), routes = true)
            }
            DockRule()
            screen.capture.entry(PauseSlot.QUIT)?.let { DockItem(it.glyph, if (it.relabelled) it.label else if (PauseWorldAccess.singleplayer) "Quit" else "Leave", danger = true, enabled = it.enabled, onClick = it.press) }
        }
    }
    ToolsCard(screen, reveal, rise)
    Box(
        Modifier
            .align(Alignment.TopStart)
            .padding(top = CARD_MARGIN, start = CARD_MARGIN)
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
    if (screen.customizeOpen) {
        CustomizeDismiss(screen)
        PauseCustomizePanel(screen, Modifier.align(Alignment.TopStart).padding(top = CARD_MARGIN + CUSTOMIZE_DROP, start = CARD_MARGIN))
    }
}

@Composable
private fun BoxScope.ToolsCard(screen: OneClientPauseScreen, reveal: Float, rise: Float) {
    PauseWidgetCard(
        screen,
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = CARD_MARGIN, end = CARD_MARGIN)
            .graphicsLayer {
                alpha = reveal
                translationY = -rise * density
            }
            .blockPointer(),
        CARD_WIDTH,
    )
}

internal fun Modifier.blockPointer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

@Composable
private fun DockRule() {
    Divider(Modifier.height(40.dp), vertical = true)
}

@Composable
private fun DockResume(screen: OneClientPauseScreen, where: String, narrow: Boolean) {
    val shape = OneConfigTheme.shape(12.dp)
    val ink = OneConfigTheme.current.accentTextColor
    Column(
        Modifier
            .padding(horizontal = 4.dp)
            .width(if (narrow) 128.dp else 168.dp)
            .height(56.dp)
            .onGloballyPositioned { screen.resumeBounds = it.boundsInRoot() }
            .clip(shape)
            .background(OneConfigTheme.accent)
            .oneConfigClickable("Resume", screen::resume, shape = shape, recipe = ButtonDefaults.Lift),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text("Resume", color = ink, style = OneConfigTheme.current.typography.title.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(where, color = ink.copy(alpha = 0.8f), style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun DockItem(
    icon: String,
    label: String,
    width: Dp = 64.dp,
    active: Boolean = false,
    danger: Boolean = false,
    badge: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DockTile(badge?.let { "$label, $it" } ?: label, width, active, enabled, onClick = onClick) { hovered ->
        DockGlyphLabel(label, hovered || active) { PauseGlyph(icon, if (danger && hovered) SocialDangerText else SocialTextPrimary, 20.dp) }
        if (badge != null) {
            StatusDot(BadgeTone.Accent, Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 8.dp), size = 8.dp)
        }
    }
}

@Composable
private fun DockTile(
    label: String,
    width: Dp? = DOCK_ITEM_WIDTH,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.(hovered: Boolean) -> Unit,
) {
    val hoverState = rememberHoverState()
    val interaction = hoverState.interactionSource
    val hovered = hoverState.hovered
    val live = enabled && onClick != null
    val disabledAlpha = OneConfigTheme.current.disabledAlpha
    Box(
        Modifier
            .then(if (width != null) Modifier.width(width) else Modifier.widthIn(min = DOCK_ITEM_WIDTH))
            .height(DOCK_ITEM_HEIGHT)
            .rowFrame(false, hovered && live, OneConfigTheme.shape(12.dp))
            .graphicsLayer { alpha = if (enabled) 1f else disabledAlpha }
            .hoverable(interaction)
            .then(if (live && onClick != null) Modifier.oneConfigClickable(label, onClick, indication = false, interactionSource = interaction) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content(hovered)
        if (active) Box(Modifier.align(Alignment.BottomCenter).width(18.dp).height(2.dp).background(OneConfigTheme.accent))
    }
}

@Composable
private fun DockGlyphLabel(label: String, highlighted: Boolean, glyph: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        glyph()
        Text(label, color = if (highlighted) SocialTextPrimary else OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}
