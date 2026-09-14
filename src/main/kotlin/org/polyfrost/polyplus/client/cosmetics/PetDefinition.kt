package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.controller.BedrockAnimationController
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry

enum class PetArchetype {
    Flying,
    Walking,
    Shoulder;

    companion object {
        fun fromSerializedName(name: String): PetArchetype? = when (name.lowercase()) {
            "flying", "fly" -> Flying
            "walking", "walk", "land" -> Walking
            "shoulder" -> Shoulder
            else -> null
        }
    }
}

data class PetDefinition(
    val id: Int,
    val archetype: PetArchetype,
    val geometry: BedrockGeometry,
    val texture: Identifier,
    val textureFrameCount: Int,
    val animations: Map<String, BedrockAnimation>,
    val controller: BedrockAnimationController?,
    val stateMap: Map<String, String>,
    val leashRadius: Float,
    val moveSpeed: Float,
    val scale: Float,
)
