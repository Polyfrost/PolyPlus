package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.player.AbstractClientPlayer
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess

object EmoteApi {
    fun stop(player: AbstractClientPlayer) {
        (player as PlayerEmotesAccess).`polyplus$emoteController`().stop()
    }

    fun playOwnedEmote(player: AbstractClientPlayer, emoteId: Int): Boolean {
        if (emoteId !in CosmeticCatalog.ownedEmoteIds()) {
            return false
        }
        return CosmeticService.playEmote(emoteId).isSuccess
    }
}
