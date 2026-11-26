package org.polyfrost.polyplus.client.party.soundboard

import fr.delthas.javamp3.Sound
import org.polyfrost.polyplus.client.party.PartyAudio
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
//? if > 1.8.9 {
import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
//?}

internal class SoundException(message: String) : Exception(message)

internal object SoundDecoder {
    const val MAX_SECONDS = 10
    const val MAX_FILE_BYTES = 8L * 1024 * 1024
    private const val MAX_SAMPLES = PartyAudio.SAMPLE_RATE * MAX_SECONDS
    private const val MAX_FLAC_RATE = 384_000
    private const val MP3_CHUNK = 4608
    private const val MP3_ERROR = "Couldn't read that MP3 file."
    private const val FLAC_ERROR = "Couldn't read that FLAC file."

    //? if > 1.8.9 {
    val EXTENSIONS = listOf("mp3", "wav", "ogg", "flac")
    //?} else {
    /*val EXTENSIONS = listOf("mp3", "wav", "flac")
    *///?}

    val FORMATS: String = EXTENSIONS.map { it.uppercase() }.let { names ->
        if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " or " + names.last()
    }

    fun supports(path: Path): Boolean = path.fileName.toString().substringAfterLast('.', "").lowercase() in EXTENSIONS

    fun decode(path: Path): ShortArray {
        val size = runCatching { Files.size(path) }.getOrElse { throw SoundException("Couldn't open that file.") }
        if (size > MAX_FILE_BYTES) throw SoundException("That file is too big. Keep sounds under ${MAX_FILE_BYTES / (1024 * 1024)} MB.")
        val pcm = when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
            "wav" -> decodeWav(path)
            "ogg" -> decodeOgg(path)
            "mp3" -> decodeMp3(path)
            "flac" -> decodeFlac(path)
            else -> throw SoundException("That file type isn't supported. Use $FORMATS.")
        }
        if (pcm.samples.isEmpty() || pcm.channels <= 0 || pcm.rate <= 0) throw SoundException("That sound is empty.")
        return toVoice(pcm)
    }

    private class Pcm(val samples: ShortArray, val channels: Int, val rate: Int)

    private fun readFile(path: Path): ByteArray {
        val bytes = runCatching { Files.readAllBytes(path) }.getOrElse { throw SoundException("Couldn't open that file.") }
        if (bytes.size > MAX_FILE_BYTES) throw SoundException("That file is too big. Keep sounds under ${MAX_FILE_BYTES / (1024 * 1024)} MB.")
        return bytes
    }

    private fun skipId3(data: ByteArray): Int {
        var position = 0
        while (position + 10 <= data.size && data[position] == 'I'.code.toByte() && data[position + 1] == 'D'.code.toByte() && data[position + 2] == '3'.code.toByte()) {
            var size = 0
            for (i in 6..9) size = (size shl 7) or (data[position + i].toInt() and 0x7F)
            val footer = if (data[position + 5].toInt() and 0x10 != 0) 10 else 0
            position += 10 + size + footer
        }
        return position.coerceAtMost(data.size)
    }

    private fun decodeWav(path: Path): Pcm = try {
        BufferedInputStream(Files.newInputStream(path)).use { input -> readWav(input) }
    } catch (error: SoundException) {
        throw error
    } catch (error: Exception) {
        throw SoundException("Couldn't read that WAV file. Try saving it as 16-bit PCM.")
    }

    private fun readWav(input: BufferedInputStream): Pcm =
        AudioSystem.getAudioInputStream(input).use { source ->
            val format = source.format
            val channels = format.channels.coerceAtLeast(1)
            val rate = format.sampleRate.toInt()
            val target = AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.sampleRate, 16, channels, channels * 2, format.sampleRate, false)
            val stream = if (format.matches(target)) source else AudioSystem.getAudioInputStream(target, source)
            stream.use {
                val bytes = it.readNBytes(rate.coerceAtLeast(1) * MAX_SECONDS * channels * 2)
                val samples = ShortArray(bytes.size / 2) { i -> ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort() }
                Pcm(samples, channels, rate)
            }
        }

    //? if > 1.8.9 {
    private fun decodeOgg(path: Path): Pcm {
        val bytes = runCatching { Files.readAllBytes(path) }.getOrElse { throw SoundException("Couldn't open that file.") }
        val input = MemoryUtil.memAlloc(bytes.size)
        try {
            input.put(bytes).flip()
            val handle = MemoryStack.stackPush().use { stack ->
                STBVorbis.stb_vorbis_open_memory(input, stack.mallocInt(1), null)
            }
            if (handle == 0L) throw SoundException("Couldn't read that OGG file.")
            try {
                val (channels, rate) = STBVorbisInfo.malloc().use { info ->
                    STBVorbis.stb_vorbis_get_info(handle, info)
                    info.channels() to info.sample_rate()
                }
                if (channels <= 0 || rate <= 0) throw SoundException("Couldn't read that OGG file.")
                val limit = rate * MAX_SECONDS * channels
                val out = MemoryUtil.memAllocShort(limit)
                try {
                    var total = 0
                    while (total < limit) {
                        out.position(total)
                        val frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, out)
                        if (frames <= 0) break
                        total += frames * channels
                    }
                    val samples = ShortArray(total)
                    out.position(0)
                    out.get(samples, 0, total)
                    return Pcm(samples, channels, rate)
                } finally {
                    MemoryUtil.memFree(out)
                }
            } finally {
                STBVorbis.stb_vorbis_close(handle)
            }
        } finally {
            MemoryUtil.memFree(input)
        }
    }
    //?} else {
    /*private fun decodeOgg(path: Path): Pcm = throw SoundException("OGG sounds need a newer Minecraft version. Use $FORMATS instead.")
    *///?}

    private val MP3_RATES = intArrayOf(44_100, 48_000, 32_000)
    private val MP3_BITRATES = arrayOf(
        intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448),
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384),
        intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320),
        intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256),
        intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160),
    )

    private class Mp3Frame(val version: Int, val layer: Int, val rate: Int, val length: Int)

    private fun mp3Frame(data: ByteArray, at: Int, end: Int): Mp3Frame? {
        if (at < 0 || at + 4 > end) return null
        val b1 = data[at + 1].toInt() and 0xFF
        val b2 = data[at + 2].toInt() and 0xFF
        if (data[at].toInt() and 0xFF != 0xFF || b1 and 0xE0 != 0xE0) return null
        val version = (b1 shr 3) and 3
        val layer = 4 - ((b1 shr 1) and 3)
        val bitrateIndex = b2 shr 4
        val rateIndex = (b2 shr 2) and 3
        if (version == 1 || layer == 4 || bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
        val rate = MP3_RATES[rateIndex] shr (if (version == 3) 0 else if (version == 2) 1 else 2)
        val table = if (version == 3) layer - 1 else if (layer == 1) 3 else 4
        val bitrate = MP3_BITRATES[table][bitrateIndex] * 1000
        val padding = (b2 shr 1) and 1
        val length = when {
            layer == 1 -> (12 * bitrate / rate + padding) * 4
            layer == 3 && version != 3 -> 72 * bitrate / rate + padding
            else -> 144 * bitrate / rate + padding
        }
        return Mp3Frame(version, layer, rate, length)
    }

    private fun mp3Start(data: ByteArray, from: Int, end: Int): Int {
        for (at in from until end - 3) {
            val frame = mp3Frame(data, at, end) ?: continue
            val next = at + frame.length
            if (next == end) return at
            val following = mp3Frame(data, next, end) ?: continue
            if (following.version == frame.version && following.layer == frame.layer && following.rate == frame.rate) return at
        }
        return -1
    }

    private fun decodeMp3(path: Path): Pcm {
        val data = readFile(path)
        val tagged = data.size >= 128 && data[data.size - 128] == 'T'.code.toByte() && data[data.size - 127] == 'A'.code.toByte() && data[data.size - 126] == 'G'.code.toByte()
        val end = if (tagged) data.size - 128 else data.size
        val start = mp3Start(data, skipId3(data), end)
        val first = mp3Frame(data, start, end) ?: throw SoundException(MP3_ERROR)
        if (first.version != 3) throw SoundException("That MP3's sample rate isn't supported. Save it at 44.1 or 48 kHz and try again.")
        val sound = try {
            Sound(ByteArrayInputStream(data, start, end - start))
        } catch (error: Exception) {
            throw SoundException(MP3_ERROR)
        }
        val channels = if (sound.isStereo) 2 else 1
        val rate = sound.samplingFrequency
        val bytes = ByteArray(rate * MAX_SECONDS * channels * 2)
        var total = 0
        try {
            while (total < bytes.size) {
                val read = sound.read(bytes, total, minOf(MP3_CHUNK, bytes.size - total))
                if (read <= 0) break
                total += read
            }
        } catch (error: Exception) {
            if (total == 0) throw SoundException(MP3_ERROR)
        }
        val samples = ShortArray(total / 2) { i -> ((bytes[i * 2].toInt() and 0xFF) or (bytes[i * 2 + 1].toInt() shl 8)).toShort() }
        return Pcm(samples, channels, rate)
    }

    private val CRC8 = IntArray(256) { index ->
        var crc = index
        repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF }
        crc
    }

    private val CRC16 = IntArray(256) { index ->
        var crc = index shl 8
        repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF }
        crc
    }

    private class Bits(private val data: ByteArray, start: Int) {
        var position = start * 8

        val byte: Int get() = position ushr 3

        fun read(count: Int): Int {
            var result = 0L
            var left = count
            while (left > 0) {
                val index = position ushr 3
                if (index >= data.size) throw EOFException()
                val available = 8 - (position and 7)
                val take = minOf(available, left)
                val chunk = ((data[index].toInt() and 0xFF) ushr (available - take)) and ((1 shl take) - 1)
                result = (result shl take) or chunk.toLong()
                left -= take
                position += take
            }
            return result.toInt()
        }

        fun signed(count: Int): Int = if (count == 0) 0 else (read(count) shl (32 - count)) shr (32 - count)

        fun unary(): Int {
            var count = 0
            while (true) {
                val index = position ushr 3
                if (index >= data.size) throw EOFException()
                val offset = position and 7
                val rest = ((data[index].toInt() and 0xFF) shl offset) and 0xFF
                if (rest == 0) {
                    count += 8 - offset
                    position = (index + 1) * 8
                } else {
                    val zeros = Integer.numberOfLeadingZeros(rest) - 24
                    position += zeros + 1
                    return count + zeros
                }
            }
        }

        fun align() {
            position = (position + 7) and 7.inv()
        }
    }

    private class FlacInfo(val rate: Int, val channels: Int, val depth: Int, val total: Long, val start: Int)

    private fun flacInfo(data: ByteArray): FlacInfo {
        var position = skipId3(data)
        if (position + 4 > data.size || String(data, position, 4, Charsets.ISO_8859_1) != "fLaC") throw SoundException(FLAC_ERROR)
        position += 4
        var info: FlacInfo? = null
        while (true) {
            if (position + 4 > data.size) throw SoundException(FLAC_ERROR)
            val header = data[position].toInt() and 0xFF
            val length = ((data[position + 1].toInt() and 0xFF) shl 16) or ((data[position + 2].toInt() and 0xFF) shl 8) or (data[position + 3].toInt() and 0xFF)
            position += 4
            if (position + length > data.size || header and 0x7F == 127) throw SoundException(FLAC_ERROR)
            if (header and 0x7F == 0) {
                if (length < 34) throw SoundException(FLAC_ERROR)
                val bits = Bits(data, position + 10)
                val rate = bits.read(20)
                val channels = bits.read(3) + 1
                val depth = bits.read(5) + 1
                val total = (bits.read(4).toLong() shl 32) or (bits.read(32).toLong() and 0xFFFFFFFFL)
                info = FlacInfo(rate, channels, depth, total, 0)
            }
            position += length
            if (header and 0x80 != 0) break
        }
        val found = info ?: throw SoundException(FLAC_ERROR)
        if (found.rate <= 0 || found.rate > MAX_FLAC_RATE) throw SoundException("That FLAC file's sample rate isn't supported.")
        if (found.depth < 4 || found.depth > 24) throw SoundException("That FLAC file's bit depth isn't supported. Use 16 or 24-bit.")
        return FlacInfo(found.rate, found.channels, found.depth, found.total, position)
    }

    private fun decodeFlac(path: Path): Pcm {
        val data = readFile(path)
        val info = flacInfo(data)
        val limit = info.rate * MAX_SECONDS
        val out = ShortArray(if (info.total in 1 until limit) info.total.toInt() else limit)
        val channels = Array(8) { IntArray(0) }
        val bits = Bits(data, info.start)
        var produced = 0
        while (produced < out.size && bits.byte + 2 <= data.size) {
            val start = bits.byte
            val decoded = try {
                flacFrame(data, bits, info, channels)
            } catch (error: Exception) {
                -1
            }
            if (decoded < 0) {
                if (produced == 0) throw SoundException(FLAC_ERROR)
                break
            }
            val count = decoded and 0xF
            val size = decoded ushr 4
            val depth = info.depth
            val take = minOf(size, out.size - produced)
            for (i in 0 until take) {
                var sum = 0L
                for (c in 0 until count) sum += channels[c][i]
                val value = (sum / count).toInt()
                out[produced + i] = (if (depth > 16) value shr (depth - 16) else value shl (16 - depth)).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            produced += take
            if (bits.byte <= start) break
        }
        return Pcm(if (produced == out.size) out else out.copyOf(produced), 1, info.rate)
    }

    private fun flacFrame(data: ByteArray, bits: Bits, info: FlacInfo, channels: Array<IntArray>): Int {
        val start = bits.byte
        if (bits.read(15) != 0x7FFC) return -1
        bits.read(1)
        val sizeCode = bits.read(4)
        val rateCode = bits.read(4)
        val assignment = bits.read(4)
        val depthCode = bits.read(3)
        if (bits.read(1) != 0 || sizeCode == 0 || rateCode == 15 || assignment > 10 || depthCode == 3) return -1
        val lead = bits.read(8)
        val extra = when {
            lead and 0x80 == 0 -> 0
            lead and 0xE0 == 0xC0 -> 1
            lead and 0xF0 == 0xE0 -> 2
            lead and 0xF8 == 0xF0 -> 3
            lead and 0xFC == 0xF8 -> 4
            lead and 0xFE == 0xFC -> 5
            lead == 0xFE -> 6
            else -> return -1
        }
        repeat(extra) { if (bits.read(8) and 0xC0 != 0x80) return -1 }
        val size = when (sizeCode) {
            1 -> 192
            in 2..5 -> 576 shl (sizeCode - 2)
            6 -> bits.read(8) + 1
            7 -> bits.read(16) + 1
            else -> 256 shl (sizeCode - 8)
        }
        when (rateCode) {
            12 -> bits.read(8)
            13, 14 -> bits.read(16)
        }
        val headerEnd = bits.byte
        var crc8 = 0
        for (i in start until headerEnd) crc8 = CRC8[crc8 xor (data[i].toInt() and 0xFF)]
        if (bits.read(8) != crc8) return -1
        val depth = when (depthCode) {
            0 -> info.depth
            1 -> 8
            2 -> 12
            4 -> 16
            5 -> 20
            6 -> 24
            else -> 32
        }
        if (depth != info.depth) return -1
        val count = if (assignment < 8) assignment + 1 else 2
        for (c in 0 until count) {
            if (channels[c].size < size) channels[c] = IntArray(size)
            val side = (assignment == 8 && c == 1) || (assignment == 9 && c == 0) || (assignment == 10 && c == 1)
            if (!flacSubframe(bits, channels[c], size, if (side) depth + 1 else depth)) return -1
        }
        val left = channels[0]
        val right = channels[1]
        when (assignment) {
            8 -> for (i in 0 until size) right[i] = left[i] - right[i]
            9 -> for (i in 0 until size) left[i] += right[i]
            10 -> for (i in 0 until size) {
                val side = right[i]
                val mid = (left[i] shl 1) or (side and 1)
                left[i] = (mid + side) shr 1
                right[i] = (mid - side) shr 1
            }
        }
        bits.align()
        var crc16 = 0
        for (i in start until bits.byte) crc16 = ((crc16 shl 8) and 0xFFFF) xor CRC16[(crc16 ushr 8) xor (data[i].toInt() and 0xFF)]
        if (bits.read(16) != crc16) return -1
        return (size shl 4) or count
    }

    private fun flacSubframe(bits: Bits, samples: IntArray, size: Int, depth: Int): Boolean {
        if (bits.read(1) != 0) return false
        val type = bits.read(6)
        val wasted = if (bits.read(1) == 1) bits.unary() + 1 else 0
        if (wasted >= depth) return false
        val width = depth - wasted
        when {
            type == 0 -> samples.fill(bits.signed(width), 0, size)
            type == 1 -> for (i in 0 until size) samples[i] = bits.signed(width)
            type in 8..12 -> {
                val order = type - 8
                if (order > size) return false
                for (i in 0 until order) samples[i] = bits.signed(width)
                if (!flacResidual(bits, samples, size, order)) return false
                for (i in order until size) {
                    val prediction = when (order) {
                        0 -> 0L
                        1 -> samples[i - 1].toLong()
                        2 -> 2L * samples[i - 1] - samples[i - 2]
                        3 -> 3L * samples[i - 1] - 3L * samples[i - 2] + samples[i - 3]
                        else -> 4L * samples[i - 1] - 6L * samples[i - 2] + 4L * samples[i - 3] - samples[i - 4]
                    }
                    samples[i] = (samples[i] + prediction).toInt()
                }
            }
            type >= 32 -> {
                val order = type - 31
                if (order > size) return false
                for (i in 0 until order) samples[i] = bits.signed(width)
                val precision = bits.read(4) + 1
                if (precision == 16) return false
                val shift = bits.signed(5)
                if (shift < 0) return false
                val coefficients = IntArray(order) { bits.signed(precision) }
                if (!flacResidual(bits, samples, size, order)) return false
                for (i in order until size) {
                    var sum = 0L
                    for (j in 0 until order) sum += coefficients[j].toLong() * samples[i - 1 - j]
                    samples[i] = (samples[i] + (sum shr shift)).toInt()
                }
            }
            else -> return false
        }
        if (wasted > 0) for (i in 0 until size) samples[i] = samples[i] shl wasted
        return true
    }

    private fun flacResidual(bits: Bits, samples: IntArray, size: Int, order: Int): Boolean {
        val method = bits.read(2)
        if (method > 1) return false
        val parameterBits = if (method == 0) 4 else 5
        val escape = (1 shl parameterBits) - 1
        val partitionOrder = bits.read(4)
        val partitionSize = size shr partitionOrder
        if (partitionSize shl partitionOrder != size || partitionSize < order) return false
        var index = order
        for (partition in 0 until (1 shl partitionOrder)) {
            val count = if (partition == 0) partitionSize - order else partitionSize
            val parameter = bits.read(parameterBits)
            if (parameter == escape) {
                val width = bits.read(5)
                for (i in 0 until count) samples[index++] = bits.signed(width)
            } else {
                for (i in 0 until count) {
                    val quotient = bits.unary().toLong()
                    val value = (quotient shl parameter) or bits.read(parameter).toLong()
                    samples[index++] = ((value ushr 1) xor -(value and 1)).toInt()
                }
            }
        }
        return true
    }

    private fun toVoice(pcm: Pcm): ShortArray {
        val frames = pcm.samples.size / pcm.channels
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until pcm.channels) sum += pcm.samples[i * pcm.channels + c]
            mono[i] = sum / pcm.channels
        }
        val target = PartyAudio.SAMPLE_RATE
        val length = (frames.toLong() * target / pcm.rate).toInt().coerceAtMost(MAX_SAMPLES)
        val step = pcm.rate.toDouble() / target
        return ShortArray(length) { i ->
            val position = i * step
            val index = position.toInt().coerceAtMost(frames - 1)
            val next = (index + 1).coerceAtMost(frames - 1)
            val fraction = (position - index).toFloat()
            (mono[index] + (mono[next] - mono[index]) * fraction).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
