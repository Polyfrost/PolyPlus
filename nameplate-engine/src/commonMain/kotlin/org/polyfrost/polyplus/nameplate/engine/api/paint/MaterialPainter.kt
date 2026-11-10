package org.polyfrost.polyplus.nameplate.engine.api.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.cache.ByteLruCache
import org.polyfrost.polyplus.nameplate.engine.api.cache.LayerKey
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.material.Material
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialFrame
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialPlacement
import org.polyfrost.polyplus.nameplate.engine.api.shader.MaterialTextures
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.mirrorFrame
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.compile.MaterialParams
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialClock
import org.polyfrost.polyplus.nameplate.engine.internal.material.load.MaterialEvaluator
import org.polyfrost.polyplus.nameplate.engine.internal.paint.BodyEdges

internal class MaterialPainter(
    private val grid: PixelGrid,
    private val cache: ByteLruCache<LayerKey, PixelImage>,
    private val textures: MaterialTextures,
    private val fallbacks: Boolean,
    private val masks: MaskPainter,
) {
    private val evaluators = HashMap<Material, MaterialEvaluator>()
    private val drifts = HashMap<LayerKey, Drifted>()
    private val shots = HashMap<LayerKey, Shots>()
    private var reusedKey: LayerKey? = null
    private var override: MaterialTextures? = null
    private val bound = MaterialTextures { ref -> (override ?: textures).pixels(ref) }

    class Target(
        val index: Int,
        val bundle: NameplateBundle,
        val variant: NameplateBundle.Variant,
        val layer: NameplateBundle.Layer,
        val block: TagBlock,
        val frame: Int,
        val seed: Int = 0,
        val state: TagState = TagState.NONE,
        val mirrored: Boolean = false,
    ) {
        fun mirror(): Target = Target(index, bundle, variant, layer, block, frame, seed, state, mirrored = true)
    }

    fun paint(
        target: Target,
        content: MaterialLayer,
        body: PixelRect,
        pose: LayerPose,
        follow: LayerPose,
        evaluate: Boolean = fallbacks,
    ): LayerPainter.Painted {
        val bundle = target.bundle
        val layer = target.layer
        val block = target.block
        val material = content.material
        val shifted = mirrorFrame(layer, target.frame, target.mirrored)
        val state = when {
            !material.animated -> 0
            content.drift -> MaterialClock.drift(shifted, layer.step)
            else -> MaterialClock.state(shifted, layer.step, bundle.loop)
        }
        val placement = placementOf(block)
        val variant = target.variant
        val params = MaterialParams.at(bundle, layer, variant, target.frame, target.seed, target.state, target.mirrored)
        val edge = BodyEdges.of(bundle, content, body)
        val frame = MaterialFrame(body, state, bundle.fps, layer.step, variant.resolved, params, placement, edge)
        val area = material.area(PixelRect(0, 0, body.width, body.height))
        val shot = MaterialParams.inShot(bundle, layer, target.frame, target.seed, target.state, target.mirrored)
        val op = MaterialOp(layer.id, content, area, body, frame, bundle.loop / layer.step, this, bundle, variant, shot = shot)
        val image = if (evaluate) image(op, frame, null, shot) else PixelImage.EMPTY
        return LayerPainter.Painted(
            target.index,
            layer.depth,
            (if (target.mirrored) body.x0 - pose.dx else body.x0 + pose.dx) + follow.dx,
            body.y0 + pose.dy + follow.dy,
            target.mirrored,
            body.width,
            image,
            pose.opacity,
            op,
        )
    }

    fun image(op: MaterialOp, frame: MaterialFrame, textures: MaterialTextures?, shot: Boolean = false): PixelImage =
        clipped(op, lookup(op, frame, shot) ?: create(op, frame, textures, shot))

    fun cached(op: MaterialOp, frame: MaterialFrame, shot: Boolean = false): PixelImage? =
        lookup(op, frame, shot)?.let { clipped(op, it) }

    fun held(op: MaterialOp, frame: MaterialFrame, shot: Boolean = false): PixelImage? {
        val image = if (op.layer.drift || !shot) {
            drifts[probe(op, frame.copy(frame = HELD))]?.latest()
        } else {
            shots[probe(op, frame.copy(frame = SHOT), held = true)]?.latest()
        }
        return image?.let { clipped(op, it) }
    }

    private fun lookup(op: MaterialOp, frame: MaterialFrame, shot: Boolean): PixelImage? = when {
        op.layer.drift && frame.frame != 0 -> drifts[probe(op, frame.copy(frame = HELD))]?.at(frame.frame)
        shot -> shots[probe(op, frame.copy(frame = SHOT), held = true)]?.at(probe(op, frame))
        else -> cache[probe(op, frame)]
    }

    private fun create(op: MaterialOp, frame: MaterialFrame, textures: MaterialTextures?, shot: Boolean): PixelImage {
        val image = evaluate(op, frame, textures)
        when {
            op.layer.drift && frame.frame != 0 -> {
                val key = probe(op, frame.copy(frame = HELD))
                val ring = drifts[key] ?: Drifted().also {
                    if (drifts.size >= MAX_DRIFTS) {
                        drifts.clear()
                    }
                    drifts[key.stored()] = it
                }
                ring.put(frame.frame, image)
            }
            shot -> {
                val key = probe(op, frame.copy(frame = SHOT), held = true)
                val ring = shots[key] ?: Shots().also {
                    if (shots.size >= MAX_DRIFTS) {
                        shots.clear()
                    }
                    shots[key.stored()] = it
                }
                ring.put(probe(op, frame).stored(), image)
            }
            else -> cache.store(probe(op, frame).stored(), image)
        }
        return image
    }

    private fun evaluate(op: MaterialOp, frame: MaterialFrame, textures: MaterialTextures?): PixelImage {
        override = textures
        try {
            return evaluator(op.material).evaluate(frame, grid)
        } finally {
            override = null
        }
    }

    private fun clipped(op: MaterialOp, image: PixelImage): PixelImage =
        op.clip?.let { masks.clip(op.bundle, op.layerId, image, it) } ?: image

    private fun probe(op: MaterialOp, frame: MaterialFrame, held: Boolean = false): LayerKey {
        val bundle = op.bundle
        val material = op.material
        val body = op.body
        val placement = op.frame.placement.takeIf { material.readsPlacement }
        val variantKey = op.variant.key.takeIf { material.inputs.isNotEmpty() || material.readsPalette }
        val params = if (held) null else MaterialParams.values(bundle, op.layerId, frame.params)
        val state = frame.frame
        return reusedKey?.set(bundle, op.layerId, body.width, body.height, null, state, placement, variantKey, params)
            ?: LayerKey(bundle, op.layerId, body.width, body.height, null, state, placement, variantKey, params).also { reusedKey = it }
    }

    private fun evaluator(material: Material): MaterialEvaluator {
        evaluators[material]?.let { return it }
        if (evaluators.size >= MAX_EVALUATORS) {
            evaluators.clear()
        }
        return MaterialEvaluator(material, bound).also { evaluators[material] = it }
    }

    private class Drifted {
        private val states = IntArray(DRIFT_STATES) { HELD }
        private val images = arrayOfNulls<PixelImage>(DRIFT_STATES)
        private var next = 0
        private var newest = 0

        fun at(state: Int): PixelImage? {
            for (i in 0 until DRIFT_STATES) {
                if (states[i] == state) return images[i]
            }
            return null
        }

        fun put(state: Int, image: PixelImage) {
            states[next] = state
            images[next] = image
            newest = next
            next = (next + 1) % DRIFT_STATES
        }

        fun latest(): PixelImage? = images[newest]
    }

    private class Shots {
        private val keys = arrayOfNulls<LayerKey>(DRIFT_STATES)
        private val images = arrayOfNulls<PixelImage>(DRIFT_STATES)
        private var next = 0
        private var newest = 0

        fun at(key: LayerKey): PixelImage? {
            for (i in 0 until DRIFT_STATES) {
                if (keys[i] == key) return images[i]
            }
            return null
        }

        fun put(key: LayerKey, image: PixelImage) {
            keys[next] = key
            images[next] = image
            newest = next
            next = (next + 1) % DRIFT_STATES
        }

        fun latest(): PixelImage? = images[newest]
    }

    companion object {
        private const val MAX_EVALUATORS = 64
        private const val MAX_DRIFTS = 256
        const val DRIFT_STATES = 4
        private const val HELD = Int.MIN_VALUE
        private const val SHOT = Int.MIN_VALUE + 1

        fun placementOf(block: TagBlock): MaterialPlacement = MaterialPlacement(
            width = block.width,
            height = block.height,
            nameX = block.nameX,
            nameY = block.nameLineY,
            nameWidth = block.nameWidth,
            prefixWidth = block.prefixWidth,
            background = block.background,
            lineCount = block.lineCount,
        )
    }
}
