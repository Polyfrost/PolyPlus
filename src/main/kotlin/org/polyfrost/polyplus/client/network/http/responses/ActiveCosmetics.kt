package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class EquippedCosmetics(
    val equipped: Map<BodySlot, Int> = emptyMap(),
) {
    val cape: Int?
        get() = equipped[BodySlot.Cape]

    fun ids(): List<Int> = equipped.values.toList()
}

@Serializable
data class PartialEquippedCosmetics(
    val equipped: Map<BodySlot, Int?>,
)
