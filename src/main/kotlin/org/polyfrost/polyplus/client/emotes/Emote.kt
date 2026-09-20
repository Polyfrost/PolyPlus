package org.polyfrost.polyplus.client.emotes

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.emotes.conditions.EmoteRules
import org.polyfrost.polyplus.client.emotes.effects.EmoteEffect

data class Emote(
    val id: Identifier,
    val animation: BedrockAnimation,
    val effects: List<EmoteEffect> = emptyList(),
    val rules: EmoteRules = EmoteRules.DEFAULT,
)
