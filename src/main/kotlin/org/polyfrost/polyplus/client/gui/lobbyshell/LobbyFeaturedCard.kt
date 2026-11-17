@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.featured.MainMenuFeaturedServer
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.rememberRemoteImage
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private val CardIconSize = 56.dp
private val CardActionHeight = 40.dp
private val CardActionLabel = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium)
private val OfferActionLabel = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
private val CardGutter = 12.dp

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
                Text("Prefer vanilla Minecraft?", Modifier.weight(1f), style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                IconButton(icon = SOCIAL_ASSETS + "x-close.svg", contentDescription = "Dismiss", onClick = onDismiss, modifier = Modifier.size(28.dp), tooltipPlacement = TooltipPlacement.BelowStart)
            }
            Text("Switch to the OneClient v1 main menu with Minecraft's own pause screen and startup. To return, press the OneClient logo in the top right of that menu.", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "Keep OneClient", onClick = onDismiss, modifier = Modifier.weight(1f).height(CardActionHeight), style = ControlStyle.Tinted, shape = OneConfigTheme.shape(12.dp), size = ControlSize.Large, fillWidth = true, labelStyle = OfferActionLabel, glass = false)
                TextButton(text = "Use vanilla Minecraft", onClick = MainMenuReplacement::useVanillaOffer, modifier = Modifier.weight(1f).height(CardActionHeight), style = ControlStyle.Tinted, shape = OneConfigTheme.shape(12.dp), size = ControlSize.Large, fillWidth = true, labelStyle = OfferActionLabel, glass = false)
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

    val dismissible = MainMenuFeaturedServer.isDismissible(server)
    Box(
        modifier
            .width(LobbyLayout.UnitWidth)
            .panel(),
    ) {
        Column(Modifier.fillMaxWidth().padding(CardGutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("SPONSORED", Modifier.weight(1f), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp), maxLines = 1, softWrap = false)
                if (dismissible) {
                    IconButton(icon = SOCIAL_ASSETS + "x-close.svg", contentDescription = "Dismiss", onClick = { MainMenuFeaturedServer.dismiss(server) }, modifier = Modifier.size(28.dp), tooltipPlacement = TooltipPlacement.BelowStart)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val shape = OneConfigTheme.shape(12.dp)
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
                    Text(server.name, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    CardStatus(data, revision)
                }
            }
            if (campaign.description.isNotBlank()) {
                Text(campaign.description, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(text = campaign.ctaLabel, onClick = { lobby.join(data) }, modifier = Modifier.fillMaxWidth().height(CardActionHeight), style = ControlStyle.Tinted, shape = OneConfigTheme.shape(12.dp), size = ControlSize.Large, labelStyle = CardActionLabel, glass = false)
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
        else -> CardStatusLine(OneConfigTheme.current.mutedText, "Checking status...")
    }
}

@Composable
private fun CardStatusLine(dot: Color, text: String, color: Color = SocialTextPrimary.copy(alpha = CARD_DETAIL_ALPHA), trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(OneConfigTheme.current.circleShape).background(dot))
        Text(text, Modifier.weight(1f, fill = false), color = color, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        trailing?.let { Text(it, color = SocialTextPrimary.copy(alpha = CARD_DETAIL_ALPHA), style = OneConfigTheme.current.typography.small, maxLines = 1, softWrap = false) }
    }
}
