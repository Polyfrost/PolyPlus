package org.polyfrost.polyplus.client.nameplate.render

//? if >= 1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.SubmitNodeCollector
import org.joml.Matrix4fc
//?} elif > 1.8.9 {
/*import net.minecraft.client.renderer.MultiBufferSource
import org.joml.Matrix4f
*///?}
//? if > 1.8.9 {
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
//?} else {
/*import net.minecraft.client.render.TextRenderer
import net.minecraft.client.render.platform.GlStateManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.ResourceFinishedLoading
*///?}
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_ARGB
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_DX
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_DY
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_KEEP
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_OPACITY
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_SCALE_ROT
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.LETTER_WORDS
import org.polyfrost.polyplus.client.nameplate.render.NameplateDrawList.Companion.SCALE_ONE
import kotlin.math.roundToInt

//? if > 1.8.9 {
internal class NameTagLayout : FormattedCharSink {
    var count = 0
        private set
    var codepoints = IntArray(64)
        private set
    var styles = arrayOfNulls<Style>(64)
        private set
    private var badges = BooleanArray(64)
    private var defaults = BooleanArray(64)
    private var glyphs = arrayOfNulls<Glyph>(64)
    var positions = FloatArray(65)
        private set
    var width = 0
        private set
    var nameStart = -1
        private set
    var nameEnd = -1
        private set

    private var gapAt = -1

    private val plain = StringBuilder()
    private val glyph = SingleGlyph()

    fun scan(font: Font, text: FormattedCharSequence, profileName: String, color: Int, tag: NameplateDrawTag) {
        count = 0
        gapAt = -1
        plain.setLength(0)
        text.accept(this)

        val splitter = font.splitter
        var x = 0f
        for (i in 0 until count) {
            positions[i] = x
            glyph.codepoint = codepoints[i]
            glyph.style = styles[i] ?: Style.EMPTY
            x += splitter.stringWidth(glyph)
        }
        positions[count] = x
        width = font.width(text)
        findName(profileName)

        tag.clearMeasure()
        tag.lineWidth = width
        if (nameStart >= 0) {
            tag.nameX = positions[nameStart].roundToInt()
            tag.nameWidth = (positions[nameEnd] - positions[nameStart]).roundToInt()
            tag.prefixWidth = tag.nameX
            val origin = positions[nameStart]
            for (i in nameStart until nameEnd) tag.glyph(positions[i] - origin, positions[i + 1] - positions[i])
        }
        for (i in 0 until count) {
            if (!named(i)) continue
            val own = styled(i)
            tag.color(if (own == NameplateDrawList.NO_STYLE) color else own)
        }
        if (gapAt >= 0) tag.badgeGapX = positions[gapAt].roundToInt()
    }

    override fun accept(index: Int, style: Style, codepoint: Int): Boolean {
        if (count == codepoints.size) {
            codepoints = codepoints.copyOf(count * 2)
            styles = styles.copyOf(count * 2)
            badges = badges.copyOf(count * 2)
            defaults = defaults.copyOf(count * 2)
            glyphs = glyphs.copyOf(count * 2)
            positions = positions.copyOf(count * 2 + 1)
        }
        codepoints[count] = codepoint
        styles[count] = style
        badges[count] = style.font == BADGE_FONT || style.font == GAP_FONT
        defaults[count] = DisplayTags.defaultFont(style)
        if (gapAt < 0 && style.font == GAP_FONT) gapAt = count
        plain.append(DisplayTags.glyphChar(codepoint))
        count++
        return true
    }

    private fun findName(profileName: String) {
        nameStart = -1
        nameEnd = -1
        val at = DisplayTags.findName(plain, defaults, profileName)
        if (at < 0) return
        nameStart = at
        nameEnd = at + profileName.length
    }

    fun styled(i: Int): Int {
        val color = styles[i]?.color ?: return NameplateDrawList.NO_STYLE
        return color.value
    }

    fun named(i: Int): Boolean =
        nameStart >= 0 && i >= nameStart && i < nameEnd && !badges[i] && DisplayTags.defaultFont(styles[i] ?: Style.EMPTY)

    fun glyph(i: Int): FormattedCharSequence {
        val style = styles[i] ?: Style.EMPTY
        val cached = glyphs[i]
        if (cached != null && cached.codepoint == codepoints[i] && cached.source === style) return cached
        return Glyph(codepoints[i], style, style.withColor(null as TextColor?)).also { glyphs[i] = it }
    }

    private class Glyph(val codepoint: Int, val source: Style, private val style: Style) : FormattedCharSequence {
        override fun accept(sink: FormattedCharSink): Boolean = sink.accept(0, style, codepoint)
    }

    private class SingleGlyph : FormattedCharSequence {
        var codepoint = 0
        var style: Style = Style.EMPTY

        override fun accept(sink: FormattedCharSink): Boolean = sink.accept(0, style, codepoint)
    }

    private companion object {
        val BADGE_FONT = PolyPlusBadge.badgeIcon.style.font
        val GAP_FONT = PolyPlusBadge.badgeGap.style.font
    }
}
//?} else {
/*internal class NameTagLayout(val text: String, val profileName: String, val unicode: Boolean) {
    var count = 0
        private set
    var width = 0
        private set
    var nameStart = -1
        private set
    var nameEnd = -1
        private set
    private var gapAt = -1

    private var tones = IntArray(text.length)
    private var formats = IntArray(text.length)
    private var chars = CharArray(text.length)
    var positions = FloatArray(text.length + 1)
        private set

    private var recolored: Array<String?> = arrayOfNulls(text.length)

    fun scan(font: TextRenderer) {
        val plain = StringBuilder(text.length)
        var color = NO_COLOR
        var format = 0
        var x = 0f
        var i = 0
        count = 0
        gapAt = -1
        while (i < text.length) {
            val c = text[i]
            if (c == SECTION && i + 1 < text.length) {
                val code = CODES.indexOf(text[i + 1].lowercaseChar())
                when {
                    code == RESET -> {
                        color = NO_COLOR
                        format = 0
                    }
                    code >= COLOR_CODES -> format = format or (1 shl (code - COLOR_CODES))
                    else -> {
                        color = if (code < 0) CODES[WHITE] else CODES[code]
                        format = 0
                    }
                }
                i += 2
                continue
            }
            if (gapAt < 0 && c == PolyPlusBadge.LEGACY_CHAR) gapAt = count
            tones[count] = if (color == NO_COLOR) NameplateDrawList.NO_STYLE else paletteOf(color)
            formats[count] = format
            chars[count] = c
            positions[count] = x
            val advance = maxOf(0, font.getWidth(c))
            x += advance + if (format and BOLD != 0 && advance > 0) 1 else 0
            plain.append(c)
            count++
            i++
        }
        positions[count] = x
        width = font.getWidth(text)
        findName(plain)
    }

    fun measure(tag: NameplateDrawTag, color: Int) {
        tag.clearMeasure()
        tag.lineWidth = width
        if (nameStart >= 0) {
            tag.nameX = positions[nameStart].roundToInt()
            tag.nameWidth = (positions[nameEnd] - positions[nameStart]).roundToInt()
            tag.prefixWidth = tag.nameX
            val origin = positions[nameStart]
            for (i in nameStart until nameEnd) tag.glyph(positions[i] - origin, positions[i + 1] - positions[i])
        }
        for (i in 0 until count) {
            if (!named(i)) continue
            val own = styled(i)
            tag.color(if (own == NameplateDrawList.NO_STYLE) color else own)
        }
        if (gapAt >= 0) tag.badgeGapX = positions[gapAt].roundToInt() + BADGE_INSET
    }

    fun named(i: Int): Boolean = nameStart >= 0 && i >= nameStart && i < nameEnd

    fun styled(i: Int): Int = tones[i]

    private fun paletteOf(code: Char): Int {
        val index = CODES.indexOf(code)
        return if (index in 0 until COLOR_CODES) PALETTE[index] else NameplateDrawList.NO_STYLE
    }

    private fun findName(plain: StringBuilder) {
        nameStart = -1
        nameEnd = -1
        if (profileName.isEmpty()) return
        var from = 0
        while (true) {
            val at = plain.indexOf(profileName, from)
            if (at < 0) return
            val end = at + profileName.length
            if ((at == 0 || !NameTagSupport.wordChar(plain[at - 1])) && (end == plain.length || !NameTagSupport.wordChar(plain[end]))) {
                nameStart = at
                nameEnd = end
                return
            }
            from = at + 1
        }
    }

    fun recolored(i: Int): String = recolored[i] ?: (formatState(i) + chars[i]).also { recolored[i] = it }

    private fun formatState(i: Int): String {
        val out = StringBuilder(12)
        val format = formats[i]
        for (bit in 0 until FORMAT_CODES) {
            if (format and (1 shl bit) != 0) out.append(SECTION).append(CODES[COLOR_CODES + bit])
        }
        return out.toString()
    }

    private companion object {
        const val SECTION = '§'
        const val CODES = "0123456789abcdefklmnor"
        const val COLOR_CODES = 16
        const val FORMAT_CODES = 5
        const val RESET = 21
        const val WHITE = 15
        const val BOLD = 1 shl 1
        const val NO_COLOR = '\u0000'
        const val BADGE_INSET = 1

        val PALETTE = intArrayOf(
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
        )
    }
}
*///?}

internal object NameTagText {
    //? if = 1.8.9 {
    /*private const val CAPACITY = 128
    *///?}
    private const val RGB = 0xFFFFFF
    private const val SHADOW_OFFSET = 1f

    //? if >= 1.21.10 {
    private val pose = PoseStack()
    //?} elif > 1.8.9 {
    /*private val matrix = Matrix4f()
    *///?} else {
    /*private val OUTLINE_X = floatArrayOf(-1f, 0f, 1f, -1f, 1f, -1f, 0f, 1f)
    private val OUTLINE_Y = floatArrayOf(-1f, -1f, -1f, 0f, 0f, 1f, 1f, 1f)

    private val layouts = object : LinkedHashMap<String, NameTagLayout>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NameTagLayout>): Boolean = size > CAPACITY
    }

    @Volatile
    private var stale = false
    private var unicode = false

    init {
        eventHandler<ResourceFinishedLoading> { stale = true }
    }

    fun layout(font: TextRenderer, text: String, profileName: String): NameTagLayout {
        if (stale || font.unicode != unicode) {
            stale = false
            unicode = font.unicode
            layouts.clear()
        }
        val cached = layouts[text]
        if (cached != null && cached.profileName == profileName && cached.unicode == font.unicode && cached.width == font.getWidth(text)) {
            return cached
        }
        val layout = NameTagLayout(text, profileName, font.unicode)
        layout.scan(font)
        layouts[text] = layout
        return layout
    }

    fun clear() {
        layouts.clear()
    }
    *///?}

    fun redraw(
        //? if >= 1.21.10 {
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        color: Int,
        lightCoords: Int,
        layout: NameTagLayout,
        list: NameplateDrawList,
        mode: Font.DisplayMode = Font.DisplayMode.NORMAL,
        //?} elif > 1.8.9 {
        /*font: Font,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        x: Float,
        y: Float,
        color: Int,
        light: Int,
        layout: NameTagLayout,
        list: NameplateDrawList,
        mode: Font.DisplayMode = Font.DisplayMode.NORMAL,
        *///?} else {
        /*font: TextRenderer,
        layout: NameTagLayout,
        list: NameplateDrawList,
        x: Float,
        y: Float,
        color: Int,
        *///?}
    ) {
        val textAlpha = color ushr 24
        val letters = list.letters
        for (i in 0 until layout.count) {
            val tone = layout.styled(i)
            val hasOwn = tone != NameplateDrawList.NO_STYLE
            val base = if (hasOwn) tone else color
            val named = layout.named(i)
            var rgb = if (named) list.glyphRgb(hasOwn, base) else base and RGB
            var alpha = textAlpha
            var gx = x + layout.positions[i]
            var gy = y
            var scaleRot = SCALE_ONE
            var letter = -1
            if (named) {
                letter = i - layout.nameStart
                if (letter < list.letterCount) {
                    val at = letter * LETTER_WORDS
                    gx += letters[at + LETTER_DX]
                    gy += letters[at + LETTER_DY]
                    val own = letters[at + LETTER_ARGB]
                    if (own != LETTER_KEEP) rgb = own and RGB
                    alpha = NameplateDrawList.letterAlpha(textAlpha, letters[at + LETTER_OPACITY])
                    scaleRot = letters[at + LETTER_SCALE_ROT]
                }
            }
            if (alpha < NameTagSupport.VISIBLE_ALPHA) continue
            //? if >= 1.21.10 {
            val glyph = layout.glyph(i)
            val advance = layout.positions[i + 1] - layout.positions[i]
            place(matrix, gx, gy, advance, scaleRot)
            val shadow = if (named) list.shadow(letter, rgb) else list.plainShadow(rgb)
            if (shadow != 0) {
                val shadowColor = NameplateDrawList.guard((alpha shl 24) or (shadow and RGB))
                submit(collector, SHADOW_OFFSET, SHADOW_OFFSET, glyph, shadowColor, lightCoords, 0, mode)
            }
            val outline = if (named) list.outline(letter) else 0
            val outlineColor = if (outline != 0) NameplateDrawList.guard((alpha shl 24) or (outline and RGB)) else 0
            val glyphColor = NameplateDrawList.guard((alpha shl 24) or rgb)
            submit(collector, 0f, 0f, glyph, glyphColor, lightCoords, outlineColor, mode)
            //?} elif > 1.8.9 {
            /*place(pose, gx, gy, layout.positions[i + 1] - layout.positions[i], scaleRot)
            val glyph = layout.glyph(i)
            val shadow = if (named) list.shadow(letter, rgb) else list.plainShadow(rgb)
            if (shadow != 0) {
                val shadowColor = NameplateDrawList.guard((alpha shl 24) or (shadow and RGB))
                font.drawInBatch(glyph, SHADOW_OFFSET, SHADOW_OFFSET, shadowColor, false, matrix, buffers, mode, 0, light)
            }
            val glyphColor = NameplateDrawList.guard((alpha shl 24) or rgb)
            val outline = if (named) list.outline(letter) else 0
            if (outline != 0) {
                val outlineColor = NameplateDrawList.guard((alpha shl 24) or (outline and RGB))
                font.drawInBatch8xOutline(glyph, 0f, 0f, glyphColor, outlineColor, matrix, buffers, light)
            } else {
                font.drawInBatch(glyph, 0f, 0f, glyphColor, false, matrix, buffers, mode, 0, light)
            }
            *///?} else {
            /*val glyph = layout.recolored(i)
            val advance = layout.positions[i + 1] - layout.positions[i]
            GlStateManager.pushMatrix()
            try {
                place(gx, gy, advance, scaleRot)
                val shadow = if (named) list.shadow(letter, rgb) else list.plainShadow(rgb)
                if (shadow != 0) draw(font, glyph, SHADOW_OFFSET, SHADOW_OFFSET, (alpha shl 24) or (shadow and RGB))
                val outline = if (named) list.outline(letter) else 0
                if (outline != 0) {
                    val outlineColor = (alpha shl 24) or (outline and RGB)
                    for (k in OUTLINE_X.indices) draw(font, glyph, OUTLINE_X[k], OUTLINE_Y[k], outlineColor)
                }
                draw(font, glyph, 0f, 0f, (alpha shl 24) or rgb)
            } finally {
                GlStateManager.popMatrix()
            }
            *///?}
        }
    }

    //? if >= 1.21.10 {
    fun plain(
        collector: SubmitNodeCollector,
        matrix: Matrix4fc,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        color: Int,
        lightCoords: Int,
        mode: Font.DisplayMode = Font.DisplayMode.NORMAL,
    ) {
        pose.last().pose().set(matrix)
        submit(collector, x, y, text, color, lightCoords, 0, mode)
    }

    private fun place(matrix: Matrix4fc, x: Float, y: Float, advance: Float, scaleRot: Int) {
        val target = pose.last().pose().set(matrix)
        val quarters = NameplateDrawList.scaleQuarters(scaleRot)
        val degrees = NameplateDrawList.rotationDegrees(scaleRot)
        if (quarters == SCALE_ONE && degrees == 0) {
            target.translate(x, y, 0f)
            return
        }
        val cx = x + advance / 2f
        val cy = y + NameplateDrawList.GLYPH_HEIGHT / 2f
        val scale = quarters / SCALE_ONE.toFloat()
        target.translate(cx, cy, 0f)
            .rotateZ(Math.toRadians(degrees.toDouble()).toFloat())
            .scale(scale, scale, 1f)
            .translate(-advance / 2f, -NameplateDrawList.GLYPH_HEIGHT / 2f, 0f)
    }

    private fun submit(
        collector: SubmitNodeCollector,
        x: Float,
        y: Float,
        text: FormattedCharSequence,
        color: Int,
        lightCoords: Int,
        outline: Int,
        mode: Font.DisplayMode,
    ) {
        collector.submitText(pose, x, y, text, false, mode, lightCoords, color, 0, outline)
    }
    //?} elif > 1.8.9 {
    /*fun plain(
        font: Font,
        text: FormattedCharSequence,
        x: Float,
        y: Float,
        color: Int,
        pose: Matrix4f,
        buffers: MultiBufferSource,
        light: Int,
        mode: Font.DisplayMode = Font.DisplayMode.NORMAL,
    ) {
        if (color ushr 24 < NameTagSupport.VISIBLE_ALPHA) return
        font.drawInBatch(text, x, y, color, false, pose, buffers, mode, 0, light)
    }

    private fun place(pose: Matrix4f, x: Float, y: Float, advance: Float, scaleRot: Int) {
        matrix.set(pose)
        val quarters = NameplateDrawList.scaleQuarters(scaleRot)
        val degrees = NameplateDrawList.rotationDegrees(scaleRot)
        if (quarters == SCALE_ONE && degrees == 0) {
            matrix.translate(x, y, 0f)
            return
        }
        val half = NameplateDrawList.GLYPH_HEIGHT / 2f
        val scale = quarters / SCALE_ONE.toFloat()
        matrix.translate(x + advance / 2f, y + half, 0f)
            .rotateZ(Math.toRadians(degrees.toDouble()).toFloat())
            .scale(scale, scale, 1f)
            .translate(-advance / 2f, -half, 0f)
    }
    *///?} else {
    /*fun plain(font: TextRenderer, text: String, x: Float, y: Float, color: Int) {
        draw(font, text, x, y, color)
    }

    private fun place(x: Float, y: Float, advance: Float, scaleRot: Int) {
        val quarters = NameplateDrawList.scaleQuarters(scaleRot)
        val degrees = NameplateDrawList.rotationDegrees(scaleRot)
        if (quarters == SCALE_ONE && degrees == 0) {
            GlStateManager.translatef(x, y, 0f)
            return
        }
        val half = NameplateDrawList.GLYPH_HEIGHT / 2f
        val scale = quarters / SCALE_ONE.toFloat()
        GlStateManager.translatef(x + advance / 2f, y + half, 0f)
        GlStateManager.rotatef(degrees.toFloat(), 0f, 0f, 1f)
        GlStateManager.scalef(scale, scale, 1f)
        GlStateManager.translatef(-advance / 2f, -half, 0f)
    }

    private fun draw(font: TextRenderer, text: String, x: Float, y: Float, color: Int) {
        if (color ushr 24 < NameTagSupport.VISIBLE_ALPHA) return
        font.draw(text, x, y, NameplateDrawList.guard(color), false)
    }
    *///?}
}
