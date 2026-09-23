package org.polyfrost.polyplus.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.GenericMessageScreen
import net.minecraft.client.gui.screens.LevelLoadingScreen
//?} else {
/*import net.minecraft.client.gui.GuiElement
import net.minecraft.client.gui.screen.DownloadingTerrainScreen
import net.minecraft.client.gui.screen.ProgressScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.render.platform.GlStateManager
import org.polyfrost.oneconfig.internal.legacy.LegacyPanoramaTracker
import org.polyfrost.oneconfig.internal.ui.compose.opengl.StoredGLState
import org.polyfrost.polyplus.mixin.client.access.GuiElementInvoker
import org.polyfrost.polyplus.mixin.client.access.TitleScreenInvoker
*///?}

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if < 1.21.10 && > 1.8.9 {
/*import net.minecraft.client.gui.screens.ReceivingLevelScreen
*///?}

object MenuPanorama {
    @JvmField
    val BASE_COLOR: Int = 0xFF11171C.toInt()

    @JvmField
    val LIST_TINT: Int = 0x990D1116.toInt()

    @JvmStatic
    fun panoramaBackdrop(): Boolean = mainMenuPanoramaEnabled()

    @JvmStatic
    fun menusActive(): Boolean = menusActive(currentScreen())

    private fun menusActive(screen: Any?): Boolean {
        if (!PolyPlusMainMenuConfig.panoramaInAllMenus) return false
        //? if > 1.8.9 {
        if (Minecraft.getInstance().level == null) return true
        //?} else {
        /*if (Minecraft.getInstance().world == null) return true
        *///?}
        return isLoadingScreen(screen)
    }

    private fun isLoadingScreen(screen: Any?): Boolean {
        //? if > 1.8.9 {
        if (screen is GenericMessageScreen) return true
        if (screen is LevelLoadingScreen) return true
        //?} else {
        /*if (screen is ProgressScreen) return true
        if (screen is DownloadingTerrainScreen) return true
        *///?}
        //? if < 1.21.10 && > 1.8.9 {
        /*if (screen is ReceivingLevelScreen) return true
        *///?}
        return false
    }

    @JvmStatic
    fun active(screen: Screen): Boolean = menusActive(screen) && screen !is ComposeScreen

    @JvmStatic
    fun panoramaPassNeedsBackdrop(screen: Screen): Boolean = !panoramaBackdrop() || screen is ComposeScreen

    private fun backdropWanted(screen: Screen, onPanoramaPass: Boolean): Boolean {
        if (!PolyPlusMainMenuConfig.panoramaInAllMenus) return false
        if (!onPanoramaPass && !menusActive(screen)) return false
        return screen !is PolyPlusMainMenuScreen && screen !is PolyPlusOnboardingScreen
    }

    private var drawnThisPass = false
    private var filledThisFrame = false

    @JvmStatic
    fun beginPass() {
        drawnThisPass = false
        filledThisFrame = false
    }

    @JvmStatic
    fun backdropDrawn(): Boolean = drawnThisPass

    @JvmStatic
    fun backdropFilled(): Boolean = filledThisFrame

    @JvmStatic
    fun suppressPanorama(): Boolean {
        if (!PolyPlusMainMenuConfig.panoramaInAllMenus || panoramaBackdrop()) return false
        val screen = currentScreen() ?: return false
        return screen !is PolyPlusMainMenuScreen && screen !is PolyPlusOnboardingScreen
    }

    //? if >= 26.1 {
    @JvmStatic
    fun drawBackdrop(ctx: GuiGraphicsExtractor, screen: Screen, onPanoramaPass: Boolean): Boolean {
        if (!backdropWanted(screen, onPanoramaPass)) return false
        if (drawnThisPass) return true
        if (!panoramaBackdrop()) {
            ctx.fill(0, 0, screen.width, screen.height, BASE_COLOR)
            filledThisFrame = true
        }
        val drew = MenuBackgroundPass.renderInline(ctx, panoramaBackdrop(), screen)
        if (drew) drawnThisPass = true
        return drew
    }
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun drawBackdrop(ctx: GuiGraphics, screen: Screen, onPanoramaPass: Boolean): Boolean {
        if (!backdropWanted(screen, onPanoramaPass)) return false
        if (drawnThisPass) return true
        if (!panoramaBackdrop()) {
            ctx.fill(0, 0, screen.width, screen.height, BASE_COLOR)
            filledThisFrame = true
        }
        val drew = MenuBackgroundPass.renderInline(ctx, panoramaBackdrop(), screen)
        if (drew) drawnThisPass = true
        return drew
    }
    *///?} else {
    /*@JvmStatic
    fun drawBackdrop(screen: Screen, onPanoramaPass: Boolean): Boolean {
        if (!backdropWanted(screen, onPanoramaPass)) return false
        if (drawnThisPass) return true
        if (!panoramaBackdrop() || !drawLegacyPanorama(screen)) {
            GuiElement.fill(0, 0, screen.width, screen.height, BASE_COLOR)
            filledThisFrame = true
        }
        val drew = MenuBackgroundPass.renderInline(panoramaBackdrop(), screen)
        if (drew) drawnThisPass = true
        return drew
    }

    private const val WASH_TOP = 0x80FFFFFF.toInt()
    private const val WASH_BOTTOM = 0x00FFFFFF
    private const val SHADE_TOP = 0x00000000
    private const val SHADE_BOTTOM = 0x80000000.toInt()

    private val panoramaGlState = StoredGLState(330)
    private var lastPanoramaTick = 0L

    @JvmStatic
    fun legacyPanorama(): TitleScreen? {
        LegacyPanoramaTracker.current()?.let { return it }
        val mc = Minecraft.getInstance()
        val title = runCatching {
            TitleScreen().also { it.init(mc, mc.window.guiScaledWidth, mc.window.guiScaledHeight) }
        }.getOrNull() ?: return null
        LegacyPanoramaTracker.capture(title)
        return title
    }

    private fun drawLegacyPanorama(screen: Screen): Boolean {
        val title = legacyPanorama() ?: return false
        val now = System.currentTimeMillis()
        if (now - lastPanoramaTick > 1000L) lastPanoramaTick = now
        while (now - lastPanoramaTick >= 50L) {
            title.tick()
            lastPanoramaTick += 50L
        }
        title.width = screen.width
        title.height = screen.height
        panoramaGlState.capture()
        try {
            GlStateManager.disableAlphaTest()
            (title as TitleScreenInvoker).`polyplus$drawBackground`(0, 0, (now - lastPanoramaTick) / 50f)
            GlStateManager.enableAlphaTest()
            val gradients = title as GuiElementInvoker
            gradients.`polyplus$fillGradient`(0, 0, screen.width, screen.height, WASH_TOP, WASH_BOTTOM)
            gradients.`polyplus$fillGradient`(0, 0, screen.width, screen.height, SHADE_TOP, SHADE_BOTTOM)
        } finally {
            panoramaGlState.restore()
        }
        return true
    }
    *///?}

    private fun currentScreen() =
        //? if >= 26.2 {
        Minecraft.getInstance().gui.screen()
        //?} else {
        /*Minecraft.getInstance().screen
        *///?}
}
