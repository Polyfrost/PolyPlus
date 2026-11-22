@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.polyfrost.polyplus.nameplate.engine.api.anim.reactedPalette
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialGlsl
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialShader
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialUniforms
import org.polyfrost.polyplus.nameplate.engine.api.paint.Detail
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.paint.MaterialOp
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslGlobals
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslSamplers
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialClock
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput

private const val ES_300 = "es300"
private const val NUMBERED = "numbered"
private const val SCREEN_FIRST = "screen"
private const val LANES = 4
private const val INPUT_VECTORS = 4
private const val ATLAS_VECTORS = 4

private fun dialectName(dialect: GlslDialect): String = dialect.name.replace("_", "").lowercase()

private fun dialectOf(name: String): GlslDialect =
    GlslDialect.entries.firstOrNull { dialectName(it) == name.lowercase() } ?: GlslDialect.ES_300

private fun samplersOf(name: String, dialect: GlslDialect): GlslSamplers = when (name.lowercase()) {
    NUMBERED -> GlslSamplers.NUMBERED
    "" -> dialect.defaultSamplers
    else -> GlslSamplers.NAMED
}

private fun globalsOf(name: String): GlslGlobals =
    if (name.equals(SCREEN_FIRST, ignoreCase = true)) GlslGlobals.SCREEN_FIRST else GlslGlobals.CAMERA_FIRST

private fun rectOf(rect: PixelRect): IntArray = intArrayOf(rect.x0, rect.y0, rect.x1, rect.y1)

private fun lower(value: Enum<*>): String = value.name.lowercase()

private fun texturePath(ref: TextureRef): String = when (ref) {
    is TextureRef.Bundle -> "textures/${ref.id}.png"
    is TextureRef.Vanilla -> "vanilla/${ref.name}"
    is TextureRef.Atlas -> "atlas/${ref.sprite}"
}

@JsExport
class NameplateShaderTexture internal constructor(
    private val ref: TextureRef,
    val unit: Int,
    val sampler: String = GlslSamplers.NAMED.name(unit),
) {
    val kind: String
        get() = when (ref) {
            is TextureRef.Bundle -> "bundle"
            is TextureRef.Vanilla -> "vanilla"
            is TextureRef.Atlas -> "atlas"
        }

    val id: String
        get() = when (ref) {
            is TextureRef.Bundle -> ref.id
            is TextureRef.Vanilla -> ref.name
            is TextureRef.Atlas -> ref.sprite
        }

    val path: String
        get() = texturePath(ref)
}

@JsExport
class NameplateShaderSource internal constructor(private val material: Material, private val shader: MaterialShader) {
    val key: String
        get() = shader.key

    val materialId: String
        get() = material.id

    val dialect: String
        get() = dialectName(shader.dialect)

    val vertex: String
        get() = shader.vertex

    val fragment: String
        get() = shader.fragment

    val include: String
        get() = shader.include

    val resolution: String
        get() = lower(material.resolution)

    val blend: String
        get() = lower(material.blend)

    val emissive: Boolean
        get() = material.emissive

    val outline: Boolean
        get() = material.outline != null

    val tones: Int
        get() = shader.layout.tones

    val toneSlots: IntArray
        get() = shader.layout.toneSlots()

    val inputs: Array<String>
        get() = shader.layout.inputs.map { input ->
            when (input) {
                is MaterialInput.Param -> input.name
                is MaterialInput.Expression -> input.source
                is MaterialInput.BodyEdgeInput -> "\$edge${input.lane}"
            }
        }.toTypedArray()

    val textures: Array<NameplateShaderTexture>
        get() = shader.layout.textures.mapIndexed { unit, ref -> NameplateShaderTexture(ref, unit, shader.samplers[unit]) }.toTypedArray()

    val uniforms: Array<NameplateUniform>
        get() = if (shader.dialect == GlslDialect.LEGACY_FLAT) {
            shader.uniforms.map { NameplateUniform(it.name, it.type, it.offset, 1) }.toTypedArray()
        } else {
            nameplateUniformLayout(shader.layout.tones)
        }
}

@JsExport
class NameplateUniform internal constructor(
    val name: String,
    val type: String,
    val offset: Int,
    val count: Int,
)

@JsExport
fun nameplateUniformLayout(tones: Int): Array<NameplateUniform> = arrayOf(
    NameplateUniform("AreaRect", "vec4", MaterialUniforms.AREA, 1),
    NameplateUniform("BodyRect", "vec4", MaterialUniforms.BODY, 1),
    NameplateUniform("NameplateClock", "ivec4", MaterialUniforms.CLOCK, 1),
    NameplateUniform("Inputs", "vec4", MaterialUniforms.INPUTS, INPUT_VECTORS),
    NameplateUniform("AtlasRects", "vec4", MaterialUniforms.ATLAS, ATLAS_VECTORS),
    NameplateUniform("Tones", "vec4", MaterialUniforms.TONES, tones),
)

@JsExport
fun nameplateUniformWords(tones: Int): Int = MaterialUniforms.TONES + tones * LANES

@JsExport
fun nameplateShaderAttributes(): Array<String> = arrayOf("Position", "Color", "UV0")

@JsExport
fun nameplateShaderGlobals(): Array<NameplateUniform> = arrayOf(
    NameplateUniform("ProjMat", "mat4", -1, 1),
    NameplateUniform("ModelViewMat", "mat4", -1, 1),
    NameplateUniform("ScreenSize", "vec2", -1, 1),
    NameplateUniform("ColorModulator", "vec4", -1, 1),
    NameplateUniform("NameplateLight", "vec4", -1, 1),
)

@JsExport
fun nameplateMaterialClock(frame: Int, step: Int, loop: Int): Int = MaterialClock.state(frame, step, loop)

@JsExport
fun nameplateMaterialSeconds(clock: Int, fps: Int, step: Int): Double = if (fps <= 0) 0.0 else clock.toDouble() * step / fps

@JsExport
class NameplateMaterialDraw internal constructor(
    private val op: LayerPainter.Painted,
    private val material: MaterialOp,
    private val palette: ResolvedPalette,
    private val shader: MaterialShader,
) {
    val index: Int
        get() = op.index

    val layer: String
        get() = material.layerId

    val key: String
        get() = shader.key

    val depth: String
        get() = lower(op.depth)

    val x: Int
        get() = op.x

    val y: Int
        get() = op.y

    val width: Int
        get() = op.width

    val mirrorX: Boolean
        get() = op.mirrorX

    val opacity: Int
        get() = op.opacity

    val area: IntArray
        get() = rectOf(material.area)

    val body: IntArray
        get() = rectOf(material.body)

    val clock: Int
        get() = material.clock

    val cycle: Int
        get() = material.cycle

    val fps: Int
        get() = material.frame.fps

    val step: Int
        get() = material.frame.step

    val seconds: Double
        get() = nameplateMaterialSeconds(material.clock, material.frame.fps, material.frame.step)

    val blend: String
        get() = lower(material.material.blend)

    val resolution: String
        get() = lower(material.material.resolution)

    val emissive: Boolean
        get() = material.material.emissive

    val fallbackStill: Boolean
        get() = material.fallback.still

    val clip: IntArray?
        get() {
            val rect = op.clip ?: return if (material.clip == null) null else material.clipRects()
            val area = material.area
            val x0 = maxOf(area.x0, rect.x0)
            val y0 = maxOf(area.y0, rect.y0)
            val x1 = minOf(area.x1, rect.x1)
            val y1 = minOf(area.y1, rect.y1)
            return if (x1 > x0 && y1 > y0) intArrayOf(x0, y0, x1, y1) else IntArray(0)
        }

    val quads: Int
        get() = if (op.clip == null) material.quads else (clip?.size ?: 0) / MaterialOp.CLIP_STRIDE

    val uniforms: IntArray
        get() = material.uniforms(palette, null)

    fun source(): NameplateShaderSource = NameplateShaderSource(material.material, shader)
}

@JsExport
class NameplateShaders(
    private val preview: NameplatePreview,
    dialect: String = ES_300,
    samplers: String = "",
    globals: String = "",
) {
    private val glsl = dialectOf(dialect)
    private val naming = samplersOf(samplers, glsl)
    private val blocks = globalsOf(globals)
    private val shaders = HashMap<Material, MaterialShader>()
    private val painter = LayerPainter(PixelGrid(), materialFallbacks = false)

    val count: Int
        get() = preview.bundle.materialLayers.size

    fun sources(): Array<NameplateShaderSource> =
        preview.bundle.materialLayers
            .map { (_, layer) -> source(layer) }
            .distinctBy { it.key }
            .toTypedArray()

    fun source(key: String): NameplateShaderSource? =
        preview.bundle.materialLayers.firstOrNull { (_, layer) -> shader(layer.material).key == key }?.let { (_, layer) -> source(layer) }

    fun draws(
        variant: String,
        block: NameplateBlock,
        frame: Int,
        seed: Int,
        state: NameplateState? = null,
        detail: String = "full",
    ): Array<NameplateMaterialDraw> {
        val bundle = preview.bundle
        if (bundle.materialLayers.isEmpty()) return emptyArray()
        val resolved = bundle.variant(variant)
        val tag = state.tag
        val level = Detail.named(detail) ?: Detail.FULL
        val palette = reactedPalette(bundle, resolved, painter.palette(bundle, resolved, frame, seed, block.block), frame, seed, tag)
        return painter.paint(bundle, resolved, block.block, frame, seed, tag, level)
            .mapNotNull { op -> op.material?.let { NameplateMaterialDraw(op, it, palette, shader(it.material)) } }
            .toTypedArray()
    }

    private fun shader(material: Material): MaterialShader =
        shaders.getOrPut(material) { MaterialGlsl.generate(material, glsl, naming, blocks) }

    private fun source(layer: MaterialLayer): NameplateShaderSource = NameplateShaderSource(layer.material, shader(layer.material))
}

@JsExport
fun nameplateTextureRefs(preview: NameplatePreview): Array<NameplateShaderTexture> =
    preview.bundle.materialLayers
        .flatMap { (_, layer) -> layer.material.textures }
        .distinct()
        .mapIndexed { unit, ref -> NameplateShaderTexture(ref, unit) }
        .toTypedArray()

@JsExport
fun nameplateWithTextures(preview: NameplatePreview, pixels: Array<NameplatePixels>): NameplatePreview {
    val textures = pixels.mapNotNull { entry -> entry.toTexturePixels()?.let { entry.path to it } }.toMap()
    val detail = preview.detail
    return NameplatePreview(preview.bundle, MaterialTextures { ref -> textures[texturePath(ref)] }).also { it.detail = detail }
}
