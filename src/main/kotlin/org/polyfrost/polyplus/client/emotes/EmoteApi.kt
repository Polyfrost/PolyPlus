//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.emotes

import net.minecraft.client.player.AbstractClientPlayer
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.access.PlayerEmotesAccess
import org.polyfrost.polyplus.client.emotes.conditions.EmoteConditions
import org.polyfrost.polyplus.client.utils.ClientPlatform

object EmoteApi {
    fun findEmote(emoteId: Int): Emote? = CosmeticAssetCache.getEmote(emoteId)

    fun play(player: AbstractClientPlayer, emoteId: Int): Boolean {
        val emote = findEmote(emoteId) ?: return false
        return play(player, emote)
    }

    fun play(player: AbstractClientPlayer, emote: Emote): Boolean {
        if (!EmoteConditions.allows(player, emote.rules)) {
            return false
        }

        (player as PlayerEmotesAccess).`polyplus$emoteController`().play(emote)
        return true
    }

    fun playRemote(emoteId: Int) {
        PolyPlusClient.SCOPE.launch {
            if (!CosmeticAssetCache.ensureEmoteLoaded(emoteId)) return@launch
            val emote = findEmote(emoteId) ?: return@launch
            ClientPlatform.runOnMain {
                val player = net.minecraft.client.Minecraft.getInstance().player ?: return@runOnMain
                play(player, emote)
            }
        }
    }

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
//?}
