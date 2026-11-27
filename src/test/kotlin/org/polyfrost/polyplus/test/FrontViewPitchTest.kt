package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.nameplate.render.FrontViewPitch

class FrontViewPitchTest {
    @Test
    fun `a vanilla cached pitch is flipped to face the camera`() {
        assertEquals(30f, FrontViewPitch.facing(-30f, -30f))
        assertEquals(-45f, FrontViewPitch.facing(45f, 45f))
    }

    @Test
    fun `a cached pitch another mod already flipped is kept`() {
        assertEquals(30f, FrontViewPitch.facing(30f, -30f))
        assertEquals(-45f, FrontViewPitch.facing(-45f, 45f))
    }

    @Test
    fun `the cached magnitude is kept when the camera pitch is interpolated`() {
        assertEquals(-20f, FrontViewPitch.facing(20f, 19.5f))
    }

    @Test
    fun `a level camera stays level`() {
        assertEquals(0f, FrontViewPitch.facing(0f, 0f) + 0f)
        assertEquals(-0.5f, FrontViewPitch.facing(0.5f, 0f))
    }
}
