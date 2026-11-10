package org.polyfrost.polyplus.nameplate.engine.internal.material.load

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialSprite
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

internal fun Palette.slotRef(value: String): Int? {
    val ref = ColorRef.parse(value) as? ColorRef.Slot ?: return null
    val slot = slots.indexOfFirst { it.id == ref.id }
    return if (slot >= 0 && ref.tone < slots[slot].size) slot * Palette.MAX_TONES + ref.tone else null
}

internal object MaterialFiles {
    private val JSON = Json {
        ignoreUnknownKeys = true
    }

    private val PNG_SIGNATURE = intArrayOf(137, 80, 78, 71, 13, 10, 26, 10)
    private const val IHDR_END = 24
    private const val WIDTH_AT = 16
    private const val HEIGHT_AT = 20
    private const val CHUNK_TYPE_AT = 12
    private const val BYTE = 0xFF
    private const val BITS = 8
    private const val WORD = 4
    private const val IHDR = "IHDR"
    private const val MISSING = "missing"

    fun materialPath(id: String): String = "materials/$id.json"

    fun texturePath(id: String): String = "textures/$id.png"

    fun <T> read(source: BundleSource, path: String, strategy: DeserializationStrategy<T>, issues: MutableList<BundleIssue>): T? {
        val text = source.text(path)
        if (text == null) {
            issues += BundleIssue(path, MISSING, IssueCode.MISSING_FILE)
            return null
        }
        val problem = when {
            text.length > BundleLimits.MAX_JSON_CHARS -> BundleIssue(
                path,
                "larger than ${BundleLimits.MAX_JSON_CHARS} characters",
                IssueCode.JSON_SIZE,
                issueValues("limit" to BundleLimits.MAX_JSON_CHARS),
            )
            depth(text) > BundleLimits.MAX_JSON_DEPTH -> BundleIssue(
                path,
                "nested deeper than ${BundleLimits.MAX_JSON_DEPTH}",
                IssueCode.JSON_DEPTH,
                issueValues("limit" to BundleLimits.MAX_JSON_DEPTH),
            )
            else -> null
        }
        if (problem != null) {
            issues += problem
            return null
        }
        return try {
            JSON.decodeFromString(strategy, text)
        } catch (e: IllegalArgumentException) {
            issues += BundleIssue(path, e.message ?: "does not match the format")
            null
        }
    }

    fun sprite(source: BundleSource, id: String, palette: Palette, issues: MutableList<BundleIssue>): MaterialSprite? {
        val path = BundleFiles.spritePath(id)
        val file = read(source, path, SpriteFileJson.serializer(), issues) ?: return null
        val start = issues.size
        val frames = BundleFiles.framesOf(file).orEmpty()
        val height = frames.firstOrNull()?.size ?: 0
        val width = frames.firstOrNull()?.firstOrNull()?.length ?: 0
        val colors = file.colors.mapValues { (key, value) -> spriteColor("$path#colors.$key", value, palette, issues) }
        when {
            frames.isEmpty() || frames.size > BundleLimits.MAX_SPRITE_FRAMES ->
                issues += BundleIssue(path, "needs 1 to ${BundleLimits.MAX_SPRITE_FRAMES} frames in rows or frames")
            width !in 1..BundleLimits.MAX_SPRITE_SIZE || height !in 1..BundleLimits.MAX_SPRITE_SIZE ->
                issues += BundleIssue(path, "frames must be 1 to ${BundleLimits.MAX_SPRITE_SIZE} px each way")
            width * height * frames.size > MaterialLimits.MAX_SPRITE_CELLS ->
                issues += BundleIssue(path, "materials take sprites of at most ${MaterialLimits.MAX_SPRITE_CELLS} cells in all")
            frames.any { rows -> rows.size != height || rows.any { it.length != width } } ->
                issues += BundleIssue(path, "every frame and row must be $width x $height")
        }
        if (issues.size != start) return null
        val cells = IntArray(width * height * frames.size)
        var i = 0
        for (row in frames.flatten()) {
            for (c in row) {
                cells[i++] = if (c == '.') -1 else colors[c.toString()] ?: unknownCell(path, c, issues)
            }
        }
        return if (issues.size == start) MaterialSprite(width, height, frames.size, cells) else null
    }

    fun textureIssue(path: String, bytes: ByteArray?): BundleIssue? {
        val size = bytes?.let(::pngSize)
        return when {
            bytes == null -> BundleIssue(path, MISSING, IssueCode.MISSING_FILE)
            size == null -> BundleIssue(path, "is not a PNG", IssueCode.TEXTURE_FORMAT)
            size.any { it !in 1..MaterialLimits.MAX_TEXTURE_SIZE || it and (it - 1) != 0 } -> BundleIssue(
                path,
                "is ${size[0]} x ${size[1]}; each side must be a power of two up to ${MaterialLimits.MAX_TEXTURE_SIZE}",
                IssueCode.TEXTURE_SIZE,
                issueValues("width" to size[0], "height" to size[1], "limit" to MaterialLimits.MAX_TEXTURE_SIZE),
            )
            else -> null
        }
    }

    private fun spriteColor(path: String, value: String, palette: Palette, issues: MutableList<BundleIssue>): Int {
        val ref = palette.slotRef(value)
        if (ref == null) {
            issues += BundleIssue(path, "must be a slot or slot[tone] of the palette")
        }
        return ref ?: -1
    }

    private fun unknownCell(path: String, char: Char, issues: MutableList<BundleIssue>): Int {
        issues += BundleIssue(path, "uses '$char', not in colors")
        return -1
    }

    private fun pngSize(bytes: ByteArray): IntArray? {
        val valid = bytes.size >= IHDR_END && PNG_SIGNATURE.indices.all { (bytes[it].toInt() and BYTE) == PNG_SIGNATURE[it] } &&
            (0 until WORD).all { bytes[CHUNK_TYPE_AT + it].toInt().toChar() == IHDR[it] }
        return if (valid) intArrayOf(word(bytes, WIDTH_AT), word(bytes, HEIGHT_AT)) else null
    }

    private fun word(bytes: ByteArray, at: Int): Int {
        var value = 0
        for (i in 0 until WORD) {
            value = (value shl BITS) or (bytes[at + i].toInt() and BYTE)
        }
        return value
    }

    private fun depth(text: String): Int {
        var depth = 0
        var max = 0
        var inString = false
        var escaped = false
        for (c in text) {
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' || c == '[' -> max = maxOf(max, ++depth)
                c == '}' || c == ']' -> depth--
            }
        }
        return max
    }
}
