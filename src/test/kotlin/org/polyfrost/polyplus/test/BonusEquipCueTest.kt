package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.EquipCues
import org.polyfrost.polyplus.client.cosmetics.render.bonusCueChanges
import org.polyfrost.polyplus.client.cosmetics.render.bonusCueId
import org.polyfrost.polyplus.client.cosmetics.render.emptyBonusSlots
import org.polyfrost.polyplus.client.network.http.responses.BonusRef

class BonusEquipCueTest {
    private val knight = BonusRef(3)
    private val wizard = BonusRef(5)

    private fun slots() = emptyBonusSlots(2)

    @Test
    fun `a newly active bonus gets an equip target`() {
        assertEquals(listOf(0 to bonusCueId(knight)), bonusCueChanges(slots(), emptyList(), listOf(knight)))
    }

    @Test
    fun `a bonus that stays active gets no new target`() {
        assertEquals(listOf(1 to bonusCueId(wizard)), bonusCueChanges(slots(), listOf(knight), listOf(wizard, knight)))
    }

    @Test
    fun `a bonus that ends frees only its own slot`() {
        assertEquals(listOf(1 to null), bonusCueChanges(slots(), listOf(knight, wizard), listOf(knight)))
        assertEquals(listOf(0 to null), bonusCueChanges(slots(), listOf(knight, wizard), listOf(wizard)))
    }

    @Test
    fun `nothing changing gives no changes`() {
        assertTrue(bonusCueChanges(slots(), listOf(knight), listOf(knight)).isEmpty())
    }

    @Test
    fun `a bonus activating on a seen wearer plays its equip cue once`() {
        val cues = EquipCues(2)
        cues.seen(42L, 1, 0.0)
        for ((index, target) in bonusCueChanges(slots(), emptyList(), listOf(knight))) cues.changed(42L, index, target, 0.5)
        assertTrue(cues.drawn(42L, 2, 0, bonusCueId(knight), 0, 0.6))
        assertEquals(false, cues.drawn(42L, 3, 0, bonusCueId(knight), 0, 0.7))
    }

    @Test
    fun `a downloading bonus keeps its cue when the bonus before it ends`() {
        val cues = EquipCues(2)
        val slots = slots()
        cues.seen(42L, 1, 0.0)
        for ((slot, target) in bonusCueChanges(slots, listOf(knight), listOf(knight, wizard))) cues.changed(42L, slot, target, 0.5)
        val wizardSlot = slots.indexOf(wizard.id)
        assertEquals(1, wizardSlot)
        assertEquals(false, cues.drawn(42L, 2, 0, bonusCueId(knight), 0, 0.6))
        for ((slot, target) in bonusCueChanges(slots, listOf(knight, wizard), listOf(wizard))) cues.changed(42L, slot, target, 0.7)
        assertEquals(wizardSlot, slots.indexOf(wizard.id))
        assertTrue(cues.drawn(42L, 3, wizardSlot, bonusCueId(wizard), 0, 0.8))
    }

}
