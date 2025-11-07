package org.polyfrost.polyplus.network.plus.cache

import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.util.ResourceLocation
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import java.awt.image.BufferedImage

sealed class CachedCosmetic {
    class Cape(image: BufferedImage) : CachedCosmetic() {
        val resource: ResourceLocation? = mc.textureManager.getDynamicTextureLocation("polyplus/cape", DynamicTexture(image))
    }
    object InvalidType : CachedCosmetic()

    fun asResource(): ResourceLocation? {
        return when (this) {
            is Cape -> resource
            is InvalidType -> null
        }
    }
}