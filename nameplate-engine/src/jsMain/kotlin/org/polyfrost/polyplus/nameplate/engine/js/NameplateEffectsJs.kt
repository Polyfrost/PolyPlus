@file:OptIn(ExperimentalJsExport::class)

package org.polyfrost.polyplus.nameplate.engine.js

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.materialLayers
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tagFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParams
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialInput
import org.polyfrost.polyplus.nameplate.engine.internal.track.MaterialParamTracks

@JsExport
class NameplateEffect internal constructor(
    val layer: String,
    val material: String,
    val mirror: Boolean,
    val mirrorDelay: Int,
    val mirrorSeed: Boolean,
    val reads: Array<String>,
    val animated: Array<String>,
    val states: Int,
    val shotStates: Int,
)

@JsExport
class NameplateParamValues internal constructor(val names: Array<String>, val values: DoubleArray) {
    fun value(name: String): Double? = names.indexOf(name).takeIf { it >= 0 }?.let { values[it] }
}

@JsExport
fun nameplateEffects(preview: NameplatePreview): Array<NameplateEffect> {
    val bundle = preview.bundle
    return bundle.materialLayers.map { (layer, content) ->
        NameplateEffect(
            layer = layer.id,
            material = content.material.id,
            mirror = content.mirror,
            mirrorDelay = content.mirrorDelay,
            mirrorSeed = content.mirrorSeed,
            reads = content.material.inputs.filterIsInstance<MaterialInput.Param>().map { it.name }.distinct().toTypedArray(),
            animated = bundle.materialParams.animated(layer.id).toTypedArray(),
            states = MaterialParams.states(bundle, layer, content),
            shotStates = content.fallback.shotStates,
        )
    }.toTypedArray()
}

@JsExport
fun nameplateParamNames(preview: NameplatePreview): Array<String> {
    val bundle = preview.bundle
    val names = LinkedHashSet(bundle.params.keys)
    bundle.variants.values.forEach { names.addAll(it.params.keys) }
    return names.toTypedArray()
}

@JsExport
fun nameplateParamChannel(param: String): String = MaterialParamTracks.PREFIX + param

@JsExport
fun nameplateParamOfChannel(channel: String): String? =
    if (MaterialParamTracks.isChannel(channel)) MaterialParamTracks.paramOf(channel) else null

@JsExport
fun nameplateParamBaseKey(): String = MaterialParamTracks.BASE

@JsExport
fun nameplateEffectParams(
    preview: NameplatePreview,
    layer: String,
    variant: String,
    frame: Int,
    seed: Int,
    state: NameplateState? = null,
    mirrored: Boolean = false,
): NameplateParamValues {
    val values = paramsAt(preview.bundle, layer, variant, frame, seed, state?.state ?: TagState.NONE, mirrored)
    val names = values.keys.sorted().toTypedArray()
    return NameplateParamValues(names, DoubleArray(names.size) { values.getValue(names[it]) })
}

@JsExport
fun nameplateParamCurve(
    preview: NameplatePreview,
    layer: String,
    param: String,
    variant: String,
    from: Int,
    count: Int,
    seed: Int = 0,
    state: NameplateState? = null,
    mirrored: Boolean = false,
): DoubleArray {
    val bundle = preview.bundle
    val tag = state?.state ?: TagState.NONE
    return DoubleArray(maxOf(count, 0)) { i -> paramsAt(bundle, layer, variant, from + i, seed, tag, mirrored)[param] ?: 0.0 }
}

private fun paramsAt(
    bundle: NameplateBundle,
    layerId: String,
    variant: String,
    frame: Int,
    seed: Int,
    state: TagState,
    mirrored: Boolean,
): Map<String, Double> {
    val resolved = bundle.variant(variant)
    val layer = bundle.layers.firstOrNull { it.id == layerId } ?: return resolved.params
    return MaterialParams.at(bundle, layer, resolved, tagFrame(bundle, frame, seed), seed, state, mirrored)
}
