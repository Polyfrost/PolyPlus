package org.polyfrost.polyplus.client.utils

import org.polyfrost.polyplus.client.gui.pausescreen.PauseStage
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.StartupOverlay

object AnimationActivity {
    private const val SCREEN_HOLD_NANOS = 1_000_000_000L
    private const val OWN_PACKAGE = "org.polyfrost.polyplus."

    private var holdUntil = 0L
    private var lastScreen: Any? = null

    @Volatile
    var running = false
        private set

    @JvmStatic
    fun hold(seconds: Double) {
        extend(System.nanoTime(), (seconds * 1_000_000_000.0).toLong())
    }

    @JvmStatic
    fun update(): Boolean {
        val now = System.nanoTime()
        val screen = ClientPlatform.currentScreen()
        if (screen !== lastScreen) {
            if (ours(screen) || ours(lastScreen)) extend(now, SCREEN_HOLD_NANOS)
            lastScreen = screen
        }
        val depart = LobbyScene.departProgress
        running = now - holdUntil < 0L ||
            StartupOverlay.active ||
            LobbyScene.introPlaying() ||
            (depart > 0f && depart < 1f) ||
            PauseStage.animating()
        return running
    }

    private fun extend(now: Long, nanos: Long) {
        val until = now + nanos
        if (until - holdUntil > 0L) holdUntil = until
    }

    private fun ours(screen: Any?): Boolean = screen != null && screen.javaClass.name.startsWith(OWN_PACKAGE)
}
