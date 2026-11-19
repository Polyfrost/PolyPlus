package org.polyfrost.polyplus.client.gui.onboarding.live

//? if > 1.8.9 && < 26.1 {
/*import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.internal.ui.OffscreenTarget
import org.polyfrost.oneconfig.internal.ui.RenderTargetFbo
import org.polyfrost.oneconfig.internal.ui.TextureRenderer
import org.polyfrost.oneconfig.internal.ui.hud.GuiTargetRedirect
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import org.slf4j.LoggerFactory
import kotlin.math.ceil
//? if >= 1.21.8 {
import net.minecraft.client.gui.render.GuiRenderer
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer
import net.minecraft.client.gui.render.pip.GuiEntityRenderer
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer
import net.minecraft.client.gui.render.pip.GuiSignRenderer
import net.minecraft.client.gui.render.pip.GuiSkinRenderer
import net.minecraft.client.gui.render.state.GuiRenderState
import net.minecraft.client.renderer.fog.FogRenderer
//?} else {
/^import org.joml.Matrix4f
^///?}
//? if = 1.21.1 {
/^import com.mojang.blaze3d.vertex.VertexSorting
^///?} elif < 1.21.8 {
/^import com.mojang.blaze3d.ProjectionType
^///?}

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

    private var tile: OffscreenTarget? = null
    private var screen: Screen? = null
    private var backdrop: Screen? = null
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
        return !hasContent || screenKind != kind || now - capturedNanos >= REFRESH_NANOS || tileWidthFor(contentWidth) != tile?.target?.width
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
        val target = tile?.target ?: return
        val textures = ctx.renderer as? TextureRenderer ?: return
        textures.texture(target, x, y, width, height, 0f, 1f, 1f, 0f, 1f)
    }

    fun release() {
        hasContent = false
        screen = null
        backdrop = null
        screenKind = null
        tile?.dispose()
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
        val window = mc.window
        if (main.width <= 0 || main.height <= 0 || window.width != main.width || window.height != main.height) return
        val shown = screenFor(mc, window.guiScaledWidth, window.guiScaledHeight) ?: return
        val scratch = shared.scratch(main.width, main.height) ?: return
        if (!shared.renderScreen(mc, backdropFor(mc, window.guiScaledWidth, window.guiScaledHeight), shown, scratch)) return
        val width = tileWidthFor(main.width)
        val height = (width.toLong() * main.height / main.width).toInt().coerceAtLeast(1)
        val offscreen = tile ?: OffscreenTarget().also { tile = it }
        if (!offscreen.resolve(width, height)) return
        val target = offscreen.target ?: return
        if (!downsample(scratch, target)) return
        contentWidth = main.width
        contentHeight = main.height
        hasContent = true
    }

    private fun tileWidthFor(full: Int): Int {
        val wanted = ceil(wantedWidth * TILE_OVERSAMPLE).toInt()
        val source = if (full > 0) full else Minecraft.getInstance().window.width
        return wanted.coerceIn(1, source.coerceAtLeast(1))
    }

    private fun downsample(source: RenderTarget, target: RenderTarget): Boolean {
        val from = RenderTargetFbo.getFboId(source)
        val to = RenderTargetFbo.getFboId(target)
        if (from <= 0 || to <= 0) return false
        val read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
        val drawn = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
        val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        if (scissor) GL11.glDisable(GL11.GL_SCISSOR_TEST)
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to)
            GL30.glBlitFramebuffer(0, 0, source.width, source.height, 0, 0, target.width, target.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR)
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read)
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawn)
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST)
        }
        return true
    }

    private fun screenFor(mc: Minecraft, width: Int, height: Int): Screen? {
        val existing = screen
        if (existing != null && screenKind == kind) {
            if (existing.width != width || existing.height != height) initScreen(mc, existing, width, height)
            return existing
        }
        val created = when (kind) {
            RenderScreenKind.PAUSE -> RenderScreenPause()
            RenderScreenKind.OPTIONS -> RenderScreenOptions(mc.options)
            RenderScreenKind.INVENTORY -> RenderScreenInventory.create()
            RenderScreenKind.WORLDS -> RenderScreenVanilla.worlds(mc)
            RenderScreenKind.CONNECTING -> RenderScreenVanilla.connecting()
            RenderScreenKind.REALMS -> RenderScreenVanilla.realms()
            RenderScreenKind.LOADING -> RenderScreenLoading()
            RenderScreenKind.BACKDROP -> RenderScreenBackdrop()
        } ?: return null
        initScreen(mc, created, width, height)
        screen = created
        screenKind = kind
        return created
    }

    private fun backdropFor(mc: Minecraft, width: Int, height: Int): Screen? {
        if (kind != RenderScreenKind.CONNECTING) return null
        val existing = backdrop ?: RenderScreenBackdrop().also {
            initScreen(mc, it, width, height)
            backdrop = it
        }
        if (existing.width != width || existing.height != height) initScreen(mc, existing, width, height)
        return existing
    }

    private fun initScreen(mc: Minecraft, target: Screen, width: Int, height: Int) {
        //? if >= 1.21.11 {
        target.init(width, height)
        //?} else {
        /^target.init(mc, width, height)
        ^///?}
    }

    private companion object {
        val LOG = LoggerFactory.getLogger("polyplus/render-screen")
        const val IDLE_NANOS = 250_000_000L
        const val REFRESH_NANOS = 1_000_000_000L
        const val RETRY_NANOS = 500_000_000L
        const val RETRY_MAX_SHIFT = 4
        const val TILE_OVERSAMPLE = 1.5f
    }
}

internal object RenderScreenCaptures : LivePreviewFrame {
    private val tiles = mutableListOf<RenderScreenCapture>()
    private var shared: Shared? = null

    class Shared {
        private val scratchTarget = OffscreenTarget()
        //? if >= 1.21.8 {
        private val state = GuiRenderState()
        private var renderer: GuiRenderer? = null
        private var fogs: FogRenderer? = null
        //?}

        fun scratch(width: Int, height: Int): RenderTarget? =
            if (scratchTarget.resolve(width, height)) scratchTarget.target else null

        fun renderScreen(mc: Minecraft, backdrop: Screen?, shown: Screen, scratch: RenderTarget): Boolean {
            val savedRedirect = GuiTargetRedirect.target
            GuiTargetRedirect.target = null
            val savedMain = mc.mainRenderTarget
            val blur = mc.options.menuBackgroundBlurriness()
            val savedBlur = blur.get()
            val watermark = PolyPlusConfig.watermarkEnabled
            val readBinding = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            val drawBinding = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            val viewport = IntArray(VIEWPORT_SIZE).also { GL11.glGetIntegerv(GL11.GL_VIEWPORT, it) }
            //? if < 1.21.5 {
            /^val bound = RenderTargetFbo.saveBoundTarget()
            val clearColor = FloatArray(VIEWPORT_SIZE).also { GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, it) }
            ^///?}
            //? if >= 1.21.8 {
            val projection = RenderSystem.getProjectionMatrixBuffer()
            val projectionType = RenderSystem.getProjectionType()
            //?}
            val accessor = mc as MinecraftAccessor
            accessor.`polyplus$setMainRenderTarget`(scratch)
            blur.set(0)
            PolyPlusConfig.watermarkEnabled = false
            try {
                if (!clear(scratch)) return false
                drawScreen(mc, backdrop, shown)
            } finally {
                PolyPlusConfig.watermarkEnabled = watermark
                blur.set(savedBlur)
                accessor.`polyplus$setMainRenderTarget`(savedMain)
                GuiTargetRedirect.target = savedRedirect
                //? if < 1.21.5 {
                /^RenderTargetFbo.restoreBoundTarget(bound)
                RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
                ^///?}
                //? if >= 1.21.8 {
                projection?.let { RenderSystem.setProjectionMatrix(it, projectionType) }
                //?}
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readBinding)
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawBinding)
                GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            }
            return true
        }

        private fun clear(scratch: RenderTarget): Boolean {
            //? if >= 1.21.5 {
            val color = scratch.colorTexture ?: return false
            val depth = scratch.depthTexture ?: return false
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color, OPAQUE_BLACK, depth, 1.0)
            //?} elif = 1.21.4 {
            /^resetState()
            scratch.setClearColor(0f, 0f, 0f, 1f)
            scratch.clear()
            scratch.bindWrite(true)
            ^///?} else {
            /^resetState()
            scratch.setClearColor(0f, 0f, 0f, 1f)
            scratch.clear(Minecraft.ON_OSX)
            scratch.bindWrite(true)
            ^///?}
            return true
        }

        //? if < 1.21.5 {
        /^private fun resetState() {
            RenderSystem.disableScissor()
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.depthMask(true)
            RenderSystem.enableDepthTest()
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.enableCull()
            RenderSystem.disableBlend()
            RenderSystem.defaultBlendFunc()
        }
        ^///?}

        private fun drawScreen(mc: Minecraft, backdrop: Screen?, shown: Screen) {
            //? if >= 1.21.4 {
            val partial = mc.deltaTracker.gameTimeDeltaTicks
            //?} else {
            /^val partial = mc.timer.gameTimeDeltaTicks
            ^///?}
            //? if >= 1.21.8 {
            state.reset()
            mc.gameRenderer.lighting.setupFor(Lighting.Entry.ITEMS_3D)
            //? if >= 1.21.11 {
            val graphics = GuiGraphics(mc, state, OFF_SCREEN, OFF_SCREEN)
            //?} else {
            /^val graphics = GuiGraphics(mc, state)
            ^///?}
            //? if >= 1.21.10 {
            backdrop?.renderWithTooltipAndSubtitles(graphics, OFF_SCREEN, OFF_SCREEN, partial)
            shown.renderWithTooltipAndSubtitles(graphics, OFF_SCREEN, OFF_SCREEN, partial)
            //?} else {
            /^backdrop?.renderWithTooltip(graphics, OFF_SCREEN, OFF_SCREEN, partial)
            shown.renderWithTooltip(graphics, OFF_SCREEN, OFF_SCREEN, partial)
            ^///?}
            val gui = renderer(mc)
            gui.render(fog().getBuffer(FogRenderer.FogMode.NONE))
            gui.incrementFrameNumber()
            //?} else {
            /^val window = mc.window
            val projection = Matrix4f().setOrtho(
                0f,
                (window.width / window.guiScale).toFloat(),
                (window.height / window.guiScale).toFloat(),
                0f,
                PROJECTION_NEAR,
                PROJECTION_FAR,
            )
            val savedProjection = Matrix4f(RenderSystem.getProjectionMatrix())
            //? if = 1.21.1 {
            /^val savedSorting = RenderSystem.getVertexSorting()
            ^///?} else {
            val savedType = RenderSystem.getProjectionType()
            //?}
            //? if = 1.21.1 {
            /^RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z)
            ^///?} else {
            RenderSystem.setProjectionMatrix(projection, ProjectionType.ORTHOGRAPHIC)
            //?}
            val modelView = RenderSystem.getModelViewStack()
            modelView.pushMatrix()
            modelView.translation(0f, 0f, GUI_DEPTH)
            //? if = 1.21.1 {
            /^RenderSystem.applyModelViewMatrix()
            ^///?}
            Lighting.setupFor3DItems()
            try {
                val graphics = GuiGraphics(mc, mc.renderBuffers().bufferSource())
                backdrop?.renderWithTooltip(graphics, OFF_SCREEN, OFF_SCREEN, partial)
                shown.renderWithTooltip(graphics, OFF_SCREEN, OFF_SCREEN, partial)
                graphics.flush()
            } finally {
                modelView.popMatrix()
                //? if = 1.21.1 {
                /^RenderSystem.applyModelViewMatrix()
                ^///?}
                //? if = 1.21.1 {
                /^RenderSystem.setProjectionMatrix(savedProjection, savedSorting)
                ^///?} else {
                RenderSystem.setProjectionMatrix(savedProjection, savedType)
                //?}
            }
            ^///?}
        }

        //? if >= 1.21.8 {
        private fun fog(): FogRenderer = fogs ?: run {
            val saved = RenderSystem.getShaderFog()
            FogRenderer().also { created ->
                fogs = created
                saved?.let { RenderSystem.setShaderFog(it) }
            }
        }

        private fun renderer(mc: Minecraft): GuiRenderer = renderer ?: run {
            val buffers = mc.renderBuffers().bufferSource()
            //? if >= 1.21.10 {
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
            //?} else {
            /^GuiRenderer(
                state,
                buffers,
                listOf(
                    GuiEntityRenderer(buffers, mc.entityRenderDispatcher),
                    GuiSkinRenderer(buffers),
                    GuiBookModelRenderer(buffers),
                    GuiBannerResultRenderer(buffers),
                    GuiSignRenderer(buffers),
                    GuiProfilerChartRenderer(buffers),
                ),
            )
            ^///?}
        }.also { renderer = it }
        //?}

        fun resetRenderer() {
            //? if >= 1.21.8 {
            runCatching { renderer?.close() }
            renderer = null
            runCatching { fogs?.close() }
            fogs = null
            //?}
        }

        fun close() {
            resetRenderer()
            scratchTarget.dispose()
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
            shared?.close()
            shared = null
            LivePreviewFrames.unregister(this)
            return
        }
        val now = System.nanoTime()
        val next = tiles.filter { it.due(now) }.minByOrNull { it.capturedNanos } ?: return
        next.capture(main, shared ?: Shared().also { shared = it })
    }

    private const val OFF_SCREEN = -10_000
    private const val OPAQUE_BLACK = 0xFF000000.toInt()
    private const val PROJECTION_NEAR = 1000f
    private const val PROJECTION_FAR = 21000f
    private const val GUI_DEPTH = -11000f
    private const val VIEWPORT_SIZE = 4
}
*///?}
