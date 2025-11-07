package org.polyfrost.polyplus.network.plus.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmeticList(
    @SerialName("cosmetics") val cosmetics: List<Cosmetic>
)

@Serializable
data class Cosmetic(
    @SerialName("type") val type: String,
    @SerialName("id") val id: Int,
    @SerialName("url") val url: String,
    @SerialName("hash") val hash: String,
)