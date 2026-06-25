package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class EquippedCosmetics(
    val equipped: Map<BodySlot, Int> = emptyMap(),
) {
    val cape: Int?
        get() = equipped[BodySlot.Cape]

    fun ids(): List<Int> = equipped.values.toList()

    fun with(slot: BodySlot, cosmeticId: Int?): EquippedCosmetics {
        val next = equipped.toMutableMap()
        if (cosmeticId == null) {
            next.remove(slot)
        } else {
            next[slot] = cosmeticId
        }
        return EquippedCosmetics(next)
    }
}

@Serializable
data class PartialEquippedCosmetics(
    val equipped: Map<BodySlot, Int?>,
)
