package org.polyfrost.polyplus.client.lobby

enum class LobbyMusic(private val label: String) {
    MATCH_SCENE("Match scene"),
    DAY("Day"),
    NIGHT("Night"),
    SUNSET("Sunset"),
    AURORA("Aurora"),
    HOME("Home"),
    CAVE("Cave"),
    NETHER("Nether"),
    THE_END("The End"),
    MINECRAFT("Minecraft music"),
    OFF("Off");

    override fun toString(): String = label
}
