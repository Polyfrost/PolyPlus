package org.polyfrost.polyplus.client.cosmetics.render

import org.joml.Matrix4f
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
    private val partIds: IntArray
    private val queryIds: IntArray
    private val input = FloatArray(PARTS.size * 12 + QUERIES.size)
    private val skin: CosmeticSkin
    private val frame: FloatArray

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
            partIds = IntArray(PARTS.size) { renderer.partId(PARTS[it]) }
            queryIds = IntArray(QUERIES.size) { renderer.queryId(QUERIES[it]) }
            isEmpty = renderer.buildSkin() == 0
            skin = CosmeticSkin(
                renderer.skinPositions(),
                renderer.skinNormals(),
                renderer.skinUvs(),
                renderer.skinGlow(),
                renderer.skinBones(),
                renderer.skinRuns(),
                renderer.skinBoneCount(),
            )
            frame = FloatArray(skin.boneCount * 12 + skin.runs.size / 4)
            hasSheet = !isEmpty && sheetOffsetAt(SECOND_SHEET_FRAME_SECONDS) != 0f
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
        if (isEmpty) return null
        val parts = feedParts(model, backOffset)
        val queries = if (hasAnimation) {
            feedQueries(context, parts * 12)
            QUERIES.size
        } else {
            0
        }
        renderer.frame(seconds, input, parts, queries, Renderer.JAVA_MODEL_SPACE, frame)
        return CosmeticMesh(skin, frame.copyOf())
    }

    /** Frees the scene early. Only for one that was never handed out. */
    fun close() = cleanable.clean()

    private fun sheetOffsetAt(seconds: Float): Float {
        renderer.frame(seconds, input, 0, 0, Renderer.JAVA_MODEL_SPACE, frame)
        return frame[skin.boneCount * 12]
    }

    private fun feedQueries(context: PlayerRenderContext, base: Int) {
        val lifeTime = context.ageInTicks / BedrockConstants.TICKS_PER_SECOND
        query(base, LIFE_TIME, lifeTime)
        query(base, DELTA_TIME, 1f / BedrockConstants.TICKS_PER_SECOND)
        query(base, TIME_OF_DAY, lifeTime % 24000f)
        query(base, IS_MOVING, flag(context.walkAnimationSpeed > 0.01f))
        query(base, GROUND_SPEED, context.walkAnimationSpeed)
        query(base, IS_ON_GROUND, flag(context.isOnGround))
        query(base, IS_IN_WATER, flag(context.isInWater || context.swimAmount > 0.01f))
        query(base, IS_SNEAKING, flag(context.isCrouching))
        query(base, IS_SPRINTING, flag(context.walkAnimationSpeed > 0.12f))
    }

    private fun query(base: Int, query: Int, value: Float) {
        input[base + queryIds[query]] = value
    }

    private fun flag(value: Boolean): Float = if (value) 1f else 0f

    private fun put(
        part: Int,
        restX: Float, restY: Float, restZ: Float,
        x: Float, y: Float, z: Float,
        xRot: Float, yRot: Float, zRot: Float,
        xScale: Float, yScale: Float, zScale: Float,
    ) {
        val i = partIds[part] * 12
        input[i] = restX
        input[i + 1] = restY
        input[i + 2] = restZ
        input[i + 3] = x
        input[i + 4] = y
        input[i + 5] = z
        input[i + 6] = xRot
        input[i + 7] = yRot
        input[i + 8] = zRot
        input[i + 9] = xScale
        input[i + 10] = yScale
        input[i + 11] = zScale
    }

    // rests are the core player's pivots on vanilla's axes, where slim arms sit as high as wide ones,
    // so cosmetics follow vanilla's lower slim arms down
    //? if > 1.8.9 {
    private fun feedParts(model: PlayerModel, backOffset: Float): Int {
        //? if >= 1.21.4 {
        feed(ROOT, model.root())
        feed(HAT, model.hat)
        //?} else {
        /*// the renderer already applies root, and hat copies head's pose instead of hanging from it
        feedIdentity(HAT)
        *///?}
        feed(HEAD, model.head)
        // a rest pivot moved forward leaves the posed part pushed back by as much in its own frame
        feed(BODY, model.body, restZ = -backOffset)
        feed(RIGHT_ARM, model.rightArm, -ARM_X, ARM_Y)
        feed(LEFT_ARM, model.leftArm, ARM_X, ARM_Y)
        feed(RIGHT_LEG, model.rightLeg, -LEG_X, LEG_Y)
        feed(LEFT_LEG, model.leftLeg, LEG_X, LEG_Y)
        //? if >= 1.21.4 {
        return PARTS.size
        //?} else {
        /*return ROOT
        *///?}
    }

    private fun feed(index: Int, part: ModelPart, restX: Float = 0f, restY: Float = 0f, restZ: Float = 0f) {
        put(
            index, restX, restY, restZ,
            part.x, part.y, part.z, part.xRot, part.yRot, part.zRot, part.xScale, part.yScale, part.zScale,
        )
    }
    //?} else {
    /*private fun feedParts(model: PlayerModel, backOffset: Float): Int {
        val sneakOffset = if (model.sneaking && !OverflowAnimationsCompat.dropsSneakOffset()) SNEAK_OFFSET else 0f
        put(ROOT, 0f, 0f, 0f, 0f, sneakOffset, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
        // hat copies head's pose instead of hanging from it
        feedIdentity(HAT)
        feed(HEAD, model.head)
        // a rest pivot moved forward leaves the posed part pushed back by as much in its own frame
        feed(BODY, model.body, restZ = -backOffset)
        feed(RIGHT_ARM, model.rightArm, -ARM_X, ARM_Y)
        feed(LEFT_ARM, model.leftArm, ARM_X, ARM_Y)
        feed(RIGHT_LEG, model.rightLeg, -LEG_X, LEG_Y)
        feed(LEFT_LEG, model.leftLeg, LEG_X, LEG_Y)
        return PARTS.size
    }

    private fun feed(index: Int, part: ModelPart, restX: Float = 0f, restY: Float = 0f, restZ: Float = 0f) {
        put(
            index, restX, restY, restZ,
            part.translateX * 16f + part.x, part.translateY * 16f + part.y, part.translateZ * 16f + part.z,
            part.rotationX, part.rotationY, part.rotationZ, 1f, 1f, 1f,
        )
    }
    *///?}

    //? if <= 1.21.1 {
    /*private fun feedIdentity(index: Int) {
        put(index, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f)
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

        val PARTS = arrayOf("hat", "head", "body", "rightArm", "leftArm", "rightLeg", "leftLeg", "root")
        const val HAT = 0
        const val HEAD = 1
        const val BODY = 2
        const val RIGHT_ARM = 3
        const val LEFT_ARM = 4
        const val RIGHT_LEG = 5
        const val LEFT_LEG = 6
        const val ROOT = 7

        val QUERIES = arrayOf(
            "life_time", "delta_time", "time_of_day", "is_moving", "ground_speed",
            "is_on_ground", "is_in_water", "is_sneaking", "is_sprinting",
        )
        const val LIFE_TIME = 0
        const val DELTA_TIME = 1
        const val TIME_OF_DAY = 2
        const val IS_MOVING = 3
        const val GROUND_SPEED = 4
        const val IS_ON_GROUND = 5
        const val IS_IN_WATER = 6
        const val IS_SNEAKING = 7
        const val IS_SPRINTING = 8

        init {
            // antivirus heuristics distrust native libraries loaded from the temp dir
            if (System.getProperty("plusrenderer.natives.dir") == null) {
                System.setProperty("plusrenderer.natives.dir", File("${PolyPlusConstants.NAME}/natives").absolutePath)
            }
        }
    }
}

internal class CosmeticSkin(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val glow: ByteArray,
    val bones: IntArray,
    val runs: IntArray,
    val boneCount: Int,
)

/** One frame of a cosmetic's quads, four vertices each, in a render layer's space. */
class CosmeticMesh internal constructor(
    private val skin: CosmeticSkin,
    private val frame: FloatArray,
) {
    fun emit(pose: Pose, buffer: VertexConsumer, lightCoords: Int, overlayCoords: Int, color: Int) {
        val matrix = Matrix4f()
        val bone = Matrix4f()
        val position = Vector3f()
        val normal = Vector3f()
        val offsets = skin.boneCount * 12
        var current = -1
        var b = 0
        for (run in 0 until skin.runs.size / 4) {
            val vOffset = frame[offsets + run]
            val start = skin.runs[run * 4 + 1] * 4
            val end = start + skin.runs[run * 4 + 2] * 4
            for (i in start until end) {
                if (skin.bones[i] != current) {
                    current = skin.bones[i]
                    b = current * 12
                    bone.set(
                        frame[b], frame[b + 4], frame[b + 8], 0f,
                        frame[b + 1], frame[b + 5], frame[b + 9], 0f,
                        frame[b + 2], frame[b + 6], frame[b + 10], 0f,
                        frame[b + 3], frame[b + 7], frame[b + 11], 1f,
                    )
                    matrix.set(pose.pose()).mul(bone)
                }
                val px = skin.positions[i * 3]
                val py = skin.positions[i * 3 + 1]
                val pz = skin.positions[i * 3 + 2]
                matrix.transformPosition(px, py, pz, position)
                val nx = skin.normals[i * 3]
                val ny = skin.normals[i * 3 + 1]
                val nz = skin.normals[i * 3 + 2]
                pose.transformNormal(
                    frame[b] * nx + frame[b + 1] * ny + frame[b + 2] * nz,
                    frame[b + 4] * nx + frame[b + 5] * ny + frame[b + 6] * nz,
                    frame[b + 8] * nx + frame[b + 9] * ny + frame[b + 10] * nz,
                    normal,
                )
                val light = emissive(lightCoords, skin.glow[i].toInt())
                buffer.addVertex(
                    position.x, position.y, position.z, color,
                    skin.uvs[i * 2], skin.uvs[i * 2 + 1] + vOffset,
                    overlayCoords, light, normal.x, normal.y, normal.z,
                )
            }
        }
    }

    private fun emissive(base: Int, level: Int): Int {
        if (level == 0) return base
        val block = maxOf((base shr 4) and 0xF, level)
        val sky = maxOf((base shr 20) and 0xF, level)
        return (block shl 4) or (sky shl 20)
    }
}
