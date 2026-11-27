package org.polyfrost.polyplus.client.nameplate.engine

import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager

object NameplateBadgeArt {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    const val SIZE: Int = 8
    const val OUTLINE_INSET: Int = 1
    const val OUTLINE_SIZE: Int = SIZE + 2 * OUTLINE_INSET

    private const val ART_ID = -1
    private const val OUTLINE_ID = -2
    private const val TEXELS_PER_PIXEL = 4
    private const val OUTLINE_FRAME = OUTLINE_SIZE * TEXELS_PER_PIXEL

    private val ART: Identifier = Identifier.fromNamespaceAndPath("polyplus", "textures/nameplate/badge.png")
    private val OUTLINE: Identifier = Identifier.fromNamespaceAndPath("polyplus", "textures/nameplate/badge_outline.png")

    private var artTexels: Texels? = null
    private var outlineTexels: Texels? = null
    private var failed = false

    private class Texels(val width: Int, val height: Int, val argb: IntArray)

    fun art(): IntArray? = NameplateAtlas.region(ART_ID)

    fun outline(): IntArray? = NameplateAtlas.region(OUTLINE_ID)

    fun ensure() {
        if (failed) return
        if (NameplateAtlas.region(ART_ID) != null && NameplateAtlas.region(OUTLINE_ID) != null) return
        val art = artTexels ?: load(ART, 0)?.also { artTexels = it }
        val outline = outlineTexels ?: load(OUTLINE, OUTLINE_FRAME)?.also { outlineTexels = it }
        if (art == null || outline == null) {
            failed = true
            LOGGER.warn("The nameplate badge art could not be read; badges beside nameplates are hidden")
            return
        }
        if (NameplateAtlas.region(ART_ID) == null) NameplateAtlas.put(ART_ID, art.width, art.height, art.argb)
        if (NameplateAtlas.region(OUTLINE_ID) == null) NameplateAtlas.put(OUTLINE_ID, outline.width, outline.height, outline.argb)
    }

    fun reload() {
        artTexels = null
        outlineTexels = null
        failed = false
    }

    private fun load(id: Identifier, frame: Int): Texels? {
        val image = NameplateVanillaTextures.resource(id) ?: return null
        val width = maxOf(image.width, frame)
        val height = maxOf(image.height, frame)
        val left = (width - image.width) / 2
        val top = (height - image.height) / 2
        val argb = IntArray(width * height)
        val rgba = image.rgba
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val at = (y * image.width + x) * 4
                val r = rgba[at].toInt() and 0xFF
                val g = rgba[at + 1].toInt() and 0xFF
                val b = rgba[at + 2].toInt() and 0xFF
                val a = rgba[at + 3].toInt() and 0xFF
                argb[(y + top) * width + x + left] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Texels(width, height, argb)
    }
}
