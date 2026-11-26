package org.polyfrost.polyplus.client.party

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.party.soundboard.Soundboard
import org.polyfrost.polyplus.client.party.soundboard.SoundboardStream
import java.util.concurrent.ArrayBlockingQueue

internal object LocalMonitor {
    private const val IDLE_CLOSE_MS = 1_500L
    private const val LOOPBACK_FRAMES = 6

    private val loopback = ArrayBlockingQueue<ShortArray>(LOOPBACK_FRAMES)
    private val mixLock = Any()

    @Volatile private var stream: VoiceStream? = null
    @Volatile private var generation = 0
    @Volatile private var idleSince = 0L
    @Volatile private var hearSelf = false
    @Volatile private var opening = false

    fun wake() {
        idleSince = 0L
        if (stream != null || opening || PartyMesh.rendering) return
        opening = true
        PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            try {
                open()
            } finally {
                opening = false
            }
        }
    }

    private fun open() {
        synchronized(this) {
            idleSince = 0L
            if (stream != null || PartyMesh.rendering) return
            val current = ++generation
            val opened = PartyAudio.openSpeaker(PartyVoiceSettings.outputDevice()) { out -> fill(out, current) }
            if (opened == null) {
                Soundboard.onMonitorUnavailable()
                loopback.clear()
                return
            }
            stream = opened
        }
    }

    fun setHearSelf(enabled: Boolean) {
        hearSelf = enabled
        loopback.clear()
        if (enabled) wake()
    }

    fun pushLoopback(frame: ShortArray) {
        if (!hearSelf || (stream == null && !PartyMesh.rendering)) return
        val copy = frame.copyOf()
        while (!loopback.offer(copy)) loopback.poll()
    }

    fun restart() {
        close()
        if (hearSelf || Soundboard.playing.value.isNotEmpty()) wake()
    }

    fun mixShared(out: ShortArray) {
        mixSources(out)
    }

    private fun close(only: Int? = null) {
        synchronized(this) {
            if (only != null && only != generation) return
            generation++
            stream?.stop()
            stream = null
            loopback.clear()
        }
    }

    private fun mixSources(out: ShortArray): Boolean = synchronized(mixLock) { mixLocked(out) }

    private fun mixLocked(out: ShortArray): Boolean {
        var busy = Soundboard.mixMonitor(out)
        if (SoundboardStream.mixRemote(out)) busy = true
        loopback.poll()?.let { frame ->
            val count = minOf(frame.size, out.size / 2)
            for (i in 0 until count) {
                out[i * 2] = clip(out[i * 2] + frame[i])
                out[i * 2 + 1] = clip(out[i * 2 + 1] + frame[i])
            }
            busy = true
        }
        return busy || hearSelf
    }

    private fun clip(value: Int): Short = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private fun fill(out: ShortArray, owner: Int) {
        if (owner != generation) return
        if (PartyMesh.rendering) {
            close(owner)
            return
        }
        val busy = mixSources(out)
        val now = System.currentTimeMillis()
        when {
            busy -> idleSince = 0L
            idleSince == 0L -> idleSince = now
            now - idleSince >= IDLE_CLOSE_MS -> close(owner)
        }
    }
}
