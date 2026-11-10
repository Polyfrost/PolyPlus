package org.polyfrost.polyplus.server

import java.io.ByteArrayOutputStream

object OneClientProtocol {
    const val CHANNEL = "oneclient:api"
    const val VERSION = 2

    private const val SERVERBOUND_HELLO = 0x00
    private const val SERVERBOUND_CAPABILITIES = 0x01
    private const val CLIENTBOUND_WELCOME = 0x00
    private const val CLIENTBOUND_SET_OPTIONS = 0x01
    private const val CLIENTBOUND_RESET_OPTIONS = 0x02
    private const val CLIENTBOUND_NOTIFICATION = 0x03
    private const val CLIENTBOUND_RESET_NOTIFICATIONS = 0x04

    private const val TAG_RESET = 0
    private const val TAG_BOOL = 1
    private const val TAG_INT = 2
    private const val TAG_DOUBLE = 3
    private const val TAG_STRING = 4

    const val MAX_KEY_BYTES = 256
    const val MAX_VALUE_STRING_BYTES = 4096
    const val MAX_TEXT_BYTES = 16384
    const val MAX_OPTIONS = 1024
    private const val MAX_VERSION_BYTES = 64

    sealed interface Clientbound
    data class Welcome(val protocolVersion: Int, val serverApiVersion: String) : Clientbound
    data class Command(val command: ServerCommand) : Clientbound

    data class RawNotification(val titleJson: String, val descriptionJson: String, val durationMs: Long, val icon: String?) : Clientbound

    class MalformedPacketException(message: String) : RuntimeException(message)

    fun encodeHello(clientVersion: String, minecraftVersion: String): ByteArray =
        Writer()
            .varInt(SERVERBOUND_HELLO)
            .varInt(VERSION)
            .string(clientVersion.take(MAX_VERSION_BYTES))
            .string(minecraftVersion.take(MAX_VERSION_BYTES))
            .bytes()

    fun encodeCapabilities(keys: Collection<String>): ByteArray {
        val writer = Writer().varInt(SERVERBOUND_CAPABILITIES)
        val fitting = keys.filter { it.toByteArray(Charsets.UTF_8).size <= MAX_KEY_BYTES }
        writer.varInt(fitting.size)
        fitting.forEach(writer::string)
        return writer.bytes()
    }

    fun decode(data: ByteArray): Clientbound? {
        val reader = Reader(data)
        return when (reader.varInt()) {
            CLIENTBOUND_WELCOME -> Welcome(reader.varInt(), reader.string(MAX_KEY_BYTES))
            CLIENTBOUND_SET_OPTIONS -> {
                val count = reader.varInt()
                if (count !in 0..MAX_OPTIONS) throw MalformedPacketException("Bad option count $count")
                val values = LinkedHashMap<String, OptionValue?>(count)
                repeat(count) {
                    val key = reader.string(MAX_KEY_BYTES)
                    values[key] = readValue(reader)
                }
                Command(ServerCommand.SetOptions(values))
            }
            CLIENTBOUND_RESET_OPTIONS -> Command(ServerCommand.ResetOptions)
            CLIENTBOUND_NOTIFICATION -> RawNotification(
                titleJson = reader.string(MAX_TEXT_BYTES),
                descriptionJson = reader.string(MAX_TEXT_BYTES),
                durationMs = reader.varInt().toLong() and 0xFFFFFFFFL,
                icon = reader.string(MAX_KEY_BYTES).ifEmpty { null },
            )
            CLIENTBOUND_RESET_NOTIFICATIONS -> Command(ServerCommand.ResetNotifications)
            else -> null
        }
    }

    fun encodeValue(value: OptionValue?): ByteArray = Writer().value(value).bytes()

    fun decodeValue(data: ByteArray): OptionValue? = readValue(Reader(data))

    private fun readValue(reader: Reader): OptionValue? = when (val tag = reader.byte()) {
        TAG_RESET -> null
        TAG_BOOL -> OptionValue.Bool(reader.byte() != 0)
        TAG_INT -> reader.varInt().let { OptionValue.IntV((it ushr 1) xor -(it and 1)) }
        TAG_DOUBLE -> OptionValue.Num(Double.fromBits(reader.long()))
        TAG_STRING -> OptionValue.Str(reader.string(MAX_VALUE_STRING_BYTES))
        else -> throw MalformedPacketException("Unknown value tag $tag")
    }

    private class Writer {
        private val out = ByteArrayOutputStream()

        fun varInt(value: Int): Writer {
            var remaining = value
            while (remaining and 0x7F.inv() != 0) {
                out.write((remaining and 0x7F) or 0x80)
                remaining = remaining ushr 7
            }
            out.write(remaining)
            return this
        }

        fun string(value: String): Writer {
            val encoded = value.toByteArray(Charsets.UTF_8)
            varInt(encoded.size)
            out.write(encoded, 0, encoded.size)
            return this
        }

        fun value(value: OptionValue?): Writer {
            when (value) {
                null -> out.write(TAG_RESET)
                is OptionValue.Bool -> {
                    out.write(TAG_BOOL)
                    out.write(if (value.value) 1 else 0)
                }
                is OptionValue.IntV -> {
                    out.write(TAG_INT)
                    varInt((value.value shl 1) xor (value.value shr 31))
                }
                is OptionValue.Num -> {
                    out.write(TAG_DOUBLE)
                    val bits = value.value.toRawBits()
                    for (shift in 56 downTo 0 step 8) out.write((bits ushr shift).toInt() and 0xFF)
                }
                is OptionValue.Str -> {
                    out.write(TAG_STRING)
                    string(value.value)
                }
            }
            return this
        }

        fun bytes(): ByteArray = out.toByteArray()
    }

    private class Reader(private val data: ByteArray) {
        private var position = 0

        fun varInt(): Int {
            var value = 0
            for (shift in 0 until 35 step 7) {
                val b = byte()
                value = value or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return value
            }
            throw MalformedPacketException("VarInt is too long")
        }

        fun long(): Long {
            var value = 0L
            repeat(8) { value = (value shl 8) or byte().toLong() }
            return value
        }

        fun string(maxBytes: Int): String {
            val length = varInt()
            if (length !in 0..maxBytes || length > data.size - position) throw MalformedPacketException("Bad string length $length")
            return String(data, position, length, Charsets.UTF_8).also { position += length }
        }

        fun byte(): Int {
            if (position >= data.size) throw MalformedPacketException("Unexpected end of packet")
            return data[position++].toInt() and 0xFF
        }
    }
}
