package org.polyfrost.polyplus.test

import org.joml.Matrix4f
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticOrigin
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRows
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticView

class CosmeticViewTest {
    @AfterEach
    fun leave() {
        CosmeticView.exitEntity()
    }

    @Test
    fun `an entity pass with no view keeps the layer pose untouched`() {
        CosmeticView.enterEntity(Matrix4f())
        val pose = layer(12.25f, 30.75f)
        assertFalse(CosmeticView.rebased)
        assertSame(pose, CosmeticView.layerPose(pose))
    }

    @Test
    fun `a shadow pass root lands within the pre-collected root once its view is taken off`() {
        val input = FloatArray(ROOT * 3)
        val pose = layer(12.25f, 30.75f)
        rootOf(pose, input, 0)
        val view = shadowView()
        CosmeticView.enterEntity(view)
        rootOf(CosmeticView.layerPose(Matrix4f(view).mul(pose)), input, ROOT)
        CosmeticView.exitEntity()
        rootOf(Matrix4f(view).mul(pose), input, ROOT * 2)
        assertTrue(CosmeticView.layerPose(pose) === pose)
        assertTrue(CosmeticRows.nearRoot(input, 0, ROOT, ROOT))
        assertFalse(CosmeticRows.nearRoot(input, 0, ROOT * 2, ROOT))
    }

    @Test
    fun `a wearer drawn somewhere else in a shadow pass keeps its own root`() {
        val input = FloatArray(ROOT * 2)
        rootOf(layer(12.25f, 30.75f), input, 0)
        val view = shadowView()
        CosmeticView.enterEntity(view)
        rootOf(CosmeticView.layerPose(Matrix4f(view).mul(layer(12.5f, 30.75f))), input, ROOT)
        assertFalse(CosmeticRows.nearRoot(input, 0, ROOT, ROOT))
    }

    @Test
    fun `a slightly turned body doesn't count as the same root`() {
        val input = FloatArray(ROOT * 2)
        rootOf(layer(12.25f, 30.75f), input, 0)
        rootOf(layer(12.25f, 30.75f, yaw = 0.601f), input, ROOT)
        assertFalse(CosmeticRows.nearRoot(input, 0, ROOT, ROOT))
    }

    private fun rootOf(pose: Matrix4f, input: FloatArray, at: Int) {
        val out = FloatArray(ROOT)
        CosmeticOrigin.fromLayer(out, pose)
        System.arraycopy(out, 0, input, at, ROOT)
    }

    private fun layer(x: Float, z: Float, yaw: Float = 0.6f): Matrix4f =
        Matrix4f()
            .translate(x, -1.62f, z)
            .rotateY(yaw)
            .scale(0.9375f)
            .scale(-1f, -1f, 1f)
            .translate(0f, -1.501f, 0f)

    private fun shadowView(): Matrix4f =
        Matrix4f()
            .translate(0f, 0f, -100f)
            .rotateX(Math.toRadians(90.0).toFloat())
            .rotateZ(Math.toRadians(-40.0).toFloat())
            .rotateX(Math.toRadians(-133.7).toFloat())
            .translate(0.37f, -0.12f, 1.69f)

    private companion object {
        const val ROOT = 12
    }
}
