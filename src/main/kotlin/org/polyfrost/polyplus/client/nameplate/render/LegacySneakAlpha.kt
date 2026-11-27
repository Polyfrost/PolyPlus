package org.polyfrost.polyplus.client.nameplate.render

import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_BEHIND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.BAND_OVER_BACKGROUND
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_ADDITIVE
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_FLAGS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_SOLID
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.IMAGE_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_INDEX
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_OPACITY
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_X0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_X1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_Y0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.MATERIAL_Y1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_ARGB
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_BAND_LAYER
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_X1
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y0
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.OP_Y1

object LegacySneakAlpha {
    const val CLEAR: Int = 0x20
    const val READABLE: Int = 0x7F

    private const val VANILLA_OPACITY = 0.25f
    private const val BRIGHT_FULL = 0.5f
    private const val MIN_ROW_ALPHA = 0x10

    fun fromScore(score: Float): Int = CLEAR + ((READABLE - CLEAR) * score.coerceIn(0f, 1f) + 0.5f).toInt()

    fun of(
        list: NameplateDrawList,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        rowAlpha: Int,
        plateAlpha: Float,
        vanillaBackground: Boolean,
    ): Int {
        val area = (x1 - x0).toFloat() * (y1 - y0)
        if (area <= 0f) return CLEAR
        val unfade = 255f / rowAlpha.coerceIn(MIN_ROW_ALPHA, 255)
        var clear = if (vanillaBackground) 1f - VANILLA_OPACITY else 1f
        var weight = if (vanillaBackground) VANILLA_OPACITY else 0f
        var light = 0f
        var added = 0f
        val ops = list.ops
        for (op in 0 until list.opCount) {
            val at = op * OP_WORDS
            val k = cover(ops, at, x0, y0, x1, y1, area) * opacity(ops[at + OP_ARGB], unfade) * plateAlpha
            if (k <= 0f) continue
            clear *= 1f - k
            weight += k
            light += k * luma(ops[at + OP_ARGB])
        }
        val images = list.images
        for (image in 0 until list.imageCount) {
            val at = image * IMAGE_WORDS
            val flags = images[at + IMAGE_FLAGS]
            val faded = if (flags and IMAGE_SOLID != 0) 1f else plateAlpha
            val k = cover(images, at, x0, y0, x1, y1, area) * opacity(images[at + OP_ARGB], unfade) * faded
            if (k <= 0f) continue
            if (flags and IMAGE_ADDITIVE != 0) {
                added += k * luma(images[at + OP_ARGB])
                continue
            }
            clear *= 1f - k
            weight += k
            light += k * luma(images[at + OP_ARGB])
        }
        for (material in 0 until list.materialCount) {
            val shader = list.materialPrograms[material]?.shader(list.materialWord(material, MATERIAL_INDEX)) ?: continue
            if (shader.additive) continue
            val k = coverMaterial(list, material, x0, y0, x1, y1, area) * minOf(1f, list.materialWord(material, MATERIAL_OPACITY) * unfade / 255f) * plateAlpha
            if (k > 0f) clear *= 1f - k
        }
        val opaque = 1f - clear
        val bright = (if (weight > 0f) opaque * light / weight else 0f) + added
        val score = maxOf(bright / BRIGHT_FULL, (opaque - VANILLA_OPACITY) / (1f - VANILLA_OPACITY))
        return fromScore(score)
    }

    private fun cover(words: IntArray, at: Int, x0: Int, y0: Int, x1: Int, y1: Int, area: Float): Float {
        when (words[at + OP_BAND_LAYER] and 0xFF) {
            BAND_BEHIND, BAND_BACKGROUND, BAND_OVER_BACKGROUND -> Unit
            else -> return 0f
        }
        val w = minOf(x1, words[at + OP_X1]) - maxOf(x0, words[at + OP_X0])
        val h = minOf(y1, words[at + OP_Y1]) - maxOf(y0, words[at + OP_Y0])
        if (w <= 0 || h <= 0) return 0f
        return w.toFloat() * h / area
    }

    private fun coverMaterial(list: NameplateDrawList, material: Int, x0: Int, y0: Int, x1: Int, y1: Int, area: Float): Float {
        when (list.materialWord(material, MATERIAL_BAND_LAYER) and 0xFF) {
            BAND_BEHIND, BAND_BACKGROUND, BAND_OVER_BACKGROUND -> Unit
            else -> return 0f
        }
        val w = minOf(x1, list.materialWord(material, MATERIAL_X1)) - maxOf(x0, list.materialWord(material, MATERIAL_X0))
        val h = minOf(y1, list.materialWord(material, MATERIAL_Y1)) - maxOf(y0, list.materialWord(material, MATERIAL_Y0))
        if (w <= 0 || h <= 0) return 0f
        return w.toFloat() * h / area
    }

    private fun opacity(argb: Int, unfade: Float): Float = minOf(1f, (argb ushr 24) * unfade / 255f)

    private fun luma(argb: Int): Float =
        (0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)) / 255f
}
