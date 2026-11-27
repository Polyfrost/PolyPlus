package org.polyfrost.polyplus.client.party

import de.maxhenkel.rnnoise4j.Denoiser
import org.apache.logging.log4j.LogManager
import kotlin.math.max

internal class NoiseSuppressor private constructor(private val denoiser: Denoiser) {
    private val chunk = ShortArray(denoiser.frameSize)
    private var closed = false

    private var original = ShortArray(0)
    private val history = ShortArray(denoiser.frameSize)

    @Synchronized
    fun process(frame: ShortArray, strength: Float): Float {
        if (closed) return 1f
        if (original.size != frame.size) original = ShortArray(frame.size)
        System.arraycopy(frame, 0, original, 0, frame.size)
        val speech = denoise(frame)
        val delay = history.size
        if (strength < 1f && frame.size >= delay) {
            val keep = 1f - strength
            for (i in frame.indices) {
                val dry = if (i < delay) history[i] else original[i - delay]
                frame[i] = (dry * keep + frame[i] * strength).toInt().toShort()
            }
        }
        if (frame.size >= delay) System.arraycopy(original, frame.size - delay, history, 0, delay)
        return speech
    }

    @Synchronized
    fun analyze(frame: ShortArray): Float {
        if (closed) return 1f
        if (original.size != frame.size) original = ShortArray(frame.size)
        System.arraycopy(frame, 0, original, 0, frame.size)
        return denoise(original)
    }

    private fun denoise(frame: ShortArray): Float {
        var speech = 0f
        var offset = 0
        while (offset + chunk.size <= frame.size) {
            System.arraycopy(frame, offset, chunk, 0, chunk.size)
            speech = max(speech, denoiser.denoiseInPlace(chunk))
            System.arraycopy(chunk, 0, frame, offset, chunk.size)
            offset += chunk.size
        }
        return speech
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        runCatching { denoiser.close() }
    }

    companion object {
        private val LOGGER = LogManager.getLogger()

        @Volatile var unavailableReason: String? = null
            private set

        val available: Boolean get() = unavailableReason == null

        fun open(): NoiseSuppressor? {
            if (unavailableReason != null) return null
            return try {
                val denoiser = Denoiser()
                if (PartyAudio.FRAME_SAMPLES % denoiser.frameSize != 0) {
                    denoiser.close()
                    unavailable("RNNoise wants ${denoiser.frameSize}-sample frames")
                } else {
                    NoiseSuppressor(denoiser)
                }
            } catch (error: Throwable) {
                LOGGER.warn("Noise suppression couldn't start", error)
                unavailable(error.message ?: error.javaClass.simpleName)
            }
        }

        private fun unavailable(reason: String): NoiseSuppressor? {
            unavailableReason = reason
            return null
        }
    }
}
