package org.polyfrost.polyplus.client

enum class CosmeticPhysics(private val label: String) {
    ON("On"),

    OWN("Own Only"),

    OFF("Off");

    override fun toString(): String = label
}
