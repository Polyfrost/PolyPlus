package org.polyfrost.polyplus.client.lobby

enum class LobbyRealm {
    OVERWORLD,
    NETHER,
    END;

    companion object {
        fun of(dimension: String): LobbyRealm = when (dimension.substringAfter(':').lowercase()) {
            "the_nether", "-1" -> NETHER
            "the_end", "1" -> END
            else -> OVERWORLD
        }
    }
}
