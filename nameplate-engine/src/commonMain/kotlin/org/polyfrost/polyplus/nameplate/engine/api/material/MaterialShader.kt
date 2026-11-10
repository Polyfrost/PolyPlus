package org.polyfrost.polyplus.nameplate.engine.api.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.FlatUniform
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

class ShaderLayout internal constructor(
    toneSlots: IntArray,
    internal val inputs: List<MaterialInput>,
    internal val textures: List<TextureRef>,
) {
    private val slots = toneSlots.copyOf()

    val tones: Int
        get() = maxOf(1, slots.size) * Palette.MAX_TONES

    internal fun toneSlots(): IntArray = slots.copyOf()
}

class MaterialShader internal constructor(
    val dialect: GlslDialect,
    val vertex: String,
    val fragment: String,
    val include: String,
    val key: String,
    val layout: ShaderLayout,
    val samplers: List<String>,
    val uniforms: List<FlatUniform>,
)
