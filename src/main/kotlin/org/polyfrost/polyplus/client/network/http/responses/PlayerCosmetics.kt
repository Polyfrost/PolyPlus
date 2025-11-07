package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerCosmetics(val active: ActiveCosmetics, @SerialName("cosmetics") val owned: List<Cosmetic>)
