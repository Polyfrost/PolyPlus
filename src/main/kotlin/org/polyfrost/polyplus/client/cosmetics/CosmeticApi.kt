package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.player.AbstractClientPlayer
import org.polyfrost.polyplus.client.cosmetics.access.PlayerCosmeticsAccess
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

object CosmeticApi {
    fun equippedSlot(player: AbstractClientPlayer, slot: BodySlot): CosmeticEquipment.EquippedEntry? =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().get(slot)

    fun equipLocal(player: AbstractClientPlayer, cosmetic: AttachedCosmetic): Boolean =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().equip(cosmetic)

    fun unequipSlot(player: AbstractClientPlayer, slot: BodySlot): Boolean =
        (player as PlayerCosmeticsAccess).`polyplus$cosmeticEquipment`().unequip(slot)
}
