@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.singleplayer

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.lobbyshell.DetailRow
import org.polyfrost.polyplus.client.gui.lobbyshell.EmptyState
import org.polyfrost.polyplus.client.gui.lobbyshell.Label
import org.polyfrost.polyplus.client.gui.lobbyshell.LastPlayedStrip
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.Tag
import org.polyfrost.polyplus.client.gui.lobbyshell.TagTone
import org.polyfrost.polyplus.client.gui.lobbyshell.cardFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.hostWorldAllowed
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.rememberCardClick
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.rememberSocialHover

private const val BANNER_FALLBACK_ALPHA = 0.22f
private const val TOOLTIP_WIDTH = 240
internal val BUTTON_HEIGHT = 38.dp

private const val CARD_TAGS = 2

private val WorldTone.color: Color
    @Composable get() = when (this) {
        WorldTone.Neutral -> MutedText
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
private fun WorldBanner(icon: ImageBitmap?, height: Dp) {
    val fallback = OneConfigTheme.accent.copy(alpha = BANNER_FALLBACK_ALPHA)
    Box(Modifier.fillMaxWidth().height(height).background(fallback)) {
        icon?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        }
    }
}

@Composable
private fun WorldTile(icon: ImageBitmap?, size: Dp) {
    val shape = ppShape(if (size > 48.dp) 8.dp else 6.dp)
    Box(
        Modifier.size(size).clip(shape).background(OneConfigTheme.current.componentBackground).border(1.dp, SocialBorderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(size), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        } else {
            ThemedIcon(SOCIAL_ASSETS + "minecraft-block.svg", MutedText, size / 2)
        }
    }
}

@Composable
private fun WorldTagItems(world: SingleplayerWorld) {
    world.tags.take(CARD_TAGS).forEach { Tag(it.label, if (it.tone == WorldTone.Danger) TagTone.Danger else TagTone.Dark) }
}

@Composable
private fun WorldTags(world: SingleplayerWorld, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WorldTagItems(world)
    }
}

@Composable
private fun WorldCardText(world: SingleplayerWorld, titleSize: Int = 16) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Label(world.name, titleSize.sp, weight = FontWeight.Medium)
        SocialText(world.meta, 12.sp, color = MutedText, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val status = world.status
        if (status != null) {
            SocialText(status, 12.sp, color = world.statusTone.color, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else if (world.id != world.name) {
            Label(world.id, 12.sp, color = MutedText)
        }
    }
}

private fun worldMenuItems(state: SingleplayerState, world: SingleplayerWorld, onHost: (String) -> Unit): List<MenuAction> = listOfNotNull(
    MenuAction(world.primaryLabel, SOCIAL_ASSETS + "log-in-04.svg", enabled = world.canPlay) { state.flows.play(world) },
    MenuAction("Host", SOCIAL_ASSETS + "log-in-04.svg", enabled = world.canPlay) { onHost(world.id) }.takeIf { hostWorldAllowed() },
    MenuAction("Edit", SOCIAL_ASSETS + "edit-02.svg", enabled = world.canEdit) { state.flows.edit(world) },
    MenuAction("Backup", LobbyIcons.COPY, enabled = world.canBackup) { state.flows.backup(world) },
    MenuAction("Recreate", LobbyIcons.REFRESH, enabled = world.canRecreate) { state.flows.recreate(world) },
    MenuAction("Delete", LobbyIcons.TRASH, danger = true, enabled = world.canDelete) { state.flows.delete(world) },
)

@Composable
internal fun WorldCard(state: SingleplayerState, world: SingleplayerWorld, onHost: (String) -> Unit, modifier: Modifier) {
    val banner = 104.dp
    val tile = 52.dp
    val overhang = 12.dp
    val icon = state.icons[world.id]
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .cardFrame(state.selected === world, hovered)
            .contextMenu { worldMenuItems(state, world, onHost) }
            .hoverable(interaction)
            .clickableWithSound(world.name, selected = (state.selected === world).takeIf { it }, shape = ppShape(16.dp), onClick = rememberWorldClick(state, world)),
    ) {
        Column(Modifier.fillMaxSize()) {
            WorldBanner(icon, banner)
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = overhang + 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                WorldCardText(world)
                TextButton(world.primaryLabel, null, Modifier.height(BUTTON_HEIGHT), ButtonStyle.Primary, world.canPlay) { state.flows.play(world) }
            }
        }
        WorldTags(world, Modifier.padding(10.dp))
        Box(Modifier.offset(x = 12.dp, y = banner - tile + overhang)) { WorldTile(icon, tile) }
    }
}

@Composable
internal fun LastPlayedWorldStrip(state: SingleplayerState, onHost: (String) -> Unit) {
    val world = state.lastPlayedWorld ?: return
    LastPlayedStrip(
        tag = "Last played",
        name = world.name,
        selected = state.selected === world,
        onClick = rememberWorldClick(state, world),
        menu = { worldMenuItems(state, world, onHost) },
        actionLabel = world.primaryLabel,
        onAction = { state.flows.play(world) },
        actionEnabled = world.canPlay,
        thumb = { WorldTile(state.icons[world.id], 40.dp) },
        tags = { WorldTagItems(world) },
    ) {
        val status = world.status
        if (status != null) {
            Label(status, 12.sp, color = world.statusTone.color)
        } else {
            Label(world.meta, 12.sp, color = MutedText)
        }
    }
}

@Composable
private fun ActionButton(label: String, icon: String, enabled: Boolean, tooltip: String?, modifier: Modifier, onClick: () -> Unit) {
    TooltipBox(tooltip, modifier, TooltipPlacement.AboveCenter, TOOLTIP_WIDTH.dp) {
        TextButton(label, themedIcon(icon), Modifier.fillMaxWidth().height(BUTTON_HEIGHT), ButtonStyle.Secondary, enabled, onClick)
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
            Label(world.id, 12.sp, color = MutedText)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (world.gameMode.isNotEmpty()) DetailRow("Game mode", world.gameMode)
            DetailRow("Last played", world.lastPlayedDate)
            if (world.version.isNotEmpty()) DetailRow("Version", world.version)
            if (world.gameMode.isNotEmpty()) DetailRow("Cheats", if (world.commands) "On" else "Off")
        }
        world.status?.let { SocialText(it, 12.sp, color = world.statusTone.color, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hostWorldAllowed()) ActionButton("Host", SOCIAL_ASSETS + "log-in-04.svg", world.canPlay, null, Modifier.fillMaxWidth()) { onHost(world.id) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Edit", SOCIAL_ASSETS + "edit-02.svg", world.canEdit, fixingTooltip(world, "edit"), Modifier.weight(1f)) { state.flows.edit(world) }
                ActionButton("Backup", LobbyIcons.COPY, world.canBackup, fixingTooltip(world, "edit"), Modifier.weight(1f)) { state.flows.backup(world) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Recreate", LobbyIcons.REFRESH, world.canRecreate, fixingTooltip(world, "recreate"), Modifier.weight(1f)) { state.flows.recreate(world) }
                TextButton("Delete", themedIcon(LobbyIcons.TRASH), Modifier.weight(1f).height(BUTTON_HEIGHT), ButtonStyle.Danger, world.canDelete) { state.flows.delete(world) }
            }
        }
    }
}
