package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.render.DisplayTags

class DisplayTagNameTest {
    private fun defaults(text: String, custom: Set<Int> = emptySet()) = BooleanArray(text.length) { it !in custom }

    @Test
    fun `a custom-font word character still joins the name`() {
        val text = "xAlex"
        assertEquals(-1, DisplayTags.findName(text, defaults(text, setOf(0)), "Alex"))
    }

    @Test
    fun `a custom-font symbol is a boundary`() {
        val text = "\uE000Alex"
        assertEquals(1, DisplayTags.findName(text, defaults(text, setOf(0)), "Alex"))
    }

    @Test
    fun `the name itself must use the default font`() {
        val text = "[VIP] Alex"
        assertEquals(-1, DisplayTags.findName(text, defaults(text, setOf(8)), "Alex"))
        assertEquals(6, DisplayTags.findName(text, defaults(text), "Alex"))
    }
}
