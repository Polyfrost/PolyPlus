package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object CosmeticOrigin {
    private const val REBASE_BLOCKS = 4096.0
    private const val SNAP_BLOCKS = 16.0
    private const val PIXELS_PER_BLOCK = 16f
    private const val NECK_BLOCKS = 1.5f

    var x = 0.0
        private set
    var y = 0.0
        private set
    var z = 0.0
        private set
    var cameraX = 0.0
        private set
    var cameraY = 0.0
        private set
    var cameraZ = 0.0
        private set
    var epoch = 0
        private set

    private var yaw = 0f
    private var pitch = 0f
    private var placed = false

    private val MODEL_TO_LAYER = Matrix4f().translation(0f, NECK_BLOCKS, 0f)
        .scale(1f / PIXELS_PER_BLOCK, -1f / PIXELS_PER_BLOCK, -1f / PIXELS_PER_BLOCK)
    private val LAYER_TO_MODEL = Matrix4f(MODEL_TO_LAYER).invert()
    private val LAYER_PART = Matrix4f()
    private val ROOT = Matrix4f()

    fun reset() {
        placed = false
    }

    fun update() {
        readCamera()
        val far = abs(cameraX - x) > REBASE_BLOCKS || abs(cameraY - y) > REBASE_BLOCKS || abs(cameraZ - z) > REBASE_BLOCKS
        if (placed && !far) return
        x = floor(cameraX / SNAP_BLOCKS) * SNAP_BLOCKS
        y = floor(cameraY / SNAP_BLOCKS) * SNAP_BLOCKS
        z = floor(cameraZ / SNAP_BLOCKS) * SNAP_BLOCKS
        placed = true
        epoch++
    }

    fun fromLayer(out: FloatArray, pose: Matrix4f) {
        val dx = (cameraX - x).toFloat()
        val dy = (cameraY - y).toFloat()
        val dz = (cameraZ - z).toFloat()
        val k = 1f / PIXELS_PER_BLOCK
        for (row in 0 until 3) {
            out[row * 4] = pose.get(0, row) * k
            out[row * 4 + 1] = -pose.get(1, row) * k
            out[row * 4 + 2] = -pose.get(2, row) * k
        }
        out[3] = pose.get(1, 0) * NECK_BLOCKS + pose.get(3, 0) + dx
        out[7] = pose.get(1, 1) * NECK_BLOCKS + pose.get(3, 1) + dy
        out[11] = pose.get(1, 2) * NECK_BLOCKS + pose.get(3, 2) + dz
    }

    fun fromWorld(out: FloatArray, worldX: Double, worldY: Double, worldZ: Double, yawDegrees: Float, scale: Float) {
        val k = scale / PIXELS_PER_BLOCK
        val radians = Math.toRadians(yawDegrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        out[0] = c * k
        out[1] = 0f
        out[2] = -s * k
        out[3] = (worldX - x).toFloat()
        out[4] = 0f
        out[5] = k
        out[6] = 0f
        out[7] = (worldY - y).toFloat()
        out[8] = s * k
        out[9] = 0f
        out[10] = c * k
        out[11] = (worldZ - z).toFloat()
    }

    fun applyLayerPart(
        out: FloatArray,
        x: Float,
        y: Float,
        z: Float,
        xRot: Float,
        yRot: Float,
        zRot: Float,
        xScale: Float,
        yScale: Float,
        zScale: Float,
    ) {
        if (x == 0f && y == 0f && z == 0f && xRot == 0f && yRot == 0f && zRot == 0f && xScale == 1f && yScale == 1f && zScale == 1f) return
        LAYER_PART.translation(x / PIXELS_PER_BLOCK, y / PIXELS_PER_BLOCK, z / PIXELS_PER_BLOCK)
            .rotateZ(zRot).rotateY(yRot).rotateX(xRot).scale(xScale, yScale, zScale)
        ROOT.set(
            out[0], out[4], out[8], 0f,
            out[1], out[5], out[9], 0f,
            out[2], out[6], out[10], 0f,
            out[3], out[7], out[11], 1f,
        )
        ROOT.mul(LAYER_TO_MODEL).mul(LAYER_PART).mul(MODEL_TO_LAYER)
        for (row in 0 until 3) {
            for (column in 0 until 4) out[row * 4 + column] = ROOT.get(column, row)
        }
    }

    fun still(out: FloatArray) {
        out.fill(0f)
        out[0] = 1f / PIXELS_PER_BLOCK
        out[5] = 1f / PIXELS_PER_BLOCK
        out[10] = 1f / PIXELS_PER_BLOCK
    }

    fun view(out: FloatArray, shiftX: Float, shiftY: Float, shiftZ: Float) {
        val yawRadians = Math.toRadians(yaw.toDouble())
        val pitchRadians = Math.toRadians(pitch.toDouble())
        val sy = sin(yawRadians).toFloat()
        val cy = cos(yawRadians).toFloat()
        val sp = sin(pitchRadians).toFloat()
        val cp = cos(pitchRadians).toFloat()
        out[0] = (cameraX - x).toFloat()
        out[1] = (cameraY - y).toFloat()
        out[2] = (cameraZ - z).toFloat()
        out[3] = -cy
        out[4] = 0f
        out[5] = -sy
        out[6] = -sy * sp
        out[7] = cp
        out[8] = cy * sp
        out[9] = shiftX
        out[10] = shiftY
        out[11] = shiftZ
    }

    private fun readCamera() {
        //? if >= 26.2 {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera()
        val position = camera.position()
        yaw = camera.yRot()
        pitch = camera.xRot()
        //?} elif >= 1.21.11 {
        /*val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val position = camera.position()
        yaw = camera.yRot()
        pitch = camera.xRot()
        *///?} elif > 1.8.9 {
        /*val camera = Minecraft.getInstance().gameRenderer.mainCamera
        val position = camera.position
        yaw = camera.yRot
        pitch = camera.xRot
        *///?}
        //? if > 1.8.9 {
        cameraX = position.x
        cameraY = position.y
        cameraZ = position.z
        //?} else {
        /*val dispatcher = Minecraft.getInstance().entityRenderDispatcher
        cameraX = dispatcher.cameraX
        cameraY = dispatcher.cameraY
        cameraZ = dispatcher.cameraZ
        yaw = dispatcher.cameraYaw
        pitch = dispatcher.cameraPitch
        *///?}
    }
}
