package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = CosmeticType.Serializer::class)
enum class CosmeticType(val serializedName: String, val displayName: String) {
    Hat("hat", "Head"),
    Glasses("glasses", "Face"),
    Backpack("backpack", "Back"),
    Cape("cape", "Capes"),
    Wings("wings", "Wings"),
    Shoulder("shoulder", "Shoulders"),
    Glove("glove", "Gloves"),
    Boots("boots", "Feet"),
    Aura("aura", "Aura"),
    Pet("pet", "Pet"),
    Emote("emote", "Emote"),
    Unknown("unknown", "Unknown");

    val searchTerms: List<String>
        get() = when (this) {
            Hat -> listOf(displayName, "Hat")
            Glasses -> listOf(displayName, "Glasses")
            Backpack -> listOf(displayName, "Backpack")
            Boots -> listOf(displayName, "Boots")
            else -> listOf(displayName)
        }

    fun defaultSlot(): BodySlot? = when (this) {
        Cape -> BodySlot.Cape
        Backpack -> BodySlot.Backpack
        Glasses -> BodySlot.Glasses
        Wings -> BodySlot.Wings
        Glove -> BodySlot.RightHand
        Hat -> BodySlot.Hat
        Aura -> BodySlot.Aura
        Boots -> BodySlot.Boots
        Shoulder -> BodySlot.Shoulder
        Pet -> BodySlot.Pet
        Emote, Unknown -> null
    }

    internal object Serializer : KSerializer<CosmeticType> by ApiEnumSerializer(
        "CosmeticType",
        CosmeticType.entries,
        Unknown,
        CosmeticType::serializedName,
    )
}
