package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.gui.modPageCount
import org.polyfrost.polyplus.client.gui.modPageRange

class ModPageLayoutTest {
    private val cardsPerPage = 3

    @Test
    fun `every card lands on exactly one page`() {
        for (cards in 1..12) {
            val pages = modPageCount(cards)
            val covered = (0 until pages).flatMap { modPageRange(cards, it) }

            assertEquals((0 until cards).toList(), covered, "$cards cards over $pages pages")
        }
    }

    @Test
    fun `no page is wider than a row`() {
        for (cards in 1..12) {
            for (page in 0 until modPageCount(cards)) {
                val size = modPageRange(cards, page).count()
                assertTrue(size in 1..cardsPerPage, "$cards cards, page $page holds $size")
            }
        }
    }

    @Test
    fun `a cardless mods page needs no extra pages and renders nothing`() {
        assertEquals(0, modPageCount(0))
        assertTrue(modPageRange(0, 0).isEmpty())
    }
}
