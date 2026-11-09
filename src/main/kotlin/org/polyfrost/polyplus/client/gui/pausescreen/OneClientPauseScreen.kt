@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeStack
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.internal.ThemeConfig
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.compat.replay.ReplayProviders
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.PolyPlusPauseConfig
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.Component
//?} else {
/*import net.minecraft.client.gui.screen.menu.GameMenuScreen as PauseScreen
import net.minecraft.client.resource.language.I18n
*///?}

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

//? if >= 1.21.10 {
import net.minecraft.client.input.MouseButtonEvent
//?}

class OneClientPauseScreen(private val vanilla: PauseScreen, private val style: PauseScreenStyle) : ComposeScreen(RenderMode.CONTINUOUS) {
    internal val capture = PauseCapture(vanilla)
    internal val stepBack = style == PauseScreenStyle.STEP_BACK || style == PauseScreenStyle.STEP_BACK_FLAT
    internal val stages: Boolean get() = style == PauseScreenStyle.STEP_BACK && !Motion.reduced
    internal var staged by mutableStateOf(false)
        private set
    internal var moreOpen by mutableStateOf(false)
    private var decided = false
    private var openedAt = 0L
    internal var resumeBounds = Rect.Zero
    private var resumeArmed = false
    private var resumed = false

    private fun overResume(): Boolean = resumeBounds.contains(pointerPosition())

    private fun resumeOnRelease() {
        val armed = resumeArmed
        resumeArmed = false
        if (armed && !moreOpen && overResume()) resume()
    }

    override fun init() {
        super.init()
        resumed = false
        capture.sync(width, height)
        if (!decided) {
            decided = true
            staged = stages && PauseStage.enter()
            openedAt = System.currentTimeMillis()
        }
    }

    override fun blurAlpha(): Float {
        if (!stepBack || staged || !ThemeConfig.enableBackgroundBlur) return 0f
        return ((System.currentTimeMillis() - openedAt).toFloat() / FLAT_BLUR_MS).coerceIn(0f, 1f)
    }

    private fun keepUp() {
        if (capture.width != width || capture.height != height) capture.sync(width, height)
        capture.refresh()
    }

    //? if >= 26.1 {
    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        keepUp()
        super.extractBackground(ctx, mouseX, mouseY, tickDelta)
    }
    //?} elif > 1.8.9 {
    /*override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        keepUp()
        super.renderBackground(ctx, mouseX, mouseY, tickDelta)
    }
    *///?} else {
    /*override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        keepUp()
        super.render(mouseX, mouseY, tickDelta)
    }
    *///?}

    override fun tick() {
        super.tick()
        capture.tick()
    }

    //? if > 1.8.9 {
    override fun isPauseScreen(): Boolean = vanilla.isPauseScreen

    override fun onClose() = closeIfCurrent()
    //?} else {
    /*override fun shouldPauseGame(): Boolean = true
    *///?}

    //? if >= 1.21.10 {
    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        resumeArmed = overResume()
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        val handled = super.mouseReleased(event)
        resumeOnRelease()
        return handled
    }
    //?} elif > 1.8.9 {
    /*override fun mouseClicked(x: Double, y: Double, button: Int): Boolean {
        resumeArmed = overResume()
        return super.mouseClicked(x, y, button)
    }

    override fun mouseReleased(x: Double, y: Double, button: Int): Boolean {
        val handled = super.mouseReleased(x, y, button)
        resumeOnRelease()
        return handled
    }
    *///?} else {
    /*override fun mouseClicked(x: Int, y: Int, button: Int) {
        resumeArmed = overResume()
        super.mouseClicked(x, y, button)
    }

    override fun mouseReleased(x: Int, y: Int, button: Int) {
        super.mouseReleased(x, y, button)
        resumeOnRelease()
    }
    *///?}

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (key == InputConstants.KEY_ESCAPE && EscapeStack.closeTop()) return true
        if (key == InputConstants.KEY_ESCAPE && moreOpen) {
            moreOpen = false
            return true
        }
        return false
    }

    internal fun resume() {
        if (resumed) return
        resumed = true
        val entry = capture.entry(PauseSlot.RESUME)
        if (entry != null) entry.press() else closeIfCurrent()
    }

    internal fun closeIfCurrent() {
        if (ClientPlatform.currentScreen() === this) ClientPlatform.setScreen(null)
    }

    internal fun openClassic() {
        moreOpen = false
        PauseRouter.openClassic(vanilla)
    }

    internal fun openSocial() = SocialOverlay.open(this)

    internal fun openHost() = SocialOverlay.openHostCurrentWorld(this)

    internal fun openWardrobe() {
        PauseRouter.holdPause()
        PauseRouter.openWardrobe(vanilla)
    }

    internal fun openSettings() {
        PauseRouter.holdPause()
        PolyPlusOneConfigIntegration.openSettings()
    }

    internal val online: Boolean get() = PrivacyConsent.allowsOnlineServices()

    internal val canHost: Boolean
        get() = online && PauseWorldAccess.singleplayer && capture.entry(PauseSlot.WORLD)?.label != hostLabel

    private val hostLabel: String get() = PauseWorldAccess.hostLabel

    @Composable
    override fun compose() {
        Theme {
            PauseRoot(this)
        }
    }

    private companion object {
        const val FLAT_BLUR_MS = 160f
    }
}

object PauseRouter {
    private var classic: Screen? = null
    private var wardrobeReturn: PauseScreen? = null
    private var wardrobePlayer: Any? = null
    private var wardrobeAsShown = false
    private var holding = false

    internal fun holdPause() {
        holding = PausePlatform.pausesWorld()
    }

    @JvmStatic
    fun holdsPause(): Boolean = holding && OneConfigUI.isOneConfigScreen(ClientPlatform.currentScreen())

    internal fun openClassic(screen: Screen) {
        classic = screen
        ClientPlatform.setScreen(screen)
    }

    fun openWardrobe(from: Screen, asShown: Boolean = false) {
        wardrobeReturn = from as? PauseScreen
        wardrobePlayer = PausePlatform.player()
        wardrobeAsShown = asShown
        PolyPlusOneConfigIntegration.openCosmetics()
    }

    private fun takeWardrobeReturn(): Screen? {
        val vanilla = wardrobeReturn ?: return null
        val player = wardrobePlayer
        val asShown = wardrobeAsShown
        wardrobeReturn = null
        wardrobePlayer = null
        wardrobeAsShown = false
        if (!OneConfigUI.isOneConfigScreen(ClientPlatform.currentScreen())) return null
        if (player == null || PausePlatform.player() !== player) return null
        if (asShown) classic = vanilla
        val next = replace(vanilla)
        if (!(next is OneClientPauseScreen && next.stages)) PauseStage.onScreen(null)
        return next
    }

    @JvmStatic
    fun leaving(next: Screen?): Boolean = next == null && ClientPlatform.currentScreen() is OneClientPauseScreen

    @JvmStatic
    fun replace(screen: Screen?): Screen? {
        if (screen == null) {
            holding = false
            return takeWardrobeReturn()
        }
        if (screen !is PauseScreen) return screen
        holding = false
        wardrobeReturn = null
        wardrobePlayer = null
        if (screen === classic) {
            classic = null
            return screen
        }
        if (!showsMenu(screen) || ReplayProviders.inReplay) return screen
        val style = PolyPlusPauseConfig.pauseScreenStyle
        return if (style == PauseScreenStyle.VANILLA) screen else OneClientPauseScreen(screen, style)
    }

    private fun showsMenu(screen: PauseScreen): Boolean =
        //? if > 1.8.9 {
        screen.showsPauseMenu()
        //?} else {
        /*true
        *///?}
}

internal object PauseWorldAccess {
    val singleplayer: Boolean
        //? if > 1.8.9 {
        get() = Minecraft.getInstance().hasSingleplayerServer()
        //?} else {
        /*get() = Minecraft.getInstance().server != null
        *///?}

    val hostLabel: String
        //? if > 1.8.9 {
        get() = Component.translatable("polyplus.hostWorld").string
        //?} else {
        /*get() = I18n.translate("polyplus.hostWorld")
        *///?}
}
