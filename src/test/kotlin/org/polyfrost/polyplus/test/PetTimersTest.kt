package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetTimers

class PetTimersTest {
    @Test
    fun `an unset long timer is always due`() {
        assertTrue(PetTimers.due(0L, Long.MIN_VALUE, 5L))
        assertTrue(PetTimers.due(100L, Long.MIN_VALUE, 5L))
        assertTrue(PetTimers.due(-100L, Long.MIN_VALUE, 5L))
    }

    @Test
    fun `an unset int timer is always due`() {
        assertTrue(PetTimers.due(0, Int.MIN_VALUE, 4))
        assertTrue(PetTimers.due(5, Int.MIN_VALUE, 4))
        assertTrue(PetTimers.due(Int.MAX_VALUE, Int.MIN_VALUE, 4))
    }

    @Test
    fun `a set timer waits for its interval`() {
        assertFalse(PetTimers.due(100L, 100L, 5L))
        assertFalse(PetTimers.due(104L, 100L, 5L))
        assertTrue(PetTimers.due(105L, 100L, 5L))
        assertFalse(PetTimers.due(3, 0, 4))
        assertTrue(PetTimers.due(4, 0, 4))
    }

    @Test
    fun `a clock that went backwards makes the timer due`() {
        assertTrue(PetTimers.due(99L, 100L, 5L))
        assertTrue(PetTimers.due(0, 10, 4))
    }
}
