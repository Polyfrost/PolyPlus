package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.PolyPlusConfig
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
    internal var stepBack by mutableStateOf(style == PauseScreenStyle.STEP_BACK)
        private set
    internal var moreOpen by mutableStateOf(false)
    private var decided = false
    internal var resumeBounds = Rect.Zero
    private var resumeArmed = false

    private fun overResume(): Boolean = resumeBounds.contains(pointerPosition())

    private fun resumeOnRelease() {
        val armed = resumeArmed
        resumeArmed = false
        if (armed && !moreOpen && overResume()) resume()
    }

    override fun init() {
        super.init()
        capture.sync(width, height)
        if (!decided) {
            decided = true
            stepBack = style == PauseScreenStyle.STEP_BACK && PauseStage.enter()
        }
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

    override fun onClose() {
        if (ClientPlatform.currentScreen() === this) ClientPlatform.setScreen(null)
    }
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
        if (key == InputConstants.KEY_ESCAPE && moreOpen) {
            moreOpen = false
            return true
        }
        return false
    }

    internal fun resume() {
        val entry = capture.entry(PauseSlot.RESUME)
        if (entry != null) entry.press() else ClientPlatform.setScreen(null)
    }

    internal fun openClassic() {
        moreOpen = false
        PauseRouter.openClassic(vanilla)
    }

    internal fun openSocial() = SocialOverlay.open(this)

    internal fun openHost() = SocialOverlay.openHostCurrentWorld(this)

    internal fun openWardrobe() = PolyPlusOneConfigIntegration.openCosmetics()

    internal fun openSettings() = PolyPlusOneConfigIntegration.openSettings()

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
}

object PauseRouter {
    private var classic: Screen? = null

    internal fun openClassic(screen: Screen) {
        classic = screen
        ClientPlatform.setScreen(screen)
    }

    @JvmStatic
    fun leaving(next: Screen?): Boolean = next == null && ClientPlatform.currentScreen() is OneClientPauseScreen

    @JvmStatic
    fun replace(screen: Screen?): Screen? {
        if (screen !is PauseScreen) return screen
        if (screen === classic) {
            classic = null
            return screen
        }
        if (!showsMenu(screen)) return screen
        val style = PolyPlusConfig.pauseScreenStyle
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
