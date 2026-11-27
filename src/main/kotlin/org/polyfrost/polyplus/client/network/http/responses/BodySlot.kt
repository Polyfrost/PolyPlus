package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable(with = BodySlot.Serializer::class)
enum class BodySlot(val serializedName: String, val displayName: String) {
    Hat("hat", "Head"),
    Glasses("glasses", "Face"),
    Backpack("backpack", "Back"),
    Cape("cape", "Cape"),
    Wings("wings", "Wings"),
    Shoulder("shoulder", "Shoulder"),
    LeftHand("left_hand", "Left Hand"),
    RightHand("right_hand", "Right Hand"),
    Boots("boots", "Feet"),
    Aura("aura", "Aura"),
    Pet("pet", "Pet"),
    Nameplate("nameplate", "Nameplate"),
    EffectA("effect_a", "Effect A"),
    EffectB("effect_b", "Effect B"),
    Tail("tail", "Tail"),
    Unknown("unknown", "Unknown");

    val namedByCatalogue: Boolean
        get() = this == EffectA || this == EffectB

    fun labelFor(definition: CosmeticDefinition?): String =
        if (namedByCatalogue) definition?.groupName ?: displayName else displayName

    fun cosmeticType(): CosmeticType = when (this) {
        Cape -> CosmeticType.Cape
        Backpack, Tail -> CosmeticType.Backpack
        Glasses -> CosmeticType.Glasses
        Wings -> CosmeticType.Wings
        LeftHand, RightHand -> CosmeticType.Glove
        Hat -> CosmeticType.Hat
        Aura -> CosmeticType.Aura
        Boots -> CosmeticType.Boots
        Shoulder -> CosmeticType.Shoulder
        Pet -> CosmeticType.Pet
        Nameplate -> CosmeticType.Nameplate
        EffectA, EffectB -> CosmeticType.Effect
        Unknown -> CosmeticType.Unknown
    }

    companion object {
        val equippableSlots: List<BodySlot> = entries - Unknown

        fun isEquippableSlot(slot: BodySlot): Boolean = slot in equippableSlots
    }

    internal object Serializer : KSerializer<BodySlot> by ApiEnumSerializer(
        "BodySlot",
        BodySlot.entries,
        Unknown,
        BodySlot::serializedName,
    )
}
