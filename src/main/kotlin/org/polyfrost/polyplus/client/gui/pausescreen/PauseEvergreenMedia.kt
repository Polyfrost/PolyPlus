package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.evergreenhud.client.utils.media.MediaCommand
import org.polyfrost.evergreenhud.client.utils.media.MediaService
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.lobby.LobbyScene

private const val WARM_CHECK_TICKS = 10

internal object PauseEvergreenMedia {
    private var warm = false
    private var ticks = 0

    fun supported(): Boolean = runCatching { MediaService.isSupported() }.getOrDefault(false)

    fun keepWarmInWorld(widgetId: String) {
        eventHandler { _: TickEvent.End ->
            if (++ticks >= WARM_CHECK_TICKS) {
                ticks = 0
                setWarm(wantsWarm(widgetId))
            }
        }
    }

    private fun wantsWarm(widgetId: String): Boolean = runCatching {
        PausePlatform.player() != null &&
            !LobbyScene.showing() &&
            PauseWidgetRegistry.isAdded(widgetId) &&
            supported()
    }.getOrDefault(false)

    private fun setWarm(wanted: Boolean) {
        if (wanted == warm) return
        warm = wanted
        if (wanted) acquire() else release()
    }

    fun acquire() {
        runCatching { MediaService.acquire() }
    }

    fun release() {
        runCatching { MediaService.release() }
    }

    fun read(): PauseMediaTrack? = runCatching {
        val info = MediaService.current ?: return null
        PauseMediaTrack(
            info.title,
            info.artist,
            info.player,
            info.playing,
            info.positionMs,
            info.lengthMs,
            info.sampledAtMs,
            info.canPrevious,
            info.canNext,
            info.canToggle,
            MediaService.art,
            MediaService.artAccent,
            MediaService.artAverage,
        )
    }.getOrNull()

    fun previous() = send(MediaCommand.Previous)

    fun toggle() = send(MediaCommand.Toggle)

    fun next() = send(MediaCommand.Next)

    private fun send(command: MediaCommand) {
        runCatching { MediaService.send(command) }
    }
}
