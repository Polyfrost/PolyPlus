package org.polyfrost.polyplus.nameplate.engine.api.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.FlatUniform
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.internal.material.GlslTemplate
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

object MaterialUniforms {
    const val AREA = 0
    const val BODY = 4
    const val CLOCK = 8
    const val INPUTS = 12
    const val ATLAS = 28
    const val TONES = 44

    private const val LANES = 4
    private const val ALPHA = 24
    private const val RED = 16
    private const val GREEN = 8
    private const val BYTE = 0xFF
    private const val SCALE = 255f

    internal fun std140(material: Material, layout: ShaderLayout, frame: MaterialFrame, cycle: Int, atlas: FloatArray? = null): IntArray {
        val words = IntArray(TONES + layout.tones * LANES)
        val area = material.area(frame.body)
        floats(words, AREA, area.x0 - frame.body.x0, area.y0 - frame.body.y0, area.x1 - frame.body.x0, area.y1 - frame.body.y0)
        floats(words, BODY, frame.body.x0, frame.body.y0, frame.body.x1, frame.body.y1)
        words[CLOCK] = frame.frame
        words[CLOCK + 1] = cycle
        words[CLOCK + 2] = frame.fps
        words[CLOCK + LANES - 1] = frame.step
        for ((k, input) in layout.inputs.withIndex()) {
            words[INPUTS + k] = input.value(frame).toFloat().toRawBits()
        }
        atlas?.forEachIndexed { i, value -> words[ATLAS + i] = value.toRawBits() }
        for ((s, slot) in layout.toneSlots().withIndex()) {
            for (tone in 0 until Palette.MAX_TONES) {
                val argb = frame.palette.color(slot * Palette.MAX_TONES + tone)
                val at = TONES + (s * Palette.MAX_TONES + tone) * LANES
                words[at] = (((argb ushr RED) and BYTE) / SCALE).toRawBits()
                words[at + 1] = (((argb ushr GREEN) and BYTE) / SCALE).toRawBits()
                words[at + 2] = ((argb and BYTE) / SCALE).toRawBits()
                words[at + LANES - 1] = (((argb ushr ALPHA) and BYTE) / SCALE).toRawBits()
            }
        }
        return words
    }

    fun flat(layout: ShaderLayout): List<FlatUniform> = buildList {
        add(FlatUniform("AreaRect", FlatUniform.VEC4, AREA))
        add(FlatUniform("BodyRect", FlatUniform.VEC4, BODY))
        for ((i, name) in GlslTemplate.CLOCK_NAMES.withIndex()) {
            add(FlatUniform(name, FlatUniform.INT, CLOCK + i))
        }
        for (v in 0 until (layout.inputs.size + LANES - 1) / LANES) {
            add(FlatUniform("${GlslTemplate.INPUT_PREFIX}$v", FlatUniform.VEC4, INPUTS + v * LANES))
        }
        for ((k, ref) in layout.textures.withIndex()) {
            if (ref is TextureRef.Atlas) {
                add(FlatUniform("${GlslTemplate.ATLAS_PREFIX}$k", FlatUniform.VEC4, ATLAS + k * LANES))
            }
        }
        for (t in 0 until layout.tones) {
            add(FlatUniform("${GlslTemplate.TONE_PREFIX}$t", FlatUniform.VEC4, TONES + t * LANES))
        }
    }

    private fun floats(words: IntArray, at: Int, vararg values: Int) {
        for ((i, value) in values.withIndex()) {
            words[at + i] = value.toFloat().toRawBits()
        }
    }
}
