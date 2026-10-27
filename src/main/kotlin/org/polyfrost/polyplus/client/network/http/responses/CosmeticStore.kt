package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SaleInfo(
    val price: Float = 0f,
    val discount: Float = 0f,
    val percent: Int = 0,
    val name: String = "",
    @SerialName("ends_at") val endsAt: String? = null,
)

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
    val sale: SaleInfo? = null,
    @SerialName("asset_id") val assetId: Int? = null,
    @SerialName("cover_asset_id") val coverAssetId: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
    val tags: CosmeticTags = CosmeticTags(),
    val variants: List<StoreVariant>? = null,
) {
    val variantList: List<StoreVariant>
        get() = variants?.takeIf { it.isNotEmpty() } ?: listOf(StoreVariant(id, name))

    val free: Boolean get() = (finalPrice ?: 0f) <= 0f

    val discounted: Boolean get() = (discountRate ?: 0) > 0 && !free

    val discountRate: Int? get() = sale?.percent?.takeIf { it > 0 }

    val finalPrice: Float? get() = sale?.price ?: basePrice
}

@Serializable
data class CosmeticSearchResponse(
    val results: List<CosmeticStoreInfo> = emptyList(),
    val pagination: Pagination = Pagination(),
)

@Serializable
data class CosmeticStoreView(
    @SerialName("store_product_id") val storeProductId: String? = null,
    val id: Int,
    val name: String = "Cosmetic",
    val description: String? = null,
    val collection: Int? = null,
    val type: CosmeticType = CosmeticType.Unknown,
    @SerialName("base_price") val basePrice: Float? = null,
    val sale: SaleInfo? = null,
    @SerialName("asset_id") val assetId: Int? = null,
    @SerialName("cover_asset_id") val coverAssetId: Int? = null,
    @SerialName("created_at") val createdAt: String = "",
    val tags: CosmeticTags = CosmeticTags(),
    val variants: List<StoreVariant>? = null,
) {
    val purchasable: Boolean get() = !storeProductId.isNullOrBlank()

    val discountRate: Int? get() = sale?.percent?.takeIf { it > 0 }

    val variantList: List<StoreVariant>
        get() = variants?.takeIf { it.isNotEmpty() } ?: listOf(StoreVariant(id, name))
}
