package org.polyfrost.polyplus.client.gui.wardrobe

import org.polyfrost.polyplus.client.cosmetics.BillingService
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.PlayerSkinInfo
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

internal data class CartEntry(
    val key: String,
    val name: String,
    val description: String?,
    val basePrice: Float?,
    val finalPrice: Float?,
    val discountRate: Int?,
    val storeProductId: String?,
    val coverAssetId: Int? = null,
) {
    val free: Boolean get() = (finalPrice ?: 0f) <= 0f

    val discounted: Boolean get() = (discountRate ?: 0) > 0 && !free
}

internal fun CosmeticStoreInfo.toCartEntry(variant: CosmeticVariantUi, storeProductId: String?): CartEntry =
    CartEntry(
        cosmeticCartKey(variant.id),
        cartName(variant),
        description,
        basePrice,
        finalPrice,
        discountRate,
        storeProductId,
        coverAssetId,
    )

internal fun cosmeticCartKey(variantId: Int): String = "cosmetic-$variantId"

internal fun CosmeticStoreInfo.cartName(variant: CosmeticVariantUi): String =
    if (uiVariants().size > 1 && !variant.name.equals(name, ignoreCase = true)) "$name - ${variant.name}" else name

internal data class CosmeticVariantUi(val id: Int, val name: String)

internal fun CosmeticStoreInfo.uiVariants(): List<CosmeticVariantUi> {
    val byLabel = LinkedHashMap<String, CosmeticVariantUi>()
    for (variant in variantList.sortedBy { it.variantOrder }) {
        val label = variant.variantName ?: name
        byLabel.getOrPut(label) { CosmeticVariantUi(variant.id, label) }
    }
    return byLabel.values.toList()
}

internal fun CosmeticStoreInfo.fullyOwned(ownedIds: Set<Int>): Boolean =
    uiVariants().all { it.id in ownedIds }

internal fun selectedStoreVariant(
    info: CosmeticStoreInfo,
    picks: Map<Int, Int>,
    ownedIds: Set<Int>,
): CosmeticVariantUi {
    val variants = info.uiVariants()
    val picked = picks[info.id]
    return variants.firstOrNull { it.id == picked }
        ?: variants.firstOrNull { it.id !in ownedIds }
        ?: variants.first()
}

internal fun checkoutFailureMessage(error: Throwable): String =
    if (error is BillingService.AlreadyOwnedException) {
        error.message ?: "You already own this item."
    } else {
        "Checkout failed: ${error.message}"
    }

internal data class CosmeticUiItem(
    val groupId: Int,
    val type: CosmeticType,
    val name: String,
    val collection: String,
    val owned: Boolean,
    val equipped: Boolean,
    val variants: List<CosmeticVariantUi>,
    val equippedVariantId: Int?,
    val equippedCosmeticId: Int? = equippedVariantId,
) {
    val id: Int get() = groupId
    val hasVariants: Boolean get() = variants.size > 1
}

internal const val MOJANG_CAPE_COLLECTION = "Minecraft cape"

internal fun selectedVariantId(item: CosmeticUiItem, picks: Map<Int, Int>): Int? =
    picks[item.groupId]?.takeIf { id -> item.variants.any { it.id == id } } ?: item.equippedVariantId ?: item.variants.firstOrNull()?.id

internal enum class WardrobeSection(val label: String, val icon: String) {
    Featured("Featured", "star-filled"),
    Outfits("Outfits", "profiles"),
    Skins("Skins", "paintbrush"),
    Emotes("Emotes", "lightning-01"),
    Cosmetics("Cosmetics", "assets/polyplus/ico/stars.svg"),
}

internal enum class WardrobeSort(val label: String) {
    Popular("Popular"),
    Newest("Newest"),
    PriceLow("Lowest price"),
    PriceHigh("Highest price"),
    Name("Name"),
}

internal sealed interface WardrobeEntry {
    val key: String
    val fullSpan: Boolean get() = false

    data class Divider(val label: String, val anchor: Boolean) : WardrobeEntry {
        override val key: String get() = "divider-$label"
        override val fullSpan: Boolean get() = true
    }

    data class Group(val id: String, val title: String, val note: String, val forceToggle: Boolean = false) : WardrobeEntry {
        override val key: String get() = "group-$id"
        override val fullSpan: Boolean get() = true
    }

    data class Note(val id: String, val text: String) : WardrobeEntry {
        override val key: String get() = "note-$id"
        override val fullSpan: Boolean get() = true
    }

    data class Banner(val id: String, val text: String, val action: String) : WardrobeEntry {
        override val key: String get() = "banner-$id"
        override val fullSpan: Boolean get() = true
    }

    data class Owned(val item: CosmeticUiItem) : WardrobeEntry {
        override val key: String get() = "owned-${item.type.serializedName}-${item.groupId}"
    }

    data class Shop(val info: CosmeticStoreInfo, val group: String) : WardrobeEntry {
        override val key: String get() = "shop-$group-${info.id}"
    }

    data class Mojang(val cape: MojangCape) : WardrobeEntry {
        override val key: String get() = "mojang-${cape.id}"
    }

    data class Bundle(val bundle: BundleInfo) : WardrobeEntry {
        override val key: String get() = "bundle-${bundle.id}"
    }

    data class LibraryHeader(val section: WardrobeSection, val note: String, val action: String? = null) : WardrobeEntry {
        override val key: String get() = "library-${section.name}"
        override val fullSpan: Boolean get() = true
    }

    data class Outfit(val outfit: OutfitInfo, val active: Boolean) : WardrobeEntry {
        override val key: String get() = "outfit-${outfit.id}"
    }

    data class Skin(val skin: PlayerSkinInfo, val worn: Boolean) : WardrobeEntry {
        override val key: String get() = "skin-${skin.id}"
    }
}

internal sealed interface WardrobeSelection {
    data class Owned(val groupId: Int, val type: CosmeticType) : WardrobeSelection
    data class Shop(val infoId: Int) : WardrobeSelection
    data class Mojang(val capeId: String) : WardrobeSelection
    data class Bundle(val bundleId: Int) : WardrobeSelection
    data class Outfit(val outfitId: Int) : WardrobeSelection
    data class Skin(val skinId: Int) : WardrobeSelection
}

internal fun List<OutfitInfo>.favoritesFirst(): List<OutfitInfo> = sortedByDescending { it.favorite }

internal fun List<PlayerSkinInfo>.favoriteSkinsFirst(): List<PlayerSkinInfo> = sortedByDescending { it.favorite }

internal val WARDROBE_COSMETIC_TYPES: List<CosmeticType> =
    CosmeticType.entries.filter { it != CosmeticType.Unknown && it != CosmeticType.Emote }

internal fun CosmeticStoreInfo.anyOwned(ownedIds: Set<Int>): Boolean = variantList.any { it.id in ownedIds }

internal fun List<CosmeticStoreInfo>.sortedFor(sort: WardrobeSort): List<CosmeticStoreInfo> = when (sort) {
    WardrobeSort.Popular -> this
    WardrobeSort.Newest -> sortedByDescending { it.createdAt }
    WardrobeSort.PriceLow -> sortedBy { it.finalPrice ?: 0f }
    WardrobeSort.PriceHigh -> sortedByDescending { it.finalPrice ?: 0f }
    WardrobeSort.Name -> sortedBy { it.name.lowercase() }
}

internal val BACK_VIEW_TYPES = setOf(CosmeticType.Cape, CosmeticType.Wings, CosmeticType.Backpack)
