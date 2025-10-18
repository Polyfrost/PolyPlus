package org.polyfrost.polyplus.network.plus.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerCosmetics(
    @SerialName("active") val active: ActiveCosmetics,
    @SerialName("cosmetics") val owned: List<Cosmetic>,
)

@Serializable
data class PutCosmetics(
    @SerialName("active") val active: ActiveCosmetics,
)

@Serializable
data class ActiveCosmetics(
    @SerialName("cape") val cape: String?,
)