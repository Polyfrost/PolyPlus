package org.polyfrost.polyplus.client.pets

import org.polyfrost.polyplus.client.render.PolyPlayerModel

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart
//?} else {
/*import net.minecraft.client.render.model.ModelPart
import org.polyfrost.polyplus.client.cosmetics.access.ModelPartScaleAccess
*///?}

//? if < 1.21.4 {
/*import org.polyfrost.polyplus.client.cosmetics.access.PlayerModelRootAccess
*///?}

object PetOwnerParts {
    const val ROOT = 0
    const val HEAD = 1
    const val BODY = 2
    const val RIGHT_ARM = 3
    const val LEFT_ARM = 4
    const val RIGHT_LEG = 5
    const val LEFT_LEG = 6
    const val HAT = 7
    const val PART_FLOATS = 9
    const val FLOATS = PART_FLOATS * 8
    //? if >= 1.21.4 {
    const val HAT_ON_HEAD = true
    //?} else {
    /*const val HAT_ON_HEAD = false
    *///?}

    private const val PRUNE_EVERY = 64
    private const val KEEP_FRAMES = 2L

    private class Captured {
        val parts = FloatArray(FLOATS)
        var frame = Long.MIN_VALUE
    }

    private val captured = PetLongMap<Captured>()
    private var frame = 0L
    private var keepFrom = 0L
    private val keep: (Captured) -> Boolean = { it.frame >= keepFrom }

    internal fun nextFrame() {
        frame++
        if (frame % PRUNE_EVERY == 0L) {
            keepFrom = frame - KEEP_FRAMES
            captured.removeStale(keep)
        }
    }

    internal fun read(entityId: Int): FloatArray? {
        val entry = captured[entityId.toLong()] ?: return null
        return if (entry.frame >= frame - 1) entry.parts else null
    }

    internal fun forget() {
        captured.clear()
    }

    @JvmStatic
    fun capture(entityId: Int, model: PolyPlayerModel) {
        val entry = captured[entityId.toLong()] ?: Captured().also { captured.put(entityId.toLong(), it) }
        val parts = entry.parts
        //? if >= 1.21.4 {
        put(parts, ROOT, model.root())
        //?} else {
        /*putRoot(parts, (model as PlayerModelRootAccess).`polyplus$root`())
        *///?}
        put(parts, HEAD, model.head)
        put(parts, BODY, model.body)
        put(parts, RIGHT_ARM, model.rightArm)
        put(parts, LEFT_ARM, model.leftArm)
        put(parts, RIGHT_LEG, model.rightLeg)
        put(parts, LEFT_LEG, model.leftLeg)
        put(parts, HAT, model.hat)
        entry.frame = frame
    }

    internal fun set(
        parts: FloatArray,
        index: Int,
        x: Float,
        y: Float,
        z: Float,
        xRot: Float,
        yRot: Float,
        zRot: Float,
        xScale: Float = 1f,
        yScale: Float = 1f,
        zScale: Float = 1f,
    ) {
        val at = index * PART_FLOATS
        parts[at] = x
        parts[at + 1] = y
        parts[at + 2] = z
        parts[at + 3] = xRot
        parts[at + 4] = yRot
        parts[at + 5] = zRot
        parts[at + 6] = xScale
        parts[at + 7] = yScale
        parts[at + 8] = zScale
    }

    //? if > 1.8.9 {
    private fun put(parts: FloatArray, index: Int, part: ModelPart) =
        set(parts, index, part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale)
    //?} else {
    /*private fun put(parts: FloatArray, index: Int, part: ModelPart) {
        val scale = part as ModelPartScaleAccess
        set(
            parts, index,
            part.translateX * 16f + part.x, part.translateY * 16f + part.y, part.translateZ * 16f + part.z,
            part.rotationX, part.rotationY, part.rotationZ,
            scale.`polyplus$scaleX`(), scale.`polyplus$scaleY`(), scale.`polyplus$scaleZ`(),
        )
    }

    private fun putRoot(parts: FloatArray, part: ModelPart) {
        val scale = part as ModelPartScaleAccess
        set(
            parts, ROOT,
            part.x, part.y, part.z,
            part.rotationX, part.rotationY, part.rotationZ,
            scale.`polyplus$scaleX`(), scale.`polyplus$scaleY`(), scale.`polyplus$scaleZ`(),
        )
    }
    *///?}

    //? if = 1.21.1 {
    /*private fun putRoot(parts: FloatArray, part: ModelPart) = put(parts, ROOT, part)
    *///?}
}
