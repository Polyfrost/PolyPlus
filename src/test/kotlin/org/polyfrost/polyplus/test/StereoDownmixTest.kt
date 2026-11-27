package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.StereoDownmix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

class StereoDownmixTest {
    @Test
    fun `stereo frames average into one positional channel`() {
        for (bigEndian in listOf(false, true)) {
            val order = if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
            val stereo = ByteBuffer.allocate(12).order(order)
            stereo.putShort(1000).putShort(3000)
            stereo.putShort(-32768).putShort(-32768)
            stereo.putShort(32767).putShort(-1)
            stereo.flip()
            val mono = StereoDownmix.pcm16(stereo, bigEndian).order(order)
            assertEquals(6, mono.remaining())
            assertEquals(2000.toShort(), mono.short)
            assertEquals((-32768).toShort(), mono.short)
            assertEquals(16383.toShort(), mono.short)
            assertEquals(12, stereo.remaining())
        }
    }

    @Test
    fun `only 16-bit stereo is downmixed and the result is mono`() {
        val stereo = AudioFormat(44_100f, 16, 2, true, false)
        assertTrue(StereoDownmix.applies(stereo))
        assertFalse(StereoDownmix.applies(AudioFormat(44_100f, 16, 1, true, false)))
        assertFalse(StereoDownmix.applies(AudioFormat(44_100f, 8, 2, true, false)))
        val mono = StereoDownmix.format(stereo)
        assertEquals(1, mono.channels)
        assertEquals(2, mono.frameSize)
        assertEquals(44_100f, mono.sampleRate)
        assertEquals(16, mono.sampleSizeInBits)
        assertFalse(mono.isBigEndian)
    }
}
