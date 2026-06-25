package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerCosmetics(
    @SerialName("cosmetics") val owned: List<CosmeticGroupResponse>,
    val emotes: List<EmoteDefinition> = emptyList(),
    val equipped: Map<BodySlot, Int> = emptyMap(),
)
