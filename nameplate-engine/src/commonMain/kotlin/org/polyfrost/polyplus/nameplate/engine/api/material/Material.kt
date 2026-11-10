package org.polyfrost.polyplus.nameplate.engine.api.material

import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.shader.TextureRef
import org.polyfrost.polyplus.nameplate.engine.internal.material.CoverageReads
import org.polyfrost.polyplus.nameplate.engine.internal.material.GraphBlend
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialMargins
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialResolution
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialSpec

class Material internal constructor(
    val id: String,
    internal val spec: MaterialSpec,
) {
    internal val resolution: MaterialResolution
        get() = spec.resolution

    internal val margins: MaterialMargins
        get() = spec.margins

    val outline: Int?
        get() = spec.outline

    val emissive: Boolean
        get() = spec.emissive

    val blend: Blend
        get() = if (spec.graph.blend == GraphBlend.ADDITIVE) Blend.ADDITIVE else Blend.ALPHA

    val cost: Int
        get() = spec.graph.cost

    internal val textureSamples: Int
        get() = spec.graph.samples

    internal val inputs: List<MaterialInput>
        get() = spec.graph.valueInputs

    val textures: List<TextureRef>
        get() = spec.graph.textures

    internal val outputsSlots: Boolean
        get() = spec.graph.outputsSlots

    internal val readsPalette: Boolean
        get() = spec.graph.readsPalette || (spec.outline != null && !spec.graph.outputsSlots)

    internal val readsPlacement: Boolean
        get() = spec.graph.inputs.any { it is MaterialInput.Expression } || spec.graph.readsBodyPosition

    internal val readsBodyEdge: Boolean
        get() = spec.graph.readsBodyEdge

    val animated: Boolean
        get() = spec.graph.readsFrame || spec.graph.inputs.any { it is MaterialInput.Expression }

    internal val coverageReads: CoverageReads
        get() = spec.graph.coverageReads

    internal fun toneHint(): IntArray = spec.toneHint.copyOf()

    internal val backdropRefs: IntArray
        get() = spec.backdropRefs

    internal val backdropColors: IntArray
        get() = spec.backdropColors

    fun area(body: PixelRect): PixelRect =
        PixelRect(body.x0 - margins.left, body.y0 - margins.top, body.x1 + margins.right, body.y1 + margins.bottom)

    enum class Blend {
        ALPHA,
        ADDITIVE,
    }
}
