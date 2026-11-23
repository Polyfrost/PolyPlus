package org.polyfrost.polyplus.client.cosmetics.runtime

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

data class AttachedCosmetic(
    val id: Identifier,
    val slot: BodySlot,
    val texture: Identifier,
    val scene: CosmeticScene,
)
