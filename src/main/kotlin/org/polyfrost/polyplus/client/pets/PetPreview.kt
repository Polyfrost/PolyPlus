package org.polyfrost.polyplus.client.pets

import net.minecraft.resources.Identifier
import org.joml.Matrix4f
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticEffects
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignals
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.render.PoseStack
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.WeakHashMap
import kotlin.math.min

//? if >= 1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.texture.OverlayTexture
import org.polyfrost.polyplus.client.render.Pose
import org.polyfrost.polyplus.client.render.VertexConsumer
//?} elif > 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import net.minecraft.world.entity.HumanoidArm
*///?} elif = 1.8.9 {
/*import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.render.VertexConsumer
*///?}

object PetPreview {
    private const val SIDE_OFFSET = -0.65f
    private const val FULL_BRIGHT = 0xF000F0

    private val root = FloatArray(PetScene.ROOT_FLOATS)
    private val owner = PetOwner()
    private val eye = Matrix4f()

    private class Painted {
        var colors: Map<String, Int>? = null
        var generation = -1
        var colorway = Int.MIN_VALUE
        var paint: PetPaint? = null
    }

    private val painted = WeakHashMap<PetAsset, Painted>()

    private fun colors(asset: PetAsset): Map<String, Int>? =
        CosmeticColors.get(ClientPlatform.localPlayerUuid(), asset.id, preview = true)

    private fun texture(asset: PetAsset, preview: PetScene.Preview, colors: Map<String, Int>?): Identifier? {
        val reaction = preview.colorway()
        val colorway = if (reaction >= 0) reaction else asset.colorway
        val last = painted.getOrPut(asset) { Painted() }
        val now = System.nanoTime()
        val held = last.paint
        if (held == null || held.expired(now) || last.colors !== colors || last.generation != asset.paintGeneration ||
            last.colorway != colorway
        ) {
            last.colors = colors
            last.generation = asset.paintGeneration
            last.colorway = colorway
            last.paint = asset.paint(colorway, colors, now)
        }
        val paint = last.paint ?: return null
        paint.drawnFrame = PetFrames.frame
        return paint.texture
    }

    private fun seat(asset: PetAsset, yawDegrees: Float, leftHanded: Boolean, limbSwing: Float, limbAmount: Float): Boolean {
        if (asset.archetype != PetArchetype.Shoulder) return false
        owner.renderBodyYaw = yawDegrees
        owner.renderHeadYaw = yawDegrees
        owner.renderPitch = 0f
        owner.limbSwing = limbSwing
        owner.limbAmount = min(limbAmount, 1f)
        owner.leftHanded = leftHanded
        owner.signals = PreviewTryOn.previewSignals()
        PetPose.shoulder(root, owner, PetPose.shoulderAnchor(asset.anchor, owner), 0f, 0f, 0f, asset.scale)
        return true
    }

    private fun frame(scene: PetScene, asset: PetAsset, colors: Map<String, Int>?, eye: Matrix4f): PetScene.Preview? {
        val signals = PreviewTryOn.previewSignals()
        val events = signals.events(PlayerSignals.READER_PETS) or PreviewTryOn.equipEvents(asset.id)
        return scene.framePreview(root, asset.archetype, signals, events, signals.flag(PlayerSignals.IS_IN_COMBAT), colors, eye)
    }

    private fun eye(pose: Matrix4f, seated: Boolean): Matrix4f {
        eye.set(pose)
        if (!seated) eye.translate(SIDE_OFFSET, 0f, 0f)
        return eye
    }

    //? if >= 1.21.10 {
    private val draws = ArrayList<PreviewDraw>()
    private var used = 0

    private class PreviewDraw : SubmitNodeCollector.CustomGeometryRenderer {
        var preview: PetScene.Preview? = null
        var light = 0

        override fun render(pose: Pose, buffer: VertexConsumer) {
            preview?.emit(pose, buffer, light, OverlayTexture.NO_OVERLAY)
        }
    }

    @JvmStatic
    fun submit(
        pet: PetAsset,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        yawDegrees: Float,
        leftHanded: Boolean,
        limbSwing: Float,
        limbAmount: Float,
    ) {
        val asset = PetAssets.current(pet)
        val scene = asset.scene() ?: return
        val seated = seat(asset, yawDegrees, leftHanded, limbSwing, limbAmount)
        if (!seated) PetScene.root(root, 0f, 0f, 0f, Math.toRadians(yawDegrees.toDouble()).toFloat(), asset.scale)
        val colors = colors(asset)
        val preview = frame(scene, asset, colors, eye(poseStack.last().pose(), seated)) ?: return
        val texture = texture(asset, preview, colors) ?: return
        poseStack.pushPose()
        if (!seated) poseStack.translate(SIDE_OFFSET, 0f, 0f)
        collector.submitCustomGeometry(poseStack, PetRenderHook.renderType(texture), nextDraw(preview, light))
        asset.glowTexture?.let { glow ->
            collector.submitCustomGeometry(poseStack, PetRenderHook.glowType(glow), nextDraw(preview, FULL_BRIGHT))
        }
        if (scene.hasEffects) CosmeticEffects.submitPreview(preview, preview.model, poseStack, collector)
        poseStack.popPose()
    }

    private fun nextDraw(preview: PetScene.Preview, light: Int): PreviewDraw {
        if (used >= draws.size) draws += PreviewDraw()
        val draw = draws[used]
        used = (used + 1) % MAX_PENDING
        draw.preview = preview
        draw.light = light
        return draw
    }

    private const val MAX_PENDING = 16
    //?}

    //? if > 1.8.9 && < 1.21.10 {
    /*@JvmStatic
    fun renderEquipped(source: PlayerPreviewSource, poseStack: PoseStack, buffers: MultiBufferSource, yawDegrees: Float) {
        val asset = when (source) {
            is PlayerPreviewSource.Override -> source.pet
            PlayerPreviewSource.LocalLive -> CosmeticCatalog.localEquipped().equipped[BodySlot.Pet]?.let { PetAssets.get(it) }
        } ?: return
        val leftHanded = source == PlayerPreviewSource.LocalLive && Minecraft.getInstance().options.mainHand().get() == HumanoidArm.LEFT
        render(asset, poseStack, buffers, yawDegrees, leftHanded, 0f, 0f)
    }

    @JvmStatic
    fun render(
        pet: PetAsset,
        poseStack: PoseStack,
        buffers: MultiBufferSource,
        yawDegrees: Float,
        leftHanded: Boolean,
        limbSwing: Float,
        limbAmount: Float,
    ) {
        val asset = PetAssets.current(pet)
        val scene = asset.scene() ?: return
        val seated = seat(asset, yawDegrees, leftHanded, limbSwing, limbAmount)
        if (!seated) PetScene.root(root, 0f, 0f, 0f, Math.toRadians(yawDegrees.toDouble()).toFloat(), asset.scale)
        val colors = colors(asset)
        val preview = frame(scene, asset, colors, eye(poseStack.last().pose(), seated)) ?: return
        val texture = texture(asset, preview, colors) ?: return
        poseStack.pushPose()
        if (!seated) poseStack.translate(SIDE_OFFSET, 0f, 0f)
        preview.emit(poseStack.last(), buffers.getBuffer(PetRenderHook.renderType(texture)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
        asset.glowTexture?.let { glow ->
            preview.emit(poseStack.last(), buffers.getBuffer(PetRenderHook.glowType(glow)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY)
        }
        if (scene.hasEffects) CosmeticEffects.drawPreview(preview, preview.model, poseStack, buffers)
        poseStack.popPose()
    }
    *///?}

    //? if = 1.8.9 {
    /*private val poses = PoseStack()
    private val modelView = BufferUtils.createFloatBuffer(16)

    @JvmStatic
    fun draw(pet: PetAsset, yawDegrees: Float) {
        val asset = PetAssets.current(pet)
        val scene = asset.scene() ?: return
        val seated = seat(asset, yawDegrees, false, 0f, 0f)
        if (seated) {
            for (i in 4 until 12) root[i] = -root[i]
        } else {
            PetScene.guiRoot(root, Math.toRadians(yawDegrees.toDouble()).toFloat(), asset.scale)
        }
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView)
        val colors = colors(asset)
        val preview = frame(scene, asset, colors, eye(Matrix4f(modelView).mul(poses.last().pose()), seated)) ?: return
        val texture = texture(asset, preview, colors) ?: return
        poses.pushPose()
        if (!seated) poses.translate(SIDE_OFFSET, 0f, 0f)
        val pose = poses.last()
        VertexConsumer.draw(texture, PetTextures.isTranslucent(texture)) { buffer -> preview.emit(pose, buffer, FULL_BRIGHT, 0) }
        asset.glowTexture?.let { glow ->
            VertexConsumer.draw(glow, true, additive = true) { buffer -> preview.emit(pose, buffer, FULL_BRIGHT, 0) }
        }
        if (scene.hasEffects) CosmeticEffects.drawPreview(preview, Matrix4f(pose.pose()).mul(preview.model))
        poses.popPose()
    }
    *///?}
}
