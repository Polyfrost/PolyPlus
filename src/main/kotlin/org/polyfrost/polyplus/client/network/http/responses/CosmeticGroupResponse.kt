package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmeticVariant(
    val id: Int,
    val name: String = "Variant",
    // "slim"/"wide" when the client must match the player's skin model null otherwise
    val model: String? = null,
    @SerialName("allowed_slots") val allowedSlots: List<BodySlot> = emptyList(),
    val url: String? = null,
    val hash: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("variant_key") val variantKey: String? = null,
)

data class CosmeticGroupKey(val id: Int, val grouped: Boolean) {
    val tag: String get() = if (grouped) "g$id" else "c$id"
}

@Serializable
data class CosmeticGroupResponse(
    val id: Int,
    val grouped: Boolean = false,
    val type: CosmeticType,
    val name: String = "Cosmetic",
    @SerialName("allowed_slots") val allowedSlots: List<BodySlot> = emptyList(),
    val variants: List<CosmeticVariant> = emptyList(),
) {
    val key: CosmeticGroupKey get() = CosmeticGroupKey(id, grouped)

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
                grouped = grouped,
                groupName = name,
                variantName = variant.name,
                model = variant.model,
                coverUrl = variant.coverUrl,
                variantKey = variant.variantKey,
            )
        }
}
