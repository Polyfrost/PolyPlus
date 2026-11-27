package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager

internal object EchoReference {
    private val LOGGER = LogManager.getLogger()
    private const val TAP_TIMEOUT_MS = 8_000L

    @Volatile private var canceller: EchoCanceller? = null
    private val mono = ThreadLocal.withInitial { ShortArray(PartyAudio.FRAME_SAMPLES) }

    @Volatile var tapFailed = false
        private set

    @Volatile private var tapLive = 0
    @Volatile private var tapCount = 0
    @Volatile private var lastEosAudio = 0L
    @Volatile private var waitingSince = 0L

    val enabled: Boolean get() = canceller != null

    fun setEnabled(on: Boolean) {
        if (on && canceller == null) canceller = EchoCanceller()
        if (!on) canceller = null
    }

    fun process(mic: ShortArray) {
        canceller?.process(mic)
    }

    fun failTap(reason: String) {
        if (tapFailed) return
        tapFailed = true
        LOGGER.warn("Echo cancellation can't hear EOS voice ({}); moving to peer-to-peer voice", reason)
        PartyPresence.forceSend()
    }

    @Synchronized
    fun tapInstalled(): Int {
        val token = ++tapCount
        tapLive = token
        waitingSince = 0L
        return token
    }

    @Synchronized
    fun tapRemoved(token: Int) {
        if (tapLive != token) return
        tapLive = 0
        waitingSince = 0L
    }

    fun checkTap(othersPresent: Boolean) {
        if (tapLive == 0 || tapFailed || canceller == null) return
        if (!othersPresent) {
            waitingSince = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (waitingSince == 0L) waitingSince = now
        if (now - maxOf(waitingSince, lastEosAudio) >= TAP_TIMEOUT_MS) failTap("no audio arrived from EOS")
    }

    fun eosRendered(samples: ShortArray, frames: Int, channels: Int, rate: Int) {
        lastEosAudio = System.currentTimeMillis()
        if (canceller == null) return
        if (samples.isEmpty() || rate != PartyAudio.SAMPLE_RATE || channels !in 1..2) {
            failTap("unexpected EOS audio: $frames frames, $channels channels, $rate Hz")
            return
        }
        rendered(samples, frames, channels)
    }

    fun rendered(stereo: ShortArray) {
        rendered(stereo, stereo.size / 2, 2)
    }

    private fun rendered(samples: ShortArray, frames: Int, channels: Int) {
        val aec = canceller ?: return
        var buffer = mono.get()
        if (buffer.size < frames) buffer = ShortArray(frames).also(mono::set)
        if (channels == 1) {
            System.arraycopy(samples, 0, buffer, 0, frames)
        } else {
            for (i in 0 until frames) buffer[i] = ((samples[i * channels] + samples[i * channels + 1]) / 2).toShort()
        }
        aec.render(buffer, 0, frames)
    }
}
