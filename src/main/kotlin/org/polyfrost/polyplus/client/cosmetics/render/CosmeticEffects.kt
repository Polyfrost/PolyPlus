package org.polyfrost.polyplus.client.cosmetics.render

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.Identifier
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.plusrenderer.Renderer
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.moments.CosmeticMoments
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.PoseStack
import org.polyfrost.polyplus.client.render.VertexConsumer
import kotlin.math.floor

//? if > 1.8.9 {
import net.minecraft.client.renderer.texture.OverlayTexture
import org.polyfrost.polyplus.client.bedrock.render.BedrockAttachedModelRenderer
//?} else {
/*import net.minecraft.client.render.platform.GlStateManager
*///?}

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
*///?}

//? if >= 26.2 {
import net.minecraft.util.LightCoordsUtil
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.LevelRenderer
*///?}

//? if >= 1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.RenderType
*///?}

object CosmeticEffects {
    private const val FULL_BRIGHT = 0xF000F0
    private const val CHANNEL = 255f
    private const val MODEL_SCALE = 0.9375f
    private const val MODEL_NECK = 1.501f
    private const val MODEL_LIGHT_HEIGHT = 0.5

    private val pending = ArrayList<EffectSource>()
    private var frame = -1
    private val identity = PoseStack()

    init {
        eventHandler<FramebufferRenderEvent.End> { release() }
    }

    private fun release() {
        pending.clear()
        //? if >= 1.21.10 {
        for (batch in batches) batch.scene = null
        for (batch in models) batch.moment = null
        //?}
    }

    internal fun roll(frame: Int) {
        this.frame = frame
        pending.clear()
    }

    internal fun pending(scene: EffectSource) {
        if (pending.none { it === scene }) pending += scene
    }

    private fun prepare() {
        CosmeticWearers.begin()
        FirstPersonEffects.add()
        CosmeticMoments.addRows()
    }

    private fun place(pose: Pose, moment: CosmeticMoments.Detached) {
        val yaw = Math.toRadians(180.0 - moment.yaw).toFloat()
        MODEL.translation(
            (moment.x - CosmeticOrigin.cameraX).toFloat(),
            (moment.y - CosmeticOrigin.cameraY).toFloat(),
            (moment.z - CosmeticOrigin.cameraZ).toFloat(),
        ).rotateY(yaw).scale(-MODEL_SCALE, -MODEL_SCALE, MODEL_SCALE).translate(0f, -MODEL_NECK, 0f)
        MODEL_NORMAL.rotationY(yaw).scale(-1f, -1f, 1f)
        pose.pose().mul(MODEL)
        pose.normal().mul(MODEL_NORMAL)
    }

    private fun modelLight(moment: CosmeticMoments.Detached): Int = light(
        (moment.x - CosmeticOrigin.cameraX).toFloat(),
        (moment.y + MODEL_LIGHT_HEIGHT - CosmeticOrigin.cameraY).toFloat(),
        (moment.z - CosmeticOrigin.cameraZ).toFloat(),
    )

    private inline fun forEachBatch(draw: (EffectSource, Int, Boolean) -> Unit) {
        for (index in 0 until pending.size) {
            val scene = pending[index]
            if (!scene.computeFor(frame) || scene.quadCount == 0) continue
            val textures = scene.effectTextures
            scan(scene, textures.size)
            for (texture in textures.indices) {
                if (present[texture * 2]) draw(scene, texture, false)
                if (present[texture * 2 + 1]) draw(scene, texture, true)
            }
        }
    }

    private fun scan(scene: EffectSource, textures: Int) {
        val wanted = textures * 2
        if (present.size < wanted) present = BooleanArray(wanted * 2)
        present.fill(false, 0, wanted)
        val quads = scene.quads
        for (q in 0 until scene.quadCount) {
            val at = q * Renderer.QUAD_FLOATS
            val texture = quads[at + Renderer.QUAD_TEXTURE].toInt()
            if (texture < 0 || texture >= textures) continue
            present[texture * 2 + (if (quads[at + Renderer.QUAD_BLEND] >= 0.5f) 1 else 0)] = true
        }
    }

    private fun matches(quads: FloatArray, at: Int, texture: Int, additive: Boolean): Boolean =
        quads[at + Renderer.QUAD_TEXTURE].toInt() == texture &&
            (quads[at + Renderer.QUAD_BLEND] >= 0.5f) == additive

    private fun emit(scene: EffectSource, texture: Int, additive: Boolean, matrix: Matrix4f, buffer: VertexConsumer, lit: Boolean = false) {
        val quads = scene.quads
        lightCached = false
        for (q in 0 until scene.quadCount) {
            val at = q * Renderer.QUAD_FLOATS
            if (!matches(quads, at, texture, additive)) continue
            val color = color(quads, at + Renderer.QUAD_COLOR)
            val corner = at + Renderer.QUAD_CORNERS
            val light = if (additive || lit || emissive(quads, at)) FULL_BRIGHT else light(quads[corner], quads[corner + 1], quads[corner + 2])
            for (c in 0 until 4) {
                val v = corner + c * Renderer.CORNER_FLOATS
                matrix.transformPosition(quads[v], quads[v + 1], quads[v + 2], POSITION)
                buffer.addVertex(
                    POSITION.x, POSITION.y, POSITION.z, color,
                    quads[v + 3], quads[v + 4],
                    NO_OVERLAY, light, 0f, 1f, 0f,
                )
            }
        }
    }

    private fun emissive(quads: FloatArray, at: Int): Boolean =
        (quads[at + Renderer.QUAD_FLAGS].toInt() and Renderer.QUAD_FLAG_EMISSIVE) != 0

    private fun color(quads: FloatArray, at: Int): Int {
        val r = channel(quads[at])
        val g = channel(quads[at + 1])
        val b = channel(quads[at + 2])
        val a = channel(quads[at + 3])
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun channel(value: Float): Int = (value.coerceIn(0f, 1f) * CHANNEL + 0.5f).toInt()

    private fun light(x: Float, y: Float, z: Float): Int {
        val worldX = floor(CosmeticOrigin.cameraX + x).toInt()
        val worldY = floor(CosmeticOrigin.cameraY + y).toInt()
        val worldZ = floor(CosmeticOrigin.cameraZ + z).toInt()
        if (lightCached && worldX == lightX && worldY == lightY && worldZ == lightZ) return lightValue
        val value = lookupLight(worldX, worldY, worldZ)
        lightX = worldX
        lightY = worldY
        lightZ = worldZ
        lightValue = value
        lightCached = true
        return value
    }

    private fun lookupLight(worldX: Int, worldY: Int, worldZ: Int): Int {
        //? if > 1.8.9 {
        val level = Minecraft.getInstance().level ?: return FULL_BRIGHT
        val pos = LIGHT_POS.set(worldX, worldY, worldZ)
        //? if >= 26.2 {
        return LightCoordsUtil.getLightCoords(level, pos)
        //?} elif >= 26.1 {
        /*return LevelRenderer.getLightCoords(level, pos)
        *///?} else {
        /*return LevelRenderer.getLightColor(level, pos)
        *///?}
        //?} else {
        /*val world = Minecraft.getInstance().world ?: return FULL_BRIGHT
        LIGHT_POS.set(worldX, worldY, worldZ)
        return world.getLightColor(LIGHT_POS, 0)
        *///?}
    }

    private inline fun forEachPreviewBatch(source: EffectSource, draw: (Int, Boolean) -> Unit) {
        if (!source.computeFor(CosmeticWearers.frame) || source.quadCount == 0) return
        val textures = source.effectTextures
        scan(source, textures.size)
        for (texture in textures.indices) {
            if (present[texture * 2]) draw(texture, false)
            if (present[texture * 2 + 1]) draw(texture, true)
        }
    }

    //? if > 1.8.9 {
    private fun renderType(texture: Identifier, additive: Boolean): RenderType =
        if (additive) AdditiveEffectTypes.get(texture) else BedrockAttachedModelRenderer.renderType(texture, true)
    //?}

    //? if >= 1.21.10 {
    private val batches = ArrayList<EffectBatch>()
    private val models = ArrayList<ModelBatch>()

    private class EffectBatch : PooledGeometry {
        var scene: EffectSource? = null
        var texture = 0
        var additive = false

        override fun render(pose: Pose, buffer: VertexConsumer) {
            scene?.let { emit(it, texture, additive, pose.pose(), buffer) }
        }
    }

    private class PreviewBatch(
        val source: EffectSource,
        val frame: Int,
        val texture: Int,
        val additive: Boolean,
        val model: Matrix4f,
    ) : PooledGeometry {
        override fun render(pose: Pose, buffer: VertexConsumer) {
            if (!source.computeFor(frame)) return
            emit(source, texture, additive, Matrix4f(pose.pose()).mul(model), buffer, lit = true)
        }
    }

    internal fun submitPreview(source: EffectSource, model: Matrix4f, poseStack: PoseStack, collector: SubmitNodeCollector) {
        val frame = CosmeticWearers.frame
        val placed = Matrix4f(model)
        val textures = source.effectTextures
        for (texture in textures.indices) {
            for (additive in BLENDS) {
                collector.submitCustomGeometry(poseStack, renderType(textures[texture], additive), PreviewBatch(source, frame, texture, additive, placed))
            }
        }
    }

    private class ModelBatch : PooledGeometry {
        var moment: CosmeticMoments.Detached? = null
        var light = 0

        override fun render(pose: Pose, buffer: VertexConsumer) {
            val moment = moment ?: return
            moment.scene.emit(moment.frame, moment.row, pose, buffer, light, NO_OVERLAY, -1)
        }
    }

    @JvmStatic
    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector) {
        prepare()
        var used = 0
        forEachBatch { scene, texture, additive ->
            if (used == batches.size) batches += EffectBatch()
            val batch = batches[used++]
            batch.scene = scene
            batch.texture = texture
            batch.additive = additive
            collector.submitCustomGeometry(poseStack, renderType(scene.effectTextures[texture], additive), batch)
        }
        var drawn = 0
        CosmeticMoments.forEachModel { moment, texture ->
            if (drawn == models.size) models += ModelBatch()
            val batch = models[drawn++]
            batch.moment = moment
            batch.light = modelLight(moment)
            poseStack.pushPose()
            place(poseStack.last(), moment)
            collector.submitCustomGeometry(poseStack, BedrockAttachedModelRenderer.renderType(texture, RemoteTextures.isTranslucent(texture)), batch)
            poseStack.popPose()
        }
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun render(poseStack: PoseStack, buffers: MultiBufferSource) {
        prepare()
        val pose = poseStack.last()
        forEachBatch { scene, texture, additive ->
            emit(scene, texture, additive, pose.pose(), buffers.getBuffer(renderType(scene.effectTextures[texture], additive)))
        }
        CosmeticMoments.forEachModel { moment, texture ->
            poseStack.pushPose()
            place(poseStack.last(), moment)
            val buffer = buffers.getBuffer(BedrockAttachedModelRenderer.renderType(texture, RemoteTextures.isTranslucent(texture)))
            moment.scene.emit(moment.frame, moment.row, poseStack.last(), buffer, modelLight(moment), NO_OVERLAY, -1)
            poseStack.popPose()
        }
    }

    @JvmStatic
    fun render() {
        render(identity, Minecraft.getInstance().renderBuffers().bufferSource())
    }

    internal fun drawPreview(source: EffectSource, model: Matrix4f, poseStack: PoseStack, buffers: MultiBufferSource) {
        val matrix = Matrix4f(poseStack.last().pose()).mul(model)
        forEachPreviewBatch(source) { texture, additive ->
            emit(source, texture, additive, matrix, buffers.getBuffer(renderType(source.effectTextures[texture], additive)), lit = true)
        }
    }

    internal fun drawNow(source: EffectSource, poseStack: PoseStack, buffers: MultiBufferSource) {
        val matrix = poseStack.last().pose()
        forEachPreviewBatch(source) { texture, additive ->
            emit(source, texture, additive, matrix, buffers.getBuffer(renderType(source.effectTextures[texture], additive)))
        }
    }
    *///?} else {
    /*@JvmStatic
    fun render() {
        prepare()
        if (pending.isEmpty()) return
        val mc = Minecraft.getInstance()
        val pose = identity.last()
        mc.gameRenderer.enableLightMap()
        CosmeticMoments.forEachModel { moment, texture ->
            identity.pushPose()
            place(identity.last(), moment)
            val placed = identity.last()
            VertexConsumer.draw(texture, RemoteTextures.isTranslucent(texture)) { buffer ->
                moment.scene.emit(moment.frame, moment.row, placed, buffer, modelLight(moment), NO_OVERLAY, -1)
            }
            identity.popPose()
        }
        GlStateManager.disableCull()
        GlStateManager.depthMask(false)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        forEachBatch { scene, texture, additive ->
            VertexConsumer.draw(scene.effectTextures[texture], true) { buffer ->
                if (additive) GlStateManager.blendFunc(770, 1)
                emit(scene, texture, additive, pose.pose(), buffer)
            }
        }
        GlStateManager.blendFunc(770, 771)
        GlStateManager.depthMask(true)
        GlStateManager.enableCull()
        mc.gameRenderer.disableLightMap()
    }

    internal fun drawNow(source: EffectSource) {
        val mc = Minecraft.getInstance()
        val pose = identity.last()
        mc.gameRenderer.enableLightMap()
        GlStateManager.disableCull()
        GlStateManager.depthMask(false)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        forEachPreviewBatch(source) { texture, additive ->
            VertexConsumer.draw(source.effectTextures[texture], true) { buffer ->
                if (additive) GlStateManager.blendFunc(770, 1)
                emit(source, texture, additive, pose.pose(), buffer)
            }
        }
        GlStateManager.blendFunc(770, 771)
        GlStateManager.depthMask(true)
        GlStateManager.enableCull()
        mc.gameRenderer.disableLightMap()
    }

    internal fun drawPreview(source: EffectSource, model: Matrix4f) {
        GlStateManager.disableCull()
        GlStateManager.depthMask(false)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        forEachPreviewBatch(source) { texture, additive ->
            VertexConsumer.draw(source.effectTextures[texture], true) { buffer ->
                if (additive) GlStateManager.blendFunc(770, 1)
                emit(source, texture, additive, model, buffer, lit = true)
            }
        }
        GlStateManager.blendFunc(770, 771)
        GlStateManager.depthMask(true)
        GlStateManager.enableCull()
    }
    *///?}

    //? if > 1.8.9 {
    private val NO_OVERLAY = OverlayTexture.NO_OVERLAY
    //?} else {
    /*private const val NO_OVERLAY = 0
    *///?}

    private val BLENDS = booleanArrayOf(false, true)
    private var present = BooleanArray(16)
    private var lightCached = false
    private var lightX = 0
    private var lightY = 0
    private var lightZ = 0
    private var lightValue = 0
    private val POSITION = Vector3f()
    private val LIGHT_POS = BlockPos.MutableBlockPos()
    private val MODEL = Matrix4f()
    private val MODEL_NORMAL = Matrix3f()
}
