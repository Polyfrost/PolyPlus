package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.compose.render.PolyBitmap

object VanillaTextures {
    //? if > 1.8.9 {
    const val FIRE = "textures/block/fire_1.png"
    const val SWORD = "textures/item/diamond_sword.png"
    //?} else {
    /*const val FIRE = "textures/blocks/fire_layer_1.png"
    const val SWORD = "textures/items/diamond_sword.png"
    *///?}

    fun load(path: String): PolyBitmap? = runCatching {
        val location = Identifier.fromNamespaceAndPath("minecraft", path)
        //? if > 1.8.9 {
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
            ?: return@runCatching null
        PolyBitmap.decode(resource.open().use { it.readBytes() })
        //?} else {
        /*val resource = Minecraft.getInstance().resourceManager.getResource(location)
        PolyBitmap.decode(resource.asStream().use { it.readBytes() })
        *///?}
    }.getOrNull()
}
