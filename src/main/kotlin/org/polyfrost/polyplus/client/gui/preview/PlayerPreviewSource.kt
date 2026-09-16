package org.polyfrost.polyplus.client.gui.preview

import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment
import org.polyfrost.polyplus.client.cosmetics.PetDefinition

sealed interface PlayerPreviewSource {
    data object LocalLive : PlayerPreviewSource

    data class Override(
        val equipment: CosmeticEquipment,
        val pet: PetDefinition? = null,
        val capeCosmeticId: Int? = null,
    ) : PlayerPreviewSource
}
