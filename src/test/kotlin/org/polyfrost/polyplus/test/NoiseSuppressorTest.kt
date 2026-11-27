package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.party.NoiseSuppressor
import org.polyfrost.polyplus.client.party.PartyAudio
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class NoiseSuppressorTest {
    private fun rms(samples: ShortArray, from: Int): Double {
        var sum = 0.0
        for (i in from until samples.size) sum += samples[i].toDouble() * samples[i]
        return sqrt(sum / (samples.size - from))
    }

    private fun run(strength: Float): Double {
        val suppressor = NoiseSuppressor.open() ?: error("RNNoise didn't load: ${NoiseSuppressor.unavailableReason}")
        try {
            val frames = 100
            val out = ShortArray(frames * PartyAudio.FRAME_SAMPLES)
            val frame = ShortArray(PartyAudio.FRAME_SAMPLES)
            for (f in 0 until frames) {
                for (i in frame.indices) {
                    val t = f * PartyAudio.FRAME_SAMPLES + i
                    frame[i] = (sin(2 * PI * 225 * t / PartyAudio.SAMPLE_RATE) * 12_000).toInt().toShort()
                }
                suppressor.process(frame, strength)
                System.arraycopy(frame, 0, out, f * PartyAudio.FRAME_SAMPLES, frame.size)
            }
            return rms(out, out.size / 2)
        } finally {
            suppressor.close()
        }
    }

    @Test
    fun `partial strength does not cancel the voice`() {
        val dry = run(0f)
        val half = run(0.5f)
        val full = run(1f)
        val floor = minOf(dry, full) * 0.7
        assertTrue(half >= floor, "dry $dry, half $half, full $full")
    }
}
