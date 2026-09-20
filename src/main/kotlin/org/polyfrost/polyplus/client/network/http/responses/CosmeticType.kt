package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = CosmeticType.Serializer::class)
enum class CosmeticType(val serializedName: String, val displayName: String) {
    Cape("cape", "Cape"),
    Backpack("backpack", "Back"),
    Glasses("glasses", "Glasses"),
    Wings("wings", "Wings"),
    Glove("glove", "Glove"),
    Hat("hat", "Hat"),
    Aura("aura", "Aura"),
    Boots("boots", "Boots"),
    Shoulder("shoulder", "Shoulder"),
    Pet("pet", "Pet"),
    Emote("emote", "Emote"),
    Unknown("unknown", "Unknown");

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
