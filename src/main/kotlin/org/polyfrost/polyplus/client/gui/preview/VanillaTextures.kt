package org.polyfrost.polyplus.client.gui.preview

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.jetbrains.skia.Image as SkiaImage

object VanillaTextures {
    const val FIRE = "textures/block/fire_1.png"
    const val SWORD = "textures/item/diamond_sword.png"

    fun load(path: String): SkiaImage? = runCatching {
        val location = Identifier.fromNamespaceAndPath("minecraft", path)
        val resource = Minecraft.getInstance().resourceManager.getResource(location).orElse(null)
            ?: return@runCatching null
        SkiaImage.makeFromEncoded(resource.open().use { it.readBytes() })
    }.getOrNull()
}
