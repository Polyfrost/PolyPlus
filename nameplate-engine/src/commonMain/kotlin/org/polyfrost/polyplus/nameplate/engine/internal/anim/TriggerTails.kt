package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Triggers

internal fun mirrorTail(bundle: NameplateBundle): Int = bundle.layers.maxOfOrNull { layer ->
    if (mirrorsOf(layer)) mirrorDelayOf(layer) * layer.step else 0
} ?: 0

internal fun Triggers.tail(bundle: NameplateBundle, shot: Int): Int {
    val end = burstEnd(bundle, shot)
    return maxOf(mirrorTail(bundle), if (end == 0) 0 else end - oneShots[shot].length)
}

internal fun Triggers.burstEnd(bundle: NameplateBundle, shot: Int): Int {
    val known = ends ?: IntArray(oneShots.size) { burstEndOf(bundle, it) }.also { ends = it }
    return known.getOrElse(shot) { 0 }
}

internal fun Triggers.burstCopies(bundle: NameplateBundle, shot: Int): Int {
    val oneShot = oneShots.getOrNull(shot) ?: return 1
    val end = burstEnd(bundle, shot)
    return if (end > 0 && (oneShot.restart || end > oneShot.length + mirrorTail(bundle))) 2 else 1
}

private fun burstEndOf(bundle: NameplateBundle, shot: Int): Int {
    var end = 0
    for ((layerId, bursts) in bundle.bursts) {
        val layer = bundle.layers.firstOrNull { it.id == layerId } ?: continue
        val emitter = layer.content as? ParticleEmitter ?: continue
        for (burst in bursts) {
            if (burst.shot == shot) {
                end = maxOf(end, burst.lastStep + (emitter.life.max + 1) * layer.step)
            }
        }
    }
    return end
}
