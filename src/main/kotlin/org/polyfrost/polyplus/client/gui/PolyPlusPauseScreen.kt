package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if > 1.8.9 {
import androidx.compose.ui.graphics.toComposeImageBitmap
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.AbstractStringWidget
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.SpriteIconButton
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.resources.Identifier
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.compose.BlurRenderer
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx
import org.polyfrost.polyplus.mixin.client.access.SpriteIconButtonAccessor
//?} else {
/*import net.minecraft.client.gui.screen.menu.GameMenuScreen as PauseScreen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.resource.language.I18n
import org.polyfrost.polyplus.mixin.client.access.ScreenAccessor
*///?}

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >= 1.21.9 {
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent
//?}

/**
 * Compose pause menu. Its widgets are mirrored from a vanilla [PauseScreen] that is never shown, at the positions
 * vanilla gives them, so per-version differences and buttons other mods add carry over, and every submenu stays
 * vanilla. Submenus return to that hidden screen, which [replace] swaps for a fresh one of these.
 */
class PolyPlusPauseScreen(private val vanilla: PauseScreen) : ComposeScreen(RenderMode.CONTINUOUS) {
    private var layout by mutableStateOf(PauseLayout(1, emptyList(), null))
    //? if > 1.8.9
    private val sprites = HashMap<Identifier, ImageBitmap?>()

    override fun init() {
        super.init()
        syncVanilla()
    }

    private fun syncVanilla() {
        //? if >= 1.21.11 {
        vanilla.init(width, height)
        //?} else {
        /*vanilla.init(Minecraft.getInstance(), width, height)
        *///?}
        val widgets = mirrorWidgets()
        val buttons = widgets.filterIsInstance<PauseButton>()
        val menuLeft = buttons.minOfOrNull { it.x } ?: 0
        val menuRight = buttons.maxOfOrNull { it.x + it.width } ?: 0
        layout = PauseLayout(width, widgets, PauseMenuPanel.layout(width, menuLeft, menuRight, buttons.map { it.y }))
    }

    //? if > 1.8.9 {
    private fun mirrorWidgets(): List<PauseWidget> =
        vanilla.children()
            .filterIsInstance<AbstractWidget>()
            .filter { it.visible }
            .mapNotNull { widget ->
                when (widget) {
                    is AbstractButton -> PauseButton(
                        widget.x, widget.y, widget.width, widget.height,
                        widget.message.string, widget.isActive,
                        (widget as? SpriteIconButton)?.let { spriteIcon(it) },
                    ) { press(widget) }
                    is AbstractStringWidget -> PauseText(widget.x, widget.y, widget.width, widget.height, widget.message.string)
                    else -> null
                }
            }

    private fun press(button: AbstractButton) {
        ClientPlatform.runOnMain {
            if (!button.isActive) return@runOnMain
            //? if >= 1.21.9 {
            button.onPress(KeyEvent(InputConstants.KEY_RETURN, 0, 0))
            //?} else {
            /*button.onPress()
            *///?}
        }
    }

    private fun spriteIcon(button: SpriteIconButton): PauseIcon? {
        val access = button as SpriteIconButtonAccessor
        //? if >= 1.21.9 {
        val id = access.`polyplus$sprite`().get(button.isActive, false)
        //?} else {
        /*val id = access.`polyplus$sprite`()
        *///?}
        val image = sprites.getOrPut(id) { loadSprite(id) } ?: return null
        return PauseIcon(image, access.`polyplus$spriteWidth`(), access.`polyplus$spriteHeight`())
    }

    private fun loadSprite(id: Identifier): ImageBitmap? = runCatching {
        val location = Identifier.fromNamespaceAndPath(id.namespace, "textures/gui/sprites/${id.path}.png")
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
            ?: return@runCatching null
        SkiaImage.makeFromEncoded(resource.open().use { it.readBytes() }).toComposeImageBitmap()
    }.getOrNull()
    //?} else {
    /*// 1.8.9's pause menu draws its title in render instead of as a widget, and its buttons are always 20px tall
    private fun mirrorWidgets(): List<PauseWidget> {
        val access = vanilla as ScreenAccessor
        val title = PauseText(0, LEGACY_TITLE_Y, width, LEGACY_TEXT_HEIGHT, I18n.translate("menu.game"))
        return listOf(title) + access.`polyplus$buttons`().filter { it.visible }.map { button ->
            PauseButton(button.x, button.y, button.getWidth(), LEGACY_BUTTON_HEIGHT, button.message, button.active, null) { press(button) }
        }
    }

    private fun press(button: ButtonWidget) {
        ClientPlatform.runOnMain { if (button.active) (vanilla as ScreenAccessor).`polyplus$buttonClicked`(button) }
    }
    *///?}

    // vanilla's GUI blur doesn't reach the Compose layer, so the world is blurred the same way OneConfig's own
    // screens do it, at the strength set by vanilla's Menu Background Blur option. 1.8.9 has no menu blur.
    // ComposeScreen's resize doesn't re-init, so the hidden screen is caught up to the window size here too,
    // which also covers GUI scale changes
    //? if >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        blurWorld { if (SkiaCtx.isDeferredComposeBackend) { ctx.nextStratum(); ctx.blurBeforeThisStratum(); SkiaCtx.requestBlurSnapshot() } }
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (vanilla.width != width || vanilla.height != height) syncVanilla()
        extractMenuBackground(ctx)
    }
    //?} elif > 1.8.9 {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        //? if >= 1.21.10 {
        blurWorld { if (SkiaCtx.isDeferredComposeBackend) { ctx.nextStratum(); ctx.blurBeforeThisStratum(); SkiaCtx.requestBlurSnapshot() } }
        //?} else {
        /^blurWorld {}
        ^///?}
        super.render(ctx, mouseX, mouseY, tickDelta)
    }

    override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (vanilla.width != width || vanilla.height != height) syncVanilla()
        renderMenuBackground(ctx)
    }
    *///?} else {
    /*override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (vanilla.width != width || vanilla.height != height) syncVanilla()
        renderBackground()
        super.render(mouseX, mouseY, tickDelta)
    }
    *///?}

    //? if > 1.8.9 {
    private inline fun blurWorld(deferredBlur: () -> Unit) {
        val blurriness = Minecraft.getInstance().options.menuBackgroundBlurriness
        if (blurriness <= 0) return
        deferredBlur()
        BlurRenderer.drawBlur(blurriness * BLUR_RADIUS_PER_STEP)
    }

    override fun isPauseScreen(): Boolean = vanilla.isPauseScreen

    // ComposeScreen.onClose only releases the scene and never clears the screen, which left this one open and
    // swallowing input; removed() releases the scene once the screen is cleared
    override fun onClose() {
        Minecraft.getInstance().execute { if (ClientPlatform.currentScreen() === this) ClientPlatform.setScreen(null) }
    }
    //?} else {
    /*override fun shouldPauseGame(): Boolean = true
    *///?}

    @Composable
    override fun compose() {
        Theme { PauseMenu(this, layout) }
    }

    companion object {
        @JvmStatic
        fun replace(screen: Screen?): Screen? =
            if (PolyPlusMainMenuConfig.usePolyPlusPauseMenu && screen is PauseScreen && showsMenu(screen)) {
                PolyPlusPauseScreen(screen)
            } else {
                screen
            }

        private fun showsMenu(screen: PauseScreen): Boolean =
            //? if > 1.8.9 {
            screen.showsPauseMenu()
            //?} else {
            /*true // 1.8.9 has no menu-less pause screen
            *///?}
    }
}

private class PauseLayout(val guiWidth: Int, val widgets: List<PauseWidget>, val panel: PauseMenuPanel.Layout?)

private sealed class PauseWidget(val x: Int, val y: Int, val width: Int, val height: Int)

private class PauseText(x: Int, y: Int, width: Int, height: Int, val text: String) : PauseWidget(x, y, width, height)

private class PauseButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val label: String,
    val active: Boolean,
    val icon: PauseIcon?,
    val press: () -> Unit,
) : PauseWidget(x, y, width, height)

private class PauseIcon(val image: ImageBitmap, val width: Int, val height: Int)

// vanilla's default Menu Background Blur of 5 lands on the radius OneConfig uses for its own fullscreen blur
private const val BLUR_RADIUS_PER_STEP = 8f / 5f

//? if = 1.8.9 {
/*private const val LEGACY_TITLE_Y = 40
private const val LEGACY_TEXT_HEIGHT = 9
private const val LEGACY_BUTTON_HEIGHT = 20
*///?}

private fun gui(px: Int): Dp = (px * PauseMenuPanel.DP_PER_GUI_PIXEL).dp

@Composable
private fun PauseMenu(screen: Screen, layout: PauseLayout) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // scaled so the screen is exactly as many vanilla GUI pixels wide as vanilla's, which puts every widget
        // where vanilla draws it and makes the whole menu follow vanilla's GUI scale
        val scale = maxWidth.value / (layout.guiWidth * PauseMenuPanel.DP_PER_GUI_PIXEL)
        if (!scale.isFinite() || scale <= 0f) return@BoxWithConstraints
        CompositionLocalProvider(LocalDensity provides scaledDensity(scale)) {
            layout.widgets.forEach { widget ->
                val modifier = Modifier.offset(gui(widget.x), gui(widget.y)).size(gui(widget.width), gui(widget.height))
                when (widget) {
                    is PauseText -> PauseTitle(widget.text, modifier)
                    is PauseButton -> PausePill(widget, modifier)
                }
            }
            layout.panel?.let { PlayerPanel(screen, it) }
        }
    }
}

@Composable
private fun PlayerPanel(screen: Screen, panel: PauseMenuPanel.Layout) {
    val x = gui(panel.x)
    val width = gui(panel.width)
    val buttonHeight = gui(PauseMenuPanel.BUTTON_HEIGHT)
    // the preview keeps the title screen's proportions, shrunk when the screen is tight
    if (panel.showsPreview) {
        Box(Modifier.offset(gui(panel.previewX), gui(panel.previewY))) {
            CompositionLocalProvider(LocalDensity provides scaledDensity(panel.previewScale)) {
                MenuPlayerPreview(Modifier.width(gui(PauseMenuPanel.WIDTH)))
            }
        }
    }
    panel.socialY?.let { SocialPill(screen, Modifier.offset(x, gui(it)).size(width, buttonHeight)) }
    WardrobePill(Modifier.offset(x, gui(panel.wardrobeY)).size(width, buttonHeight))
    panel.hostWorldY?.let { HostWorldButton(screen, Modifier.offset(x, gui(it)).size(width, buttonHeight)) }
    panel.leftX?.let { gui(it) }?.let { leftX ->
        PlaytimePill(Modifier.offset(leftX, gui(panel.playtimeY)).size(width, buttonHeight))
        PillButton("Settings", ASSETS + "settings-02.svg", Modifier.offset(leftX, gui(panel.wardrobeY)).size(width, buttonHeight), PolyPlusOneConfigIntegration::openMods)
    }
}

@Composable
private fun PlaytimePill(modifier: Modifier) {
    var label by remember { mutableStateOf(PauseMenuPanel.playtimeLabel()) }
    // the counter isn't observable and the server's reply lands a moment after the request
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            label = PauseMenuPanel.playtimeLabel()
        }
    }
    Box(modifier.pillSurface(null), contentAlignment = Alignment.Center) {
        SocialText(label, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
private fun PauseTitle(text: String, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        SocialText(
            text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.wrapContentSize(unbounded = true),
        )
    }
}

@Composable
private fun PausePill(button: PauseButton, modifier: Modifier) {
    Box(modifier.pillSurface(button.press, enabled = button.active), contentAlignment = Alignment.Center) {
        val icon = button.icon
        if (icon != null) {
            Image(
                icon.image,
                contentDescription = button.label,
                filterQuality = FilterQuality.None,
                modifier = Modifier.size(gui(icon.width), gui(icon.height)),
            )
        } else {
            SocialText(
                button.label,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
