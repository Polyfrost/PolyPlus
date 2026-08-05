package org.polyfrost.polyplus.client.gui

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusConfig

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
        if (!PolyPlusConfig.panoramaInAllMenus) return false
        if (Minecraft.getInstance().level == null) return true
        return isLoadingScreen(screen)
    }

    private fun isLoadingScreen(screen: Any?): Boolean {
        if (screen is net.minecraft.client.gui.screens.GenericMessageScreen) return true
        if (screen is net.minecraft.client.gui.screens.LevelLoadingScreen) return true
        //? if < 1.21.10 {
        /*if (screen is net.minecraft.client.gui.screens.ReceivingLevelScreen) return true
        *///?}
        return false
    }

    @JvmStatic
    fun active(screen: Screen): Boolean = menusActive(screen) && screen !is ComposeScreen

    private fun backdropWanted(screen: Screen, onPanoramaPass: Boolean): Boolean {
        if (!PolyPlusConfig.panoramaInAllMenus) return false
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
        if (!PolyPlusConfig.panoramaInAllMenus || panoramaBackdrop()) return false
        val screen = currentScreen() ?: return false
        return screen !is PolyPlusMainMenuScreen && screen !is PolyPlusOnboardingScreen
    }

    //? if >= 26.1 {
    @JvmStatic
    fun drawBackdrop(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, screen: Screen, onPanoramaPass: Boolean): Boolean {
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
    //?} else {
    /*@JvmStatic
    fun drawBackdrop(ctx: net.minecraft.client.gui.GuiGraphics, screen: Screen, onPanoramaPass: Boolean): Boolean {
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
    *///?}

    private fun currentScreen() =
        //? if >= 26.2 {
        /*Minecraft.getInstance().gui.screen()
        *///?} else {
        Minecraft.getInstance().screen
        //?}
}
