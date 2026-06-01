package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.Serializable

@Serializable
data class CosmeticDefinition(
    val id: Int,
    val type: CosmeticType,
    val name: String = "Cosmetic",
    val url: String? = null,
    val hash: String,
)

@Serializable
data class EmoteDefinition(
    val id: Int,
    val name: String = "Emote",
    val url: String? = null,
    val hash: String,
) {
    fun asCosmeticDefinition(): CosmeticDefinition =
        CosmeticDefinition(
            id = id,
            type = CosmeticType.Emote,
            name = name,
            url = url,
            hash = hash,
        )
}
