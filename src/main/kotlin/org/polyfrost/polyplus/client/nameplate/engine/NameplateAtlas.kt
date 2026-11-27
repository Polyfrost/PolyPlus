package org.polyfrost.polyplus.client.nameplate.engine

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
//? if > 1.8.9 {
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.renderer.texture.DynamicTexture
//?} else {
/*import net.minecraft.client.render.texture.DynamicTexture
*///?}

object NameplateAtlas {
    private const val START_SIZE = 256
    private const val MAX_SIZE = 2048
    private const val PADDING = 1

    @JvmField
    val ID: Identifier = Identifier.fromNamespaceAndPath("polyplus", "nameplate/effects")

    @JvmStatic
    var size: Int = START_SIZE
        private set

    private var pixels = IntArray(START_SIZE * START_SIZE)
    private val regions = LongObjectMap<IntArray>()
    private var shelfX = 0
    private var shelfY = 0
    private var shelfHeight = 0
    private var dirtyX0 = Int.MAX_VALUE
    private var dirtyY0 = Int.MAX_VALUE
    private var dirtyX1 = 0
    private var dirtyY1 = 0
    private var resized = true
    private var texture: DynamicTexture? = null
    private var textureSize = 0

    fun clear() {
        regions.clear()
        shelfX = 0
        shelfY = 0
        shelfHeight = 0
    }

    fun region(id: Int): IntArray? = regions[id.toLong()]

    fun put(id: Int, width: Int, height: Int, texels: IntArray): IntArray? {
        if (width <= 0 || height <= 0 || texels.size < width * height) return null
        val spot = place(width, height) ?: return null
        val x = spot[0]
        val y = spot[1]
        for (row in 0 until height) {
            System.arraycopy(texels, row * width, pixels, (y + row) * size + x, width)
        }
        mark(x, y, x + width, y + height)
        regions[id.toLong()] = spot
        return spot
    }

    private fun place(width: Int, height: Int): IntArray? {
        while (true) {
            if (shelfX + width > size) {
                shelfY += shelfHeight + PADDING
                shelfX = 0
                shelfHeight = 0
            }
            if (width <= size && shelfY + height <= size) {
                val spot = intArrayOf(shelfX, shelfY, width, height)
                shelfX += width + PADDING
                shelfHeight = maxOf(shelfHeight, height)
                return spot
            }
            if (size >= MAX_SIZE) return null
            grow()
        }
    }

    private fun grow() {
        val old = pixels
        val oldSize = size
        size *= 2
        pixels = IntArray(size * size)
        for (row in 0 until oldSize) System.arraycopy(old, row * oldSize, pixels, row * size, oldSize)
        resized = true
        mark(0, 0, size, size)
    }

    private fun mark(x0: Int, y0: Int, x1: Int, y1: Int) {
        dirtyX0 = minOf(dirtyX0, x0)
        dirtyY0 = minOf(dirtyY0, y0)
        dirtyX1 = maxOf(dirtyX1, x1)
        dirtyY1 = maxOf(dirtyY1, y1)
    }

    fun upload() {
        if (dirtyX1 <= dirtyX0 || dirtyY1 <= dirtyY0) return
        val current = texture.takeIf { !resized && textureSize == size } ?: create()
        write(current)
        current.upload()
        dirtyX0 = Int.MAX_VALUE
        dirtyY0 = Int.MAX_VALUE
        dirtyX1 = 0
        dirtyY1 = 0
    }

    private fun create(): DynamicTexture {
        val manager = Minecraft.getInstance().textureManager
        if (texture != null) {
            //? if > 1.8.9 {
            manager.release(ID)
            //?} else {
            /*manager.close(ID)
            *///?}
        }
        //? if >= 1.21.5 {
        val created = DynamicTexture({ ID.toString() }, NativeImage(size, size, true))
        //?} elif > 1.8.9 {
        /*val created = DynamicTexture(NativeImage(size, size, true))
        *///?} else {
        /*val created = DynamicTexture(size, size)
        *///?}
        manager.register(ID, created)
        texture = created
        textureSize = size
        resized = false
        mark(0, 0, size, size)
        return created
    }

    private fun write(target: DynamicTexture) {
        //? if > 1.8.9 {
        val image = target.pixels ?: return
        for (y in dirtyY0 until dirtyY1) {
            val row = y * size
            for (x in dirtyX0 until dirtyX1) {
                //? if >= 1.21.4 {
                image.setPixel(x, y, pixels[row + x])
                //?} else {
                /*image.setPixelRGBA(x, y, abgr(pixels[row + x]))
                *///?}
            }
        }
        //?} else {
        /*val target2 = target.pixels
        for (y in dirtyY0 until dirtyY1) {
            System.arraycopy(pixels, y * size + dirtyX0, target2, y * size + dirtyX0, dirtyX1 - dirtyX0)
        }
        *///?}
    }

    @Suppress("unused")
    private fun abgr(argb: Int): Int =
        (argb and 0xFF00FF00.toInt()) or ((argb shr 16) and 0xFF) or ((argb and 0xFF) shl 16)

    fun reset() {
        clear()
        val manager = Minecraft.getInstance().textureManager
        if (texture != null) {
            //? if > 1.8.9 {
            manager.release(ID)
            //?} else {
            /*manager.close(ID)
            *///?}
        }
        texture = null
        textureSize = 0
        resized = true
    }
}
