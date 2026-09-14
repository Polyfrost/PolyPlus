package org.polyfrost.polyplus.client.emotes.conditions

data class EmoteRules(
    val allowWalking: Boolean = false,
    val allowSprinting: Boolean = false,
    val allowCrouching: Boolean = false,
    val allowFalling: Boolean = true,
    val allowElytraFlying: Boolean = false,
    val allowSwimming: Boolean = false,
    val allowVanillaPose: Boolean = false,
) {
    companion object {
        val DEFAULT = EmoteRules()
    }
}
