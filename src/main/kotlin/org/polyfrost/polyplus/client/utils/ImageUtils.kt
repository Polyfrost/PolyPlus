package org.polyfrost.polyplus.client.utils

import com.mojang.blaze3d.platform.NativeImage
import java.awt.image.BufferedImage

fun BufferedImage.toNativeImage(): NativeImage {
    val native = NativeImage(width, height, false)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)

            //? if >= 1.21.4
            native.setPixel(x, y, argb)
            //? if < 1.21.4
            /*native.setPixelRGBA(x, y, argb.toAbgr())*/
        }
    }
    return native
}

private fun Int.toAbgr(): Int =
    (this and 0xFF00FF00.toInt()) or
        ((this and 0x00FF0000) ushr 16) or
        ((this and 0x000000FF) shl 16)
