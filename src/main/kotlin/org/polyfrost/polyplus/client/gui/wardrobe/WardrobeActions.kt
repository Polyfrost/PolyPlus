package org.polyfrost.polyplus.client.gui.wardrobe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.cosmetics.CosmeticService
import org.polyfrost.polyplus.client.cosmetics.MojangCape
import org.polyfrost.polyplus.client.cosmetics.MojangCapeService
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.PlayerSkinInfo
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinModel
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.emotes.EmoteWheels
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.concurrent.atomic.AtomicBoolean

private val lookChangePending = AtomicBoolean(false)
private val equipLock = Mutex()

private const val LOOK_CHANGE_BUSY = "Another change is still running. Try again in a moment."

private fun beginLookChange(): Boolean =
    !OutfitLibrary.busy && !SkinLibrary.busy && !MojangCapeService.busy && lookChangePending.compareAndSet(false, true)

private fun beginLookChange(onStatus: (String) -> Unit): Boolean =
    beginLookChange().also { if (!it) onStatus(LOOK_CHANGE_BUSY) }

internal fun toggleMojangCape(cape: MojangCape, onStatus: (String) -> Unit) {
    if (!beginLookChange(onStatus)) return
    onStatus(if (cape.active) "Removing ${cape.name}..." else "Equipping ${cape.name}...")
    PolyPlusClient.SCOPE.launch {
        val result = try {
            if (cape.active) MojangCapeService.clear() else MojangCapeService.select(cape.id)
        } finally {
            lookChangePending.set(false)
        }
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

private fun <T> launchLibraryCall(
    onStatus: (String) -> Unit,
    pending: String?,
    call: suspend () -> Result<T>,
    failure: (Throwable) -> String,
    success: (T) -> String,
) {
    pending?.let(onStatus)
    PolyPlusClient.SCOPE.launch {
        val result = call()
        ClientPlatform.runOnMain { onStatus(result.fold(onSuccess = success, onFailure = failure)) }
    }
}

private suspend fun <T> releasing(flag: AtomicBoolean, call: suspend () -> Result<T>): Result<T> =
    try {
        call()
    } finally {
        flag.set(false)
    }

internal fun wearOutfit(outfit: OutfitInfo, onStatus: (String) -> Unit) {
    if (!beginLookChange(onStatus)) return
    launchLibraryCall(
        onStatus,
        "Switching to ${outfit.name}...",
        { releasing(lookChangePending) { OutfitLibrary.apply(outfit.id) } },
        OutfitLibrary::failureMessage,
    ) { "Wearing ${it.name}." }
}

internal fun createOutfitFromCurrent(onStatus: (String) -> Unit) {
    if (!beginLookChange(onStatus)) return
    launchLibraryCall(
        onStatus,
        "Saving your current look...",
        { releasing(lookChangePending) { OutfitLibrary.createFromCurrent() } },
        OutfitLibrary::failureMessage,
    ) { "Saved ${it.name}." }
}

internal fun renameOutfit(outfit: OutfitInfo, name: String, onStatus: (String) -> Unit) {
    launchLibraryCall(
        onStatus,
        null,
        { OutfitLibrary.rename(outfit.id, name) },
        OutfitLibrary::failureMessage,
    ) { "Renamed ${outfit.name} to ${it.name}." }
}

internal fun toggleOutfitFavorite(outfit: OutfitInfo, onStatus: (String) -> Unit) {
    val favorite = !outfit.favorite
    launchLibraryCall(
        onStatus,
        null,
        { OutfitLibrary.setFavorite(outfit.id, favorite) },
        OutfitLibrary::failureMessage,
    ) { if (favorite) "Added ${it.name} to favourites." else "Removed ${it.name} from favourites." }
}

internal fun deleteOutfit(outfit: OutfitInfo, onStatus: (String) -> Unit) {
    launchLibraryCall(
        onStatus,
        "Deleting ${outfit.name}...",
        { OutfitLibrary.delete(outfit.id) },
        OutfitLibrary::failureMessage,
    ) { "Deleted ${outfit.name}." }
}

internal fun beginSkinChange(): Boolean = beginLookChange()

internal fun endSkinChange() = lookChangePending.set(false)

internal fun wearSkin(skin: PlayerSkinInfo, onStatus: (String) -> Unit) {
    if (!beginLookChange(onStatus)) return
    launchLibraryCall(
        onStatus,
        "Changing your skin to ${skin.name}...",
        { releasing(lookChangePending) { SkinLibrary.wear(skin.id) } },
        SkinLibrary::failureMessage,
    ) { "Wearing ${skin.name}." }
}

internal fun renameSkin(skin: PlayerSkinInfo, name: String, onStatus: (String) -> Unit) {
    launchLibraryCall(
        onStatus,
        null,
        { SkinLibrary.rename(skin.id, name) },
        SkinLibrary::failureMessage,
    ) { "Renamed ${skin.name} to ${it.name}." }
}

internal fun toggleSkinFavorite(skin: PlayerSkinInfo, onStatus: (String) -> Unit) {
    val favorite = !skin.favorite
    launchLibraryCall(
        onStatus,
        null,
        { SkinLibrary.setFavorite(skin.id, favorite) },
        SkinLibrary::failureMessage,
    ) { if (favorite) "Added ${it.name} to favourites." else "Removed ${it.name} from favourites." }
}

internal fun switchSkinModel(skin: PlayerSkinInfo, onStatus: (String) -> Unit) {
    val model = if (skin.model == SkinModel.Slim) SkinModel.Classic else SkinModel.Slim
    if (!beginLookChange(onStatus)) return
    launchLibraryCall(
        onStatus,
        null,
        { releasing(lookChangePending) { SkinLibrary.setModel(skin.id, model) } },
        SkinLibrary::failureMessage,
    ) { "${it.name} now uses ${if (it.model == SkinModel.Slim) "slim" else "classic"} arms." }
}

internal fun deleteSkin(skin: PlayerSkinInfo, onStatus: (String) -> Unit) {
    launchLibraryCall(
        onStatus,
        "Deleting ${skin.name}...",
        { SkinLibrary.delete(skin.id) },
        SkinLibrary::failureMessage,
    ) { "Deleted ${skin.name}." }
}

internal fun selectAuraColor(color: Int?, onStatus: (String) -> Unit) {
    launchLibraryCall(
        onStatus,
        if (color != null) "Updating aura color..." else "Clearing aura color...",
        { CosmeticService.setParticleColor(color) },
        { wardrobeFailure(it, "update your aura color") },
    ) { if (color != null) "Aura color updated." else "Aura color cleared." }
}

internal fun addEmoteToWheel(item: CosmeticUiItem, targetSlot: Int?, onStatus: (String) -> Unit) {
    val wheel = EmoteWheels.activeIndex
    val placed = if (targetSlot != null) {
        EmoteWheels.assign(wheel, targetSlot, item.groupId)
        targetSlot
    } else {
        EmoteWheels.addToFirstEmpty(wheel, item.groupId)
    }
    onStatus(
        if (placed == null) {
            "Wheel ${wheel + 1} is full. Pick a slot to replace."
        } else {
            "Added ${item.name} to wheel ${wheel + 1}."
        },
    )
}

internal fun emoteKeybindWarning(): String? = when {
    !EmoteWheelKeybind.isBound() -> "The emote wheel has no keybind yet, so it can't be opened in game."
    else -> EmoteWheelKeybind.conflictingKeybindNames().takeIf { it.isNotEmpty() }
        ?.let { "The emote wheel keybind is also used by ${it.joinToString()}." }
}

@Composable
internal fun rememberCosmeticItems(refreshKey: Int): List<CosmeticUiItem> =
    remember(refreshKey) {
        val ownedIds = CosmeticCatalog.ownedIds()
        val equippedBySlot = CosmeticCatalog.localEquipped().equipped
        val equippedIds = equippedBySlot.values.toSet()
        val selectedEmote = CosmeticCatalog.selectedEmoteId()

        val groupItems = CosmeticCatalog.cosmeticGroupViews()
            .sortedWith(compareBy<CosmeticGroupView> { it.type.ordinal }.thenBy { it.groupId })
            .map { group ->
                val byLabel = LinkedHashMap<Pair<String, BodySlot?>, CosmeticVariantUi>()
                for (variant in group.variants) {
                    val side = handSide(variant)
                    byLabel.getOrPut(variant.variantName to side) {
                        CosmeticVariantUi(variant.id, variant.variantName, side = side)
                    }
                }
                val chips = withBothChips(byLabel.values.map { it.withSideLabel(byLabel.keys) })
                val equippedVariant = group.variants.firstOrNull { it.id in equippedIds }
                val wornChipIds = group.variants.filter { it.id in equippedIds }.mapNotNullTo(HashSet()) { byLabel[it.variantName to handSide(it)]?.id }
                val bothWorn = chips.firstOrNull { it.pieces.size > 1 && wornChipIds.containsAll(it.pieces) }
                CosmeticUiItem(
                    groupId = group.groupId,
                    type = group.type,
                    name = group.name,
                    collection = "${group.type.displayName} Collection",
                    owned = group.variants.any { it.id in ownedIds },
                    equipped = equippedVariant != null,
                    variants = chips,
                    equippedVariantId = bothWorn?.id ?: equippedVariant?.let { byLabel[it.variantName to handSide(it)]?.id ?: it.id },
                    equippedCosmeticId = equippedVariant?.id,
                    memberIds = group.variants.mapTo(HashSet()) { it.id },
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

private fun handSide(variant: CosmeticDefinition): BodySlot? = when {
    variant.allowsSlot(BodySlot.LeftHand) == variant.allowsSlot(BodySlot.RightHand) -> null
    variant.allowsSlot(BodySlot.LeftHand) -> BodySlot.LeftHand
    else -> BodySlot.RightHand
}

private fun CosmeticVariantUi.withSideLabel(labels: Set<Pair<String, BodySlot?>>): CosmeticVariantUi {
    val side = side ?: return this
    val other = if (side == BodySlot.LeftHand) BodySlot.RightHand else BodySlot.LeftHand
    if (name to other !in labels) return this
    return copy(name = if (side == BodySlot.LeftHand) "Left $name" else "Right $name")
}

private fun withoutSide(name: String): String =
    name.split(' ')
        .filterNot { it.equals("left", ignoreCase = true) || it.equals("right", ignoreCase = true) }
        .joinToString(" ")

private fun withBothChips(chips: List<CosmeticVariantUi>): List<CosmeticVariantUi> {
    if (chips.none { it.side != null }) return chips
    val result = ArrayList<CosmeticVariantUi>(chips.size + chips.size / 2)
    val doneFinishes = HashSet<String>()
    for (chip in chips) {
        if (chip.side == null) {
            result += chip
            continue
        }
        val finish = withoutSide(chip.name)
        if (!doneFinishes.add(finish)) continue
        val left = chips.firstOrNull { it.side == BodySlot.LeftHand && withoutSide(it.name) == finish }
        val right = chips.firstOrNull { it.side == BodySlot.RightHand && withoutSide(it.name) == finish }
        listOfNotNull(left, right).forEach { result += it }
        if (left != null && right != null) {
            result += CosmeticVariantUi(
                id = -left.id,
                name = if (finish.isEmpty()) "Both" else "Both $finish",
                pieces = listOf(left.id, right.id),
            )
        }
    }
    return result
}

private fun wornGroupSlots(item: CosmeticUiItem): List<BodySlot> {
    val groupIds = item.memberIds + listOfNotNull(item.equippedCosmeticId)
    return CosmeticCatalog.localEquipped().equipped.entries.filter { it.value in groupIds }.map { it.key }
}

internal fun equip(item: CosmeticUiItem, variantId: Int, onComplete: (String) -> Unit) {
    if (!item.owned) {
        onComplete("${item.name} is not in your locker.")
        return
    }

    PolyPlusClient.SCOPE.launch {
        val result = equipLock.withLock {
            val variant = item.variant(variantId)
            when {
                item.type == CosmeticType.Emote -> CosmeticService.equipEmote(variantId)
                variant != null && variant.sided -> CosmeticService.equipPieces(variant.pieces, wornGroupSlots(item))
                else -> CosmeticService.equip(variantId)
            }
        }
        ClientPlatform.runOnMain {
            onComplete(
                result.fold(
                    onSuccess = { "Equipped ${item.name}." },
                    onFailure = { wardrobeFailure(it, "equip ${item.name}") },
                ),
            )
        }
    }
}

internal fun unequip(item: CosmeticUiItem, variantId: Int? = null, onComplete: (String) -> Unit) {
    PolyPlusClient.SCOPE.launch {
        val result = equipLock.withLock {
            val side = item.variant(variantId)?.side
            if (item.type == CosmeticType.Emote) {
                CosmeticService.clearEmote()
            } else if (side != null && side in wornGroupSlots(item)) {
                CosmeticService.clearSlot(side)
            } else {
                val groupIds = CosmeticCatalog.cosmeticGroupViews()
                    .firstOrNull { it.groupId == item.groupId }
                    ?.variants?.mapTo(HashSet()) { it.id }
                    .orEmpty() + listOfNotNull(item.equippedCosmeticId)
                val slots = CosmeticCatalog.localEquipped().equipped.entries
                    .filter { it.value in groupIds }
                    .map { it.key }
                CosmeticService.clearSlots(slots)
            }
        }
        ClientPlatform.runOnMain {
            onComplete(
                result.fold(
                    onSuccess = { "Unequipped ${item.name}." },
                    onFailure = { wardrobeFailure(it, "unequip ${item.name}") },
                ),
            )
        }
    }
}
