package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.pets.PetArchetype
import org.polyfrost.polyplus.client.pets.PetAssets
import org.polyfrost.polyplus.client.pets.PetBody
import org.polyfrost.polyplus.client.pets.PetBrain
import org.polyfrost.polyplus.client.pets.PetDirective
import org.polyfrost.polyplus.client.pets.PetGoal
import org.polyfrost.polyplus.client.pets.PetGround
import org.polyfrost.polyplus.client.pets.PetInteraction
import org.polyfrost.polyplus.client.pets.PetInteractions
import org.polyfrost.polyplus.client.pets.PetMarkClearance
import org.polyfrost.polyplus.client.pets.PetMeet
import org.polyfrost.polyplus.client.pets.PetOwner
import org.polyfrost.polyplus.client.pets.PetRoles
import org.polyfrost.polyplus.client.pets.PetSharedRole
import org.polyfrost.polyplus.client.pets.PetSolid
import org.polyfrost.polyplus.client.pets.PetVariables
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

class PetPairingTest {
    private val none = PetInteraction.entries.associateWith { PetRoles(asA = false, asB = false) }

    private fun role(role: Int, seconds: Double, looping: Boolean = false, delay: Double = 0.0) =
        PetSharedRole(0, PetInteraction.Greet, role, 0.0, 0.0, 0.0, 0f, seconds, looping, delay)

    @Test
    fun `generic pets can always pair`() {
        assertTrue(PetInteractions.compatible(PetArchetype.Walking, emptyMap(), PetArchetype.Flying, emptyMap()))
        assertTrue(PetInteractions.compatible(PetArchetype.Shoulder, emptyMap(), PetArchetype.Walking, emptyMap()))
    }

    @Test
    fun `a pet with every interaction off pairs with nobody`() {
        assertFalse(PetInteractions.compatible(PetArchetype.Walking, none, PetArchetype.Walking, emptyMap()))
        assertFalse(PetInteractions.compatible(PetArchetype.Walking, emptyMap(), PetArchetype.Walking, none))
    }

    @Test
    fun `two pets that can only lead the same play are not a pair`() {
        val leadOnly = none + (PetInteraction.Play to PetRoles(asA = true, asB = false))
        val followOnly = none + (PetInteraction.Play to PetRoles(asA = false, asB = true))
        assertFalse(PetInteractions.compatible(PetArchetype.Walking, leadOnly, PetArchetype.Walking, leadOnly))
        assertTrue(PetInteractions.compatible(PetArchetype.Walking, leadOnly, PetArchetype.Walking, followOnly))
        assertTrue(PetInteractions.compatible(PetArchetype.Walking, followOnly, PetArchetype.Walking, leadOnly))
    }

    @Test
    fun `a shoulder pet only greets`() {
        val playOnly = none + (PetInteraction.Play to PetRoles(asA = true, asB = true))
        assertFalse(PetInteractions.compatible(PetArchetype.Shoulder, emptyMap(), PetArchetype.Walking, playOnly))
    }

    @Test
    fun `a session lasts through the authored start delay`() {
        assertEquals(3.5, PetInteractions.sessionSeconds(PetInteraction.Greet, role(0, 2.0, delay = 1.5), role(1, 3.0)))
        assertEquals(3.0, PetInteractions.sessionSeconds(PetInteraction.Greet, role(0, 2.0), role(1, 3.0)))
    }

    @Test
    fun `generic roles fall back to the interaction length`() {
        assertEquals(PetInteraction.Chase.seconds, PetInteractions.sessionSeconds(PetInteraction.Chase, null, null))
    }

    @Test
    fun `a looping role lasts at least the interaction length`() {
        assertEquals(PetInteraction.Greet.seconds, PetInteractions.sessionSeconds(PetInteraction.Greet, role(0, 1.0, looping = true), null))
        assertEquals(5.0, PetInteractions.sessionSeconds(PetInteraction.Greet, role(0, 1.0, looping = true, delay = 4.0), null))
    }

    @Test
    fun `start delays are read from the bundle animation files`(@TempDir dir: Path) {
        val file = dir.resolve("pet.animation.json")
        Files.writeString(
            file,
            """{"animations":{"animation.pet.greet_a":{"start_delay":1.25},"animation.pet.greet_b":{"start_delay":"0.5"},""" +
                """"animation.pet.idle":{"start_delay":"math.random(0, 1)"},"animation.pet.nap":{}}}""",
        )
        val delays = PetAssets.startDelays(listOf("pet.animation.json" to file))
        assertEquals(mapOf("animation.pet.greet_a" to 1.25, "animation.pet.greet_b" to 0.5), delays)
    }

    @Test
    fun `the shared mark rides the shoulder pet's owner`() {
        val shift = DoubleArray(3)
        PetInteractions.markShift(shift, true, 2.0, 1.5, -1.0, false, 9.0, 9.0, 9.0)
        assertArrayEquals(doubleArrayOf(2.0, 1.5, -1.0), shift, 1e-9)
        PetInteractions.markShift(shift, false, 9.0, 9.0, 9.0, true, -3.0, 0.5, 4.0)
        assertArrayEquals(doubleArrayOf(-3.0, 0.5, 4.0), shift, 1e-9)
    }

    @Test
    fun `two shoulder pets keep their mark between both walking owners`() {
        val shift = DoubleArray(3)
        PetInteractions.markShift(shift, true, 4.0, 0.0, 2.0, true, 4.0, 1.0, 6.0)
        assertArrayEquals(doubleArrayOf(4.0, 0.5, 4.0), shift, 1e-9)
    }

    @Test
    fun `a riding mark is checked for owners after it moves with them`() {
        val ownerX = 1.0
        var markX = 1.0
        val follows = {
            markX += 2.0
            true
        }
        val taken = { abs(markX - ownerX) < 0.5 }
        assertFalse(PetInteractions.sessionEnds(true, { false }, follows, taken))
        assertEquals(3.0, markX, 1e-9)
        markX = 1.0
        assertTrue(PetInteractions.sessionEnds(false, { false }, follows, taken))
        assertEquals(1.0, markX, 1e-9)
    }

    @Test
    fun `a riding session still ends when its mark can't follow`() {
        assertTrue(PetInteractions.sessionEnds(true, { false }, { false }, { false }))
        assertTrue(PetInteractions.sessionEnds(true, { false }, { true }, { true }))
        assertTrue(PetInteractions.sessionEnds(false, { true }, { true }, { false }))
        assertFalse(PetInteractions.sessionEnds(false, { false }, { true }, { false }))
    }

    @Test
    fun `ground pets keep a still mark`() {
        val shift = doubleArrayOf(1.0, 1.0, 1.0)
        PetInteractions.markShift(shift, false, 4.0, 1.0, 2.0, false, 4.0, 1.0, 6.0)
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0), shift, 1e-9)
    }

    @Test
    fun `a shared play runs on the cosmetic clock`() {
        val directive = PetDirective()
        assertEquals(0f, PetInteractions.roleAge(directive, 50.0))
        directive.startedAt = 3.0
        directive.startedClock = 100.0
        assertEquals(1.5f, PetInteractions.roleAge(directive, 101.5))
        assertFalse(PetInteractions.played(Double.NaN, 3.0, 500.0))
        assertFalse(PetInteractions.played(100.0, 3.0, 102.9))
        assertTrue(PetInteractions.played(100.0, 3.0, 103.0))
    }

    @Test
    fun `generic sessions end on the pet clock`() {
        assertFalse(PetInteractions.roleTimed(null, null))
        assertFalse(PetInteractions.roleTimed(role(0, 0.0), null))
        assertTrue(PetInteractions.roleTimed(null, role(1, 2.0)))
        val chase = PetInteraction.Chase.seconds
        assertFalse(PetInteractions.sessionPlayed(false, 10.0, 100.0, chase, 13.0, 112.0))
        assertTrue(PetInteractions.sessionPlayed(false, 10.0, 100.0, chase, 22.0, 101.0))
        assertTrue(PetInteractions.sessionPlayed(true, 10.0, 100.0, 3.0, 10.5, 103.0))
        assertFalse(PetInteractions.sessionPlayed(true, 10.0, 100.0, 3.0, 20.0, 102.0))
        assertFalse(PetInteractions.sessionPlayed(false, Double.NaN, Double.NaN, chase, 500.0, 500.0))
    }

    @Test
    fun `a pair that can't lay out the seeded interaction tries the others in order`() {
        val plays = listOf(PetInteraction.Greet, PetInteraction.Follow, PetInteraction.Chase)
        assertEquals(PetInteraction.Chase, PetInteractions.pick(plays, 2L) { true })
        assertEquals(PetInteraction.Greet, PetInteractions.pick(plays, 2L) { it != PetInteraction.Chase })
        assertEquals(PetInteraction.Follow, PetInteractions.pick(plays, 2L) { it == PetInteraction.Follow })
        assertEquals(null, PetInteractions.pick(plays, 2L) { false })
        assertEquals(null, PetInteractions.pick(emptyList<PetInteraction>(), 2L) { true })
    }

    private val reachOf = mapOf(PetInteraction.Greet to 2.0, PetInteraction.Follow to 0.5, PetInteraction.Chase to 1.0)

    private fun wallFits(play: PetInteraction, meet: PetMeet): Boolean {
        val axis = DoubleArray(2)
        PetInteractions.meetAxis(axis, meet, true)
        val reach = reachOf.getValue(play)
        val aZ = meet.z - axis[1] * reach
        val bZ = meet.z + axis[1] * reach
        return aZ >= 0.0 && bZ >= 0.0
    }

    @Test
    fun `the pick is the same wherever each client simulates the pets`() {
        val plays = listOf(PetInteraction.Greet, PetInteraction.Follow, PetInteraction.Chase)
        val shared = PetMeet().set(0.0, 64.0, 3.0, -2.0, 3.0, 2.0, 3.0)
        val nearWall = PetMeet().set(0.0, 64.0, 0.8, 0.0, 1.8, 0.0, -0.2)
        val open = PetMeet().set(0.0, 64.0, 3.0, 0.0, 1.0, 0.0, 5.0)
        assertEquals(PetInteraction.Follow, PetInteractions.pick(plays, 0L) { wallFits(it, nearWall) })
        var placedNearWall: PetMeet? = null
        val nearWallPick = PetInteractions.choose(plays, 0L, shared, nearWall) { play, meet ->
            wallFits(play, meet).also { if (it) placedNearWall = meet }
        }
        var placedOpen: PetMeet? = null
        val openPick = PetInteractions.choose(plays, 0L, shared, open) { play, meet ->
            wallFits(play, meet).also { if (it) placedOpen = meet }
        }
        assertEquals(PetInteraction.Greet, nearWallPick)
        assertEquals(nearWallPick, openPick)
        assertEquals(nearWallPick, PetInteractions.choose(plays, 0L, shared, null) { play, meet -> wallFits(play, meet) })
        assertTrue(placedNearWall === shared)
        assertTrue(placedOpen === open)
    }

    @Test
    fun `a pick that fits nowhere shared is skipped on every client`() {
        val plays = listOf(PetInteraction.Greet, PetInteraction.Follow, PetInteraction.Chase)
        val shared = PetMeet().set(0.0, 64.0, 0.7, -2.0, 0.0, 2.0, 2.0)
        val open = PetMeet().set(0.0, 64.0, 5.0, 0.0, 3.0, 0.0, 7.0)
        val picked = PetInteractions.choose(plays, 0L, shared, open) { play, meet -> wallFits(play, meet) }
        assertEquals(PetInteractions.pick(plays, 0L) { wallFits(it, shared) }, picked)
        assertEquals(PetInteraction.Follow, picked)
        val tight = PetMeet().set(0.0, 64.0, 0.2, 0.0, -1.0, 0.0, 1.0)
        assertEquals(null, PetInteractions.choose(plays, 0L, tight, open) { play, meet -> wallFits(play, meet) })
    }

    @Test
    fun `the pair axis runs from the leading owner to the other`() {
        val axis = DoubleArray(2)
        val meet = PetMeet().set(0.0, 0.0, 0.0, 1.0, 1.0, 4.0, 5.0)
        PetInteractions.meetAxis(axis, meet, true)
        assertArrayEquals(doubleArrayOf(0.6, 0.8), axis, 1e-9)
        PetInteractions.meetAxis(axis, meet, false)
        assertArrayEquals(doubleArrayOf(-0.6, -0.8), axis, 1e-9)
        PetInteractions.meetAxis(axis, PetMeet().set(0.0, 0.0, 0.0, 2.0, 2.0, 2.0, 2.0), true)
        assertArrayEquals(doubleArrayOf(0.0, 1.0), axis, 1e-9)
    }

    @Test
    fun `a raised role has to clear the blocks above its mark`() {
        val ceiling = PetSolid { _, y, _, _, height -> y + height > 65.5 }
        assertFalse(PetInteractions.roleBoxFree(0.0, 64.0, 0.0, 0.8, 0.3, 0.9, ceiling))
        assertTrue(PetInteractions.roleBoxFree(0.0, 64.0, 0.0, 0.3, 0.3, 0.9, ceiling))
        assertTrue(PetInteractions.roleBoxFree(0.0, 64.0, 0.0, 0.0, 0.3, 0.9, ceiling))
        assertTrue(PetInteractions.roleBoxFree(0.0, 64.0, 0.0, -0.2, 0.3, 0.9, ceiling))
    }

    private val platform = PetGround { x, _, _, _, top, bottom -> if (x <= 2.0 && 64.0 in bottom..top) 64.0 else Double.NaN }
    private val clear = PetSolid { _, _, _, _, _ -> false }

    private fun base(directive: PetDirective, x: Double, y: Double, z: Double) {
        directive.baseMarkX = x
        directive.baseMarkY = y
        directive.baseMarkZ = z
    }

    @Test
    fun `a walking pet's moved mark needs ground under it`() {
        val directive = PetDirective()
        base(directive, 1.0, 64.0, 0.0)
        assertTrue(PetInteractions.shiftMark(directive, PetArchetype.Walking, 0.5, 0.2, 0.0, 0.0, 0.3, 0.6, platform, clear))
        assertEquals(1.5, directive.markX)
        assertEquals(64.0, directive.markY)
        assertFalse(PetInteractions.shiftMark(directive, PetArchetype.Walking, 2.0, 0.0, 0.0, 0.0, 0.3, 0.6, platform, clear))
        val ceiling = PetSolid { _, y, _, _, height -> y + height > 65.0 }
        directive.authored = true
        assertFalse(PetInteractions.shiftMark(directive, PetArchetype.Walking, 0.5, 0.0, 0.0, 0.8, 0.3, 0.6, platform, ceiling))
    }

    @Test
    fun `a generic mark moved by a shoulder owner can't walk into the other owner`() {
        val directive = PetDirective()
        base(directive, 1.0, 64.0, 0.0)
        val discs = DoubleArray(PetMarkClearance.STRIDE * 2)
        assertTrue(PetInteractions.shiftMark(directive, PetArchetype.Walking, 0.6, 0.0, 0.0, 0.0, 0.3, 0.6, platform, clear))
        var count = PetInteractions.markFootprint(discs, 0, PetArchetype.Walking, 0.3, 0.6, 0.0, directive, PetInteraction.Greet, PetInteractions.ROLE_B, 0.0, 0.0)
        assertEquals(1, count)
        assertTrue(PetMarkClearance.clears(discs, count, 0.0, 0.0, 2.2, 64.0, 0.0, 0.0))
        assertTrue(PetInteractions.shiftMark(directive, PetArchetype.Walking, 0.9, 0.0, 0.0, 0.0, 0.3, 0.6, platform, clear))
        count = PetInteractions.markFootprint(discs, 0, PetArchetype.Walking, 0.3, 0.6, 0.0, directive, PetInteraction.Greet, PetInteractions.ROLE_B, 0.0, 0.0)
        assertFalse(PetMarkClearance.clears(discs, count, 0.0, 0.0, 2.2, 64.0, 0.0, 0.0))
        assertTrue(PetMarkClearance.clears(discs, count, 0.0, 0.0, 2.2, 66.0, 0.0, 0.0))
    }

    @Test
    fun `a generic shoulder pet's mark doesn't count against its own owner`() {
        val directive = PetDirective()
        directive.markX = 0.0
        directive.markY = 65.5
        directive.markZ = 0.0
        val discs = DoubleArray(PetMarkClearance.STRIDE * 2)
        assertEquals(0, PetInteractions.markFootprint(discs, 0, PetArchetype.Shoulder, 0.2, 0.4, 0.0, directive, PetInteraction.Greet, PetInteractions.ROLE_A, 0.0, 0.0))
        directive.authored = true
        val count = PetInteractions.markFootprint(discs, 0, PetArchetype.Shoulder, 0.2, 0.4, 0.0, directive, PetInteraction.Greet, PetInteractions.ROLE_A, 0.0, 0.0)
        assertEquals(1, count)
        assertFalse(PetMarkClearance.clears(discs, count, 0.0, 0.0, 0.0, 64.0, 0.0, 0.0))
    }

    @Test
    fun `a moved mark off the ground has to stay out of blocks`() {
        val wall = PetSolid { x, _, _, halfWidth, _ -> x + halfWidth > 3.0 }
        val directive = PetDirective()
        base(directive, 1.0, 65.0, 0.0)
        assertTrue(PetInteractions.shiftMark(directive, PetArchetype.Flying, 1.0, 0.5, 0.0, 0.0, 0.3, 0.5, platform, wall))
        assertEquals(65.5, directive.markY)
        assertFalse(PetInteractions.shiftMark(directive, PetArchetype.Flying, 1.9, 0.0, 0.0, 0.0, 0.3, 0.5, platform, wall))
        assertTrue(PetInteractions.shiftMark(directive, PetArchetype.Shoulder, 1.9, 0.0, 0.0, 0.0, 0.3, 0.5, platform, wall))
        directive.authored = true
        assertFalse(PetInteractions.shiftMark(directive, PetArchetype.Shoulder, 1.9, 0.0, 0.0, 0.0, 0.3, 0.5, platform, wall))
    }

    @Test
    fun `marks off the ground have to fit between blocks`() {
        val wall = PetSolid { _, _, z, halfWidth, _ -> z - halfWidth < 1.0 }
        assertFalse(PetInteractions.airMarkFree(PetArchetype.Flying, false, 0.0, 1.0, 0.5, 0.3, 0.5, wall))
        assertTrue(PetInteractions.airMarkFree(PetArchetype.Flying, false, 0.0, 1.0, 1.5, 0.3, 0.5, wall))
        assertFalse(PetInteractions.airMarkFree(PetArchetype.Shoulder, true, 0.0, 1.0, 0.5, 0.3, 0.5, wall))
        assertTrue(PetInteractions.airMarkFree(PetArchetype.Shoulder, false, 0.0, 1.0, 0.5, 0.3, 0.5, wall))
        assertTrue(PetInteractions.airMarkFree(PetArchetype.Walking, false, 0.0, 1.0, 0.5, 0.3, 0.5, wall))
        val out = DoubleArray(2)
        val found = PetMarkClearance.search(out, 0.0, 0.0, -4.0, 0.0, 4.0, 0.0, true) { x, z ->
            PetInteractions.airMarkFree(PetArchetype.Flying, false, x, 1.0, z, 0.3, 0.5, wall)
        }
        assertTrue(found)
        assertTrue(out[1] >= 1.3, "z ${out[1]}")
    }

    @Test
    fun `owners walk off from their own pet's play spot`() {
        val directive = PetDirective()
        directive.markX = 1.0
        directive.markY = 2.0
        directive.markZ = 3.0
        directive.circleX = 0.0
        directive.circleZ = 0.0
        directive.circleRadius = 2.0
        val out = DoubleArray(3)
        directive.interaction = PetInteraction.Greet
        PetInteractions.playSpot(out, directive, directive.interaction, directive.role, -5.0, 0.0)
        assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), out, 1e-9)
        directive.interaction = PetInteraction.Follow
        directive.role = PetInteractions.ROLE_B
        PetInteractions.playSpot(out, directive, directive.interaction, directive.role, -5.0, 0.0)
        assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), out, 1e-9)
        directive.role = PetInteractions.ROLE_A
        PetInteractions.playSpot(out, directive, directive.interaction, directive.role, -5.0, 0.0)
        assertArrayEquals(doubleArrayOf(2.0, 2.0, 0.0), out, 1e-9)
        directive.interaction = PetInteraction.Chase
        PetInteractions.playSpot(out, directive, directive.interaction, directive.role, 0.0, 5.0)
        assertArrayEquals(doubleArrayOf(0.0, 2.0, -2.0), out, 1e-9)
        directive.authored = true
        PetInteractions.playSpot(out, directive, directive.interaction, directive.role, 0.0, 5.0)
        assertArrayEquals(doubleArrayOf(1.0, 2.0, 3.0), out, 1e-9)
    }

    @Test
    fun `a shoulder pet in a pair play reports the play mode`() {
        val body = PetBody(3L)
        val owner = PetOwner()
        assertEquals(PetVariables.IDLE, PetBrain.mode(body, PetArchetype.Shoulder, owner))
        body.mind.goal = PetGoal.Play
        assertEquals(PetVariables.PLAYING, PetBrain.mode(body, PetArchetype.Shoulder, owner))
    }

    @Test
    fun `a pet playing with a mounted owner reports the play mode`() {
        val body = PetBody(3L)
        val owner = PetOwner()
        val riding = PlayerSignalRecord()
        riding.set(PlayerSignals.IS_RIDING, true)
        owner.signals = riding
        assertEquals(PetVariables.RIDING, PetBrain.mode(body, PetArchetype.Shoulder, owner))
        body.mind.goal = PetGoal.Play
        body.directive.interaction = PetInteraction.Greet
        assertEquals(PetVariables.PLAYING, PetBrain.mode(body, PetArchetype.Shoulder, owner))
        assertEquals(PetVariables.PLAYING, PetBrain.mode(body, PetArchetype.Walking, owner))
        body.directive.clear()
        assertEquals(PetVariables.RIDING, PetBrain.mode(body, PetArchetype.Shoulder, owner))
    }
}
