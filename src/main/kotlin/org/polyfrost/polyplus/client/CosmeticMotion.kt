package org.polyfrost.polyplus.client

enum class CosmeticMotion(private val label: String) {
    FULL("Full"),

    REDUCED("Reduced"),

    OFF("Off");

    override fun toString(): String = label
}
