package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.render.TextRenderer
import net.minecraft.client.render.platform.GlStateManager
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.nameplate.engine.api.anim.TextMotion
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.text.LetterFrame
import org.polyfrost.polyplus.nameplate.engine.api.text.ResolvedText
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.roundToInt

object LegacyNameplateLetters {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")
    private val motions = WeakHashMap<NameplateBundle, TextMotion>()
    private val reported: MutableSet<NameplateBundle> = Collections.newSetFromMap(WeakHashMap())
    private val letters = LetterFrame()
    private val glyphX = IntArray(LetterFrame.MAX_GLYPHS)
    private val glyphWidth = IntArray(LetterFrame.MAX_GLYPHS)

    private const val HALF_HEIGHT = 4f
    private const val RGB = 0xFFFFFF

    private var evaluated: LegacyTag? = null
    private var owned = false

    fun draw(font: TextRenderer, tag: LegacyTag, style: LegacyStyle, x: Float, y: Float, alpha: Int, pass: LegacyPass) {
        evaluate(tag)
        if (owned) {
            drawOwned(font, tag, style, x, y, alpha, pass)
        } else {
            drawRuns(font, tag, style, x, y, alpha, pass)
        }
    }

    private fun evaluate(tag: LegacyTag) {
        if (evaluated === tag) {
            return
        }
        evaluated = tag
        val line = tag.line
        val nameplate = tag.tag.nameplate
        val start = line.nameStart
        val count = line.nameEnd - start
        val motion = motions.getOrPut(nameplate.bundle) { TextMotion.of(nameplate.bundle) }
        val fits = count <= LetterFrame.MAX_GLYPHS
        if (fits) {
            val origin = line.glyphX[start]
            for (i in 0 until count) {
                glyphX[i] = (line.glyphX[start + i] - origin).roundToInt()
                glyphWidth[i] = (line.glyphX[start + i + 1] - line.glyphX[start + i]).roundToInt()
            }
        }
        motion.evaluate(nameplate.bundle, nameplate.variant, tag.block, tag.tag.frame, nameplate.seed, tag.tag.state, if (fits) count else 0, glyphX, glyphWidth, letters)
        if (motion.failed && reported.add(nameplate.bundle)) {
            LOGGER.warn("Nameplate {} letter program ran out of its instruction budget; its letters draw still", nameplate.bundle.id)
        }
        owned = tag.mode == NameplateBundle.TextMode.OWNED && fits && ownedDraws(line) <= LetterFrame.MAX_GLYPHS
    }

    private fun letterOf(line: LegacyLine, glyph: Int): Int {
        val letter = glyph - line.nameStart
        return if (letter in 0 until letters.count && !line.art[glyph]) letter else -1
    }

    private fun sameLetters(a: Int, b: Int): Boolean = when {
        a < 0 && b < 0 -> true
        a < 0 -> isPlain(b)
        b < 0 -> isPlain(a)
        else -> letters.sameRun(a, b)
    }

    private fun isPlain(letter: Int): Boolean =
        letters.dx[letter] == 0 && letters.dy[letter] == 0 && letters.opacity[letter] == LetterFrame.OPAQUE

    private fun drawRuns(font: TextRenderer, tag: LegacyTag, style: LegacyStyle, x: Float, y: Float, alpha: Int, pass: LegacyPass) {
        val line = tag.line
        var from = 0
        while (from < line.count) {
            val first = letterOf(line, from)
            var to = from + 1
            while (to < line.count && style.sameRun(line, from, to) && sameLetters(first, letterOf(line, to))) {
                to++
            }
            val dx = if (first < 0) 0 else letters.dx[first]
            val dy = if (first < 0) 0 else letters.dy[first]
            val opacity = if (first < 0) LetterFrame.OPAQUE else letters.opacity[first]
            if (opacity > 0) {
                val shown = LegacyNameplateText.scaleAlpha(alpha, opacity)
                LegacyNameplateText.drawRun(font, line, style, from, to, x + line.glyphX[from] + dx, y + dy, shown, pass, tag.outline)
            }
            from = to
        }
    }

    private fun ownedDraws(line: LegacyLine): Int {
        var draws = 0
        var inRun = false
        for (glyph in 0 until line.count) {
            if (letterOf(line, glyph) >= 0) {
                draws++
                inRun = false
            } else if (!inRun) {
                draws++
                inRun = true
            }
        }
        return draws
    }

    private fun drawOwned(font: TextRenderer, tag: LegacyTag, style: LegacyStyle, x: Float, y: Float, alpha: Int, pass: LegacyPass) {
        val line = tag.line
        var from = 0
        while (from < line.count) {
            val letter = letterOf(line, from)
            if (letter >= 0) {
                drawLetter(font, tag, style, from, letter, x, y, alpha, pass)
                from++
                continue
            }
            var to = from + 1
            while (to < line.count && letterOf(line, to) < 0 && style.sameRun(line, from, to)) {
                to++
            }
            LegacyNameplateText.drawRun(font, line, style, from, to, x + line.glyphX[from], y, alpha, pass, tag.outline)
            from = to
        }
    }

    private fun drawLetter(font: TextRenderer, tag: LegacyTag, style: LegacyStyle, glyph: Int, letter: Int, x: Float, y: Float, alpha: Int, pass: LegacyPass) {
        val opacity = letters.opacity[letter]
        if (opacity <= 0) {
            return
        }
        val line = tag.line
        val ownColor = letters.color[letter]
        val rgb = if (ownColor == LetterFrame.KEEP) style.rgb[glyph] else ownColor and RGB
        val shadow = if (pass == LegacyPass.SEE_THROUGH) style.shadow[glyph] else when (val own = letters.shadow[letter]) {
            LetterFrame.KEEP -> style.shadow[glyph]
            LetterFrame.OFF -> LegacyStyle.NONE
            else -> own and RGB
        }
        val outline = when (val own = letters.outline[letter]) {
            LetterFrame.KEEP -> tag.outline
            LetterFrame.OFF -> LegacyNameplateText.Outline.NONE
            else -> LegacyNameplateText.Outline(own and RGB, tag.tag.detailed)
        }
        val start = line.glyphX[glyph]
        val half = (line.glyphX[glyph + 1] - start) / 2f
        GlStateManager.pushMatrix()
        try {
            GlStateManager.translatef(x + start + letters.dx[letter] + half, y + letters.dy[letter] + HALF_HEIGHT, 0f)
            val rotation = letters.rotation[letter]
            if (rotation != 0) {
                GlStateManager.rotatef(rotation.toFloat(), 0f, 0f, 1f)
            }
            val scale = letters.scaleOf(letter)
            if (scale != 1f) {
                GlStateManager.scalef(scale, scale, 1f)
            }
            val decorated = pass == LegacyPass.DEPTH && !line.obfuscated(glyph)
            LegacyNameplateText.drawString(
                font,
                line.runString(glyph, glyph + 1),
                -half,
                -HALF_HEIGHT,
                LegacyNameplateText.scaleAlpha(alpha, opacity),
                rgb,
                shadow,
                if (decorated) outline else LegacyNameplateText.Outline.NONE,
                style.externalShadow,
                decorated,
            )
        } finally {
            GlStateManager.popMatrix()
        }
    }

    fun outlineOf(text: ResolvedText, detailed: Boolean): LegacyNameplateText.Outline =
        if (text.outline == ResolvedText.UNSET) LegacyNameplateText.Outline.NONE else LegacyNameplateText.Outline(text.outline and RGB, detailed)
}
*///?}
