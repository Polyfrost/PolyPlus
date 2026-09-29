package org.polyfrost.polyplus.client

enum class CosmeticVisibility(private val label: String) {
    EVERYONE("Everyone"),

    FRIENDS("Friends Only"),

    SELF("Self"),

    NOBODY("No One");

    override fun toString(): String = label
}
