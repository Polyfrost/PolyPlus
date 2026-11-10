package org.polyfrost.polyplus.nameplate.engine.api.bundle

import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.layout.OrnamentAnchor

object Layout {
    const val CAPABILITY = "layout"

    internal fun uses(manifest: ManifestJson, layers: Map<String, LayerFileJson>): Boolean =
        manifest.layers.any { it.mask != null } ||
            manifest.badge.follow != null ||
            manifest.variants.values.any { it.badge?.containsKey("follow") == true } ||
            layers.values.any { file ->
                when (file) {
                    is BodyLayerJson -> file.art != null || file.of != null
                    is SpriteLayerJson ->
                        file.at != null || OrnamentAnchor.isName(file.anchor) || file.follow != null || file.of == NAME_TARGET
                    else -> false
                }
            }
}
