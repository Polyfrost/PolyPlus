package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.party.soundboard.SoundDecoder
import org.polyfrost.polyplus.client.party.soundboard.SoundException
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SoundDecoderTest {
    @TempDir
    lateinit var dir: Path

    private fun writeWav(name: String, rate: Int, channels: Int, seconds: Double, amplitude: Double = 0.5): Path {
        val frames = (rate * seconds).toInt()
        val bytes = ByteArray(frames * channels * 2)
        for (i in 0 until frames) {
            val sample = (sin(2 * PI * 440 * i / rate) * amplitude * Short.MAX_VALUE).toInt()
            for (c in 0 until channels) {
                val at = (i * channels + c) * 2
                bytes[at] = sample.toByte()
                bytes[at + 1] = (sample shr 8).toByte()
            }
        }
        val format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        val file = dir.resolve(name)
        AudioSystem.write(AudioInputStream(ByteArrayInputStream(bytes), format, frames.toLong()), AudioFileFormat.Type.WAVE, file.toFile())
        return file
    }

    private fun fixture(name: String): Path {
        val file = dir.resolve(name)
        javaClass.getResourceAsStream("/soundboard/$name")!!.use { Files.copy(it, file) }
        return file
    }

    private fun peak(pcm: ShortArray): Int = pcm.maxOf { abs(it.toInt()) }

    @Test
    fun `stereo 44_1 kHz wav becomes 48 kHz mono`() {
        val pcm = SoundDecoder.decode(writeWav("tone.wav", 44_100, 2, 1.0))
        assertEquals(48_000, pcm.size)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue(peak in 15_000..17_500, "peak was $peak")
    }

    @Test
    fun `long sounds are cut to the limit`() {
        val pcm = SoundDecoder.decode(writeWav("long.wav", 48_000, 1, SoundDecoder.MAX_SECONDS + 2.0))
        assertEquals(48_000 * SoundDecoder.MAX_SECONDS, pcm.size)
    }

    @Test
    fun `unsupported and broken files are rejected`() {
        val mp3 = Files.write(dir.resolve("song.mp3"), byteArrayOf(1, 2, 3))
        assertThrows(SoundException::class.java) { SoundDecoder.decode(mp3) }
        val broken = Files.write(dir.resolve("broken.wav"), byteArrayOf(1, 2, 3, 4))
        assertThrows(SoundException::class.java) { SoundDecoder.decode(broken) }
        val text = Files.write(dir.resolve("notes.txt"), byteArrayOf(1, 2, 3))
        assertThrows(SoundException::class.java) { SoundDecoder.decode(text) }
    }

    @Test
    fun `mp3 and flac are accepted`() {
        assertTrue(SoundDecoder.supports(dir.resolve("clip.MP3")))
        assertTrue(SoundDecoder.supports(dir.resolve("clip.flac")))
    }

    @Test
    fun `stereo 44_1 kHz vbr mp3 with id3 cover art becomes 48 kHz mono`() {
        val pcm = SoundDecoder.decode(fixture("tone.mp3"))
        assertTrue(pcm.size in 48_000..52_000, "size was ${pcm.size}")
        val peak = peak(pcm)
        assertTrue(peak in 14_000..18_500, "peak was $peak")
    }

    @Test
    fun `low sample rate mp3 is rejected`() {
        assertThrows(SoundException::class.java) { SoundDecoder.decode(fixture("low-rate.mp3")) }
    }

    @Test
    fun `stereo 16-bit flac becomes 48 kHz mono`() {
        val pcm = SoundDecoder.decode(fixture("tone.flac"))
        assertEquals(48_000, pcm.size)
        val peak = peak(pcm)
        assertTrue(peak in 15_000..17_500, "peak was $peak")
    }

    @Test
    fun `mono 24-bit flac is decoded`() {
        val pcm = SoundDecoder.decode(fixture("tone24.flac"))
        assertEquals(12_000, pcm.size)
        val peak = peak(pcm)
        assertTrue(peak in 15_000..17_500, "peak was $peak")
    }

    @Test
    fun `mono 8-bit flac is decoded`() {
        val pcm = SoundDecoder.decode(fixture("tone8.flac"))
        assertEquals(28_800, pcm.size)
        val peak = peak(pcm)
        assertTrue(peak in 15_000..17_500, "peak was $peak")
        assertTrue(pcm.takeLast(4_000).all { it.toInt() == 0 })
    }

    @Test
    fun `corrupted mp3 and flac files are rejected`() {
        val mp3 = javaClass.getResourceAsStream("/soundboard/tone.mp3")!!.use { it.readBytes() }
        val noise = ByteArray(mp3.size) { (it * 37 + 11).toByte() }
        assertThrows(SoundException::class.java) { SoundDecoder.decode(Files.write(dir.resolve("noise.mp3"), noise)) }
        val flac = javaClass.getResourceAsStream("/soundboard/tone.flac")!!.use { it.readBytes() }
        val header = flac.copyOf().also { it[0] = 'X'.code.toByte() }
        assertThrows(SoundException::class.java) { SoundDecoder.decode(Files.write(dir.resolve("header.flac"), header)) }
        val frames = flac.copyOf().also { bytes -> for (i in 200 until bytes.size step 3) bytes[i] = (bytes[i].toInt() xor 0x5A).toByte() }
        assertThrows(SoundException::class.java) { SoundDecoder.decode(Files.write(dir.resolve("frames.flac"), frames)) }
    }

    @Test
    fun `truncated flac keeps the decoded part`() {
        val flac = javaClass.getResourceAsStream("/soundboard/tone.flac")!!.use { it.readBytes() }
        val pcm = SoundDecoder.decode(Files.write(dir.resolve("cut.flac"), flac.copyOf(flac.size / 2)))
        assertTrue(pcm.size in 1 until 48_000, "size was ${pcm.size}")
    }
}
