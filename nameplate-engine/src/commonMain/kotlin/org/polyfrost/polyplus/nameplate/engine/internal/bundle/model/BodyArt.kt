package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyArtJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelCanvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink

internal class BodyArt(
    private val sprite: PixelSprite,
    val left: Int,
    val right: Int,
    val mirror: Boolean,
    val segmentStart: Int,
    val segmentEnd: Int,
    val centerStart: Int,
    val centerEnd: Int,
    val top: Int,
    val bottom: Int,
) {
    val rightWidth: Int
        get() = if (mirror) left else right

    val centerWidth: Int
        get() = centerEnd - centerStart

    val repeats: Boolean
        get() = segmentEnd > segmentStart

    val minWidth: Int
        get() = left + rightWidth + centerWidth

    fun paint(canvas: PixelCanvas, width: Int, height: Int) {
        val middleEnd = width - rightWidth
        val segment = segmentEnd - segmentStart
        val center = left + (middleEnd - left - centerWidth).floorDiv(2)
        for (y in 0 until height) {
            val row = rowOf(y, height)
            if (segment > 0) {
                for (x in left until middleEnd) {
                    cell(canvas, segmentStart + (x - left) % segment, row, x, y)
                }
            }
            for (k in 0 until centerWidth) {
                cell(canvas, centerStart + k, row, center + k, y)
            }
            for (x in 0 until minOf(left, width)) {
                cell(canvas, x, row, x, y)
            }
            for (k in 0 until rightWidth) {
                val column = if (mirror) rightWidth - 1 - k else sprite.width - right + k
                cell(canvas, column, row, middleEnd + k, y)
            }
        }
    }

    private fun rowOf(y: Int, height: Int): Int = when {
        y < top -> y
        y >= height - bottom -> sprite.height - (height - y)
        else -> top + (y - top) % (sprite.height - top - bottom)
    }

    private fun cell(canvas: PixelCanvas, column: Int, row: Int, x: Int, y: Int) {
        val c = sprite.cell(column, row)
        if (c != PixelSprite.TRANSPARENT) {
            canvas[x, y] = sprite.color(c)
        }
    }

    companion object {
        fun of(json: BodyArtJson, sprite: PixelSprite): BodyArt {
            val width = sprite.width
            val rightWidth = if (json.mirror) 0 else json.right
            val segment = json.segment ?: if (json.center == null) listOf(json.left, width - rightWidth) else listOf(0, 0)
            val center = json.center ?: listOf(0, 0)
            val top = json.top ?: ((sprite.height - 1) / 2)
            val bottom = json.bottom ?: (sprite.height - 1 - top)
            return BodyArt(sprite, json.left, rightWidth, json.mirror, segment[0], segment[1], center[0], center[1], top, bottom)
        }

        fun validate(path: String, art: BodyArtJson, sprites: Map<String, SpriteFileJson>, report: IssueSink) {
            val file = sprites[art.sprite]
            if (file == null) {
                report("$path.sprite", "no sprite '${art.sprite}'")
                return
            }
            val frames = BundleFiles.framesOf(file)
            if (frames == null || frames.size != 1) {
                report("$path.sprite", "body art takes a sprite with one frame")
                return
            }
            val width = frames[0].firstOrNull()?.length ?: 0
            val height = frames[0].size
            checkCaps(path, art, width, report)
            art.segment?.let { checkColumns("$path.segment", it, width, report) }
            art.center?.let { checkColumns("$path.center", it, width, report) }
            val top = art.top ?: ((height - 1) / 2)
            val bottom = art.bottom ?: (height - 1 - top)
            if (top < 0 || bottom < 0 || top + bottom > height - 1) {
                report(path, "top and bottom must leave at least one of the sprite's $height rows to repeat")
            }
        }

        private fun checkCaps(path: String, art: BodyArtJson, width: Int, report: IssueSink) {
            if (art.left !in 0..width || art.right !in 0..width || art.left + art.right > width) {
                report(path, "left and right caps must fit the sprite's $width columns")
            }
            if (art.mirror && (art.right != 0 || art.left < 1)) {
                report("$path.mirror", "copies the left cap to the right: needs left of 1 or more and right of 0")
            }
        }

        private fun checkColumns(path: String, columns: List<Int>, width: Int, report: IssueSink) {
            if (columns.size != 2 || columns[0] !in 0 until width || columns[1] !in columns[0] + 1..width) {
                report(path, "must be [from, to] columns of the sprite, 0 <= from < to <= $width")
            }
        }
    }
}
