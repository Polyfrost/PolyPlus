@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.internal.material.NodeCosts

@JsExport
class NameplateMaterialCost internal constructor(val id: String, val cost: Int, val samples: Int)

@JsExport
class NameplateFallbackCost internal constructor(
    val id: String,
    val material: String,
    val frames: Int,
    val bytes: Double,
    val animatedBytes: Double,
    val frameBytes: Double,
    val still: Boolean,
    val rebuildMs: Double,
    val drift: Boolean,
    val shotStates: Int,
)

@JsExport
object NameplateMaterialLimits {
    val maxCost: Int = MaterialLimits.MAX_COST
    val outlineFactor: Int = MaterialLimits.OUTLINE_FACTOR
    val toneCost: Int = MaterialLimits.TONE_COST
    val maxNodes: Int = MaterialLimits.MAX_NODES
    val maxSamples: Int = MaterialLimits.MAX_TEXTURE_SAMPLES
    val maxInputs: Int = MaterialLimits.MAX_INPUTS
    val maxRepeat: Int = MaterialLimits.MAX_REPEAT
    val maxOctaves: Int = MaterialLimits.MAX_OCTAVES
    val maxMargin: Int = MaterialLimits.MAX_MARGIN
}

@JsExport
fun nameplateMaterialNodeCost(type: String, variant: Int): Int = NodeCosts.own(type, variant) ?: -1

@JsExport
fun nameplateMaterialCosts(preview: NameplatePreview): Array<NameplateMaterialCost> =
    preview.bundle.materialLayers
        .map { (_, layer) -> NameplateMaterialCost(layer.material.id, layer.material.cost, layer.material.textureSamples) }
        .distinctBy { it.id }
        .toTypedArray()

internal fun fallbackCosts(bundle: NameplateBundle): Array<NameplateFallbackCost> =
    bundle.materialLayers
        .map { (layer, content) ->
            val fallback = content.fallback
            NameplateFallbackCost(
                layer.id,
                content.material.id,
                fallback.frames,
                fallback.bytes.toDouble(),
                fallback.animatedBytes.toDouble(),
                fallback.frameBytes.toDouble(),
                fallback.still,
                fallback.rebuildMs,
                content.drift,
                fallback.shotStates,
            )
        }
        .toTypedArray()
