package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumpGate
import org.polyfrost.polyplus.client.cosmetics.render.WearerJumpRules
import org.polyfrost.polyplus.client.cosmetics.render.WearerSlides
import org.polyfrost.polyplus.client.pets.PetArchetype
import org.polyfrost.polyplus.client.pets.PetPose

class WearerJumpRulesTest {
    @Test
    fun `ordinary movement and small corrections keep the trail`() {
        assertFalse(WearerJumpRules.jumped(0.28, 0.0, 0.0))
        assertFalse(WearerJumpRules.jumped(1.2, 0.4, 1.0))
        assertFalse(WearerJumpRules.jumped(2.0, 0.0, 0.0))
        assertFalse(WearerJumpRules.jumped(0.0, 0.0, 0.0))
    }

    @Test
    fun `a jump past two blocks breaks the trail`() {
        assertTrue(WearerJumpRules.jumped(2.01, 0.0, 0.0))
        assertTrue(WearerJumpRules.jumped(1.5, 1.5, 0.5))
        assertTrue(WearerJumpRules.jumped(0.0, -40.0, 0.0))
        assertTrue(WearerJumpRules.jumped(3.0, 0.0, 3.0))
    }

    @Test
    fun `an unknown position never counts as a jump`() {
        assertFalse(WearerJumpRules.jumped(Double.NaN, 0.0, 0.0))
        assertFalse(WearerJumpRules.jumped(0.0, Double.NaN, 50.0))
    }

    @Test
    fun `a relative teleport is its own offset and an absolute one is measured from the wearer`() {
        assertEquals(4.0, WearerJumpRules.offset(true, 4.0, 100.0))
        assertEquals(-96.0, WearerJumpRules.offset(false, 4.0, 100.0))
        assertEquals(0.0, WearerJumpRules.offset(false, 12.5, 12.5))
    }

    @Test
    fun `only a shoulder pet jumps with its owner`() {
        assertTrue(WearerJumpRules.petJumpsWithOwner(PetArchetype.Shoulder))
        assertFalse(WearerJumpRules.petJumpsWithOwner(PetArchetype.Walking))
        assertFalse(WearerJumpRules.petJumpsWithOwner(PetArchetype.Flying))
        assertFalse(WearerJumpRules.petJumpsWithOwner(null))
    }

    @Test
    fun `a shoulder pet that moves to another anchor has jumped`() {
        assertFalse(WearerJumpRules.anchorSwitched(null, PetPose.RIGHT_ARM))
        assertFalse(WearerJumpRules.anchorSwitched(PetPose.RIGHT_ARM, PetPose.RIGHT_ARM))
        assertTrue(WearerJumpRules.anchorSwitched("head", PetPose.RIGHT_ARM))
        assertTrue(WearerJumpRules.anchorSwitched(PetPose.LEFT_ARM, PetPose.RIGHT_ARM))
    }

    @Test
    fun `a wearer breaks once per tick`() {
        val gate = WearerJumpGate()
        assertTrue(gate.first(42L, 7L))
        assertFalse(gate.first(42L, 7L))
        assertTrue(gate.first(43L, 7L))
        assertFalse(gate.first(43L, 7L))
        assertTrue(gate.first(42L, 8L))
        assertFalse(gate.first(42L, 8L))
        assertTrue(gate.first(43L, 8L))
    }

    private fun ids(slides: WearerSlides): List<Int> = (0 until slides.count).map { slides.id(it) }.sorted()

    @Test
    fun `a sliding teleport keeps breaking for the ticks the slide lasts`() {
        val slides = WearerSlides()
        slides.start(5)
        repeat(WearerSlides.SLIDE_TICKS) {
            assertEquals(listOf(5), ids(slides))
            slides.advance()
        }
        assertEquals(0, slides.count)
    }

    @Test
    fun `a second teleport during a slide restarts it without a duplicate`() {
        val slides = WearerSlides()
        slides.start(5)
        slides.advance()
        slides.start(9)
        slides.start(5)
        assertEquals(listOf(5, 9), ids(slides))
        repeat(WearerSlides.SLIDE_TICKS - 1) { slides.advance() }
        assertEquals(listOf(5, 9), ids(slides))
        slides.advance()
        assertEquals(0, slides.count)
    }

    @Test
    fun `many slides at once all finish`() {
        val slides = WearerSlides()
        for (id in 0 until 40) slides.start(id)
        assertEquals((0 until 40).toList(), ids(slides))
        repeat(WearerSlides.SLIDE_TICKS) { slides.advance() }
        assertEquals(0, slides.count)
        slides.clear()
        slides.start(3)
        slides.clear()
        assertEquals(0, slides.count)
    }

    @Test
    fun `the gate keeps every wearer of a crowded tick`() {
        val gate = WearerJumpGate()
        for (id in 0L until 100L) assertTrue(gate.first(id, 1L))
        for (id in 0L until 100L) assertFalse(gate.first(id, 1L))
        assertTrue(gate.first(5L, 2L))
    }
}
