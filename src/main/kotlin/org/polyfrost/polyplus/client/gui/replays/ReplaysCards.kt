@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.replays

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.ContextItem
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.contextMenu
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.Tag
import org.polyfrost.polyplus.client.gui.lobbyshell.TagTone
import org.polyfrost.polyplus.client.gui.lobbyshell.cardFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberDoubleClick
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.compat.replay.ReplayEntry

private const val BANNER_FALLBACK_ALPHA = 0.22f
private const val PLACEHOLDER_SCALE = 0.42f
private const val STRIP_WASH_ALPHA = 0.35f
private val BannerHeight = 104.dp
private val StripHeight = 60.dp
private val StripThumbWidth = 71.dp
private val StripThumbHeight = 40.dp

internal const val VIDEO_ICON = SOCIAL_ASSETS + "video-recorder.svg"
internal const val FOLDER_ICON = SOCIAL_ASSETS + "link-external-01.svg"

internal fun replayMenuItems(entry: ReplayEntry, onWatch: (ReplayEntry) -> Unit): List<ContextItem> = listOfNotNull(
    ContextItem("Watch", VIDEO_ICON, enabled = entry.playable) { onWatch(entry) },
    ContextItem("Show in folder", FOLDER_ICON) { entry.provider.openFolderSafely() },
    ContextItem("Open ${entry.provider.name}", VIDEO_ICON) { entry.provider.openBrowserSafely() }.takeIf { ReplayLibrary.providers.size > 1 },
)

@Composable
internal fun ReplayThumb(entry: ReplayEntry, width: Dp, height: Dp) {
    val shape = ppShape(if (minOf(width, height) > 48.dp) 12.dp else 8.dp)
    val icon = ReplayLibrary.icon(entry)
    Box(
        Modifier.size(width, height).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            ThemedIcon(VIDEO_ICON, MutedText, minOf(width, height) * PLACEHOLDER_SCALE)
        }
    }
}

@Composable
private fun rememberCardClick(entry: ReplayEntry, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit): () -> Unit {
    val doubleClick = rememberDoubleClick { if (entry.playable) onWatch(entry) }
    return {
        doubleClick()
        onSelect(entry)
    }
}

@Composable
private fun ReplayBanner(entry: ReplayEntry) {
    val icon = ReplayLibrary.icon(entry)
    Box(Modifier.fillMaxWidth().height(BannerHeight).background(OneConfigTheme.accent.copy(alpha = BANNER_FALLBACK_ALPHA)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            ThemedIcon(VIDEO_ICON, MutedText, BannerHeight * PLACEHOLDER_SCALE)
        }
    }
}

@Composable
private fun ReplayTags(entry: ReplayEntry, modifier: Modifier = Modifier, lead: (@Composable () -> Unit)? = null) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        lead?.invoke()
        if (ReplayLibrary.providers.size > 1) Tag(entry.provider.name)
        if (!entry.playable) Tag("Can't open", TagTone.Danger)
    }
}

private fun ReplayEntry.metaText(): String = listOf(dateText, sizeText).joinToString(REPLAY_SEPARATOR)

@Composable
internal fun ReplayCard(entry: ReplayEntry, selected: Boolean, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit, modifier: Modifier) {
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .cardFrame(selected, hovered)
            .contextMenu { replayMenuItems(entry, onWatch) }
            .hoverable(interaction)
            .clickableWithSound(rememberCardClick(entry, onSelect, onWatch)),
    ) {
        Column(Modifier.fillMaxSize()) {
            ReplayBanner(entry)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label(entry.name, 16.sp, weight = FontWeight.Medium)
                    entry.world?.takeIf { it.isNotBlank() }?.let { Label(it, 12.sp, color = MutedText) }
                    Label(entry.metaText(), 12.sp, color = MutedText)
                }
                SocialButton("Watch", filled = true, enabled = entry.playable) { onWatch(entry) }
            }
        }
        ReplayTags(entry, Modifier.padding(10.dp))
        entry.lengthText?.let { length ->
            Box(Modifier.align(Alignment.TopEnd).padding(top = BannerHeight - 28.dp, end = 10.dp)) { Tag(length) }
        }
    }
}

@Composable
internal fun LatestReplayStrip(entry: ReplayEntry, selected: Boolean, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(StripHeight)
            .cardFrame(selected, hovered)
            .background(Brush.horizontalGradient(listOf(OneConfigTheme.accent.copy(alpha = STRIP_WASH_ALPHA), Color.Transparent)))
            .contextMenu { replayMenuItems(entry, onWatch) }
            .hoverable(interaction)
            .clickableWithSound(rememberCardClick(entry, onSelect, onWatch))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ReplayThumb(entry, StripThumbWidth, StripThumbHeight)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplayTags(entry) { Tag("Latest replay", TagTone.Accent) }
                Label(entry.name, 15.sp, Modifier.weight(1f, fill = false), weight = FontWeight.Medium)
            }
            val detail = listOfNotNull(entry.world?.takeIf { it.isNotBlank() }, entry.lengthText, entry.dateText, entry.sizeText).joinToString(REPLAY_SEPARATOR)
            if (entry.playable) Label(detail, 12.sp, color = MutedText) else Label(entry.problem, 12.sp, color = SocialDangerText)
        }
        SocialButton("Watch", filled = true, enabled = entry.playable) { onWatch(entry) }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, 12.sp, Modifier.weight(1f), color = MutedText)
        Label(value, 12.sp)
    }
}

@Composable
internal fun ReplayDetailsPanel(entry: ReplayEntry?, onWatch: (ReplayEntry) -> Unit, modifier: Modifier) {
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (entry == null) {
            EmptyState("No replay selected", Modifier.fillMaxSize(), "Select a replay to see its details.")
            return@Column
        }
        ReplayThumb(entry, StripThumbWidth * 4, StripThumbHeight * 4)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PanelTitle(entry.name)
            entry.world?.takeIf { it.isNotBlank() }?.let { Label(it, 12.sp, color = MutedText) }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.lengthText?.let { DetailRow("Length", it) }
            DetailRow("Recorded", entry.fullDateText)
            DetailRow("Size", entry.sizeText)
            DetailRow("Recorded with", entry.provider.name)
        }
        if (!entry.playable) SocialText(entry.problem, 12.sp, color = SocialDangerText, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialButton("Watch", icon = VIDEO_ICON, modifier = Modifier.fillMaxWidth(), filled = true, enabled = entry.playable) { onWatch(entry) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialButton("Show in folder", icon = FOLDER_ICON, modifier = Modifier.weight(1f)) { entry.provider.openFolderSafely() }
                SocialButton("Open ${entry.provider.name}", icon = VIDEO_ICON, modifier = Modifier.weight(1f)) { entry.provider.openBrowserSafely() }
            }
        }
    }
}
