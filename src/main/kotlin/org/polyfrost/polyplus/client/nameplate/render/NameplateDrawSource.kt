package org.polyfrost.polyplus.client.nameplate.render

import org.polyfrost.plusrenderer.Nameplates
import org.polyfrost.polyplus.client.cosmetics.signals.PlayerSignalRecord
import org.polyfrost.polyplus.client.nameplate.NameplateEquip
import org.polyfrost.polyplus.client.nameplate.engine.EngineNameplateDrawSource
import org.polyfrost.polyplus.client.nameplate.material.NameplatePrograms
import java.util.UUID

interface NameplateDrawSource {
    fun prepare(tag: NameplateDrawTag, out: NameplateDrawList): Boolean

    fun measured(tag: NameplateDrawTag)

    fun draw(tag: NameplateDrawTag, out: NameplateDrawList)

    fun peek(tag: NameplateDrawTag, out: NameplateDrawList): Boolean

    fun badgePlacement(equip: NameplateEquip): Int

    fun flush()

    fun endFrame()
}

object NameplateDrawSources {
    @Volatile
    @JvmStatic
    var current: NameplateDrawSource = EngineNameplateDrawSource
}

class NameplateDrawTag {
    var wearer: UUID = NO_WEARER
    var profileName: String = ""
    var equip: NameplateEquip? = null
    var flags: Int = 0
    var deferred: Boolean = false
    var textAlpha: Int = 255
    var distanceSq: Double = 0.0
    var signals: PlayerSignalRecord? = null
    var lineWidth: Int = 0
    var backgroundX0: Int = 0
    var backgroundY0: Int = 0
    var backgroundX1: Int = 0
    var backgroundY1: Int = 0
    var nameX: Int = 0
    var nameWidth: Int = 0
    var prefixWidth: Int = 0
    var lineCount: Int = 1
    var nameLine: Int = 0
    var align: Int = Nameplates.ALIGN_CENTER
    val lineWidths: IntArray = IntArray(Nameplates.MAX_LINES)
    var externalColor: Int = NO_EXTERNAL
    var badgeShown: Boolean = false
    var badgeGapX: Int = NO_GAP

    var glyphs: FloatArray = FloatArray(Nameplates.GLYPH_FLOATS * MAX_GLYPHS)
        private set
    var glyphCount: Int = 0
        private set

    val colors: IntArray = IntArray(MAX_COLORS)
    var colorCount: Int = 0
        private set

    fun hasFlag(flag: Int): Boolean = flags and flag != 0

    fun clearMeasure() {
        glyphCount = 0
        colorCount = 0
        lineWidth = 0
        nameX = 0
        nameWidth = 0
        prefixWidth = 0
        lineCount = 1
        nameLine = 0
        align = Nameplates.ALIGN_CENTER
        lineWidths.fill(0)
        externalColor = NO_EXTERNAL
        badgeGapX = NO_GAP
    }

    fun glyph(x: Float, width: Float) {
        if (glyphCount == MAX_GLYPHS) return
        val at = glyphCount * Nameplates.GLYPH_FLOATS
        glyphs[at + Nameplates.GLYPH_X] = x
        glyphs[at + Nameplates.GLYPH_WIDTH] = width
        glyphCount++
    }

    fun color(rgb: Int) {
        val argb = rgb or OPAQUE
        for (i in 0 until colorCount) if (colors[i] == argb) return
        if (colorCount == MAX_COLORS) return
        colors[colorCount++] = argb
    }

    companion object {
        const val FLAG_DISPLAY: Int = Nameplates.FLAG_DISPLAY
        const val FLAG_OWN: Int = Nameplates.FLAG_OWN
        const val FLAG_ANIMATED_NAMES_OFF: Int = Nameplates.FLAG_ANIMATED_NAMES_OFF
        const val FLAG_EFFECTS_CPU: Int = Nameplates.FLAG_EFFECTS_CPU
        const val FLAG_EFFECTS_OFF: Int = Nameplates.FLAG_EFFECTS_OFF
        const val FLAG_BADGE: Int = Nameplates.FLAG_BADGE

        const val MAX_GLYPHS: Int = 64
        const val MAX_COLORS: Int = 16
        const val NO_EXTERNAL: Int = -1
        const val NO_GAP: Int = Int.MIN_VALUE

        const val BADGE_NONE: Int = -1

        private const val OPAQUE = 0xFF shl 24
        private val NO_WEARER = UUID(0L, 0L)
    }
}

class NameplateDrawList {
    var plateDx: Int = 0
    var plateDy: Int = 0
    var ownsBackground: Boolean = false
    var textMode: Int = TEXT_MODE_VANILLA
    var restyle: Boolean = false

    var ops: IntArray = IntArray(OP_WORDS * 16)
        private set
    var opCount: Int = 0
        private set

    var images: IntArray = IntArray(IMAGE_WORDS * 4)
        private set
    var imageCount: Int = 0
        private set

    var runs: IntArray = IntArray(RUN_WORDS * 4)
        private set
    var runCount: Int = 0
        private set
    private var runOpen = false

    var textColor: Int = 0
    var textShadow: Int = 0
    var textShadowUse: Int = Nameplates.SHADOW_LEAVE
    var textOutline: Int = 0
    var baseShadow: Boolean = false

    var letters: IntArray = IntArray(LETTER_WORDS * Nameplates.MAX_LETTERS)
        private set
    var letterCount: Int = 0
        private set

    var materials: IntArray = IntArray(MATERIAL_WORDS * 2)
        private set
    var materialUv: FloatArray = FloatArray(MATERIAL_UV_FLOATS * 2)
        private set
    var materialPrograms: Array<NameplatePrograms?> = arrayOfNulls(2)
        private set
    var materialCount: Int = 0
        private set
    var materialWords: IntArray = IntArray(256)
        private set
    private var materialWordCount = 0
    var clips: IntArray = IntArray(16)
        private set
    private var clipCount = 0

    private val colorsIn = IntArray(NameplateDrawTag.MAX_COLORS)
    private val colorsOut = IntArray(NameplateDrawTag.MAX_COLORS)
    private var colorCount = 0

    fun reset() {
        plateDx = 0
        plateDy = 0
        ownsBackground = false
        textMode = TEXT_MODE_VANILLA
        restyle = false
        clearDrawing()
    }

    fun clearDrawing() {
        opCount = 0
        imageCount = 0
        runCount = 0
        runOpen = false
        for (i in 0 until materialCount) materialPrograms[i] = null
        materialCount = 0
        materialWordCount = 0
        clipCount = 0
        letterCount = 0
        colorCount = 0
        textColor = 0
        textShadow = 0
        textShadowUse = Nameplates.SHADOW_LEAVE
        textOutline = 0
        baseShadow = false
    }

    fun rect(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int, bandLayer: Int) {
        if (x1 <= x0 || y1 <= y0 || argb ushr 24 == 0) return
        val at = opCount * OP_WORDS
        if (at + OP_WORDS > ops.size) ops = ops.copyOf(ops.size * 2)
        ops[at + OP_X0] = x0
        ops[at + OP_Y0] = y0
        ops[at + OP_X1] = x1
        ops[at + OP_Y1] = y1
        ops[at + OP_ARGB] = argb
        ops[at + OP_BAND_LAYER] = bandLayer and 0xFFFF
        extendRun(RUN_RECT, opCount)
        opCount++
    }

    fun image(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int, bandLayer: Int, u0: Int, v0: Int, u1: Int, v1: Int, additive: Boolean) {
        image(x0, y0, x1, y1, argb, bandLayer, u0, v0, u1, v1, if (additive) IMAGE_ADDITIVE else 0)
    }

    fun image(x0: Int, y0: Int, x1: Int, y1: Int, argb: Int, bandLayer: Int, u0: Int, v0: Int, u1: Int, v1: Int, flags: Int) {
        if (x1 <= x0 || y1 <= y0 || argb ushr 24 == 0) return
        val at = imageCount * IMAGE_WORDS
        if (at + IMAGE_WORDS > images.size) images = images.copyOf(images.size * 2)
        images[at + OP_X0] = x0
        images[at + OP_Y0] = y0
        images[at + OP_X1] = x1
        images[at + OP_Y1] = y1
        images[at + OP_ARGB] = argb
        images[at + OP_BAND_LAYER] = bandLayer and 0xFFFF
        images[at + IMAGE_U0] = u0
        images[at + IMAGE_V0] = v0
        images[at + IMAGE_U1] = u1
        images[at + IMAGE_V1] = v1
        images[at + IMAGE_FLAGS] = flags
        extendRun(if (flags and IMAGE_ADDITIVE != 0) RUN_ADDITIVE else RUN_IMAGE, imageCount)
        imageCount++
    }

    private fun extendRun(kind: Int, index: Int) {
        val last = (runCount - 1) * RUN_WORDS
        if (runOpen && runs[last + RUN_KIND] == kind) {
            runs[last + RUN_TO] = index + 1
            return
        }
        val at = runCount * RUN_WORDS
        if (at + RUN_WORDS > runs.size) runs = runs.copyOf(runs.size * 2)
        runs[at + RUN_KIND] = kind
        runs[at + RUN_FROM] = index
        runs[at + RUN_TO] = index + 1
        runCount++
        runOpen = true
    }

    fun runKind(run: Int): Int = runs[run * RUN_WORDS + RUN_KIND]

    fun runFrom(run: Int): Int = runs[run * RUN_WORDS + RUN_FROM]

    fun runTo(run: Int): Int = runs[run * RUN_WORDS + RUN_TO]

    fun material(
        programs: NameplatePrograms,
        material: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        word: Int,
        source: IntArray,
        clipsAt: Int,
        clipCount: Int,
        uniformsAt: Int,
        uniformWords: Int,
    ) {
        if (x1 <= x0 || y1 <= y0 || uniformWords <= 0) return
        val opacity = (word ushr Nameplates.MAT_OPACITY_SHIFT) and 0xFF
        if (opacity == 0) return
        val index = materialCount
        if ((index + 1) * MATERIAL_WORDS > materials.size) {
            materials = materials.copyOf(materials.size * 2)
            materialUv = materialUv.copyOf(materialUv.size * 2)
            materialPrograms = materialPrograms.copyOf(materialPrograms.size * 2)
        }
        val clipWords = clipCount * Nameplates.CLIP_WORDS
        if (this.clipCount + clipWords > clips.size) clips = clips.copyOf(maxOf(clips.size * 2, this.clipCount + clipWords))
        if (materialWordCount + uniformWords > materialWords.size) {
            materialWords = materialWords.copyOf(maxOf(materialWords.size * 2, materialWordCount + uniformWords))
        }
        val at = index * MATERIAL_WORDS
        materials[at + MATERIAL_INDEX] = material
        materials[at + MATERIAL_X0] = x0
        materials[at + MATERIAL_Y0] = y0
        materials[at + MATERIAL_X1] = x1
        materials[at + MATERIAL_Y1] = y1
        materials[at + MATERIAL_BAND_LAYER] = word and 0xFFFF
        materials[at + MATERIAL_OPACITY] = opacity
        materials[at + MATERIAL_MIRRORED] = if (word and Nameplates.MAT_MIRRORED != 0) 1 else 0
        materials[at + MATERIAL_CLIP_AT] = this.clipCount
        materials[at + MATERIAL_CLIP_COUNT] = clipCount
        materials[at + MATERIAL_UNIFORM_AT] = materialWordCount
        materials[at + MATERIAL_UNIFORM_WORDS] = uniformWords
        materials[at + MATERIAL_RUN_AT] = runCount
        runOpen = false
        val uv = index * MATERIAL_UV_FLOATS
        materialUv[uv] = u0
        materialUv[uv + 1] = v0
        materialUv[uv + 2] = u1
        materialUv[uv + 3] = v1
        materialPrograms[index] = programs
        System.arraycopy(source, clipsAt, clips, this.clipCount, clipWords)
        this.clipCount += clipWords
        System.arraycopy(source, uniformsAt, materialWords, materialWordCount, uniformWords)
        materialWordCount += uniformWords
        materialCount++
    }

    fun materialWord(material: Int, field: Int): Int = materials[material * MATERIAL_WORDS + field]

    fun letters(source: IntArray, at: Int, count: Int) {
        val words = count * LETTER_WORDS
        if (letters.size < words) letters = IntArray(words)
        System.arraycopy(source, at, letters, 0, words)
        letterCount = count
    }

    fun adjust(input: IntArray, output: IntArray, at: Int, count: Int) {
        val n = minOf(count, colorsIn.size)
        for (i in 0 until n) {
            colorsIn[i] = input[i] and RGB
            colorsOut[i] = output[at + i] and RGB
        }
        colorCount = n
    }

    fun adjusted(rgb: Int): Int {
        val key = rgb and RGB
        for (i in 0 until colorCount) if (colorsIn[i] == key) return colorsOut[i]
        return key
    }

    fun glyphRgb(styled: Boolean, rgb: Int): Int {
        if (!styled && textColor != 0) return textColor and RGB
        return adjusted(rgb)
    }

    fun shadow(letter: Int, rgb: Int): Int {
        val own = if (letter in 0 until letterCount) letters[letter * LETTER_WORDS + LETTER_SHADOW] else LETTER_KEEP
        return when (own) {
            LETTER_KEEP -> when (textShadowUse) {
                Nameplates.SHADOW_ALWAYS -> if (textShadow != 0) textShadow or OPAQUE else 0
                Nameplates.SHADOW_WITH_DROP -> if (baseShadow && textShadow != 0) textShadow or OPAQUE else 0
                Nameplates.SHADOW_REMOVE -> 0
                else -> if (baseShadow) ((rgb and VANILLA_SHADOW_MASK) shr 2) or OPAQUE else 0
            }
            LETTER_OFF -> 0
            else -> own or OPAQUE
        }
    }

    fun plainShadow(rgb: Int): Int = if (baseShadow) ((rgb and VANILLA_SHADOW_MASK) shr 2) or OPAQUE else 0

    fun outline(letter: Int): Int {
        val own = if (letter in 0 until letterCount) letters[letter * LETTER_WORDS + LETTER_OUTLINE] else LETTER_KEEP
        return when (own) {
            LETTER_KEEP -> if (textOutline != 0) textOutline or OPAQUE else 0
            LETTER_OFF -> 0
            else -> own or OPAQUE
        }
    }

    fun band(op: Int): Int = ops[op * OP_WORDS + OP_BAND_LAYER] and 0xFF

    fun layer(op: Int): Int = (ops[op * OP_WORDS + OP_BAND_LAYER] shr 8) and 0xFF

    companion object {
        const val TEXT_MODE_VANILLA: Int = Nameplates.TEXT_MODE_VANILLA
        const val TEXT_MODE_MOTION: Int = Nameplates.TEXT_MODE_MOTION
        const val TEXT_MODE_OWNED: Int = Nameplates.TEXT_MODE_OWNED

        const val OP_WORDS: Int = 6
        const val OP_X0: Int = 0
        const val OP_Y0: Int = 1
        const val OP_X1: Int = 2
        const val OP_Y1: Int = 3
        const val OP_ARGB: Int = 4
        const val OP_BAND_LAYER: Int = 5

        const val IMAGE_WORDS: Int = 11
        const val IMAGE_U0: Int = 6
        const val IMAGE_V0: Int = 7
        const val IMAGE_U1: Int = 8
        const val IMAGE_V1: Int = 9
        const val IMAGE_FLAGS: Int = 10
        const val IMAGE_ADDITIVE: Int = 1
        const val IMAGE_SOLID: Int = 2

        const val RUN_WORDS: Int = 3
        const val RUN_KIND: Int = 0
        const val RUN_FROM: Int = 1
        const val RUN_TO: Int = 2
        const val RUN_RECT: Int = 0
        const val RUN_IMAGE: Int = 1
        const val RUN_ADDITIVE: Int = 2

        const val MATERIAL_WORDS: Int = 13
        const val MATERIAL_INDEX: Int = 0
        const val MATERIAL_X0: Int = 1
        const val MATERIAL_Y0: Int = 2
        const val MATERIAL_X1: Int = 3
        const val MATERIAL_Y1: Int = 4
        const val MATERIAL_BAND_LAYER: Int = 5
        const val MATERIAL_OPACITY: Int = 6
        const val MATERIAL_MIRRORED: Int = 7
        const val MATERIAL_CLIP_AT: Int = 8
        const val MATERIAL_CLIP_COUNT: Int = 9
        const val MATERIAL_UNIFORM_AT: Int = 10
        const val MATERIAL_UNIFORM_WORDS: Int = 11
        const val MATERIAL_RUN_AT: Int = 12
        const val MATERIAL_UV_FLOATS: Int = 4

        const val BAND_BEHIND: Int = Nameplates.BAND_BEHIND
        const val BAND_BACKGROUND: Int = Nameplates.BAND_BACKGROUND
        const val BAND_OVER_BACKGROUND: Int = Nameplates.BAND_OVER_BACKGROUND
        const val BAND_OVER_TEXT: Int = Nameplates.BAND_OVER_TEXT
        const val BAND_BADGE: Int = Nameplates.BAND_BADGE
        const val MAX_LAYER: Int = Nameplates.MAX_LAYERS - 1
        const val NO_STYLE: Int = -1

        const val LETTER_WORDS: Int = Nameplates.LETTER_WORDS
        const val LETTER_DX: Int = Nameplates.LETTER_DX
        const val LETTER_DY: Int = Nameplates.LETTER_DY
        const val LETTER_ARGB: Int = Nameplates.LETTER_ARGB
        const val LETTER_SHADOW: Int = Nameplates.LETTER_SHADOW
        const val LETTER_OPACITY: Int = Nameplates.LETTER_OPACITY
        const val LETTER_SCALE_ROT: Int = Nameplates.LETTER_SCALE_ROT
        const val LETTER_OUTLINE: Int = Nameplates.LETTER_OUTLINE

        const val LETTER_KEEP: Int = 0
        const val LETTER_OFF: Int = 1
        const val SCALE_ONE: Int = 4
        const val GLYPH_HEIGHT: Int = Nameplates.GLYPH_HEIGHT

        private const val RGB = 0xFFFFFF
        private const val OPAQUE = 0xFF shl 24
        private const val VANILLA_SHADOW_MASK = 0xFCFCFC
        private const val PORTRAIT_RED = 1
        private const val NO_RED = 0xFF00FFFF.toInt()
        private const val SHADOW_PORTRAIT_RED_LOW = 4
        private const val SHADOW_PORTRAIT_RED_HIGH = 7
        private const val SHADOW_SAFE_RED = 3

        @JvmStatic
        fun guard(argb: Int): Int = if ((argb shr 16) and 0xFF == PORTRAIT_RED) argb and NO_RED else argb

        @JvmStatic
        fun guardShadowed(argb: Int): Int {
            val red = (argb shr 16) and 0xFF
            if (red in SHADOW_PORTRAIT_RED_LOW..SHADOW_PORTRAIT_RED_HIGH) return (argb and NO_RED) or (SHADOW_SAFE_RED shl 16)
            return guard(argb)
        }

        @JvmStatic
        fun scaleQuarters(word: Int): Int = (word and 0xFF).let { if (it == 0) SCALE_ONE else it }

        @JvmStatic
        fun rotationDegrees(word: Int): Int = word shr 8

        @JvmStatic
        fun letterAlpha(textAlpha: Int, opacity: Int): Int = (textAlpha * opacity.coerceIn(0, 255) + 127) / 255

        @JvmStatic
        fun imageColor(flags: Int, argb: Int, alpha: Float): Int = if (flags and IMAGE_SOLID != 0) argb else fade(argb, alpha)

        @JvmStatic
        fun fade(argb: Int, alpha: Float): Int {
            if (alpha >= 1f) return argb
            val a = ((argb ushr 24) * alpha + 0.5f).toInt().coerceIn(0, 255)
            return (a shl 24) or (argb and RGB)
        }
    }
}
