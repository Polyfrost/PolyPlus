@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.PageDots
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.emotes.EmoteWheels

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
                RadialMenuItem(label = "Empty", icon = EMPTY_ICON, enabled = false)
            } else {
                RadialMenuItem(label = definition.name, icon = emoteIcon, detail = "Release to play")
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
    HudRadialMenu(
        items = entries,
        session = session,
        title = title,
        subtitle = subtitle,
        below = if (wheelCount > 1) {
            { PageDots(wheelCount, wheelIndex, framed = true, glass = liquidGlassTheme && hudBackdropGlass) }
        } else {
            null
        },
    )
}
