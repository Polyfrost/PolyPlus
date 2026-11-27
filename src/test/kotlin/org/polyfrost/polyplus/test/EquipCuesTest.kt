package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.EquipCues

class EquipCuesTest {
    private val wearer = 42L
    private val hat = 0
    private val wings = 1

    @Test
    fun `first sight never equips`() {
        val cues = EquipCues(4)
        cues.changed(wearer, hat, 7, 0.0)
        assertFalse(cues.drawn(wearer, 1, hat, 7, 0, 0.1))
        assertFalse(cues.drawn(wearer, 2, hat, 7, 0, 0.2))
    }

    @Test
    fun `a snapshot change on a seen wearer never equips`() {
        val cues = EquipCues(4)
        assertFalse(cues.drawn(wearer, 1, hat, 7, 0, 0.0))
        assertFalse(cues.drawn(wearer, 2, hat, 8, 0, 0.1))
    }

    @Test
    fun `a live change equips for exactly one drawn frame`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 1.0)
        assertTrue(cues.drawn(wearer, 2, hat, 8, 0, 1.1))
        assertFalse(cues.drawn(wearer, 3, hat, 8, 0, 1.2))
    }

    @Test
    fun `a live change into an empty slot equips`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, wings, 9, 1.0)
        assertFalse(cues.drawn(wearer, 2, hat, 7, 0, 1.1))
        assertTrue(cues.drawn(wearer, 2, wings, 9, 0, 1.1))
    }

    @Test
    fun `a change waits for its asset and fires on its first drawn frame`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 1.0)
        assertFalse(cues.drawn(wearer, 2, hat, 7, 0, 1.1))
        assertFalse(cues.drawn(wearer, 3, hat, 7, 0, 4.0))
        assertTrue(cues.drawn(wearer, 4, hat, 8, 0, 6.0))
        assertFalse(cues.drawn(wearer, 5, hat, 8, 0, 6.1))
    }

    @Test
    fun `a change made while the wearer is hidden never equips`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 1.0)
        cues.hidden(wearer)
        assertFalse(cues.drawn(wearer, 5, hat, 8, 0, 4.0))
        assertFalse(cues.drawn(wearer, 6, hat, 8, 0, 4.1))
    }

    @Test
    fun `a change on a wearer unseen for thirty seconds never equips`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 31.0)
        assertFalse(cues.drawn(wearer, 2, hat, 8, 0, 31.1))
    }

    @Test
    fun `coming back into view after thirty seconds drops a pending equip`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 5.0)
        assertFalse(cues.drawn(wearer, 2, hat, 8, 0, 40.0))
    }

    @Test
    fun `an asset reload with the same identity never equips`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 7, 1.0)
        assertFalse(cues.drawn(wearer, 2, hat, 7, 0, 1.1))
    }

    @Test
    fun `taking a cosmetic off and putting it back on equips`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, null, 1.0)
        cues.drawn(wearer, 2, wings, 9, 0, 1.1)
        cues.changed(wearer, hat, 7, 2.0)
        assertTrue(cues.drawn(wearer, 3, hat, 7, 0, 2.1))
    }

    @Test
    fun `a newer change replaces the pending one`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 1.0)
        cues.changed(wearer, hat, 9, 1.5)
        assertFalse(cues.drawn(wearer, 2, hat, 8, 0, 2.0))
        assertTrue(cues.drawn(wearer, 3, hat, 9, 0, 2.1))
    }

    @Test
    fun `a pending equip that never draws expires`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 1.0)
        for (frame in 2..80) cues.drawn(wearer, frame, wings, 9, 0, frame.toDouble())
        assertFalse(cues.drawn(wearer, 81, hat, 8, 0, 81.0))
    }

    @Test
    fun `other wearers are untouched`() {
        val cues = EquipCues(4)
        cues.drawn(wearer, 1, hat, 7, 0, 0.0)
        cues.drawn(7L, 1, hat, 7, 0, 0.0)
        cues.changed(wearer, hat, 8, 1.0)
        assertFalse(cues.drawn(7L, 2, hat, 8, 0, 1.1))
        assertTrue(cues.drawn(wearer, 2, hat, 8, 0, 1.1))
    }
}
