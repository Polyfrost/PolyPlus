package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.polyfrost.polyplus.server.OneClientProtocol
import org.polyfrost.polyplus.server.OptionValue
import org.polyfrost.polyplus.server.ServerCommand
import org.polyfrost.polyplus.server.TextComponents

class OneClientProtocolTest {
    @Test
    fun `values round trip`() {
        val values = listOf(
            null,
            OptionValue.Bool(true),
            OptionValue.Bool(false),
            OptionValue.IntV(0),
            OptionValue.IntV(-1),
            OptionValue.IntV(Int.MAX_VALUE),
            OptionValue.IntV(Int.MIN_VALUE),
            OptionValue.Num(-0.25),
            OptionValue.Num(Double.MAX_VALUE),
            OptionValue.Str("ffffffff"),
            OptionValue.Str("ünïcode"),
        )
        for (value in values) assertEquals(value, OneClientProtocol.decodeValue(OneClientProtocol.encodeValue(value)))
    }

    @Test
    fun `decodes the spec's SetOptions example`() {
        val bytes = byteArrayOf(0x01, 0x02, 0x0F) + "fov.default-fov".toByteArray() + byteArrayOf(0x02, 0xB4.toByte(), 0x01, 0x10) +
            "zoom.smooth-zoom".toByteArray() + byteArrayOf(0x00)
        val packet = OneClientProtocol.decode(bytes) as OneClientProtocol.Command
        assertEquals(ServerCommand.SetOptions(mapOf("fov.default-fov" to OptionValue.IntV(90), "zoom.smooth-zoom" to null)), packet.command)
    }

    @Test
    fun `int values use zig-zag`() {
        assertArrayEquals(byteArrayOf(2, 1), OneClientProtocol.encodeValue(OptionValue.IntV(-1)))
        assertArrayEquals(byteArrayOf(2, 4), OneClientProtocol.encodeValue(OptionValue.IntV(2)))
    }

    @Test
    fun `malformed packets throw and unknown ids are ignored`() {
        assertThrows<OneClientProtocol.MalformedPacketException> { OneClientProtocol.decode(byteArrayOf(0x01, 0x01, 0x05, 0x61)) }
        assertThrows<OneClientProtocol.MalformedPacketException> { OneClientProtocol.decode(byteArrayOf(0x01, 0x7F)) }
        assertThrows<OneClientProtocol.MalformedPacketException> { OneClientProtocol.decodeValue(byteArrayOf(9)) }
        assertThrows<OneClientProtocol.MalformedPacketException> { OneClientProtocol.decode(byteArrayOf(0x03)) }
        assertNull(OneClientProtocol.decode(byteArrayOf(0x42)))
    }

    @Test
    fun `color strings parse as ARGB`() {
        assertEquals(0xFFFFFFFF.toInt(), OptionValue.Str("ffffffff").asArgb())
        assertEquals(0xFF112233.toInt(), OptionValue.Str("#112233").asArgb())
        assertNull(OptionValue.Str("nope").asArgb())
    }

    @Test
    fun `json text flattens to plain text`() {
        assertEquals("Hello world!", TextComponents.toPlainText("""{"text":"Hello ","extra":[{"text":"world","color":"red"},"!"]}"""))
        assertEquals("a b", TextComponents.toPlainText("""{"translate":"%s %s","with":["a",{"text":"b"}]}"""))
        assertEquals("plain", TextComponents.toPlainText("§aplain"))
    }
}
