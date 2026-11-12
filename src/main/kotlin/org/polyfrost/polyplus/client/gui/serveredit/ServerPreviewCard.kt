@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.serveredit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyServers
import org.polyfrost.polyplus.client.gui.lobbyshell.ServerBanner
import org.polyfrost.polyplus.client.gui.lobbyshell.ServerIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.Tag
import org.polyfrost.polyplus.client.gui.lobbyshell.TagTone
import org.polyfrost.polyplus.client.gui.lobbyshell.motdText

private val BannerHeight = 108.dp
private val IconSize = 56.dp
private val IconOverhang = 16.dp

private enum class PreviewStatus(val tag: String?) {
    Idle(null),
    Checking("Checking"),
    Online("Online"),
    Incompatible("Incompatible"),
    Offline("Offline"),
}

private fun previewStatus(data: ServerData, addressValid: Boolean): PreviewStatus = when {
    !addressValid -> PreviewStatus.Idle
    LobbyServers.failed(data) -> PreviewStatus.Offline
    data.state() == ServerData.State.SUCCESSFUL -> PreviewStatus.Online
    data.state() == ServerData.State.INCOMPATIBLE -> PreviewStatus.Incompatible
    else -> PreviewStatus.Checking
}

@Composable
internal fun ServerPreviewCard(name: String, addressValid: Boolean, probe: ServerStatusProbe, modifier: Modifier = Modifier) {
    val data = probe.data
    val revision = probe.revision
    val status = previewStatus(data, addressValid)
    val shape = ppShape(16.dp)
    Box(modifier.clip(shape).background(CardSurface).border(1.dp, SocialBorderColor, shape)) {
        Column(Modifier.fillMaxSize()) {
            ServerBanner(data, revision, BannerHeight)
            Column(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = IconOverhang + 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SocialText(name, 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MetaLine(data, status)
                if (status == PreviewStatus.Online || status == PreviewStatus.Incompatible) MotdLines(data)
            }
        }
        status.tag?.let {
            val tone = if (status == PreviewStatus.Offline || status == PreviewStatus.Incompatible) TagTone.Danger else TagTone.Dark
            Tag(it, tone, Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
        Box(Modifier.offset(x = 12.dp, y = BannerHeight - IconSize + IconOverhang)) { ServerIcon(data, revision, IconSize) }
    }
}

@Composable
private fun MetaLine(data: ServerData, status: PreviewStatus) {
    val muted = MutedText
    val text = when (status) {
        PreviewStatus.Idle -> AnnotatedString("Enter an address to check status")
        PreviewStatus.Checking -> AnnotatedString("Checking status...")
        PreviewStatus.Offline -> AnnotatedString(Platform.i18n().getUnformattedText(data.motd), SpanStyle(color = SocialDangerText))
        else -> buildAnnotatedString {
            data.players?.let { append("%,d online · ".format(it.online())) }
            append("${data.ping} ms · ")
            withStyle(SpanStyle(color = if (status == PreviewStatus.Incompatible) SocialDangerText else muted)) {
                append(Platform.i18n().getUnformattedText(data.version))
            }
        }
    }
    PreviewText(text, muted, maxLines = 1)
}

@Composable
private fun MotdLines(data: ServerData) {
    val muted = MutedText
    PreviewText(motdText(data.motd, muted, OneConfigTheme.current.textColor, OneConfigTheme.current.popupBackground), muted, maxLines = 2)
}

@Composable
private fun PreviewText(text: AnnotatedString, color: Color, maxLines: Int) {
    BasicText(
        text,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = 12.sp, fontFamily = OneConfigTheme.current.fontFamily),
    )
}
