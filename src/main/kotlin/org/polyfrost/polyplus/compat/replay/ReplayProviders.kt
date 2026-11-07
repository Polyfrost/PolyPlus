package org.polyfrost.polyplus.compat.replay

import java.nio.file.Path

class ReplayEntry(
    val provider: ReplayProvider,
    val path: Path,
    val name: String,
    val world: String?,
    val durationMillis: Long?,
    val modified: Long,
    val size: Long,
    val icon: ByteArray?,
    val playable: Boolean,
    val warning: String?,
)

class RecordingState(
    val paused: Boolean,
    val elapsedMillis: Long?,
)

interface ReplayProvider {
    val id: String
    val name: String
    val available: Boolean
    val inReplay: Boolean

    fun list(): List<ReplayEntry>
    fun watch(entry: ReplayEntry)
    fun openFolder()
    fun openNativeBrowser()

    val recording: RecordingState?
    val canStart: Boolean
    val canPause: Boolean
    val canDiscard: Boolean
    val controlKeyPrefixes: List<String>

    fun start()
    fun setPaused(paused: Boolean)
    fun finish()
    fun discard()

    fun detachPauseOverlay(screen: Any) = Unit
}

object ReplayProviders {
    val all: List<ReplayProvider> by lazy { listOf(FlashbackReplays, ReplayModReplays) }

    val available: List<ReplayProvider> get() = all.filter { runCatching { it.available }.getOrDefault(false) }

    val recordingProvider: ReplayProvider? get() = available.firstOrNull { runCatching { it.recording != null }.getOrDefault(false) }

    val inReplay: Boolean get() = available.any { runCatching { it.inReplay }.getOrDefault(false) }
}
