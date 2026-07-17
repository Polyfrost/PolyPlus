package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen
import org.polyfrost.oneconfig.internal.ui.navigation.NavigationGroup
import org.polyfrost.oneconfig.internal.ui.navigation.NavigationRoute
import org.polyfrost.oneconfig.internal.ui.navigation.graph.ModsGraph
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.BillingService
import org.polyfrost.polyplus.client.cosmetics.BundleCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticStore
import org.polyfrost.polyplus.client.gui.preview.LocalPlayerPreviewOpacity
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.BundleViewResponse
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.network.http.responses.TransactionInfo
import org.polyfrost.polyplus.client.network.http.responses.TransactionStatus
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Serializable
data object PolyPlusCosmeticsRoute

object PolyPlusOneConfigIntegration {
    private val cosmeticsRoute = NavigationRoute(
        id = "cosmetics",
        icon = "assets/polyplus/ico/stars.svg",
        route = PolyPlusCosmeticsRoute,
    )

    @JvmStatic
    fun navigationGroups(original: List<NavigationGroup>): List<NavigationGroup> {
        if (original.any { group -> group.routes.any { it.route == PolyPlusCosmeticsRoute } }) {
            return original
        }

        return original.map { group ->
            if (group.id == "Personalization") {
                val routes = group.routes.toMutableList()
                routes.add(minOf(1, routes.size), cosmeticsRoute)
                NavigationGroup(group.id, *routes.toTypedArray())
            } else {
                group
            }
        }
    }

    @JvmStatic
    fun addRoutes(builder: NavGraphBuilder) {
        builder.polyPlusCosmeticsGraph()
    }

    @Volatile
    private var pendingOpeningRoute: Any? = null

    @JvmStatic
    fun consumePendingOpeningRoute(): Any? {
        val route = pendingOpeningRoute
        pendingOpeningRoute = null
        return route
    }

    @JvmStatic
    fun openCosmetics() = openOneConfig(PolyPlusCosmeticsRoute)

    @JvmStatic
    fun openMods() = openOneConfig(ModsGraph)

    private fun openOneConfig(route: Any) {
        pendingOpeningRoute = route
        val mc = net.minecraft.client.Minecraft.getInstance()
        //? if >= 26.2 {
        /*mc.gui.setScreen(OneConfigUIScreen())
        *///?} else {
        mc.setScreen(OneConfigUIScreen())
        //?}
    }
}

fun NavGraphBuilder.polyPlusCosmeticsGraph() {
    composable<PolyPlusCosmeticsRoute> {
        val previewAlpha by transition.animateFloat(
            transitionSpec = { tween(durationMillis = 250) },
            label = "polyplus-preview-fade",
        ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
        CompositionLocalProvider(LocalPlayerPreviewOpacity provides previewAlpha) {
            PolyPlusCosmeticsScreen()
        }
    }
}

private enum class PolyPlusTab {
    Wardrobe,
    Store,
    Bundles,
    History,
}

private data class CartEntry(
    val key: String,
    val name: String,
    val description: String?,
    val basePrice: Float?,
    val finalPrice: Float?,
    val discountRate: Int?,
    val stripePriceId: String?,
    val coverAssetId: Int? = null,
) {
    val discounted: Boolean get() = (discountRate ?: 0) > 0
}

private fun BundleInfo.toCartEntry(): CartEntry =
    CartEntry("bundle-$id", name, description, basePrice, finalPrice, discountRate, stripePriceId)

private fun CosmeticStoreInfo.toCartEntry(variant: CosmeticVariantUi, stripePriceId: String?): CartEntry =
    CartEntry(
        cosmeticCartKey(variant.id),
        cartName(variant),
        description,
        basePrice,
        finalPrice,
        discountRate,
        stripePriceId,
        coverAssetId,
    )

private val BundleInfo.cartKey: String get() = "bundle-$id"
private fun cosmeticCartKey(variantId: Int): String = "cosmetic-$variantId"

private fun CosmeticStoreInfo.cartName(variant: CosmeticVariantUi): String =
    if (variantList.size > 1 && !variant.name.equals(name, ignoreCase = true)) "$name - ${variant.name}" else name

private data class CosmeticVariantUi(val id: Int, val name: String)

private fun CosmeticStoreInfo.uiVariants(): List<CosmeticVariantUi> {
    val byLabel = LinkedHashMap<String, CosmeticVariantUi>()
    for (variant in variantList.sortedBy { it.variantOrder }) {
        val label = variant.variantName ?: name
        byLabel.getOrPut(label) { CosmeticVariantUi(variant.id, label) }
    }
    return byLabel.values.toList()
}

private fun CosmeticStoreInfo.fullyOwned(ownedIds: Set<Int>): Boolean =
    uiVariants().all { it.id in ownedIds }

private fun selectedStoreVariant(
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

private data class CosmeticUiItem(
    val groupId: Int,
    val type: CosmeticType,
    val name: String,
    val collection: String,
    val owned: Boolean,
    val equipped: Boolean,
    /** User-facing variants (slim/wide model axis collapsed away). >= 1 entry. */
    val variants: List<CosmeticVariantUi>,
    /** Which variant id is currently equipped for this group, if any. */
    val equippedVariantId: Int?,
) {
    val id: Int get() = groupId
    val hasVariants: Boolean get() = variants.size > 1
}

@Composable
private fun PolyPlusCosmeticsScreen() {
    var tab by remember { mutableStateOf(PolyPlusTab.Wardrobe) }
    var showCart by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val cart = remember { mutableStateListOf<CartEntry>() }
    val allItems = rememberCosmeticItems(refreshKey)
    val ownedIds = remember(refreshKey) { CosmeticCatalog.ownedIds() }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    val selected = allItems.firstOrNull { it.id == selectedId } ?: allItems.firstOrNull()
    // User's chosen variant per group (groupId -> variantId). Falls back to the
    // equipped/first variant via selectedVariantId().
    val variantPicks = remember { mutableStateMapOf<Int, Int>() }
    var auraColor by remember { mutableStateOf(CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())) }
    LaunchedEffect(refreshKey) {
        auraColor = CosmeticCatalog.getParticleColor(ClientPlatform.localPlayerUuid())
    }

    LaunchedEffect(Unit) {
        PolyPlusClient.refreshCosmeticsIfNeeded()
        while (true) {
            delay(1_500L)
            refreshKey++
        }
    }

    fun checkout() {
        val priceIds = cart.mapNotNull { it.stripePriceId }
        if (priceIds.isEmpty()) {
            status = "Nothing purchasable in your cart yet."
            return
        }
        status = "Opening checkout..."
        PolyPlusClient.SCOPE.launch {
            val result = BillingService.checkoutAndOpen(priceIds)
            ClientPlatform.runOnMain {
                status = result.fold(
                    onSuccess = { "Checkout opened in your browser." },
                    onFailure = { "Checkout failed: ${it.message}" },
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Toolbar(
            activeTab = tab,
            cartSize = cart.size,
            onWardrobe = {
                tab = PolyPlusTab.Wardrobe
                status = null
            },
            onStore = {
                tab = PolyPlusTab.Store
                showCart = false
                status = null
            },
            onBundles = {
                tab = PolyPlusTab.Bundles
                showCart = false
                status = null
            },
            onHistory = {
                tab = PolyPlusTab.History
                status = null
            },
            onCart = {
                tab = PolyPlusTab.Store
                showCart = true
            },
            onRefresh = {
                PolyPlusClient.refreshCosmetics()
                refreshKey++
                status = "Refreshing cosmetic data..."
            },
        )

        when (tab) {
            PolyPlusTab.Wardrobe -> WardrobeScreen(
                items = allItems.filter { it.owned },
                selected = selected,
                status = status,
                variantPicks = variantPicks,
                auraColor = auraColor,
                onPreviewAuraColor = { color ->
                    auraColor = color
                    CosmeticCatalog.setParticleColor(ClientPlatform.localPlayerUuid(), color)
                },
                onSelectAuraColor = { color ->
                    auraColor = color
                    status = if (color != null) "Updating aura color..." else "Clearing aura color..."
                    PolyPlusClient.SCOPE.launch {
                        val result = CosmeticService.setParticleColor(color)
                        ClientPlatform.runOnMain {
                            status = result.fold(
                                onSuccess = { if (color != null) "Aura color updated." else "Aura color cleared." },
                                onFailure = { "Failed to set aura color: ${it.message}" },
                            )
                        }
                    }
                },
                onSelect = { selectedId = it.id },
                onSelectVariant = { groupId, variantId ->
                    variantPicks[groupId] = variantId
                    val item = allItems.firstOrNull { it.groupId == groupId }
                    if (item != null && item.equipped && item.equippedVariantId != variantId) {
                        status = "Equipping ${item.name}..."
                        equip(item, variantId) {
                            refreshKey++
                            status = it
                        }
                    }
                },
                onEquip = { item ->
                    val variantId = selectedVariantId(item, variantPicks)
                    if (item.equipped && variantId == item.equippedVariantId) {
                        status = "Unequipping ${item.name}..."
                        unequip(item) {
                            refreshKey++
                            status = it
                        }
                    } else if (variantId == null) {
                        status = "${item.name} has no variant to equip."
                    } else {
                        status = "Equipping ${item.name}..."
                        equip(item, variantId) {
                            refreshKey++
                            status = it
                        }
                    }
                },
            )

            PolyPlusTab.Store -> StoreScreen(
                cart = cart,
                showCart = showCart,
                status = status,
                ownedIds = ownedIds,
                onRemoveFromCart = { key -> cart.removeAll { it.key == key } },
                onBackToBrowse = { showCart = false },
                onCheckout = { checkout() },
                onAddToCart = { info, variant ->
                    val key = cosmeticCartKey(variant.id)
                    val label = info.cartName(variant)
                    if (variant.id in ownedIds) {
                        status = "You already own $label."
                    } else if (cart.any { it.key == key }) {
                        status = "$label is already in your cart."
                    } else {
                        status = "Adding $label to cart..."
                        PolyPlusClient.SCOPE.launch {
                            val view = CosmeticStore.view(variant.id).getOrNull()
                            ClientPlatform.runOnMain {
                                val priceId = view?.stripePriceId
                                if (priceId.isNullOrBlank()) {
                                    status = "$label isn't purchasable yet."
                                } else if (cart.none { it.key == key }) {
                                    cart += info.toCartEntry(variant, priceId)
                                    status = "$label added to cart."
                                }
                            }
                        }
                    }
                },
                onBuyNow = { info, variant ->
                    val label = info.cartName(variant)
                    if (variant.id in ownedIds) {
                        status = "You already own $label."
                        return@StoreScreen
                    }
                    status = "Opening checkout..."
                    PolyPlusClient.SCOPE.launch {
                        val view = CosmeticStore.view(variant.id).getOrNull()
                        val priceId = view?.stripePriceId
                        if (priceId.isNullOrBlank()) {
                            ClientPlatform.runOnMain { status = "$label isn't purchasable yet." }
                        } else {
                            val result = BillingService.checkoutAndOpen(listOf(priceId))
                            ClientPlatform.runOnMain {
                                status = result.fold(
                                    onSuccess = { "Checkout opened in your browser." },
                                    onFailure = { "Checkout failed: ${it.message}" },
                                )
                            }
                        }
                    }
                },
            )

            PolyPlusTab.Bundles -> BundlesScreen(
                cart = cart,
                showCart = showCart,
                status = status,
                onAddToCart = { bundle ->
                    if (cart.none { it.key == bundle.cartKey }) cart += bundle.toCartEntry()
                    status = "${bundle.name} added to cart."
                },
                onRemoveFromCart = { key -> cart.removeAll { it.key == key } },
                onBackToBrowse = { showCart = false },
                onCheckout = { checkout() },
                onStatus = { status = it },
            )

            PolyPlusTab.History -> HistoryScreen(refreshKey = refreshKey)
        }
    }
}

@Composable
private fun Toolbar(
    activeTab: PolyPlusTab,
    cartSize: Int,
    onWardrobe: () -> Unit,
    onStore: () -> Unit,
    onBundles: () -> Unit,
    onHistory: () -> Unit,
    onCart: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(33.dp), verticalAlignment = Alignment.CenterVertically) {
        TabButton("Wardrobe", activeTab == PolyPlusTab.Wardrobe, onWardrobe)
        Spacer(Modifier.width(8.dp))
        TabButton("Store", activeTab == PolyPlusTab.Store, onStore)
        Spacer(Modifier.width(8.dp))
        // TabButton("Bundles", activeTab == PolyPlusTab.Bundles, onBundles)
        // Spacer(Modifier.width(8.dp))
        TabButton("History", activeTab == PolyPlusTab.History, onHistory)
        Spacer(Modifier.weight(1f))
        SmallButton("Refresh", iconPath = "refresh", primary = false, onClick = onRefresh)
        Spacer(Modifier.width(8.dp))
        SmallButton("Cart ($cartSize)", iconPath = "assets/polyplus/ico/shopping-cart/0.svg", primary = true, onClick = onCart)
    }
}

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.height(33.dp)
            .clip(ppShape(7.dp))
            .background(if (selected) Accent else LocalTheme.current.chipBackground)
            .border(1.dp, LocalTheme.current.borderColor, ppShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        GuiText(
            label,
            color = if (selected) LocalTheme.current.accentTextColor else LocalTheme.current.textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SmallButton(
    label: String,
    iconPath: String,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.height(33.dp)
            .clip(ppShape(7.dp))
            .background(if (primary) Accent else LocalTheme.current.chipBackground)
            .border(1.dp, LocalTheme.current.borderColor, ppShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(iconPath, color = if (primary) LocalTheme.current.accentTextColor else LocalTheme.current.textColor, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        GuiText(label, color = if (primary) LocalTheme.current.accentTextColor else LocalTheme.current.textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WardrobeScreen(
    items: List<CosmeticUiItem>,
    selected: CosmeticUiItem?,
    status: String?,
    variantPicks: Map<Int, Int>,
    auraColor: Int?,
    onPreviewAuraColor: (Int) -> Unit,
    onSelectAuraColor: (Int?) -> Unit,
    onSelect: (CosmeticUiItem) -> Unit,
    onSelectVariant: (Int, Int) -> Unit,
    onEquip: (CosmeticUiItem) -> Unit,
) {
    var selectedType by remember { mutableStateOf(CosmeticType.Cape) }
    var stockedTypes by remember {
        mutableStateOf(CosmeticType.entries.filter { it != CosmeticType.Unknown })
    }
    LaunchedEffect(Unit) {
        stockedTypes = CosmeticStore.stockedTypes()
    }

    val railTypes = remember(stockedTypes, items) {
        val owned = items.mapTo(mutableSetOf()) { it.type }
        CosmeticType.entries.filter {
            it != CosmeticType.Unknown && (it in stockedTypes || it in owned)
        }
    }

    LaunchedEffect(railTypes) {
        if (railTypes.isNotEmpty() && selectedType !in railTypes) {
            selectedType = railTypes.first()
        }
    }

    val displayItems = remember(items, selectedType) {
        items.filter { it.type == selectedType }
    }

    LaunchedEffect(selectedType, displayItems.isEmpty()) {
        if (selected?.type != selectedType) {
            displayItems.firstOrNull()?.let(onSelect)
        }
    }

    val previewItem = selected?.takeIf { it.type == selectedType } ?: displayItems.firstOrNull()
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
        CategoryRail(
            selected = selectedType,
            onSelect = { selectedType = it },
            types = railTypes,
        )
        CosmeticGrid(
            items = displayItems,
            modifier = Modifier.width(596.dp).fillMaxHeight(),
            onSelect = onSelect,
            onEquip = onEquip,
        )
        PreviewPanel(
            selected = previewItem,
            status = status,
            selectedVariantId = previewItem?.let { selectedVariantId(it, variantPicks) },
            onSelectVariant = { variantId ->
                previewItem?.let { onSelectVariant(it.groupId, variantId) }
            },
            showAuraColor = selectedType == CosmeticType.Aura,
            auraColor = auraColor,
            onPreviewAuraColor = onPreviewAuraColor,
            onSelectAuraColor = onSelectAuraColor,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun BundlesScreen(
    cart: List<CartEntry>,
    showCart: Boolean,
    status: String?,
    onAddToCart: (BundleInfo) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onBackToBrowse: () -> Unit,
    onCheckout: () -> Unit,
    onStatus: (String?) -> Unit,
) {
    var page by remember { mutableIntStateOf(1) }
    var bundles by remember { mutableStateOf<List<BundleInfo>>(emptyList()) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedBundleId by remember { mutableStateOf<Int?>(null) }
    var bundleView by remember { mutableStateOf<BundleViewResponse?>(null) }

    LaunchedEffect(page) {
        loading = true
        loadError = null
        BundleCatalog.search(page = page)
            .onSuccess {
                bundles = it.bundles
                totalPages = it.pagination.totalPages.toInt().coerceAtLeast(1)
                if (selectedBundleId == null) selectedBundleId = it.bundles.firstOrNull()?.id
            }
            .onFailure { loadError = "Couldn't load bundles: ${it.message}" }
        loading = false
    }

    LaunchedEffect(selectedBundleId) {
        val id = selectedBundleId
        bundleView = null
        if (id != null) {
            BundleCatalog.view(id).onSuccess { bundleView = it }
        }
    }

    val selectedBundle = bundles.firstOrNull { it.id == selectedBundleId }
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(25.dp)) {
        Column(modifier = Modifier.width(596.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            when {
                loading && bundles.isEmpty() -> CenteredNote("Loading bundles...")
                loadError != null && bundles.isEmpty() -> CenteredNote(loadError!!)
                bundles.isEmpty() -> CenteredNote("No bundles available yet.")
                else -> BundleGrid(
                    bundles = bundles,
                    cartKeys = cart.map { it.key }.toSet(),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onSelect = { selectedBundleId = it.id },
                    onAddToCart = onAddToCart,
                )
            }
            if (totalPages > 1) {
                PageNav(
                    page = page,
                    totalPages = totalPages,
                    onPrev = { if (page > 1) page-- },
                    onNext = { if (page < totalPages) page++ },
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showCart) {
                SmallButton("Back to bundles", iconPath = "left-arrow", primary = false, onClick = onBackToBrowse)
                CartPanel(
                    items = cart,
                    status = status,
                    modifier = Modifier.fillMaxSize(),
                    onRemove = onRemoveFromCart,
                    onCheckout = onCheckout,
                )
            } else {
                BundlePreviewPanel(
                    bundleView = bundleView,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
                BundleDetailPanel(
                    bundle = selectedBundle,
                    contents = bundleView,
                    status = status,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun HistoryScreen(refreshKey: Int) {
    var transactions by remember { mutableStateOf<List<TransactionInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        loading = true
        loadError = null
        BillingService.fetchTransactions()
            .onSuccess { transactions = it }
            .onFailure { loadError = "Couldn't load transactions: ${it.message}" }
        loading = false
    }

    Box(
        modifier = Modifier.fillMaxSize().clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp))
            .padding(18.dp),
    ) {
        when {
            loading && transactions.isEmpty() -> CenteredNote("Loading transactions...")
            loadError != null && transactions.isEmpty() -> CenteredNote(loadError!!)
            transactions.isEmpty() -> CenteredNote("No transactions yet.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(transactions, key = { it.id }) { tx -> TransactionRow(tx) }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionInfo) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(ppShape(8.dp))
            .border(1.dp, LocalTheme.current.borderColor, ppShape(8.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Column(Modifier.weight(1f)) {
            GuiText("Order #${tx.id}", color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            GuiText(tx.provider.displayName, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        tx.amount?.let { GuiText(money(it), color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        GuiText(
            tx.status.displayName,
            color = statusColor(tx.status),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusColor(status: TransactionStatus): Color = when (status) {
    TransactionStatus.Completed -> Color(0xFF239A60)
    TransactionStatus.Pending -> Color(0xFFE0A030)
    TransactionStatus.Failed -> Color(0xFFFF4444)
    TransactionStatus.Refunded -> Color(0xFF8A9296)
    TransactionStatus.Unknown -> Color(0xFF8A9296)
}

@Composable
private fun CategoryRail(
    selected: CosmeticType,
    onSelect: (CosmeticType) -> Unit,
    types: List<CosmeticType> = CosmeticType.entries.filter { it != CosmeticType.Unknown },
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(58.dp)) {
        types.forEach { type ->
            val isSelected = type == selected
            Box(
                modifier = Modifier.fillMaxWidth().height(57.dp)
                    .clip(ppShape(7.dp))
                    .background(if (isSelected) Accent else LocalTheme.current.chipBackground)
                    .border(1.dp, LocalTheme.current.borderColor, ppShape(7.dp))
                    .clickable { onSelect(type) },
                contentAlignment = Alignment.Center,
            ) {
                GuiText(
                    type.displayName,
                    color = if (isSelected) LocalTheme.current.accentTextColor else LocalTheme.current.textColor,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CosmeticGrid(
    items: List<CosmeticUiItem>,
    modifier: Modifier,
    onSelect: (CosmeticUiItem) -> Unit,
    onEquip: (CosmeticUiItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(19.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items) { item ->
            CosmeticCard(
                item = item,
                onSelect = { onSelect(item) },
                onEquip = { onEquip(item) },
            )
        }
    }
}

@Composable
private fun CosmeticCard(
    item: CosmeticUiItem,
    onSelect: () -> Unit,
    onEquip: () -> Unit,
) {
    val border = if (item.equipped) Accent else LocalTheme.current.borderColor
    val buttonColor = if (item.equipped) Accent else Color(0xB3232D32)
    val activate = { onSelect(); onEquip() }

    Box(
        modifier = Modifier.size(180.dp, 258.dp)
            .clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, border, ppShape(12.dp))
            .clickable(onClick = activate),
    ) {
        CosmeticThumbnail(item, Modifier.offset(17.dp, 17.dp).size(144.dp))
        GuiText(item.name, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(17.dp, 169.dp).width(146.dp))
        GuiText(item.collection, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp, modifier = Modifier.offset(17.dp, 192.dp).width(146.dp))

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                .background(buttonColor)
                .clickable(onClick = activate),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuiText(if (item.equipped) "Equipped" else "Equip", color = LocalTheme.current.accentTextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun BundleGrid(
    bundles: List<BundleInfo>,
    cartKeys: Set<String>,
    modifier: Modifier,
    onSelect: (BundleInfo) -> Unit,
    onAddToCart: (BundleInfo) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(19.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(bundles, key = { it.id }) { bundle ->
            BundleCard(
                bundle = bundle,
                inCart = bundle.cartKey in cartKeys,
                onSelect = { onSelect(bundle) },
                onAddToCart = { onAddToCart(bundle) },
            )
        }
    }
}

@Composable
private fun BundleCard(
    bundle: BundleInfo,
    inCart: Boolean,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit,
) {
    val border = when {
        inCart -> Accent
        bundle.discounted -> Color(0xFF239A60)
        else -> LocalTheme.current.borderColor
    }
    val purchasable = bundle.purchasable
    val buttonColor = when {
        !purchasable -> Color(0x66232D32)
        inCart -> Accent
        bundle.discounted -> Color(0xFF239A60)
        else -> Color(0xB3232D32)
    }

    Box(
        modifier = Modifier.size(180.dp, 258.dp)
            .clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, border, ppShape(12.dp))
            .clickable(onClick = onSelect),
    ) {
        CheckerThumbnail(Modifier.offset(17.dp, 17.dp).size(144.dp))
        GuiText(bundle.name, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(17.dp, 169.dp).width(146.dp))
        Row(modifier = Modifier.offset(17.dp, 193.dp), verticalAlignment = Alignment.CenterVertically) {
            PriceLabel(bundle)
        }

        if (bundle.discounted) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter).size(81.dp, 21.dp)
                    .background(Color(0xFF239A60), ppShapeOf(bottomStart = 4.dp, bottomEnd = 4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                GuiText("${bundle.discountRate}% OFF", color = LocalTheme.current.accentTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                .background(buttonColor)
                .then(if (purchasable) Modifier.clickable(onClick = onAddToCart) else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = when {
                !purchasable -> "Unavailable"
                inCart -> "In cart"
                else -> "Add to cart"
            }
            GuiText(label, color = LocalTheme.current.accentTextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PriceLabel(basePrice: Float?, finalPrice: Float?, discounted: Boolean) {
    when {
        finalPrice == null -> GuiText("—", color = LocalTheme.current.textColorSecondary, fontSize = 14.sp)
        discounted && basePrice != null -> {
            GuiText(money(basePrice), color = Color(0xFFFF4444), fontSize = 10.sp, textDecoration = TextDecoration.LineThrough)
            Spacer(Modifier.width(4.dp))
            GuiText(money(finalPrice), color = Color(0xFF239A60), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        else -> GuiText(money(finalPrice), color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PriceLabel(bundle: BundleInfo) = PriceLabel(bundle.basePrice, bundle.finalPrice, bundle.discounted)

@Composable
private fun PriceLabel(info: CosmeticStoreInfo) = PriceLabel(info.basePrice, info.finalPrice, info.discounted)

@Composable
private fun PriceLabel(entry: CartEntry) = PriceLabel(entry.basePrice, entry.finalPrice, entry.discounted)

@Composable
private fun BundleDetailPanel(
    bundle: BundleInfo?,
    contents: BundleViewResponse?,
    status: String?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp)),
    ) {
        if (bundle == null) {
            CenteredNote("Select a bundle to see what's inside.")
        } else {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GuiText(bundle.name, color = LocalTheme.current.textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(bundle) }
                bundle.description?.takeIf { it.isNotBlank() }?.let {
                    GuiText(it, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                }
                GuiText("Includes", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                val names = contents?.let { view ->
                    (view.cosmetics + view.emotes).map { id ->
                        CosmeticCatalog.getDefinition(id)?.name ?: "Item #$id"
                    }
                }
                when {
                    names == null -> GuiText("Loading contents...", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                    names.isEmpty() -> GuiText("This bundle lists no items.", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                    else -> LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(names) { name ->
                            GuiText("• $name", color = LocalTheme.current.textColor, fontSize = 12.sp)
                        }
                    }
                }
                if (status != null) {
                    GuiText(status, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PageNav(page: Int, totalPages: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmallButton("Prev", iconPath = "left-arrow", primary = false, onClick = onPrev)
        GuiText("Page $page / $totalPages", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        SmallButton("Next", iconPath = "refresh", primary = false, onClick = onNext)
    }
}

@Composable
private fun PreviewPanel(
    selected: CosmeticUiItem?,
    status: String?,
    modifier: Modifier,
    selectedVariantId: Int? = null,
    onSelectVariant: (Int) -> Unit = {},
    showAuraColor: Boolean = false,
    auraColor: Int? = null,
    onPreviewAuraColor: (Int) -> Unit = {},
    onSelectAuraColor: (Int?) -> Unit = {},
) {
    Box(
        modifier = modifier.clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp)),
    ) {
        val hasHeadCosmetic = CosmeticCatalog.localEquipped().equipped
            .containsKey(org.polyfrost.polyplus.client.network.http.responses.BodySlot.Hat)
        PlayerPreview(
            Modifier.align(Alignment.Center).fillMaxWidth().height(330.dp),
            source = PlayerPreviewSource.LocalLive,
            autoSpin = false,
            verticalAnchor = if (hasHeadCosmetic) 0.64f else 0.5f,
            initialYaw = FRONT_YAW_DEG,
            live = true,
        )
        Column(Modifier.align(Alignment.TopStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected != null) {
                Column {
                    GuiText(selected.name, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    GuiText(selected.collection, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                }
            }
            if (status != null) {
                GuiText(status, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
            }
        }
        val variants = if (selected != null && selected.hasVariants) selected.variants else emptyList()
        if (variants.isNotEmpty() || showAuraColor) {
            PreviewPill(
                variants = variants,
                selectedVariantId = selectedVariantId,
                onSelectVariant = onSelectVariant,
                showAuraColor = showAuraColor,
                auraColor = auraColor,
                onPreviewAuraColor = onPreviewAuraColor,
                onSelectAuraColor = onSelectAuraColor,
                modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp),
            )
        }
    }
}

@Composable
private fun PreviewPill(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelectVariant: (Int) -> Unit,
    showAuraColor: Boolean,
    auraColor: Int?,
    onPreviewAuraColor: (Int) -> Unit,
    onSelectAuraColor: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showAuraColor) { if (!showAuraColor) customOpen = false }

    var pillHeight by remember { mutableIntStateOf(0) }
    val popoverGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }

    Box(modifier) {
        Row(
            modifier = Modifier
                .onSizeChanged { pillHeight = it.height }
                .clip(ppShape(PILL_RADIUS))
                .background(LocalTheme.current.chipBackground)
                .border(1.dp, LocalTheme.current.borderColor, ppShape(PILL_RADIUS))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (variants.isNotEmpty()) {
                VariantStrip(
                    variants = variants,
                    selectedVariantId = selectedVariantId,
                    onSelect = onSelectVariant,
                )
            }
            if (variants.isNotEmpty() && showAuraColor) {
                Box(Modifier.width(1.dp).height(20.dp).background(LocalTheme.current.borderColor))
            }
            if (showAuraColor) {
                AuraSwatchRow(
                    selectedColor = auraColor,
                    customOpen = customOpen,
                    onToggleCustom = { customOpen = !customOpen },
                    onCommit = onSelectAuraColor,
                )
            }
        }
        if (customOpen && showAuraColor) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, pillHeight + popoverGapPx),
                onDismissRequest = { customOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                AuraCustomPopover(
                    selectedColor = auraColor,
                    onPreview = onPreviewAuraColor,
                    onCommit = onSelectAuraColor,
                )
            }
        }
    }
}

@Composable
private fun RowScope.VariantStrip(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelect: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { VARIANT_WHEEL_STEP.toPx() }
    val arrowStep = with(LocalDensity.current) { VARIANT_ARROW_STEP.toPx() }
    val overflows = scrollState.maxValue > 0 && scrollState.maxValue != Int.MAX_VALUE

    if (overflows) {
        ScrollArrow(
            iconName = "left-arrow",
            enabled = scrollState.value > 0,
            onClick = { scope.launch { scrollState.animateScrollBy(-arrowStep) } },
        )
    }
    Row(
        modifier = Modifier.weight(1f, fill = false)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        val delta = event.changes.first().scrollDelta
                        if (delta.x == 0f && delta.y != 0f) {
                            scope.launch { scrollState.scrollBy(delta.y * wheelStep) }
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (variant in variants) {
            val isSelected = variant.id == selectedVariantId
            Box(
                modifier = Modifier
                    .clip(ppShape(6.dp))
                    .background(if (isSelected) Accent else LocalTheme.current.componentBackground)
                    .border(1.dp, if (isSelected) Accent else LocalTheme.current.borderColor, ppShape(6.dp))
                    .clickable { onSelect(variant.id) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                GuiText(
                    variant.name,
                    color = if (isSelected) LocalTheme.current.accentTextColor else LocalTheme.current.textColor,
                    fontSize = 12.sp,
                )
            }
        }
    }
    if (overflows) {
        ScrollArrow(
            iconName = "right-arrow",
            enabled = scrollState.value < scrollState.maxValue,
            onClick = { scope.launch { scrollState.animateScrollBy(arrowStep) } },
        )
    }
}

@Composable
private fun ScrollArrow(iconName: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(18.dp)
            .clip(ppShape(4.dp))
            .background(LocalTheme.current.componentBackground)
            .border(1.dp, LocalTheme.current.borderColor, ppShape(4.dp))
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconName, color = LocalTheme.current.textColor, modifier = Modifier.size(10.dp))
    }
}

@Composable
private fun AuraSwatchRow(
    selectedColor: Int?,
    customOpen: Boolean,
    onToggleCustom: () -> Unit,
    onCommit: (Int?) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        AuraDot(
            color = Color(DEFAULT_AURA_ARGB),
            isSelected = selectedColor == null,
            onClick = { onCommit(null) },
        )
        for (color in AURA_PRESETS) {
            AuraDot(
                color = Color(color),
                isSelected = selectedColor == color,
                onClick = { onCommit(color) },
            )
        }
        val isCustom = selectedColor != null && selectedColor !in AURA_PRESETS
        AuraDot(
            color = if (isCustom) Color(selectedColor!!) else LocalTheme.current.componentBackground,
            isSelected = isCustom || customOpen,
            onClick = onToggleCustom,
        ) {
            if (!isCustom) {
                GuiText("+", color = LocalTheme.current.textColorSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AuraDot(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier.size(18.dp)
            .clip(ppShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AlphaCheckerboard(Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(color))
        Box(
            Modifier.fillMaxSize().border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Accent else LocalTheme.current.borderColor,
                shape = ppShape(4.dp),
            ),
        )
        content()
    }
}

/**
 * Chips for selecting which variant of a grouped cosmetic to equip. The chosen
 * variant is what the Equip button acts on (the client then auto-resolves the
 * slim/wide model to the player's skin).
 */
@Composable
private fun VariantPicker(
    variants: List<CosmeticVariantUi>,
    selectedVariantId: Int?,
    onSelect: (Int) -> Unit,
    ownedVariantIds: Set<Int> = emptySet(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.width(220.dp)) {
        GuiText("Variant", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
        for (row in variants.chunked(2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (variant in row) {
                    val isSelected = variant.id == selectedVariantId
                    Box(
                        modifier = Modifier
                            .alpha(if (variant.id in ownedVariantIds && !isSelected) 0.55f else 1f)
                            .clip(ppShape(6.dp))
                            .background(if (isSelected) Accent else LocalTheme.current.chipBackground)
                            .border(1.dp, if (isSelected) Accent else LocalTheme.current.borderColor, ppShape(6.dp))
                            .clickable { onSelect(variant.id) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        GuiText(
                            variant.name,
                            color = if (isSelected) LocalTheme.current.accentTextColor else LocalTheme.current.textColor,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

private val PILL_RADIUS = 18.dp

private val VARIANT_WHEEL_STEP = 48.dp

private val VARIANT_ARROW_STEP = 96.dp

private val AURA_COLORS: List<Int> = listOf(
    0xFFFF4444.toInt(), // red
    0xFFFF9E3D.toInt(), // orange
    0xFFF4D03F.toInt(), // yellow
    0xFF2ECC71.toInt(), // green
    0xFF1ABC9C.toInt(), // teal
    0xFF3DA5FF.toInt(), // blue
    0xFF6C5CE7.toInt(), // indigo
    0xFFB05CFF.toInt(), // purple
    0xFFFF6FD8.toInt(), // pink
    0xFFFFFFFF.toInt(), // white
    0xFF3A3F43.toInt(), // dark
    0xFF9BA2A6.toInt(), // gray
)

private const val DEFAULT_AURA_ARGB: Int = -1

private val AURA_PRESETS: List<Int> = AURA_COLORS.filter { it != DEFAULT_AURA_ARGB }

private fun argbToHsb(argb: Int): FloatArray {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val saturation = if (max == 0f) 0f else delta / max
    val hue = when {
        delta == 0f -> 0f
        max == r -> ((g - b) / delta).mod(6f) * 60f
        max == g -> ((b - r) / delta + 2f) * 60f
        else -> ((r - g) / delta + 4f) * 60f
    }
    return floatArrayOf(hue, saturation, max)
}

private fun hsbToColor(hue: Float, saturation: Float, brightness: Float): Color {
    val h = hue / 60f
    val c = brightness * saturation
    val x = c * (1f - abs(h.mod(2f) - 1f))
    val m = brightness - c
    val (r, g, b) = when {
        h < 1f -> Triple(c, x, 0f)
        h < 2f -> Triple(x, c, 0f)
        h < 3f -> Triple(0f, c, x)
        h < 4f -> Triple(0f, x, c)
        h < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, 1f)
}

private fun argbHex(argb: Int): String =
    "%02X%02X%02X".format((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

private fun hexToArgb(hex: String, alpha: Float): Int? {
    val h = hex.removePrefix("#").trim()
    if (h.length != 6) return null
    return try {
        ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or Integer.parseInt(h, 16)
    } catch (_: Exception) {
        null
    }
}

private fun Color.toArgb(alpha: Float): Int =
    ((alpha.coerceIn(0f, 1f) * 255f).roundToInt() shl 24) or
        ((red.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
        ((green.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
        (blue.coerceIn(0f, 1f) * 255f).roundToInt()

private class AuraPickerState(argb: Int) {
    var hue by mutableFloatStateOf(0f)
    var saturation by mutableFloatStateOf(0f)
    var brightness by mutableFloatStateOf(0f)
    var alpha by mutableFloatStateOf(1f)

    var lastEmitted: Int? = null

    init {
        applyFrom(argb)
    }

    fun applyFrom(argb: Int) {
        val hsb = argbToHsb(argb)
        hue = hsb[0]
        saturation = hsb[1]
        brightness = hsb[2]
        alpha = ((argb ushr 24) and 0xFF) / 255f
    }

    fun currentArgb(): Int = hsbToColor(hue, saturation, brightness).toArgb(alpha)
}

@Composable
private fun AlphaCheckerboard(modifier: Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val cell = 4.dp.toPx()
            val cols = ceil(size.width / cell).toInt()
            val rows = ceil(size.height / cell).toInt()
            for (x in 0..cols) {
                for (y in 0..rows) {
                    drawRect(
                        color = if ((x + y) % 2 == 0) Color(0xFF5F6568) else Color(0xFF3D4245),
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        },
    )
}

@Composable
private fun AuraCustomPopover(
    selectedColor: Int?,
    onPreview: (Int) -> Unit,
    onCommit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = remember { AuraPickerState(selectedColor ?: 0xFFFFFFFF.toInt()) }
    var hexText by remember { mutableStateOf(argbHex(state.currentArgb())) }
    LaunchedEffect(selectedColor) {
        if (selectedColor != null && selectedColor != state.lastEmitted) {
            state.applyFrom(selectedColor)
            hexText = argbHex(selectedColor)
        }
    }

    fun emitPreview() {
        val argb = state.currentArgb()
        state.lastEmitted = argb
        hexText = argbHex(argb)
        onPreview(argb)
    }

    fun emitCommit() {
        val argb = state.currentArgb()
        state.lastEmitted = argb
        onCommit(argb)
    }

    val hue = state.hue
    val saturation = state.saturation
    val brightness = state.brightness
    val alpha = state.alpha
    val current = hsbToColor(hue, saturation, brightness)

    Column(
        modifier = modifier.width(190.dp)
            .clip(ppShape(10.dp))
            .background(LocalTheme.current.popupBackground)
            .border(1.dp, LocalTheme.current.borderColor, ppShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GuiText("Aura Color", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)

        var paneSize by remember { mutableStateOf(Size.Zero) }
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp)
                .clip(ppShape(6.dp))
                .onSizeChanged { paneSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .drawWithCache {
                    val hueColor = hsbToColor(hue, 1f, 1f)
                    val horizontal = Brush.horizontalGradient(listOf(Color.White, hueColor))
                    val vertical = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                    onDrawBehind {
                        drawRect(horizontal)
                        drawRect(vertical)
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float, y: Float) {
                            state.saturation = (x / paneSize.width).coerceIn(0f, 1f)
                            state.brightness = 1f - (y / paneSize.height).coerceIn(0f, 1f)
                            emitPreview()
                        }
                        update(down.position.x, down.position.y)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    update(ch.position.x, ch.position.y)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        emitCommit()
                    }
                },
        ) {
            val selX = (saturation * paneSize.width).coerceIn(0f, paneSize.width)
            val selY = ((1f - brightness) * paneSize.height).coerceIn(0f, paneSize.height)
            Box(
                modifier = Modifier
                    .offset { IntOffset((selX - 7.dp.toPx()).roundToInt(), (selY - 7.dp.toPx()).roundToInt()) }
                    .size(14.dp)
                    .clip(ppShape(7.dp))
                    .background(current)
                    .border(2.dp, Color.White, ppShape(7.dp)),
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(14.dp)
                .clip(ppShape(4.dp))
                .drawWithCache {
                    val gradient = Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
                    )
                    onDrawBehind { drawRect(gradient) }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float) {
                            state.hue = ((x / size.width) * 360f).coerceIn(0f, 360f)
                            emitPreview()
                        }
                        update(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    update(ch.position.x)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        emitCommit()
                    }
                },
        ) {
            var barWidth by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxWidth().height(14.dp).onSizeChanged { barWidth = it.width.toFloat() })
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset(((hue / 360f) * barWidth - 5.dp.toPx()).roundToInt().coerceAtLeast(0), 0) }
                    .size(10.dp)
                    .clip(ppShape(5.dp))
                    .background(Color.White)
                    .border(1.dp, Color.White.copy(0.5f), ppShape(5.dp)),
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth().height(14.dp)
                .clip(ppShape(4.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun update(x: Float) {
                            state.alpha = (x / size.width).coerceIn(0f, 1f)
                            emitPreview()
                        }
                        update(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { ch ->
                                if (ch.pressed) {
                                    update(ch.position.x)
                                    ch.consume()
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        emitCommit()
                    }
                },
        ) {
            AlphaCheckerboard(Modifier.fillMaxWidth().height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(14.dp)
                    .drawWithCache {
                        val gradient = Brush.horizontalGradient(listOf(current.copy(alpha = 0f), current))
                        onDrawBehind { drawRect(gradient) }
                    },
            )
            var alphaBarWidth by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxWidth().height(14.dp).onSizeChanged { alphaBarWidth = it.width.toFloat() })
            Box(
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset { IntOffset((alpha * alphaBarWidth - 5.dp.toPx()).roundToInt().coerceAtLeast(0), 0) }
                    .size(10.dp)
                    .clip(ppShape(5.dp))
                    .background(Color.White)
                    .border(1.dp, Color.White.copy(0.5f), ppShape(5.dp)),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.size(28.dp).clip(ppShape(5.dp))) {
                AlphaCheckerboard(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(current.copy(alpha = alpha)))
                Box(Modifier.fillMaxSize().border(1.dp, LocalTheme.current.borderColor, ppShape(5.dp)))
            }
            BasicTextField(
                value = hexText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isLetterOrDigit() }.take(6)
                    hexText = filtered
                    hexToArgb(filtered, state.alpha)?.let { argb ->
                        state.applyFrom(argb)
                        state.lastEmitted = argb
                        onCommit(argb)
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = LocalTheme.current.textColor,
                    fontSize = 12.sp,
                    fontFamily = LocalTheme.current.typography.family,
                ),
                cursorBrush = SolidColor(LocalTheme.current.textColor),
                modifier = Modifier.weight(1f)
                    .clip(ppShape(5.dp))
                    .background(LocalTheme.current.chipBackground)
                    .border(1.dp, LocalTheme.current.borderColor, ppShape(5.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GuiText("#", color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                        inner()
                    }
                },
            )
        }

    }
}

@Composable
private fun CartPanel(
    items: List<CartEntry>,
    status: String?,
    modifier: Modifier,
    onRemove: (String) -> Unit,
    onCheckout: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(11.dp)) {
        if (items.isEmpty()) {
            CenteredNote("Your cart is empty.")
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(items, key = { it.key }) { item ->
                CartRow(item = item, onRemove = { onRemove(item.key) })
            }
        }

        val subtotal = items.sumOf { (it.basePrice ?: 0f).toDouble() }.toFloat()
        val total = items.sumOf { (it.finalPrice ?: it.basePrice ?: 0f).toDouble() }.toFloat()
        val discount = (subtotal - total).coerceAtLeast(0f)
        RowTotals("Subtotal", money(subtotal), LocalTheme.current.textColor)
        if (discount > 0f) {
            RowTotals("Discounts", "-${money(discount)}", Color(0xFF239A60))
        }
        RowTotals("Total", money(total), Color.White, large = true)
        if (status != null) {
            GuiText(status, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
        }
        SmallButton(
            label = "Checkout ${items.size} item${if (items.size == 1) "" else "s"}",
            iconPath = "assets/polyplus/ico/shopping-cart/0.svg",
            primary = true,
            onClick = onCheckout,
        )
    }
}

@Composable
private fun CartRow(item: CartEntry, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(81.dp)
            .clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        CoverThumbnail(item.coverAssetId, Modifier.size(58.dp))
        Column(Modifier.weight(1f)) {
            GuiText(item.name, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            item.description?.takeIf { it.isNotBlank() }?.let {
                GuiText(it, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(item) }
            if (item.discounted) GuiText("SAVE ${item.discountRate}%", color = Color(0xFF239A60), fontSize = 12.sp)
            GuiText("Remove", color = Color(0xFFFF4444), fontSize = 12.sp, modifier = Modifier.clickable(onClick = onRemove))
        }
    }
}

@Composable
private fun RowTotals(label: String, value: String, color: Color, large: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GuiText(label, color = LocalTheme.current.textColor, fontSize = if (large) 18.sp else 12.sp, modifier = Modifier.weight(1f))
        GuiText(value, color = color, fontSize = if (large) 22.sp else 12.sp, fontWeight = if (large) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GuiText(text, color = LocalTheme.current.textColorSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CosmeticThumbnail(item: CosmeticUiItem, modifier: Modifier) {
    val source = rememberCosmeticPreviewSource(item)
    val framing = cosmeticPreviewFraming(item.type)
    Box(modifier) {
        CheckerThumbnail(Modifier.fillMaxSize())
        if (source != null) {
            PlayerPreview(
                Modifier.fillMaxSize(),
                source = source,
                autoSpin = false,
                allowDrag = false,
                modelScale = framing.modelScale,
                verticalAnchor = framing.verticalAnchor,
                initialYaw = framing.yawDeg,
                previewKey = "card-${item.groupId}",
                live = item.type == CosmeticType.Aura,
            )
        }
    }
}

private data class PreviewFraming(val yawDeg: Float, val modelScale: Float, val verticalAnchor: Float)

private const val FRONT_YAW_DEG = 180f + 22.9f
private const val BACK_YAW_DEG = 22.9f
private const val STORE_PREVIEW_FADE_FRACTION = 0.22f

private fun cosmeticPreviewFraming(type: CosmeticType): PreviewFraming = when (type) {
    CosmeticType.Hat, CosmeticType.Glasses -> PreviewFraming(FRONT_YAW_DEG, 1.0f, 1.5f)
    CosmeticType.Boots -> PreviewFraming(FRONT_YAW_DEG, 0.85f, 0.26f)
    CosmeticType.Shoulder -> PreviewFraming(FRONT_YAW_DEG, 0.58f, 0.62f)
    CosmeticType.Wings -> PreviewFraming(BACK_YAW_DEG, 0.5f, 0.5f)
    CosmeticType.Backpack -> PreviewFraming(BACK_YAW_DEG, 0.55f, 0.55f)
    CosmeticType.Cape -> PreviewFraming(BACK_YAW_DEG, 0.42f, 0.5f)
    CosmeticType.Aura -> PreviewFraming(FRONT_YAW_DEG, 0.5f, 0.5f)
    CosmeticType.Glove -> PreviewFraming(FRONT_YAW_DEG, 0.7f, 0.42f)
    else -> PreviewFraming(FRONT_YAW_DEG, 0.42f, 0.52f)
}

@Composable
private fun rememberCosmeticPreviewSource(item: CosmeticUiItem): PlayerPreviewSource? {
    val cosmeticId = item.equippedVariantId ?: item.variants.firstOrNull()?.id ?: return null
    return rememberCosmeticPreviewSource(cosmeticId, item.type)
}

@Composable
private fun rememberCosmeticPreviewSource(cosmeticId: Int, type: CosmeticType): PlayerPreviewSource? {
    val isCape = type == CosmeticType.Cape

    var previewId by remember(cosmeticId) { mutableIntStateOf(cosmeticId) }
    var loadTick by remember(cosmeticId) { mutableIntStateOf(0) }
    LaunchedEffect(cosmeticId) {
        val slim = ClientPlatform.runOnMainSync { ClientPlatform.localSkinSlim() }
        val id = CosmeticCatalog.resolveVariantForSkin(cosmeticId, slim)
        previewId = id
        val loaded = if (isCape) {
            CosmeticAssetCache.getCapeResource(id) != null
        } else {
            CosmeticAssetCache.getAttachedCosmetic(id) != null
        }
        if (!loaded) {
            CosmeticAssetCache.ensureCosmeticLoaded(id)
            loadTick++
        }
    }

    return remember(previewId, loadTick, isCape) {
        if (isCape) {
            PlayerPreviewSource.Override(CosmeticEquipment(), capeTexture = CosmeticAssetCache.getCapeResource(previewId))
        } else {
            val attached = CosmeticAssetCache.getAttachedCosmetic(previewId) ?: return@remember null
            val equipment = CosmeticEquipment()
            equipment.equip(attached)
            PlayerPreviewSource.Override(equipment)
        }
    }
}

private fun isNewItem(createdAt: String): Boolean {
    if (createdAt.isBlank()) return false
    return runCatching {
        val created = java.time.OffsetDateTime.parse(createdAt).toInstant()
        created.isAfter(java.time.Instant.now().minus(java.time.Duration.ofDays(7)))
    }.getOrDefault(false)
}

private const val STORE_PAGE_SIZE = CosmeticStore.MAX_PAGE_SIZE

private const val STORE_PREFETCH_DISTANCE = 6

@Composable
private fun StoreScreen(
    cart: List<CartEntry>,
    showCart: Boolean,
    status: String?,
    ownedIds: Set<Int>,
    onRemoveFromCart: (String) -> Unit,
    onBackToBrowse: () -> Unit,
    onCheckout: () -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onBuyNow: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
) {
    val variantPicks = remember { mutableStateMapOf<Int, Int>() }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CosmeticType.Cape) }
    var nextPage by remember { mutableIntStateOf(1) }
    var results by remember { mutableStateOf<List<CosmeticStoreInfo>>(emptyList()) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var stockedTypes by remember {
        mutableStateOf(CosmeticType.entries.filter { it != CosmeticType.Unknown })
    }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        stockedTypes = CosmeticStore.stockedTypes()
    }

    LaunchedEffect(submitted, selectedType, nextPage) {
        loading = true
        loadError = null
        CosmeticStore.search(
            page = nextPage,
            perPage = STORE_PAGE_SIZE,
            text = submitted.ifBlank { null },
            types = listOf(selectedType.serializedName),
        )
            .onSuccess { resp ->
                results = if (nextPage == 1) resp.results else results + resp.results
                hasMore = nextPage < resp.pagination.totalPages
            }
            .onFailure {
                loadError = "Couldn't load store: ${it.message}"
                hasMore = false
            }
        loading = false
    }

    val items = remember(results, ownedIds) {
        results.sortedBy { if (it.fullyOwned(ownedIds)) 1 else 0 }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            last.index >= info.totalItemsCount - STORE_PREFETCH_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, hasMore, loading) {
        if (nearEnd && hasMore && !loading) nextPage++
    }

    LaunchedEffect(items) {
        if (selectedId == null || items.none { it.id == selectedId }) {
            selectedId = items.firstOrNull()?.id
        }
    }

    val cartKeys = cart.map { it.key }.toSet()
    val selected = items.firstOrNull { it.id == selectedId }

    fun reset() {
        results = emptyList()
        hasMore = true
        nextPage = 1
    }

    fun submit() {
        submitted = query.trim()
        reset()
    }

    LaunchedEffect(stockedTypes) {
        if (stockedTypes.isNotEmpty() && selectedType !in stockedTypes) {
            selectedType = stockedTypes.first()
            reset()
        }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(19.dp)) {
        CategoryRail(
            selected = selectedType,
            onSelect = {
                if (it != selectedType) {
                    selectedType = it
                    reset()
                }
            },
            types = stockedTypes,
        )
        Column(modifier = Modifier.width(596.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            StoreSearchBar(query = query, onQueryChange = { query = it }, onSubmit = { submit() })
            when {
                loading && items.isEmpty() -> CenteredNote("Loading cosmetics...")
                loadError != null && items.isEmpty() -> CenteredNote(loadError!!)
                items.isEmpty() -> CenteredNote(
                    if (submitted.isBlank()) {
                        "No ${selectedType.displayName.lowercase()} cosmetics available yet."
                    } else {
                        "No cosmetics match \"$submitted\"."
                    },
                )
                else -> StoreGrid(
                    items = items,
                    cartKeys = cartKeys,
                    ownedIds = ownedIds,
                    variantPicks = variantPicks,
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onSelect = { selectedId = it.id },
                    onAddToCart = onAddToCart,
                )
            }
        }

        val selectedVariant = selected?.let { selectedStoreVariant(it, variantPicks, ownedIds) }
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showCart) {
                SmallButton("Back to store", iconPath = "left-arrow", primary = false, onClick = onBackToBrowse)
                CartPanel(
                    items = cart,
                    status = status,
                    modifier = Modifier.fillMaxSize(),
                    onRemove = onRemoveFromCart,
                    onCheckout = onCheckout,
                )
            } else {
                StoreDetailPanel(
                    info = selected,
                    variant = selectedVariant,
                    status = status,
                    ownedIds = ownedIds,
                    inCart = selectedVariant?.let { cosmeticCartKey(it.id) in cartKeys } ?: false,
                    onSelectVariant = { variantId -> selected?.let { variantPicks[it.id] = variantId } },
                    onAddToCart = onAddToCart,
                    onBuyNow = onBuyNow,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun StoreSearchBar(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(33.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = LocalTheme.current.textColor,
                fontSize = 13.sp,
                fontFamily = LocalTheme.current.typography.family,
            ),
            cursorBrush = SolidColor(LocalTheme.current.textColor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f).fillMaxHeight()
                .clip(ppShape(7.dp))
                .background(LocalTheme.current.chipBackground)
                .border(1.dp, LocalTheme.current.borderColor, ppShape(7.dp))
                .padding(horizontal = 12.dp),
            decorationBox = { inner ->
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            GuiText("Search cosmetics...", color = LocalTheme.current.textColorSecondary, fontSize = 13.sp)
                        }
                        inner()
                    }
                }
            },
        )
        SmallButton("Search", iconPath = "assets/polyplus/ico/search.svg", primary = true, onClick = onSubmit)
    }
}

@Composable
private fun StoreGrid(
    items: List<CosmeticStoreInfo>,
    cartKeys: Set<String>,
    ownedIds: Set<Int>,
    variantPicks: Map<Int, Int>,
    state: LazyGridState,
    modifier: Modifier,
    onSelect: (CosmeticStoreInfo) -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(19.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.id }) { info ->
            val variant = selectedStoreVariant(info, variantPicks, ownedIds)
            StoreCard(
                info = info,
                variant = variant,
                owned = info.fullyOwned(ownedIds),
                variantOwned = variant.id in ownedIds,
                inCart = cosmeticCartKey(variant.id) in cartKeys,
                onSelect = { onSelect(info) },
                onAddToCart = { onAddToCart(info, variant) },
            )
        }
    }
}

@Composable
private fun StoreCard(
    info: CosmeticStoreInfo,
    variant: CosmeticVariantUi,
    owned: Boolean,
    variantOwned: Boolean,
    inCart: Boolean,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit,
) {
    val variantCount = info.uiVariants().size
    val border = when {
        inCart -> Accent
        owned -> LocalTheme.current.borderColor
        info.discounted -> Color(0xFF239A60)
        else -> LocalTheme.current.borderColor
    }
    val buttonColor = if (inCart) Accent else Color(0xB3232D32)

    Box(
        modifier = Modifier.size(180.dp, 258.dp)
            // Owned listings stay legible but read as already-handled.
            .alpha(if (owned) 0.6f else 1f)
            .clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, border, ppShape(12.dp))
            .clickable(onClick = onSelect),
    ) {
        StoreThumbnail(info, variant.id, Modifier.offset(17.dp, 17.dp).size(144.dp))
        GuiText(info.name, color = LocalTheme.current.textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.offset(17.dp, 169.dp).width(146.dp))
        Row(modifier = Modifier.offset(17.dp, 193.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PriceLabel(info)
            if (variantCount > 1) {
                GuiText("$variantCount variants", color = LocalTheme.current.textColorSecondary, fontSize = 11.sp)
            }
        }

        if (owned) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).offset(x = 8.dp, y = 8.dp)
                    .clip(ppShape(4.dp))
                    .background(Color(0xFF239A60))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                GuiText("OWNED", color = LocalTheme.current.accentTextColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        } else if (isNewItem(info.createdAt)) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).offset(x = 8.dp, y = 8.dp)
                    .clip(ppShape(4.dp))
                    .background(Accent)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                GuiText("NEW", color = LocalTheme.current.accentTextColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (info.discounted && !owned) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).size(66.dp, 21.dp)
                    .background(Color(0xFF239A60), ppShapeOf(bottomStart = 4.dp, topEnd = 12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                GuiText("${info.discountRate}% OFF", color = LocalTheme.current.accentTextColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                .background(buttonColor)
                .clickable(onClick = if (inCart || variantOwned) onSelect else onAddToCart),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = when {
                variantOwned && owned -> "Owned"
                variantOwned -> "Owned - see variants"
                inCart -> "In cart"
                else -> "Add to cart"
            }
            GuiText(label, color = LocalTheme.current.accentTextColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StoreThumbnail(info: CosmeticStoreInfo, variantId: Int, modifier: Modifier) {
    val source = rememberCosmeticPreviewSource(variantId, info.type)
    val framing = cosmeticPreviewFraming(info.type)
    Box(modifier) {
        CheckerThumbnail(Modifier.fillMaxSize())
        if (source != null) {
            PlayerPreview(
                Modifier.fillMaxSize(),
                source = source,
                autoSpin = false,
                allowDrag = false,
                modelScale = framing.modelScale,
                verticalAnchor = framing.verticalAnchor,
                initialYaw = framing.yawDeg,
                previewKey = "store-$variantId",
                live = info.type == CosmeticType.Aura,
            )
        }
    }
}

@Composable
private fun StoreDetailPanel(
    info: CosmeticStoreInfo?,
    variant: CosmeticVariantUi?,
    status: String?,
    ownedIds: Set<Int>,
    inCart: Boolean,
    onSelectVariant: (Int) -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onBuyNow: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(330.dp)
                .clip(ppShape(12.dp))
                .background(cardBrush())
                .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp)),
        ) {
            if (info == null || variant == null) {
                CenteredNote("Select a cosmetic to preview it.")
            } else {
                val source = rememberCosmeticPreviewSource(variant.id, info.type)
                val fades = info.type != CosmeticType.Boots
                PlayerPreview(
                    Modifier.align(Alignment.Center).fillMaxWidth().height(330.dp),
                    source = source ?: PlayerPreviewSource.LocalLive,
                    autoSpin = false,
                    bottomFade = if (fades) {
                        Brush.verticalGradient(
                            1f - STORE_PREVIEW_FADE_FRACTION to Color.White,
                            1f to Color.Transparent,
                        )
                    } else {
                        null
                    },
                    bottomFadeFraction = if (fades) STORE_PREVIEW_FADE_FRACTION else 0f,
                    verticalAnchor = when (info.type) {
                        CosmeticType.Hat -> 0.70f
                        CosmeticType.Boots -> 0.44f
                        else -> 0.56f
                    },
                    initialYaw = FRONT_YAW_DEG,
                    previewKey = "store-detail-${variant.id}",
                    live = true,
                )
                if (variant.id in ownedIds) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .clip(ppShape(4.dp))
                            .background(Color(0xFF239A60))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        GuiText("OWNED", color = LocalTheme.current.accentTextColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                } else if (isNewItem(info.createdAt)) {
                    Box(
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
                            .clip(ppShape(4.dp))
                            .background(Accent)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        GuiText("NEW", color = LocalTheme.current.accentTextColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .clip(ppShape(12.dp))
                .background(cardBrush())
                .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp)),
        ) {
            if (info == null || variant == null) {
                CenteredNote("Browse the store to buy individual cosmetics.")
            } else {
                val variants = info.uiVariants()
                Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GuiText(info.type.displayName, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                        GuiText(info.name, color = LocalTheme.current.textColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) { PriceLabel(info) }
                        if (variants.size > 1) {
                            VariantPicker(
                                variants = variants,
                                selectedVariantId = variant.id,
                                onSelect = onSelectVariant,
                                ownedVariantIds = ownedIds,
                            )
                        }
                        if (info.tags.all.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (tag in info.tags.all.take(4)) {
                                    Box(
                                        modifier = Modifier.clip(ppShape(5.dp))
                                            .background(LocalTheme.current.chipBackground)
                                            .border(1.dp, LocalTheme.current.borderColor, ppShape(5.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) {
                                        GuiText(tag.replaceFirstChar { it.uppercase() }, color = LocalTheme.current.textColor, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        info.description?.takeIf { it.isNotBlank() }?.let {
                            GuiText(it, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                        }
                        if (status != null) {
                            GuiText(status, color = LocalTheme.current.textColorSecondary, fontSize = 12.sp)
                        }
                    }
                    if (variant.id in ownedIds) {
                        GuiText(
                            "You already own this cosmetic.",
                            color = LocalTheme.current.textColorSecondary,
                            fontSize = 12.sp,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton(
                                label = if (inCart) "In cart" else "Add to cart",
                                iconPath = "assets/polyplus/ico/shopping-cart/0.svg",
                                primary = false,
                                onClick = { if (!inCart) onAddToCart(info, variant) },
                            )
                            SmallButton(
                                label = "Buy now",
                                iconPath = "assets/polyplus/ico/shopping-bag.svg",
                                primary = true,
                                onClick = { onBuyNow(info, variant) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverThumbnail(coverAssetId: Int?, modifier: Modifier) {
    val cover = rememberCoverImage(coverAssetId)
    Box(modifier) {
        CheckerThumbnail(Modifier.fillMaxSize())
        if (cover != null) {
            Image(
                bitmap = cover,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(ppShape(5.dp)),
            )
        }
    }
}

@Composable
private fun CheckerThumbnail(modifier: Modifier) {
    Box(
        modifier = modifier.clip(ppShape(5.dp))
            .border(1.dp, LocalTheme.current.borderColor, ppShape(5.dp))
            .drawBehind {
                val cell = 9.dp.toPx()
                val cols = ceil(size.width / cell).toInt()
                val rows = ceil(size.height / cell).toInt()
                for (x in 0..cols) {
                    for (y in 0..rows) {
                        drawRect(
                            color = if ((x + y) % 2 == 0) Color(0xFF5F6568) else Color(0xFF3D4245),
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            },
    )
}

@Composable
private fun BundlePreviewPanel(
    bundleView: BundleViewResponse?,
    modifier: Modifier,
) {
    Box(
        modifier = modifier.clip(ppShape(12.dp))
            .background(cardBrush())
            .border(1.dp, LocalTheme.current.borderColor, ppShape(12.dp)),
    ) {
        val bundleSource = rememberBundlePreviewSource(bundleView)
        val bundleHasHat = (bundleSource as? PlayerPreviewSource.Override)
            ?.equipment?.get(org.polyfrost.polyplus.client.network.http.responses.BodySlot.Hat) != null
        PlayerPreview(
            Modifier.align(Alignment.Center).fillMaxWidth().height(300.dp),
            source = bundleSource,
            autoSpin = false,
            verticalAnchor = if (bundleHasHat) 0.64f else 0.5f,
            live = true,
        )
    }
}

@Composable
private fun rememberBundlePreviewSource(bundleView: BundleViewResponse?): PlayerPreviewSource {
    val bundleCosmeticIds = bundleView?.cosmetics ?: emptyList()
    if (bundleCosmeticIds.isEmpty()) return PlayerPreviewSource.LocalLive

    var loadTick by remember(bundleCosmeticIds) { mutableIntStateOf(0) }
    LaunchedEffect(bundleCosmeticIds) {
        var changed = false
        for (id in bundleCosmeticIds) {
            if (CosmeticAssetCache.getAttachedCosmetic(id) == null) {
                CosmeticAssetCache.ensureCosmeticLoaded(id)
                changed = true
            }
        }
        if (changed) loadTick++
    }

    return remember(bundleCosmeticIds, loadTick) {
        val equipment = CosmeticEquipment()
        for (id in bundleCosmeticIds) {
            CosmeticAssetCache.getAttachedCosmetic(id)?.let { equipment.equip(it) }
        }
        for (id in CosmeticCatalog.localEquipped().ids()) {
            CosmeticAssetCache.getAttachedCosmetic(id)?.let { equipment.equip(it) }
        }
        PlayerPreviewSource.Override(equipment)
    }
}

@Composable
private fun GuiText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    textDecoration: TextDecoration? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = LocalTheme.current.typography.family,
            textAlign = textAlign,
            textDecoration = textDecoration,
        ),
    )
}

@Composable
private fun rememberCosmeticItems(refreshKey: Int): List<CosmeticUiItem> =
    remember(refreshKey) {
        val ownedIds = CosmeticCatalog.ownedIds()
        val equippedIds = CosmeticCatalog.localEquipped().equipped.values.toSet()
        val selectedEmote = CosmeticCatalog.selectedEmoteId()

        val groupItems = CosmeticCatalog.cosmeticGroupViews()
            .sortedWith(compareBy<CosmeticGroupView> { it.type.ordinal }.thenBy { it.groupId })
            .map { group ->
                val byLabel = LinkedHashMap<String, CosmeticVariantUi>()
                for (variant in group.variants) {
                    byLabel.getOrPut(variant.variantName) {
                        CosmeticVariantUi(variant.id, variant.variantName)
                    }
                }
                val equippedVariantId = group.variants.firstOrNull { it.id in equippedIds }?.id
                CosmeticUiItem(
                    groupId = group.groupId,
                    type = group.type,
                    name = group.name,
                    collection = "${group.type.displayName} Collection",
                    owned = group.variants.any { it.id in ownedIds },
                    equipped = equippedVariantId != null,
                    variants = byLabel.values.toList(),
                    equippedVariantId = equippedVariantId,
                )
            }

        val emoteItems = CosmeticCatalog.allEmoteDefinitions()
            .sortedBy { it.id }
            .map { emote ->
                CosmeticUiItem(
                    groupId = emote.id,
                    type = CosmeticType.Emote,
                    name = emote.name,
                    collection = "${CosmeticType.Emote.displayName} Collection",
                    owned = emote.id in ownedIds,
                    equipped = selectedEmote == emote.id,
                    variants = listOf(CosmeticVariantUi(emote.id, emote.name)),
                    equippedVariantId = if (selectedEmote == emote.id) emote.id else null,
                )
            }

        groupItems + emoteItems
    }

private fun selectedVariantId(item: CosmeticUiItem, picks: Map<Int, Int>): Int? =
    picks[item.groupId] ?: item.equippedVariantId ?: item.variants.firstOrNull()?.id

private fun equip(item: CosmeticUiItem, variantId: Int, onComplete: (String) -> Unit) {
    if (!item.owned) {
        onComplete("${item.name} is not in your locker.")
        return
    }

    PolyPlusClient.SCOPE.launch {
        val result = if (item.type == CosmeticType.Emote) {
            CosmeticService.equipEmote(variantId)
        } else {
            CosmeticService.equip(variantId)
        }
        ClientPlatform.runOnMain {
            onComplete(
                result.fold(
                    onSuccess = { "Equipped ${item.name}." },
                    onFailure = { "Failed to equip ${item.name}: ${it.message}" },
                ),
            )
        }
    }
}

private fun unequip(item: CosmeticUiItem, onComplete: (String) -> Unit) {
    PolyPlusClient.SCOPE.launch {
        val result = if (item.type == CosmeticType.Emote) {
            CosmeticService.clearEmote()
        } else {
            val slot = CosmeticCatalog.localEquipped().equipped.entries
                .firstOrNull { it.value == item.equippedVariantId }?.key
            if (slot == null) {
                Result.failure(IllegalStateException("${item.name} is not equipped"))
            } else {
                CosmeticService.clearSlot(slot)
            }
        }
        ClientPlatform.runOnMain {
            onComplete(
                result.fold(
                    onSuccess = { "Unequipped ${item.name}." },
                    onFailure = { "Failed to unequip ${item.name}: ${it.message}" },
                ),
            )
        }
    }
}

private fun cardBrush(): Brush =
    Brush.verticalGradient(
        listOf(
            Color(0x59232D32),
            Color(0xB3232D32),
        ),
    )

private fun money(amount: Float): String = "$" + String.format("%.2f", amount)
