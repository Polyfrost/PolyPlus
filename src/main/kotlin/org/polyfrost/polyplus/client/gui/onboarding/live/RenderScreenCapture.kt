package org.polyfrost.polyplus.client.gui.onboarding.live

//? if >= 26.1 {
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
//? if >= 26.3 {
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.textures.FilterMode
//?} elif >= 26.2 {
/*import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.textures.FilterMode
*///?} else {
/*import com.mojang.blaze3d.textures.FilterMode
*///?}
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer
import net.minecraft.client.gui.render.pip.GuiEntityRenderer
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer
import net.minecraft.client.gui.render.pip.GuiSkinRenderer
//? if < 26.2 {
/*import net.minecraft.client.gui.render.pip.GuiSignRenderer
import net.minecraft.client.renderer.fog.FogRenderer
*///?}
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.Panorama
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.state.gui.GuiRenderState
import org.joml.Vector4f
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.internal.ui.TextureRenderer
import org.polyfrost.oneconfig.internal.ui.hud.GuiTargetRedirect
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.util.Optional
import java.util.OptionalDouble
//? if < 26.2
/*import java.util.OptionalInt*/
import kotlin.math.ceil

internal class RenderScreenCapture {
    var kind: RenderScreenKind = RenderScreenKind.PAUSE

    @Volatile
    var lastDrawNanos = 0L

    @Volatile
    var wantedWidth = 0f

    var contentWidth = 0
        private set
    var contentHeight = 0
        private set
    var hasContent = false
        private set

    @Volatile
    var closed = false
        private set

    private val panorama = Panorama()
    private var tile: TextureTarget? = null
    private var screen: Screen? = null
    private var screenKind: RenderScreenKind? = null
    private var failures = 0
    private var retryAtNanos = 0L
    internal var capturedNanos = 0L
        private set

    fun close() {
        closed = true
    }

    fun due(now: Long): Boolean {
        if (closed || now < retryAtNanos || now - lastDrawNanos > IDLE_NANOS) return false
        return !hasContent || screenKind != kind || now - capturedNanos >= REFRESH_NANOS || tileWidthFor(contentWidth) != tile?.width
    }

    fun capture(main: RenderTarget, shared: RenderScreenCaptures.Shared) {
        val now = System.nanoTime()
        capturedNanos = now
        runCatching { captureInto(main, shared) }
            .onSuccess { failures = 0 }
            .onFailure { fail(now, it, shared) }
    }

    fun draw(ctx: RenderContext, x: Float, y: Float, width: Float, height: Float) {
        if (!hasContent || width <= 0f || height <= 0f) return
        val target = tile ?: return
        val textures = ctx.renderer as? TextureRenderer ?: return
        textures.texture(target, x, y, width, height, 0f, 1f, 1f, 0f, 1f)
    }

    fun release() {
        hasContent = false
        screen = null
        screenKind = null
        tile?.destroyBuffers()
        tile = null
    }

    private fun fail(now: Long, error: Throwable, shared: RenderScreenCaptures.Shared) {
        if (failures == 0) LOG.warn("[render-screen] capture of {} failed, retrying shortly", kind, error)
        else LOG.debug("[render-screen] capture of {} failed again", kind, error)
        failures++
        retryAtNanos = now + (RETRY_NANOS shl (failures - 1).coerceAtMost(RETRY_MAX_SHIFT))
        shared.resetRenderer()
    }

    private fun captureInto(main: RenderTarget, shared: RenderScreenCaptures.Shared) {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        val window = mc.gameRenderer.gameRenderState().windowRenderState
        //?} else
        /*val window = mc.gameRenderer.gameRenderState.windowRenderState*/
        if (main.width <= 0 || main.height <= 0 || window.width != main.width || window.height != main.height) return
        val shown = screenFor(mc, mc.window.guiScaledWidth, mc.window.guiScaledHeight) ?: return
        extract(mc, shown, shared.state)
        if (!drawGui(mc, main, shared)) return
        val width = tileWidthFor(main.width)
        val height = (width.toLong() * main.height / main.width).toInt().coerceAtLeast(1)
        val target = tile?.takeIf { it.width == width && it.height == height } ?: run {
            tile?.destroyBuffers()
            //? if >= 26.3 {
            TextureTarget(TILE_LABEL, width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT).also { tile = it }
            //?} elif >= 26.2 {
            /*TextureTarget(TILE_LABEL, width, height, true, GpuFormat.RGBA8_UNORM).also { tile = it }
            *///?} else {
            /*TextureTarget(TILE_LABEL, width, height, true).also { tile = it }
            *///?}
        }
        downsample(main, target)
        contentWidth = main.width
        contentHeight = main.height
        hasContent = true
    }

    private fun tileWidthFor(full: Int): Int {
        val wanted = ceil(wantedWidth * TILE_OVERSAMPLE).toInt()
        val source = if (full > 0) full else Minecraft.getInstance().window.width
        return wanted.coerceIn(1, source.coerceAtLeast(1))
    }

    private fun downsample(source: RenderTarget, target: TextureTarget) {
        val color = target.colorTexture ?: return
        val depth = target.depthTexture ?: return
        val colorView = target.colorTextureView ?: return
        val sourceView = source.colorTextureView ?: return
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        //? if >= 26.2 {
        encoder.clearColorAndDepthTextures(color, Vector4f(0f, 0f, 0f, 0f), depth, 1.0)
        encoder.createRenderPass({ TILE_LABEL }, colorView, Optional.empty(), target.depthTextureView, OptionalDouble.empty()).use { pass ->
        //?} else {
        /*encoder.clearColorAndDepthTextures(color, 0, depth, 1.0)
        encoder.createRenderPass({ TILE_LABEL }, colorView, OptionalInt.empty(), target.depthTextureView, OptionalDouble.empty()).use { pass ->
        *///?}
            //? if >= 26.3 {
            pass.setPipeline(RenderSystem.getCompiledPipeline(RenderPipelines.TRACY_BLIT))
            RenderSystem.bindDefaultUniforms(pass)
            pass.setUniform(SAMPLER, sourceView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            //?} else {
            /*pass.setPipeline(RenderPipelines.TRACY_BLIT)
            RenderSystem.bindDefaultUniforms(pass)
            pass.bindTexture(SAMPLER, sourceView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
            *///?}
            //? if >= 26.2 {
            pass.draw(BLIT_VERTICES, 1, 0, 0)
            //?} else
            /*pass.draw(0, BLIT_VERTICES)*/
        }
    }

    private fun screenFor(mc: Minecraft, width: Int, height: Int): Screen? {
        val existing = screen
        if (existing != null && screenKind == kind) {
            if (existing.width != width || existing.height != height) existing.init(width, height)
            return existing
        }
        val created = when (kind) {
            RenderScreenKind.PAUSE -> RenderScreenPause(panorama)
            RenderScreenKind.OPTIONS -> RenderScreenOptions(mc.options, panorama)
            RenderScreenKind.INVENTORY -> RenderScreenInventory.create(panorama)
            RenderScreenKind.WORLDS -> RenderScreenVanilla.worlds(mc)
            RenderScreenKind.CONNECTING -> RenderScreenVanilla.connecting()
            RenderScreenKind.REALMS -> RenderScreenVanilla.realms()
            RenderScreenKind.LOADING -> RenderScreenLoading()
            RenderScreenKind.BACKDROP -> RenderScreenBackdrop(panorama)
        } ?: return null
        created.init(width, height)
        screen = created
        screenKind = kind
        return created
    }

    private fun extract(mc: Minecraft, shown: Screen, state: GuiRenderState) {
        //? if >= 26.2 {
        val mainGui = mc.gameRenderer.gameRenderState().guiRenderState
        //?} else
        /*val mainGui = mc.gameRenderer.gameRenderState.guiRenderState*/
        val mainPanorama = mainGui.panoramaRenderState
        state.reset()
        val noBlur = blurField?.let { runCatching { it.getInt(state) }.getOrNull() }
        val graphics = GuiGraphicsExtractor(mc, state, OFF_SCREEN, OFF_SCREEN)
        val watermark = PolyPlusConfig.watermarkEnabled
        PolyPlusConfig.watermarkEnabled = false
        try {
            shown.extractRenderStateWithTooltipAndSubtitles(graphics, OFF_SCREEN, OFF_SCREEN, mc.deltaTracker.gameTimeDeltaTicks)
        } finally {
            PolyPlusConfig.watermarkEnabled = watermark
            if (noBlur != null) blurField?.let { runCatching { it.setInt(state, noBlur) } }
            val written = mainGui.panoramaRenderState
            if (written !== mainPanorama) state.panoramaRenderState = written
            mainGui.panoramaRenderState = mainPanorama
        }
    }

    private fun drawGui(mc: Minecraft, main: RenderTarget, shared: RenderScreenCaptures.Shared): Boolean {
        val gui = shared.renderer(mc)
        val color = main.colorTexture ?: return false
        val depth = main.depthTexture ?: return false
        //? if >= 26.2 {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color, Vector4f(0f, 0f, 0f, 1f), depth, 0.0)
        //?} else
        /*RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color, OPAQUE_BLACK, depth, 1.0)*/
        val savedLights = RenderSystem.getShaderLights()
        val savedRedirect = GuiTargetRedirect.target
        val savedUiLightmap = uiLightmap(mc.gameRenderer)
        RenderSystem.backupProjectionMatrix()
        GuiTargetRedirect.target = main
        try {
            //? if >= 26.2 {
            mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D)
            setUiLightmap(mc.gameRenderer, true)
            gui.render()
            //?} else {
            /*mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_3D)
            setUiLightmap(mc.gameRenderer, true)
            gui.render(shared.fog().getBuffer(FogRenderer.FogMode.NONE))
            *///?}
            gui.endFrame()
        } finally {
            setUiLightmap(mc.gameRenderer, savedUiLightmap)
            GuiTargetRedirect.target = savedRedirect
            RenderSystem.restoreProjectionMatrix()
            savedLights?.let { RenderSystem.setShaderLights(it) }
        }
        return true
    }

    private companion object {
        val LOG = LoggerFactory.getLogger("polyplus/render-screen")
        const val IDLE_NANOS = 250_000_000L
        const val REFRESH_NANOS = 1_000_000_000L
        const val RETRY_NANOS = 500_000_000L
        const val RETRY_MAX_SHIFT = 4
        const val TILE_OVERSAMPLE = 1.5f
        const val TILE_LABEL = "PolyPlus screen tile"
        const val SAMPLER = "InSampler"
        const val BLIT_VERTICES = 3
        const val OFF_SCREEN = -10_000
        //? if < 26.2
        /*const val OPAQUE_BLACK = 0xFF000000.toInt()*/

        val blurField: Field? by lazy {
            runCatching { GuiRenderState::class.java.getDeclaredField("firstStratumAfterBlur").apply { isAccessible = true } }
                .onFailure { LOG.warn("[render-screen] no blur switch on the GUI state, captures may run the menu blur", it) }
                .getOrNull()
        }

        val uiLightmapField: Field? by lazy {
            runCatching { GameRenderer::class.java.getDeclaredField("useUiLightmap").apply { isAccessible = true } }
                .onFailure { LOG.warn("[render-screen] no UI lightmap switch, the inventory player may render dark", it) }
                .getOrNull()
        }

        fun uiLightmap(renderer: GameRenderer): Boolean =
            uiLightmapField?.let { runCatching { it.getBoolean(renderer) }.getOrNull() } ?: false

        fun setUiLightmap(renderer: GameRenderer, value: Boolean) {
            uiLightmapField?.let { runCatching { it.setBoolean(renderer, value) } }
        }
    }
}

internal object RenderScreenCaptures : LivePreviewFrame {
    private val tiles = mutableListOf<RenderScreenCapture>()
    private var shared: Shared? = null

    class Shared {
        val state = GuiRenderState()
        private var renderer: GuiRenderer? = null

        //? if >= 26.2 {
        fun renderer(mc: Minecraft): GuiRenderer = renderer ?: GuiRenderer(
            state,
            mc.gameRenderer.featureRenderDispatcher(),
            listOf(
                GuiEntityRenderer(mc.entityRenderDispatcher),
                GuiSkinRenderer(),
                GuiBookModelRenderer(),
                GuiBannerResultRenderer(mc.atlasManager),
                GuiProfilerChartRenderer(),
            ),
        ).also { renderer = it }
        //?} else {
        /*private var fogs: FogRenderer? = null

        fun fog(): FogRenderer = fogs ?: run {
            val saved = RenderSystem.getShaderFog()
            FogRenderer().also { created ->
                fogs = created
                saved?.let { RenderSystem.setShaderFog(it) }
            }
        }

        fun renderer(mc: Minecraft): GuiRenderer = renderer ?: run {
            val buffers = mc.renderBuffers().bufferSource()
            GuiRenderer(
                state,
                buffers,
                mc.gameRenderer.submitNodeStorage,
                mc.gameRenderer.featureRenderDispatcher,
                listOf(
                    GuiEntityRenderer(buffers, mc.entityRenderDispatcher),
                    GuiSkinRenderer(buffers),
                    GuiBookModelRenderer(buffers),
                    GuiBannerResultRenderer(buffers, mc.atlasManager),
                    GuiSignRenderer(buffers, mc.atlasManager),
                    GuiProfilerChartRenderer(buffers),
                ),
            )
        }.also { renderer = it }
        *///?}

        fun resetRenderer() {
            runCatching { renderer?.close() }
            renderer = null
            //? if < 26.2 {
            /*runCatching { fogs?.close() }
            fogs = null
            *///?}
        }
    }

    fun add(capture: RenderScreenCapture) {
        if (tiles.isEmpty()) LivePreviewFrames.register(this)
        tiles += capture
    }

    override fun render(main: RenderTarget) {
        tiles.filter { it.closed }.forEach {
            it.release()
            tiles -= it
        }
        if (tiles.isEmpty()) {
            shared?.resetRenderer()
            shared = null
            LivePreviewFrames.unregister(this)
            return
        }
        val now = System.nanoTime()
        val next = tiles.filter { it.due(now) }.minByOrNull { it.capturedNanos } ?: return
        next.capture(main, shared ?: Shared().also { shared = it })
    }
}
//?}
