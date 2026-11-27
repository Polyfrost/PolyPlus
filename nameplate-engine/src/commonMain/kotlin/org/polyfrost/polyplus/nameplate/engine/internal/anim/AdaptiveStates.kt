package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite

internal fun adaptiveStates(sprite: LayerContent.Sprite, shown: MutableSet<Int>): Set<Int> {
    val adaptive = sprite.adaptive
    val path = adaptive.path
    if (path != null && path.hasFacings) {
        shown.clear()
        path.facings.forEach { frames -> frames?.forEach(shown::add) }
    }
    val alternatives = if (adaptive.breakpoints.isEmpty()) listOf(0) else adaptive.breakpoints.map { it.alternative }.distinct()
    return alternatives.flatMap { alternative -> shown.map { alternative * SpriteAdaptive.STATE_STRIDE + it } }.sorted().toSet()
}

internal fun NameplateBundle.LayerContent.Sprite.framesOf(alternative: Int): List<PixelSprite> =
    if (alternative == 0) frames else adaptive.alternatives[alternative - 1]

internal fun NameplateBundle.LayerContent.Sprite.spriteOf(state: Int): PixelSprite =
    framesOf(state / SpriteAdaptive.STATE_STRIDE)[state % SpriteAdaptive.STATE_STRIDE]

internal fun NameplateBundle.LayerContent.Sprite.alongPathX(x: Int, pixels: PixelSprite): Int =
    (if (adaptive.path?.follow == null) x - pivot.pivotX(pixels.width) else x) + dx

internal fun NameplateBundle.LayerContent.Sprite.alongPathY(y: Int, pixels: PixelSprite): Int =
    (if (adaptive.path?.follow == null) y - pivot.pivotY(pixels.height) else y) + dy

internal fun NameplateBundle.shownAt(layerId: String, nameWidth: Int): Boolean =
    layerGates[layerId]?.all { gate -> gate.any { it.contains(nameWidth) } } ?: true
