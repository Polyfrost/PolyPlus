package org.polyfrost.polyplus.client.cosmetics

import dev.deftu.omnicore.api.client.image.OmniImages
import dev.deftu.omnicore.api.client.textures.OmniTextureHandle
import dev.deftu.omnicore.api.client.textures.OmniTextures
import net.minecraft.util.ResourceLocation
import java.awt.image.BufferedImage

sealed interface CachedCosmetic {
    data object None : CachedCosmetic

    data class Cape(val image: BufferedImage) : CachedCosmetic {
        private var texture: OmniTextureHandle? = null

        override fun asResource(): ResourceLocation? {
            if (texture == null) {
                texture = OmniTextures.load(OmniImages.from(image))
            }

            return texture?.location
        }
    }

    fun asResource(): ResourceLocation? {
        return null
    }
}
