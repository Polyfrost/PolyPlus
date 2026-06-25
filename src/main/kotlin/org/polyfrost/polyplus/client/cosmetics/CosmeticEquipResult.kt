//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

sealed interface CosmeticEquipResult {
    data object Success : CosmeticEquipResult

    data class SlotOccupied(
        val slot: BodySlot,
        val occupiedBy: Identifier,
    ) : CosmeticEquipResult

    data class UnknownCosmetic(val id: Identifier) : CosmeticEquipResult
}
//?}
