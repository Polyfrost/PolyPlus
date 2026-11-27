package org.polyfrost.polyplus.client

import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticVariantUi
import org.polyfrost.polyplus.client.gui.wardrobe.lockerVariants
import org.polyfrost.polyplus.client.gui.wardrobe.variantChips

internal sealed interface EquipChoice {
    data class Chosen(val chip: CosmeticVariantUi) : EquipChoice
    data class NotOwned(val variant: String) : EquipChoice
    data class NoSuchVariant(val options: List<String>) : EquipChoice
    object Empty : EquipChoice
}

internal fun lockerChips(group: CosmeticGroupView, owned: Set<Int>, equipped: Set<Int>): List<CosmeticVariantUi> =
    variantChips(lockerVariants(group, owned, equipped))

internal fun commandNumber(group: CosmeticGroupView, owned: Set<Int>): Int =
    lockerVariants(group, owned, emptySet()).variants.first().id

internal fun equipChoice(
    group: CosmeticGroupView,
    number: Int,
    variant: String?,
    owned: Set<Int>,
    equipped: Set<Int>,
): EquipChoice {
    val shown = lockerVariants(group, owned, equipped)
    val picked = group.variants.firstOrNull { it.id == number }
        ?.takeIf { variant == null && it.id != commandNumber(group, owned) }
        ?.let { wanted ->
            shown.variants.firstOrNull { it.id == wanted.id }
                ?: shown.variants.firstOrNull { it.variantName == wanted.variantName && it.allowedSlots.toSet() == wanted.allowedSlots.toSet() }
                ?: wanted
        }
    val view = if (picked == null) shown else shown.copy(variants = shown.variants.sortedByDescending { it.id == picked.id })
    val chips = variantChips(view)
    if (variant != null) {
        chips.firstOrNull { it.name.equals(variant, ignoreCase = true) }?.let { return EquipChoice.Chosen(it) }
        val listed = variantChips(group).firstOrNull { it.name.equals(variant, ignoreCase = true) }
            ?: return EquipChoice.NoSuchVariant(chips.map { it.name })
        return chips.firstOrNull { it.pieces == listed.pieces }?.let { EquipChoice.Chosen(it) } ?: EquipChoice.NotOwned(listed.name)
    }
    if (picked != null) {
        return chips.firstOrNull { picked.id in it.pieces }?.let { EquipChoice.Chosen(it) } ?: EquipChoice.NotOwned(picked.variantName)
    }
    val target = chips.firstOrNull { chip -> chip.pieces.all { it in owned } } ?: chips.firstOrNull()
    return target?.let { EquipChoice.Chosen(it) } ?: EquipChoice.Empty
}
