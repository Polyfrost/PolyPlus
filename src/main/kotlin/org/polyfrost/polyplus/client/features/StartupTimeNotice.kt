package org.polyfrost.polyplus.client.features

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.lang.management.ManagementFactory
import java.util.Locale

object StartupTimeNotice {
    private var done = false

    fun register() {
        eventHandler { _: TickEvent.End -> check() }.register()
    }

    private fun check() {
        if (done || !loaded()) return
        done = true
        if (!PolyPlusConfig.startupTimeNotification) return
        val startMillis = runCatching { ManagementFactory.getRuntimeMXBean().startTime }.getOrNull() ?: return
        val seconds = (System.currentTimeMillis() - startMillis) / 1000.0
        runCatching { Notifications.info("PolyPlus", String.format(Locale.ROOT, "Game loaded in %.1fs", seconds)) }
    }

    private fun loaded(): Boolean {
        if (StartupOverlay.active || ClientPlatform.currentScreen() == null) return false
        val mc = Minecraft.getInstance()
        //? if >= 26.2 {
        return mc.isGameLoadFinished && mc.gui.overlay() == null
        //?} elif > 1.8.9 {
        /*return mc.isGameLoadFinished && mc.overlay == null
        *///?} else {
        /*return mc != null
        *///?}
    }
}
