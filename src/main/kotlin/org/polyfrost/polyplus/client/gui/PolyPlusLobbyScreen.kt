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
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyShell
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyRoot
import org.polyfrost.polyplus.client.gui.lobbyshell.StartupAccessibility
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.StartupOverlay

class PolyPlusLobbyScreen internal constructor(firstPage: LobbyPage) : ComposeScreen(RenderMode.CONTINUOUS) {
    constructor() : this(LobbyPage.Home)

    internal val lobby = LobbyShell(this, firstPage)

    //? if > 1.8.9 {
    override fun shouldCloseOnEsc(): Boolean = false
    //?}

    override fun init() {
        lobby.open()
        super.init()
    }

    override fun removed() {
        lobby.close()
        super.removed()
    }

    override fun tick() {
        super.tick()
        if (LobbyScene.cannotRun()) MainMenuReplacement.fallBackToVanilla() else lobby.tick()
    }

    override fun handleMouseClicked(button: Int): Boolean = StartupOverlay.active || lobby.skipIntro()

    override fun handleKeyPressed(key: Int, modifiers: Int): Boolean {
        if (lobby.setup && StartupAccessibility.handleKey(key, modifiers)) return true
        if (lobby.setup && OnboardingKeyCapture.capture(key)) return true
        if (lobby.skipIntro()) return true
        if (key == InputConstants.KEY_ESCAPE) {
            if (lobby.escape()) return true
            //? if = 1.8.9 {
            /*return true
            *///?}
        }
        val enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER
        if (enter && (lobby.tour != null || !LobbyA11y.keyboardFocused()) && lobby.openSocial()) return true
        return super.handleKeyPressed(key, modifiers)
    }

    //? if >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        LobbyScene.start()
        if (!LobbyScene.showing()) ctx.fill(0, 0, width, height, 0xFF11171C.toInt())
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
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
        super.render(mouseX, mouseY, tickDelta)
        StartupAccessibility.frame()
        LobbyA11y.frame()
        StartupOverlay.legacyFrame()
    }
    *///?} else {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        LobbyScene.start()
        if (!LobbyScene.showing()) ctx.fill(0, 0, width, height, 0xFF11171C.toInt())
        super.render(ctx, mouseX, mouseY, tickDelta)
        StartupAccessibility.frame()
        LobbyA11y.frame()
        StartupOverlay.screenFrame(client, ctx)
    }

    override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
    }
    *///?}

    @Composable
    override fun compose() {
        ProvideOneConfigTheme {
            LobbyRoot(lobby, this)
        }
    }

    internal fun showPage(page: LobbyPage) = lobby.show(page)
}
