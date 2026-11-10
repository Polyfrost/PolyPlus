package org.polyfrost.polyplus.nameplate.engine.internal.material.load

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.material.MaterialGlsl
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialPlacement
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleBytes
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.issue.issueValues
import org.polyfrost.polyplus.nameplate.engine.internal.material.GraphBuilder
import org.polyfrost.polyplus.nameplate.engine.internal.material.GraphContext
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialGraph
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialMargins
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialMolang
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialResolution
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialSpec
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialSprite
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

internal sealed interface MaterialLoad {
    data class Loaded(val materials: Map<String, Material>) : MaterialLoad

    data class Invalid(val issues: List<BundleIssue>) : MaterialLoad
}

internal class MaterialScope(
    val palette: Palette,
    val paramNames: Set<String>,
    val params: Map<String, Double>,
    val fps: Int,
    val loop: Int,
)

internal class MaterialLoader(private val molang: MaterialMolang? = null) {
    fun load(
        source: BundleSource,
        bytes: BundleBytes,
        bundle: NameplateBundle,
        ids: Collection<String>,
        cycle: Int = bundle.loop,
    ): MaterialLoad {
        val params = bundle.params.keys + bundle.variants.values.flatMap { it.params.keys }
        return load(source, bytes, MaterialScope(bundle.palette, params, bundle.params, bundle.fps, bundle.loop), ids, cycle)
    }

    fun load(
        source: BundleSource,
        bytes: BundleBytes,
        scope: MaterialScope,
        ids: Collection<String>,
        cycle: Int,
        periodic: Boolean = true,
    ): MaterialLoad {
        val issues = ArrayList<BundleIssue>()
        val sprites = HashMap<String, MaterialSprite?>()
        val textures = HashMap<String, Boolean>()
        val context = GraphContext(
            palette = scope.palette,
            params = scope.paramNames,
            sprites = { id -> sprites.getOrPut(id) { sprite(source, id, scope.palette, issues) } },
            textures = { id -> textures.getOrPut(id) { texture(bytes, id, issues) } },
            molang = molang,
        )
        val materials = LinkedHashMap<String, Material>()
        for (id in ids.distinct()) {
            if (!ID.matches(id)) {
                issues += BundleIssue("materials", "material id '$id' must be 1 to 64 of a-z, 0-9 and _")
            } else {
                load(id, source, context, issues)?.let { materials[id] = it }
            }
        }
        if (issues.isEmpty() && periodic) {
            val validator = PeriodValidator(scope, cycle)
            materials.values.forEach { validator.validate(it)?.let(issues::add) }
        }
        return if (issues.isEmpty()) MaterialLoad.Loaded(materials) else MaterialLoad.Invalid(issues.distinct())
    }

    private fun load(id: String, source: BundleSource, context: GraphContext, issues: MutableList<BundleIssue>): Material? {
        val path = MaterialFiles.materialPath(id)
        val file = MaterialFiles.read(source, path, MaterialFileJson.serializer(), issues) ?: return null
        val start = issues.size
        val resolution = when (file.resolution) {
            "pixel" -> MaterialResolution.PIXEL
            "smooth" -> MaterialResolution.SMOOTH
            else -> {
                issues += BundleIssue("$path#resolution", "must be pixel or smooth")
                MaterialResolution.PIXEL
            }
        }
        val margins = file.margins
        if (margins.size != MARGINS || margins.any { it !in 0..MaterialLimits.MAX_MARGIN }) {
            issues += BundleIssue(
                "$path#margins",
                "must be [left, top, right, bottom], each 0 to ${MaterialLimits.MAX_MARGIN} px",
                IssueCode.MATERIAL_MARGINS,
                issueValues("limit" to MaterialLimits.MAX_MARGIN),
            )
        }
        val outline = file.outline?.let { slotRef(it, context.palette) ?: issue(issues, "$path#outline", "must be a slot or slot[tone]") }
        val resolved = context.palette.resolve()
        val toneHint = file.toneHint.mapIndexed { i, ref ->
            argb(ref, context.palette, resolved) ?: issue(issues, "$path#toneHint[$i]", "must be a color or a slot")
        }
        val hintRefs = file.toneHint.mapNotNull { slotRef(it, context.palette) }
        val hintColors = file.toneHint.mapNotNull { (ColorRef.parse(it) as? ColorRef.Hex)?.argb }
        val graph = GraphBuilder(path, context, issues).build(file.nodes, outline != null)
        val valid = issues.size == start && graph != null && margins.size == MARGINS
        return if (valid) {
            val spec = MaterialSpec(
                resolution = resolution,
                margins = MaterialMargins(margins[0], margins[1], margins[2], margins[MARGINS - 1]),
                outline = outline,
                emissive = file.emissive,
                toneHint = toneHint.filterNotNull().toIntArray(),
                graph = graph,
                backdropRefs = if (file.toneHint.isEmpty()) paletteRange(outline, graph, context.palette) else hintRefs.toIntArray(),
                backdropColors = hintColors.toIntArray(),
            )
            Material(id, spec)
        } else {
            null
        }
    }

    private fun sprite(source: BundleSource, id: String, palette: Palette, issues: MutableList<BundleIssue>): MaterialSprite? =
        if (BundleFiles.isSpriteId(id)) MaterialFiles.sprite(source, id, palette, issues) else null

    private fun texture(bytes: BundleBytes, id: String, issues: MutableList<BundleIssue>): Boolean {
        val path = MaterialFiles.texturePath(id)
        val issue = if (ID.matches(id)) MaterialFiles.textureIssue(path, bytes.bytes(path)) else BundleIssue(path, "bad id")
        if (issue != null) {
            issues += issue
        }
        return issue == null
    }

    private class PeriodValidator(private val scope: MaterialScope, private val cycle: Int) {
        private val body = PixelRect.vanillaBackground(REFERENCE_WIDTH, PixelRect.GLYPH_HEIGHT)
        private val placement = MaterialPlacement(
            width = REFERENCE_WIDTH,
            height = PixelRect.GLYPH_HEIGHT,
            nameX = 0,
            nameY = 0,
            nameWidth = REFERENCE_WIDTH,
            prefixWidth = 0,
            background = body,
        )
        private val palette = scope.palette.resolve()

        fun validate(material: Material): BundleIssue? {
            val evaluator = MaterialEvaluator(material)
            evaluator.render(frame(0))
            val first = evaluator.snapshot()
            evaluator.render(frame(cycle))
            val same = first.contentEquals(evaluator.snapshot())
            return if (same) {
                null
            } else {
                BundleIssue(
                    MaterialFiles.materialPath(material.id),
                    "frame $cycle doesn't match frame 0, so the material jumps when the loop wraps; " +
                        "periods must divide the $cycle-frame cycle (give drifting noise a tiling period)",
                    IssueCode.MATERIAL_LOOP,
                    issueValues("cycle" to cycle),
                )
            }
        }

        private fun frame(index: Int) = MaterialFrame(
            body = body,
            frame = index,
            fps = scope.fps,
            step = maxOf(1, scope.loop / maxOf(1, cycle)),
            palette = palette,
            params = scope.params,
            placement = placement,
        )
    }

    private companion object {
        const val MARGINS = 4
        const val REFERENCE_WIDTH = 96
        val ID = Regex("[a-z0-9_]{1,64}")

        fun issue(issues: MutableList<BundleIssue>, path: String, message: String): Int? {
            issues += BundleIssue(path, message)
            return null
        }

        fun paletteRange(outline: Int?, graph: MaterialGraph, palette: Palette): IntArray {
            val refs = ArrayList<Int>()
            for (slot in MaterialGlsl.toneSlots(outline, graph)) {
                for (tone in 0 until palette.slots[slot].size) {
                    refs += slot * Palette.MAX_TONES + tone
                }
            }
            return refs.toIntArray()
        }

        fun slotRef(value: String, palette: Palette): Int? {
            val ref = ColorRef.parse(value) as? ColorRef.Slot ?: return null
            val slot = palette.slots.indexOfFirst { it.id == ref.id }
            return if (slot >= 0 && ref.tone < palette.slots[slot].size) slot * Palette.MAX_TONES + ref.tone else null
        }

        fun argb(value: String, palette: Palette, resolved: ResolvedPalette): Int? = when (val ref = ColorRef.parse(value)) {
            is ColorRef.Hex -> ref.argb
            is ColorRef.Slot -> slotRef(value, palette)?.let(resolved::color)
            else -> null
        }
    }
}
