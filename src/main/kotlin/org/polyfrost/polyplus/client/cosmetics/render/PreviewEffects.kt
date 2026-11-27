package org.polyfrost.polyplus.client.cosmetics.render

import org.joml.Matrix4f
import org.joml.Vector3f
import org.polyfrost.plusrenderer.Renderer

internal object PreviewEffects {
    private const val PIXELS_PER_BLOCK = 16f
    private const val NECK_BLOCKS = 1.5f
    private const val CAMERA_DISTANCE = 1000f
    private const val BODY_BLOCKS = 1f

    private val rootMatrix = Matrix4f()
    private val toLayer = Matrix4f()
    private val toEye = Matrix4f()
    private val fromEye = Matrix4f()
    private val point = Vector3f()

    fun place(wearer: CosmeticWearers.Wearer, eye: Matrix4f, root: FloatArray) {
        rootMatrix.set(
            root[0], root[4], root[8], 0f,
            root[1], root[5], root[9], 0f,
            root[2], root[6], root[10], 0f,
            root[3], root[7], root[11], 1f,
        )
        val k = 1f / PIXELS_PER_BLOCK
        toLayer.translation(0f, NECK_BLOCKS, 0f).scale(k, -k, -k).mul(rootMatrix.invert())
        place(wearer.previewView, wearer.previewModel, eye, toLayer, BODY_BLOCKS)
        wearer.previewPlaced = true
    }

    fun place(view: FloatArray, model: Matrix4f, eye: Matrix4f, layer: Matrix4f, bodyY: Float) {
        eye.mul(layer, toEye)
        toEye.invert(fromEye)
        toEye.transformPosition(0f, bodyY, 0f, point)
        point.z += CAMERA_DISTANCE
        fromEye.transformPosition(point)
        view[Renderer.VIEW_CAMERA] = point.x
        view[Renderer.VIEW_CAMERA + 1] = point.y
        view[Renderer.VIEW_CAMERA + 2] = point.z
        layer.translate(point.x, point.y, point.z, model)
        direction(view, Renderer.VIEW_RIGHT, 1f, 0f)
        direction(view, Renderer.VIEW_UP, 0f, -1f)
        view[Renderer.VIEW_ORIGIN_SHIFT] = 0f
        view[Renderer.VIEW_ORIGIN_SHIFT + 1] = 0f
        view[Renderer.VIEW_ORIGIN_SHIFT + 2] = 0f
    }

    private fun direction(view: FloatArray, at: Int, x: Float, y: Float) {
        fromEye.transformDirection(x, y, 0f, point)
        if (point.lengthSquared() > 0f) point.normalize()
        view[at] = point.x
        view[at + 1] = point.y
        view[at + 2] = point.z
    }
}
