package org.polyfrost.polyplus.client.gui.preview

//? if > 1.8.9 {
import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.compose.backend.asComposeImageBitmap
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.DefaultPlayerSkin
import org.polyfrost.compose.render.PolyBitmap
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.LocalAccountSkin
import org.polyfrost.polyplus.client.cosmetics.LocalSkinOverride
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.Easing
import org.polyfrost.polyplus.client.utils.rotateBy
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

//? if >= 1.21.8
import org.polyfrost.polyplus.mixin.client.access.LightingInvoker
//? if < 1.21.10
/*import org.polyfrost.polyplus.client.lobby.LobbyPipelines*/
//? if < 26.2 && > 1.8.9
/*import org.joml.Vector3f*/
//? if >= 26.3 {
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.textures.GpuTexture
import com.mojang.renderpearl.api.textures.GpuTextureView
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import java.util.OptionalDouble
//?}

//? if >= 26.2 {
import net.minecraft.client.renderer.Projection
import net.minecraft.client.renderer.SubmitNodeStorage
import org.joml.Vector4f
//?}

//? if >= 26.1 {
import net.minecraft.client.renderer.ProjectionMatrixBuffer
import net.minecraft.client.renderer.state.level.CameraRenderState
//?}

//? if >= 1.21.10 {
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.core.ClientAsset
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.PlayerModelPart as SkinLayer
import net.minecraft.world.entity.player.PlayerSkin
//?}

//? if >= 1.21.8 {
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.pets.PetAsset
import org.polyfrost.polyplus.client.pets.PetAssets
import org.polyfrost.polyplus.client.pets.PetPreview
import java.util.UUID
//?}

//? if >= 1.21.4 {
import com.mojang.blaze3d.ProjectionType
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import org.polyfrost.polyplus.client.cosmetics.access.AvatarEmoteRenderAccess
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalsRenderAccess
import org.polyfrost.polyplus.client.emotes.playback.EmoteController
//?}

//? if < 1.21.8 || >= 26.1 {
import org.joml.Matrix4f
//?}

//? if = 1.21.1 || >= 1.21.8 {
import com.mojang.authlib.GameProfile
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.player.RemotePlayer
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.util.Collections
import kotlinx.coroutines.launch
//?}

//? if = 1.21.1 || >= 1.21.10 {
import net.minecraft.world.phys.Vec3
//?}

//? if = 1.21.1 || >= 26.3 {
import java.util.Optional
//?}

//? if = 26.2 {
/*import com.mojang.blaze3d.GpuFormat
*///?}

//? if >= 1.21.8 && < 26.3 {
/*import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.textures.GpuTexture
*///?}

//? if >= 1.21.10 && < 26.1 {
/*import net.minecraft.client.renderer.state.CameraRenderState
*///?}

//? if >= 1.21.8 && < 26.1 {
/*import net.minecraft.client.renderer.CachedOrthoProjectionMatrixBuffer
*///?}

//? if = 1.21.8 {
/*import net.minecraft.client.renderer.entity.state.PlayerRenderState as AvatarRenderState
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.player.PlayerModelPart as SkinLayer
*///?}

//? if < 1.21.10 {
/*import net.minecraft.client.resources.PlayerSkin
*///?}

//? if >= 1.21.4 && < 1.21.8 {
/*import net.minecraft.client.renderer.entity.player.PlayerRenderer
import net.minecraft.client.renderer.entity.state.PlayerRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.PlayerModelPart
import org.polyfrost.polyplus.mixin.client.cosmetics.EntityRenderDispatcherAccessor
*///?}

//? if < 1.21.8 {
/*import net.minecraft.world.entity.HumanoidArm
import org.polyfrost.polyplus.client.pets.PetPreview
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
*///?}

//? if = 1.21.5 {
/*import com.mojang.blaze3d.buffers.BufferType
import com.mojang.blaze3d.buffers.BufferUsage
*///?}

//? if < 1.21.5 {
/*import com.mojang.blaze3d.platform.NativeImage
import org.polyfrost.compose.minecraft.render.RenderTargetFbo
*///?}

//? if = 1.21.4 {
/*import net.minecraft.util.ARGB
*///?}

//? if = 1.21.1 {
/*import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.serialization.Lifecycle
import net.minecraft.client.Camera
import net.minecraft.client.ClientRecipeBook
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.Holder
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry
import net.minecraft.core.RegistryAccess
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.DimensionTypes
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.stats.StatsCounter
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
import net.minecraft.world.scores.Scoreboard
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.mixin.client.access.CameraAccessor
import org.polyfrost.polyplus.mixin.client.access.ClientPacketListenerAccessor
import sun.misc.Unsafe
import java.lang.reflect.Modifier
import java.util.stream.Stream
*///?}

object PlayerPreviewRenderer {
    private val equipmentByEntityId = ConcurrentHashMap<Int, CosmeticEquipment>()
    private val clockStart = System.nanoTime()
    private const val NANOS_PER_TICK = 50_000_000.0
    private const val PREVIEW_DEPTH = 450f

    private fun previewTicks(): Float = ((System.nanoTime() - clockStart) / NANOS_PER_TICK).toFloat()
    private val tintByEntityId = ConcurrentHashMap<Int, PreviewTint>()

    @JvmStatic
    fun previewEquipment(entityId: Int): CosmeticEquipment? = if (equipmentByEntityId.isEmpty()) null else equipmentByEntityId[entityId]

    fun heldCosmetics() = equipmentByEntityId.values.flatMap { equipment -> equipment.equipped().map { it.cosmetic } }

    @Volatile
    private var renderingPreview = false

    @JvmStatic
    fun isRenderingPreview(): Boolean = renderingPreview

    @Volatile
    private var directPreview = false

    @JvmStatic
    fun isDirectPreview(): Boolean = directPreview

    @Volatile
    private var buildingLevel = false

    @JvmStatic
    fun isBuildingLevel(): Boolean = buildingLevel

    //? if >= 1.21.10 {
    @Volatile
    private var previewCape: Identifier? = null

    @JvmStatic
    fun previewCapeOverride(): Any? = previewCape
    //?}

    @JvmStatic
    fun previewParticleColor(entityId: Int): Int? {
        if (!equipmentByEntityId.containsKey(entityId)) return null
        val tint = tintByEntityId[entityId]
        return if (tint != null) tint.particleColor else CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())
    }

    private fun bindTint(entityId: Int, source: PlayerPreviewSource) {
        val tint = (source as? PlayerPreviewSource.Override)?.tint
        if (tint != null) tintByEntityId[entityId] = tint else tintByEntityId.remove(entityId)
    }

    private fun previewSkin(source: PlayerPreviewSource): PreviewSkin? =
        (source as? PlayerPreviewSource.Override)?.skin ?: LocalSkinOverride.forPlayer(ClientPlatform.localPlayerUuid())

    private val latestByKey = ConcurrentHashMap<Any, ImageBitmap>()
    // readbacks land frames after capture, so only keys an on-screen preview still retains get cached
    private val retainCounts = ConcurrentHashMap<Any, Int>()

    fun cached(key: Any): ImageBitmap? = latestByKey[key]

    fun retain(key: Any) {
        retainCounts.merge(key, 1, Int::plus)
    }

    fun release(key: Any) {
        if (retainCounts.computeIfPresent(key) { _, n -> (n - 1).takeIf { it > 0 } } == null) latestByKey.remove(key)
        dropHoldersIfIdle()
    }

    @JvmStatic
    fun dropHoldersIfIdle() {
        if (retainCounts.isNotEmpty() || PlayerPreviewOffscreen.hasEntries()) return
        equipmentByEntityId.clear()
        tintByEntityId.clear()
        //? if >= 1.21.8 {
        cachedLocalEquipment = null
        cachedLocalKey = null
        //?}
    }

    private inline fun publish(key: Any, bitmap: () -> ImageBitmap) {
        if (!retainCounts.containsKey(key)) return
        val bmp = bitmap()
        latestByKey[key] = bmp
        // a release between the check above and the write would otherwise leave this entry behind for good
        if (!retainCounts.containsKey(key)) latestByKey.remove(key, bmp)
    }

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
            dropHoldersIfIdle()
        }
        return latestByKey[key]
        //?} else {
        /*val w = widthPx.coerceAtMost(LEGACY_MAX_DIM)
        val h = heightPx.coerceAtMost(LEGACY_MAX_DIM)
        ClientPlatform.runOnMain {
            runCatching { renderLegacy(source, yawDeg, w, h, modelScale, verticalAnchor, key) }
                .onFailure { LEGACY_LOG.error("[preview] legacy capture failed", it) }
            dropHoldersIfIdle()
        }
        return latestByKey[key]
        *///?}
    }

    //? if = 1.21.1 || >= 1.21.8 {
    fun initialize() {
        // the preview avatar pins the whole ClientLevel it was built in, so let go of it once that level is replaced
        eventHandler<TickEvent.End> {
            val level = Minecraft.getInstance().level
            //? if >= 1.21.8 {
            if (dummy?.level() !== level) dummy = null
            //?} else {
            /*val dummyLevel = legacyDummy?.level()
            if (dummyLevel !== level && dummyLevel !== PreviewWorld.cached) legacyDummy = null
            if (standIn?.level() !== PreviewWorld.cached) standIn = null
            *///?}
        }
    }
    //?}

    internal const val EDGE_FADE_FRACTION = 0.18f

    private fun edgeFadeColumns(w: Int): FloatArray {
        val cols = FloatArray(w)
        val fadePx = w * EDGE_FADE_FRACTION
        for (x in 0 until w) {
            val d = minOf(x + 0.5f, w - 0.5f - x) // distance from nearest edge at pixel center
            cols[x] = if (fadePx <= 0f) 1f else smooth(d / fadePx)
        }
        return cols
    }

    private fun scaleByte(v: Int, f: Float): Byte {
        val s = (v * f).toInt()
        return (if (s > 255) 255 else s).toByte()
    }

    internal fun smooth(t: Float): Float = Easing.smootherstep(t)

    private fun toImageBitmap(data: ByteBuffer, w: Int, h: Int, pixelSize: Int): ImageBitmap {
        val out = ByteArray(w * h * 4)
        val fade = edgeFadeColumns(w)
        for (y in 0 until h) {
            val outRow = h - 1 - y
            val dstRow = outRow * w
            val srcRow = y * w
            for (x in 0 until w) {
                val si = (srcRow + x) * pixelSize
                val di = (dstRow + x) * 4
                val f = fade[x]
                out[di] = data.get(si)
                out[di + 1] = data.get(si + 1)
                out[di + 2] = data.get(si + 2)
                out[di + 3] = scaleByte(data.get(si + 3).toInt() and 0xFF, f)
            }
        }
        return PolyBitmap(w, h, out).asComposeImageBitmap()
    }

    private val mountOpacitySetter: MethodHandle? = runCatching {
        val hook = Class.forName("dev.microcontrollers.mountopacity.hook.EntityRenderStateHook")
        MethodHandles.lookup()
            .findVirtual(hook, "mountopacity\$setOpacity", MethodType.methodType(Void.TYPE, Float::class.javaPrimitiveType))
            .asType(MethodType.methodType(Void.TYPE, Any::class.java, Float::class.javaPrimitiveType))
    }.getOrNull()

    private fun applyModCompat(state: Any) {
        mountOpacitySetter?.invokeExact(state, 100f)
    }

    //? if >= 1.21.8 {
    private val LOG = LoggerFactory.getLogger("polyplus/preview")
    private const val MAX_DIM = 512

    private val loggedSkips = HashSet<String>()

    private fun logSkippedFrame(t: Throwable) {
        val top = t.stackTrace.firstOrNull()
        val signature = "${t.javaClass.name}@${top?.className}.${top?.methodName}:${top?.lineNumber}"
        if (loggedSkips.add(signature)) LOG.error("[preview] entity submit failed; skipping frame", t)
        else LOG.debug("[preview] entity submit failed again ({}); skipping frame", signature)
    }

    private var target: TextureTarget? = null
    private var poseTiltDeg = 0f
    private var poseLights: PreviewLights? = null
    //? if >= 26.3 {
    private var passColorView: GpuTextureView? = null
    private var passDepthView: GpuTextureView? = null

    private fun renderFeatures(mc: Minecraft, storage: SubmitNodeStorage) {
        val colorView = passColorView ?: return
        mc.gameRenderer.featureRenderDispatcher().prepareFrame(storage).use { frame ->
            RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                { "polyplus_preview" },
                colorView,
                Optional.empty(),
                passDepthView,
                OptionalDouble.empty(),
            ).use { pass ->
                RenderSystem.bindDefaultUniforms(pass)
                FeatureRenderDispatcher.renderAllFeatures(pass, frame)
            }
        }
    }
    //?}
    private var dummy: AbstractClientPlayer? = null
    private var dummyProfileId: UUID? = null

    @JvmStatic
    fun isPreviewEntity(entity: Any?): Boolean = entity != null && entity === dummy
    //? if < 26.1 {
    /*private val projection by lazy { CachedOrthoProjectionMatrixBuffer("polyplus_preview", -1000f, 1000f, true) }
    *///?}
    //? if >= 26.1 {
    private val projection by lazy { ProjectionMatrixBuffer("polyplus_preview") }
    //?}
    //? if >= 26.1 && < 26.2 {
    /*private fun orthoMatrix(w: Int, h: Int): Matrix4f =
        Matrix4f().setOrtho(0f, w.toFloat(), h.toFloat(), 0f, -1000f, 1000f, RenderSystem.getDevice().isZZeroToOne())
    *///?}
    //? if >= 26.2 {
    private fun orthoProjection(w: Int, h: Int): Projection =
        Projection().apply { setupOrtho(-1000f, 1000f, w.toFloat(), h.toFloat(), true) }
    //?}

    private fun ensureTarget(w: Int, h: Int): TextureTarget {
        val existing = target
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.destroyBuffers()
        //? if >= 26.3 {
        return TextureTarget(
            "polyplus_player_preview", w, h,
            GpuFormat.RGBA8_UNORM,
            GpuFormat.D32_FLOAT,
        ).also { target = it }
        //?} elif >= 26.2 {
        /*return TextureTarget("polyplus_player_preview", w, h, true, GpuFormat.RGBA8_UNORM).also { target = it }
        *///?} else {
        /*return TextureTarget("polyplus_player_preview", w, h, true).also { target = it }
        *///?}
    }

    private fun renderAndReadback(source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, key: Any) {
        val fbo = ensureTarget(w, h)
        if (!renderInto(fbo, source, yawDeg, pitchDeg, w, h, modelScale, verticalAnchor)) return
        val colorTex = fbo.colorTexture ?: return
        readback(colorTex, w, h, key)
    }

    //? if < 1.21.10 {
    /*fun renderInto(fbo: RenderTarget, source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, cameraTiltDeg: Float = 0f, lights: PreviewLights? = null): Boolean {
        LobbyPipelines.previewing = true
        try {
            return drawInto(fbo, source, yawDeg, pitchDeg, w, h, modelScale, verticalAnchor, cameraTiltDeg, lights)
        } finally {
            LobbyPipelines.previewing = false
        }
    }

    private fun drawInto(fbo: RenderTarget, source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, cameraTiltDeg: Float, lights: PreviewLights?): Boolean {
    *///?} else {
    fun renderInto(fbo: RenderTarget, source: PlayerPreviewSource, yawDeg: Float, pitchDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, cameraTiltDeg: Float = 0f, lights: PreviewLights? = null): Boolean {
    //?}
        val mc = Minecraft.getInstance()
        poseTiltDeg = cameraTiltDeg
        poseLights = lights
        val colorTex = fbo.colorTexture ?: return false
        val depthTex = fbo.depthTexture ?: return false
        val colorView = fbo.colorTextureView ?: return false
        val depthView = fbo.depthTextureView ?: return false

        val savedLights = RenderSystem.getShaderLights()
        val savedFog = RenderSystem.getShaderFog()

        //? if >= 26.2 {
        RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, Vector4f(0f, 0f, 0f, 0f), depthTex, 0.0)
        //?} else {
        /*RenderSystem.getDevice().createCommandEncoder()
            .clearColorAndDepthTextures(colorTex, 0x00000000, depthTex, 1.0)
        *///?}
        RenderSystem.backupProjectionMatrix()
        //? if < 26.1 {
        /*RenderSystem.setProjectionMatrix(projection.getBuffer(w.toFloat(), h.toFloat()), ProjectionType.ORTHOGRAPHIC)
        *///?}
        //? if >= 26.2 {
        RenderSystem.setProjectionMatrix(projection.getBuffer(orthoProjection(w, h)), ProjectionType.ORTHOGRAPHIC)
        //?} else {
        /*//? if >= 26.1 {
        RenderSystem.setProjectionMatrix(projection.getBuffer(orthoMatrix(w, h)), ProjectionType.ORTHOGRAPHIC)
        //?}
        *///?}
        //? if >= 26.3 {
        passColorView = colorView
        passDepthView = depthView
        //?} else {
        /*RenderSystem.outputColorTextureOverride = colorView
        RenderSystem.outputDepthTextureOverride = depthView
        *///?}
        //? if >= 26.1 {
        val scissor = RenderSystem.getScissorStateForRenderTypeDraws()
        val hadScissor = scissor.enabled()
        val scX = scissor.x(); val scY = scissor.y(); val scW = scissor.width(); val scH = scissor.height()
        RenderSystem.disableScissorForRenderTypeDraws()
        //?} else {
        /*RenderSystem.disableScissorForRenderTypeDraws()
        *///?}
        //? if >= 26.2 {
        val modelViewStack = RenderSystem.getModelViewStack()
        modelViewStack.pushMatrix()
        modelViewStack.identity()
        //?}
        return try {
            //? if >= 1.21.10 {
            previewCape = capeOverride(source)?.let { ClientAsset.ResourceTexture(it).texturePath() }
            renderingPreview = true
            //?}
            try {
                PolyPlusSentry.handlingLocally {
                    //? if >= 1.21.10 {
                    val level = mc.level
                    if (level != null && mc.cameraEntity != null) renderEntity(mc, level, source, yawDeg, w, h, modelScale, verticalAnchor)
                    else renderDirect(mc, source, yawDeg, w, h, modelScale, verticalAnchor)
                    //?} else {
                    /*renderDirect(mc, source, yawDeg, w, h, modelScale, verticalAnchor)
                    *///?}
                }
                true
            } catch (t: Throwable) {
                logSkippedFrame(t)
                false
            } finally {
                //? if >= 1.21.10 {
                renderingPreview = false
                previewCape = null
                //?}
            }
        } finally {
            //? if >= 26.2 {
            RenderSystem.getModelViewStack().popMatrix()
            //?}
            //? if >= 26.3 {
            passColorView = null
            passDepthView = null
            //?} else {
            /*RenderSystem.outputColorTextureOverride = null
            RenderSystem.outputDepthTextureOverride = null
            *///?}
            RenderSystem.restoreProjectionMatrix()
            savedLights?.let { RenderSystem.setShaderLights(it) }
            savedFog?.let { RenderSystem.setShaderFog(it) }
            //? if >= 26.1 {
            if (hadScissor) RenderSystem.enableScissorForRenderTypeDraws(scX, scY, scW, scH)
            //?}
        }
    }

    private fun previewPose(w: Int, h: Int, modelScale: Float, verticalAnchor: Float, bbH: Float): PoseStack {
        val scale = h * modelScale
        val pose = PoseStack()
        pose.translate(w / 2f, h * verticalAnchor, PREVIEW_DEPTH)
        pose.scale(scale, scale, -scale)
        pose.translate(0f, bbH / 2f, 0f)
        if (poseTiltDeg != 0f) pose.rotateBy(Quaternionf().rotateX(Math.toRadians(poseTiltDeg.toDouble()).toFloat()))
        pose.rotateBy(Quaternionf().rotateZ(Math.PI.toFloat()))
        return pose
    }

    private fun setupUiLighting(mc: Minecraft) {
        val lights = poseLights
        if (lights != null) {
            //? if >= 26.2 {
            val lighting = mc.gameRenderer.lighting()
            (lighting as LightingInvoker).`polyplus$updateBuffer`(Lighting.Entry.LEVEL, lights.key, lights.fill)
            //?} else {
            /*val lighting = mc.gameRenderer.lighting
            (lighting as LightingInvoker).`polyplus$updateBuffer`(Lighting.Entry.LEVEL, Vector3f(lights.key), Vector3f(lights.fill))
            *///?}
            lighting.setupFor(Lighting.Entry.LEVEL)
            return
        }
        //? if >= 26.2 {
        mc.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI)
        //?} else {
        /*mc.gameRenderer.lighting.setupFor(Lighting.Entry.ENTITY_IN_UI)
        *///?}
    }

    //? if >= 1.21.10 {
    private fun submitPreviewFrame(mc: Minecraft, state: AvatarRenderState, source: PlayerPreviewSource, pose: PoseStack, yawDeg: Float) {
        val camera = CameraRenderState().apply {
            orientation = Quaternionf().rotateY(Math.PI.toFloat())
            pos = Vec3.ZERO
            //? if < 26.1 {
            /*entityPos = Vec3.ZERO
            *///?}
        }
        //? if >= 26.3 {
        val submitStorage = SubmitNodeStorage()
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, submitStorage)
        previewPet(source)?.let { PetPreview.submit(it, pose, submitStorage, state.lightCoords, yawDeg, state.mainArm == HumanoidArm.LEFT, state.walkAnimationPos, state.walkAnimationSpeed) }
        renderFeatures(mc, submitStorage)
        //?} elif >= 26.2 {
        /*val features = mc.gameRenderer.featureRenderDispatcher()
        val submitStorage = SubmitNodeStorage()
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, submitStorage)
        previewPet(source)?.let { PetPreview.submit(it, pose, submitStorage, state.lightCoords, yawDeg, state.mainArm == HumanoidArm.LEFT, state.walkAnimationPos, state.walkAnimationSpeed) }
        features.renderAllFeatures(submitStorage)
        *///?} else {
        /*val features = mc.gameRenderer.featureRenderDispatcher
        mc.entityRenderDispatcher.submit(state, camera, 0.0, 0.0, 0.0, pose, features.submitNodeStorage)
        previewPet(source)?.let { PetPreview.submit(it, pose, features.submitNodeStorage, state.lightCoords, yawDeg, state.mainArm == HumanoidArm.LEFT, state.walkAnimationPos, state.walkAnimationSpeed) }
        features.renderAllFeatures()
        mc.renderBuffers().bufferSource().endBatch()
        *///?}
    }
    //?}

    //? if >= 1.21.10 {
    private fun renderEntity(mc: Minecraft, level: ClientLevel, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float) {
        val player = dummy(mc, level) ?: run {
            LOG.debug("[preview] skipping frame: preview avatar not built yet")
            return
        }
        bindEquipment(player, source)
        bindTint(player.id, source)
        player.setYRot(0f); player.yRotO = 0f
        player.yBodyRot = 0f; player.yBodyRotO = 0f
        player.yHeadRot = 0f; player.yHeadRotO = 0f
        player.setXRot(0f); player.xRotO = 0f
        if (mc.entityRenderDispatcher.getRenderer(player) == null) return
        val state = mc.entityRenderDispatcher.extractEntity(player, 1.0f) as? AvatarRenderState ?: return
        state.lightCoords = 0xF000F0
        state.nameTag = null
        state.scoreText = null
        state.ageInTicks = previewTicks()
        // the dummy's skin customisation data is never synced, which hides every outer layer; match directState
        state.showHat = true
        state.showJacket = true
        state.showLeftSleeve = true
        state.showRightSleeve = true
        state.showLeftPants = true
        state.showRightPants = true
        state.showCape = (source !is PlayerPreviewSource.Override || equipmentByEntityId[player.id]?.get(BodySlot.Backpack) == null) &&
            !hidesCape(source)
        applyLocalSkinOptions(mc, state, source)
        previewSkin(source)?.let { state.skin = LocalSkinOverride.withSkin(state.skin, it) }
        capeOverride(source)?.let { state.skin = withCape(state.skin, it) }
        state.bodyRot = yawDeg
        state.yRot = 0f
        state.xRot = 0f
        val bbH = state.boundingBoxHeight / state.scale
        state.boundingBoxWidth /= state.scale
        state.boundingBoxHeight = bbH
        state.scale = 1f
        applyTryOn(state)

        val pose = previewPose(w, h, modelScale, verticalAnchor, bbH)
        setupUiLighting(mc)
        submitPreviewFrame(mc, state, source, pose, yawDeg)
    }
    //?}

    //? if >= 1.21.8 {
    private fun applyLocalSkinOptions(mc: Minecraft, state: AvatarRenderState, source: PlayerPreviewSource) {
        if (source != PlayerPreviewSource.LocalLive) return
        val options = mc.options
        state.showHat = options.isModelPartEnabled(SkinLayer.HAT)
        state.showJacket = options.isModelPartEnabled(SkinLayer.JACKET)
        state.showLeftSleeve = options.isModelPartEnabled(SkinLayer.LEFT_SLEEVE)
        state.showRightSleeve = options.isModelPartEnabled(SkinLayer.RIGHT_SLEEVE)
        state.showLeftPants = options.isModelPartEnabled(SkinLayer.LEFT_PANTS_LEG)
        state.showRightPants = options.isModelPartEnabled(SkinLayer.RIGHT_PANTS_LEG)
        state.showCape = state.showCape && options.isModelPartEnabled(SkinLayer.CAPE)
        //? if >= 1.21.10 {
        state.mainArm = options.mainHand().get()
        //?}
    }
    //?}

    private fun renderDirect(mc: Minecraft, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float) {
        directPreview = true
        try {
            renderDirectState(mc, source, yawDeg, w, h, modelScale, verticalAnchor)
        } finally {
            directPreview = false
        }
    }

    private fun renderDirectState(mc: Minecraft, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float) {
        val accountSkin = localSkin(mc) ?: return
        val baseSkin = previewSkin(source)?.let { LocalSkinOverride.withSkin(accountSkin, it) } ?: accountSkin
        val skin = capeOverride(source)?.let { withCape(baseSkin, it) } ?: baseSkin
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive -> localEquipment()
        }
        equipmentByEntityId[PREVIEW_ENTITY_ID] = equipment
        bindTint(PREVIEW_ENTITY_ID, source)

        val state = directState(skin)
        if (mc.entityRenderDispatcher.getRenderer(state) == null) return
        state.id = PREVIEW_ENTITY_ID
        if (source is PlayerPreviewSource.Override && equipment.get(BodySlot.Backpack) != null) state.showCape = false
        if (hidesCape(source)) state.showCape = false
        //? if >= 1.21.8 {
        applyLocalSkinOptions(mc, state, source)
        //?}
        state.bodyRot = yawDeg
        state.boundingBoxWidth = PLAYER_BB_WIDTH
        state.boundingBoxHeight = PLAYER_BB_HEIGHT
        state.scale = 1f
        applyTryOn(state)
        val bbH = PLAYER_BB_HEIGHT

        val pose = previewPose(w, h, modelScale, verticalAnchor, bbH)
        setupUiLighting(mc)
        //? if >= 1.21.10 {
        submitPreviewFrame(mc, state, source, pose, yawDeg)
        //?} else {
        /*val bufferSource = mc.renderBuffers().bufferSource()
        // render draws a ground shadow that dereferences mc.level and NPEs off-world
        mc.entityRenderDispatcher.setRenderShadow(false)
        try {
            mc.entityRenderDispatcher.render(state, 0.0, 0.0, 0.0, pose, bufferSource, 0xF000F0)
            previewPet(source)?.let { PetPreview.render(it, pose, bufferSource, yawDeg, state.mainArm == HumanoidArm.LEFT, state.walkAnimationPos, state.walkAnimationSpeed) }
            bufferSource.endBatch()
        } finally {
            mc.entityRenderDispatcher.setRenderShadow(true)
        }
        *///?}
    }

    private fun previewPet(source: PlayerPreviewSource): PetAsset? = when (source) {
        is PlayerPreviewSource.Override -> source.pet
        PlayerPreviewSource.LocalLive ->
            CosmeticCatalog.localEquipped().equipped[BodySlot.Pet]
                ?.let { PetAssets.get(it) }
    }

    private val loadAttempted = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    private var cachedLocalEquipment: CosmeticEquipment? = null
    private var cachedLocalKey: List<Any>? = null

    private fun localEquipment(): CosmeticEquipment {
        val worn = CosmeticCatalog.localEquipped().equipped.entries.toList()
        val ids = worn.map { it.value }
        val resolved = worn.map { (slot, id) -> CosmeticAssetCache.getAttachedCosmetic(id)?.copy(slot = slot) }
        val key = worn.mapIndexed { i, (slot, id) -> Triple(slot, id, resolved[i]) }
        cachedLocalEquipment?.let { if (cachedLocalKey == key) return it }

        val equipment = CosmeticEquipment()
        ids.forEachIndexed { i, id ->
            val attached = resolved[i]
            if (attached != null) {
                equipment.equip(attached)
            } else if (loadAttempted.add(id)) {
                PolyPlusClient.SCOPE.launch {
                    runSuspendCatching { CosmeticAssetCache.ensureCosmeticLoaded(id) }
                }
            }
        }
        cachedLocalEquipment = equipment
        cachedLocalKey = key
        return equipment
    }

    private fun localSkin(mc: Minecraft): PlayerSkin? =
        runCatching { LocalAccountSkin.skin() }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(LocalAccountSkin.profile()) }.getOrNull()

    //? if >= 1.21.10 {
    private fun directState(skin: PlayerSkin): AvatarRenderState = AvatarRenderState().apply {
        (this as? AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(EmoteController())
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
        ageInTicks = previewTicks()
    }
    //?} else {
    /*private fun directState(skin: PlayerSkin): AvatarRenderState = AvatarRenderState().apply {
        (this as? AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(EmoteController())
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
        ageInTicks = previewTicks()
    }
    *///?}

    private const val PREVIEW_ENTITY_ID = Int.MIN_VALUE + 1

    private const val PLAYER_BB_WIDTH = 0.6f
    private const val PLAYER_BB_HEIGHT = 1.8f

    private fun dummy(mc: Minecraft, level: ClientLevel): AbstractClientPlayer? {
        // A bare-profile dummy would cache the default skin and never refresh under the same uuid
        val profile = LocalAccountSkin.profile()
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

    private fun readback(colorTex: GpuTexture, w: Int, h: Int, key: Any) {
        val device = RenderSystem.getDevice()
        //? if >= 26.2 {
        val pixelSize = colorTex.format.blockSize()
        //?} else {
        /*val pixelSize = colorTex.format.pixelSize()
        *///?}
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
                val mapped = buffer.map(true, false)
                //?} else {
                /*val mapped = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, true, false)
                *///?}
                try {
                    publish(key) { toImageBitmap(mapped.data(), w, h, pixelSize) }
                } finally {
                    mapped.close()
                }
            }.onFailure { LOG.error("[preview] readback failed", it) }
            buffer.close()
        }, 0)
    }
    //?}

    private fun capeOverride(source: PlayerPreviewSource): Identifier? = when (source) {
        is PlayerPreviewSource.Override ->
            source.capeCosmeticId?.let {
                CosmeticAssetCache.getCapeResource(it)
            } ?: source.mojangCape
        PlayerPreviewSource.LocalLive ->
            if (localEngineCape()) null
            else CosmeticCatalog.localEquipped().cape?.let {
                CosmeticAssetCache.getCapeResource(it)
            } ?: MojangCapeService.localCapeTexture()?.takeIf { mojangCapeChosen() }
    }

    private fun hidesCape(source: PlayerPreviewSource): Boolean = when (source) {
        is PlayerPreviewSource.Override -> source.capeCosmeticId == null && source.mojangCape == null ||
            source.capeCosmeticId?.let(CosmeticAssetCache::isEngineCape) == true
        PlayerPreviewSource.LocalLive -> localEngineCape() ||
            CosmeticCatalog.localEquipped().cape == null && MojangCapeService.localCapeHidden() && mojangCapeChosen()
    }

    private fun localEngineCape(): Boolean = CosmeticCatalog.localEquipped().cape?.let(CosmeticAssetCache::isEngineCape) == true

    private fun mojangCapeChosen(): Boolean = MojangCapeService.overridesLocalCape(ClientPlatform.localPlayerUuid())

    //? if >= 1.21.10 {
    private fun withCape(skin: PlayerSkin, cape: Identifier): PlayerSkin =
        PlayerSkin(skin.body(), ClientAsset.ResourceTexture(cape), skin.elytra(), skin.model(), skin.secure())
    //?} else {
    /*private fun withCape(skin: PlayerSkin, cape: Identifier): PlayerSkin =
        PlayerSkin(skin.texture(), skin.textureUrl(), cape, skin.elytraTexture(), skin.model(), skin.secure())
    *///?}

    //? if >= 1.21.4 {
    private fun applyTryOn(state: HumanoidRenderState) {
        PreviewTryOn.yaw = state.bodyRot
        (state as? PlayerSignalsRenderAccess)?.`polyplus$setSignals`(PreviewTryOn.previewSignals())
        val tryOn = PreviewTryOn.current ?: return
        if (tryOn.crouching()) state.isCrouching = true
        val legs = tryOn.walkSpeed()
        if (legs > 0f) {
            state.walkAnimationSpeed = legs
            state.walkAnimationPos = previewTicks() * legs * WALK_CYCLE_PER_TICK
        }
    }

    private const val WALK_CYCLE_PER_TICK = 0.6f
    //?}

    //? if < 1.21.8 {
    /*private val LEGACY_LOG = LoggerFactory.getLogger("polyplus/preview")
    private const val LEGACY_MAX_DIM = 512
    private const val LEGACY_PREVIEW_ENTITY_ID = Int.MIN_VALUE + 1
    private var legacyTarget: TextureTarget? = null
    //? if < 1.21.4 {
    /*private var legacyDummy: PreviewRemotePlayer? = null

    @JvmStatic
    fun isPreviewEntity(entity: Any?): Boolean = entity != null && entity === legacyDummy
    *///?}

    private fun ensureLegacyTarget(w: Int, h: Int): TextureTarget {
        val existing = legacyTarget
        if (existing != null && existing.width == w && existing.height == h) return existing
        existing?.destroyBuffers()
        //? if = 1.21.5 {
        return TextureTarget("polyplus_player_preview", w, h, true).also { legacyTarget = it }
        //?} elif = 1.21.4 {
        /*return TextureTarget(w, h, true).also { legacyTarget = it }
        *///?} else
        //return TextureTarget(w, h, true, Minecraft.ON_OSX).also { legacyTarget = it }
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
        @JvmField var localOptions: Boolean = false
        override fun getSkin(): PlayerSkin = skinOverride ?: DefaultPlayerSkin.get(uuid)
        override fun isSpectator(): Boolean = false
        override fun isCreative(): Boolean = false
        override fun getPlayerInfo(): PlayerInfo? = null
        override fun isModelPartShown(part: PlayerModelPart): Boolean =
            !localOptions || Minecraft.getInstance().options.isModelPartEnabled(part)
        override fun getMainArm(): HumanoidArm =
            if (localOptions) Minecraft.getInstance().options.mainHand().get() else HumanoidArm.RIGHT
    }

    private var standIn: PreviewStandIn? = null
    private var standInFailed = false

    private fun swapInStandIn(mc: Minecraft, level: ClientLevel): Boolean {
        if (mc.player != null || LobbyScene.terrainActive()) return false
        val preview = PreviewWorld.cached ?: return false
        if (level !== preview || (mc.level != null && mc.level !== preview)) return false
        mc.player = standIn(mc, preview) ?: return false
        return true
    }

    private fun standIn(mc: Minecraft, level: ClientLevel): PreviewStandIn? {
        val profile = mc.gameProfile
        standIn?.let { if (it.level() === level && it.uuid == profile.id) return it }
        if (standInFailed) return null
        val connection = PreviewWorld.connection ?: return null
        return runCatching {
            setField(ClientPacketListener::class.java, connection, GameProfile::class.java, profile)
            PreviewStandIn(mc, level, connection).also {
                setField(LocalPlayer::class.java, it, ClientPacketListener::class.java, null)
                it.id = LEGACY_PREVIEW_ENTITY_ID + 1
            }
        }.onFailure {
            standInFailed = true
            LEGACY_LOG.warn("[preview] stand-in player build failed", it)
        }.getOrNull()?.also { standIn = it }
    }

    private fun setField(owner: Class<*>, target: Any, type: Class<*>, value: Any?) {
        val field = owner.declaredFields.single { !Modifier.isStatic(it.modifiers) && it.type == type }
        field.isAccessible = true
        field.set(target, value)
    }

    private class PreviewStandIn(mc: Minecraft, level: ClientLevel, connection: ClientPacketListener) :
        LocalPlayer(mc, level, connection, StatsCounter(), ClientRecipeBook(), false, false) {
        override fun tick() {}
        override fun aiStep() {}
        override fun isSpectator(): Boolean = false
        override fun isCreative(): Boolean = false
        override fun getPlayerInfo(): PlayerInfo? = null
    }

    private fun withoutCape(skin: PlayerSkin): PlayerSkin =
        PlayerSkin(skin.texture(), skin.textureUrl(), null, skin.elytraTexture(), skin.model(), skin.secure())

    private object PreviewWorld {
        var cached: ClientLevel? = null
            private set
        var connection: ClientPacketListener? = null
            private set
        private var cachedCamera: Camera? = null

        fun level(): ClientLevel? {
            cached?.let { return it }
            buildingLevel = true
            return runCatching { build() }
                .also { buildingLevel = false }
                .onFailure { LEGACY_LOG.error("[preview] standalone level build failed", it) }
                .getOrNull()?.also { cached = it }
        }

        fun camera(): Camera {
            cachedCamera?.let { return it }
            val cam = UNSAFE.allocateInstance(Camera::class.java) as Camera
            (cam as CameraAccessor).`polyplus$setPosition`(Vec3(0.0, 1.0E9, 0.0))
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
            val stub = stubConnection(registries)
            return ClientLevel(
                stub, data, Level.OVERWORLD, dimHolder, 2, 2,
                { InactiveProfiler.INSTANCE }, mc.levelRenderer, false, 0L,
            ).also { connection = stub }
        }

        private fun damageRegistry(): Registry<DamageType> {
            val registry = MappedRegistry(Registries.DAMAGE_TYPE, Lifecycle.stable())
            for (field in DamageTypes::class.java.declaredFields) {
                if (!Modifier.isStatic(field.modifiers)) continue
                if (!ResourceKey::class.java.isAssignableFrom(field.type)) continue
                @Suppress("UNCHECKED_CAST")
                val key = field.get(null) as ResourceKey<DamageType>
                Registry.register(registry, key, DamageType(key.location().path, DamageScaling.NEVER, 0f))
            }
            registry.freeze()
            return registry
        }

        private fun biomeRegistry(): Registry<Biome> {
            val registry = MappedRegistry(Registries.BIOME, Lifecycle.stable())
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
            val stub = UNSAFE.allocateInstance(ClientPacketListener::class.java) as ClientPacketListener
            (stub as ClientPacketListenerAccessor).`polyplus$setRegistryAccess`(registries)
            (stub as ClientPacketListenerAccessor).`polyplus$setScoreboard`(Scoreboard())
            return stub
        }
    }

    private class PreviewRegistryAccess(
        private val base: RegistryAccess.Frozen,
        private val overrides: Map<ResourceKey<*>, Registry<*>>,
    ) : RegistryAccess.Frozen {
        override fun <E> registry(key: ResourceKey<out Registry<out E>>): Optional<Registry<E>> {
            overrides[key]?.let {
                @Suppress("UNCHECKED_CAST")
                return Optional.of(it as Registry<E>)
            }
            return base.registry(key)
        }

        override fun registries(): Stream<RegistryAccess.RegistryEntry<*>> = base.registries()
    }

    private val UNSAFE: Unsafe by lazy {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe")
        f.isAccessible = true
        f.get(null) as Unsafe
    }

    private fun bindLegacyEquipment(player: AbstractClientPlayer, source: PlayerPreviewSource) {
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            // Off-world mc.player is null so fall back to the catalog's locally equipped set
            PlayerPreviewSource.LocalLive ->
                (Minecraft.getInstance().player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`()
                    ?: legacyLocalEquipment()
        } ?: return
        equipmentByEntityId[player.id] = equipment
        bindTint(player.id, source)
    }

    private val legacyLoadAttempted =
        Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())

    private var cachedLegacyEquipment: CosmeticEquipment? = null
    private var cachedLegacyKey: List<Any>? = null

    private fun legacyLocalEquipment(): CosmeticEquipment {
        val worn = CosmeticCatalog.localEquipped().equipped.entries.toList()
        val ids = worn.map { it.value }
        val resolved = worn.map { (slot, id) -> CosmeticAssetCache.getAttachedCosmetic(id)?.copy(slot = slot) }
        val key = worn.mapIndexed { i, (slot, id) -> Triple(slot, id, resolved[i]) }
        cachedLegacyEquipment?.let { if (cachedLegacyKey == key) return it }

        val equipment = CosmeticEquipment()
        ids.forEachIndexed { i, id ->
            val attached = resolved[i]
            if (attached != null) {
                equipment.equip(attached)
            } else if (legacyLoadAttempted.add(id)) {
                PolyPlusClient.SCOPE.launch {
                    runCatching { CosmeticAssetCache.ensureCosmeticLoaded(id) }
                }
            }
        }
        cachedLegacyEquipment = equipment
        cachedLegacyKey = key
        return equipment
    }
    *///?}

    //? if >= 1.21.4 {
    private fun legacySkin(mc: Minecraft): PlayerSkin? =
        runCatching { LocalAccountSkin.skin() }.getOrNull()
            ?: runCatching { DefaultPlayerSkin.get(LocalAccountSkin.profile()) }.getOrNull()

    private fun legacyPlayerRenderer(mc: Minecraft, skin: PlayerSkin): PlayerRenderer? {
        val map = (mc.entityRenderDispatcher as EntityRenderDispatcherAccessor)
            .`polyplus$playerRenderers`()
        return (map[skin.model()] ?: map.values.firstOrNull()) as? PlayerRenderer
    }

    private fun legacyState(skin: PlayerSkin, yawDeg: Float): PlayerRenderState = PlayerRenderState().apply {
        (this as? AvatarEmoteRenderAccess)
            ?.`polyplus$bindEmoteController`(EmoteController())
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
        bodyRot = yawDeg
        yRot = 0f
        xRot = 0f
        scale = 1f
        boundingBoxWidth = 0.6f
        boundingBoxHeight = 1.8f
        ageInTicks = previewTicks()
    }

    private fun applyLegacySkinOptions(mc: Minecraft, state: PlayerRenderState, source: PlayerPreviewSource) {
        if (source != PlayerPreviewSource.LocalLive) return
        val options = mc.options
        state.showHat = options.isModelPartEnabled(PlayerModelPart.HAT)
        state.showJacket = options.isModelPartEnabled(PlayerModelPart.JACKET)
        state.showLeftSleeve = options.isModelPartEnabled(PlayerModelPart.LEFT_SLEEVE)
        state.showRightSleeve = options.isModelPartEnabled(PlayerModelPart.RIGHT_SLEEVE)
        state.showLeftPants = options.isModelPartEnabled(PlayerModelPart.LEFT_PANTS_LEG)
        state.showRightPants = options.isModelPartEnabled(PlayerModelPart.RIGHT_PANTS_LEG)
        state.showCape = state.showCape && options.isModelPartEnabled(PlayerModelPart.CAPE)
        state.mainArm = options.mainHand().get()
    }
    //?}

    fun renderInto(fbo: RenderTarget, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, cameraTiltDeg: Float = 0f, lights: PreviewLights? = null): Boolean {
        val mc = Minecraft.getInstance()

        //? if >= 1.21.4 {
        val accountSkin = legacySkin(mc) ?: return false
        val baseSkin = previewSkin(source)?.let { LocalSkinOverride.withSkin(accountSkin, it) } ?: accountSkin
        val skin = capeOverride(source)?.let { withCape(baseSkin, it) } ?: baseSkin
        val equipment = when (source) {
            is PlayerPreviewSource.Override -> source.equipment
            PlayerPreviewSource.LocalLive ->
                (mc.player as? PlayerCosmeticsAccess)?.`polyplus$cosmeticEquipment`() ?: CosmeticEquipment()
        }
        equipmentByEntityId[LEGACY_PREVIEW_ENTITY_ID] = equipment
        bindTint(LEGACY_PREVIEW_ENTITY_ID, source)
        val state = legacyState(skin, yawDeg)
        if (hidesCape(source)) state.showCape = false
        applyLegacySkinOptions(mc, state, source)
        applyTryOn(state)
        val renderer = legacyPlayerRenderer(mc, skin) ?: return false
        //?}
        //? if < 1.21.4 {
        /*// 1.21.1 has no render states: it must render a real entity, which needs a client
        val level = mc.level ?: PreviewWorld.level() ?: return false
        val accountSkin = runCatching { LocalAccountSkin.skin() }.getOrNull() ?: DefaultPlayerSkin.get(LocalAccountSkin.profile())
        val skin = previewSkin(source)?.let { LocalSkinOverride.withSkin(accountSkin, it) } ?: accountSkin
        val player = legacyDummy(mc, level) ?: return false
        val cape = capeOverride(source)
            ?: CosmeticAssetCache.getCapeTexture(mc.gameProfile.id)
        player.skinOverride = if (hidesCape(source)) withoutCape(skin) else cape?.let { withCape(skin, it) } ?: skin
        player.localOptions = source == PlayerPreviewSource.LocalLive
        bindLegacyEquipment(player, source)
        player.setYRot(0f); player.yRotO = 0f
        player.yBodyRot = yawDeg; player.yBodyRotO = yawDeg
        player.yHeadRot = yawDeg; player.yHeadRotO = yawDeg
        player.setXRot(0f); player.xRotO = 0f
        player.setPos(0.0, 0.0, 0.0)
        player.xo = 0.0; player.yo = 0.0; player.zo = 0.0
        player.xCloak = 0.0; player.yCloak = 0.0; player.zCloak = 0.0
        player.xCloakO = 0.0; player.yCloakO = 0.0; player.zCloakO = 0.0
        PreviewTryOn.yaw = yawDeg
        *///?}

        //? if < 1.21.5 {
        /*// live previews render right before the buffer swap, which presents nothing on macOS unless framebuffer 0 is still bound
        val boundTarget = RenderTargetFbo.saveBoundTarget()
        *///?}
        //? if >= 1.21.5 {
        val colorTex = fbo.colorTexture ?: return false
        val depthTex = fbo.depthTexture ?: return false
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(colorTex, 0, depthTex, 1.0)
        //?} elif = 1.21.4 {
        /*fbo.setClearColor(0f, 0f, 0f, 0f)
        fbo.clear()
        fbo.bindWrite(true)
        *///?} else {
        /*fbo.setClearColor(0f, 0f, 0f, 0f)
        fbo.clear(Minecraft.ON_OSX)
        fbo.bindWrite(true)
        *///?}

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
        pose.translate(w / 2f, h * verticalAnchor, PREVIEW_DEPTH)
        pose.scale(scale, scale, -scale)
        pose.translate(0f, bbH / 2f, 0f)
        if (cameraTiltDeg != 0f) pose.rotateBy(Quaternionf().rotateX(Math.toRadians(cameraTiltDeg.toDouble()).toFloat()))
        pose.rotateBy(Quaternionf().rotateZ(Math.PI.toFloat()))

        Lighting.setupForEntityInInventory()
        lights?.let { RenderSystem.setShaderLights(Vector3f(it.key), Vector3f(it.fill)) }
        val dispatcher = mc.entityRenderDispatcher
        //? if < 1.21.4 {
        /*val prevCamera = dispatcher.camera
        val standInSwapped = swapInStandIn(mc, level)
        *///?}
        val realMainTarget = mc.mainRenderTarget
        try {
            LobbyPipelines.previewing = true
            dispatcher.setRenderShadow(false)
            val bufferSource = mc.renderBuffers().bufferSource()
            (mc as MinecraftAccessor)
                .`polyplus$setMainRenderTarget`(fbo)
            //? if >= 1.21.4 {
            renderer.render(state, pose, bufferSource, 0xF000F0)
            //?}
            //? if < 1.21.4 {
            /*// Always swap in the far-away stand-in camera (not only off-world): in a live
            dispatcher.camera = PreviewWorld.camera()
            dispatcher.overrideCameraOrientation(Quaternionf().rotateY(Math.PI.toFloat()))
            dispatcher.getRenderer(player)?.render(player, 0f, 1f, pose, bufferSource, 0xF000F0)
            *///?}
            PetPreview.renderEquipped(source, pose, bufferSource, yawDeg)
            //? if < 1.21.5
            //fbo.bindWrite(true)
            bufferSource.endBatch()
        } finally {
            LobbyPipelines.previewing = false
            (mc as MinecraftAccessor)
                .`polyplus$setMainRenderTarget`(realMainTarget)
            dispatcher.setRenderShadow(true)
            //? if < 1.21.4 {
            /*dispatcher.camera = prevCamera
            if (standInSwapped) mc.player = null
            *///?}
            RenderSystem.restoreProjectionMatrix()
            //? if < 1.21.5 {
            /*RenderTargetFbo.restoreBoundTarget(boundTarget)
            *///?}
        }

        return true
    }

    private fun renderLegacy(source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float, key: Any) {
        val fbo = ensureLegacyTarget(w, h)
        if (!renderInto(fbo, source, yawDeg, w, h, modelScale, verticalAnchor)) return
        readbackLegacy(fbo, w, h, key)
    }

    private fun readbackLegacy(fbo: TextureTarget, w: Int, h: Int, key: Any) {
        //? if >= 1.21.5 {
        val colorTex = fbo.colorTexture ?: return
        val pixelSize = colorTex.format.pixelSize()
        val device = RenderSystem.getDevice()
        val buffer = device.createBuffer({ "polyplus_preview_readback" }, BufferType.PIXEL_PACK, BufferUsage.STATIC_READ, w * h * pixelSize)
        val encoder = device.createCommandEncoder()
        encoder.copyTextureToBuffer(colorTex, buffer, 0, {
            runCatching {
                encoder.readBuffer(buffer).use { view -> publish(key) { toImageBitmap(view.data(), w, h, pixelSize) } }
            }.onFailure { LEGACY_LOG.error("[preview] readback failed", it) }
            buffer.close()
        }, 0)
        //?} else {
        /*fbo.bindRead()
        val img = NativeImage(w, h, false)
        img.downloadTexture(0, false)
        fbo.unbindRead()
        try {
            val out = ByteArray(w * h * 4)
            val fade = edgeFadeColumns(w)
            for (y in 0 until h) {
                val outRow = h - 1 - y
                val dstRow = outRow * w
                for (x in 0 until w) {
                    //? if >= 1.21.4 {
                    val px = ARGB.toABGR(img.getPixel(x, y))
                    //?} else
                    //val px = img.getPixelRGBA(x, y)
                    val di = (dstRow + x) * 4
                    val f = fade[x]
                    out[di] = px.toByte()
                    out[di + 1] = (px ushr 8).toByte()
                    out[di + 2] = (px ushr 16).toByte()
                    out[di + 3] = scaleByte((px ushr 24) and 0xFF, f)
                }
            }
            publish(key) { PolyBitmap(w, h, out).asComposeImageBitmap() }
        } finally {
            img.close()
        }
        *///?}
    }
    *///?}
}
//?} else {
/*import androidx.compose.ui.graphics.ImageBitmap
import org.polyfrost.compose.backend.asComposeImageBitmap
import net.minecraft.client.Minecraft
import net.minecraft.client.render.model.Model
import net.minecraft.client.render.model.PlayerModelPart
import net.minecraft.client.render.model.entity.PlayerModel
import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GLX
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.render.platform.Lighting
import net.minecraft.client.resource.skin.DefaultSkinUtils
import net.minecraft.resources.Identifier
import org.polyfrost.compose.render.PolyBitmap
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.joml.Quaternionf
import org.polyfrost.polyplus.client.pets.PetAsset
import org.polyfrost.polyplus.client.pets.PetAssets
import org.polyfrost.polyplus.client.pets.PetPreview
import org.polyfrost.polyplus.client.utils.Easing
import org.polyfrost.polyplus.client.utils.rotateBy
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.LocalAccountSkin
import org.polyfrost.polyplus.client.cosmetics.LocalSkinOverride
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticRenderer
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticWearers
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffects
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import org.polyfrost.polyplus.client.render.PoseStack
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.PolyPlusConstants
import net.minecraft.client.render.texture.HttpImageProcessor
import net.minecraft.client.render.texture.HttpTexture
import java.awt.image.BufferedImage
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.UUID
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

object PlayerPreviewRenderer {
    private val LOG = LoggerFactory.getLogger("polyplus/preview")
    private const val MAX_DIM = 512
    private const val MODEL_SCALE = 0.0625f
    private const val PLAYER_BB_HEIGHT = 1.8f
    internal const val EDGE_FADE_FRACTION = 0.18f
    private const val GRID = 16
    private const val FULL_BRIGHT = 0xF000F0

    private val latestByKey = ConcurrentHashMap<Any, ImageBitmap>()
    private val retainCounts = ConcurrentHashMap<Any, Int>()
    private val models = HashMap<Boolean, PlayerModel>()
    private var target: RenderTarget? = null

    fun cached(key: Any): ImageBitmap? = latestByKey[key]

    fun retain(key: Any) {
        retainCounts.merge(key, 1, Int::plus)
    }

    fun release(key: Any) {
        if (retainCounts.computeIfPresent(key) { _, n -> (n - 1).takeIf { it > 0 } } == null) latestByKey.remove(key)
        dropHoldersIfIdle()
    }

    @JvmStatic
    fun dropHoldersIfIdle() {
        if (retainCounts.isNotEmpty() || PlayerPreviewOffscreen.hasEntries()) return
        drawnEquipment = null
        cachedLocalEquipment = null
        cachedLocalKey = null
    }

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
        val w = widthPx.coerceAtMost(MAX_DIM)
        val h = heightPx.coerceAtMost(MAX_DIM)
        ClientPlatform.runOnMain {
            runCatching {
                val fbo = renderOffscreen(source, yawDeg, w, h, modelScale, verticalAnchor) ?: return@runCatching
                if (!retainCounts.containsKey(key)) return@runCatching
                val bmp = readback(fbo, w, h)
                latestByKey[key] = bmp
                if (!retainCounts.containsKey(key)) latestByKey.remove(key, bmp)
            }.onFailure { LOG.error("[preview] capture failed", it) }
            dropHoldersIfIdle()
        }
        return latestByKey[key]
    }

    fun dispose() {
        val t = target
        target = null
        latestByKey.clear()
        if (t != null) ClientPlatform.runOnMain { runCatching { t.destroyBuffers() } }
    }

    internal fun smooth(t: Float): Float = Easing.smootherstep(t)

    private fun fadeAlpha(tx: Float, ty: Float, fadeEdges: Boolean, bottomFade: Float): Float {
        var a = 1f
        if (fadeEdges) {
            a *= smooth(minOf(tx, 1f - tx) / EDGE_FADE_FRACTION)
        }
        if (bottomFade > 0f) a *= smooth((1f - ty) / bottomFade)
        return a
    }

    private fun ensureTarget(w: Int, h: Int): RenderTarget {
        val existing = target
        if (existing != null && existing.viewWidth == w && existing.viewHeight == h) return existing
        existing?.destroyBuffers()
        return RenderTarget(w, h, true).also {
            it.setClearColor(0f, 0f, 0f, 0f)
            target = it
        }
    }

    fun renderInto(fbo: RenderTarget, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float): Boolean {
        if (!GLX.useFbo()) return false
        val mc = Minecraft.getInstance()
        var drawn = false
        withSavedFramebuffer {
            resyncGlState()
            fbo.setClearColor(0f, 0f, 0f, 0f)
            fbo.clear()
            fbo.bindWrite(true)
            withOrtho(w, h) {
                runCatching { drawPlayer(mc, source, yawDeg, w, h, modelScale, verticalAnchor) }
                    .onSuccess { drawn = true }
                    .onFailure { LOG.error("[preview] player draw failed", it) }
            }
        }
        return drawn
    }

    private fun renderOffscreen(source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float): RenderTarget? {
        if (!GLX.useFbo()) return null
        val mc = Minecraft.getInstance()
        var fbo: RenderTarget? = null
        withSavedFramebuffer {
            val target = ensureTarget(w, h)
            resyncGlState()
            target.clear()
            target.bindWrite(true)
            withOrtho(w, h) {
                runCatching { drawPlayer(mc, source, yawDeg, w, h, modelScale, verticalAnchor) }
                    .onFailure { LOG.error("[preview] player draw failed", it) }
            }
            fbo = target
        }
        return fbo
    }

    private fun resyncGlState() {
        GlStateManager.disableDepthTest(); GlStateManager.enableDepthTest()
        GlStateManager.depthFunc(GL11.GL_ALWAYS); GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.depthMask(false); GlStateManager.depthMask(true)
        GlStateManager.colorMask(false, false, false, false); GlStateManager.colorMask(true, true, true, true)
        GlStateManager.enableCull(); GlStateManager.disableCull()
        GlStateManager.enableBlend(); GlStateManager.disableBlend()
        GlStateManager.enableLighting(); GlStateManager.disableLighting()
        GlStateManager.activeTexture(GLX.GL_TEXTURE0); GlStateManager.activeTexture(GLX.GL_TEXTURE1)
        GlStateManager.enableTexture(); GlStateManager.disableTexture()
        GlStateManager.activeTexture(GLX.GL_TEXTURE0)
        GlStateManager.disableTexture(); GlStateManager.enableTexture()
        GlStateManager.bindTexture(0)
        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GL11.glDisable(GL11.GL_STENCIL_TEST)
    }

    private fun drawPlayer(mc: Minecraft, source: PlayerPreviewSource, yawDeg: Float, w: Int, h: Int, modelScale: Float, verticalAnchor: Float) {
        val (skin, slim) = previewSkin(source)?.let { it.texture to it.slim } ?: localSkin(mc)
        val model = models.getOrPut(slim) { PlayerModel(0f, slim) }
        val scale = h * modelScale

        GlStateManager.activeTexture(GLX.GL_TEXTURE1)
        GlStateManager.disableTexture()
        GlStateManager.activeTexture(GLX.GL_TEXTURE0)
        GlStateManager.enableTexture()
        GlStateManager.enableDepthTest()
        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.enableAlphaTest()
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f)
        GlStateManager.enableColorMaterial()
        GlStateManager.color4f(1f, 1f, 1f, 1f)

        GlStateManager.pushMatrix()
        GlStateManager.translatef(w / 2f, h * verticalAnchor + scale * PLAYER_BB_HEIGHT / 2f, 50f)
        GlStateManager.scalef(-scale, scale, scale)
        GlStateManager.pushMatrix()
        GlStateManager.rotatef(180f, 0f, 0f, 1f)
        GlStateManager.rotatef(135f, 0f, 1f, 0f)
        Lighting.turnOn()
        GlStateManager.popMatrix()
        GlStateManager.enableRescaleNormal()
        GlStateManager.disableCull()
        PreviewTryOn.yaw = yawDeg
        previewPet(source)?.let { PetPreview.draw(it, yawDeg) }
        GlStateManager.rotatef(yawDeg, 0f, 1f, 0f)
        GlStateManager.scalef(0.9375f, 0.9375f, 0.9375f)
        GlStateManager.translatef(0f, -1.5078125f, 0f)

        Model.copyRotation(model.head, model.hat)
        Model.copyRotation(model.leftLeg, model.leftPants)
        Model.copyRotation(model.rightLeg, model.rightPants)
        Model.copyRotation(model.leftArm, model.leftSleeve)
        Model.copyRotation(model.rightArm, model.rightSleeve)
        Model.copyRotation(model.body, model.jacket)

        // keep translucent overlays from overwriting the base layer's alpha
        GlStateManager.enableBlend()
        GlStateManager.blendFuncSeparate(770, 771, 1, 771)
        mc.textureManager.bind(skin)
        val shown = localModelParts(mc, source)
        val layers = listOf(
            PlayerModelPart.HAT to model.hat,
            PlayerModelPart.LEFT_PANTS_LEG to model.leftPants,
            PlayerModelPart.RIGHT_PANTS_LEG to model.rightPants,
            PlayerModelPart.LEFT_SLEEVE to model.leftSleeve,
            PlayerModelPart.RIGHT_SLEEVE to model.rightSleeve,
            PlayerModelPart.JACKET to model.jacket,
        ).filter { (layer, _) -> shown == null || layer in shown }.map { it.second }
        for (part in listOf(model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg) + layers) {
            part.render(MODEL_SCALE)
        }

        capeFor(mc, source)?.takeIf { shown == null || PlayerModelPart.CAPE in shown }?.let { cape ->
            mc.textureManager.bind(cape)
            GlStateManager.pushMatrix()
            GlStateManager.translatef(0f, 0f, 0.125f)
            GlStateManager.rotatef(6f, 1f, 0f, 0f)
            GlStateManager.rotatef(180f, 0f, 1f, 0f)
            model.renderCape(MODEL_SCALE)
            GlStateManager.popMatrix()
        }
        GlStateManager.disableBlend()

        val equipment = equipmentFor(source)
        if (equipment.equipped().isNotEmpty() || BonusEffects.active(null, true)) {
            val renderContext = PlayerRenderContext(
                ageInTicks = mc.player?.ticks?.toFloat() ?: 0f,
                walkAnimationSpeed = 0f,
                isCrouching = false,
                isOnGround = false,
                isInWater = false,
                swimAmount = 0f,
                isInvisible = false,
                signals = PreviewTryOn.previewSignals(),
            )
            CosmeticRenderer.render(
                PoseStack(),
                FULL_BRIGHT,
                renderContext,
                model,
                equipment,
                particleColor(source),
                false,
                emptySet(),
                CosmeticWearers.previewWearer(),
            )
        }

        GlStateManager.enableCull()
        GlStateManager.disableRescaleNormal()
        GlStateManager.popMatrix()
        Lighting.turnOff()
        GlStateManager.disableColorMaterial()
    }

    private fun previewPet(source: PlayerPreviewSource): PetAsset? = when (source) {
        is PlayerPreviewSource.Override -> source.pet
        PlayerPreviewSource.LocalLive ->
            CosmeticCatalog.localEquipped().equipped[BodySlot.Pet]
                ?.let { PetAssets.get(it) }
    }

    private fun capeFor(mc: Minecraft, source: PlayerPreviewSource): Identifier? = when (source) {
        is PlayerPreviewSource.Override ->
            if (source.equipment.get(BodySlot.Backpack) != null) null
            else source.capeCosmeticId?.let(CosmeticAssetCache::getCapeResource) ?: source.mojangCape
        PlayerPreviewSource.LocalLive ->
            if (CosmeticCatalog.localEquipped().cape?.let(CosmeticAssetCache::isEngineCape) == true) null
            else CosmeticCatalog.localEquipped().cape?.let(CosmeticAssetCache::getCapeResource)
                ?: if (!MojangCapeService.overridesLocalCape(ClientPlatform.localPlayerUuid())) playerCape(mc)
                else MojangCapeService.localCapeTexture()
                    ?: if (MojangCapeService.localCapeHidden()) null else playerCape(mc)
    }

    private fun playerCape(mc: Minecraft): Identifier? = mc.player?.capeTextureLocation ?: optifineCape(mc) ?: accountCape(mc)

    private var drawnEquipment: CosmeticEquipment? = null

    fun heldCosmetics() = drawnEquipment?.equipped()?.map { it.cosmetic }.orEmpty()

    private fun equipmentFor(source: PlayerPreviewSource): CosmeticEquipment = when (source) {
        is PlayerPreviewSource.Override -> source.equipment
        PlayerPreviewSource.LocalLive -> localEquipment()
    }.also { drawnEquipment = it }

    private val loadAttempted = Collections.newSetFromMap(ConcurrentHashMap<Int, Boolean>())
    private var cachedLocalEquipment: CosmeticEquipment? = null
    private var cachedLocalKey: List<Any>? = null

    private fun localEquipment(): CosmeticEquipment {
        val worn = CosmeticCatalog.localEquipped().equipped.entries.toList()
        val ids = worn.map { it.value }
        val resolved = worn.map { (slot, id) -> CosmeticAssetCache.getAttachedCosmetic(id)?.copy(slot = slot) }
        val key = worn.mapIndexed { i, (slot, id) -> Triple(slot, id, resolved[i]) }
        cachedLocalEquipment?.let { if (cachedLocalKey == key) return it }

        val equipment = CosmeticEquipment()
        ids.forEachIndexed { i, id ->
            val attached = resolved[i]
            if (attached != null) {
                equipment.equip(attached)
            } else if (loadAttempted.add(id)) {
                PolyPlusClient.SCOPE.launch {
                    runSuspendCatching { CosmeticAssetCache.ensureCosmeticLoaded(id) }
                }
            }
        }
        cachedLocalEquipment = equipment
        cachedLocalKey = key
        return equipment
    }

    private fun localSkin(mc: Minecraft): Pair<Identifier, Boolean> {
        mc.player?.let { return it.skinTextureLocation to (it.modelType == "slim") }
        LocalAccountSkin.textures()?.let { textures -> textures.texture?.let { return it to (textures.slim == true) } }
        val id = ClientPlatform.localPlayerUuid()
        return DefaultSkinUtils.getDefaultSkin(id) to (DefaultSkinUtils.getDefaultModelType(id) == "slim")
    }

    private fun localModelParts(mc: Minecraft, source: PlayerPreviewSource): Set<PlayerModelPart>? =
        if (source == PlayerPreviewSource.LocalLive) mc.options.getPlayerModelParts() else null

    private fun previewSkin(source: PlayerPreviewSource): PreviewSkin? =
        (source as? PlayerPreviewSource.Override)?.skin ?: LocalSkinOverride.forPlayer(ClientPlatform.localPlayerUuid())

    private fun particleColor(source: PlayerPreviewSource): Int? {
        val tint = (source as? PlayerPreviewSource.Override)?.tint
        return if (tint != null) tint.particleColor else CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())
    }

    private fun accountCape(mc: Minecraft): Identifier? = LocalAccountSkin.textures()?.cape

    private val OPTIFINE_USERNAME = Regex("[A-Za-z0-9_]+")
    private val optifineCapesRequested = ConcurrentHashMap.newKeySet<String>()
    private val optifineCapesLoaded = ConcurrentHashMap.newKeySet<String>()

    private fun optifineCape(mc: Minecraft): Identifier? {
        if (!ceraOptifineCapes()) return null
        val name = mc.session.username
        if (!name.matches(OPTIFINE_USERNAME)) return null
        val location = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "optifine_cape/${name.lowercase()}")
        if (optifineCapesRequested.add(name)) {
            mc.textureManager.register(location, HttpTexture(null, "https://optifine.net/capes/$name.png", null, object : HttpImageProcessor {
                override fun process(image: BufferedImage): BufferedImage {
                    var width = 64
                    var height = 32
                    while (width < image.width || height < image.height) {
                        width *= 2
                        height *= 2
                    }
                    return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
                        graphics.apply { drawImage(image, 0, 0, null); dispose() }
                    }
                }

                override fun onTextureDownloaded() {
                    optifineCapesLoaded.add(name)
                }
            }))
        }
        return location.takeIf { name in optifineCapesLoaded }
    }

    private val ceraConfig by lazy { runCatching { Class.forName("dev.rdh.cera.Cera").getField("CONFIG") }.getOrNull() }

    private fun ceraOptifineCapes(): Boolean = runCatching {
        val config = ceraConfig?.get(null) ?: return false
        config.javaClass.getField("optifineCosmetics").getBoolean(config)
    }.getOrDefault(false)

    private fun readback(fbo: RenderTarget, w: Int, h: Int): ImageBitmap {
        val data = BufferUtils.createByteBuffer(w * h * 4)
        withSavedFramebuffer {
            fbo.bindWrite(false)
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1)
            GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data)
        }
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) {
            val outRow = h - 1 - y
            val ty = (outRow + 0.5f) / h
            for (x in 0 until w) {
                val f = fadeAlpha((x + 0.5f) / w, ty, true, 0f)
                val si = (y * w + x) * 4
                val di = (outRow * w + x) * 4
                out[di] = data.get(si)
                out[di + 1] = data.get(si + 1)
                out[di + 2] = data.get(si + 2)
                out[di + 3] = scaleByte(data.get(si + 3).toInt() and 0xFF, f)
            }
        }
        return PolyBitmap(w, h, out).asComposeImageBitmap()
    }

    private fun scaleByte(v: Int, f: Float): Byte = (v * f).toInt().coerceAtMost(255).toByte()

    private inline fun withSavedFramebuffer(block: () -> Unit) {
        val fb = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)
        val viewport = IntArray(4)
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport)
        try {
            block()
        } finally {
            GLX.bindFramebuffer(GLX.GL_FRAMEBUFFER, fb)
            GlStateManager.viewport(viewport[0], viewport[1], viewport[2], viewport[3])
        }
    }

    private inline fun withOrtho(w: Int, h: Int, block: () -> Unit) {
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.ortho(0.0, w.toDouble(), h.toDouble(), 0.0, 1000.0, 3000.0)
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        GlStateManager.translatef(0f, 0f, -2000f)
        try {
            block()
        } finally {
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.popMatrix()
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.popMatrix()
        }
    }
}
*///?}
