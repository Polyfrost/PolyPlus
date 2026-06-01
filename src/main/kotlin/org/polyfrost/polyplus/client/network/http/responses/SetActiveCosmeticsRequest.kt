package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class SetEquippedCosmeticsRequest(
    val equipped: Map<CosmeticType, Int?>,
)
