package org.polyfrost.polyplus.nameplate.engine.api.material

import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslDialect
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslGlobals
import org.polyfrost.polyplus.nameplate.engine.api.shader.GlslSamplers
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.internal.material.GlslContext
import org.polyfrost.polyplus.nameplate.engine.internal.material.GlslTemplate
import org.polyfrost.polyplus.nameplate.engine.internal.material.GraphBlend
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialGraph
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialResolution
import org.polyfrost.polyplus.nameplate.engine.internal.material.RampKind
import org.polyfrost.polyplus.nameplate.engine.internal.material.SlotKind
import org.polyfrost.polyplus.nameplate.engine.internal.material.SpriteKind
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

object MaterialGlsl {
    const val INCLUDE = GlslTemplate.INCLUDE

    private const val HEX = 16
    private const val HEX_DIGITS = 8
    private val SAMPLER = Regex("\\bNameplateSampler(\\d+)\\b")

    fun generate(
        material: Material,
        dialect: GlslDialect,
        samplers: GlslSamplers = dialect.defaultSamplers,
        globals: GlslGlobals = GlslGlobals.CAMERA_FIRST,
    ): MaterialShader {
        val spec = material.spec
        val graph = spec.graph
        val toneSlots = toneSlots(spec.outline, graph)
        val g = GlslContext(toneSlots)
        val body = g.statements(graph.output, skipRoot = true)
        val color = g.arg(graph.output.inputs[0]).let { if (graph.outputsSlots) "nTone($it)" else it }
        val coverage = g.arg(graph.output.inputs[1])
        val defines = buildList {
            add("NAMEPLATE_TONES ${maxOf(1, toneSlots.size) * Palette.MAX_TONES}")
            if (spec.resolution == MaterialResolution.PIXEL) {
                add("NAMEPLATE_PIXEL")
            }
            spec.outline?.let { add("NAMEPLATE_OUTLINE ${g.compact(it)}") }
            if (spec.emissive) {
                add("NAMEPLATE_EMISSIVE")
            }
            if (graph.blend == GraphBlend.ADDITIVE) {
                add("NAMEPLATE_ADDITIVE")
            }
        }
        val declared = graph.textures.indices.joinToString("") { "uniform sampler2D NameplateSampler$it;\n" }
        val generated = declared + g.declarations() + g.functions() +
            "vec4 material(MaterialInputs m) {\n    vec2 pos = m.pos;\n    float frame = m.frame;\n    float index = 0.0;\n" +
            body + "    return $coverage > 0.0 ? $color : vec4(0.0);\n}\n"
        val code = if (samplers == GlslSamplers.NAMED) {
            generated
        } else {
            SAMPLER.replace(generated) { samplers.name(it.groupValues[1].toInt()) }
        }
        val layout = ShaderLayout(toneSlots, graph.inputs, graph.textures)
        val uniforms = if (dialect == GlslDialect.LEGACY_FLAT) MaterialUniforms.flat(layout) else emptyList()
        val fragment = GlslTemplate.fragment(dialect, defines, code, globals, uniforms)
        return MaterialShader(
            dialect = dialect,
            vertex = GlslTemplate.vertex(dialect),
            fragment = fragment,
            include = if (dialect == GlslDialect.CORE_330) GlslTemplate.include() else "",
            key = hex(NameplateHash.text(fragment)) + hex(NameplateHash.text(fragment.reversed())),
            layout = layout,
            samplers = graph.textures.indices.map(samplers::name),
            uniforms = uniforms,
        )
    }

    internal fun toneSlots(outline: Int?, graph: MaterialGraph): IntArray {
        val slots = HashSet<Int>()
        outline?.let { slots += it / Palette.MAX_TONES }
        for (node in graph.reachable) {
            val kind = node.kind
            when {
                kind === SlotKind || kind is RampKind -> slots += node.settings.slot
                kind === SpriteKind -> node.settings.sprite?.cells?.forEach { if (it >= 0) slots += it / Palette.MAX_TONES }
            }
        }
        return slots.sorted().toIntArray()
    }

    private fun hex(value: Int): String = value.toUInt().toString(HEX).padStart(HEX_DIGITS, '0')
}
