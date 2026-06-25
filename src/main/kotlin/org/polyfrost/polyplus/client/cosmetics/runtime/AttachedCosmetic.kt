//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.runtime

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

data class AttachedCosmetic(
    val id: Identifier,
    val slot: BodySlot,
    val geometry: BedrockGeometry,
    val texture: Identifier,
    val model: BedrockEffectModel,
    val animation: BedrockAnimation? = null,
)
//?}
