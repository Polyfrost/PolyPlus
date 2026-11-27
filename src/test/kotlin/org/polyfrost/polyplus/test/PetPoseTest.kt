package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.pets.PetInteraction
import org.polyfrost.polyplus.client.pets.PetOwner
import org.polyfrost.polyplus.client.pets.PetOwnerParts
import org.polyfrost.polyplus.client.pets.PetPath
import org.polyfrost.polyplus.client.pets.PetPlacement
import org.polyfrost.polyplus.client.pets.PetPose
import org.polyfrost.polyplus.client.pets.PetSharedRole

class PetPoseTest {
    private val pixel = 1f / 16f

    private fun up(owner: PetOwner): FloatArray {
        val out = FloatArray(12)
        PetPose.body(out, owner, 0f, 0f, 0f)
        return floatArrayOf(out[1], out[5], out[9], out[3], out[7], out[11])
    }

    @Test
    fun `a standing owner keeps the shoulder upright`() {
        val axis = up(PetOwner())
        assertEquals(0f, axis[0], 1e-6f)
        assertEquals(pixel, axis[1], 1e-6f)
        assertEquals(0f, axis[2], 1e-6f)
    }

    @Test
    fun `a sleeping owner lays the shoulder flat toward the bed head`() {
        val owner = PetOwner()
        owner.sleepYaw = PetPose.sleepYaw(-1, 0)
        owner.sleepShiftX = 1.52f
        val axis = up(owner)
        assertEquals(-pixel, axis[0], 1e-6f)
        assertEquals(0f, axis[1], 1e-6f)
        assertEquals(0f, axis[2], 1e-6f)
        assertEquals(1.52f, axis[3], 1e-6f)
    }

    @Test
    fun `a gliding owner tips the shoulder forward`() {
        val owner = PetOwner()
        owner.leanPitch = -90f
        val axis = up(owner)
        assertEquals(0f, axis[0], 1e-6f)
        assertEquals(0f, axis[1], 1e-6f)
        assertEquals(pixel, axis[2], 1e-6f)
    }

    @Test
    fun `beds and glides turn the way vanilla turns the body`() {
        assertEquals(90f, PetPose.sleepYaw(0, 1))
        assertEquals(270f, PetPose.sleepYaw(0, -1))
        assertEquals(180f, PetPose.sleepYaw(1, 0))
        assertEquals(0f, PetPose.sleepYaw(-1, 0))
        assertEquals(0f, PetPose.glideYaw(0.0, 1.0, 0.0, 1.0), 1e-6f)
        assertEquals((Math.PI / 4).toFloat(), PetPose.glideYaw(0.0, 1.0, 1.0, 1.0), 1e-6f)
        assertEquals(0f, PetPose.glideYaw(0.0, 1.0, 0.0, 0.0))
    }

    @Test
    fun `a glide turn past 90 degrees keeps its full angle where vanilla does`() {
        val along = -Math.sqrt(0.5)
        assertEquals((3 * Math.PI / 4).toFloat(), PetPose.glideTurn(along, 1.0, folds = false), 1e-6f)
        assertEquals((-3 * Math.PI / 4).toFloat(), PetPose.glideTurn(along, -1.0, folds = false), 1e-6f)
        assertEquals((Math.PI / 4).toFloat(), PetPose.glideTurn(along, 1.0, folds = true), 1e-6f)
        assertEquals(0f, PetPose.glideTurn(1.0000001, 1.0, folds = false), 1e-6f)
        assertEquals(Math.PI.toFloat(), PetPose.glideTurn(-1.0000001, 1.0, folds = false), 1e-6f)
    }

    private fun restParts(): FloatArray {
        val parts = FloatArray(PetOwnerParts.FLOATS)
        PetOwnerParts.set(parts, PetOwnerParts.ROOT, 0f, 0f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.HEAD, 0f, 0f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.BODY, 0f, 0f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.RIGHT_ARM, -5f, 2f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.LEFT_ARM, 5f, 2f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.RIGHT_LEG, -1.9f, 12f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.LEFT_LEG, 1.9f, 12f, 0f, 0f, 0f, 0f)
        PetOwnerParts.set(parts, PetOwnerParts.HAT, 0f, 0f, 0f, 0f, 0f, 0f)
        return parts
    }

    private fun shoulder(owner: PetOwner, anchor: String, parts: FloatArray?): FloatArray {
        val out = FloatArray(12)
        PetPose.shoulder(out, owner, anchor, 1f, 2f, 3f, 1f, parts)
        return out
    }

    @Test
    fun `a rendered rest pose sits where the walking pose puts it`() {
        val owner = PetOwner()
        owner.renderBodyYaw = 30f
        owner.renderHeadYaw = 30f
        for (anchor in listOf("root", "body", "head", "hat", PetPose.RIGHT_ARM, PetPose.LEFT_ARM, "rightLeg", "leftLeg")) {
            assertArrayEquals(shoulder(owner, anchor, null), shoulder(owner, anchor, restParts()), 1e-5f, anchor)
        }
    }

    @Test
    fun `a rendered raised arm carries the pet with it`() {
        val owner = PetOwner()
        owner.renderBodyYaw = -45f
        owner.limbSwing = 0f
        owner.limbAmount = 1.2f
        val walking = shoulder(owner, PetPose.RIGHT_ARM, null)
        val parts = restParts()
        PetOwnerParts.set(parts, PetOwnerParts.RIGHT_ARM, -5f, 2f, 0f, -1.2f, 0f, 0f)
        owner.limbAmount = 0f
        assertArrayEquals(walking, shoulder(owner, PetPose.RIGHT_ARM, parts), 1e-5f)
    }

    @Test
    fun `an arm raised sideways moves the pet to the rendered arm`() {
        val owner = PetOwner()
        val parts = restParts()
        PetOwnerParts.set(parts, PetOwnerParts.RIGHT_ARM, -5f, 2f, 0f, 0f, 0f, (Math.PI / 2).toFloat())
        val raised = shoulder(owner, PetPose.RIGHT_ARM, parts)
        assertEquals(1f - 3f / 16f, raised[3], 1e-5f)
        assertEquals(2f + 23f / 16f, raised[7], 1e-5f)
        assertEquals(3f, raised[11], 1e-5f)
    }

    @Test
    fun `a rendered emote root moves the anchor with the body`() {
        val owner = PetOwner()
        val parts = restParts()
        PetOwnerParts.set(parts, PetOwnerParts.ROOT, 0f, -8f, 0f, 0f, 0f, 0f)
        val lifted = shoulder(owner, PetPose.LEFT_ARM, parts)
        val resting = shoulder(owner, PetPose.LEFT_ARM, restParts())
        assertEquals(resting[7] + 0.5f, lifted[7], 1e-5f)
    }

    @Test
    fun `a root anchored pet follows the emote root's move, turn and scale`() {
        val owner = PetOwner()
        val parts = restParts()
        PetOwnerParts.set(parts, PetOwnerParts.ROOT, 0f, -8f, 0f, 0f, (Math.PI / 2).toFloat(), 0f, 1f, 0.5f, 1f)
        val posed = shoulder(owner, "root", parts)
        val resting = shoulder(owner, "root", restParts())
        assertEquals(resting[7] + 1.25f, posed[7], 1e-5f)
        assertEquals(resting[3], posed[3], 1e-5f)
        assertEquals(resting[11], posed[11], 1e-5f)
        assertEquals(0.5f / 16f, posed[5], 1e-5f)
        assertEquals(0f, posed[0], 1e-5f)
        assertEquals(-1f / 16f, posed[2], 1e-5f)
    }

    @Test
    fun `a hat anchored pet rides the rendered hat, not the head`() {
        val owner = PetOwner()
        val parts = restParts()
        PetOwnerParts.set(parts, PetOwnerParts.HAT, 0f, -4f, 0f, 0f, 0f, 0f, 2f, 2f, 2f)
        val onHat = shoulder(owner, "hat", parts)
        val onHead = shoulder(owner, "head", parts)
        assertEquals(shoulder(owner, "head", restParts())[7], onHead[7], 1e-5f)
        assertEquals(onHead[7] + 0.75f, onHat[7], 1e-5f)
        assertEquals(2f / 16f, onHat[5], 1e-5f)
    }

    @Test
    fun `a hat anchored pet still follows the head the hat sits on`() {
        val owner = PetOwner()
        val parts = restParts()
        PetOwnerParts.set(parts, PetOwnerParts.HEAD, 0f, -8f, 0f, 0f, 0f, 0f)
        if (!PetOwnerParts.HAT_ON_HEAD) PetOwnerParts.set(parts, PetOwnerParts.HAT, 0f, -8f, 0f, 0f, 0f, 0f)
        val lifted = shoulder(owner, "hat", parts)
        val resting = shoulder(owner, "hat", restParts())
        assertEquals(resting[7] + 0.5f, lifted[7], 1e-5f)
    }

    private fun uniformScale(m: FloatArray): Float {
        val det = m[0] * (m[5] * m[10] - m[6] * m[9]) - m[1] * (m[4] * m[10] - m[6] * m[8]) +
            m[2] * (m[4] * m[9] - m[5] * m[8])
        return Math.cbrt(det.toDouble()).toFloat()
    }

    private fun engineMark(anchor: FloatArray, offset: DoubleArray, roleYaw: Float, scale: Float): FloatArray {
        val turn = Math.toRadians(anchor[3].toDouble())
        val x = anchor[0] + offset[0] * Math.cos(turn) - offset[2] * Math.sin(turn)
        val y = anchor[1] + offset[1]
        val z = anchor[2] + offset[0] * Math.sin(turn) + offset[2] * Math.cos(turn)
        val facing = Math.toRadians((anchor[3] + roleYaw).toDouble())
        val s = Math.sin(facing).toFloat() * scale
        val c = Math.cos(facing).toFloat() * scale
        return floatArrayOf(c, 0f, -s, x.toFloat(), 0f, scale, 0f, y.toFloat(), s, 0f, c, z.toFloat())
    }

    @Test
    fun `role anchors sit in the pet root's space`() {
        val cameraX = 100.3
        val cameraY = 64.0
        val cameraZ = -20.7
        val originX = 96.0
        val originY = 60.0
        val originZ = -16.0
        val x = 103.25
        val y = 65.5
        val z = -18.4
        val root = FloatArray(12)
        PetPose.root(
            root,
            (x - cameraX).toFloat(),
            (y - cameraY).toFloat(),
            (z - cameraZ).toFloat(),
            Math.toRadians(37.0).toFloat(),
            0.3f,
            2.5f * pixel,
        )
        root[3] -= (originX - cameraX).toFloat()
        root[7] -= (originY - cameraY).toFloat()
        root[11] -= (originZ - cameraZ).toFloat()
        val anchor = FloatArray(4)
        PetPose.roleAnchor(anchor, x, y, z, 120f, originX, originY, originZ)
        assertArrayEquals(floatArrayOf(root[3], root[7], root[11], 120f), anchor, 1e-4f)
    }

    @Test
    fun `a pet on its role mark poses like a pet simulated there`() {
        val offset = doubleArrayOf(0.5, 0.1, -0.75)
        val anchor = FloatArray(4)
        PetPose.roleAnchor(anchor, 5.0, 1.5, 4.0, 120f, 0.0, 0.0, 0.0)
        val root = FloatArray(12)
        PetPose.root(root, 3f, 1f, -2f, Math.toRadians(37.0).toFloat(), 0.4f, 2.5f * pixel)
        val scale = uniformScale(root)
        assertEquals(2.5f * pixel, scale, 1e-6f)
        val mark = engineMark(anchor, offset, 45f, scale)
        val turn = Math.toRadians(120.0)
        val markX = 5.0 + offset[0] * Math.cos(turn) - offset[2] * Math.sin(turn)
        val markZ = 4.0 + offset[0] * Math.sin(turn) + offset[2] * Math.cos(turn)
        val simulated = FloatArray(12)
        PetPose.root(simulated, markX.toFloat(), 1.6f, markZ.toFloat(), Math.toRadians(165.0).toFloat(), 0f, scale)
        assertArrayEquals(simulated, mark, 1e-5f)
    }

    @Test
    fun `a shoulder pet keeps its size on the role mark`() {
        val owner = PetOwner()
        owner.renderBodyYaw = 70f
        val root = FloatArray(12)
        PetPose.shoulder(root, owner, PetPose.RIGHT_ARM, -3f, 2f, 7f, 0.6f, restParts())
        val scale = uniformScale(root)
        assertEquals(0.6f * pixel, scale, 1e-6f)
        val anchor = FloatArray(4)
        PetPose.roleAnchor(anchor, -2.5, 69.0, 7.5, -30f, 0.0, 60.0, 0.0)
        val mark = engineMark(anchor, doubleArrayOf(0.0, 0.0, 0.75), 180f, scale)
        val simulated = FloatArray(12)
        val turn = Math.toRadians(-30.0)
        PetPose.root(
            simulated,
            (-2.5 - 0.75 * Math.sin(turn)).toFloat(),
            9f,
            (7.5 + 0.75 * Math.cos(turn)).toFloat(),
            Math.toRadians(150.0).toFloat(),
            0f,
            scale,
        )
        assertArrayEquals(simulated, mark, 1e-5f)
    }

    private fun placedAt(role: PetSharedRole, age: Float): FloatArray {
        val root = FloatArray(12)
        PetPose.root(root, 1f, 2f, 3f, 0f, 0f, pixel)
        val anchor = floatArrayOf(10f, 20f, 30f, 90f)
        PetPose.placeRole(root, 0, role, age, anchor)
        return floatArrayOf(root[3], root[7], root[11])
    }

    private fun role(looping: Boolean) =
        PetSharedRole(0, PetInteraction.Greet, 0, 1.0, 0.5, 0.0, 0f, 2.0, looping, 0.5)

    @Test
    fun `role effects come from the mark the engine places the pet on`() {
        val role = role(looping = false)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), placedAt(role, 0.4f), 1e-5f)
        assertArrayEquals(floatArrayOf(5.5f, 11.25f, 17f), placedAt(role, 0.57f), 1e-4f)
        assertArrayEquals(floatArrayOf(10f, 20.5f, 31f), placedAt(role, 1f), 1e-5f)
        assertArrayEquals(floatArrayOf(10f, 20.5f, 31f), placedAt(role(looping = true), 9f), 1e-5f)
    }

    @Test
    fun `a finished role clip stays on the mark until the shared play ends`() {
        val role = role(looping = false)
        assertEquals(1f, PetPose.roleWeight(role, 2.7f))
        assertArrayEquals(floatArrayOf(10f, 20.5f, 31f), placedAt(role, 2.7f), 1e-5f)
        assertArrayEquals(floatArrayOf(10f, 20.5f, 31f), placedAt(role, 3.5f), 1e-5f)
    }

    @Test
    fun `a placed role keeps its pet in the world through both fades`() {
        val role = role(looping = false)
        val placement = PetPlacement()
        assertEquals(0f, placement.weigh(null, 0f, 9.0))
        placement.commit()
        assertEquals(0f, placement.weigh(role, 0.4f, 10.0))
        placement.commit()
        assertTrue(placement.weigh(role, 0.57f, 10.17) > 0f)
        placement.commit()
        assertEquals(0.5f, placement.weigh(null, 0f, 10.3), 1e-5f)
        placement.commit()
        assertEquals(0.25f, placement.weigh(null, 0f, 10.37), 1e-4f)
        placement.commit()
        assertEquals(0f, placement.weigh(null, 0f, 10.45))
        placement.commit()
        assertEquals(0f, placement.base)
        assertEquals(0f, placement.weigh(null, 0f, 10.46))
    }

    @Test
    fun `the fade out starts on the first frame the engine sees without the role`() {
        val role = role(looping = false)
        val placement = PetPlacement()
        assertEquals(1f, placement.weigh(role, 2f, 20.0))
        placement.commit()
        assertEquals(1f, placement.weigh(null, 0f, 20.2))
        placement.commit()
        assertEquals(20.2, placement.stop)
        assertTrue(placement.weigh(null, 0f, 20.33) > 0f)
        placement.commit()
        assertEquals(0f, placement.weigh(null, 0f, 20.35))
    }

    @Test
    fun `frames the engine never computes don't move the placement`() {
        val role = role(looping = false)
        val placement = PetPlacement()
        placement.weigh(role, 2f, 30.0)
        placement.commit()
        placement.weigh(null, 0f, 30.1)
        placement.weigh(null, 0f, 31.0)
        assertTrue(placement.stop.isNaN())
        assertEquals(1f, placement.weigh(null, 0f, 32.0))
        placement.commit()
        assertEquals(32.0, placement.stop)
    }

    @Test
    fun `big pets pay more of the path budget per node`() {
        assertEquals(1, PetPath.probeCost(0.25, 0.6))
        val big = PetPath.probeCost(2.0, 4.8)
        assertTrue(big in 2..64, "cost $big")
        assertTrue(PetPath.probeCost(0.45, 1.08) <= big)
    }
}
