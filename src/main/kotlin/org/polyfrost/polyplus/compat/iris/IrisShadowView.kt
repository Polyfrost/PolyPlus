package org.polyfrost.polyplus.compat.iris

import net.irisshaders.iris.shadows.ShadowRenderer
import net.irisshaders.iris.shadows.ShadowRenderingState
import org.joml.Matrix4f

object IrisShadowView {
    fun modelView(): Matrix4f? {
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered()) return null
        return ShadowRenderer.MODELVIEW
    }
}
