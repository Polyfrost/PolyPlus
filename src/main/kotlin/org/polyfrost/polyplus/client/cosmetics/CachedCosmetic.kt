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
                val hash = image.hashCode()
                val texture = DynamicTexture(
                    //?if >= 1.21.5 {
                     { "polyplus:cape/$hash" },
                    //?}
                    image.toNativeImage())
                //? if >= 1.21.10 {
                val textureId = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "textures/cape/$hash.png",
                )
                //?} else {
                /*val textureId = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "cape/$hash",
                )*/
                //?}
                Minecraft.getInstance().textureManager.register(textureId, texture)
                location = Identifier.fromNamespaceAndPath(
                    PolyPlusConstants.ID,
                    "cape/$hash",
                )
            }
            return location
        }
    }

    fun asResource(): Identifier? {
        return null
    }
}
