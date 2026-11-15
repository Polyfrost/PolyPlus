@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.replays

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.polyplus.client.gui.lobbyshell.DangerTag
import org.polyfrost.oneconfig.api.ui.v1.components.ControlDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
import org.polyfrost.oneconfig.api.ui.v1.components.selectableCard
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.DetailRow
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LastPlayedStrip
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberCardClick
import org.polyfrost.polyplus.compat.replay.ReplayEntry
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone

private const val BANNER_FALLBACK_ALPHA = 0.22f
private const val PLACEHOLDER_SCALE = 0.42f
private val BannerHeight = 104.dp
private val StripThumbWidth = 71.dp
private val StripThumbHeight = 40.dp

internal const val VIDEO_ICON = SOCIAL_ASSETS + "video-recorder.svg"
internal const val FOLDER_ICON = SOCIAL_ASSETS + "link-external-01.svg"

internal fun replayMenuItems(entry: ReplayEntry, onWatch: (ReplayEntry) -> Unit): List<MenuAction> = listOfNotNull(
    MenuAction("Watch", VIDEO_ICON, enabled = entry.playable) { onWatch(entry) },
    MenuAction("Show in folder", FOLDER_ICON) { entry.provider.openFolderSafely() },
    MenuAction("Open ${entry.provider.name}", VIDEO_ICON) { entry.provider.openBrowserSafely() }.takeIf { ReplayLibrary.providers.size > 1 },
)

@Composable
internal fun ReplayThumb(entry: ReplayEntry, width: Dp, height: Dp) {
    val shape = OneConfigTheme.shape(if (minOf(width, height) > 48.dp) 12.dp else 8.dp)
    val icon = rememberReplayIcon(entry)
    Box(
        Modifier.size(width, height).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            ThemedIcon(VIDEO_ICON, OneConfigTheme.current.mutedText, minOf(width, height) * PLACEHOLDER_SCALE)
        }
    }
}

@Composable
private fun rememberReplayClick(entry: ReplayEntry, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit): () -> Unit =
    rememberCardClick({ if (entry.playable) onWatch(entry) }) { onSelect(entry) }

@Composable
private fun ReplayBanner(entry: ReplayEntry) {
    val icon = rememberReplayIcon(entry)
    Box(Modifier.fillMaxWidth().height(BannerHeight).background(OneConfigTheme.accent.copy(alpha = BANNER_FALLBACK_ALPHA)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            ThemedIcon(VIDEO_ICON, OneConfigTheme.current.mutedText, BannerHeight * PLACEHOLDER_SCALE)
        }
    }
}

@Composable
private fun ReplayTagItems(entry: ReplayEntry) {
    if (ReplayLibrary.providers.size > 1) Badge(entry.provider.name.uppercase(), overImage = true, style = BadgeStyle.Hairline)
    if (!entry.playable) DangerTag("CAN'T OPEN") else if (!entry.warning.isNullOrBlank()) DangerTag("MAY NOT LOAD")
}

@Composable
private fun ReplayTags(entry: ReplayEntry, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ReplayTagItems(entry)
    }
}

private fun ReplayEntry.metaText(): String = listOf(dateText, sizeText).joinToString(REPLAY_SEPARATOR)

@Composable
internal fun ReplayCard(entry: ReplayEntry, selected: Boolean, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit, modifier: Modifier) {
    val shape = OneConfigTheme.shape(16.dp)
    val click = rememberReplayClick(entry, onSelect, onWatch)
    Box(
        modifier
            .clip(shape)
            .background(CardSurface)
            .border(1.dp, SocialBorderColor, shape)
            .contextMenu { replayMenuItems(entry, onWatch) }
            .selectableCard(selected, click, entry.name, shape, recipe = ControlDefaults.Media),
    ) {
        Column(Modifier.fillMaxSize()) {
            ReplayBanner(entry)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.name, style = OneConfigTheme.current.typography.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                    entry.world?.takeIf { it.isNotBlank() }?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
                    Text(entry.metaText(), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                }
                TextButton(text = "Watch", onClick = { onWatch(entry) }, style = ControlStyle.Primary, size = ControlSize.Large, enabled = entry.playable)
            }
        }
        ReplayTags(entry, Modifier.padding(10.dp))
        entry.lengthText?.let { length ->
            Box(Modifier.align(Alignment.TopEnd).padding(top = BannerHeight - 28.dp, end = 10.dp)) { Badge(length.uppercase(), overImage = true, style = BadgeStyle.Hairline) }
        }
    }
}

@Composable
internal fun LatestReplayStrip(entry: ReplayEntry, selected: Boolean, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit) {
    LastPlayedStrip(
        tag = "Latest replay",
        name = entry.name,
        selected = selected,
        onClick = rememberReplayClick(entry, onSelect, onWatch),
        menu = { replayMenuItems(entry, onWatch) },
        actionLabel = "Watch",
        onAction = { onWatch(entry) },
        actionEnabled = entry.playable,
        thumb = { ReplayThumb(entry, StripThumbWidth, StripThumbHeight) },
        tags = { ReplayTagItems(entry) },
    ) {
        val detail = listOfNotNull(entry.world?.takeIf { it.isNotBlank() }, entry.lengthText, entry.dateText, entry.sizeText).joinToString(REPLAY_SEPARATOR)
        if (entry.playable) Text(detail, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) else Text(entry.problem, color = SocialDangerText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
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
            entry.world?.takeIf { it.isNotBlank() }?.let { Text(it, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entry.lengthText?.let { DetailRow("Length", it) }
            DetailRow("Recorded", entry.fullDateText)
            DetailRow("Size", entry.sizeText)
            DetailRow("Recorded with", entry.provider.name)
        }
        if (!entry.playable || !entry.warning.isNullOrBlank()) Text(entry.problem, color = SocialDangerText, style = OneConfigTheme.current.typography.small, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(text = "Watch", icon = VIDEO_ICON, onClick = { onWatch(entry) }, modifier = Modifier.fillMaxWidth(), style = ControlStyle.Primary, size = ControlSize.Large, enabled = entry.playable)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = "Show in folder", icon = FOLDER_ICON, onClick = { entry.provider.openFolderSafely() }, modifier = Modifier.weight(1f), style = ControlStyle.Secondary, size = ControlSize.Large)
                TextButton(text = "Open ${entry.provider.name}", icon = VIDEO_ICON, onClick = { entry.provider.openBrowserSafely() }, modifier = Modifier.weight(1f), style = ControlStyle.Secondary, size = ControlSize.Large)
            }
        }
    }
}
