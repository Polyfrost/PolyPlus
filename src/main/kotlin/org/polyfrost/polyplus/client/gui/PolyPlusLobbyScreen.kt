@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.runtime.Composable
import com.mojang.blaze3d.platform.InputConstants
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.components.ProvidePopupSounds
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.oneconfig.internal.OneConfigConfig
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyShell
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyRoot
import org.polyfrost.polyplus.client.gui.lobbyshell.StartupAccessibility
import org.polyfrost.polyplus.client.lobby.LobbyAntics
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.StartupOverlay
//? if > 1.8.9 {
import java.nio.file.Path
import org.polyfrost.polyplus.client.gui.screens.packs.PackDrops
//?}

class PolyPlusLobbyScreen internal constructor(firstPage: LobbyPage) : OneConfigComposeScreen(previous = null) {
    constructor() : this(LobbyPage.Home)

    internal val lobby = LobbyShell(this, firstPage)

    override val closeOnEscape: Boolean get() = false

    override val blurBackground: Boolean get() = false

    override val dimBackground: Boolean get() = false

    override fun init() {
        lobby.open()
        super.init()
    }

    //? if > 1.8.9 {
    override fun onFilesDrop(files: List<Path>) {
        if (!PackDrops.drop(files)) super.onFilesDrop(files)
    }
    //?}

    override fun removed() {
        lobby.close()
        super.removed()
    }

    override fun tick() {
        super.tick()
        if (LobbyScene.cannotRun()) MainMenuReplacement.fallBackToVanilla() else lobby.tick()
    }

    private var keyNavigated = false

    override fun handleMouseClicked(button: Int): Boolean {
        if (WheelOverlay.isOpenOverLobby) {
            WheelOverlay.consumeButton(button, true)
            return true
        }
        if (emoteWheelReady() && EmoteWheelKeybind.claimsButton(button)) return true
        keyNavigated = false
        return StartupOverlay.active || lobby.skipIntro()
    }

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (WheelOverlay.isOpenOverLobby) {
            if (key == InputConstants.KEY_ESCAPE) WheelOverlay.consumeEscape()
            return key !in wheelKeys
        }
        if (emoteWheelReady() && EmoteWheelKeybind.claimsKey(key, modifiers)) return true
        if (key in navigationKeys) keyNavigated = true
        LobbyAntics.key(key, lobby.calm())
        if (lobby.setup && StartupAccessibility.handleKey(key, modifiers)) return true
        if (lobby.setup && OnboardingKeyCapture.capture(key)) return true
        if (lobby.skipIntro()) return true
        if (!lobby.setup && !TextInputFocus.isTyping && opensOneConfig(key, modifiers)) {
            PolyPlusOneConfigIntegration.openSettingsReturning()
            return true
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (lobby.escape()) return true
            //? if = 1.8.9 {
            /*return true
            *///?}
        }
        val enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
        if (enter && (lobby.tour != null || !keyNavigated || !LobbyA11y.keyboardFocused()) && lobby.openSocial()) return true
        return super.handleKeyPressed(key, modifiers)
    }

    //? if >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        LobbyScene.start()
        if (!LobbyScene.showing()) ctx.fill(0, 0, width, height, 0xFF11171C.toInt())
        lobby.feed.pull()
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
        lobby.feed.push()
        StartupAccessibility.frame()
        LobbyA11y.frame()
        StartupOverlay.screenFrame(client, ctx)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
    }
    //?} elif = 1.8.9 {
    /*override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        LobbyScene.start()
        if (!LobbyScene.showing()) fill(0, 0, width, height, 0xFF11171C.toInt())
        lobby.feed.pull()
        super.render(mouseX, mouseY, tickDelta)
        lobby.feed.push()
        StartupAccessibility.frame()
        LobbyA11y.frame()
        StartupOverlay.legacyFrame()
    }
    *///?} else {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        LobbyScene.start()
        if (!LobbyScene.showing()) ctx.fill(0, 0, width, height, 0xFF11171C.toInt())
        lobby.feed.pull()
        super.render(ctx, mouseX, mouseY, tickDelta)
        lobby.feed.push()
        StartupAccessibility.frame()
        LobbyA11y.frame()
        StartupOverlay.screenFrame(client, ctx)
    }

    override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
    }
    *///?}

    @Composable
    override fun Content() {
        ProvidePopupSounds(false) { LobbyRoot(lobby, this) }
        WheelOverlay.LobbyLayer()
    }

    internal fun showPage(page: LobbyPage) = lobby.show(page)

    internal fun emoteWheelReady(): Boolean =
        LobbyScene.showing() && !LobbyScene.introPlaying() && !StartupOverlay.active && !lobby.page.frosted && lobby.calm()

    private fun opensOneConfig(key: Int, modifiers: Int): Boolean {
        val bind = OneConfigConfig.oneConfigKeybind
        if (bind.keyCodes?.contains(key) != true || bind.mouseBtns?.isNotEmpty() == true) return false
        return bind.test(KeybindManager.heldKeys + key, emptySet(), (modifiers and MODIFIER_MASK).toByte())
    }

    private companion object {
        const val MODIFIER_MASK = 0xF
        val navigationKeys = intArrayOf(
            InputConstants.KEY_TAB, InputConstants.KEY_UP, InputConstants.KEY_DOWN, InputConstants.KEY_LEFT, InputConstants.KEY_RIGHT,
        )

        val wheelKeys = navigationKeys + intArrayOf(
            InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE,
            InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4,
            InputConstants.KEY_5, InputConstants.KEY_6, InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9,
            InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1, InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3,
            InputConstants.KEY_NUMPAD4, InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7,
            InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9,
        )
    }
}
