package org.polyfrost.polyplus.client.gui.pausescreen

enum class PauseScreenStyle { STEP_BACK_FLAT, STEP_BACK, COMPACT, VANILLA }

enum class FocusPauseStyle(val style: PauseScreenStyle?) {
    SAME(null),
    STEP_BACK_FLAT(PauseScreenStyle.STEP_BACK_FLAT),
    STEP_BACK(PauseScreenStyle.STEP_BACK),
    COMPACT(PauseScreenStyle.COMPACT),
    VANILLA(PauseScreenStyle.VANILLA),
}
