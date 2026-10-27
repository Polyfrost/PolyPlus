package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.PetDefinition

sealed interface PlayerPreviewSource {
    data object LocalLive : PlayerPreviewSource

    data class Override(
        val equipment: CosmeticEquipment,
        val pet: PetDefinition? = null,
        val capeCosmeticId: Int? = null,
        val mojangCape: Identifier? = null,
        val skin: PreviewSkin? = null,
        val tint: PreviewTint? = null,
    ) : PlayerPreviewSource
}

data class PreviewSkin(val texture: Identifier, val slim: Boolean)

data class PreviewTint(val particleColor: Int?)
