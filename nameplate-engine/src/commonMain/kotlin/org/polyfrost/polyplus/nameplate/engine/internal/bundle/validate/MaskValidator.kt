package org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate

import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BundleFiles
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.EmitterLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.MaterialLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.issue.IssueSink

internal object MaskValidator {
    private const val FILL = "fill"

    fun validate(manifest: ManifestJson, layers: Map<String, LayerFileJson>, report: IssueSink) {
        val byId = manifest.layers.associateBy { it.id }
        for (layer in manifest.layers) {
            checkClip(layer, report)
            val mask = layer.mask ?: continue
            val path = "${BundleFiles.MANIFEST}#layers.${layer.id}.mask"
            val own = layers[layer.id]
            if (own is EmitterLayerJson || (own as? SpriteLayerJson)?.path != null) {
                report(path, "emitters and sprites on a path can't be masked")
            }
            val target = byId[mask] ?: continue
            if (target.mask != null) {
                report(path, "'$mask' is masked itself; masks don't chain")
            }
            val shown = listOf(mask) + manifest.variants.values.mapNotNull { it.swap[mask] }
            for (id in shown.distinct()) {
                val file = layers[id]
                if (layer.clip != null && file != null && file !is BodyLayerJson) {
                    report("${BundleFiles.MANIFEST}#layers.${layer.id}.clip", "'$id' isn't a body; only a body has a fill to clip to")
                }
                when (file) {
                    is SpriteLayerJson -> if (file.path != null) {
                        report(path, "'$id' moves along a path; a mask must be a body or a sprite that isn't on a path")
                    }
                    is EmitterLayerJson, is MaterialLayerJson ->
                        report(path, "'$id' can't be a mask; a mask must be a body or a sprite")
                    is BodyLayerJson, null -> Unit
                }
            }
        }
    }

    private fun checkClip(layer: LayerJson, report: IssueSink) {
        val clip = layer.clip ?: return
        val path = "${BundleFiles.MANIFEST}#layers.${layer.id}.clip"
        when {
            clip != FILL -> report(path, "must be $FILL")
            layer.mask == null -> report(path, "needs a mask")
            layer.mask == NAME_TARGET -> report(path, "the name has no fill; clip to a body instead")
        }
    }
}
