//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
//? if >= 1.21.11 {
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBuffer
import kotlinx.coroutines.launch
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
//? if < 26.1 {
/*import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
import net.minecraft.client.renderer.state.CameraRenderState
*///?}
//? if >= 26.1 {
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
//?}
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.PlayerSkin
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.utils.ClientPlatform
//?}

object PlayerPreviewRenderer {
    private val equipmentByEntityId = java.util.concurrent.ConcurrentHashMap<Int, CosmeticEquipment>()

    @JvmStatic
    fun previewEquipment(entityId: Int): CosmeticEquipment? = equipmentByEntityId[entityId]

    @Volatile
    private var latest: ImageBitmap? = null

    fun capture(
        source: PlayerPreviewSource,
        yawDeg: Float,
        pitchDeg: Float,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? {
        if (widthPx <= 0 || heightPx <= 0) return latest
        //? if >= 1.21.11 {
        val w = widthPx.coerceAtMost(MAX_DIM)
        val h = heightPx.coerceAtMost(MAX_DIM)
        ClientPlatform.runOnMain {
            runCatching { renderAndReadback(source, yawDeg, pitchDeg, w, h) }
                .onFailure { LOG.error("[preview] capture failed", it) }
        }
        return latest
        //?} else {
        /*return runCatching { testPattern(widthPx, heightPx, yawDeg) }.getOrNull()
        *///?}
    }

    fun dispose() {
        //? if >= 1.21.11 {
        val t = target
        target = null
        latest = null
        if (t != null) ClientPlatform.runOnMain { runCatching { t.destroyBuffers() } }
        //?}
    }

    //? if >= 1.21.11 {
    private val LOG = org.slf4j.LoggerFactory.getLogger("polyplus/preview")
    private const val MAX_DIM = 512

    private const val MODEL_SCALE_FACTOR = 0.5f

    private var target: TextureTarget? = null
    private var dummy: AbstractClientPlayer? = null
    //? if < 26.1 {
    /*private val projection = CachedOrthoProjectionMatrixBuffer("polyplus_preview", -1000f, 1000f, true)
    *///?}
    //? if >= 26.1 {
    private val projection = ProjectionMatrixBuffer("polyplus_preview")

    private fun orthoMatrix(w: Int, h: Int): Matrix4f =
        Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -1000f, 1000f)
    //?}

    private fun ensureTarget(w: Int, h: Int): TextureTarget {
        val existing = target
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.destroyBuffers()
        return TextureTarget("polyplus_player_preview", w, h, true).also { target = it }
    }

    private fun renderAndReadback(source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int) {
        val mc = Minecraft.getInstance()
        val fbo = ensureTarget(w, h)
        val colorTex = fbo.colorTexture ?: return
        val depthTex = fbo.depthTexture ?: return
        val colorView = fbo.colorTextureView ?: return
        val depthView = fbo.depthTextureView ?: return

        val savedLights = RenderSystem.getShaderLights()
        val savedFog = RenderSystem.getShaderFog()

        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, 0x00000000, depthTex, 1.0)
        RenderSystem.backupProjectionMatrix()
        //? if < 26.1 {
        /*RenderSystem.setProjectionMatrix(projection.getBuffer(w.toFloat(), h.toFloat()), ProjectionType.ORTHOGRAPHIC)
        *///?}
        //? if >= 26.1 {
        RenderSystem.setProjectionMatrix(projection.getBuffer(orthoMatrix(w, h)), ProjectionType.ORTHOGRAPHIC)
        //?}
        RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        try {
            val level = mc.level
            if (level != null) renderEntity(mc, level, source, yawDeg, w, h)
            else renderDirect(mc, source, yawDeg, w, h)
        } finally {
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            RenderSystem.restoreProjectionMatrix()
            if (savedLights != null) RenderSystem.setShaderLights(savedLights)
            if (savedFog != null) RenderSystem.setShaderFog(savedFog)
        }

        readback(colorTex, w, h)
    }

    private fun renderEntity(mc: Minecraft, level: ClientLevel, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int) {
        val player = dummy(mc, level) ?: return
        bindEquipment(player, source)
        player.setYRot(0f); player.yRotO = 0f
        player.yBodyRot = 0f; player.yBodyRotO = 0f
        player.yHeadRot = 0f; player.yHeadRotO = 0f
        player.setXRot(0f); player.xRotO = 0f
        val state = mc.entityRenderDispatcher.extractEntity(player, 1.0f) as? AvatarRenderState ?: return
        state.lightCoords = 0xF000F0
        state.bodyRot = 180f + yawDeg
        state.yRot = 0f
        state.xRot = 0f
        val bbH = state.boundingBoxHeight / state.scale
        state.boundingBoxWidth /= state.scale
        state.boundingBoxHeight = bbH
        state.scale = 1f

        val scale = h * MODEL_SCALE_FACTOR
        val pose = PoseStack()
        pose.translate(w / 2f, h / 2f, 0f)
        pose.scale(scale, scale, scale)
        pose.translate(0f, bbH / 2f, 0f)
        pose.mulPose(Quaternionf().rotateZ(Math.PI.toFloat()))

        mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        val camera = CameraRenderState().apply {
            orientation = Quaternionf().rotateY(Math.PI.toFloat())
            pos = net.minecraft.world.phys.Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = net.minecraft.world.phys.Vec3.ZERO
            *///?}
        }
        val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
    }

    private fun renderDirect(mc: Minecraft, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int) {
        val skin = localSkin(mc) ?: return
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive -> localEquipment()
        }
        equipmentByEntityId[PREVIEW_ENTITY_ID] = equipment

        val state = directState(skin)
        state.id = PREVIEW_ENTITY_ID
        state.bodyRot = 180f + yawDeg
        state.boundingBoxWidth = PLAYER_BB_WIDTH
        state.boundingBoxHeight = PLAYER_BB_HEIGHT
        state.scale = 1f
        val bbH = PLAYER_BB_HEIGHT

        val scale = h * MODEL_SCALE_FACTOR
        val pose = PoseStack()
        pose.translate(w / 2f, h / 2f, 0f)
        pose.scale(scale, scale, scale)
        pose.translate(0f, bbH / 2f, 0f)
        pose.mulPose(Quaternionf().rotateZ(Math.PI.toFloat()))

        mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        val camera = CameraRenderState().apply {
            orientation = Quaternionf().rotateY(Math.PI.toFloat())
            pos = net.minecraft.world.phys.Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = net.minecraft.world.phys.Vec3.ZERO
            *///?}
        }
        val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
    }

    private val loadAttempted = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())

    private fun localEquipment(): CosmeticEquipment {
        val ids = org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog.localEquipped().ids()
        val equipment = CosmeticEquipment()
        for (id in ids) {
            val attached = org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.getAttachedCosmetic(id)
            if (attached != null) {
                equipment.equip(attached)
            } else if (loadAttempted.add(id)) {
                org.polyfrost.polyplus.client.PolyPlusClient.SCOPE.launch {
                    runCatching { org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.ensureCosmeticLoaded(id) }
                }
            }
        }
        return equipment
    }

    private fun localSkin(mc: Minecraft): PlayerSkin? {
        val profile = mc.gameProfile
        return runCatching { mc.skinManager.createLookup(profile, false).get() }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(profile) }.getOrNull()
    }

    private fun directState(skin: PlayerSkin): AvatarRenderState = AvatarRenderState().apply {
        (this as? org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(org.polyfrost.polyplus.client.emotes.playback.EmoteController())
        this.skin = skin
        mainArm = HumanoidArm.RIGHT
        leftArmPose = HumanoidModel.ArmPose.EMPTY
        rightArmPose = HumanoidModel.ArmPose.EMPTY
        showHat = true
        showJacket = true
        showLeftSleeve = true
        showRightSleeve = true
        showLeftPants = true
        showRightPants = true
        showCape = false
        lightCoords = 0xF000F0
        yRot = 0f
        xRot = 0f
        ageInTicks = (System.nanoTime() / 50_000_000L).toFloat()
    }

    private const val PREVIEW_ENTITY_ID = Int.MIN_VALUE + 1

    private const val PLAYER_BB_WIDTH = 0.6f
    private const val PLAYER_BB_HEIGHT = 1.8f

    private fun dummy(mc: Minecraft, level: ClientLevel): AbstractClientPlayer? {
        dummy?.let { return it }
        return runCatching { RemotePlayer(level, mc.gameProfile) }.getOrNull()?.also { dummy = it }
    }

    private fun bindEquipment(player: AbstractClientPlayer, source: PlayerPreviewSource) {
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive ->
                (Minecraft.getInstance().player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`()
        } ?: return
        equipmentByEntityId[player.id] = equipment
    }

    private fun readback(colorTex: com.mojang.blaze3d.textures.GpuTexture, w: Int, h: Int) {
        val device = RenderSystem.getDevice()
        val pixelSize = colorTex.format.pixelSize()
        val size = w.toLong() * h.toLong() * pixelSize
        val buffer = device.createBuffer({ "polyplus_preview_readback" }, GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST, size)
        device.createCommandEncoder().copyTextureToBuffer(colorTex, buffer, 0L, Runnable {
            runCatching {
                val mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)
                try {
                    latest = toImageBitmap(mapped.data(), w, h, pixelSize)
                } finally {
                    mapped.close()
                }
            }.onFailure { LOG.error("[preview] readback failed", it) }
            buffer.close()
        }, 0)
    }

    private const val BRIGHTNESS_GAIN = 1.6f

    private fun toImageBitmap(data: java.nio.ByteBuffer, w: Int, h: Int, pixelSize: Int): ImageBitmap {
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) {
            val dstRow = (h - 1 - y) * w
            val srcRow = y * w
            for (x in 0 until w) {
                val si = (srcRow + x) * pixelSize
                val a = data.get(si + 3)
                val di = (dstRow + x) * 4
                out[di] = gain(data.get(si + 2))
                out[di + 1] = gain(data.get(si + 1))
                out[di + 2] = gain(data.get(si))
                out[di + 3] = a
            }
        }
        return SkiaImage.makeRaster(ImageInfo.makeN32Premul(w, h), out, w * 4).toComposeImageBitmap()
    }

    private fun gain(v: Byte): Byte {
        val scaled = ((v.toInt() and 0xFF) * BRIGHTNESS_GAIN).toInt()
        return (if (scaled > 255) 255 else scaled).toByte()
    }

    private var tick = 0
    private fun logTick(): Boolean { tick++; return tick % 120 == 0 }
    //?}

    //? if < 1.21.11 {
    /*private fun testPattern(w: Int, h: Int, yawDeg: Float): ImageBitmap {
        val bytes = ByteArray(w * h * 4)
        for (i in bytes.indices step 4) {
            bytes[i] = 60; bytes[i + 1] = 40; bytes[i + 2] = 30; bytes[i + 3] = 0xFF.toByte()
        }
        return SkiaImage.makeRaster(ImageInfo.makeN32Premul(w, h), bytes, w * 4).toComposeImageBitmap()
    }*///?}
}
//?}
