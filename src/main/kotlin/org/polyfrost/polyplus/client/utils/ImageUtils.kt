package org.polyfrost.polyplus.client.utils

import com.mojang.blaze3d.platform.NativeImage
import java.awt.image.BufferedImage

fun BufferedImage.toNativeImage(): NativeImage {
    val native = NativeImage(width, height, false)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = getRGB(x, y)
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            native.setPixel(x, y, (a shl 24) or (b shl 16) or (g shl 8) or r)
        }
    }
    return native
}
