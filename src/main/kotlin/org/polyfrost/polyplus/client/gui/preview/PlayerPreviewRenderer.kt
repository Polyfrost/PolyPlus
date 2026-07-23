//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.gui.preview

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
//? if >= 1.21.8 {
import com.mojang.blaze3d.ProjectionType
import com.mojang.blaze3d.buffers.GpuBuffer
import kotlinx.coroutines.launch
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuTextureView
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
//? if < 26.2 {
import com.mojang.blaze3d.vertex.Tesselator
//?}
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
//? if >= 1.21.10 {
import net.minecraft.client.model.HumanoidModel
//?}
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
//? if < 26.1 {
/*import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
*///?}
//? if >= 1.21.10 && < 26.1 {
/*import net.minecraft.client.renderer.state.CameraRenderState
*///?}
//? if >= 26.1 {
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.state.level.CameraRenderState
import org.joml.Matrix4f
//?}
//? if >= 1.21.10 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState
//?} else {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState as AvatarRenderState
*///?}
import net.minecraft.client.resources.DefaultPlayerSkin
//? if >= 1.21.10 {
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.HumanoidArm
//?}
import net.minecraft.resources.Identifier
//? if >= 1.21.10 {
import net.minecraft.world.entity.player.PlayerSkin
//?} else {
/*import net.minecraft.client.resources.PlayerSkin
*///?}
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.utils.ClientPlatform
//?}
//? if >= 1.21.5 && < 1.21.8 {
/*import org.polyfrost.polyplus.client.utils.ClientPlatform
*///?}
//? if >= 1.21.1 && < 1.21.5 {
/*import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.utils.ClientPlatform
*///?}
//? if >= 1.21.4 && < 1.21.5 {
/*import com.mojang.blaze3d.ProjectionType
import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.client.renderer.entity.state.PlayerRenderState
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
*///?}
//? if >= 1.21.1 && < 1.21.4 {
/*import com.mojang.authlib.GameProfile
import com.mojang.blaze3d.vertex.VertexSorting
import kotlinx.coroutines.launch
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.client.resources.PlayerSkin
import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.DimensionTypes
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.profiling.InactiveProfiler
import net.minecraft.world.Difficulty
import net.minecraft.world.damagesource.DamageScaling
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.damagesource.DamageTypes
import net.minecraft.world.entity.player.PlayerModelPart
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.BiomeGenerationSettings
import net.minecraft.world.level.biome.BiomeSpecialEffects
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.biome.MobSpawnSettings
import net.minecraft.world.level.dimension.BuiltinDimensionTypes
import net.minecraft.world.level.dimension.DimensionType
import net.minecraft.world.phys.Vec3
*///?}

object PlayerPreviewRenderer {
    private val equipmentByEntityId = java.util.concurrent.ConcurrentHashMap<Int, CosmeticEquipment>()

    @JvmStatic
    fun previewEquipment(entityId: Int): CosmeticEquipment? = equipmentByEntityId[entityId]

    @Volatile
    private var renderingPreview = false

    @JvmStatic
    fun isRenderingPreview(): Boolean = renderingPreview

    //? if >= 1.21.10 {
    @Volatile
    private var previewCape: Identifier? = null

    @JvmStatic
    fun previewCapeOverride(): Any? = previewCape
    //?}

    @JvmStatic
    fun previewParticleColor(entityId: Int): Int? =
        if (equipmentByEntityId.containsKey(entityId)) {
            org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())
        } else {
            null
        }

    private val latestByKey = java.util.concurrent.ConcurrentHashMap<Any, ImageBitmap>()

    fun cached(key: Any): ImageBitmap? = latestByKey[key]

    fun capture(
        source: PlayerPreviewSource,
        yawDeg: Float,
        pitchDeg: Float,
        widthPx: Int,
        heightPx: Int,
        modelScale: Float,
        verticalAnchor: Float,
        key: Any = source,
    ): ImageBitmap? {
        if (widthPx <= 0 || heightPx <= 0) return latestByKey[key]
        //? if >= 1.21.8 {
        val w = widthPx.coerceAtMost(MAX_DIM)
        val h = heightPx.coerceAtMost(MAX_DIM)
        ClientPlatform.runOnMain {
            runCatching { renderAndReadback(source, yawDeg, pitchDeg, w, h, modelScale, verticalAnchor, key) }
                .onFailure { LOG.error("[preview] capture failed", it) }
        }
        return latestByKey[key]
        //?} elif >= 1.21.5 {
        /*return runCatching { testPattern(widthPx, heightPx, yawDeg) }.getOrNull()
        *///?} else {
        /*val w = widthPx.coerceAtMost(LEGACY_MAX_DIM)
        val h = heightPx.coerceAtMost(LEGACY_MAX_DIM)
        ClientPlatform.runOnMain {
            runCatching { renderLegacy(source, yawDeg, w, h, modelScale, verticalAnchor, key) }
                .onFailure { LEGACY_LOG.error("[preview] legacy capture failed", it) }
        }
        return latestByKey[key]
        *///?}
    }

    fun dispose() {
        //? if >= 1.21.8 {
        val t = target
        target = null
        latestByKey.clear()
        if (t != null) ClientPlatform.runOnMain { runCatching { t.destroyBuffers() } }
        //?}
    }

    private const val EDGE_FADE_FRACTION = 0.18f

    private fun edgeFadeColumns(w: Int): FloatArray {
        val cols = FloatArray(w)
        val fadePx = w * EDGE_FADE_FRACTION
        for (x in 0 until w) {
            val d = minOf(x + 0.5f, w - 0.5f - x) // distance from nearest edge (pixel center)
            val t = if (fadePx <= 0f) 1f else (d / fadePx).coerceIn(0f, 1f)
            cols[x] = t * t * t * (t * (t * 6f - 15f) + 10f)
        }
        return cols
    }

    private fun topFadeRows(h: Int): FloatArray {
        val rows = FloatArray(h)
        val fadePx = h * EDGE_FADE_FRACTION
        for (r in 0 until h) {
            val d = r + 0.5f // distance from top edge (pixel center)
            val t = if (fadePx <= 0f) 1f else (d / fadePx).coerceIn(0f, 1f)
            rows[r] = t * t * t * (t * (t * 6f - 15f) + 10f)
        }
        return rows
    }

    private fun scaleByte(v: Int, f: Float): Byte {
        val s = (v * f).toInt()
        return (if (s > 255) 255 else s).toByte()
    }

    private const val GRID_X = 24
    private const val GRID_Y = 16

    private fun smooth(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * c * (c * (c * 6f - 15f) + 10f)
    }

    private fun fadeAlpha(tx: Float, ty: Float, fadeEdges: Boolean, bottomFade: Float): Float {
        var a = 1f
        if (fadeEdges) {
            val ef = EDGE_FADE_FRACTION
            a *= smooth(minOf(tx, 1f - tx) / ef)
            a *= smooth(ty / ef)
        }
        if (bottomFade > 0f) a *= smooth((1f - ty) / bottomFade)
        return a
    }

    private fun buildFadeQuad(bb: com.mojang.blaze3d.vertex.VertexConsumer, x: Int, y: Int, w: Int, h: Int, fadeEdges: Boolean, bottomFade: Float, opacity: Float) {
        for (iy in 0 until GRID_Y) {
            val ty0 = iy / GRID_Y.toFloat(); val ty1 = (iy + 1) / GRID_Y.toFloat()
            for (ix in 0 until GRID_X) {
                val tx0 = ix / GRID_X.toFloat(); val tx1 = (ix + 1) / GRID_X.toFloat()
                addFadeVertex(bb, x, y, w, h, tx0, ty0, fadeEdges, bottomFade, opacity)
                addFadeVertex(bb, x, y, w, h, tx0, ty1, fadeEdges, bottomFade, opacity)
                addFadeVertex(bb, x, y, w, h, tx1, ty1, fadeEdges, bottomFade, opacity)
                addFadeVertex(bb, x, y, w, h, tx1, ty0, fadeEdges, bottomFade, opacity)
            }
        }
    }

    private fun addFadeVertex(bb: com.mojang.blaze3d.vertex.VertexConsumer, x: Int, y: Int, w: Int, h: Int, tx: Float, ty: Float, fadeEdges: Boolean, bottomFade: Float, opacity: Float) {
        val px = x + tx * w
        val py = y + ty * h
        val a = (fadeAlpha(tx, ty, fadeEdges, bottomFade) * opacity * 255f).toInt().coerceIn(0, 255)
        bb.addVertex(px, py, 0f).setUv(tx, 1f - ty).setColor(255, 255, 255, a)
    }

    private val mountOpacitySetter: java.lang.invoke.MethodHandle? = runCatching {
        val hook = Class.forName("dev.microcontrollers.mountopacity.hook.EntityRenderStateHook")
        java.lang.invoke.MethodHandles.lookup()
            .findVirtual(hook, "mountopacity\$setOpacity", java.lang.invoke.MethodType.methodType(Void.TYPE, Float::class.javaPrimitiveType))
            .asType(java.lang.invoke.MethodType.methodType(Void.TYPE, Any::class.java, Float::class.javaPrimitiveType))
    }.getOrNull()

    private fun applyModCompat(state: Any) {
        mountOpacitySetter?.invokeExact(state, 100f)
    }

    //? if >= 1.21.8 {
    private val LOG = org.slf4j.LoggerFactory.getLogger("polyplus/preview")
    private const val MAX_DIM = 512

    private var target: TextureTarget? = null
    private var dummy: AbstractClientPlayer? = null
    private var dummyProfileId: java.util.UUID? = null
    //? if < 26.1 {
    /*private val projection by lazy { CachedOrthoProjectionMatrixBuffer("polyplus_preview", -1000f, 1000f, true) }
    *///?}
    //? if >= 26.1 {
    private val projection by lazy { ProjectionMatrixBuffer("polyplus_preview") }

    private fun orthoMatrix(w: Int, h: Int): Matrix4f =
        Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -1000f, 1000f)
    //?}
    //? if >= 26.2 {
    /*private fun orthoProjection(w: Int, h: Int): net.minecraft.client.renderer.Projection =
        net.minecraft.client.renderer.Projection().apply { setupOrtho(-1000f, 1000f, w.toFloat(), h.toFloat(), true) }
    *///?}

    private fun ensureTarget(w: Int, h: Int): TextureTarget {
        val existing = target
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.destroyBuffers()
        //? if >= 26.2 {
        /*return TextureTarget("polyplus_player_preview", w, h, true, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM).also { target = it }
        *///?} else {
        return TextureTarget("polyplus_player_preview", w, h, true).also { target = it }
        //?}
    }

    private fun renderAndReadback(source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, key: Any) {
        val fbo = renderSceneIntoTarget(source, yawDeg, pitchDeg, w, h, modelScale, verticalAnchor) ?: return
        val colorTex = fbo.colorTexture ?: return
        readback(colorTex, w, h, key)
    }

    private fun renderSceneIntoTarget(source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float): TextureTarget? {
        val mc = Minecraft.getInstance()
        val fbo = ensureTarget(w, h)
        val colorTex = fbo.colorTexture ?: return null
        val depthTex = fbo.depthTexture ?: return null
        val colorView = fbo.colorTextureView ?: return null
        val depthView = fbo.depthTextureView ?: return null

        val savedLights = RenderSystem.getShaderLights()
        val savedFog = RenderSystem.getShaderFog()

        //? if >= 26.2 {
        /*RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, org.joml.Vector4f(0f, 0f, 0f, 0f), depthTex, 0.0)
        *///?} else {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, 0x00000000, depthTex, 1.0)
        //?}
        RenderSystem.backupProjectionMatrix()
        //? if < 26.1 {
        /*RenderSystem.setProjectionMatrix(projection.getBuffer(w.toFloat(), h.toFloat()), ProjectionType.ORTHOGRAPHIC)
        *///?}
        //? if >= 26.2 {
        /*RenderSystem.setProjectionMatrix(projection.getBuffer(orthoProjection(w, h)), ProjectionType.ORTHOGRAPHIC)
        *///?} else {
        //? if >= 26.1 {
        RenderSystem.setProjectionMatrix(projection.getBuffer(orthoMatrix(w, h)), ProjectionType.ORTHOGRAPHIC)
        //?}
        //?}
        RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        //? if >= 26.1 {
        val scissor = RenderSystem.getScissorStateForRenderTypeDraws()
        val hadScissor = scissor.enabled()
        val scX = scissor.x(); val scY = scissor.y(); val scW = scissor.width(); val scH = scissor.height()
        RenderSystem.disableScissorForRenderTypeDraws()
        //?} else {
        /*RenderSystem.disableScissorForRenderTypeDraws()
        *///?}
        //? if >= 26.2 {
        /*val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.identity()
        *///?}
        try {
            //? if >= 1.21.10 {
            previewCape = capeOverride(source)?.let { ClientAsset.ResourceTexture(it).texturePath() }
            renderingPreview = true
            try {
                val level = mc.level
                if (level != null && mc.cameraEntity != null) renderEntity(mc, level, source, yawDeg, w, h, modelScale, verticalAnchor)
                else renderDirect(mc, source, yawDeg, w, h, modelScale, verticalAnchor)
            } catch (t: Throwable) {
                LOG.error("[preview] entity submit failed; skipping frame", t)
            } finally {
                renderingPreview = false
                previewCape = null
            }
            //?} else {
            /*renderDirect(mc, source, yawDeg, w, h, modelScale, verticalAnchor)
            *///?}
        } finally {
            //? if >= 26.2 {
            /*RenderSystem.getModelViewStack().popMatrix()
            *///?}
            RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            RenderSystem.restoreProjectionMatrix()
            savedLights?.let { RenderSystem.setShaderLights(it) }
            savedFog?.let { RenderSystem.setShaderFog(it) }
            //? if >= 26.1 {
            if (hadScissor) RenderSystem.enableScissorForRenderTypeDraws(scX, scY, scW, scH)
            //?}
        }

        return fbo
    }

    @JvmStatic
    fun renderOverlayEntry(target: RenderTarget, e: PlayerPreviewOverlay.Entry, yawDeg: Float, pitchDeg: Float, rectX: Int, rectY: Int, rectW: Int, rectH: Int) {
        if (rectW <= 0 || rectH <= 0) return
        val fit = minOf(1f, MAX_DIM.toFloat() / maxOf(rectW, rectH))
        val w = (rectW * fit).toInt().coerceAtLeast(1)
        val h = (rectH * fit).toInt().coerceAtLeast(1)
        val fbo = renderSceneIntoTarget(e.source, yawDeg, pitchDeg, w, h, e.modelScale, e.verticalAnchor) ?: return
        val srcView = fbo.colorTextureView ?: return
        compositeOntoTarget(target, srcView, rectX, rectY, rectW, rectH, e.fadeEdges, e.bottomFade, e.opacity)
    }

    private fun compositeOntoTarget(target: RenderTarget, srcView: GpuTextureView, x: Int, y: Int, w: Int, h: Int, fadeEdges: Boolean, bottomFade: Float, opacity: Float) {
        val dstView = target.colorTextureView ?: return
        val fbW = target.width
        val fbH = target.height

        val fmt = DefaultVertexFormat.POSITION_TEX_COLOR
        //? if >= 26.2 {
        /*val byteBuilder = com.mojang.blaze3d.vertex.ByteBufferBuilder(1024)
        val bb: com.mojang.blaze3d.vertex.VertexConsumer = com.mojang.blaze3d.vertex.BufferBuilder(byteBuilder, com.mojang.blaze3d.PrimitiveTopology.QUADS, fmt)
        *///?} else {
        val bb: com.mojang.blaze3d.vertex.VertexConsumer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, fmt)
        //?}
        buildFadeQuad(bb, x, y, w, h, fadeEdges, bottomFade, opacity)
        //? if >= 26.2 {
        /*val mesh = (bb as com.mojang.blaze3d.vertex.BufferBuilder).build() ?: run { byteBuilder.close(); return }
        *///?} else {
        val mesh = (bb as com.mojang.blaze3d.vertex.BufferBuilder).build() ?: return
        //?}
        val indexCount = GRID_X * GRID_Y * 6

        try {
            val device = RenderSystem.getDevice()
            val vbuf = device.createBuffer({ "polyplus_preview_quad" }, GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer())
            try {
                //? if >= 26.2 {
                /*val seq = RenderSystem.getSequentialBuffer(com.mojang.blaze3d.PrimitiveTopology.QUADS)
                *///?} else {
                val seq = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS)
                //?}
                val ibuf = seq.getBuffer(indexCount)
                val itype = seq.type()

                RenderSystem.backupProjectionMatrix()
                //? if >= 26.2 {
                /*RenderSystem.setProjectionMatrix(projection.getBuffer(orthoProjection(fbW, fbH)), ProjectionType.ORTHOGRAPHIC)
                *///?} elif >= 26.1 {
                RenderSystem.setProjectionMatrix(projection.getBuffer(orthoMatrix(fbW, fbH)), ProjectionType.ORTHOGRAPHIC)
                //?} else {
                /*RenderSystem.setProjectionMatrix(projection.getBuffer(fbW.toFloat(), fbH.toFloat()), ProjectionType.ORTHOGRAPHIC)
                *///?}
                val encoder = device.createCommandEncoder()
                //? if >= 26.2 {
                /*val pass = encoder.createRenderPass({ "polyplus_preview_composite" }, dstView, java.util.Optional.empty())
                *///?} else {
                val pass = encoder.createRenderPass({ "polyplus_preview_composite" }, dstView, java.util.OptionalInt.empty())
                //?}
                try {
                    pass.setPipeline(RenderPipelines.GUI_TEXTURED)
                    RenderSystem.bindDefaultUniforms(pass)
                    //? if >= 26.2 {
                    /*pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(Matrix4f()))
                    *///?}
                    //? if >= 1.21.11 {
                    val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                    pass.bindTexture("Sampler0", srcView, sampler)
                    //?} else {
                    /*pass.bindSampler("Sampler0", srcView)
                    *///?}
                    //? if >= 26.2 {
                    /*pass.setVertexBuffer(0, vbuf.slice())
                    *///?} else {
                    pass.setVertexBuffer(0, vbuf)
                    //?}
                    pass.setIndexBuffer(ibuf, itype)
                    //? if >= 26.2 {
                    /*pass.drawIndexed(indexCount, 1, 0, 0, 0)
                    *///?} else {
                    pass.drawIndexed(0, 0, indexCount, 1)
                    //?}
                } finally {
                    pass.close()
                    RenderSystem.restoreProjectionMatrix()
                }
            } finally {
                vbuf.close()
            }
        } finally {
            mesh.close()
            //? if >= 26.2 {
            /*byteBuilder.close()
            *///?}
        }
    }

    //? if >= 1.21.10 {
    private fun renderEntity(mc: Minecraft, level: ClientLevel, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float) {
        val player = dummy(mc, level) ?: return
        bindEquipment(player, source)
        player.setYRot(0f); player.yRotO = 0f
        player.yBodyRot = 0f; player.yBodyRotO = 0f
        player.yHeadRot = 0f; player.yHeadRotO = 0f
        player.setXRot(0f); player.xRotO = 0f
        val state = mc.entityRenderDispatcher.extractEntity(player, 1.0f) as? AvatarRenderState ?: return
        state.lightCoords = 0xF000F0
        state.showCape = source !is PlayerPreviewSource.Override || equipmentByEntityId[player.id]?.get(BodySlot.Backpack) == null
        capeOverride(source)?.let { state.skin = withCape(state.skin, it) }
        state.bodyRot = yawDeg
        state.yRot = 0f
        state.xRot = 0f
        val bbH = state.boundingBoxHeight / state.scale
        state.boundingBoxWidth /= state.scale
        state.boundingBoxHeight = bbH
        state.scale = 1f

        val scale = h * modelScale
        val pose = PoseStack()
        pose.translate(w / 2f, h * verticalAnchor, 0f)
        pose.scale(scale, scale, -scale)
        pose.translate(0f, bbH / 2f, 0f)
        pose.mulPose(Quaternionf().rotateZ(Math.PI.toFloat()))

        //? if >= 26.2 {
        /*mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        *///?} else {
        mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        //?}
        val camera = CameraRenderState().apply {
            orientation = Quaternionf().rotateY(Math.PI.toFloat())
            pos = net.minecraft.world.phys.Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = net.minecraft.world.phys.Vec3.ZERO
            *///?}
        }
        //? if >= 26.2 {
        /*val features = mc.gameRenderer.featureRenderDispatcher()
        val submitStorage = net.minecraft.client.renderer.SubmitNodeStorage()
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, submitStorage)
        features.renderAllFeatures(submitStorage)
        *///?} else {
        val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        //?}
    }
    //?}

    private fun renderDirect(mc: Minecraft, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float) {
        val baseSkin = localSkin(mc) ?: return
        val skin = capeOverride(source)?.let { withCape(baseSkin, it) } ?: baseSkin
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive -> localEquipment()
        }
        equipmentByEntityId[PREVIEW_ENTITY_ID] = equipment

        val state = directState(skin)
        state.id = PREVIEW_ENTITY_ID
        if (source is PlayerPreviewSource.Override && equipment.get(BodySlot.Backpack) != null) state.showCape = false
        //? if >= 1.21.10 {
        state.bodyRot = yawDeg
        //?} else {
        /*state.bodyRot = yawDeg + 180f // no CameraRenderState flip on render(state); turn body to camera
        *///?}
        state.boundingBoxWidth = PLAYER_BB_WIDTH
        state.boundingBoxHeight = PLAYER_BB_HEIGHT
        state.scale = 1f
        val bbH = PLAYER_BB_HEIGHT

        val scale = h * modelScale
        val pose = PoseStack()
        pose.translate(w / 2f, h * verticalAnchor, 0f)
        pose.scale(scale, scale, -scale)
        pose.translate(0f, bbH / 2f, 0f)
        pose.mulPose(Quaternionf().rotateZ(Math.PI.toFloat()))

        //? if >= 26.2 {
        /*mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        *///?} else {
        mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        //?}
        //? if >= 1.21.10 {
        val camera = CameraRenderState().apply {
            orientation = Quaternionf().rotateY(Math.PI.toFloat())
            pos = net.minecraft.world.phys.Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = net.minecraft.world.phys.Vec3.ZERO
            *///?}
        }
        //? if >= 26.2 {
        /*val features = mc.gameRenderer.featureRenderDispatcher()
        val submitStorage = net.minecraft.client.renderer.SubmitNodeStorage()
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, submitStorage)
        features.renderAllFeatures(submitStorage)
        *///?} else {
        val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        //?}
        //?} else {
        /*val bufferSource = mc.renderBuffers().bufferSource()
        // render(state,…) draws a ground shadow that dereferences mc.level → NPE off-world.
        mc.entityRenderDispatcher.setRenderShadow(false)
        try {
            mc.entityRenderDispatcher.render(state, 0.0, 0.0, 0.0, pose, bufferSource, 0xF000F0)
            bufferSource.endBatch()
        } finally {
            mc.entityRenderDispatcher.setRenderShadow(true)
        }
        *///?}
    }

    private val loadAttempted = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())

    private var cachedLocalEquipment: CosmeticEquipment? = null
    private var cachedLocalKey: List<String>? = null

    private fun localEquipment(): CosmeticEquipment {
        val ids = org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog.localEquipped().ids()
        val resolved = ids.map { id -> org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.getAttachedCosmetic(id) }
        val key = ids.mapIndexed { i, id -> if (resolved[i] != null) "$id" else "$id:pending" }
        cachedLocalEquipment?.let { if (cachedLocalKey == key) return it }

        val equipment = CosmeticEquipment()
        ids.forEachIndexed { i, id ->
            val attached = resolved[i]
            if (attached != null) {
                equipment.equip(attached)
            } else if (loadAttempted.add(id)) {
                org.polyfrost.polyplus.client.PolyPlusClient.SCOPE.launch {
                    runCatching { org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.ensureCosmeticLoaded(id) }
                }
            }
        }
        cachedLocalEquipment = equipment
        cachedLocalKey = key
        return equipment
    }

    private fun capeOverride(source: PlayerPreviewSource): Identifier? = when (source) {
        is PlayerPreviewSource.Override -> source.capeTexture
        PlayerPreviewSource.LocalLive ->
            org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog.localEquipped().cape?.let {
                org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.getCapeResource(it)
            }
    }

    //? if >= 1.21.10 {
    private fun withCape(skin: PlayerSkin, cape: Identifier): PlayerSkin =
        PlayerSkin(skin.body(), ClientAsset.ResourceTexture(cape), skin.elytra(), skin.model(), skin.secure())
    //?} else {
    /*private fun withCape(skin: PlayerSkin, cape: Identifier): PlayerSkin =
        PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure())
    *///?}

    @Volatile
    private var resolvedProfile: com.mojang.authlib.GameProfile? = null
    private var resolvingProfileId: java.util.UUID? = null

    private fun texturedProfile(mc: Minecraft): com.mojang.authlib.GameProfile? {
        val id = mc.user.profileId
        val startup = mc.gameProfile
        if (startup.id == id) return startup // no switch: startup profile already carries textures
        resolvedProfile?.let { if (it.id == id) return it }
        synchronized(this) {
            if (resolvingProfileId != id) {
                resolvingProfileId = id
                val name = mc.user.name
                org.polyfrost.polyplus.client.PolyPlusClient.SCOPE.launch {
                    val fetched = runCatching {
                        //? if >= 1.21.10 {
                        mc.services().sessionService().fetchProfile(id, false)?.profile()
                        //?} else {
                        /*mc.minecraftSessionService.fetchProfile(id, false)?.profile()
                        *///?}
                    }.getOrNull() ?: com.mojang.authlib.GameProfile(id, name)
                    synchronized(this@PlayerPreviewRenderer) {
                        if (resolvingProfileId == id) {
                            resolvedProfile = fetched
                            resolvingProfileId = null
                        }
                    }
                }
            }
        }
        return null
    }

    //? if >= 1.21.10 {
    private fun localSkin(mc: Minecraft): PlayerSkin? {
        val profile = texturedProfile(mc) ?: return DefaultPlayerSkin.get(com.mojang.authlib.GameProfile(mc.user.profileId, mc.user.name))
        return runCatching { mc.skinManager.createLookup(profile, false).get() }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(profile) }.getOrNull()
    }
    //?} else {
    /*private fun localSkin(mc: Minecraft): PlayerSkin? {
        val profile = texturedProfile(mc) ?: return DefaultPlayerSkin.get(com.mojang.authlib.GameProfile(mc.user.profileId, mc.user.name))
        return runCatching { mc.skinManager.getInsecureSkin(profile) }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(profile) }.getOrNull()
    }
    *///?}

    //? if >= 1.21.10 {
    private fun directState(skin: PlayerSkin): AvatarRenderState = AvatarRenderState().apply {
        (this as? org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(org.polyfrost.polyplus.client.emotes.playback.EmoteController())
        applyModCompat(this)
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
        showCape = true
        lightCoords = 0xF000F0
        yRot = 0f
        xRot = 0f
        ageInTicks = (System.nanoTime() / 50_000_000L).toFloat()
    }
    //?} else {
    /*private fun directState(skin: PlayerSkin): AvatarRenderState = AvatarRenderState().apply {
        (this as? org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(org.polyfrost.polyplus.client.emotes.playback.EmoteController())
        applyModCompat(this)
        this.skin = skin
        showHat = true
        showJacket = true
        showLeftSleeve = true
        showRightSleeve = true
        showLeftPants = true
        showRightPants = true
        showCape = true
        yRot = 0f
        xRot = 0f
        ageInTicks = (System.nanoTime() / 50_000_000L).toFloat()
    }
    *///?}

    private const val PREVIEW_ENTITY_ID = Int.MIN_VALUE + 1

    private const val PLAYER_BB_WIDTH = 0.6f
    private const val PLAYER_BB_HEIGHT = 1.8f

    private fun dummy(mc: Minecraft, level: ClientLevel): AbstractClientPlayer? {
        // Skip the frame while the textured profile resolves; a bare-profile dummy would cache the
        // default skin and never refresh (same uuid), whereas retrying picks up textures once ready.
        val profile = texturedProfile(mc) ?: return null
        dummy?.let { if (dummyProfileId == profile.id && it.level() === level) return it }
        return runCatching { RemotePlayer(level, profile) }.getOrNull()?.also {
            it.id = PREVIEW_ENTITY_ID
            dummy = it
            dummyProfileId = profile.id
        }
    }

    private fun bindEquipment(player: AbstractClientPlayer, source: PlayerPreviewSource) {
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive ->
                (Minecraft.getInstance().player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`()
        } ?: return
        equipmentByEntityId[player.id] = equipment
    }

    private fun readback(colorTex: com.mojang.blaze3d.textures.GpuTexture, w: Int, h: Int, key: Any) {
        val device = RenderSystem.getDevice()
        //? if >= 26.2 {
        /*val pixelSize = colorTex.format.blockSize()
        *///?} else {
        val pixelSize = colorTex.format.pixelSize()
        //?}
        //? if >= 1.21.11 {
        val size = w.toLong() * h.toLong() * pixelSize
        val offset = 0L
        //?} else {
        /*val size = w * h * pixelSize
        val offset = 0
        *///?}
        val buffer = device.createBuffer({ "polyplus_preview_readback" }, GpuBuffer.USAGE_MAP_READ or GpuBuffer.USAGE_COPY_DST, size)
        device.createCommandEncoder().copyTextureToBuffer(colorTex, buffer, offset, Runnable {
            runCatching {
                //? if >= 26.2 {
                /*val mapped = buffer.map(true, false)
                *///?} else {
                val mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)
                //?}
                try {
                    latestByKey[key] = toImageBitmap(mapped.data(), w, h, pixelSize)
                } finally {
                    mapped.close()
                }
            }.onFailure { LOG.error("[preview] readback failed", it) }
            buffer.close()
        }, 0)
    }

    private fun toImageBitmap(data: java.nio.ByteBuffer, w: Int, h: Int, pixelSize: Int): ImageBitmap {
        val out = ByteArray(w * h * 4)
        val fade = edgeFadeColumns(w)
        val rowFade = topFadeRows(h)
        for (y in 0 until h) {
            val outRow = h - 1 - y
            val dstRow = outRow * w
            val srcRow = y * w
            val rf = rowFade[outRow]
            for (x in 0 until w) {
                val si = (srcRow + x) * pixelSize
                val di = (dstRow + x) * 4
                val f = fade[x] * rf
                out[di] = scaleByte(data.get(si + 2).toInt() and 0xFF, f)
                out[di + 1] = scaleByte(data.get(si + 1).toInt() and 0xFF, f)
                out[di + 2] = scaleByte(data.get(si).toInt() and 0xFF, f)
                out[di + 3] = scaleByte(data.get(si + 3).toInt() and 0xFF, f)
            }
        }
        return SkiaImage.makeRaster(ImageInfo.makeN32Premul(w, h), out, w * 4).toComposeImageBitmap()
    }

    //?}

    //? if >= 1.21.5 && < 1.21.8 {
    /*private fun testPattern(w: Int, h: Int, yawDeg: Float): ImageBitmap {
        val bytes = ByteArray(w * h * 4)
        for (i in bytes.indices step 4) {
            bytes[i] = 60; bytes[i + 1] = 40; bytes[i + 2] = 30; bytes[i + 3] = 0xFF.toByte()
        }
        return SkiaImage.makeRaster(ImageInfo.makeN32Premul(w, h), bytes, w * 4).toComposeImageBitmap()
    }*///?}

    //? if >= 1.21.1 && < 1.21.5 {
    /*private val LEGACY_LOG = org.slf4j.LoggerFactory.getLogger("polyplus/preview")
    private const val LEGACY_MAX_DIM = 512
    private const val LEGACY_PREVIEW_ENTITY_ID = Int.MIN_VALUE + 1
    private var legacyTarget: TextureTarget? = null
    //? if < 1.21.4 {
    /*private var legacyDummy: PreviewRemotePlayer? = null
    *///?}

    private fun ensureLegacyTarget(w: Int, h: Int): TextureTarget {
        val existing = legacyTarget
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.destroyBuffers()
        //? if >= 1.21.4 {
        return TextureTarget(w, h, true).also { legacyTarget = it }
        //?}
        //? if < 1.21.4 {
        /*return TextureTarget(w, h, true, Minecraft.ON_OSX).also { legacyTarget = it }
        *///?}
    }

    //? if < 1.21.4 {
    /*// Rebuild when the backing level changes (live world <-> standalone preview level) so
    private fun legacyDummy(mc: Minecraft, level: ClientLevel): PreviewRemotePlayer? {
        legacyDummy?.let { if (it.level() === level) return it }
        return runCatching { PreviewRemotePlayer(level, mc.gameProfile) }.getOrNull()?.also {
            it.id = LEGACY_PREVIEW_ENTITY_ID
            legacyDummy = it
        }
    }

    private class PreviewRemotePlayer(level: ClientLevel, profile: GameProfile) : RemotePlayer(level, profile) {
        @JvmField var skinOverride: PlayerSkin? = null
        override fun getSkin(): PlayerSkin = skinOverride ?: DefaultPlayerSkin.get(uuid)
        override fun isSpectator(): Boolean = false
        override fun isCreative(): Boolean = false
        override fun getPlayerInfo(): PlayerInfo? = null
        override fun isModelPartShown(part: PlayerModelPart): Boolean = true
    }

    private object PreviewWorld {
        private var cached: ClientLevel? = null
        private var cachedCamera: Camera? = null

        fun level(): ClientLevel? {
            cached?.let { return it }
            return runCatching { build() }
                .onFailure { LEGACY_LOG.error("[preview] standalone level build failed", it) }
                .getOrNull()?.also { cached = it }
        }

        fun camera(): Camera {
            cachedCamera?.let { return it }
            val unsafe = UNSAFE
            val cam = unsafe.allocateInstance(Camera::class.java) as Camera
            val field = Camera::class.java.getDeclaredField("position")
            unsafe.putObject(cam, unsafe.objectFieldOffset(field), Vec3(0.0, 1.0E9, 0.0))
            cachedCamera = cam
            return cam
        }

        private fun build(): ClientLevel {
            val mc = Minecraft.getInstance()
            val frozen = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
            val registries = PreviewRegistryAccess(
                frozen,
                mapOf(
                    Registries.DAMAGE_TYPE to damageRegistry(),
                    Registries.BIOME to biomeRegistry(),
                ),
            )
            val dimHolder: Holder<DimensionType> = RegistrySetBuilder()
                .add(Registries.DIMENSION_TYPE, DimensionTypes::bootstrap)
                .build(frozen)
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(BuiltinDimensionTypes.OVERWORLD)
            val data = ClientLevel.ClientLevelData(Difficulty.NORMAL, false, true)
            return ClientLevel(
                stubConnection(registries), data, Level.OVERWORLD, dimHolder, 2, 2,
                { InactiveProfiler.INSTANCE }, mc.levelRenderer, false, 0L,
            )
        }

        private fun damageRegistry(): Registry<DamageType> {
            val registry = MappedRegistry(Registries.DAMAGE_TYPE, com.mojang.serialization.Lifecycle.stable())
            for (field in DamageTypes::class.java.declaredFields) {
                if (!java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                if (!ResourceKey::class.java.isAssignableFrom(field.type)) continue
                @Suppress("UNCHECKED_CAST")
                val key = field.get(null) as ResourceKey<DamageType>
                Registry.register(registry, key, DamageType(key.location().path, DamageScaling.NEVER, 0f))
            }
            registry.freeze()
            return registry
        }

        private fun biomeRegistry(): Registry<Biome> {
            val registry = MappedRegistry(Registries.BIOME, com.mojang.serialization.Lifecycle.stable())
            val biome = Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8f)
                .downfall(0.4f)
                .specialEffects(
                    BiomeSpecialEffects.Builder()
                        .fogColor(0xC0D8FF).waterColor(0x3F76E4).waterFogColor(0x050533).skyColor(0x78A7FF)
                        .build(),
                )
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build()
            Registry.register(registry, Biomes.PLAINS, biome)
            registry.freeze()
            return registry
        }

        private fun stubConnection(registries: RegistryAccess.Frozen): ClientPacketListener {
            val unsafe = UNSAFE
            val stub = unsafe.allocateInstance(ClientPacketListener::class.java) as ClientPacketListener
            val field = ClientPacketListener::class.java.getDeclaredField("registryAccess")
            unsafe.putObject(stub, unsafe.objectFieldOffset(field), registries)
            return stub
        }
    }

    private class PreviewRegistryAccess(
        private val base: RegistryAccess.Frozen,
        private val overrides: Map<ResourceKey<*>, Registry<*>>,
    ) : RegistryAccess.Frozen {
        override fun <E> registry(key: ResourceKey<out Registry<out E>>): java.util.Optional<Registry<E>> {
            overrides[key]?.let {
                @Suppress("UNCHECKED_CAST")
                return java.util.Optional.of(it as Registry<E>)
            }
            return base.registry(key)
        }

        override fun registries(): java.util.stream.Stream<RegistryAccess.RegistryEntry<*>> = base.registries()
    }

    private val UNSAFE: sun.misc.Unsafe by lazy {
        val f = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        f.get(null) as sun.misc.Unsafe
    }

    private fun capeOverrideLegacy(source: PlayerPreviewSource): ResourceLocation? = when (source) {
        is PlayerPreviewSource.Override -> source.capeTexture
        PlayerPreviewSource.LocalLive ->
            org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog.localEquipped().cape?.let {
                org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.getCapeResource(it)
            }
    }

    private fun withCapeLegacy(skin: PlayerSkin, cape: ResourceLocation): PlayerSkin =
        PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure())

    private fun bindLegacyEquipment(player: AbstractClientPlayer, source: PlayerPreviewSource) {
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            // Off-world (main menu) mc.player is null, so fall back to the catalog's locally
            // equipped set — otherwise cosmetics wouldn't render on the preview there.
            PlayerPreviewSource.LocalLive ->
                (Minecraft.getInstance().player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`()
                    ?: legacyLocalEquipment()
        } ?: return
        equipmentByEntityId[player.id] = equipment
    }

    private val legacyLoadAttempted =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<Int, Boolean>())

    private var cachedLegacyEquipment: CosmeticEquipment? = null
    private var cachedLegacyKey: List<String>? = null

    private fun legacyLocalEquipment(): CosmeticEquipment {
        val ids = org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog.localEquipped().ids()
        val resolved = ids.map { id -> org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.getAttachedCosmetic(id) }
        val key = ids.mapIndexed { i, id -> if (resolved[i] != null) "$id" else "$id:pending" }
        cachedLegacyEquipment?.let { if (cachedLegacyKey == key) return it }

        val equipment = CosmeticEquipment()
        ids.forEachIndexed { i, id ->
            val attached = resolved[i]
            if (attached != null) {
                equipment.equip(attached)
            } else if (legacyLoadAttempted.add(id)) {
                org.polyfrost.polyplus.client.PolyPlusClient.SCOPE.launch {
                    runCatching { org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.ensureCosmeticLoaded(id) }
                }
            }
        }
        cachedLegacyEquipment = equipment
        cachedLegacyKey = key
        return equipment
    }
    *///?}

    //? if >= 1.21.4 {
    private fun legacySkin(mc: Minecraft): PlayerSkin? {
        val profile = mc.gameProfile
        return runCatching { mc.skinManager.getInsecureSkin(profile) }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(profile) }.getOrNull()
    }

    private fun legacyPlayerRenderer(mc: Minecraft, skin: PlayerSkin): PlayerRenderer? {
        val map = (mc.entityRenderDispatcher as org.polyfrost.polyplus.mixin.client.cosmetics.EntityRenderDispatcherPlayerAccessor)
            .`polyplus$playerRenderers`()
        return (map[skin.model()] ?: map.values.firstOrNull()) as? PlayerRenderer
    }

    private fun legacyState(skin: PlayerSkin, yawDeg: Float): PlayerRenderState = PlayerRenderState().apply {
        (this as? org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(org.polyfrost.polyplus.client.emotes.playback.EmoteController())
        applyModCompat(this)
        this.skin = skin
        showHat = true
        showJacket = true
        showLeftSleeve = true
        showRightSleeve = true
        showLeftPants = true
        showRightPants = true
        showCape = true
        id = LEGACY_PREVIEW_ENTITY_ID
        bodyRot = yawDeg + 180f
        yRot = 0f
        xRot = 0f
        scale = 1f
        boundingBoxWidth = 0.6f
        boundingBoxHeight = 1.8f
        ageInTicks = (System.nanoTime() / 50_000_000L).toFloat()
    }
    //?}

    private fun renderLegacySceneIntoTarget(source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float): TextureTarget? {
        val mc = Minecraft.getInstance()

        //? if >= 1.21.4 {
        val skin = legacySkin(mc) ?: return null
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive ->
                (mc.player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`() ?: CosmeticEquipment()
        }
        equipmentByEntityId[LEGACY_PREVIEW_ENTITY_ID] = equipment
        val state = legacyState(skin, yawDeg)
        val renderer = legacyPlayerRenderer(mc, skin) ?: return null
        //?}
        //? if < 1.21.4 {
        /*// 1.21.1 has no render states: it must render a real entity, which needs a client
        val level = mc.level ?: PreviewWorld.level() ?: return null
        val skin = mc.skinManager.getInsecureSkin(mc.gameProfile) ?: DefaultPlayerSkin.get(mc.gameProfile)
        val player = legacyDummy(mc, level) ?: return null
        val cape = capeOverrideLegacy(source)
            ?: org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache.getCapeTexture(mc.gameProfile.id)
        player.skinOverride = cape?.let { withCapeLegacy(skin, it) } ?: skin
        bindLegacyEquipment(player, source)
        player.setYRot(0f); player.yRotO = 0f
        player.yBodyRot = yawDeg; player.yBodyRotO = yawDeg
        player.yHeadRot = yawDeg; player.yHeadRotO = yawDeg
        player.setXRot(0f); player.xRotO = 0f
        player.setPos(0.0, 0.0, 0.0)
        player.xo = 0.0; player.yo = 0.0; player.zo = 0.0
        player.xCloak = 0.0; player.yCloak = 0.0; player.zCloak = 0.0
        player.xCloakO = 0.0; player.yCloakO = 0.0; player.zCloakO = 0.0
        *///?}

        val fbo = ensureLegacyTarget(w, h)
        fbo.setClearColor(0f, 0f, 0f, 0f)
        //? if >= 1.21.4 {
        fbo.clear()
        //?}
        //? if < 1.21.4 {
        /*fbo.clear(Minecraft.ON_OSX)
        *///?}
        fbo.bindWrite(true)

        RenderSystem.backupProjectionMatrix()
        val ortho = Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -1000f, 1000f)
        //? if >= 1.21.4 {
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC)
        //?}
        //? if < 1.21.4 {
        /*RenderSystem.setProjectionMatrix(ortho, VertexSorting.ORTHOGRAPHIC_Z)
        *///?}

        val bbH = 1.8f
        val scale = h * modelScale
        val pose = PoseStack()
        pose.translate(w / 2f, h * verticalAnchor, 0f)
        pose.scale(scale, scale, -scale)
        pose.translate(0f, bbH / 2f, 0f)
        pose.mulPose(Quaternionf().rotateZ(Math.PI.toFloat()))

        Lighting.setupForEntityInInventory()
        val dispatcher = mc.entityRenderDispatcher
        dispatcher.setRenderShadow(false)
        val bufferSource = mc.renderBuffers().bufferSource()
        //? if < 1.21.4 {
        /*val prevCamera = dispatcher.camera
        *///?}
        val realMainTarget = mc.mainRenderTarget
        (mc as org.polyfrost.polyplus.mixin.client.MinecraftMainRenderTargetAccessor)
            .`polyplus$setMainRenderTarget`(fbo)
        try {
            //? if >= 1.21.4 {
            renderer.render(state, pose, bufferSource, 0xF000F0)
            //?}
            //? if < 1.21.4 {
            /*// Always swap in the far-away stand-in camera (not only off-world): in a live
            dispatcher.camera = PreviewWorld.camera()
            dispatcher.overrideCameraOrientation(Quaternionf().rotateY(Math.PI.toFloat()))
            dispatcher.render(player, 0.0, 0.0, 0.0, 0f, 1f, pose, bufferSource, 0xF000F0)
            *///?}
            fbo.bindWrite(true)
            bufferSource.endBatch()
        } finally {
            (mc as org.polyfrost.polyplus.mixin.client.MinecraftMainRenderTargetAccessor)
                .`polyplus$setMainRenderTarget`(realMainTarget)
            dispatcher.setRenderShadow(true)
            //? if < 1.21.4 {
            /*dispatcher.camera = prevCamera
            *///?}
            RenderSystem.restoreProjectionMatrix()
            fbo.unbindWrite()
            mc.mainRenderTarget.bindWrite(true)
        }

        return fbo
    }

    private fun renderLegacy(source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, key: Any) {
        val fbo = renderLegacySceneIntoTarget(source, yawDeg, w, h, modelScale, verticalAnchor) ?: return
        readbackLegacy(fbo, w, h, key)
    }

    @JvmStatic
    fun renderOverlayEntry(target: RenderTarget, e: PlayerPreviewOverlay.Entry, yawDeg: Float, pitchDeg: Float, rectX: Int, rectY: Int, rectW: Int, rectH: Int) {
        if (rectW <= 0 || rectH <= 0) return
        val fit = minOf(1f, LEGACY_MAX_DIM.toFloat() / maxOf(rectW, rectH))
        val w = (rectW * fit).toInt().coerceAtLeast(1)
        val h = (rectH * fit).toInt().coerceAtLeast(1)
        val fbo = renderLegacySceneIntoTarget(e.source, yawDeg, w, h, e.modelScale, e.verticalAnchor) ?: return
        if (java.lang.Boolean.getBoolean("pp.overlay.nocomposite")) return
        compositeOntoTargetLegacy(target, fbo, rectX, rectY, rectW, rectH, e.fadeEdges, e.bottomFade, e.opacity)
    }

    private fun compositeOntoTargetLegacy(target: RenderTarget, fbo: TextureTarget, x: Int, y: Int, w: Int, h: Int, fadeEdges: Boolean, bottomFade: Float, opacity: Float) {
        val fbW = target.width
        val fbH = target.height
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0)
        com.mojang.blaze3d.platform.GlStateManager._viewport(0, 0, fbW, fbH)
        RenderSystem.backupProjectionMatrix()
        val ortho = Matrix4f().setOrtho(0f, fbW.toFloat(), fbH.toFloat(), 0f, -1000f, 1000f)
        //? if >= 1.21.4 {
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC)
        //?}
        //? if < 1.21.4 {
        /*RenderSystem.setProjectionMatrix(ortho, com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z)
        *///?}
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        //? if >= 1.21.4 {
        RenderSystem.setShader(net.minecraft.client.renderer.CoreShaders.POSITION_TEX_COLOR)
        //?}
        //? if < 1.21.4 {
        /*RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        *///?}
        RenderSystem.setShaderTexture(0, fbo.colorTextureId)
        try {
            val bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
            buildFadeQuad(bb, x, y, w, h, fadeEdges, bottomFade, opacity)
            BufferUploader.drawWithShader(bb.buildOrThrow())
        } finally {
            RenderSystem.restoreProjectionMatrix()
            RenderSystem.enableDepthTest()
            RenderSystem.enableCull()
            RenderSystem.disableBlend()
        }
    }

    private fun readbackLegacy(fbo: TextureTarget, w: Int, h: Int, key: Any) {
        fbo.bindRead()
        val img = NativeImage(w, h, false)
        img.downloadTexture(0, false)
        fbo.unbindRead()
        try {
            val out = ByteArray(w * h * 4)
            val fade = edgeFadeColumns(w)
            val rowFade = topFadeRows(h)
            for (y in 0 until h) {
                val outRow = h - 1 - y
                val dstRow = outRow * w
                val rf = rowFade[outRow]
                for (x in 0 until w) {
                    //? if >= 1.21.4 {
                    val px = img.getPixel(x, y)
                    //?}
                    //? if < 1.21.4 {
                    /*val px = img.getPixelRGBA(x, y)
                    *///?}
                    val di = (dstRow + x) * 4
                    val f = fade[x] * rf
                    out[di] = scaleByte((px ushr 16) and 0xFF, f)
                    out[di + 1] = scaleByte((px ushr 8) and 0xFF, f)
                    out[di + 2] = scaleByte(px and 0xFF, f)
                    out[di + 3] = scaleByte((px ushr 24) and 0xFF, f)
                }
            }
            latestByKey[key] = SkiaImage.makeRaster(ImageInfo.makeN32Premul(w, h), out, w * 4).toComposeImageBitmap()
        } finally {
            img.close()
        }
    }
    *///?}
}
//?}
