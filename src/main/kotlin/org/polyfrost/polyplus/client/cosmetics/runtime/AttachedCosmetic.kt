package org.polyfrost.polyplus.client.cosmetics.runtime

import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.client.cosmetics.render.CosmeticScene
import org.polyfrost.polyplus.client.network.http.responses.BodySlot

class AttachedCosmetic(
    val id: Identifier,
    val cosmeticId: Int,
    val slot: BodySlot,
    val texture: Identifier,
    scene: CosmeticScene,
    val colorway: Int,
) {
    val scene: CosmeticScene = scene.forSlot(slot)

    fun copy(
        id: Identifier = this.id,
        cosmeticId: Int = this.cosmeticId,
        slot: BodySlot = this.slot,
        texture: Identifier = this.texture,
        scene: CosmeticScene = this.scene,
        colorway: Int = this.colorway,
    ): AttachedCosmetic = AttachedCosmetic(id, cosmeticId, slot, texture, scene, colorway)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AttachedCosmetic) return false
        return id == other.id &&
            cosmeticId == other.cosmeticId &&
            slot == other.slot &&
            texture == other.texture &&
            scene == other.scene &&
            colorway == other.colorway
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + cosmeticId
        result = 31 * result + slot.hashCode()
        result = 31 * result + texture.hashCode()
        result = 31 * result + scene.hashCode()
        result = 31 * result + colorway
        return result
    }

    override fun toString(): String =
        "AttachedCosmetic(id=$id, cosmeticId=$cosmeticId, slot=$slot, texture=$texture, scene=$scene, colorway=$colorway)"
}
