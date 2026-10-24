package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.BillingService
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticLoadProgress
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.CosmeticStore
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.gui.wardrobe.CartEntry
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticsLoadingScreen
import org.polyfrost.polyplus.client.gui.wardrobe.MinecraftCapesOnlyScreen
import org.polyfrost.polyplus.client.gui.wardrobe.WardrobeShell
import org.polyfrost.polyplus.client.gui.wardrobe.cartName
import org.polyfrost.polyplus.client.gui.wardrobe.checkoutFailureMessage
import org.polyfrost.polyplus.client.gui.wardrobe.cosmeticCartKey
import org.polyfrost.polyplus.client.gui.wardrobe.equip
import org.polyfrost.polyplus.client.gui.wardrobe.rememberCosmeticItems
import org.polyfrost.polyplus.client.gui.wardrobe.selectedVariantId
import org.polyfrost.polyplus.client.gui.wardrobe.toCartEntry
import org.polyfrost.polyplus.client.gui.wardrobe.unequip
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent

@Composable
internal fun PolyPlusCosmeticsScreen() {
    if (!PrivacyConsent.allowsOnlineServices()) {
        MinecraftCapesOnlyScreen()
        return
    }
    var loadState by remember { mutableStateOf(CosmeticLoadProgress.snapshot()) }
    LaunchedEffect(Unit) {
        PolyPlusClient.refreshCosmeticsIfNeeded()
        while (true) {
            loadState = CosmeticLoadProgress.snapshot()
            if (loadState.ready) break
            delay(100L)
        }
    }
    if (!loadState.ready) {
        CosmeticsLoadingScreen(
            state = loadState,
            onRetry = {
                PolyPlusClient.refreshCosmetics()
                loadState = CosmeticLoadProgress.snapshot()
            },
        )
        return
    }
    var refreshKey by remember { mutableIntStateOf(0) }
    var manualRefreshKey by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val cart = remember { mutableStateListOf<CartEntry>() }
    val allItems = rememberCosmeticItems(refreshKey)
    val ownedIds = remember(refreshKey) { CosmeticCatalog.ownedIds() }
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

    var checkoutPending by remember { mutableStateOf(false) }

    fun checkout(productIds: List<String>, claimed: Boolean = false) {
        if (checkoutPending && !claimed) return
        if (productIds.isEmpty()) {
            checkoutPending = false
            status = "Nothing purchasable in your cart yet."
            return
        }
        checkoutPending = true
        status = "Opening checkout..."
        PolyPlusClient.SCOPE.launch {
            val result = BillingService.checkoutAndOpen(productIds)
            ClientPlatform.runOnMain {
                checkoutPending = false
                status = result.fold(
                    onSuccess = { "Checkout opened in your browser." },
                    onFailure = ::checkoutFailureMessage,
                )
            }
        }
    }

    WardrobeShell(
        items = allItems,
        ownedIds = ownedIds,
        cart = cart,
        status = status,
        variantPicks = variantPicks,
        auraColor = auraColor,
        historyReloadKey = manualRefreshKey,
        onStatus = { status = it },
        onRefresh = {
            PolyPlusClient.refreshCosmetics()
            PolyPlusClient.SCOPE.launch { MojangCapeService.refresh() }
            refreshKey++
            manualRefreshKey++
            status = "Refreshing cosmetic data..."
        },
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
        onSelectVariant = { groupId, variantId ->
            variantPicks[groupId] = variantId
            val item = allItems.firstOrNull { it.groupId == groupId && it.variants.any { variant -> variant.id == variantId } }
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
                        val priceId = view?.storeProductId
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
            } else if (!checkoutPending) {
                checkoutPending = true
                status = "Opening checkout..."
                PolyPlusClient.SCOPE.launch {
                    val priceId = CosmeticStore.view(variant.id).getOrNull()?.storeProductId
                    if (priceId.isNullOrBlank()) {
                        ClientPlatform.runOnMain {
                            checkoutPending = false
                            status = "$label isn't purchasable yet."
                        }
                    } else {
                        ClientPlatform.runOnMain { checkout(listOf(priceId), claimed = true) }
                    }
                }
            }
        },
        onBuyBundle = { bundle ->
            val priceId = bundle.storeProductId
            if (priceId.isNullOrBlank()) status = "${bundle.name} isn't purchasable yet." else checkout(listOf(priceId))
        },
        onRemoveFromCart = { key -> cart.removeAll { it.key == key } },
        onCheckout = { checkout(cart.mapNotNull { it.storeProductId }) },
    )
}
