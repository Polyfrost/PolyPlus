package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET

internal object LayerMasks {
    fun cachedStates(bundle: NameplateBundle, layer: NameplateBundle.Layer): Int {
        val own = states(bundle, layer).size
        val mask = layer.mask
        if (mask == null || mask == NAME_TARGET) return own
        return own * states(bundle, bundle.layer(mask)).size
    }
}
