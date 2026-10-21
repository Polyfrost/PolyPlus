@file:OptIn(ExperimentalOneConfigUiApi::class)

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.page.currentPageSearchQuery
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
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
    LaunchedEffect(storeReloadKey) {
        if (polyPlusAvailable) {
            store.load()
        } else {
            store.loading = false
            store.featuredLoading = false
        }
    }
    LaunchedEffect(Unit) { MojangCapeService.refreshIfNeeded() }

    val sections = if (polyPlusAvailable) WardrobeSection.entries else listOf(WardrobeSection.Cosmetics)
    var section by remember { mutableStateOf(sections.first()) }
    val query = currentPageSearchQuery
    var sort by remember { mutableStateOf(WardrobeSort.Popular) }
    var selection by remember { mutableStateOf<WardrobeSelection?>(null) }
    var showCart by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var addingSkin by remember { mutableStateOf(false) }
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
    LaunchedEffect(section) {
        if (!polyPlusAvailable) return@LaunchedEffect
        when (section) {
            WardrobeSection.Outfits -> OutfitLibrary.refreshIfNeeded(reload = true)
            WardrobeSection.Skins -> {
                SkinLibrary.refreshIfNeeded()
                OutfitLibrary.refreshIfNeeded()
            }
            else -> Unit
        }
    }
    val outfitKey = listOf(OutfitLibrary.outfits, OutfitLibrary.activeOutfitId, OutfitLibrary.state, OutfitLibrary.limit)
    val skinKey = listOf(SkinLibrary.skins, SkinLibrary.state, SkinLibrary.wornSkinId(), SkinLibrary.limit)
    val entries = remember(section, items, ownedIds, store.byType, store.popular, store.newest, store.bundles, store.loading, store.featuredLoading, store.failedTypes, store.featuredFailed, mojangState, query, sort, emoteKeybindWarning, outfitKey, skinKey) {
        buildWardrobeEntries(section, items, ownedIds, store, mojangState, query, sort, polyPlusAvailable, emoteKeybindWarning)
    }
    val coverByVariant = remember(store.byType, store.popular, store.newest) {
        val covers = HashMap<Int, Int>()
        for (info in store.all) {
            val cover = info.coverAssetId ?: continue
            for (variant in info.variantList) covers.putIfAbsent(variant.id, cover)
        }
        covers
    }
    val anchors = remember(entries) {
        entries.mapIndexedNotNull { index, entry ->
            (entry as? WardrobeEntry.Divider)?.takeIf { it.anchor }?.let { it.label to index }
        }
    }
    val activeAnchor by remember(anchors) {
        derivedStateOf { anchors.lastOrNull { it.second <= gridState.firstVisibleItemIndex }?.second ?: anchors.firstOrNull()?.second }
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
    val selectedOutfit = (selection as? WardrobeSelection.Outfit)?.let { sel -> OutfitLibrary.outfits.firstOrNull { it.id == sel.outfitId } }
    val selectedSkin = (selection as? WardrobeSelection.Skin)?.let { sel -> SkinLibrary.skins.firstOrNull { it.id == sel.skinId } }
    val previewHeading = selectedMojang?.let { it.name to MOJANG_CAPE_COLLECTION }
        ?: selectedOutfit?.let { it.name to "Outfit" }
        ?: selectedSkin?.let { it.name to "${it.model.label} skin" }
    val itemPreview = rememberSelectionPreview(
        owned = selectedOwned,
        variantId = selectedOwned?.let { selectedVariantId(it, variantPicks) },
        mojang = selectedMojang,
    )
    val outfitPreview = rememberOutfitPreview(selectedOutfit)
    val skinPreview = rememberSkinPreview(selectedSkin)
    val selectionPreview = outfitPreview ?: skinPreview ?: itemPreview
    val previewRotation = remember { PreviewRotation(FRONT_YAW_DEG) }
    val selectionShowsBack = if (selection == null) {
        null
    } else {
        selectedMojang != null ||
            selectedOwned?.type?.let { it in BACK_VIEW_TYPES } == true ||
            selectedShop?.type?.let { it in BACK_VIEW_TYPES } == true
    }
    val activeAnchorLabel by remember(anchors) {
        derivedStateOf { anchors.firstOrNull { it.second == activeAnchor }?.first }
    }
    val scrollShowsBack = section == WardrobeSection.Cosmetics && activeAnchorLabel == CosmeticType.Cape.displayName
    var showBack by remember { mutableStateOf(false) }
    LaunchedEffect(scrollShowsBack) { showBack = scrollShowsBack }
    LaunchedEffect(selection) { selectionShowsBack?.let { showBack = it } }
    var rotationResets by remember { mutableIntStateOf(0) }
    LaunchedEffect(showBack, rotationResets) {
        if (previewRotation.userRotated) return@LaunchedEffect
        val target = if (showBack) BACK_YAW_DEG else FRONT_YAW_DEG
        val startYaw = previewRotation.yaw
        val startPitch = previewRotation.pitch
        val delta = ((target - startYaw) % 360f + 540f) % 360f - 180f
        animate(0f, 1f, animationSpec = tween(300)) { progress, _ ->
            if (!previewRotation.userRotated) {
                previewRotation.yaw = startYaw + delta * progress
                previewRotation.pitch = startPitch * (1f - progress)
            }
        }
    }
    val resetRotation: () -> Unit = {
        previewRotation.userRotated = false
        rotationResets++
    }
    val addToWheel = { item: CosmeticUiItem ->
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
    }

    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 25.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WardrobeHeader(
                sections = sections,
                selected = section,
                anchors = anchors,
                activeAnchor = activeAnchor,
                onSelectSection = {
                    section = it
                    showHistory = false
                    addingSkin = false
                },
                onAnchor = { index -> scope.launch { gridState.scrollToItem(index) } },
                sort = sort,
                cartSize = cart.size,
                showingHistory = showHistory,
                storeEnabled = polyPlusAvailable,
                onCycleSort = { sort = WardrobeSort.entries[(sort.ordinal + 1) % WardrobeSort.entries.size] },
                onToggleHistory = { showHistory = !showHistory },
                onRefresh = {
                    onRefresh()
                    storeReloadKey++
                    if (polyPlusAvailable && section == WardrobeSection.Outfits) {
                        scope.launch { OutfitLibrary.refresh() }
                    }
                    if (polyPlusAvailable && section == WardrobeSection.Skins) {
                        scope.launch {
                            SkinLibrary.refresh()
                            OutfitLibrary.refresh()
                        }
                    }
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
                    coverFor = { item -> item.variants.firstNotNullOfOrNull { coverByVariant[it.id] } },
                    onOwned = { item ->
                        if (wheelMode && item.type == CosmeticType.Emote) {
                            addToWheel(item)
                        } else {
                            selection = WardrobeSelection.Owned(item.groupId, item.type)
                            showCart = false
                            onEquip(item)
                        }
                    },
                    onClickOwned = { item ->
                        if (wheelMode && item.type == CosmeticType.Emote) {
                            addToWheel(item)
                        } else {
                            selection = WardrobeSelection.Owned(item.groupId, item.type)
                            showCart = false
                        }
                    },
                    onPreviewOwned = { item ->
                        selection = WardrobeSelection.Owned(item.groupId, item.type)
                        showCart = false
                    },
                    onSelectVariant = onSelectVariant,
                    onShop = { info ->
                        val ownedItem = items.firstOrNull { item -> item.owned && info.variantList.any { v -> item.variants.any { it.id == v.id } } }
                            ?.takeIf { info.fullyOwned(ownedIds) }
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
                    onPreviewMojang = { cape ->
                        selection = WardrobeSelection.Mojang(cape.id)
                        showCart = false
                    },
                    onBundle = { bundle ->
                        selection = WardrobeSelection.Bundle(bundle.id)
                        showCart = false
                    },
                    onBuyBundle = onBuyBundle,
                    onLibraryAction = { target ->
                        if (target == WardrobeSection.Skins) {
                            addingSkin = true
                            showCart = false
                        }
                    },
                    onSelectOutfit = { outfit ->
                        selection = WardrobeSelection.Outfit(outfit.id)
                        showCart = false
                    },
                    onSelectSkin = { skin ->
                        selection = WardrobeSelection.Skin(skin.id)
                        showCart = false
                    },
                    onStatus = onStatus,
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
                            addingSkin = false
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
                if (!wheelMode) {
                    OutfitSwitcher(onStatus = onStatus)
                }
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
                    addingSkin && polyPlusAvailable -> AddSkinPanel(
                        onClose = { addingSkin = false },
                        onStatus = onStatus,
                        modifier = Modifier.fillMaxSize(),
                    )
                    wheelMode -> EmoteWheelEditor(
                        selectedSlot = wheelSlot,
                        coverForEmote = { id -> coverByVariant[id] },
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
                            onResetRotation = resetRotation,
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
                            selected = selectedOwned?.takeIf { previewHeading == null },
                            heading = previewHeading,
                            status = status,
                            selectedVariantId = selectedOwned?.let { selectedVariantId(it, variantPicks) },
                            onSelectVariant = { variantId -> selectedOwned?.let { onSelectVariant(it.groupId, variantId) } },
                            rotation = previewRotation,
                            onResetRotation = resetRotation,
                            selectionPreview = selectionPreview,
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
