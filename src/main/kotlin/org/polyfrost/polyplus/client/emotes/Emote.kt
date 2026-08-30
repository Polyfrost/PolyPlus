package org.polyfrost.polyplus.client.emotes

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.emotes.effects.EmoteEffect
import org.polyfrost.polyplus.client.emotes.conditions.EmoteRules

data class Emote(
    val id: Identifier,
    val animation: BedrockAnimation,
    val geometry: BedrockGeometry,
    val effects: List<EmoteEffect> = emptyList(),
    val rules: EmoteRules = EmoteRules.DEFAULT,
)
