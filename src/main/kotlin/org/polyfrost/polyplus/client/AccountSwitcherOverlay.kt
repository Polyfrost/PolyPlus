package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.TitleScreen
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.gui.AccountSwitcherScreen
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen

//? if = 1.8.9 {
/*import org.polyfrost.polyplus.client.social.execute
import org.polyfrost.polyplus.client.social.setScreen
*///?}

object AccountSwitcherOverlay {
    private val logger = LogManager.getLogger("polyplus/account-switcher-overlay")

    fun open(from: Screen? = currentScreen()) {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        mc.gui.setScreen(AccountSwitcherScreen(from))
        //?} else {
        /*mc.setScreen(AccountSwitcherScreen(from))
        *///?}

    }

    fun close() {
        val mc = Minecraft.getInstance()
        mc.execute {
            //? if >= 26.2 {
            (mc.gui.screen() as? AccountSwitcherScreen)?.let { mc.gui.setScreen(it.previousScreen) }
            //?} else {
            /*(mc.screen as? AccountSwitcherScreen)?.let { mc.setScreen(it.previousScreen) }
            *///?}
        }
    }

    //? if = 1.8.9 {
    /*fun restoreAfterEscape(previousScreen: Screen?) {
        val mc = Minecraft.getInstance()
        if (mc.screen == null || mc.screen is TitleScreen) mc.setScreen(previousScreen)
    }
    *///?}

    private fun currentScreen(): Screen? {
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        return mc.gui.screen()
        //?} else {
        /*return mc.screen
        *///?}
    }
}