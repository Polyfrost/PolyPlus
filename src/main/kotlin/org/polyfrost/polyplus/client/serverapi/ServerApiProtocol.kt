package org.polyfrost.polyplus.client.serverapi

import java.io.ByteArrayOutputStream

object ServerApiProtocol {
    const val NAMESPACE = "oneclient"
    const val PATH = "api"
    const val CHANNEL = "$NAMESPACE:$PATH"
    const val VERSION = 1

    private const val SERVERBOUND_HELLO = 0x00
    private const val CLIENTBOUND_WELCOME = 0x00
    private const val MAX_STRING_BYTES = 256

    sealed interface Clientbound
    data class Welcome(val protocolVersion: Int, val serverApiVersion: String) : Clientbound

    fun encodeHello(clientVersion: String, minecraftVersion: String): ByteArray =
        Writer()
            .varInt(SERVERBOUND_HELLO)
            .varInt(VERSION)
            .string(clientVersion)
            .string(minecraftVersion)
            .bytes()

    fun decode(data: ByteArray): Clientbound? {
        val reader = Reader(data)
        return when (reader.varInt()) {
            CLIENTBOUND_WELCOME -> Welcome(reader.varInt(), reader.string())
            else -> null
        }
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
            throw IllegalArgumentException("VarInt is too long")
        }

        fun string(): String {
            val length = varInt()
            require(length in 0..MAX_STRING_BYTES && length <= data.size - position) { "Bad string length $length" }
            return String(data, position, length, Charsets.UTF_8).also { position += length }
        }

        private fun byte(): Int {
            require(position < data.size) { "Unexpected end of packet" }
            return data[position++].toInt() and 0xFF
        }
    }
}
