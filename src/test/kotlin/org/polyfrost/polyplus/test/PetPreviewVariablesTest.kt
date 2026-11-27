package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.pets.PetArchetype
import org.polyfrost.polyplus.client.pets.PetPreviewVariables
import org.polyfrost.polyplus.client.pets.PetVariables

class PetPreviewVariablesTest {
    private val variables = FloatArray(PetVariables.NAMES.size)

    private fun record(vararg flags: Int, speed: Float = 0f): PlayerSignalRecord {
        val record = PlayerSignalRecord()
        for (flag in flags) record.set(flag, true)
        record.set(PlayerSignals.GROUND_SPEED, speed)
        return record
    }

    private fun mode(archetype: PetArchetype, signals: PlayerSignalRecord): Float {
        PetPreviewVariables.fill(variables, archetype, signals)
        return variables[PetVariables.MODE]
    }

    @Test
    fun `a still owner leaves the pet idle`() {
        assertEquals(PetVariables.IDLE, mode(PetArchetype.Walking, record()))
        assertEquals(1f, variables[PetVariables.ON_GROUND])
    }

    @Test
    fun `trying sleep puts every pet to sleep`() {
        for (archetype in PetArchetype.entries) {
            assertEquals(PetVariables.SLEEPING, mode(archetype, record(PlayerSignals.IS_SLEEPING)))
        }
    }

    @Test
    fun `a sneaking still owner sits a walking pet but not a flying one`() {
        assertEquals(PetVariables.SITTING, mode(PetArchetype.Walking, record(PlayerSignals.IS_SNEAKING)))
        assertEquals(PetVariables.IDLE, mode(PetArchetype.Flying, record(PlayerSignals.IS_SNEAKING)))
        assertEquals(PetVariables.IDLE, mode(PetArchetype.Shoulder, record(PlayerSignals.IS_SNEAKING)))
    }

    @Test
    fun `a moving owner makes the pet follow at their speed`() {
        assertEquals(PetVariables.FOLLOWING, mode(PetArchetype.Walking, record(PlayerSignals.IS_MOVING, speed = 4.3f)))
        assertEquals(4.3f, variables[PetVariables.SPEED])
    }

    @Test
    fun `riding seats a pet unless a walking pet has to keep up`() {
        assertEquals(PetVariables.RIDING, mode(PetArchetype.Walking, record(PlayerSignals.IS_RIDING)))
        assertEquals(PetVariables.RIDING, mode(PetArchetype.Shoulder, record(PlayerSignals.IS_RIDING, PlayerSignals.IS_MOVING)))
        assertEquals(PetVariables.FOLLOWING, mode(PetArchetype.Walking, record(PlayerSignals.IS_RIDING, PlayerSignals.IS_MOVING)))
    }

    @Test
    fun `flying pets are off the ground and walking pets swim with the owner`() {
        mode(PetArchetype.Flying, record())
        assertEquals(0f, variables[PetVariables.ON_GROUND])
        mode(PetArchetype.Walking, record(PlayerSignals.IS_IN_WATER))
        assertEquals(1f, variables[PetVariables.IN_WATER])
        assertEquals(0f, variables[PetVariables.ON_GROUND])
    }
}
