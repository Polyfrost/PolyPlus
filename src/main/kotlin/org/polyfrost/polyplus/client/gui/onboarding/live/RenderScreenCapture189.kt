package org.polyfrost.polyplus.client.gui.onboarding.live

//? if = 1.8.9 {
/*import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen
import net.minecraft.client.gui.screen.menu.GameMenuScreen
import net.minecraft.client.gui.screen.menu.multiplayer.ConnectScreen
import net.minecraft.client.gui.screen.menu.options.OptionsScreen
import net.minecraft.client.gui.screen.menu.world.CreateWorldScreen
import net.minecraft.client.render.Window
import net.minecraft.client.render.texture.TextureAtlas
import net.minecraft.inventory.slot.InventorySlot
import net.minecraft.client.render.pipeline.RenderTarget
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.client.resource.language.I18n
import net.minecraft.resources.Identifier
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.internal.ui.OffscreenTarget
import org.polyfrost.oneconfig.internal.ui.RenderTargetFbo
import org.polyfrost.oneconfig.internal.ui.TextureRenderer
import org.polyfrost.oneconfig.internal.ui.hud.GuiTargetRedirect
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.MenuPanorama
import org.polyfrost.polyplus.mixin.client.access.GuiElementInvoker
import org.polyfrost.polyplus.mixin.client.access.TitleScreenInvoker
import org.polyfrost.polyplus.mixin.client.access.MinecraftAccessor
import org.slf4j.LoggerFactory
import sun.misc.Unsafe
import java.lang.reflect.Modifier
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

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
            .onFailure { fail(now, it) }
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
        screenKind = null
        tile?.dispose()
        tile = null
    }

    private fun fail(now: Long, error: Throwable) {
        if (failures == 0) LOG.warn("[render-screen] capture of {} failed, retrying shortly", kind, error)
        else LOG.debug("[render-screen] capture of {} failed again", kind, error)
        failures++
        retryAtNanos = now + (RETRY_NANOS shl (failures - 1).coerceAtMost(RETRY_MAX_SHIFT))
    }

    private fun captureInto(main: RenderTarget, shared: RenderScreenCaptures.Shared) {
        val mc = Minecraft.getInstance()
        if (main.width <= 0 || main.height <= 0 || mc.width != main.width || mc.height != main.height) return
        val window = Window(mc)
        val shown = screenFor(mc, window.width, window.height) ?: return
        val scratch = shared.scratch(main.width, main.height) ?: return
        if (!shared.renderScreen(mc, shown, scratch, window)) return
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
        val source = if (full > 0) full else Minecraft.getInstance().width
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
            if (existing.width != width || existing.height != height) existing.init(mc, width, height)
            return existing
        }
        val created = when (kind) {
            RenderScreenKind.PAUSE -> RenderScreenPause()
            RenderScreenKind.OPTIONS -> OptionsScreen(null, mc.options)
            RenderScreenKind.INVENTORY -> RenderScreenInventory.create()
            RenderScreenKind.WORLDS -> CreateWorldScreen(null)
            RenderScreenKind.CONNECTING -> RenderScreenVanilla.connecting()
            RenderScreenKind.REALMS -> null
            RenderScreenKind.LOADING -> RenderScreenLoading()
            RenderScreenKind.BACKDROP -> RenderScreenBackdrop()
        } ?: return null
        created.init(mc, width, height)
        screen = created
        screenKind = kind
        return created
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

        fun scratch(width: Int, height: Int): RenderTarget? =
            if (scratchTarget.resolve(width, height)) scratchTarget.target else null

        fun renderScreen(mc: Minecraft, shown: Screen, scratch: RenderTarget, window: Window): Boolean {
            val savedRedirect = GuiTargetRedirect.target
            GuiTargetRedirect.target = null
            val savedMain = mc.renderTarget
            val watermark = PolyPlusConfig.watermarkEnabled
            val bound = RenderTargetFbo.saveBoundTarget()
            val accessor = mc as MinecraftAccessor
            accessor.`polyplus$setMainRenderTarget`(scratch)
            PolyPlusConfig.watermarkEnabled = false
            GlStateManager.matrixMode(GL11.GL_PROJECTION)
            GlStateManager.pushMatrix()
            GlStateManager.matrixMode(GL11.GL_MODELVIEW)
            GlStateManager.pushMatrix()
            try {
                scratch.setClearColor(0f, 0f, 0f, 1f)
                scratch.clear()
                scratch.bindWrite(true)
                GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT)
                GlStateManager.matrixMode(GL11.GL_PROJECTION)
                GlStateManager.loadIdentity()
                GlStateManager.ortho(0.0, window.scaledWidth, window.scaledHeight, 0.0, PROJECTION_NEAR, PROJECTION_FAR)
                GlStateManager.matrixMode(GL11.GL_MODELVIEW)
                GlStateManager.loadIdentity()
                GlStateManager.translatef(0f, 0f, GUI_DEPTH)
                GlStateManager.color4f(1f, 1f, 1f, 1f)
                shown.render(OFF_SCREEN, OFF_SCREEN, 0f)
            } finally {
                GlStateManager.matrixMode(GL11.GL_PROJECTION)
                GlStateManager.popMatrix()
                GlStateManager.matrixMode(GL11.GL_MODELVIEW)
                GlStateManager.popMatrix()
                PolyPlusConfig.watermarkEnabled = watermark
                accessor.`polyplus$setMainRenderTarget`(savedMain)
                GuiTargetRedirect.target = savedRedirect
                RenderTargetFbo.restoreBoundTarget(bound)
            }
            return true
        }

        fun close() {
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
    private const val PROJECTION_NEAR = 1000.0
    private const val PROJECTION_FAR = 3000.0
    private const val GUI_DEPTH = -2000f
}

internal object RenderScreenVanilla {
    private val LOG = LoggerFactory.getLogger("polyplus/render-screen")

    fun connecting(): Screen? = runCatching {
        val screen = unsafe().allocateInstance(ConnectScreen::class.java) as ConnectScreen
        val template = object : Screen() {}
        Screen::class.java.declaredFields
            .filter { !Modifier.isStatic(it.modifiers) }
            .forEach { field ->
                field.isAccessible = true
                field.set(screen, field.get(template))
            }
        screen
    }.onFailure { LOG.warn("[render-screen] could not build the connect screen", it) }.getOrNull()

    private fun unsafe(): Unsafe {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return field.get(null) as Unsafe
    }
}

internal object RenderScreenPanorama {
    private val startNanos = System.nanoTime()

    fun draw(width: Int, height: Int): Boolean {
        val title = MenuPanorama.legacyPanorama() ?: return false
        title.width = width
        title.height = height
        val ticks = ((System.nanoTime() - startNanos) / NANOS_PER_TICK).toFloat()
        GlStateManager.disableAlphaTest()
        try {
            (title as TitleScreenInvoker).`polyplus$drawBackground`(0, 0, ticks % TICK_WRAP)
        } finally {
            GlStateManager.enableAlphaTest()
        }
        return true
    }

    fun inWorld(screen: Screen) {
        if (!draw(screen.width, screen.height)) screen.drawBackgroundTexture(0)
        (screen as GuiElementInvoker).`polyplus$fillGradient`(0, 0, screen.width, screen.height, IN_WORLD_TOP, IN_WORLD_BOTTOM)
    }

    private const val NANOS_PER_TICK = 50_000_000.0
    private const val TICK_WRAP = 1_000_000f
    private const val IN_WORLD_TOP = -1072689136
    private const val IN_WORLD_BOTTOM = -804253680
}

internal class RenderScreenPause : GameMenuScreen() {
    override fun renderBackground() {
        RenderScreenPanorama.inWorld(this)
    }
}

internal class RenderScreenBackdrop : Screen() {
    override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (!RenderScreenPanorama.draw(width, height)) drawBackgroundTexture(0)
    }
}

internal class RenderScreenLoading : Screen() {
    override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        fill(0, 0, width, height, WHITE)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        minecraft.textureManager.bind(MOJANG_LOGO)
        drawTexture((width - LOGO_SIZE) / 2, (height - LOGO_SIZE) / 2, 0, 0, LOGO_SIZE, LOGO_SIZE)
    }

    private companion object {
        val MOJANG_LOGO: Identifier = Identifier.fromNamespaceAndPath("minecraft", "textures/gui/title/mojang.png")
        const val LOGO_SIZE = 256
        const val WHITE = -1
    }
}

internal class RenderScreenInventory private constructor(private val avatar: LivePlayerAvatar) : InventoryMenuScreen(avatar.playerMenu) {
    private val startNanos = System.nanoTime()

    override fun init() {
        x = (width - backgroundWidth) / 2
        y = (height - backgroundHeight) / 2
    }

    override fun renderBackground() {
        RenderScreenPanorama.inWorld(this)
    }

    override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        renderBackground()
        renderMenuBackground(tickDelta, mouseX, mouseY)
        GlStateManager.pushMatrix()
        GlStateManager.translatef(x.toFloat(), y.toFloat(), 0f)
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        menu.slots.forEach { renderEmptySlot(it) }
        renderLabels(mouseX, mouseY)
        GlStateManager.popMatrix()
    }

    private fun renderEmptySlot(slot: InventorySlot) {
        if (slot.item != null) return
        val texture = slot.texture ?: return
        val sprite = minecraft.blocksAtlas.getSprite(texture)
        GlStateManager.disableLighting()
        minecraft.textureManager.bind(TextureAtlas.BLOCKS_LOCATION)
        drawSprite(slot.x, slot.y, sprite, SLOT_SIZE, SLOT_SIZE)
        GlStateManager.enableLighting()
    }

    override fun renderLabels(mouseX: Int, mouseY: Int) {
        textRenderer.draw(I18n.translate("container.crafting"), LABEL_X, LABEL_Y, LABEL_COLOR)
    }

    override fun renderMenuBackground(tickDelta: Float, mouseX: Int, mouseY: Int) {
        GlStateManager.color4f(1f, 1f, 1f, 1f)
        minecraft.textureManager.bind(MENU_LOCATION)
        drawTexture(x, y, 0, 0, backgroundWidth, backgroundHeight)
        val seconds = (System.nanoTime() - startNanos) / NANOS_PER_SECOND
        val lookX = sin(seconds * SWAY_SPEED) * SWAY_REACH * LOOK_DISTANCE
        val lookY = (NOD_BASE + cos(seconds * NOD_SPEED) * NOD_REACH) * LOOK_DISTANCE
        val dispatcher = minecraft.entityRenderDispatcher
        val camera = dispatcher.camera
        val world = dispatcher.world
        val player = minecraft.player
        try {
            dispatcher.camera = avatar
            if (world == null) dispatcher.world = avatar.world
            if (player == null) minecraft.player = LivePlayerAvatars.nameGuard()
            SurvivalInventoryScreen.renderEntity(x + PLAYER_X, y + PLAYER_Y, PLAYER_SIZE, lookX.toFloat(), lookY.toFloat(), avatar)
        } finally {
            minecraft.player = player
            dispatcher.camera = camera
            dispatcher.world = world
        }
    }

    companion object {
        private const val LABEL_X = 86
        private const val LABEL_Y = 16
        private const val LABEL_COLOR = 4210752
        private const val SLOT_SIZE = 16
        private const val PLAYER_X = 51
        private const val PLAYER_Y = 75
        private const val PLAYER_SIZE = 30
        private const val LOOK_DISTANCE = 40.0
        private const val SWAY_SPEED = 0.9
        private const val SWAY_REACH = 1.2
        private const val NOD_SPEED = 0.6
        private const val NOD_BASE = -0.35
        private const val NOD_REACH = 0.25
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        private val LOG = LoggerFactory.getLogger("polyplus/render-screen")
        private val noted = HashSet<String>()

        private fun note(reason: String): RenderScreenInventory? {
            if (noted.add(reason)) LOG.info("[render-screen] inventory capture skipped: {}", reason)
            return null
        }

        fun create(): RenderScreenInventory? {
            val level = LivePlayerAvatars.level() ?: return note("no lobby level")
            val avatar = LivePlayerAvatars.player(level) ?: return note("no avatar")
            avatar.shownSkin = LivePlayerSkins.base()
            return RenderScreenInventory(avatar)
        }
    }
}
*///?}
