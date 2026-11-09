package org.polyfrost.polyplus.client.party

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusSignal
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.util.concurrent.LinkedBlockingQueue
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.sqrt

internal object PartyAudio {
    const val SAMPLE_RATE = 48_000
    const val FRAME_SAMPLES = 960
    const val FRAME_MILLIS = 20L
    const val BITRATE = 24_000
    const val MAX_PACKET = 1000

    val MONO = AudioFormat(SAMPLE_RATE.toFloat(), 16, 1, true, false)
    val STEREO = AudioFormat(SAMPLE_RATE.toFloat(), 16, 2, true, false)

    private const val OPENAL_DEVICE_PREFIX = "OpenAL Soft on "
    private const val JAVA_SOUND_DEFAULT = "Primary Sound"

    private val LOGGER = LogManager.getLogger()

    fun inputDevices(): List<String> = (OpenAlDevices.inputs() + devices(TargetDataLine::class.java, MONO)).distinctBy(::deviceLabel).filterNot(::isSystemDefault)

    fun outputDevices(): List<String> = (OpenAlDevices.outputs() + devices(SourceDataLine::class.java, STEREO)).distinctBy(::deviceLabel).filterNot(::isSystemDefault)

    private fun isSystemDefault(device: String): Boolean = device.startsWith(JAVA_SOUND_DEFAULT)

    fun deviceLabel(device: String): String = device.removePrefix(OPENAL_DEVICE_PREFIX)

    fun openCapture(device: String, gain: () -> Float, onFrame: (ShortArray, Float) -> Unit): VoiceStream? {
        val openAl = OpenAlCapture(device, gain, onFrame)
        val javaSound = MicCapture(device, gain, onFrame)
        val order = when {
            device.isNotBlank() && device in OpenAlDevices.inputs() -> listOf(openAl, javaSound)
            mixerFor(device, TargetDataLine::class.java, MONO) != null -> listOf(javaSound, openAl)
            PolyPlusConfig.voiceJavaSound -> listOf(javaSound, openAl)
            else -> listOf(openAl, javaSound)
        }
        return order.firstOrNull { it.start() }
    }

    fun openSpeaker(device: String, next: (ShortArray) -> Unit): VoiceStream? {
        val openAl = OpenAlSpeaker(device, next)
        val javaSound = SpeakerOutput(device, next)
        val order = when {
            device.isNotBlank() && device in OpenAlDevices.outputs() -> listOf(openAl, javaSound)
            mixerFor(device, SourceDataLine::class.java, STEREO) != null -> listOf(javaSound, openAl)
            PolyPlusConfig.voiceJavaSound -> listOf(javaSound, openAl)
            else -> listOf(openAl, javaSound)
        }
        return order.firstOrNull { it.start() }
    }

    fun deliver(raw: ShortArray, gain: Float, onFrame: (ShortArray, Float) -> Unit) {
        val frame = ShortArray(raw.size)
        for (i in raw.indices) {
            frame[i] = (raw[i] * gain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        runCatching { onFrame(frame, rms(frame)) }.onFailure { LOGGER.warn("Microphone frame handler failed", it) }
    }

    private fun devices(type: Class<out Line>, format: AudioFormat): List<String> = runCatching {
        val info = DataLine.Info(type, format)
        AudioSystem.getMixerInfo().filter { AudioSystem.getMixer(it).isLineSupported(info) }.map { it.name }.distinct()
    }.onFailure { LOGGER.warn("Could not list audio devices", it) }.getOrDefault(emptyList())

    fun mixerFor(name: String, type: Class<out Line>, format: AudioFormat): Mixer.Info? {
        if (name.isBlank()) return null
        val info = DataLine.Info(type, format)
        return AudioSystem.getMixerInfo().firstOrNull { it.name == name && AudioSystem.getMixer(it).isLineSupported(info) }
    }

    fun rms(frame: ShortArray): Float {
        var sum = 0.0
        for (sample in frame) sum += sample.toDouble() * sample
        return (sqrt(sum / frame.size) / Short.MAX_VALUE).toFloat()
    }

    fun encoder(): OpusEncoder = OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP).apply {
        bitrate = BITRATE
        complexity = 5
        signalType = OpusSignal.OPUS_SIGNAL_VOICE
    }

    fun decoder(): OpusDecoder = OpusDecoder(SAMPLE_RATE, 1)
}

internal class MicCapture(
    private val device: String,
    private val gain: () -> Float,
    private val onFrame: (frame: ShortArray, level: Float) -> Unit,
) : VoiceStream {
    private val logger = LogManager.getLogger()

    @Volatile private var running = false
    private var thread: Thread? = null
    private var line: TargetDataLine? = null

    override fun start(): Boolean {
        val opened = runCatching {
            val info = DataLine.Info(TargetDataLine::class.java, PartyAudio.MONO)
            val mixer = PartyAudio.mixerFor(device, TargetDataLine::class.java, PartyAudio.MONO)
            val target = (if (mixer != null) AudioSystem.getMixer(mixer).getLine(info) else AudioSystem.getLine(info)) as TargetDataLine
            target.open(PartyAudio.MONO, PartyAudio.FRAME_SAMPLES * 2 * 4)
            target.start()
            target
        }.onFailure { logger.warn("Could not open the microphone", it) }.getOrNull() ?: return false
        line = opened
        running = true
        thread = Thread({ loop(opened) }, "polyplus-party-mic").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun loop(target: TargetDataLine) {
        val bytes = ByteArray(PartyAudio.FRAME_SAMPLES * 2)
        while (running) {
            var read = 0
            while (read < bytes.size && running) {
                val got = runCatching { target.read(bytes, read, bytes.size - read) }.getOrDefault(-1)
                if (got < 0) {
                    if (running) {
                        running = false
                        PartyVoice.onCaptureFailed()
                    }
                    break
                }
                read += got
            }
            if (!running) break
            val raw = ShortArray(PartyAudio.FRAME_SAMPLES) { i -> ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort() }
            PartyAudio.deliver(raw, gain(), onFrame)
        }
    }

    override fun stop() {
        running = false
        line?.let { runCatching { it.stop() }; runCatching { it.close() } }
        line = null
        thread = null
    }
}

internal class SpeakerOutput(private val device: String, private val next: (ShortArray) -> Unit) : VoiceStream {
    private val logger = LogManager.getLogger()

    @Volatile private var running = false
    private var line: SourceDataLine? = null

    override fun start(): Boolean {
        val opened = runCatching {
            val info = DataLine.Info(SourceDataLine::class.java, PartyAudio.STEREO)
            val mixer = PartyAudio.mixerFor(device, SourceDataLine::class.java, PartyAudio.STEREO)
            val source = (if (mixer != null) AudioSystem.getMixer(mixer).getLine(info) else AudioSystem.getLine(info)) as SourceDataLine
            source.open(PartyAudio.STEREO, PartyAudio.FRAME_SAMPLES * 4 * 4)
            source.start()
            source
        }.onFailure { logger.warn("Could not open the speaker", it) }.getOrNull() ?: return false
        line = opened
        running = true
        Thread({ loop(opened) }, "polyplus-party-speaker").apply {
            isDaemon = true
            start()
        }
        return true
    }

    private fun loop(source: SourceDataLine) {
        val frame = ShortArray(PartyAudio.FRAME_SAMPLES * 2)
        val bytes = ByteArray(frame.size * 2)
        while (running) {
            frame.fill(0)
            runCatching { next(frame) }.onFailure { logger.warn("Party voice mixer failed", it) }
            for (i in frame.indices) {
                bytes[i * 2] = frame[i].toInt().toByte()
                bytes[i * 2 + 1] = (frame[i].toInt() shr 8).toByte()
            }
            if (runCatching { source.write(bytes, 0, bytes.size) }.isFailure) break
        }
    }

    override fun stop() {
        running = false
        line?.let { runCatching { it.stop() }; runCatching { it.close() } }
        line = null
    }
}

internal class JitterBuffer {
    private class Packet(val seq: Int, val last: Boolean, val data: ByteArray)

    private val inbound = LinkedBlockingQueue<Packet>(MAX_INBOUND)
    private val pending = HashMap<Int, Packet>()
    private val decoder = PartyAudio.decoder()
    private val pcm = ShortArray(PartyAudio.FRAME_SAMPLES)

    private var playing = false
    private var expected = 0
    private var misses = 0
    private var ending = false

    @Volatile var lastAudioAt = 0L
        private set

    fun push(seq: Int, last: Boolean, data: ByteArray) {
        inbound.offer(Packet(seq, last, data))
        lastAudioAt = System.currentTimeMillis()
    }

    fun clear() {
        inbound.clear()
        pending.clear()
        playing = false
    }

    fun next(): ShortArray? {
        while (true) {
            val packet = inbound.poll() ?: break
            if (playing && behind(packet.seq, expected)) continue
            pending[packet.seq] = packet
        }
        while (pending.size > MAX_PENDING) pending.remove(oldest())
        if (!playing) {
            if (pending.size < START_DEPTH && pending.values.none { it.last }) return null
            expected = oldest() ?: return null
            playing = true
            misses = 0
            ending = false
        }
        val packet = pending.remove(expected)
        expected = (expected + 1) and 0xFFFF
        val decoded = runCatching {
            if (packet != null) {
                misses = 0
                if (packet.last) ending = true
                decoder.decode(packet.data, 0, packet.data.size, pcm, 0, PartyAudio.FRAME_SAMPLES, false)
            } else {
                misses++
                if (ending || misses > MAX_CONCEALED) {
                    playing = false
                    return null
                }
                decoder.decode(null, 0, 0, pcm, 0, PartyAudio.FRAME_SAMPLES, false)
            }
        }.getOrDefault(0)
        if (ending && pending.isEmpty() && packet != null && packet.last) playing = false
        return if (decoded > 0) pcm else null
    }

    private fun oldest(): Int? {
        if (pending.isEmpty()) return null
        return pending.keys.minWithOrNull { a, b -> if (a == b) 0 else if (behind(a, b)) -1 else 1 }
    }

    private fun behind(a: Int, b: Int): Boolean {
        val diff = (b - a) and 0xFFFF
        return diff in 1 until 0x8000
    }

    private companion object {
        const val START_DEPTH = 3
        const val MAX_PENDING = 16
        const val MAX_INBOUND = 100
        const val MAX_CONCEALED = 3
    }
}
