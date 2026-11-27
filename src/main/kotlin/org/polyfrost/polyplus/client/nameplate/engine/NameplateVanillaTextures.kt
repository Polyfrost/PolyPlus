package org.polyfrost.polyplus.client.nameplate.engine

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

object NameplateVanillaTextures {
    private const val BLOCK_PREFIX = "block/"

    class Image(val width: Int, val height: Int, val rgba: ByteArray)

    private val paths: Map<String, String> = HashMap<String, String>().apply {
        //? if >= 26.1 {
        put("beacon_beam", "textures/entity/beacon/beacon_beam.png")
        put("end_portal", "textures/entity/end_portal/end_portal.png")
        //?} else {
        /*put("beacon_beam", "textures/entity/beacon_beam.png")
        put("end_portal", "textures/entity/end_portal.png")
        *///?}
        put("end_sky", "textures/environment/end_sky.png")
        put("creeper_armor", "textures/entity/creeper/creeper_armor.png")
        //? if > 1.8.9 {
        put("glint", "textures/misc/enchanted_glint_item.png")
        put("breeze_wind", "textures/entity/breeze/breeze_wind.png")
        //?} else {
        /*put("glint", "textures/misc/enchanted_item_glint.png")
        *///?}
    }

    fun load(name: String): Image? {
        val location = location(name) ?: return null
        val bytes = read(location) ?: return null
        return decode(bytes, name.contains(':'))
    }

    fun resource(location: Identifier): Image? {
        val bytes = read(location) ?: return null
        return decode(bytes, false)
    }

    fun location(name: String): Identifier? {
        paths[name]?.let { return Identifier.fromNamespaceAndPath("minecraft", it) }
        val split = name.indexOf(':')
        val namespace = if (split < 0) "minecraft" else name.substring(0, split)
        var path = if (split < 0) name else name.substring(split + 1)
        if (!path.startsWith(BLOCK_PREFIX)) return null
        //? if = 1.8.9 {
        /*path = "blocks/" + LegacyBlockTextures.name(path.substring(BLOCK_PREFIX.length))
        *///?}
        return Identifier.fromNamespaceAndPath(namespace, "textures/$path.png")
    }

    private fun read(location: Identifier): ByteArray? = runCatching {
        //? if > 1.8.9 {
        Minecraft.getInstance().resourceManager.getResource(location).orElse(null)?.open()?.use { it.readAllBytes() }
        //?} else {
        /*Minecraft.getInstance().resourceManager.getResource(location).asStream().use { it.readAllBytes() }
        *///?}
    }.getOrNull()

    private fun decode(bytes: ByteArray, sprite: Boolean): Image? {
        val image = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        val width = image.width
        val height = if (sprite && image.height > width && image.height % width == 0) width else image.height
        val rgba = ByteArray(width * height * 4)
        var at = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                rgba[at++] = (argb shr 16).toByte()
                rgba[at++] = (argb shr 8).toByte()
                rgba[at++] = argb.toByte()
                rgba[at++] = (argb ushr 24).toByte()
            }
        }
        return Image(width, height, rgba)
    }
}
