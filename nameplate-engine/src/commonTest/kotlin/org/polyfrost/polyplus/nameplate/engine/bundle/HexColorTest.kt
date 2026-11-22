package org.polyfrost.polyplus.nameplate.engine.bundle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.HexColor

class HexColorTest {
    @Test
    fun rgbIsOpaque() {
        assertEquals(0xFFFFC6DC.toInt(), HexColor.parse("#FFC6DC"))
        assertEquals(0xFF0a0b0c.toInt(), HexColor.parse("#0a0b0c"))
    }

    @Test
    fun argbKeepsItsAlpha() {
        assertEquals(0x80FFFFFF.toInt(), HexColor.parse("#80FFFFFF"))
        assertEquals(0x00000000, HexColor.parse("#00000000"))
    }

    @Test
    fun malformedColoursAreRejected() {
        for (text in listOf("FFC6DC", "#FFF", "#FFC6DCA", "#-12345", "#GGGGGG", "#+1234567", "")) {
            assertNull(HexColor.parse(text), text)
        }
    }

    @Test
    fun colourRefsParse() {
        assertEquals(ColorRef.Auto, ColorRef.parse("auto"))
        assertEquals(ColorRef.None, ColorRef.parse("none"))
        assertEquals(ColorRef.Hex(0xFF123456.toInt()), ColorRef.parse("#123456"))
        assertEquals(ColorRef.Slot("bow", 0), ColorRef.parse("bow"))
        assertEquals(ColorRef.Slot("bow", 12), ColorRef.parse("bow[12]"))
        for (text in listOf("Bow", "bow[]", "bow[123]", "bow[-1]", "#12")) {
            assertNull(ColorRef.parse(text), text)
        }
    }
}
