package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.PetDefinition

sealed interface PlayerPreviewSource {
    data object LocalLive : PlayerPreviewSource

    data class Override(
        val equipment: CosmeticEquipment,
        val capeTexture: Identifier? = null,
        val pet: PetDefinition? = null,
    ) : PlayerPreviewSource
}
