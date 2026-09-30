package org.polyfrost.polyplus.client.social

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.gui.SocialOverlayScreen
import org.polyfrost.polyplus.privacy.PrivacyConsent

object SocialOverlay {
    private val logger = LogManager.getLogger("polyplus/social-overlay")

    @Volatile
    private var pendingAutoHostCurrentWorld = false

    fun openHostCurrentWorld(from: Screen? = currentScreen()) {
        pendingAutoHostCurrentWorld = true
        open(from)
    }

    fun consumeAutoHostCurrentWorld(): Boolean {
        val was = pendingAutoHostCurrentWorld
        pendingAutoHostCurrentWorld = false
        return was
    }

    fun toggle() {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        val current = mc.gui.screen()
        //?} else {
        /*val current = mc.screen
        *///?}

        when (current) {
            is SocialOverlayScreen -> close()
            null, is TitleScreen, is PolyPlusMainMenuScreen -> open(current)
            else -> logger.debug("Ignoring Shift+P: {} is open and isn't safe to interrupt", current.javaClass.simpleName)
        }
    }

    /** Explicit open, e.g. from a button - always allowed regardless of the current screen. */
    fun open(from: Screen? = currentScreen()) {
        if (!PrivacyConsent.allowsOnlineServices()) {
            logger.debug("Ignoring social overlay open: online services are disabled (ToS not accepted)")
            return
        }
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        mc.gui.setScreen(SocialOverlayScreen(from))
        //?} else {
        /*mc.setScreen(SocialOverlayScreen(from))
        *///?}

        SocialRefresh.refreshAll()
    }

    fun close() {
        val mc = Minecraft.getInstance()
        mc.execute {
            //? if >= 26.2 {
            (mc.gui.screen() as? SocialOverlayScreen)?.let { mc.gui.setScreen(it.previousScreen) }
            //?} else {
            /*(mc.screen as? SocialOverlayScreen)?.let { mc.setScreen(it.previousScreen) }
            *///?}
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
