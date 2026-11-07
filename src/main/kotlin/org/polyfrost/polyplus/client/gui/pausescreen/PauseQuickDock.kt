@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableTextWithSound
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.Head
import org.polyfrost.polyplus.client.gui.lobbyshell.InviteButton
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.PartyInviteCards
import org.polyfrost.polyplus.client.gui.lobbyshell.VoiceButtons
import org.polyfrost.polyplus.client.gui.lobbyshell.rowFrame
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if = 1.8.9 {
/*import net.minecraft.client.render.Window
*///?}

private const val HUD_GUI_PIXELS = 48
private const val RISE_PIXELS = 24f

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
    val rise = (1f - reveal) * RISE_PIXELS
    val place = rememberPlace()
    val state = rememberPauseState()
    val lift = with(LocalDensity.current) { (HUD_GUI_PIXELS * guiScale()).toDp() }
    val party by PartyRepository.party.collectAsState()
    val groups by GroupsRepository.groups.collectAsState()
    val invites by PartyRepository.incomingInvites.collectAsState()
    Box(Modifier.fillMaxSize().clickableTextWithSound(screen::resume))
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
        if (screen.moreOpen) MorePanel(screen, Modifier.blockPointer())
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
            Keycap("Esc")
            Label("or click anywhere to resume", 12.sp, color = MutedText)
            Box(Modifier.width(1.dp).height(14.dp).background(SocialBorderColor))
            PauseStateLine(state)
        }
        Row(
            Modifier.blockPointer().height(72.dp).pauseGlass().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (narrow) 2.dp else 4.dp),
        ) {
            val current = party
            if (current != null || screen.online) {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (current != null) {
                        Head(ClientPlatform.localPlayerUuid(), 30.dp, realSkin = true)
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
            if (screen.online) DockItem(asset("message-chat-circle"), "Social", badge = groups.any { it.unread } || invites.isNotEmpty(), onClick = screen::openSocial)
            DockItem(asset("diamond-01"), "Wardrobe", onClick = screen::openWardrobe)
            screen.capture.entry(PauseSlot.OPTIONS)?.let { DockItem(it.glyph, "Options", enabled = it.enabled, onClick = it.press) }
            screen.capture.entry(PauseSlot.WORLD)?.let { DockItem(it.glyph, "World", enabled = it.enabled, onClick = it.press) }
            if (screen.canHost) DockItem(asset("log-in-04"), "Host", onClick = screen::openHost)
            DockItem(asset("dots-vertical"), "More", active = screen.moreOpen) { screen.moreOpen = !screen.moreOpen }
            DockRule()
            screen.capture.entry(PauseSlot.QUIT)?.let { DockItem(it.glyph, if (PauseWorldAccess.singleplayer) "Quit" else "Leave", danger = true, enabled = it.enabled, onClick = it.press) }
        }
    }
}

private fun Modifier.blockPointer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

@Composable
private fun DockRule() {
    Box(Modifier.width(1.dp).height(40.dp).background(SocialBorderColor))
}

@Composable
private fun DockResume(screen: OneClientPauseScreen, where: String, narrow: Boolean) {
    val (interaction, hovered) = rememberSocialHover()
    val accent = OneConfigTheme.accent
    val fill by animateColorAsState(if (hovered) lerp(accent, Color.White, 0.1f) else accent)
    val ink = OneConfigTheme.current.accentTextColor
    Column(
        Modifier
            .padding(horizontal = 4.dp)
            .width(if (narrow) 128.dp else 168.dp)
            .height(56.dp)
            .onGloballyPositioned { screen.resumeBounds = it.boundsInRoot() }
            .clip(ppShape(12.dp))
            .background(fill)
            .hoverable(interaction)
            .clickableWithSound(screen::resume),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Label("Resume", 18.sp, color = ink, weight = FontWeight.Medium)
        Label(where, 11.sp, color = ink.copy(alpha = 0.8f))
    }
}

@Composable
private fun DockItem(
    icon: String,
    label: String,
    width: Dp = 64.dp,
    active: Boolean = false,
    danger: Boolean = false,
    badge: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val (interaction, hovered) = rememberSocialHover()
    val ink = if (danger && hovered) SocialDangerText else SocialTextPrimary
    Box(
        Modifier
            .width(width)
            .height(56.dp)
            .rowFrame(false, hovered && enabled, ppShape(12.dp))
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
            PauseGlyph(icon, ink, 20.dp)
            Label(label, 11.sp, color = if (hovered || active) SocialTextPrimary else MutedText)
        }
        if (active) Box(Modifier.align(Alignment.BottomCenter).width(18.dp).height(2.dp).background(OneConfigTheme.accent))
        if (badge) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = 8.dp).width(8.dp).height(8.dp).background(OneConfigTheme.accent, OneConfigTheme.current.circleShape))
        }
    }
}
