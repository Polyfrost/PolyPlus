package org.polyfrost.polyplus.nameplate.engine.api.bundle

import org.polyfrost.polyplus.nameplate.engine.internal.track.PoseTarget

internal object FollowChains {
    fun build(
        layers: List<NameplateBundle.Layer>,
        chains: Map<String, Array<PoseTarget>>,
    ): Map<String, Array<PoseTarget>> {
        val byId = layers.associateBy { it.id }
        val follows = HashMap<String, Array<PoseTarget>>()
        for (layer in layers) {
            val body = targetOf(layer.content)?.let(byId::get)?.takeIf { it.content is NameplateBundle.LayerContent.Body } ?: continue
            put(follows, layer, chains[body.id].orEmpty().asList(), chains)
        }
        for (layer in layers) {
            val emitter = layer.content as? ParticleEmitter ?: continue
            val sprite = emitter.of?.let(byId::get)?.takeIf { it.content is NameplateBundle.LayerContent.Sprite } ?: continue
            put(follows, layer, chains[sprite.id].orEmpty().asList() + follows[sprite.id].orEmpty(), chains)
        }
        return follows
    }

    fun targetOf(content: NameplateBundle.LayerContent): String? = when (content) {
        is NameplateBundle.LayerContent.Sprite -> content.of
        is ParticleEmitter -> content.of
        is MaterialLayer -> content.of
        else -> null
    }

    private fun put(
        follows: MutableMap<String, Array<PoseTarget>>,
        layer: NameplateBundle.Layer,
        targets: List<PoseTarget>,
        chains: Map<String, Array<PoseTarget>>,
    ) {
        val own = chains[layer.id].orEmpty().map { it.id }
        val added = targets.distinctBy { it.id }.filterNot { it.id in own }
        if (added.isNotEmpty()) {
            follows[layer.id] = added.toTypedArray()
        }
    }
}
