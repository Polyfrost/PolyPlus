package org.polyfrost.polyplus.client.gui.marketplace

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.BillingService
import org.polyfrost.polyplus.client.cosmetics.BundleCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticStore
import org.polyfrost.polyplus.client.gui.wardrobe.CartEntry
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticVariantUi
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeEntry
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSelection
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeSort
import org.polyfrost.polyplus.client.gui.wardrobe.cartName
import org.polyfrost.polyplus.client.gui.wardrobe.checkoutFailureMessage
import org.polyfrost.polyplus.client.gui.wardrobe.cosmeticCartKey
import org.polyfrost.polyplus.client.gui.wardrobe.fullyOwned
import org.polyfrost.polyplus.client.gui.wardrobe.selectedStoreVariant
import org.polyfrost.polyplus.client.gui.wardrobe.sortedFor
import org.polyfrost.polyplus.client.gui.wardrobe.toCartEntry
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal enum class MarketPhase { Loading, Ready, Failed }

internal enum class OwnershipFilter(val label: String) {
    All("All cosmetics"),
    Available("Not owned"),
    Owned("Owned"),
}

internal class MarketPiece(val info: CosmeticStoreInfo, val variantId: Int)

internal class MarketBundle(val info: BundleInfo, val label: String, val pieces: List<MarketPiece>)

internal val CosmeticType.pluralName: String
    get() = when (this) {
        CosmeticType.Backpack -> "Back items"
        CosmeticType.Glasses, CosmeticType.Wings, CosmeticType.Boots -> displayName
        else -> "${displayName}s"
    }

internal fun String.titled(): String = replaceFirstChar { it.uppercase() }

private const val MAX_PAGES = 10
private const val NEW_ROW = 4
private const val NEW_LABEL = "New"

internal object MarketplaceCart {
    val entries = mutableStateListOf<CartEntry>()
}

private object MarketplaceCatalog {
    var items: List<CosmeticStoreInfo>? = null
    var bundle: MarketBundle? = null
}

internal class MarketplaceState {
    var phase by mutableStateOf(if (MarketplaceCatalog.items != null) MarketPhase.Ready else MarketPhase.Loading)
        private set
    var items by mutableStateOf(MarketplaceCatalog.items.orEmpty())
        private set
    var ownedIds by mutableStateOf(CosmeticCatalog.ownedIds())
        private set
    var look by mutableStateOf(CosmeticCatalog.localEquipped().equipped)
        private set
    var bundle by mutableStateOf(MarketplaceCatalog.bundle)
        private set
    var facing by mutableStateOf<CosmeticType?>(null)
        private set
    var selection by mutableStateOf<WardrobeSelection?>(null)
        private set

    var query by mutableStateOf("")
    var theme by mutableStateOf<String?>(null)
    var ownership by mutableStateOf(OwnershipFilter.All)
    var sort by mutableStateOf(WardrobeSort.Popular)
    var notice by mutableStateOf<String?>(null)
    var showCart by mutableStateOf(false)
    private var checkoutPending = false

    init {
        pruneOwned()
    }

    val variantPicks = mutableStateMapOf<Int, Int>()
    val fitting = mutableStateMapOf<BodySlot, MarketPiece>()
    val cart get() = MarketplaceCart.entries

    val activeFilters: Int
        get() = listOf(theme != null, ownership != OwnershipFilter.All, sort != WardrobeSort.Popular).count { it }

    val selectedItem: CosmeticStoreInfo?
        get() = (selection as? WardrobeSelection.Shop)?.let { picked -> items.firstOrNull { it.id == picked.infoId } }

    val selectedBundle: MarketBundle?
        get() = bundle?.takeIf { selection == WardrobeSelection.Bundle(it.info.id) }

    val narrowed: Boolean
        get() = theme != null || ownership != OwnershipFilter.All || query.isNotBlank()

    val entries: List<WardrobeEntry> by derivedStateOf {
        val shown = visible()
        buildList {
            if (!narrowed && items.isNotEmpty()) {
                addFeaturedRow()
            }
            CosmeticType.entries.forEach { type -> addSection(type.pluralName, shown.filter { it.type == type }, type.serializedName) }
        }
    }

    private fun MutableList<WardrobeEntry>.addFeaturedRow() {
        val current = bundle
        add(WardrobeEntry.Divider(NEW_LABEL, anchor = true))
        if (current != null) add(WardrobeEntry.Bundle(current.info))
        addAll(shopEntries(items.sortedFor(WardrobeSort.Newest).take(if (current != null) NEW_ROW - 1 else NEW_ROW), "new"))
    }

    private fun MutableList<WardrobeEntry>.addSection(label: String, shown: List<CosmeticStoreInfo>, group: String) {
        if (shown.isEmpty()) return
        add(WardrobeEntry.Divider(label, anchor = true))
        addAll(shopEntries(shown, group))
    }

    private fun shopEntries(shown: List<CosmeticStoreInfo>, group: String): List<WardrobeEntry> = shown.map { WardrobeEntry.Shop(it, group) }

    fun themes(): List<String> =
        items.flatMap { it.tags.custom }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    suspend fun load() {
        val cached = MarketplaceCatalog.items != null
        if (!cached) phase = MarketPhase.Loading
        val fetched = fetchItems()
        if (fetched == null) {
            if (!cached) phase = MarketPhase.Failed
            return
        }
        val live = liveBundle(fetched)
        MarketplaceCatalog.items = fetched
        MarketplaceCatalog.bundle = live
        items = fetched
        bundle = live
        syncPlayer()
        phase = MarketPhase.Ready
    }

    private suspend fun fetchItems(): List<CosmeticStoreInfo>? {
        val results = LinkedHashMap<Int, CosmeticStoreInfo>()
        var page = 1
        do {
            val response = CosmeticStore.search(page = page, perPage = CosmeticStore.MAX_PAGE_SIZE, sort = CosmeticStore.Sort.Popularity).getOrNull()
                ?: return null
            response.results.filter { it.type != CosmeticType.Unknown }.forEach { results.putIfAbsent(it.id, it) }
            page++
        } while (page <= response.pagination.totalPages && page <= MAX_PAGES)
        return results.values.toList()
    }

    private suspend fun liveBundle(items: List<CosmeticStoreInfo>): MarketBundle? {
        val live = BundleCatalog.featured() ?: return null
        val pieces = (live.cosmetics + live.emotes).mapNotNull { id ->
            items.firstOrNull { info -> info.variantList.any { it.id == id } }?.let { MarketPiece(it, id) }
        }
        return MarketBundle(live.info, "Bundle", pieces)
    }

    fun syncPlayer() {
        val owned = CosmeticCatalog.ownedIds()
        if (owned != ownedIds) ownedIds = owned
        pruneOwned()
        val worn = CosmeticCatalog.localEquipped().equipped
        if (worn != look) look = worn
    }

    private fun pruneOwned() {
        val bought = ownedIds.mapTo(HashSet(), ::cosmeticCartKey)
        if (cart.any { it.key in bought }) cart.removeAll { it.key in bought }
    }

    private fun visible(): List<CosmeticStoreInfo> {
        val needle = query.trim()
        return items.filter { info ->
            val owned = info.fullyOwned(ownedIds)
            (theme == null || theme in info.tags.custom) &&
                (needle.isEmpty() || matches(info, needle)) &&
                when (ownership) {
                    OwnershipFilter.All -> true
                    OwnershipFilter.Available -> !owned
                    OwnershipFilter.Owned -> owned
                }
        }.sortedFor(sort)
    }

    private fun matches(info: CosmeticStoreInfo, needle: String): Boolean =
        info.name.contains(needle, ignoreCase = true) ||
            info.type.displayName.contains(needle, ignoreCase = true) ||
            info.tags.all.any { it.contains(needle, ignoreCase = true) }

    fun clearFilters() {
        theme = null
        ownership = OwnershipFilter.All
        sort = WardrobeSort.Popular
    }

    fun clearSearch() {
        query = ""
        clearFilters()
    }

    fun variant(info: CosmeticStoreInfo): CosmeticVariantUi = selectedStoreVariant(info, variantPicks, ownedIds)

    fun select(info: CosmeticStoreInfo) {
        notice = null
        showCart = false
        selection = WardrobeSelection.Shop(info.id)
        fitting.clear()
        fit(info, variant(info).id)
    }

    fun selectBundle(target: BundleInfo) {
        val current = bundle?.takeIf { it.info.id == target.id } ?: return
        notice = null
        showCart = false
        selection = WardrobeSelection.Bundle(target.id)
        fitting.clear()
        current.pieces.filter { it.info.type != CosmeticType.Emote }.forEach { fit(it.info, it.variantId) }
    }

    fun pickVariant(infoId: Int, variantId: Int) {
        variantPicks[infoId] = variantId
        selectedItem?.takeIf { it.id == infoId }?.let { fit(it, variantId) }
    }

    private fun fit(info: CosmeticStoreInfo, variantId: Int) {
        facing = info.type
        info.type.defaultSlot()?.let { fitting[it] = MarketPiece(info, variantId) }
    }

    fun inCart(variantId: Int): Boolean = cart.any { it.key == cosmeticCartKey(variantId) }

    fun addToCart(info: CosmeticStoreInfo, variant: CosmeticVariantUi) {
        if (inCart(variant.id)) return
        val label = info.cartName(variant)
        PolyPlusClient.SCOPE.launch {
            val productId = CosmeticStore.view(variant.id).getOrNull()?.storeProductId
            ClientPlatform.runOnMain {
                if (productId.isNullOrBlank()) {
                    notice = "$label isn't purchasable yet."
                } else if (!inCart(variant.id)) {
                    cart += info.toCartEntry(variant, productId)
                }
            }
        }
    }

    fun buy(info: CosmeticStoreInfo, variant: CosmeticVariantUi) {
        if (checkoutPending) return
        val label = info.cartName(variant)
        checkoutPending = true
        notice = "Opening checkout..."
        PolyPlusClient.SCOPE.launch {
            val productId = CosmeticStore.view(variant.id).getOrNull()?.storeProductId
            ClientPlatform.runOnMain {
                if (productId.isNullOrBlank()) {
                    checkoutPending = false
                    notice = "$label isn't purchasable yet."
                } else {
                    openCheckout(listOf(productId))
                }
            }
        }
    }

    fun buyBundle(target: BundleInfo) {
        val productId = target.storeProductId
        if (productId.isNullOrBlank()) notice = "${target.name} isn't purchasable yet." else if (!checkoutPending) openCheckout(listOf(productId))
    }

    fun checkout() {
        syncPlayer()
        val productIds = cart.mapNotNull { it.storeProductId }
        if (productIds.isEmpty()) notice = "Nothing purchasable in your cart yet." else if (!checkoutPending) openCheckout(productIds)
    }

    private fun openCheckout(productIds: List<String>) {
        checkoutPending = true
        notice = "Opening checkout..."
        PolyPlusClient.SCOPE.launch {
            val result = BillingService.checkoutAndOpen(productIds)
            ClientPlatform.runOnMain {
                checkoutPending = false
                notice = result.fold(onSuccess = { "Checkout opened in your browser." }, onFailure = ::checkoutFailureMessage)
            }
        }
    }
}
