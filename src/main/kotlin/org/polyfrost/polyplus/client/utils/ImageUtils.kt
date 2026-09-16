package org.polyfrost.polyplus.client.utils

import org.polyfrost.polyplus.client.render.NativeImage
import java.awt.image.BufferedImage

fun BufferedImage.copyFrameInto(target: NativeImage, srcY: Int, buffer: IntArray? = null) {
    require(srcY >= 0 && target.width == width && srcY + target.height <= height) {
        "a ${target.width}x${target.height} frame at row $srcY does not fit this ${width}x$height image"
    }
    require(buffer == null || buffer.size >= target.width * target.height) {
        "the reused buffer cannot hold a ${target.width}x${target.height} frame"
    }
    val block = getRGB(0, srcY, target.width, target.height, buffer, 0, target.width)
    for (y in 0 until target.height) {
        for (x in 0 until target.width) {
            val argb = block[y * target.width + x]

            //? if >= 1.21.4
            target.setPixel(x, y, argb)
            //? if < 1.21.4
            //target.setPixelRGBA(x, y, argb.toAbgr())
        }
    }
}

private fun Int.toAbgr(): Int =
    (this and 0xFF00FF00.toInt()) or
        ((this and 0x00FF0000) ushr 16) or
        ((this and 0x000000FF) shl 16)
