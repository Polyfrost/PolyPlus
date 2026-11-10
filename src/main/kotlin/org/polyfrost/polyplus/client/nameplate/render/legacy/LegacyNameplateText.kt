package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.render.TextRenderer
import org.lwjgl.opengl.GL11
import org.polyfrost.polyplus.client.PolyPlusBadge
import org.polyfrost.polyplus.client.emoji.EmojiFont
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import org.polyfrost.polyplus.nameplate.engine.api.text.TextStyling
import kotlin.math.roundToInt

class LegacyLine internal constructor(val text: String, val profileName: String?) {
    var count: Int = 0
        private set
    var textWidth: Int = 0
        private set
    var badgeX: Float = -1f
        private set
    var nameStart: Int = 0
        private set
    var nameEnd: Int = 0
        private set
    var nameX: Int = 0
        private set
    var nameWidth: Int = 0
        private set
    var prefixWidth: Int = 0
        private set

    internal var unicode: Boolean = false
        private set
    internal var chars: CharArray = CharArray(0)
        private set
    internal var ownRgb: IntArray = IntArray(0)
        private set
    internal var formats: IntArray = IntArray(0)
        private set
    internal var art: BooleanArray = BooleanArray(0)
        private set
    internal var glyphX: FloatArray = FloatArray(1)
        private set
    internal var style: LegacyStyle? = null

    private val strings = HashMap<Int, String>()

    internal fun parse(font: TextRenderer) {
        val n = text.length
        chars = CharArray(n)
        ownRgb = IntArray(n)
        formats = IntArray(n)
        art = BooleanArray(n)
        glyphX = FloatArray(n + 1)
        unicode = font.unicode
        strings.clear()
        style = null

        val plain = StringBuilder(n)
        val plainGlyph = IntArray(n)
        var color = -1
        var format = 0
        var i = 0
        count = 0
        badgeX = -1f
        while (i < n) {
            val c = text[i]
            if (c == SECTION && i + 1 < n) {
                val code = CODES.indexOf(text[i + 1].lowercaseChar())
                when {
                    code < COLOR_CODES -> {
                        format = 0
                        color = if (code < 0) WHITE_CODE else code
                    }
                    code == RESET -> {
                        format = 0
                        color = -1
                    }
                    else -> format = format or (1 shl (code - COLOR_CODES))
                }
                i += 2
                continue
            }
            val glyph = count
            val isArt = c == PolyPlusBadge.LEGACY_CHAR || EmojiFont.legacyIndex(c) >= 0
            if (c == PolyPlusBadge.LEGACY_CHAR && badgeX < 0) {
                badgeX = glyphX[glyph]
            }
            chars[glyph] = c
            ownRgb[glyph] = if (color >= 0) font.getColor(CODES[color]) else -1
            formats[glyph] = format
            art[glyph] = isArt
            if (!isArt) {
                plain.append(c)
                plainGlyph[plain.length - 1] = glyph
            }
            val bold = if (format and BOLD != 0) 1 else 0
            glyphX[glyph + 1] = glyphX[glyph] + maxOf(0, font.getWidth(c)) + bold
            count++
            i++
        }
        textWidth = font.getWidth(text)
        findName(plain, plainGlyph)
    }

    private fun findName(plain: StringBuilder, plainGlyph: IntArray) {
        val name = profileName
        val at = if (name.isNullOrEmpty()) -1 else plain.lastIndexOf(name)
        when {
            at >= 0 && name != null -> {
                nameStart = plainGlyph[at]
                nameEnd = plainGlyph[at + name.length - 1] + 1
            }
            plain.isNotEmpty() -> {
                nameStart = plainGlyph[0]
                nameEnd = plainGlyph[plain.length - 1] + 1
            }
            else -> {
                nameStart = 0
                nameEnd = 0
            }
        }
        nameX = glyphX[nameStart].roundToInt()
        nameWidth = (glyphX[nameEnd] - glyphX[nameStart]).roundToInt()
        var before = 0f
        for (glyph in 0 until nameStart) {
            if (!art[glyph]) {
                before += glyphX[glyph + 1] - glyphX[glyph]
            }
        }
        prefixWidth = before.roundToInt()
    }

    internal fun runString(from: Int, to: Int): String = strings.getOrPut(from * KEY_SPAN + to) {
        val out = StringBuilder(to - from + PREFIX_CAPACITY)
        out.append(SECTION).append(RESET_CHAR)
        val format = formats[from]
        for (bit in 0 until FORMAT_CODES) {
            if (format and (1 shl bit) != 0) {
                out.append(SECTION).append(CODES[COLOR_CODES + bit])
            }
        }
        out.append(chars, from, to - from)
        out.toString()
    }

    internal fun obfuscated(glyph: Int): Boolean = formats[glyph] and OBFUSCATED != 0

    companion object {
        const val SECTION: Char = '\u00A7'
        const val CODES: String = "0123456789abcdefklmnor"
        const val COLOR_CODES: Int = 16
        const val FORMAT_CODES: Int = 5
        const val RESET: Int = 21
        const val WHITE_CODE: Int = 15
        const val OBFUSCATED: Int = 1
        const val BOLD: Int = 2
        private const val RESET_CHAR = 'r'
        private const val KEY_SPAN = 1 shl 16
        private const val PREFIX_CAPACITY = 12
    }
}

class LegacyStyle internal constructor(
    val text: ResolvedText,
    val callRgb: Int,
    val externalShadow: Boolean,
    size: Int,
) {
    val rgb: IntArray = IntArray(size)
    val shadow: IntArray = IntArray(size)
    var changes: Boolean = false
        internal set

    fun sameRun(line: LegacyLine, a: Int, b: Int): Boolean =
        rgb[a] == rgb[b] && shadow[a] == shadow[b] && line.formats[a] == line.formats[b] && line.art[a] == line.art[b]

    companion object {
        const val VANILLA: Int = -1
        const val NONE: Int = -2
    }
}

object LegacyNameplateText {
    private const val RGB = 0xFFFFFF
    private const val ALPHA_SHIFT = 24
    private const val MIN_ALPHA = 4
    private const val CAPACITY = 256
    private const val BACKING_SLOPE = 1f
    private const val BACKING_UNITS = 2f
    private val OUTLINE_X = floatArrayOf(-1f, 1f, 0f, 0f, -1f, 1f, -1f, 1f)
    private val OUTLINE_Y = floatArrayOf(0f, 0f, -1f, 1f, -1f, -1f, 1f, 1f)

    private val lines = object : LinkedHashMap<String, LegacyLine>(CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LegacyLine>?): Boolean = size > CAPACITY
    }

    fun line(font: TextRenderer, text: String, profileName: String?): LegacyLine {
        val cached = lines[text]
        if (cached != null && cached.profileName == profileName && cached.unicode == font.unicode && cached.textWidth == font.getWidth(text)) {
            return cached
        }
        val line = LegacyLine(text, profileName)
        line.parse(font)
        lines[text] = line
        return line
    }

    fun style(line: LegacyLine, text: ResolvedText, callRgb: Int, externalShadow: Boolean): LegacyStyle {
        line.style?.let { cached ->
            if (cached.text === text && cached.callRgb == callRgb && cached.externalShadow == externalShadow) {
                return cached
            }
        }
        val style = LegacyStyle(text, callRgb, externalShadow, line.count)
        val recolors = text.color != ResolvedText.UNSET
        val shadows = when (text.shadowUse) {
            ResolvedText.ShadowUse.LEAVE -> false
            ResolvedText.ShadowUse.ALWAYS, ResolvedText.ShadowUse.REMOVE -> true
            ResolvedText.ShadowUse.WITH_DROP_SHADOW -> externalShadow
        }
        style.changes = recolors || shadows || text.formatting != TextStyling.Formatting.KEEP || text.outline != ResolvedText.UNSET
        for (glyph in 0 until line.count) {
            val own = line.ownRgb[glyph]
            when {
                line.art[glyph] || glyph >= line.nameEnd -> {
                    style.rgb[glyph] = if (own >= 0) own else callRgb
                    style.shadow[glyph] = LegacyStyle.VANILLA
                }
                glyph < line.nameStart -> {
                    style.rgb[glyph] = formatted(own, style)
                    style.shadow[glyph] = LegacyStyle.VANILLA
                }
                else -> {
                    style.rgb[glyph] = when {
                        own >= 0 -> formatted(own, style)
                        recolors -> text.color and RGB
                        else -> callRgb
                    }
                    style.shadow[glyph] = nameShadow(own, text, shadows)
                }
            }
        }
        line.style = style
        return style
    }

    fun drawStyled(font: TextRenderer, line: LegacyLine, style: LegacyStyle, x: Float, y: Float, alpha: Int, pass: LegacyPass, outline: Outline) {
        var from = 0
        while (from < line.count) {
            var to = from + 1
            while (to < line.count && style.sameRun(line, from, to)) {
                to++
            }
            drawRun(font, line, style, from, to, x + line.glyphX[from], y, alpha, pass, outline)
            from = to
        }
    }

    fun drawRun(
        font: TextRenderer,
        line: LegacyLine,
        style: LegacyStyle,
        from: Int,
        to: Int,
        x: Float,
        y: Float,
        alpha: Int,
        pass: LegacyPass,
        outline: Outline,
    ) {
        val decorated = pass == LegacyPass.DEPTH && !line.art[from] && !line.obfuscated(from)
        drawString(font, line.runString(from, to), x, y, alpha, style.rgb[from], style.shadow[from], if (decorated) outline else Outline.NONE, style.externalShadow, decorated)
    }

    fun drawString(
        font: TextRenderer,
        text: String,
        x: Float,
        y: Float,
        alpha: Int,
        rgb: Int,
        shadow: Int,
        outline: Outline,
        externalShadow: Boolean,
        decorated: Boolean,
    ) {
        if (alpha < MIN_ALPHA) {
            return
        }
        val a = alpha shl ALPHA_SHIFT
        if (decorated && (outline.rgb != ResolvedText.UNSET || shadow >= 0)) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
            GL11.glPolygonOffset(BACKING_SLOPE, BACKING_UNITS)
            try {
                if (outline.rgb != ResolvedText.UNSET) {
                    if (outline.full) {
                        for (k in OUTLINE_X.indices) {
                            font.draw(text, x + OUTLINE_X[k], y + OUTLINE_Y[k], a or outline.rgb, false)
                        }
                    } else if (shadow < 0) {
                        font.draw(text, x + 1, y + 1, a or outline.rgb, false)
                    }
                }
                if (shadow >= 0) {
                    font.draw(text, x + 1, y + 1, a or shadow, false)
                }
                LegacyTagBatch.flush()
            } finally {
                GL11.glPolygonOffset(0f, 0f)
                GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
            }
        }
        font.draw(text, x, y, a or rgb, shadow == LegacyStyle.VANILLA && externalShadow)
    }

    fun scaleAlpha(alpha: Int, opacity: Int): Int = (alpha * opacity + CHANNEL_MAX / 2) / CHANNEL_MAX

    private fun formatted(own: Int, style: LegacyStyle): Int {
        if (own < 0) {
            return style.callRgb
        }
        val text = style.text
        return when (text.formatting) {
            TextStyling.Formatting.KEEP -> own
            TextStyling.Formatting.OVERRIDE -> if (text.color != ResolvedText.UNSET) text.color and RGB else style.callRgb
            TextStyling.Formatting.ADJUST -> text.adjust(own) and RGB
        }
    }

    private fun nameShadow(own: Int, text: ResolvedText, shadows: Boolean): Int {
        val adjusted = if (own >= 0 && text.formatting == TextStyling.Formatting.ADJUST && text.shadowUse != ResolvedText.ShadowUse.REMOVE) {
            text.adjustShadow(own)
        } else {
            0
        }
        return when {
            adjusted != 0 -> adjusted and RGB
            !shadows -> LegacyStyle.VANILLA
            text.shadowUse == ResolvedText.ShadowUse.REMOVE -> LegacyStyle.NONE
            else -> text.shadow and RGB
        }
    }

    class Outline(val rgb: Int, val full: Boolean) {
        companion object {
            val NONE: Outline = Outline(ResolvedText.UNSET, false)
        }
    }

    private const val CHANNEL_MAX = 255
    private const val LOAD_FACTOR = 0.75f
}

enum class LegacyPass {
    SEE_THROUGH,
    DEPTH,
}
*///?}
