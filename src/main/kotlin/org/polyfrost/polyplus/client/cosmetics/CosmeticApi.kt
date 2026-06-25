//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.resources.Identifier
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

object CosmeticApi {
    fun equipped(player: AbstractClientPlayer): Collection<CosmeticEquipment.EquippedEntry> =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equipped()

    fun equippedSlot(player: AbstractClientPlayer, slot: BodySlot): CosmeticEquipment.EquippedEntry? =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().get(slot)

    fun equipLocal(player: AbstractClientPlayer, cosmetic: AttachedCosmetic): CosmeticEquipResult =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equip(cosmetic)

    fun unequip(player: AbstractClientPlayer, cosmeticId: Identifier): Boolean =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().unequip(cosmeticId)

    fun unequipSlot(player: AbstractClientPlayer, slot: BodySlot): Boolean =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().unequip(slot)

    fun clear(player: AbstractClientPlayer) {
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().clear()
    }

    fun equipApi(cosmeticId: Int, onComplete: (Result<Unit>) -> Unit = {}) {
        PolyPlusClient.SCOPE.launch {
            val result = CosmeticService.equip(cosmeticId)
            onComplete(result)
        }
    }
}
//?}
