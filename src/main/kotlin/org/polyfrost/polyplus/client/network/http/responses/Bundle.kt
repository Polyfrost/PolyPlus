package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BundleInfo(
    val id: Int,
    val name: String = "Bundle",
    val description: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("asset_id") val assetId: Int? = null,
    @SerialName("base_price") val basePrice: Float? = null,
    @SerialName("discount_rate") val discountRate: Int? = null,
    @SerialName("stripe_price_id") val stripePriceId: String? = null,
) {
    val purchasable: Boolean get() = !stripePriceId.isNullOrBlank()

    val free: Boolean get() = (finalPrice ?: 0f) <= 0f

    val discounted: Boolean get() = (discountRate ?: 0) > 0 && !free

    val finalPrice: Float?
        get() = basePrice?.let { base ->
            val rate = (discountRate ?: 0).coerceIn(0, 100)
            base * (1f - rate / 100f)
        }
}

@Serializable
data class Pagination(
    val count: Long = 0,
    val page: Long = 1,
    @SerialName("total_items") val totalItems: Long = 0,
    @SerialName("total_pages") val totalPages: Long = 0,
)

@Serializable
data class BundleSearchResponse(
    val bundles: List<BundleInfo> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
data class BundleViewResponse(
    val bundle: BundleInfo,
    val cosmetics: List<Int> = emptyList(),
    val emotes: List<Int> = emptyList(),
)
