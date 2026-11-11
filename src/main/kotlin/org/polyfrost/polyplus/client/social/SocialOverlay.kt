package org.polyfrost.polyplus.client.social

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.PolyPlusLobbyScreen
import org.polyfrost.polyplus.client.gui.ScreenFocus
import org.polyfrost.polyplus.client.gui.SocialOverlayScreen
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyPage
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.privacy.PrivacyConsent

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.PauseScreen
//?} else {
/*import net.minecraft.client.gui.screen.menu.GameMenuScreen as PauseScreen
*///?}

object SocialOverlay {
    private val logger = LogManager.getLogger("polyplus/social-overlay")

    @Volatile
    private var pendingAutoHostCurrentWorld = false

    @Volatile
    private var pendingVoice = false

    fun openHostCurrentWorld(from: Screen? = currentScreen()) {
        pendingAutoHostCurrentWorld = true
        open(from)
    }

    fun consumeAutoHostCurrentWorld(): Boolean {
        val was = pendingAutoHostCurrentWorld
        pendingAutoHostCurrentWorld = false
        return was
    }

    fun openVoice(from: Screen? = currentScreen()) {
        pendingVoice = true
        open(from)
    }

    fun consumeOpenVoice(): Boolean {
        val was = pendingVoice
        pendingVoice = false
        return was
    }

    fun toggle() {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        val current = mc.gui.screen()
        //?} else {
        /*val current = mc.screen
        *///?}

        if (PolyPlusConfig.socialsMenuKeybind.keyCodes?.contains(InputConstants.KEY_TAB) == true && ScreenFocus.holds(current)) return
        when {
            current is SocialOverlayScreen -> close()
            current is PolyPlusLobbyScreen -> if (!current.lobby.setup) current.showPage(if (current.lobby.page == LobbyPage.Social) LobbyPage.Home else LobbyPage.Social)
            current == null || current is TitleScreen || MainMenuReplacement.isMenu(current) -> open(current)
            current is OneClientPauseScreen || current is PauseScreen -> if (PrivacyConsent.allowsOnlineServices()) open(current)
            else -> logger.debug("Ignoring socials shortcut: {} is open and isn't safe to interrupt", current.javaClass.simpleName)
        }
    }

    /** Explicit open, e.g. from a button - always allowed regardless of the current screen. */
    fun open(from: Screen? = currentScreen()) {
        val mc = Minecraft.getInstance()
        //? if > 1.8.9 {
        mc.gameMode?.stopDestroyBlock()
        //?} else {
        /*mc.interactionManager?.stopMiningBlock()
        *///?}
        //? if >= 26.2 {
        mc.gui.setScreen(SocialOverlayScreen(from))
        //?} else {
        /*mc.setScreen(SocialOverlayScreen(from))
        *///?}

        if (PrivacyConsent.allowsOnlineServices()) SocialRefresh.refreshAll()
    }

    fun close() {
        (currentScreen() as? SocialOverlayScreen)?.dismiss()
    }

    fun isShowing(): Boolean = when (val screen = currentScreen()) {
        is SocialOverlayScreen -> true
        is PolyPlusLobbyScreen -> screen.lobby.page == LobbyPage.Social
        else -> false
    }

    fun showsChat(): Boolean {
        if (!Minecraft.getInstance().isWindowActive) return false
        return when (val screen = currentScreen()) {
            is SocialOverlayScreen -> true
            is PolyPlusLobbyScreen -> !screen.lobby.accountsOpen
            else -> false
        }
    }

    private fun currentScreen(): Screen? {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        return mc.gui.screen()
        //?} else {
        /*return mc.screen
        *///?}
    }
}

/** One place to kick every repository's initial fetch when the overlay opens. */
internal object SocialRefresh {
    fun refreshAll() {
        FriendsRepository.refreshAll()
        GroupsRepository.refreshGroups()
        // GlobalChatRepository.refreshHistory() // Global chat is disabled for now.
        SessionsRepository.refreshIncoming()
        SpecialChatRepository.refreshTargets()
    }
}
