package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayerCosmetics(val active: HashMap<String, Int?>, @SerialName("cosmetics") val owned: List<Cosmetic>)
