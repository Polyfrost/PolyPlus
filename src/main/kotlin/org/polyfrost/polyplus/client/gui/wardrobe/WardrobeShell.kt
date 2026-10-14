package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.internal.ui.shell.ShellState
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import org.polyfrost.polyplus.client.gui.preview.PreviewRotation
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

@Composable
internal fun WardrobeShell(
    items: List<CosmeticUiItem>,
    ownedIds: Set<Int>,
    cart: List<CartEntry>,
    status: String?,
    variantPicks: Map<Int, Int>,
    auraColor: Int?,
    historyReloadKey: Int,
    onStatus: (String) -> Unit,
    onRefresh: () -> Unit,
    onPreviewAuraColor: (Int) -> Unit,
    onSelectAuraColor: (Int?) -> Unit,
    onSelectVariant: (Int, Int) -> Unit,
    onEquip: (CosmeticUiItem) -> Unit,
    onAddToCart: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onBuyNow: (CosmeticStoreInfo, CosmeticVariantUi) -> Unit,
    onBuyBundle: (BundleInfo) -> Unit,
    onRemoveFromCart: (String) -> Unit,
    onCheckout: () -> Unit,
    polyPlusAvailable: Boolean = true,
) {
    val store = remember { WardrobeStoreData() }
    var storeReloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(storeReloadKey) { if (polyPlusAvailable) store.load() else store.loading = false }
    LaunchedEffect(Unit) { MojangCapeService.refreshIfNeeded() }

    val sections = if (polyPlusAvailable) WardrobeSection.entries else listOf(WardrobeSection.Cosmetics)
    var section by remember { mutableStateOf(sections.first()) }
    val query = ShellState.searchQuery
    var sort by remember { mutableStateOf(WardrobeSort.Popular) }
    var selection by remember { mutableStateOf<WardrobeSelection?>(null) }
    var showCart by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var wheelMode by remember { mutableStateOf(false) }
    var wheelSlot by remember { mutableStateOf<Int?>(null) }
    var returnPoint by remember { mutableStateOf<WardrobeReturnPoint?>(null) }
    var pendingScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val storeVariantPicks = remember { mutableStateMapOf<Int, Int>() }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val mojangState = MojangCapeService.state

    val emoteKeybindWarning = remember(section) {
        when {
            section != WardrobeSection.Emotes || !polyPlusAvailable -> null
            !EmoteWheelKeybind.isBound() -> "The emote wheel has no keybind yet, so it can't be opened in game."
            else -> EmoteWheelKeybind.conflictingKeybindNames().takeIf { it.isNotEmpty() }
                ?.let { "The emote wheel keybind is also used by ${it.joinToString()}." }
        }
    }
    val entries = remember(section, items, ownedIds, store.byType, store.popular, store.newest, store.bundles, store.loading, mojangState, query, sort, emoteKeybindWarning) {
        buildWardrobeEntries(section, items, ownedIds, store, mojangState, query, sort, polyPlusAvailable, emoteKeybindWarning)
    }
    val anchors = remember(entries) {
        entries.mapIndexedNotNull { index, entry ->
            (entry as? WardrobeEntry.Divider)?.takeIf { it.anchor }?.let { it.label to index }
        }
    }
    LaunchedEffect(section) {
        val target = pendingScroll
        pendingScroll = null
        if (target != null) gridState.scrollToItem(target.first, target.second) else gridState.scrollToItem(0)
    }

    val selectedOwned = (selection as? WardrobeSelection.Owned)?.let { sel -> items.firstOrNull { it.groupId == sel.groupId && it.type == sel.type } }
    val selectedShop = (selection as? WardrobeSelection.Shop)?.let { sel -> store.all.firstOrNull { it.id == sel.infoId } }
    val selectedMojang = (selection as? WardrobeSelection.Mojang)?.let { sel ->
        (mojangState as? MojangCapeState.Loaded)?.capes?.firstOrNull { it.id == sel.capeId }
    }
    val selectedBundle = (selection as? WardrobeSelection.Bundle)?.let { sel -> store.bundles.firstOrNull { it.id == sel.bundleId } }
    val previewRotation = remember { PreviewRotation(FRONT_YAW_DEG) }
    val showBack = selectedMojang != null ||
        selectedOwned?.type?.let { it in BACK_VIEW_TYPES } == true ||
        selectedShop?.type?.let { it in BACK_VIEW_TYPES } == true ||
        (selection == null && section == WardrobeSection.Cosmetics)
    LaunchedEffect(showBack) {
        if (previewRotation.userRotated) return@LaunchedEffect
        val target = if (showBack) BACK_YAW_DEG else FRONT_YAW_DEG
        val delta = ((target - previewRotation.yaw) % 360f + 540f) % 360f - 180f
        animate(previewRotation.yaw, previewRotation.yaw + delta, animationSpec = tween(300)) { value, _ ->
            if (!previewRotation.userRotated) previewRotation.yaw = value
        }
    }

    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        WardrobeRail(
            sections = sections,
            selected = section,
            anchors = anchors,
            modifier = Modifier.width(150.dp).fillMaxHeight(),
            onSelect = {
                section = it
                showHistory = false
            },
            onAnchor = { index -> scope.launch { gridState.scrollToItem(index) } },
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WardrobeHeader(
                title = if (showHistory) "History" else section.label,
                sort = sort,
                cartSize = cart.size,
                showingHistory = showHistory,
                storeEnabled = polyPlusAvailable,
                onCycleSort = { sort = WardrobeSort.entries[(sort.ordinal + 1) % WardrobeSort.entries.size] },
                onToggleHistory = { showHistory = !showHistory },
                onRefresh = {
                    onRefresh()
                    storeReloadKey++
                },
                onCart = { showCart = !showCart },
            )
            if (showHistory) {
                HistoryScreen(ownedIds = ownedIds, reloadKey = historyReloadKey)
            } else {
                WardrobeGrid(
                    entries = entries,
                    state = gridState,
                    ownedIds = ownedIds,
                    cartKeys = cart.map { it.key }.toSet(),
                    selection = selection,
                    variantPicks = variantPicks,
                    storeVariantPicks = storeVariantPicks,
                    mojangBusy = MojangCapeService.busy,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    coverFor = { item ->
                        store.all.firstOrNull { info -> info.variantList.any { v -> item.variants.any { it.id == v.id } } }?.coverAssetId
                    },
                    onOwned = { item ->
                        if (wheelMode && item.type == CosmeticType.Emote) {
                            val wheel = EmoteWheels.activeIndex
                            val target = wheelSlot
                            val placed = if (target != null) {
                                EmoteWheels.assign(wheel, target, item.groupId)
                                target
                            } else {
                                EmoteWheels.addToFirstEmpty(wheel, item.groupId)
                            }
                            wheelSlot = null
                            onStatus(
                                if (placed == null) {
                                    "Wheel ${wheel + 1} is full. Pick a slot to replace."
                                } else {
                                    "Added ${item.name} to wheel ${wheel + 1}."
                                },
                            )
                        } else {
                            selection = WardrobeSelection.Owned(item.groupId, item.type)
                            showCart = false
                            onEquip(item)
                        }
                    },
                    onPreviewOwned = { item ->
                        selection = WardrobeSelection.Owned(item.groupId, item.type)
                        showCart = false
                    },
                    onSelectVariant = onSelectVariant,
                    onShop = { info ->
                        val ownedItem = items.firstOrNull { item -> item.owned && info.variantList.any { v -> item.variants.any { it.id == v.id } } }
                        selection = if (ownedItem != null) WardrobeSelection.Owned(ownedItem.groupId, ownedItem.type) else WardrobeSelection.Shop(info.id)
                        showCart = false
                    },
                    onPickStoreVariant = { infoId, variantId -> storeVariantPicks[infoId] = variantId },
                    onAddToCart = onAddToCart,
                    onBuyNow = onBuyNow,
                    onMojang = { cape ->
                        selection = WardrobeSelection.Mojang(cape.id)
                        showCart = false
                        toggleMojangCape(cape, onStatus)
                    },
                    onBundle = { bundle ->
                        selection = WardrobeSelection.Bundle(bundle.id)
                        showCart = false
                    },
                    onBuyBundle = onBuyBundle,
                    onOpenStore = { section = WardrobeSection.Featured },
                )
            }
        }

        Column(modifier = Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (polyPlusAvailable) {
                PreviewModeToggle(
                    wheelMode = wheelMode,
                    onChange = { wheel ->
                        if (wheel == wheelMode) return@PreviewModeToggle
                        wheelMode = wheel
                        wheelSlot = null
                        if (wheel) {
                            returnPoint = WardrobeReturnPoint(
                                section = section,
                                showHistory = showHistory,
                                scrollIndex = gridState.firstVisibleItemIndex,
                                scrollOffset = gridState.firstVisibleItemScrollOffset,
                            )
                            section = WardrobeSection.Emotes
                            showHistory = false
                            showCart = false
                        } else {
                            val back = returnPoint
                            returnPoint = null
                            if (back != null && section == WardrobeSection.Emotes) {
                                showHistory = back.showHistory
                                if (back.section == section) {
                                    scope.launch { gridState.scrollToItem(back.scrollIndex, back.scrollOffset) }
                                } else {
                                    pendingScroll = back.scrollIndex to back.scrollOffset
                                    section = back.section
                                }
                            }
                        }
                    },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    showCart -> WardrobeCartPane(
                        cart = cart,
                        status = status,
                        onClose = { showCart = false },
                        onRemove = onRemoveFromCart,
                        onCheckout = onCheckout,
                    )
                    wheelMode -> EmoteWheelEditor(
                        selectedSlot = wheelSlot,
                        coverForEmote = { id -> store.all.firstOrNull { info -> info.variantList.any { it.id == id } }?.coverAssetId },
                        onSelectSlot = { wheelSlot = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    selectedShop != null -> {
                        val variant = selectedStoreVariant(selectedShop, storeVariantPicks, ownedIds)
                        StoreDetailPanel(
                            info = selectedShop,
                            variant = variant,
                            status = status,
                            ownedIds = ownedIds,
                            inCart = cosmeticCartKey(variant.id) in cart.map { it.key },
                            onSelectVariant = { storeVariantPicks[selectedShop.id] = it },
                            onAddToCart = onAddToCart,
                            onBuyNow = onBuyNow,
                            modifier = Modifier.fillMaxSize(),
                            rotation = previewRotation,
                        )
                    }
                    selectedBundle != null -> BundleDetailPanel(
                        bundle = selectedBundle,
                        status = status,
                        onBuy = { onBuyBundle(selectedBundle) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> {
                        PreviewPanel(
                            selected = selectedOwned?.takeIf { selectedMojang == null },
                            heading = selectedMojang?.let { it.name to MOJANG_CAPE_COLLECTION },
                            status = status,
                            selectedVariantId = selectedOwned?.let { selectedVariantId(it, variantPicks) },
                            onSelectVariant = { variantId -> selectedOwned?.let { onSelectVariant(it.groupId, variantId) } },
                            rotation = previewRotation,
                            showAuraColor = selectedOwned?.type == CosmeticType.Aura,
                            auraColor = auraColor,
                            onPreviewAuraColor = onPreviewAuraColor,
                            onSelectAuraColor = onSelectAuraColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MinecraftCapesOnlyScreen() {
    var status by remember { mutableStateOf<String?>(null) }
    WardrobeShell(
        items = emptyList(),
        ownedIds = emptySet(),
        cart = emptyList(),
        status = status,
        variantPicks = emptyMap(),
        auraColor = null,
        historyReloadKey = 0,
        onStatus = { status = it },
        onRefresh = { PolyPlusClient.SCOPE.launch { MojangCapeService.refresh() } },
        onPreviewAuraColor = {},
        onSelectAuraColor = {},
        onSelectVariant = { _, _ -> },
        onEquip = {},
        onAddToCart = { _, _ -> },
        onBuyNow = { _, _ -> },
        onBuyBundle = {},
        onRemoveFromCart = {},
        onCheckout = {},
        polyPlusAvailable = false,
    )
}

private data class WardrobeReturnPoint(
    val section: WardrobeSection,
    val showHistory: Boolean,
    val scrollIndex: Int,
    val scrollOffset: Int,
)
