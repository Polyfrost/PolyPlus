package org.polyfrost.polyplus.client.network.http.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CosmeticDefinition(
    val id: Int,
    val type: CosmeticType,
    val name: String = "Cosmetic",
    val url: String? = null,
    val hash: String,
    @SerialName("allowed_slots") val allowedSlots: List<BodySlot> = emptyList(),
    val groupId: Int = id,
    val groupName: String = name,
    val variantName: String = name,
    // "slim"/"wide" or null when the variant is model-independent
    val model: String? = null,
) {
    fun preferredSlot(): BodySlot? {
        val default = type.defaultSlot()
        if (default != null && (allowedSlots.isEmpty() || default in allowedSlots)) {
            return default
        }
        return allowedSlots.firstOrNull { BodySlot.isEquippableSlot(it) }
    }

    fun allowsSlot(slot: BodySlot): Boolean =
        if (allowedSlots.isEmpty()) slot.cosmeticType() == type else slot in allowedSlots
}

@Serializable
data class EmoteDefinition(
    val id: Int,
    val name: String = "Emote",
    val url: String? = null,
    val hash: String,
) {
    fun asCosmeticDefinition(): CosmeticDefinition =
        CosmeticDefinition(
            id = id,
            type = CosmeticType.Emote,
            name = name,
            url = url,
            hash = hash,
        )
}
