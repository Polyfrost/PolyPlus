@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.lobby

import androidx.compose.ui.graphics.luminance
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.party.PartyScene
import java.time.LocalTime

enum class LobbyTime(private val label: String) {
    CAPTURED("As captured"),
    THEME("Match theme"),
    REAL("Real time"),
    DAY("Day"),
    SUNSET("Sunset"),
    NIGHT("Night");

    override fun toString(): String = label

    companion object {
        private const val DAY_TICKS = 24000L
        private const val DAY_CLOCK = 6000L
        private const val SUNSET_CLOCK = 12500L
        private const val NIGHT_CLOCK = 18000L
        private const val DAY_SECONDS = 86400L
        private const val DAWN_SECOND = 21600L
        private const val REAL_CHECK_NANOS = 60_000_000_000L
        private const val LUMINANCE_MIDPOINT = 0.5f

        private var realClock = DAY_CLOCK
        private var realCheckedNanos: Long? = null

        fun clock(captured: Long): Long = Math.floorMod(
            PartyScene.followedClock() ?: when (PolyPlusMainMenuConfig.lobbyTime) {
                CAPTURED -> captured
                THEME -> if (lightTheme()) DAY_CLOCK else NIGHT_CLOCK
                REAL -> realClock()
                DAY -> DAY_CLOCK
                SUNSET -> SUNSET_CLOCK
                NIGHT -> NIGHT_CLOCK
            },
            DAY_TICKS,
        )

        private fun lightTheme(): Boolean {
            val theme = OneConfigTheme.active ?: return false
            return theme.pageBackground.luminance() > LUMINANCE_MIDPOINT && theme.textColor.luminance() < LUMINANCE_MIDPOINT
        }

        private fun realClock(): Long {
            val now = System.nanoTime()
            val checked = realCheckedNanos
            if (checked == null || now - checked >= REAL_CHECK_NANOS) {
                realCheckedNanos = now
                realClock = (LocalTime.now().toSecondOfDay() - DAWN_SECOND) * DAY_TICKS / DAY_SECONDS
            }
            return realClock
        }
    }
}
