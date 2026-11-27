package org.polyfrost.polyplus.test

import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticOrigin

class CosmeticOriginTest {
    @Test
    fun `an emote root part moves the effect root the way it moves the model`() {
        val root = FloatArray(12)
        CosmeticOrigin.fromWorld(root, 10.0, 64.0, -5.0, 30f, 0.9375f)
        CosmeticOrigin.applyLayerPart(root, 2f, -3f, 4f, 0.3f, -0.7f, 0.2f, 1.2f, 0.8f, 1.1f)

        val part = Matrix4f().translation(2f / 16f, -3f / 16f, 4f / 16f)
            .rotateZ(0.2f).rotateY(-0.7f).rotateX(0.3f).scale(1.2f, 0.8f, 1.1f)
        val layer = Matrix4f()
            .translation((10.0 - CosmeticOrigin.x).toFloat(), (64.0 - CosmeticOrigin.y).toFloat(), (-5.0 - CosmeticOrigin.z).toFloat())
            .rotateY(Math.toRadians(180.0 - 30.0).toFloat())
            .scale(0.9375f)
            .scale(-1f, -1f, 1f)
            .translate(0f, -1.5f, 0f)
            .mul(part)
        for (point in listOf(Vector3f(0f, 0f, 0f), Vector3f(3f, 20f, -5f), Vector3f(-4f, 8f, 2f))) {
            val expected = layer.transformPosition(Vector3f(point.x / 16f, 1.5f - point.y / 16f, -point.z / 16f))
            val actual = Vector3f(
                root[0] * point.x + root[1] * point.y + root[2] * point.z + root[3],
                root[4] * point.x + root[5] * point.y + root[6] * point.z + root[7],
                root[8] * point.x + root[9] * point.y + root[10] * point.z + root[11],
            )
            assertEquals(expected.x, actual.x, 1e-4f)
            assertEquals(expected.y, actual.y, 1e-4f)
            assertEquals(expected.z, actual.z, 1e-4f)
        }
    }

    @Test
    fun `a resting root part leaves the effect root untouched`() {
        val root = FloatArray(12)
        CosmeticOrigin.fromWorld(root, 1.0, 2.0, 3.0, 45f, 0.9375f)
        val before = root.copyOf()
        CosmeticOrigin.applyLayerPart(root, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        assertArrayEquals(before, root)
    }
}
