package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BonusRef(
    val id: Int,
    val url: String? = null,
    val hash: String = "",
)

@Serializable
data class BonusPreviewRequest(@SerialName("cosmetic_ids") val cosmeticIds: List<Int>)

@Serializable
data class BonusPreviewResponse(val bonuses: List<BonusRef> = emptyList())

@Serializable
data class DiscoveredBonus(
    @SerialName("bonus_id") val bonusId: Int,
    val name: String,
    @SerialName("cosmetic_ids") val cosmeticIds: List<Int> = emptyList(),
    @SerialName("discovered_at") val discoveredAt: String? = null,
)
