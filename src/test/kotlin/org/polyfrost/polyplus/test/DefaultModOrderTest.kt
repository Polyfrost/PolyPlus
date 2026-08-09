package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.DefaultModOrder

class DefaultModOrderTest {

    private companion object {
        val DEFAULTS = listOf("a", "b", "c", "d", "e")
    }

    @Test
    fun `a fresh install takes the bundled order verbatim`() {
        assertEquals(DEFAULTS, DefaultModOrder.merge(emptyList(), DEFAULTS))
    }

    @Test
    fun `an existing order is never reshuffled`() {
        val current = listOf("e", "d", "c", "b", "a")
        assertEquals(current, DefaultModOrder.merge(current, DEFAULTS))
    }

    @Test
    fun `new entries land behind their bundled predecessor`() {
        assertEquals(
            listOf("e", "c", "d", "a", "b"),
            DefaultModOrder.merge(listOf("e", "c", "a"), DEFAULTS),
        )
    }

    @Test
    fun `a run of new entries keeps its bundled order`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            DefaultModOrder.merge(listOf("a", "e"), DEFAULTS),
        )
    }

    @Test
    fun `an entry with no predecessor lands ahead of its bundled successor`() {
        assertEquals(
            listOf("z", "a", "b", "c"),
            DefaultModOrder.merge(listOf("z", "c"), listOf("a", "b", "c")),
        )
    }

    @Test
    fun `mods outside the bundled list keep their place`() {
        assertEquals(
            listOf("mine", "a", "b", "c", "d", "e"),
            DefaultModOrder.merge(listOf("mine", "a", "e"), DEFAULTS),
        )
    }
}
