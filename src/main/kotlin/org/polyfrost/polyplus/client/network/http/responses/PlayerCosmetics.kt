package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerCosmetics(
    @SerialName("cosmetics") val owned: List<CosmeticDefinition>,
    val emotes: List<EmoteDefinition> = emptyList(),
    val equipped: Map<CosmeticType, Int> = emptyMap(),
)
