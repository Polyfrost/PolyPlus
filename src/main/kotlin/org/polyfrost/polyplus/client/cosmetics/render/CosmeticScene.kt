package org.polyfrost.polyplus.client.cosmetics.render

import org.joml.Vector3f
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.PolyPlayerModel as PlayerModel
import org.polyfrost.polyplus.client.render.VertexConsumer
import java.io.File
import java.lang.ref.Cleaner

//? if > 1.8.9 {
import net.minecraft.client.model.geom.ModelPart
//?} else {
/*import net.minecraft.client.render.model.ModelPart
import org.polyfrost.polyplus.compat.OverflowAnimationsCompat
*///?}

/**
 * One cosmetic in its own plusrenderer scene, which attaches, animates and meshes it the same way every
 * plusrenderer host does. Each frame follows the vanilla player model's posed parts.
 *
 * Built on any thread, then used from the render thread only. Its native memory goes once it is unreachable,
 * since equipment may still be wearing a scene the asset cache has let go of.
 */
class CosmeticScene(geometry: String, slot: BodySlot, anchor: String?, imageWidth: Int, imageHeight: Int, scale: Float) {
    private val renderer = Renderer()
    private val cleanable = CLEANER.register(this, renderer::close)

    /** Seconds per loop of the cosmetic's animation, or 0 if it has none or it is unknown. */
    var animationSeconds = 0f
        private set

    /** Whether the bundle's animation file loaded. */
    var hasAnimation = false
        private set

    /** Whether the geometry has no cubes to draw. */
    val isEmpty: Boolean

    private val hasSheet: Boolean

    /** Whether its pose or texture changes over time. */
    val isAnimated: Boolean get() = hasAnimation || hasSheet

    init {
        try {
            renderer.loadCosmetic(SLOT, geometry, slot.serializedName, anchor.orEmpty(), imageWidth, imageHeight)
            renderer.setScale(SLOT, scale)
            val firstFrame = uvsAt(0f)
            isEmpty = firstFrame.isEmpty()
            // shortcut: plusrenderer's Java binding doesn't expose the frame count, read it once it does
            hasSheet = !firstFrame.contentEquals(uvsAt(SECOND_SHEET_FRAME_SECONDS))
        } catch (ex: RuntimeException) {
            close()
            throw ex
        }
    }

    /** Plays the bundle's animation file from equip time. Throws [IllegalArgumentException] if it doesn't parse. */
    fun animate(json: String, seconds: Float) {
        renderer.loadAnimation(SLOT, json)
        renderer.play(SLOT, 0f)
        hasAnimation = true
        animationSeconds = seconds
    }

    /** The cosmetic's vertices off the player, [seconds] into its animation, in a render layer's space. */
    fun restPositions(seconds: Float): FloatArray {
        renderer.clearGameParts()
        renderer.pose(seconds)
        renderer.buildMesh(Renderer.JAVA_MODEL_SPACE)
        return renderer.meshPositions()
    }

    /**
     * Meshes the cosmetic on [model]'s posed parts, [seconds] after it was equipped.
     * [backOffset] pushes it back off the body in pixels.
     */
    fun build(model: PlayerModel, seconds: Float, context: PlayerRenderContext, backOffset: Float): CosmeticMesh? {
        feedParts(model, backOffset)
        if (hasAnimation) feedQueries(context)
        renderer.pose(seconds)
        if (renderer.buildMesh(Renderer.JAVA_MODEL_SPACE) == 0) return null
        return CosmeticMesh(renderer.meshPositions(), renderer.meshNormals(), renderer.meshUvs(), renderer.meshGlow())
    }

    /** Frees the scene early. Only for one that was never handed out. */
    fun close() = cleanable.clean()

    private fun uvsAt(seconds: Float): FloatArray {
        renderer.pose(seconds)
        renderer.buildMesh(Renderer.JAVA_MODEL_SPACE)
        return renderer.meshUvs()
    }

    private fun feedQueries(context: PlayerRenderContext) {
        val lifeTime = context.ageInTicks / BedrockConstants.TICKS_PER_SECOND
        renderer.setQuery("life_time", lifeTime)
        renderer.setQuery("delta_time", 1f / BedrockConstants.TICKS_PER_SECOND)
        renderer.setQuery("time_of_day", lifeTime % 24000f)
        renderer.setQuery("is_moving", flag(context.walkAnimationSpeed > 0.01f))
        renderer.setQuery("ground_speed", context.walkAnimationSpeed)
        renderer.setQuery("is_on_ground", flag(context.isOnGround))
        renderer.setQuery("is_in_water", flag(context.isInWater || context.swimAmount > 0.01f))
        renderer.setQuery("is_sneaking", flag(context.isCrouching))
        renderer.setQuery("is_sprinting", flag(context.walkAnimationSpeed > 0.12f))
    }

    private fun flag(value: Boolean): Float = if (value) 1f else 0f

    // rests are the core player's pivots on vanilla's axes, where slim arms sit as high as wide ones,
    // so cosmetics follow vanilla's lower slim arms down
    //? if > 1.8.9 {
    private fun feedParts(model: PlayerModel, backOffset: Float) {
        //? if >= 1.21.4 {
        feed("root", model.root())
        feed("hat", model.hat)
        //?} else {
        /*// the renderer already applies root, and hat copies head's pose instead of hanging from it
        feedIdentity("hat")
        *///?}
        feed("head", model.head)
        // a rest pivot moved forward leaves the posed part pushed back by as much in its own frame
        feed("body", model.body, restZ = -backOffset)
        feed("rightArm", model.rightArm, -ARM_X, ARM_Y)
        feed("leftArm", model.leftArm, ARM_X, ARM_Y)
        feed("rightLeg", model.rightLeg, -LEG_X, LEG_Y)
        feed("leftLeg", model.leftLeg, LEG_X, LEG_Y)
    }

    private fun feed(name: String, part: ModelPart, restX: Float = 0f, restY: Float = 0f, restZ: Float = 0f) {
        renderer.setGamePart(
            name, restX, restY, restZ,
            part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale,
        )
    }
    //?} else {
    /*private fun feedParts(model: PlayerModel, backOffset: Float) {
        val sneakOffset = if (model.sneaking && !OverflowAnimationsCompat.dropsSneakOffset()) SNEAK_OFFSET else 0f
        renderer.setGamePart("root", 0f, 0f, 0f, 0f, sneakOffset, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        // hat copies head's pose instead of hanging from it
        feedIdentity("hat")
        feed("head", model.head)
        // a rest pivot moved forward leaves the posed part pushed back by as much in its own frame
        feed("body", model.body, restZ = -backOffset)
        feed("rightArm", model.rightArm, -ARM_X, ARM_Y)
        feed("leftArm", model.leftArm, ARM_X, ARM_Y)
        feed("rightLeg", model.rightLeg, -LEG_X, LEG_Y)
        feed("leftLeg", model.leftLeg, LEG_X, LEG_Y)
    }

    private fun feed(name: String, part: ModelPart, restX: Float = 0f, restY: Float = 0f, restZ: Float = 0f) {
        renderer.setGamePart(
            name, restX, restY, restZ,
            part.translateX * 16f + part.x, part.translateY * 16f + part.y, part.translateZ * 16f + part.z,
            part.rotationX, part.rotationY, part.rotationZ, 1f, 1f, 1f,
        )
    }
    *///?}

    //? if <= 1.21.1 {
    /*private fun feedIdentity(name: String) {
        renderer.setGamePart(name, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
    }
    *///?}

    private companion object {
        const val SLOT = 0
        // plusrenderer steps a sheet every 0.2 s, so this is mid second frame
        const val SECOND_SHEET_FRAME_SECONDS = 0.3f
        const val ARM_X = 5f
        const val ARM_Y = 2f
        const val LEG_X = 1.9f
        const val LEG_Y = 12f
        val CLEANER: Cleaner = Cleaner.create()
        //? if = 1.8.9
        //const val SNEAK_OFFSET = 3.2f

        init {
            // antivirus heuristics distrust native libraries loaded from the temp dir
            if (System.getProperty("plusrenderer.natives.dir") == null) {
                System.setProperty("plusrenderer.natives.dir", File("${PolyPlusConstants.NAME}/natives").absolutePath)
            }
        }
    }
}

/** One frame of a cosmetic's quads, four vertices each, in a render layer's space. */
class CosmeticMesh internal constructor(
    private val positions: FloatArray,
    private val normals: FloatArray,
    private val uvs: FloatArray,
    private val glow: ByteArray,
) {
    fun emit(pose: Pose, buffer: VertexConsumer, lightCoords: Int, overlayCoords: Int, color: Int) {
        val matrix = pose.pose()
        val scratch = Vector3f()
        for (i in glow.indices) {
            val position = matrix.transformPosition(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2], scratch)
            val x = position.x
            val y = position.y
            val z = position.z
            val normal = pose.transformNormal(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2], scratch)
            val light = emissive(lightCoords, glow[i].toInt())
            buffer.addVertex(x, y, z, color, uvs[i * 2], uvs[i * 2 + 1], overlayCoords, light, normal.x, normal.y, normal.z)
        }
    }

    private fun emissive(base: Int, level: Int): Int {
        if (level == 0) return base
        val block = maxOf((base shr 4) and 0xF, level)
        val sky = maxOf((base shr 20) and 0xF, level)
        return (block shl 4) or (sky shl 20)
    }
}
