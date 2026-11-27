package org.polyfrost.polyplus.client.nameplate

enum class NameplateEffects(private val label: String) {
    GPU("GPU"),

    CPU("CPU"),

    OFF("Off");

    override fun toString(): String = label
}
