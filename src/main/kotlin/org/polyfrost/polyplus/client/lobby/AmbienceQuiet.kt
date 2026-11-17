package org.polyfrost.polyplus.client.lobby

import net.minecraft.client.Minecraft
import org.polyfrost.oneconfig.internal.ui.sound.UiSounds
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.pausescreen.PauseEvergreenMedia
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.compat.Mods

object AmbienceQuiet {
    private const val CHECK_TICKS = 10

    private var ticks = 0
    private var watching = false

    @JvmStatic
    var quiet = false
        private set

    fun tick() {
        if (++ticks < CHECK_TICKS) return
        ticks = 0
        val watch = PolyPlusLobbyConfig.muteAmbienceDuringMedia && Mods.evergreenHud && PauseEvergreenMedia.supported() && menusOpen()
        if (watch != watching) {
            watching = watch
            if (watch) PauseEvergreenMedia.acquire() else PauseEvergreenMedia.release()
        }
        val now = watching && PauseEvergreenMedia.playing()
        if (now == quiet) return
        quiet = now
        runCatching { (UiSounds as Any as UiAmbienceAccess).`polyplus$setQuiet`(now) }
    }

    private fun menusOpen(): Boolean = Minecraft.getInstance().level == null || ClientPlatform.currentScreen() != null
}
