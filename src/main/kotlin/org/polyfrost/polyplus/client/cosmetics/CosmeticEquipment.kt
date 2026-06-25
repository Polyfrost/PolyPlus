//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.resources.Identifier
//? if >= 1.21.11 {
import net.minecraft.util.Util
//?} else {
/*import net.minecraft.Util
*///?}
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.EnumMap

class CosmeticEquipment {
    private val equipped = EnumMap<BodySlot, EquippedEntry>(BodySlot::class.java)

    fun equipped(): Collection<EquippedEntry> = equipped.values

    fun get(slot: BodySlot): EquippedEntry? = equipped[slot]

    fun findById(id: Identifier): EquippedEntry? =
        equipped.values.firstOrNull { it.cosmetic.id == id }

    fun equip(cosmetic: AttachedCosmetic): CosmeticEquipResult {
        val existing = equipped[cosmetic.slot]
        if (existing != null) {
            return CosmeticEquipResult.SlotOccupied(cosmetic.slot, existing.cosmetic.id)
        }

        equipped[cosmetic.slot] = EquippedEntry(cosmetic, Util.getMillis())
        return CosmeticEquipResult.Success
    }

    fun unequip(slot: BodySlot): Boolean =
        equipped.remove(slot) != null

    fun unequip(id: Identifier): Boolean {
        val slot = equipped.entries.firstOrNull { it.value.cosmetic.id == id }?.key ?: return false
        return equipped.remove(slot) != null
    }

    fun clear() {
        equipped.clear()
    }

    data class EquippedEntry(
        val cosmetic: AttachedCosmetic,
        val startTimeMs: Long,
    )
}
//?}
