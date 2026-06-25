package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmeticVariant(
    val id: Int,
    val name: String = "Variant",
    /**
     * "slim"/"wide" when the client must pick a model to match the player's
     * skin; null otherwise.
     */
    val model: String? = null,
    @SerialName("allowed_slots") val allowedSlots: List<BodySlot> = emptyList(),
    val url: String? = null,
    val hash: String,
)

@Serializable
data class CosmeticGroupResponse(
    val id: Int,
    val type: CosmeticType,
    val name: String = "Cosmetic",
    @SerialName("allowed_slots") val allowedSlots: List<BodySlot> = emptyList(),
    val variants: List<CosmeticVariant> = emptyList(),
) {
    fun flatten(): List<CosmeticDefinition> =
        variants.map { variant ->
            CosmeticDefinition(
                id = variant.id,
                type = type,
                name = variant.name,
                url = variant.url,
                hash = variant.hash,
                allowedSlots = variant.allowedSlots.ifEmpty { allowedSlots },
                groupId = id,
                groupName = name,
                variantName = variant.name,
                model = variant.model,
            )
        }
}
