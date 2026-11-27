package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.cosmetics.colors.auraPalette
import org.polyfrost.polyplus.client.cosmetics.colors.writeAtomically
import org.polyfrost.polyplus.client.gui.wardrobe.PaletteCounterparts
import org.polyfrost.polyplus.client.gui.wardrobe.sharedPaletteSource
import org.polyfrost.polyplus.client.gui.wardrobe.stalePalette
import java.io.File
import java.io.IOException

class PaletteSyncRulesTest {
    private val old = mapOf("fur" to 0x111111)
    private val fresh = mapOf("fur" to 0x222222)
    private val counterparts = PaletteCounterparts(sameColourway = listOf(2), others = listOf(3))

    @Test
    fun `the shared palette carries the order of the save it came from`() {
        val kept = mapOf(1 to old, 2 to old, 3 to fresh)
        val order = mapOf(1 to 4L, 2 to 4L, 3 to 9L)
        val source = sharedPaletteSource(listOf(1), counterparts, kept::get, kept::get) { order[it] ?: 0L }
        assertEquals(fresh, source?.colors)
        assertEquals(9L, source?.order)
    }

    @Test
    fun `a palette with no recorded order has order zero`() {
        val kept = mapOf(2 to old)
        val source = sharedPaletteSource(listOf(1), counterparts, { null }, kept::get) { 0L }
        assertEquals(old, source?.colors)
        assertEquals(0L, source?.order)
    }

    @Test
    fun `a worn palette that already matches the newest is not stale`() {
        val kept = mapOf(1 to fresh, 3 to fresh)
        val order = mapOf(1 to 9L, 3 to 9L)
        assertNull(stalePalette(1, counterparts, kept::get, kept::get) { order[it] ?: 0L })
    }

    @Test
    fun `a worn palette older than a sibling save is stale`() {
        val kept = mapOf(1 to old, 3 to fresh)
        val order = mapOf(1 to 2L, 3 to 7L)
        val stale = stalePalette(1, counterparts, kept::get, kept::get) { order[it] ?: 0L }
        assertEquals(fresh, stale?.colors)
        assertEquals(7L, stale?.order)
    }

    @Test
    fun `an outfit aura colour marked newest is not replaced by an older sibling save`() {
        val kept = mapOf(1 to old, 3 to fresh)
        val order = mapOf(1 to 8L, 3 to 7L)
        assertNull(stalePalette(1, counterparts, kept::get, kept::get) { order[it] ?: 0L })
    }

    @Test
    fun `a piece without custom colours is never propagated to`() {
        val kept = mapOf(3 to fresh)
        assertNull(stalePalette(1, counterparts, { null }, kept::get) { 5L })
    }

    @Test
    fun `an aura colour replaces only the aura slot`() {
        val stored = mapOf("aura" to 0x000001, "glow" to 0x00FF00)
        assertEquals(mapOf("glow" to 0x00FF00, "aura" to 0xABCDEF), auraPalette(stored, 0x7FABCDEF))
    }

    @Test
    fun `clearing the aura colour keeps the other slots`() {
        assertEquals(mapOf("glow" to 0x00FF00), auraPalette(mapOf("aura" to 1, "glow" to 0x00FF00), null))
        assertNull(auraPalette(mapOf("aura" to 1), null))
    }

    @Test
    fun `an aura colour with nothing saved starts a palette`() {
        assertEquals(mapOf("aura" to 0x123456), auraPalette(null, 0x123456))
    }

    @Test
    fun `the order file is replaced whole and leaves no temp files`(@TempDir dir: File) {
        val target = File(dir, "polyplus/order.json")
        writeAtomically(target, "first")
        writeAtomically(target, "second")
        assertEquals("second", target.readText())
        assertEquals(listOf("order.json"), target.parentFile.list()?.toList())
    }

    @Test
    fun `a write into an unusable folder fails instead of passing silently`(@TempDir dir: File) {
        val blocker = File(dir, "polyplus")
        blocker.writeText("not a folder")
        assertThrows(IOException::class.java) { writeAtomically(File(blocker, "order.json"), "data") }
        assertEquals("not a folder", blocker.readText())
    }
}
