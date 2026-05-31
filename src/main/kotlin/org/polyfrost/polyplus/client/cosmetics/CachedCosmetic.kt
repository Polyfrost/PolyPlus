package org.polyfrost.polyplus.client.cosmetics

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.utils.toNativeImage
import java.awt.image.BufferedImage

sealed interface CachedCosmetic {
    data object None : CachedCosmetic

    data class Cape(val image: BufferedImage) : CachedCosmetic {
        private var location: Identifier? = null

        override fun asResource(): Identifier? {
            if (location == null) {
                val texture = DynamicTexture({ "polyplus:cape/${image.hashCode()}" }, image.toNativeImage())
                val id = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "cape/${image.hashCode()}",
                )
                Minecraft.getInstance().textureManager.register(id, texture)
                location = id
            }
            return location
        }
    }

    fun asResource(): Identifier? {
        return null
    }
}
