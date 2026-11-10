package org.polyfrost.polyplus.nameplate.engine.internal.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.shader.BodyEdge

internal object BodyEdges {
    fun of(bundle: NameplateBundle, content: MaterialLayer, body: PixelRect): BodyEdge {
        val of = content.of
        if (of == null || !content.material.readsBodyEdge) return BodyEdge.SQUARE
        val target = bundle.layer(of).content as? LayerContent.Body ?: return BodyEdge.SQUARE
        val rings = target.rings.size
        if (target.art != null) return BodyEdge(0, 0, 0, 0, rings)
        val radii = target.corners.fittedTo(body)
        return BodyEdge(radii.topLeft, radii.topRight, radii.bottomRight, radii.bottomLeft, rings)
    }
}
