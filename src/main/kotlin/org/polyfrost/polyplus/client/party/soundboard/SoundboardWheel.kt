@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.party.soundboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.PageDots
import org.polyfrost.oneconfig.api.ui.v1.components.RadialMenuItem
import org.polyfrost.polyplus.client.gui.HudRadialMenu
import org.polyfrost.polyplus.client.gui.WheelOverlay
import org.polyfrost.polyplus.client.gui.WheelSession
import org.polyfrost.polyplus.client.gui.hudBackdropGlass
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.utils.ClientPlatform

private const val PLAY_ICON = "assets/polyplus/mainmenu/play-circle.svg"
private const val STOP_ICON = "assets/polyplus/mainmenu/stop-circle.svg"
private const val PAGE_SIZE = 8

object SoundboardKeybind {
    private val LOGGER = LogManager.getLogger()

    @Volatile private var held = false
    private var page by mutableIntStateOf(0)

    fun isHeld(): Boolean = held

    fun onKeybindState(pressed: Boolean) {
        held = pressed
        if (!pressed || WheelOverlay.isOpen) return
        if (Minecraft.getInstance().player == null || ClientPlatform.currentScreen() != null) return
        Soundboard.ensureLoaded()
        val sounds = Soundboard.ordered()
        if (sounds.isEmpty()) {
            notify("You don't have any sounds yet. Add some from the Soundboard tab in voice settings.")
            return
        }
        page = page.coerceIn(0, pages(sounds.size) - 1)
        WheelOverlay.open(
            WheelSession(
                held = ::isHeld,
                onPick = { index -> pick(index) },
                onScroll = { step -> cycle(step) },
                content = { session -> SoundboardWheelContent(session) },
            ),
        )
    }

    private fun pages(count: Int): Int = ((count + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)

    private fun cycle(step: Int) {
        val total = pages(Soundboard.sounds.value.size)
        page = Math.floorMod(page + step, total)
    }

    internal fun pageOf(sounds: List<Soundboard.Sound>): List<Soundboard.Sound> = sounds.drop(page * PAGE_SIZE).take(PAGE_SIZE)

    internal fun currentPage(): Int = page

    private fun pick(index: Int) {
        val sound = pageOf(Soundboard.ordered()).getOrNull(index) ?: return
        Soundboard.toggle(sound)
    }

    private fun notify(message: String) {
        runCatching { Notifications.builder("Soundboard", message).duration(3000L).send() }
            .onFailure { LOGGER.warn("Couldn't show the soundboard notification", it) }
    }

    @Composable
    private fun SoundboardWheelContent(session: WheelSession) {
        val all by Soundboard.sounds.collectAsState()
        val playing by Soundboard.playing.collectAsState()
        val voice by PartyVoice.ui.collectAsState()
        val ordered = remember(all, Soundboard.favorites) { Soundboard.ordered(all) }
        val shown = pageOf(ordered)
        val total = pages(ordered.size)
        val sending = voice.soundsReady
        val entries = remember(shown, playing, sending) {
            shown.map { sound ->
                val on = sound.id in playing
                RadialMenuItem(
                    label = sound.name,
                    icon = if (on) STOP_ICON else PLAY_ICON,
                    detail = when {
                        on -> "Let go to stop it"
                        sending -> "Let go to play it for your party"
                        else -> "Only you'll hear this"
                    },
                )
            }
        }
        val subtitle = when {
            !voice.inVoice -> "Join voice so your party hears it"
            !sending -> "Your party can't hear sounds right now"
            total > 1 -> "Page ${page + 1} of $total · Scroll for more"
            else -> null
        }
        HudRadialMenu(
            items = entries,
            session = session,
            title = "Soundboard",
            subtitle = subtitle,
            below = if (total > 1) {
                { PageDots(total, page, framed = true, glass = liquidGlassTheme && hudBackdropGlass) }
            } else {
                null
            },
        )
    }
}
