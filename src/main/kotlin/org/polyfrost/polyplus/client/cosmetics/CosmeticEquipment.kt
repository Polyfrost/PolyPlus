package org.polyfrost.polyplus.client.cosmetics

import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import java.util.EnumMap

//? if >= 1.21.11 {
import net.minecraft.util.Util
//?}

//? if < 1.21.11 {
/*import net.minecraft.Util
*///?}

class CosmeticEquipment {
    private val equipped = EnumMap<BodySlot, EquippedEntry>(BodySlot::class.java)

    fun equipped(): Collection<EquippedEntry> = equipped.values

    fun get(slot: BodySlot): EquippedEntry? = equipped[slot]

    /** Equips into a free slot (returns false when the slot is already taken). */
    fun equip(cosmetic: AttachedCosmetic): Boolean {
        if (equipped.containsKey(cosmetic.slot)) {
            return false
        }

        equipped[cosmetic.slot] = EquippedEntry(cosmetic, Util.getMillis())
        return true
    }

    fun unequip(slot: BodySlot): Boolean =
        equipped.remove(slot) != null

    data class EquippedEntry(
        val cosmetic: AttachedCosmetic,
        val startTimeMs: Long,
    )
}
