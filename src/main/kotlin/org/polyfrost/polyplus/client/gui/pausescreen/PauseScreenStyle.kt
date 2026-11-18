package org.polyfrost.polyplus.client.gui.pausescreen

enum class PauseScreenStyle { STEP_BACK_FLAT, STEP_BACK, COMPACT, CLASSIC, VANILLA }

enum class PauseWidgetCorner { TOP_RIGHT, BOTTOM_LEFT }

enum class FocusPauseStyle(val style: PauseScreenStyle?) {
    SAME(null),
    STEP_BACK_FLAT(PauseScreenStyle.STEP_BACK_FLAT),
    STEP_BACK(PauseScreenStyle.STEP_BACK),
    COMPACT(PauseScreenStyle.COMPACT),
    CLASSIC(PauseScreenStyle.CLASSIC),
    VANILLA(PauseScreenStyle.VANILLA),
}
