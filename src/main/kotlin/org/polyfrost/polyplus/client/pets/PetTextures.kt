package org.polyfrost.polyplus.client.pets

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.polyfrost.polyplus.PolyPlusConstants
import java.util.concurrent.ConcurrentHashMap

//? if > 1.8.9 {
import net.minecraft.client.renderer.texture.DynamicTexture
import org.polyfrost.polyplus.client.render.NativeImage
//?} else {
/*import net.minecraft.client.render.texture.DynamicTexture
import java.awt.image.BufferedImage
*///?}

internal object PetTextures {
    private var uploads = 0
    private val translucent = ConcurrentHashMap.newKeySet<Identifier>()

    fun isTranslucent(location: Identifier): Boolean = translucent.isNotEmpty() && location in translucent

    fun register(prefix: String, width: Int, height: Int, rgba: ByteArray): Identifier {
        val location = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "$prefix/${uploads++}")
        if (partialAlpha(rgba)) translucent.add(location)
        //? if > 1.8.9 {
        val image = NativeImage(width, height, false)
        var adopted = false
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val argb = argb(rgba, (y * width + x) * 4)
                    //? if >= 1.21.4
                    image.setPixel(x, y, argb)
                    //? if < 1.21.4
                    //image.setPixelRGBA(x, y, toAbgr(argb))
                }
            }
            val texture = DynamicTexture(
                //? if >= 1.21.5 {
                { location.toString() },
                //?}
                image,
            )
            adopted = true
            Minecraft.getInstance().textureManager.register(location, texture)
        } finally {
            if (!adopted) image.close()
        }
        //?} else {
        /*val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) image.setRGB(x, y, argb(rgba, (y * width + x) * 4))
        }
        Minecraft.getInstance().textureManager.register(location, DynamicTexture(image))
        *///?}
        return location
    }

    fun release(location: Identifier) {
        translucent.remove(location)
        //? if > 1.8.9 {
        Minecraft.getInstance().textureManager.release(location)
        //?} else {
        /*Minecraft.getInstance().textureManager.close(location)
        *///?}
    }

    private fun partialAlpha(rgba: ByteArray): Boolean {
        var at = 3
        while (at < rgba.size) {
            if ((rgba[at].toInt() and 0xFF) in 1..254) return true
            at += 4
        }
        return false
    }

    private fun argb(rgba: ByteArray, at: Int): Int {
        val r = rgba[at].toInt() and 0xFF
        val g = rgba[at + 1].toInt() and 0xFF
        val b = rgba[at + 2].toInt() and 0xFF
        val a = rgba[at + 3].toInt() and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    //? if < 1.21.4 && > 1.8.9 {
    /*private fun toAbgr(argb: Int): Int =
        (argb and 0xFF00FF00.toInt()) or ((argb and 0x00FF0000) ushr 16) or ((argb and 0x000000FF) shl 16)
    *///?}
}
