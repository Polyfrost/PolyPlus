@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ui.components.liquidGlass
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import kotlin.math.max

private val DOT_SIZE = 6.dp
private val ACTIVE_DOT_WIDTH = 18.dp

private const val DOT_MILLIS = 140
private const val SURFACE_ALPHA = 0.92f

private const val EMOTE_ICON = "assets/polyplus/mainmenu/play-circle.svg"
private const val PIXEL_EMOTE_ICON = "/assets/oneconfig/ico/minecraft/star.svg"
private const val EMPTY_ICON = "assets/polyplus/mainmenu/plus.svg"

object EmoteWheelOverlay {
    private val logger = LogManager.getLogger("polyplus/emote-wheel")

    fun open(): Boolean = WheelOverlay.open(
        WheelSession(
            held = EmoteWheelKeybind::isHeld,
            onPick = ::play,
            onScroll = { step -> EmoteWheels.cycle(step) },
            content = { session -> EmoteWheelContent(session) },
        ),
    )

    private fun play(slot: Int) {
        val emoteId = EmoteWheels.slots(EmoteWheels.activeIndex).getOrNull(slot) ?: return
        PolyPlusClient.SCOPE.launch {
            CosmeticService.playEmote(emoteId).onFailure { logger.error("Failed to play emote {}", emoteId, it) }
        }
    }
}

@Composable
private fun EmoteWheelContent(session: WheelSession) {
    val wheelIndex = EmoteWheels.activeIndex
    val wheelCount = EmoteWheels.wheelCount
    val slotIds = remember(wheelIndex, wheelCount) { EmoteWheels.slots(wheelIndex) }
    val emoteIcon = if (isMinecraftTheme) PIXEL_EMOTE_ICON else EMOTE_ICON
    val entries = remember(slotIds, emoteIcon) {
        slotIds.map { id ->
            val definition = id?.let(CosmeticCatalog::getEmoteDefinition)
            if (definition == null) {
                RadialWheelEntry(icon = EMPTY_ICON, label = "Empty", enabled = false)
            } else {
                RadialWheelEntry(icon = emoteIcon, label = definition.name, sublabel = "Release to play")
            }
        }
    }
    val empty = entries.none { it.enabled }
    val title = if (empty) "No emotes yet" else "Emotes"
    val subtitle = when {
        empty -> null
        wheelCount > 1 -> "Wheel ${wheelIndex + 1} of $wheelCount · Scroll for more"
        else -> null
    }
    RadialWheel(
        entries = entries,
        session = session,
        title = title,
        subtitle = subtitle,
        below = if (wheelCount > 1) {
            { WheelPager(wheelIndex, wheelCount) }
        } else {
            null
        },
    )
}

@Composable
private fun WheelPager(index: Int, count: Int) {
    val theme = OneConfigTheme.current
    val shape = ppShape(10.dp)
    val glass = liquidGlassTheme && hudBackdropGlass
    Row(
        modifier = Modifier
            .then(if (glass) Modifier.liquidGlass(shape, frosted = true) else Modifier)
            .clip(shape)
            .background(if (glass) PanelSurface else theme.popupBackground.let { it.copy(alpha = max(it.alpha, SURFACE_ALPHA)) })
            .border(1.dp, theme.borderColor, shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { page ->
            PagerDot(page == index)
        }
    }
}

@Composable
private fun PagerDot(active: Boolean) {
    val width by animateDpAsState(
        if (active) ACTIVE_DOT_WIDTH else DOT_SIZE,
        if (Motion.reduced) snap() else tween(DOT_MILLIS),
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(DOT_SIZE)
            .clip(ppShape(DOT_SIZE / 2))
            .background(if (active) OneConfigTheme.accent else SocialTextSecondary.copy(alpha = DISABLED_ALPHA)),
    )
}
