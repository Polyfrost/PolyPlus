package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.polyfrost.polyplus.client.cosmetics.BundleCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticStore
import org.polyfrost.polyplus.client.cosmetics.MojangCapeState
import org.polyfrost.polyplus.client.network.http.responses.BundleInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticStoreInfo
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

internal class WardrobeStoreData {
    var byType by mutableStateOf<Map<CosmeticType, List<CosmeticStoreInfo>>>(emptyMap())
    var popular by mutableStateOf<List<CosmeticStoreInfo>>(emptyList())
    var newest by mutableStateOf<List<CosmeticStoreInfo>>(emptyList())
    var bundles by mutableStateOf<List<BundleInfo>>(emptyList())
    var loading by mutableStateOf(true)
    var failed by mutableStateOf(false)

    val all: List<CosmeticStoreInfo> get() = byType.values.flatten() + popular + newest

    suspend fun load() {
        loading = true
        val types = CosmeticType.entries.filter { it != CosmeticType.Unknown }
        coroutineScope {
            val perType = types.map { type ->
                async {
                    type to CosmeticStore.search(
                        perPage = CosmeticStore.MAX_PAGE_SIZE,
                        sort = CosmeticStore.Sort.Popularity,
                        types = listOf(type.serializedName),
                        reportFailures = false,
                    ).getOrNull()
                }
            }
            val popularResult = async { CosmeticStore.search(perPage = FEATURED_ROW_SIZE, sort = CosmeticStore.Sort.Popularity) }
            val newestResult = async { CosmeticStore.search(perPage = FEATURED_ROW_SIZE, sort = CosmeticStore.Sort.Newest) }
            val bundleResult = async { BundleCatalog.search(perPage = FEATURED_ROW_SIZE) }

            val fetched = perType.awaitAll()
            byType = fetched.mapNotNull { (type, response) -> response?.let { type to it.results } }.toMap()
            popular = popularResult.await().getOrNull()?.results.orEmpty()
            newest = newestResult.await().getOrNull()?.results.orEmpty()
            bundles = bundleResult.await().getOrNull()?.bundles.orEmpty().filter { it.purchasable }
            failed = fetched.all { it.second == null }
        }
        loading = false
    }
}

private const val FEATURED_ROW_SIZE = 6

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
        .filter { !it.anyOwned(ownedIds) && matches(it.name) }
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
                        store.loading -> "Loading the store..."
                        store.failed -> "Couldn't reach the PolyPlus store."
                        else -> "Nothing featured right now."
                    },
                )
            }
        }

        WardrobeSection.Outfits ->
            entries += WardrobeEntry.Note("outfits", "Saved outfits aren't available yet.")

        WardrobeSection.Skins ->
            entries += WardrobeEntry.Note("skins", "The skin library isn't available yet.")

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
            if (entries.isEmpty()) {
                entries += WardrobeEntry.Note("emotes", if (store.loading) "Loading emotes..." else "No emotes found.")
            }
        }

        WardrobeSection.Cosmetics -> for (type in WARDROBE_COSMETIC_TYPES) {
            val ownedItems = owned(type)
            val shopItems = shop(type)
            if (type == CosmeticType.Cape) {
                entries += WardrobeEntry.Divider(type.displayName, anchor = true)
                entries += WardrobeEntry.ForceCapes
                entries += WardrobeEntry.Group(
                    "minecraft",
                    "Minecraft",
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
                    "PolyPlus",
                    "Players with PolyPlus see this cape instead of your Minecraft cape.",
                )
                if (!polyPlusAvailable) {
                    entries += WardrobeEntry.Note(
                        "polyplus",
                        "PolyPlus capes need the Terms of Service and Privacy Policy. Accept them under Privacy in the PolyPlus settings.",
                    )
                } else if (ownedItems.isEmpty() && shopItems.isEmpty()) {
                    entries += WardrobeEntry.Note("polyplus", "No PolyPlus capes found.")
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
