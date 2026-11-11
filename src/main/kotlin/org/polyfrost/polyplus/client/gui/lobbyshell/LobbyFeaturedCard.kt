@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.featured.MainMenuFeaturedServer
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberRemoteImage
import org.polyfrost.polyplus.client.gui.rememberSocialHover

private val CardIconSize = 56.dp
private val CardActionHeight = 40.dp
private val CardGutter = 12.dp
private val OfferActionSize = 14.sp

private const val CARD_ACTION_ALPHA = 0.16f
private const val CARD_ACTION_HOVER_ALPHA = 0.28f
private const val CARD_ACTION_BORDER_ALPHA = 0.55f
private const val CARD_DETAIL_ALPHA = 0.82f

@Composable
internal fun LobbyCornerCard(lobby: LobbyShell, modifier: Modifier) {
    var offered by remember { mutableStateOf(!PolyPlusConfig.vanillaOfferDismissed) }
    if (offered) {
        VanillaOfferCard(modifier) {
            MainMenuReplacement.dismissVanillaOffer()
            offered = false
        }
    } else {
        FeaturedLobbyCard(lobby, modifier)
    }
}

@Composable
private fun VanillaOfferCard(modifier: Modifier, onDismiss: () -> Unit) {
    Box(modifier.width(LobbyLayout.UnitWidth).panel()) {
        Column(Modifier.fillMaxWidth().padding(CardGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                SocialText("Prefer vanilla Minecraft?", 16.sp, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(themedIcon(SOCIAL_ASSETS + "x-close.svg"), "Dismiss", Modifier.size(28.dp), onDismiss)
            }
            SocialText("Switch back to Minecraft's own main menu, pause screen and startup. To return, press the OneClient button in the top right of the title screen.", 12.sp, color = MutedText)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardAction("Keep OneClient", Modifier.weight(1f), OfferActionSize, onDismiss)
                CardAction("Use vanilla Minecraft", Modifier.weight(1f), OfferActionSize, MainMenuReplacement::returnToVanilla)
            }
        }
    }
}

@Composable
private fun FeaturedLobbyCard(lobby: LobbyShell, modifier: Modifier) {
    val snapshot by FeaturedServers.state.collectAsState()
    val server = MainMenuFeaturedServer.current(snapshot) ?: return
    val campaign = server.featured ?: return
    val servers = lobby.servers
    val data = remember(server.id, server.address) { servers.dataFor(server) }
    val revision = servers.revision
    LaunchedEffect(data) { servers.ping(data) }
    val catalogIcon = rememberRemoteImage(campaign.imageUrl ?: FeaturedServers.iconUrl(server.id))

    Box(modifier.width(LobbyLayout.UnitWidth).panel()) {
        Column(Modifier.fillMaxWidth().padding(CardGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                SocialText("SPONSORED", 11.sp, Modifier.weight(1f), color = MutedText, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp, maxLines = 1)
                if (MainMenuFeaturedServer.isDismissible(server)) {
                    IconButton(themedIcon(SOCIAL_ASSETS + "x-close.svg"), "Dismiss", Modifier.size(28.dp)) { MainMenuFeaturedServer.dismiss(server) }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val shape = ppShape(12.dp)
                Box(if (OneConfigTheme.current.shadowEnabled) Modifier.shadow(8.dp, shape, ambientColor = OneConfigTheme.current.shadowColor, spotColor = OneConfigTheme.current.shadowColor) else Modifier) {
                    if (catalogIcon != null) {
                        Image(
                            catalogIcon,
                            null,
                            Modifier.size(CardIconSize).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape),
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.Medium,
                        )
                    } else {
                        ServerIcon(data, revision, CardIconSize)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SocialText(server.name, 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CardStatus(data, revision)
                }
            }
            if (campaign.description.isNotBlank()) {
                SocialText(campaign.description, 12.sp, color = MutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            CardAction(campaign.ctaLabel) { lobby.join(data) }
        }
    }
}

@Composable
private fun CardStatus(data: ServerData, revision: Int) {
    @Suppress("UNUSED_EXPRESSION") revision
    ServerRevisions.of(data)
    when {
        LobbyServers.failed(data) -> CardStatusLine(SocialDangerColor, "Offline", SocialDangerText)
        data.state() == ServerData.State.INCOMPATIBLE -> CardStatusLine(SocialDangerColor, "Incompatible, ${Platform.i18n().getUnformattedText(data.version)}")
        data.state() == ServerData.State.SUCCESSFUL -> CardStatusLine(
            SocialSuccessColor,
            data.players?.let { "%,d online".format(it.online()) } ?: "Online",
            SocialTextPrimary,
            trailing = "${data.ping} ms",
        )
        else -> CardStatusLine(MutedText, "Checking status...")
    }
}

@Composable
private fun CardStatusLine(dot: Color, text: String, color: Color = SocialTextPrimary.copy(alpha = CARD_DETAIL_ALPHA), trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(OneConfigTheme.current.circleShape).background(dot))
        SocialText(text, 12.sp, Modifier.weight(1f, fill = false), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.let { SocialText(it, 12.sp, color = SocialTextPrimary.copy(alpha = CARD_DETAIL_ALPHA), maxLines = 1) }
    }
}

@Composable
private fun CardAction(label: String, modifier: Modifier = Modifier, size: TextUnit = 15.sp, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    val accent = OneConfigTheme.accent
    val shape = ppShape(12.dp)
    val fill by animateColorAsState(accent.copy(alpha = if (hovered) CARD_ACTION_HOVER_ALPHA else CARD_ACTION_ALPHA))
    val edge by animateColorAsState(accent.copy(alpha = if (hovered) 1f else CARD_ACTION_BORDER_ALPHA))
    Box(
        modifier
            .fillMaxWidth()
            .height(CardActionHeight)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .hoverable(interaction)
            .clickableWithSound(label, shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Label(label, size, weight = FontWeight.Medium)
    }
}
