//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.gui.preview

import org.polyfrost.polyplus.client.cosmetics.CosmeticEquipment

sealed interface PlayerPreviewSource {
    data object LocalLive : PlayerPreviewSource

    data class Override(val equipment: CosmeticEquipment) : PlayerPreviewSource
}
//?}
