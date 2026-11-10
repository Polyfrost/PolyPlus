package org.polyfrost.polyplus.nameplate.engine.internal.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.internal.anim.BodyOutline
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeRoutes
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Burst
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.NAME_TARGET
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.WidthRange

internal typealias ImageNode = ByteLruCache.Node<LayerKey, PixelImage>

internal class LayerMemo(private val layer: NameplateBundle.Layer, private val bundle: NameplateBundle) {
    val idHash: Int = NameplateHash.text(layer.id)
    val bursts: List<Burst> = bundle.bursts[layer.id].orEmpty()
    val gates: List<List<WidthRange>>? = bundle.layerGates[layer.id]
    private var states: Array<ImageNode?> = NO_NODES
    private val bodies = arrayOfNulls<ImageNode>(BODY_WAYS)
    private var edges: EdgeRoutes? = null

    fun edgeRoutes(): EdgeRoutes = edges ?: EdgeRoutes(BodyOutline.of(bundle, layer.content)).also { edges = it }

    fun shownAt(nameWidth: Int): Boolean {
        val gates = gates ?: return true
        for (g in gates.indices) {
            val gate = gates[g]
            var open = false
            for (r in gate.indices) {
                if (gate[r].contains(nameWidth)) {
                    open = true
                    break
                }
            }
            if (!open) return false
        }
        return true
    }

    fun state(state: Int): ImageNode? = if (state in states.indices) states[state] else null

    fun keepState(state: Int, node: ImageNode?) {
        if (state < 0 || state >= MAX_STATES) return
        if (state >= states.size) {
            states = states.copyOf(maxOf(state + 1, minOf(MAX_STATES, states.size * 2)))
        }
        states[state] = node
    }

    fun body(width: Int, height: Int, flagId: String?): ImageNode? {
        val node = bodies[way(width, height)] ?: return null
        val key = node.key
        return if (node.live && key.width == width && key.height == height && key.flagId == flagId) node else null
    }

    fun keepBody(width: Int, height: Int, node: ImageNode?) {
        bodies[way(width, height)] = node
    }

    private fun way(width: Int, height: Int): Int = (width * WAY_MIX + height) and (BODY_WAYS - 1)

    private companion object {
        const val MAX_STATES = 1024
        const val BODY_WAYS = 64
        const val WAY_MIX = 31
        val NO_NODES: Array<ImageNode?> = emptyArray()
    }
}

internal class LayerPlan(val bundle: NameplateBundle) {
    val layers: List<NameplateBundle.Layer> = bundle.layers
    val memos: Array<LayerMemo> = Array(layers.size) { LayerMemo(layers[it], bundle) }
    val bodyIndices: IntArray = layers.indices.filter { layers[it].content is LayerContent.Body }.toIntArray()
    val ofIndices: IntArray = IntArray(layers.size) { ofIndex(layers[it].content) }
    val usesName: Boolean = layers.any { ofName(it.content) == NAME_TARGET }
    val attachIndices: IntArray = layers.indices.filter { index ->
        layers[index].content is LayerContent.Sprite && layers.any { (it.content as? ParticleEmitter)?.of == layers[index].id }
    }.toIntArray()
    val maskIndices: IntArray = IntArray(layers.size) { index -> layers.indexOfFirst { it.id == layers[index].mask } }
    val bodies: Array<PixelRect?> = arrayOfNulls(layers.size)
    var nameRect: PixelRect? = null
    var opsHint: Int = 0
    private val variants = HashMap<NameplateBundle.Variant, IntArray>()

    fun slots(variant: NameplateBundle.Variant): IntArray = variants.getOrPut(variant) {
        IntArray(layers.size) { index ->
            val slot = layers[index]
            if (slot.id in variant.hidden) {
                HIDDEN
            } else {
                val id = variant.swaps[slot.id] ?: slot.id
                layers.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: MISSING
            }
        }
    }

    fun body(index: Int): PixelRect {
        val of = ofIndices[index]
        if (of == NAME) return checkNotNull(nameRect)
        if (of < 0) {
            throw NoSuchElementException("Key ${ofName(layers[index].content)} is missing in the map.")
        }
        return checkNotNull(bodies[of])
    }

    private fun ofIndex(content: NameplateBundle.LayerContent): Int {
        val of = ofName(content) ?: return NONE
        if (of == NAME_TARGET) return NAME
        val index = layers.indexOfFirst { candidate ->
            candidate.id == of &&
                (candidate.content is LayerContent.Body || (content is ParticleEmitter && candidate.content is LayerContent.Sprite))
        }
        return if (index >= 0) index else MISSING
    }

    private fun ofName(content: NameplateBundle.LayerContent): String? = when (content) {
        is LayerContent.Sprite -> content.of
        is ParticleEmitter -> content.of
        is MaterialLayer -> content.of
        else -> null
    }

    companion object {
        const val HIDDEN = -1
        const val MISSING = -2
        const val NONE = -3
        const val NAME = -4
    }
}
