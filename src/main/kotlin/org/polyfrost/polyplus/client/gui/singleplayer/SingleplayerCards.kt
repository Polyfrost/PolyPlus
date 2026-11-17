@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.singleplayer

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.polyplus.client.gui.KitDangerColor
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.DetailRow
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.hostWorldAllowed
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberCardClick
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeStyle
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone

private const val BANNER_FALLBACK_ALPHA = 0.22f
private const val TOOLTIP_WIDTH = 240

private const val CARD_TAGS = 2

private val WorldTone.color: Color
    @Composable get() = when (this) {
        WorldTone.Neutral -> OneConfigTheme.current.mutedText
        WorldTone.Warning -> SocialTextPrimary
        WorldTone.Danger -> SocialDangerText
    }

@Composable
private fun rememberWorldClick(state: SingleplayerState, world: SingleplayerWorld): () -> Unit =
    rememberCardClick({ state.flows.play(world) }) {
        state.selectedId = world.id
        state.onSelect(world.id)
    }

@Composable
private fun WorldBanner(icon: ImageBitmap?, modifier: Modifier) {
    val fallback = OneConfigTheme.accent.copy(alpha = BANNER_FALLBACK_ALPHA)
    Box(modifier.background(fallback)) {
        icon?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        }
    }
}

@Composable
private fun WorldTile(icon: ImageBitmap?, size: Dp) {
    val shape = OneConfigTheme.shape(if (size > 48.dp) 8.dp else 6.dp)
    Box(
        Modifier.size(size).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        } else {
            ThemedIcon(SOCIAL_ASSETS + "minecraft-block.svg", OneConfigTheme.current.mutedText, size / 2)
        }
    }
}

@Composable
private fun WorldTagItems(world: SingleplayerWorld) {
    world.tags.take(CARD_TAGS).forEach { Badge(it.label.uppercase(), tone = if (it.tone == WorldTone.Danger) BadgeTone.Danger else BadgeTone.Neutral, overImage = true, style = BadgeStyle.Hairline) }
}

@Composable
private fun WorldTags(world: SingleplayerWorld, lastPlayed: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (lastPlayed) Badge("LAST PLAYED", tone = BadgeTone.Accent, style = BadgeStyle.Solid)
        WorldTagItems(world)
    }
}

@Composable
private fun WorldCardText(world: SingleplayerWorld, titleSize: Int = 16) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(world.name, style = OneConfigTheme.current.typography.body.copy(fontSize = titleSize.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(world.meta, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val status = world.status
        if (status != null) {
            Text(status, color = world.statusTone.color, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else if (world.id != world.name) {
            Text(world.id, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

private fun worldMenuItems(state: SingleplayerState, world: SingleplayerWorld, onHost: (String) -> Unit): List<MenuAction> = listOfNotNull(
    MenuAction(world.primaryLabel, SOCIAL_ASSETS + "log-in-04.svg", enabled = world.canPlay) { state.flows.play(world) },
    MenuAction("Host", SOCIAL_ASSETS + "log-in-04.svg", enabled = world.canPlay) { onHost(world.id) }.takeIf { hostWorldAllowed() },
    MenuAction("Edit", SOCIAL_ASSETS + "edit-02.svg", enabled = world.canEdit) { state.flows.edit(world) },
    MenuAction("Backup", LobbyIcons.COPY, enabled = world.canBackup) { state.flows.backup(world) },
    MenuAction("Recreate", LobbyIcons.REFRESH, enabled = world.canRecreate) { state.flows.recreate(world) },
    MenuAction("Delete", { state.flows.delete(world) }, icon = LobbyIcons.TRASH, enabled = world.canDelete, danger = true, dangerColor = KitDangerColor),
)

@Composable
internal fun WorldCard(state: SingleplayerState, world: SingleplayerWorld, onHost: (String) -> Unit, modifier: Modifier, lastPlayed: Boolean = false) {
    val banner = 104.dp
    val tile = 52.dp
    val overhang = 12.dp
    val icon = state.icons[world.id]
    val shape = OneConfigTheme.shape(16.dp)
    val click = rememberWorldClick(state, world)
    Box(
        modifier
            .clip(shape)
            .background(CardSurface)
            .border(1.dp, SocialBorderColor, shape)
            .contextMenu { worldMenuItems(state, world, onHost) }
            .selectableCard(state.selected === world, click, if (lastPlayed) "Last played, ${world.name}" else world.name, shape, recipe = ControlDefaults.Media),
    ) {
        Column(Modifier.fillMaxSize()) {
            WorldBanner(icon, Modifier.fillMaxWidth().height(banner))
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = overhang + 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                WorldCardText(world)
                TextButton(text = world.primaryLabel, onClick = { state.flows.play(world) }, style = ControlStyle.Primary, size = ControlSize.Large, enabled = world.canPlay)
            }
        }
        WorldTags(world, lastPlayed, Modifier.padding(10.dp))
        Box(Modifier.offset(x = 12.dp, y = banner - tile + overhang)) { WorldTile(icon, tile) }
    }
}

@Composable
private fun ActionButton(label: String, icon: String, enabled: Boolean, tooltip: String?, modifier: Modifier, onClick: () -> Unit) {
    TooltipBox(tooltip, modifier, TooltipPlacement.AboveCenter, TOOLTIP_WIDTH.dp) {
        TextButton(text = label, icon = icon, onClick = onClick, modifier = Modifier.fillMaxWidth(), style = ControlStyle.Secondary, size = ControlSize.Large, enabled = enabled)
    }
}

private fun fixingTooltip(world: SingleplayerWorld, key: String): String? =
    if (world.needsFileFixing) Platform.i18n().getUnformattedText(Component.translatable("selectWorld.requiresFileFixingTooltip.$key")) else null

@Composable
internal fun WorldDetailsPanel(state: SingleplayerState, onHost: (String) -> Unit, modifier: Modifier) {
    val world = state.selected
    Column(modifier.panel().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (world == null) {
            EmptyState("No world selected", Modifier.fillMaxSize(), "Select a world to see its details.")
            return@Column
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PanelTitle(world.name)
            Text(world.id, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (world.gameMode.isNotEmpty()) DetailRow("Game mode", world.gameMode)
            DetailRow("Last played", world.lastPlayedDate)
            if (world.version.isNotEmpty()) DetailRow("Version", world.version)
            if (world.gameMode.isNotEmpty()) DetailRow("Cheats", if (world.commands) "On" else "Off")
        }
        world.status?.let { Text(it, color = world.statusTone.color, style = OneConfigTheme.current.typography.small, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hostWorldAllowed()) ActionButton("Host", SOCIAL_ASSETS + "log-in-04.svg", world.canPlay, null, Modifier.fillMaxWidth()) { onHost(world.id) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Edit", SOCIAL_ASSETS + "edit-02.svg", world.canEdit, fixingTooltip(world, "edit"), Modifier.weight(1f)) { state.flows.edit(world) }
                ActionButton("Backup", LobbyIcons.COPY, world.canBackup, fixingTooltip(world, "edit"), Modifier.weight(1f)) { state.flows.backup(world) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Recreate", LobbyIcons.REFRESH, world.canRecreate, fixingTooltip(world, "recreate"), Modifier.weight(1f)) { state.flows.recreate(world) }
                TextButton(text = "Delete", icon = LobbyIcons.TRASH, onClick = { state.flows.delete(world) }, modifier = Modifier.weight(1f), style = ControlStyle.Danger, size = ControlSize.Large, enabled = world.canDelete, dangerColor = KitDangerColor)
            }
        }
    }
}
