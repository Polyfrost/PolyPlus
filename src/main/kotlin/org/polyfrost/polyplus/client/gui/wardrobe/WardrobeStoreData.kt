package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.cosmetics.BundleCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticStore
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.cosmetics.outfits.LibraryState
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

internal class WardrobeStoreData {
    var byType by mutableStateOf<Map<CosmeticType, List<CosmeticStoreInfo>>>(emptyMap())
    var popular by mutableStateOf<List<CosmeticStoreInfo>>(emptyList())
    var newest by mutableStateOf<List<CosmeticStoreInfo>>(emptyList())
    var bundles by mutableStateOf<List<BundleInfo>>(emptyList())
    var loading by mutableStateOf(true)
    var featuredLoading by mutableStateOf(true)
    var failedTypes by mutableStateOf<Set<CosmeticType>>(emptySet())
    var featuredFailed by mutableStateOf(false)

    val all: List<CosmeticStoreInfo> get() = byType.values.flatten() + popular + newest

    suspend fun load() {
        loading = true
        featuredLoading = true
        val types = CosmeticType.entries.filter { it != CosmeticType.Unknown }
        coroutineScope {
            launch {
                val popularFetched = async {
                    CosmeticStore.search(perPage = FEATURED_ROW_SIZE, sort = CosmeticStore.Sort.Popularity).getOrNull()
                        ?.also { response -> popular = response.results.filter { it.type != CosmeticType.Unknown } }
                }
                val newestFetched = async {
                    CosmeticStore.search(perPage = FEATURED_ROW_SIZE, sort = CosmeticStore.Sort.Newest).getOrNull()
                        ?.also { response -> newest = response.results.filter { it.type != CosmeticType.Unknown } }
                }
                val bundlesFetched = async {
                    BundleCatalog.search(perPage = FEATURED_ROW_SIZE).getOrNull()
                        ?.also { response -> bundles = response.bundles.filter { it.purchasable } }
                }
                featuredFailed = popularFetched.await() == null && newestFetched.await() == null && bundlesFetched.await() == null
                featuredLoading = false
            }
            for (type in types) {
                launch {
                    val fetched = searchAll(type)
                    val previous = byType[type]
                    val results = when {
                        fetched == null -> previous
                        fetched.partial && previous != null -> previous
                        else -> fetched.items
                    }
                    if (results != null) byType = byType + (type to results)
                    failedTypes = if (results == null) failedTypes + type else failedTypes - type
                }
            }
        }
        featuredLoading = false
        loading = false
    }

    private class StoreResults(val items: List<CosmeticStoreInfo>, val partial: Boolean)

    private suspend fun searchAll(type: CosmeticType): StoreResults? {
        val results = LinkedHashMap<Int, CosmeticStoreInfo>()
        var page = 1
        do {
            val response = CosmeticStore.search(
                page = page,
                perPage = CosmeticStore.MAX_PAGE_SIZE,
                sort = CosmeticStore.Sort.Popularity,
                types = listOf(type.serializedName),
                reportFailures = false,
            ).getOrNull() ?: return if (page == 1) null else StoreResults(results.values.toList(), partial = true)
            response.results.forEach { results.putIfAbsent(it.id, it) }
            page++
        } while (page <= response.pagination.totalPages && page <= MAX_STORE_PAGES)
        return StoreResults(results.values.toList(), partial = false)
    }
}

private const val FEATURED_ROW_SIZE = 6

private const val MAX_STORE_PAGES = 10

private const val STORE_LOADING = "Loading the store..."

private const val STORE_FAILED = "Couldn't reach the PolyPlus store."

internal fun buildWardrobeEntries(
    section: WardrobeSection,
    items: List<CosmeticUiItem>,
    ownedIds: Set<Int>,
    store: WardrobeStoreData,
    mojangState: MojangCapeState,
    query: String,
    sort: WardrobeSort,
    polyPlusAvailable: Boolean,
    emoteKeybindWarning: String? = null,
): List<WardrobeEntry> {
    val needle = query.trim()
    fun matches(name: String) = needle.isEmpty() || name.contains(needle, ignoreCase = true)
    fun owned(type: CosmeticType) = items.filter { it.owned && it.type == type && matches(it.name) }
    fun shop(type: CosmeticType) = store.byType[type].orEmpty()
        .filter { !it.fullyOwned(ownedIds) && matches(it.name) }
        .sortedFor(sort)

    val entries = mutableListOf<WardrobeEntry>()
    when (section) {
        WardrobeSection.Featured -> {
            val popular = store.popular.filter { matches(it.name) }
            val newest = store.newest.filter { matches(it.name) }
            val bundles = store.bundles.filter { matches(it.name) }
            if (popular.isNotEmpty()) {
                entries += WardrobeEntry.Divider("Popular", anchor = true)
                popular.mapTo(entries) { WardrobeEntry.Shop(it, "popular") }
            }
            if (newest.isNotEmpty()) {
                entries += WardrobeEntry.Divider("New", anchor = true)
                newest.mapTo(entries) { WardrobeEntry.Shop(it, "new") }
            }
            if (bundles.isNotEmpty()) {
                entries += WardrobeEntry.Divider("Bundles", anchor = true)
                bundles.mapTo(entries) { WardrobeEntry.Bundle(it) }
            }
            if (entries.isEmpty()) {
                entries += WardrobeEntry.Note(
                    "featured",
                    when {
                        store.featuredLoading -> STORE_LOADING
                        store.featuredFailed -> STORE_FAILED
                        else -> "Nothing featured right now."
                    },
                )
            }
        }

        WardrobeSection.Outfits -> {
            val library = OutfitLibrary.outfits
            entries += WardrobeEntry.LibraryHeader(
                WardrobeSection.Outfits,
                "${library.size} of ${OutfitLibrary.limit} saved. Save your look and switch between outfits.",
            )
            when (val state = OutfitLibrary.state) {
                LibraryState.Idle, LibraryState.Loading ->
                    entries += WardrobeEntry.Note("outfits", "Loading your outfits...")
                is LibraryState.Unavailable ->
                    entries += WardrobeEntry.Note("outfits", state.reason)
                LibraryState.Loaded -> {
                    val activeId = OutfitLibrary.activeOutfitId
                    val outfits = library.filter { matches(it.name) }.favoritesFirst()
                    when {
                        library.isEmpty() -> entries += WardrobeEntry.Note(
                            "outfits",
                            "You have no saved outfits yet. Use + above the preview to save your current look.",
                        )
                        outfits.isEmpty() -> entries += WardrobeEntry.Note("outfits", "No outfits match your search.")
                        else -> outfits.mapTo(entries) { WardrobeEntry.Outfit(it, it.id == activeId) }
                    }
                }
            }
        }

        WardrobeSection.Skins -> {
            val library = SkinLibrary.skins
            entries += WardrobeEntry.LibraryHeader(
                WardrobeSection.Skins,
                "${library.size} of ${SkinLibrary.limit} saved. Every skin you wear is saved here automatically.",
                action = "Add skin",
            )
            when (val state = SkinLibrary.state) {
                LibraryState.Idle, LibraryState.Loading ->
                    entries += WardrobeEntry.Note("skins", "Loading your skins...")
                is LibraryState.Unavailable ->
                    entries += WardrobeEntry.Note("skins", state.reason)
                LibraryState.Loaded -> {
                    val wornId = SkinLibrary.wornSkinId()
                    val skins = library.filter { matches(it.name) }.favoriteSkinsFirst()
                    when {
                        library.isEmpty() -> entries += WardrobeEntry.Note(
                            "skins",
                            "You have no saved skins yet. Use Add skin to save one.",
                        )
                        skins.isEmpty() -> entries += WardrobeEntry.Note("skins", "No skins match your search.")
                        else -> skins.mapTo(entries) { WardrobeEntry.Skin(it, it.id == wornId) }
                    }
                }
            }
        }

        WardrobeSection.Emotes -> {
            if (emoteKeybindWarning != null) {
                entries += WardrobeEntry.Banner("emote-keybind", emoteKeybindWarning, "View keybinds")
            }
            val ownedEmotes = owned(CosmeticType.Emote)
            if (ownedEmotes.isNotEmpty()) {
                entries += WardrobeEntry.Divider("Your emotes", anchor = true)
                ownedEmotes.mapTo(entries) { WardrobeEntry.Owned(it) }
            }
            shop(CosmeticType.Emote)
                .groupBy { info -> info.tags.category.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "More emotes" }
                .forEach { (category, infos) ->
                    entries += WardrobeEntry.Divider(category, anchor = true)
                    infos.mapTo(entries) { WardrobeEntry.Shop(it, "emote") }
                }
            if (entries.all { it is WardrobeEntry.Banner }) {
                entries += WardrobeEntry.Note(
                    "emotes",
                    when {
                        CosmeticType.Emote in store.failedTypes -> STORE_FAILED
                        store.loading -> "Loading emotes..."
                        else -> "No emotes found."
                    },
                )
            }
        }

        WardrobeSection.Cosmetics -> for (type in WARDROBE_COSMETIC_TYPES) {
            val ownedItems = owned(type)
            val shopItems = shop(type)
            if (type == CosmeticType.Cape) {
                entries += WardrobeEntry.Divider(type.displayName, anchor = true)
                entries += WardrobeEntry.Group(
                    "minecraft",
                    "Minecraft capes",
                    "Everyone sees this cape, including players without PolyPlus.",
                )
                when (mojangState) {
                    MojangCapeState.Idle, MojangCapeState.Loading ->
                        entries += WardrobeEntry.Note("minecraft", "Loading your Minecraft capes...")
                    is MojangCapeState.Unavailable ->
                        entries += WardrobeEntry.Note("minecraft", mojangState.reason)
                    is MojangCapeState.Loaded -> {
                        val capes = mojangState.capes.filter { matches(it.name) }
                        if (capes.isEmpty()) {
                            entries += WardrobeEntry.Note("minecraft", "You don't own any Minecraft capes.")
                        } else {
                            capes.mapTo(entries) { WardrobeEntry.Mojang(it) }
                        }
                    }
                }
                entries += WardrobeEntry.Group(
                    "polyplus",
                    "PolyPlus capes",
                    "Players with PolyPlus see this cape instead of your Minecraft cape.",
                    forceToggle = polyPlusAvailable,
                )
                if (!polyPlusAvailable) {
                    entries += WardrobeEntry.Note(
                        "polyplus",
                        "PolyPlus capes need the Terms of Service and Privacy Policy. Accept them under Privacy in the PolyPlus settings.",
                    )
                } else if (ownedItems.isEmpty() && shopItems.isEmpty()) {
                    entries += WardrobeEntry.Note(
                        "polyplus",
                        when {
                            CosmeticType.Cape in store.failedTypes -> STORE_FAILED
                            store.loading -> STORE_LOADING
                            else -> "No PolyPlus capes found."
                        },
                    )
                }
            } else if (ownedItems.isEmpty() && shopItems.isEmpty()) {
                continue
            } else {
                entries += WardrobeEntry.Divider(type.displayName, anchor = true)
            }
            ownedItems.mapTo(entries) { WardrobeEntry.Owned(it) }
            shopItems.mapTo(entries) { WardrobeEntry.Shop(it, type.serializedName) }
        }
    }
    return entries
}
