package org.polyfrost.polyplus.client

enum class MainMenuFpsLimitMode(private val label: String) {
    VANILLA("Vanilla (60 FPS limit)"),
    SMART("Smart (monitor refresh rate + 60)"),
    CUSTOM("Custom (15-260 value)");

    override fun toString(): String = label
}
