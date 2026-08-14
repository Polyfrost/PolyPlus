package org.polyfrost.polyplus.client.social

import com.mojang.blaze3d.platform.InputConstants
//? if >= 26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
*///?}
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.gui.SocialOverlayScreen
import org.lwjgl.glfw.GLFW
import org.polyfrost.polyplus.client.PolyPlusConfig

object SocialOverlay {
    private val logger = LogManager.getLogger("polyplus/social-overlay")
    private var previousScreen: Screen? = null

    @Volatile
    private var pendingAutoHostCurrentWorld = false

    fun registerKeybind() {
    }

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
        /*val current = mc.gui.screen()
        *///?} else {
        val current = mc.screen
        //?}

        when (current) {
            is SocialOverlayScreen -> close()
            null, is TitleScreen, is PolyPlusMainMenuScreen -> open(current)
            else -> logger.debug("Ignoring Shift+P: {} is open and isn't safe to interrupt", current.javaClass.simpleName)
        }
    }

    /** Explicit open, e.g. from a button - always allowed regardless of the current screen. */
    fun open(from: Screen? = currentScreen()) {
        previousScreen = from
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        /*mc.gui.setScreen(SocialOverlayScreen())
        *///?} else {
        mc.setScreen(SocialOverlayScreen())
        //?}

        SocialRefresh.refreshAll()
    }

    fun close() {
        val mc = Minecraft.getInstance()
        mc.execute {
            //? if >= 26.2 {
            /*if (mc.gui.screen() is SocialOverlayScreen) mc.gui.setScreen(previousScreen)
            *///?} else {
            if (mc.screen is SocialOverlayScreen) mc.setScreen(previousScreen)
            //?}
        }
    }

    private fun currentScreen(): Screen? {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        /*return mc.gui.screen()
        *///?} else {
        return mc.screen
        //?}
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
