package org.polyfrost.polyplus.client

enum class CosmeticSounds(private val label: String) {
    OFF("Off"),

    OWN("Own"),

    EVERYONE("Everyone");

    override fun toString(): String = label
}
