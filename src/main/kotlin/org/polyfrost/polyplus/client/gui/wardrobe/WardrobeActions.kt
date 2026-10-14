package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal fun toggleMojangCape(cape: MojangCape, onStatus: (String) -> Unit) {
    if (MojangCapeService.busy) return
    onStatus(if (cape.active) "Removing ${cape.name}..." else "Equipping ${cape.name}...")
    PolyPlusClient.SCOPE.launch {
        val result = if (cape.active) MojangCapeService.clear() else MojangCapeService.select(cape.id)
        ClientPlatform.runOnMain {
            onStatus(
                result.fold(
                    onSuccess = { if (cape.active) "Removed ${cape.name}." else "Equipped ${cape.name}." },
                    onFailure = MojangCapeService::failureMessage,
                ),
            )
        }
    }
}

@Composable
internal fun rememberCosmeticItems(refreshKey: Int): List<CosmeticUiItem> =
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

internal fun equip(item: CosmeticUiItem, variantId: Int, onComplete: (String) -> Unit) {
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

internal fun unequip(item: CosmeticUiItem, onComplete: (String) -> Unit) {
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
