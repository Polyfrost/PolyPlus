package org.polyfrost.polyplus.client.emotes.effects

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.model.BedrockEffectModel

data class EmoteEffect(
    val texture: Identifier,
    val model: BedrockEffectModel,
)
