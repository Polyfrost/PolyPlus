package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.evergreenhud.client.utils.media.MediaCommand
import org.polyfrost.evergreenhud.client.utils.media.MediaService

internal object PauseEvergreenMedia {
    fun supported(): Boolean = runCatching { MediaService.isSupported() }.getOrDefault(false)

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
