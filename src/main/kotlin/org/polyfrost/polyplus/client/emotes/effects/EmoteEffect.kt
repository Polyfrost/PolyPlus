package org.polyfrost.polyplus.client.emotes.effects

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel

data class EmoteEffect(
    val id: String,
    val geometry: BedrockGeometry,
    val texture: Identifier,
    val model: BedrockEffectModel,
)
