package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET

internal object OfRects {
    fun rect(bundle: NameplateBundle, content: NameplateBundle.LayerContent, block: TagBlock): PixelRect {
        val of = ofOf(content) ?: return block.rect()
        if (of == NAME_TARGET) return block.nameRect()
        return when (val target = bundle.layer(of).content) {
            is NameplateBundle.LayerContent.Body -> target.rect(block)
            is NameplateBundle.LayerContent.Sprite ->
                EmitterAttach.rest(bundle, target, block, EdgeRoutes(BodyOutline.of(bundle, target)))
            else -> block.rect()
        }
    }

    private fun ofOf(content: NameplateBundle.LayerContent): String? = when (content) {
        is NameplateBundle.LayerContent.Sprite -> content.of
        is ParticleEmitter -> content.of
        else -> null
    }
}

internal object EmitterAttach {
    fun rest(bundle: NameplateBundle, sprite: NameplateBundle.LayerContent.Sprite, block: TagBlock, routes: EdgeRoutes): PixelRect {
        val pixels = sprite.framesOf(maxOf(0, sprite.adaptive.alternative(block.nameWidth))).first()
        val rect = OfRects.rect(bundle, sprite, block)
        val place = sprite.place
        val spot = sprite.follow?.let { routes.spot(it, place, rect, pixels.width, pixels.height) }
        val x = (spot?.let(EdgeSpot::x) ?: (place.x(rect, block) - sprite.pivot.pivotX(pixels.width))) + sprite.dx
        val y = (spot?.let(EdgeSpot::y) ?: (place.y(rect, block) - sprite.pivot.pivotY(pixels.height))) + sprite.dy
        return PixelRect(x, y, x + pixels.width, y + pixels.height)
    }
}
