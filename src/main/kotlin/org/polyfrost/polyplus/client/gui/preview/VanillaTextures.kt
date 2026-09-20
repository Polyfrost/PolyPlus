package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.jetbrains.skia.Image as SkiaImage

object VanillaTextures {
    //? if > 1.8.9 {
    const val FIRE = "textures/block/fire_1.png"
    const val SWORD = "textures/item/diamond_sword.png"
    //?} else {
    /*const val FIRE = "textures/blocks/fire_layer_1.png"
    const val SWORD = "textures/items/diamond_sword.png"
    *///?}

    fun load(path: String): SkiaImage? = runCatching {
        val location = Identifier.fromNamespaceAndPath("minecraft", path)
        //? if > 1.8.9 {
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
            ?: return@runCatching null
        SkiaImage.makeFromEncoded(resource.open().use { it.readBytes() })
        //?} else {
        /*val resource = Minecraft.getInstance().resourceManager.getResource(location)
        SkiaImage.makeFromEncoded(resource.asStream().use { it.readBytes() })
        *///?}
    }.getOrNull()
}
