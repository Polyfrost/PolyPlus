@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.replays

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.contextMenu
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.DetailRow
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LastPlayedStrip
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.Tag
import org.polyfrost.polyplus.client.gui.lobbyshell.TagTone
import org.polyfrost.polyplus.client.gui.lobbyshell.cardFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberCardClick
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.compat.replay.ReplayEntry

private const val BANNER_FALLBACK_ALPHA = 0.22f
private const val PLACEHOLDER_SCALE = 0.42f
private val BannerHeight = 104.dp
private val StripThumbWidth = 71.dp
private val StripThumbHeight = 40.dp
internal val BUTTON_HEIGHT = 38.dp

internal const val VIDEO_ICON = SOCIAL_ASSETS + "video-recorder.svg"
internal const val FOLDER_ICON = SOCIAL_ASSETS + "link-external-01.svg"

internal fun replayMenuItems(entry: ReplayEntry, onWatch: (ReplayEntry) -> Unit): List<MenuAction> = listOfNotNull(
    MenuAction("Watch", VIDEO_ICON, enabled = entry.playable) { onWatch(entry) },
    MenuAction("Show in folder", FOLDER_ICON) { entry.provider.openFolderSafely() },
    MenuAction("Open ${entry.provider.name}", VIDEO_ICON) { entry.provider.openBrowserSafely() }.takeIf { ReplayLibrary.providers.size > 1 },
)

@Composable
internal fun ReplayThumb(entry: ReplayEntry, width: Dp, height: Dp) {
    val shape = ppShape(if (minOf(width, height) > 48.dp) 12.dp else 8.dp)
    val icon = rememberReplayIcon(entry)
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
private fun rememberReplayClick(entry: ReplayEntry, onSelect: (ReplayEntry) -> Unit, onWatch: (ReplayEntry) -> Unit): () -> Unit =
    rememberCardClick({ if (entry.playable) onWatch(entry) }) { onSelect(entry) }

@Composable
private fun ReplayBanner(entry: ReplayEntry) {
    val icon = rememberReplayIcon(entry)
    Box(Modifier.fillMaxWidth().height(BannerHeight).background(OneConfigTheme.accent.copy(alpha = BANNER_FALLBACK_ALPHA)), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(icon, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium)
        } else {
            ThemedIcon(VIDEO_ICON, MutedText, BannerHeight * PLACEHOLDER_SCALE)
        }
    }
}

@Composable
private fun ReplayTagItems(entry: ReplayEntry) {
    if (ReplayLibrary.providers.size > 1) Tag(entry.provider.name)
    if (!entry.playable) Tag("Can't open", TagTone.Danger)
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
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .cardFrame(selected, hovered)
            .contextMenu { replayMenuItems(entry, onWatch) }
            .hoverable(interaction)
            .clickableWithSound(entry.name, selected = selected.takeIf { it }, shape = ppShape(16.dp), onClick = rememberReplayClick(entry, onSelect, onWatch)),
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
                TextButton("Watch", null, Modifier.height(BUTTON_HEIGHT), ButtonStyle.Primary, entry.playable) { onWatch(entry) }
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
        if (entry.playable) Label(detail, 12.sp, color = MutedText) else Label(entry.problem, 12.sp, color = SocialDangerText)
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
            TextButton("Watch", themedIcon(VIDEO_ICON), Modifier.fillMaxWidth().height(BUTTON_HEIGHT), ButtonStyle.Primary, entry.playable) { onWatch(entry) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton("Show in folder", themedIcon(FOLDER_ICON), Modifier.weight(1f).height(BUTTON_HEIGHT), ButtonStyle.Secondary, true) { entry.provider.openFolderSafely() }
                TextButton("Open ${entry.provider.name}", themedIcon(VIDEO_ICON), Modifier.weight(1f).height(BUTTON_HEIGHT), ButtonStyle.Secondary, true) { entry.provider.openBrowserSafely() }
            }
        }
    }
}
