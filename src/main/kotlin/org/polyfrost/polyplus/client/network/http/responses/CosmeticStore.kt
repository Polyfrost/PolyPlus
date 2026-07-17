package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmeticTags(
    val colors: List<String> = emptyList(),
    val custom: List<String> = emptyList(),
    val category: List<String> = emptyList(),
) {
    val all: List<String> get() = custom + colors
}

@Serializable
data class StoreVariant(
    val id: Int,
    @SerialName("variant_name") val variantName: String? = null,
    @SerialName("model_variant") val modelVariant: String? = null,
    @SerialName("variant_order") val variantOrder: Int = 0,
    @SerialName("asset_id") val assetId: Int? = null,
    @SerialName("cover_asset_id") val coverAssetId: Int? = null,
)

@Serializable
data class CosmeticStoreInfo(
    val id: Int,
    val name: String = "Cosmetic",
    val description: String? = null,
    val collection: Int? = null,
    val type: CosmeticType = CosmeticType.Unknown,
    @SerialName("base_price") val basePrice: Float? = null,
    @SerialName("discount_rate") val discountRate: Int? = null,
    @SerialName("asset_id") val assetId: Int? = null,
    @SerialName("cover_asset_id") val coverAssetId: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
    val tags: CosmeticTags = CosmeticTags(),
    val variants: List<StoreVariant>? = null,
) {
    val variantList: List<StoreVariant>
        get() = variants?.takeIf { it.isNotEmpty() } ?: listOf(StoreVariant(id, name))

    val discounted: Boolean get() = (discountRate ?: 0) > 0

    val finalPrice: Float?
        get() = basePrice?.let { base ->
            val rate = (discountRate ?: 0).coerceIn(0, 100)
            base * (1f - rate / 100f)
        }
}

@Serializable
data class CosmeticSearchResponse(
    val results: List<CosmeticStoreInfo> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
data class CosmeticStoreView(
    @SerialName("stripe_price_id") val stripePriceId: String? = null,
    val id: Int,
    val name: String = "Cosmetic",
    val description: String? = null,
    val collection: Int? = null,
    val type: CosmeticType = CosmeticType.Unknown,
    @SerialName("base_price") val basePrice: Float? = null,
    @SerialName("discount_rate") val discountRate: Int? = null,
    @SerialName("asset_id") val assetId: Int? = null,
    @SerialName("cover_asset_id") val coverAssetId: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
    val tags: CosmeticTags = CosmeticTags(),
    val variants: List<StoreVariant>? = null,
) {
    val purchasable: Boolean get() = !stripePriceId.isNullOrBlank()

    val variantList: List<StoreVariant>
        get() = variants?.takeIf { it.isNotEmpty() } ?: listOf(StoreVariant(id, name))
}
