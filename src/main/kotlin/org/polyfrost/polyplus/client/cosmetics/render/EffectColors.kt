package org.polyfrost.polyplus.client.cosmetics.render

import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticColors
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalettes
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import java.util.UUID

internal object EffectColors {
    fun sync(cosmetic: AttachedCosmetic, wearer: Long, owner: UUID?, preview: Boolean = false) {
        val scene = cosmetic.scene
        if (!scene.hasEffects || owner == null) return
        scene.paint(wearer, cosmetic.colorway, CosmeticColors.get(owner, cosmetic.cosmeticId, preview))
    }

    fun colorway(cosmeticId: Int, variantKey: String?): String? {
        val palette = CosmeticPalettes.cached(cosmeticId) ?: return variantKey
        return palette.designedColorway(variantKey)
    }
}
