package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.BodyOutline
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeRoute
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeRows
import org.polyfrost.polyplus.nameplate.engine.internal.anim.OfRects
import org.polyfrost.polyplus.nameplate.engine.internal.anim.ParticleClock
import org.polyfrost.polyplus.nameplate.engine.internal.anim.Particles
import org.polyfrost.polyplus.nameplate.engine.internal.anim.PathGeometry
import org.polyfrost.polyplus.nameplate.engine.internal.anim.PathMotion
import org.polyfrost.polyplus.nameplate.engine.internal.anim.alongPathX
import org.polyfrost.polyplus.nameplate.engine.internal.anim.alongPathY
import org.polyfrost.polyplus.nameplate.engine.internal.anim.burstCopies
import org.polyfrost.polyplus.nameplate.engine.internal.anim.framesOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.shownAt
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeSide
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpawnShape
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpritePath

internal class DynamicLayer(
    val layer: NameplateBundle.Layer,
    val content: NameplateBundle.LayerContent,
    val rect: PixelRect,
    val alternative: Int,
    val capacity: Int,
    private val outline: BodyOutline,
) {
    val sprite: LayerContent.Sprite? = content as? LayerContent.Sprite
    val emitter: ParticleEmitter? = content as? ParticleEmitter

    fun isLive(): Boolean = capacity > 0

    fun geometry(sprite: LayerContent.Sprite, path: SpritePath): PathGeometry {
        val follow = path.follow ?: return PathGeometry(path, rect)
        val pixels = sprite.framesOf(alternative).first()
        return PathGeometry(path, EdgeRoute.of(follow, path.side, outline, rect, pixels.width, pixels.height))
    }

    fun spawnRoute(emitter: ParticleEmitter): EdgeRoute? = emitter.spawn.follow?.takeIf { emitter.spawn.shape == SpawnShape.BORDER }?.let {
        EdgeRoute.of(it, EdgeSide.PERIMETER, outline, rect, emitter.boxWidth, emitter.boxHeight)
    }

    fun spawnRows(emitter: ParticleEmitter): EdgeRows? =
        if (emitter.spawn.shape == SpawnShape.AREA && emitter.spawn.follow != null) {
            outline.rows(rect)?.fitted(emitter.boxWidth, emitter.boxHeight)
        } else {
            null
        }
}

internal object DynamicSlots {
    val HIDDEN_POSE = LayerPose(0, 0, 0, false)
    private const val OPACITY_HALF = 127

    fun isDynamic(layer: NameplateBundle.Layer): Boolean {
        val content = layer.content
        return content is ParticleEmitter || (content as? LayerContent.Sprite)?.adaptive?.path != null
    }

    fun layers(bundle: NameplateBundle, variant: NameplateBundle.Variant, block: TagBlock): List<Pair<Int, DynamicLayer>> {
        val found = ArrayList<Pair<Int, DynamicLayer>>()
        for ((index, slot) in bundle.layers.withIndex()) {
            if (slot.id in variant.hidden) continue
            val layer = bundle.layer(variant.swaps[slot.id] ?: slot.id)
            if (!isDynamic(layer) || !bundle.shownAt(layer.id, block.nameWidth)) continue
            val content = layer.content
            val rect = OfRects.rect(bundle, content, block)
            val outline = BodyOutline.of(bundle, content)
            val dynamic = when (content) {
                is ParticleEmitter -> DynamicLayer(layer, content, rect, 0, maxLive(bundle, layer, content), outline)
                is LayerContent.Sprite -> {
                    val alternative = content.adaptive.alternative(block.nameWidth)
                    val capacity = if (alternative == SpriteAdaptive.HIDDEN) 0 else checkNotNull(content.adaptive.path).walkers
                    DynamicLayer(layer, content, rect, alternative, capacity, outline)
                }
                is LayerContent.Body, is MaterialLayer -> null
            }
            if (dynamic != null && dynamic.isLive()) {
                found.add(index to dynamic)
            }
        }
        return found
    }

    fun maxLive(bundle: NameplateBundle, layer: NameplateBundle.Layer, emitter: ParticleEmitter): Int =
        Particles.maxLive(emitter, bundle.bursts[layer.id].orEmpty(), bundle.loop / layer.step, layer.step) { shot ->
            bundle.triggers.burstCopies(bundle, shot)
        }

    fun of(bundle: NameplateBundle, variant: NameplateBundle.Variant, block: TagBlock): List<Placed> {
        val placed = ArrayList<Placed>()
        for ((index, dynamic) in layers(bundle, variant, block)) {
            val source = Source(bundle, dynamic)
            val width = dynamic.sprite?.framesOf(maxOf(0, dynamic.alternative))?.first()?.width ?: 0
            val flips = if (dynamic.sprite != null) listOf(false, true) else listOf(false)
            for (i in 0 until dynamic.capacity) {
                for (flip in flips) {
                    placed.add(Placed(dynamic.layer, dynamic.layer.depth, 0, 0, flip, width, null, index, 0, Slot(source, i, flip)))
                }
            }
        }
        return placed
    }

    internal class Slot(private val source: Source, private val index: Int, private val flip: Boolean) {
        fun pose(motion: SimulatedMotion, frame: Int): LayerPose {
            source.at(motion, frame)
            if (!source.present[index] || source.flips[index] != flip) return HIDDEN_POSE
            val pose = motion.pose(source.layer, frame, false)
            val opacity = (pose.opacity * source.fades[index] + OPACITY_HALF) / LayerPose.OPAQUE
            return LayerPose(source.xs[index] + pose.dx, source.ys[index] + pose.dy, opacity, pose.visible)
        }

        fun state(motion: SimulatedMotion, frame: Int): Int {
            source.at(motion, frame)
            return if (source.present[index]) source.states[index] else 0
        }
    }

    internal class Source(private val bundle: NameplateBundle, private val dynamic: DynamicLayer) {
        val layer: NameplateBundle.Layer = dynamic.layer
        val xs = IntArray(dynamic.capacity)
        val ys = IntArray(dynamic.capacity)
        val states = IntArray(dynamic.capacity)
        val fades = IntArray(dynamic.capacity)
        val flips = BooleanArray(dynamic.capacity)
        val present = BooleanArray(dynamic.capacity)
        private val ids = IntArray(dynamic.capacity)
        private val live = BooleanArray(dynamic.capacity)
        private val waiting = IntArray(dynamic.capacity * WAITING)
        private var queued = 0
        private var frame = Int.MIN_VALUE
        private val geometry = dynamic.sprite?.let { sprite -> sprite.adaptive.path?.let { dynamic.geometry(sprite, it) } }
        private val clock = dynamic.emitter?.let { emitter ->
            ParticleClock(
                emitter,
                bundle.bursts[layer.id].orEmpty(),
                dynamic.rect,
                0,
                NameplateHash.text(layer.id),
                bundle.loop / layer.step,
                TagState.NONE,
                layer.step,
                0,
                dynamic.spawnRoute(emitter),
                dynamic.spawnRows(emitter),
            )
        }

        fun at(motion: SimulatedMotion, frame: Int) {
            if (frame == this.frame) return
            if (frame < this.frame) {
                live.fill(false)
            }
            this.frame = frame
            present.fill(false)
            val time = frame.floorDiv(layer.step)
            val sprite = dynamic.sprite
            val emitter = dynamic.emitter
            if (geometry != null && sprite != null) {
                walk(sprite, geometry, motion, frame, time)
            } else if (clock != null && emitter != null) {
                queued = 0
                clock.state = motion.tagState
                clock.emit(time) { state, x, y, id, opacity ->
                    val pixels = emitter.spriteOf(state)
                    claim(state, x - pixels.width / 2, y - pixels.height / 2, id, opacity)
                }
                seat()
                present.copyInto(live)
            }
        }

        private fun claim(state: Int, x: Int, y: Int, id: Int, opacity: Int) {
            for (slot in 0 until dynamic.capacity) {
                if (live[slot] && !present[slot] && ids[slot] == id) {
                    record(slot, state, x, y, false, opacity)
                    return
                }
            }
            if (queued < dynamic.capacity) {
                val at = queued * WAITING
                waiting[at] = state
                waiting[at + 1] = x
                waiting[at + 2] = y
                waiting[at + 3] = id
                waiting[at + 4] = opacity
                queued++
            }
        }

        private fun seat() {
            for (k in 0 until queued) {
                val at = k * WAITING
                val slot = free(skipLive = true).takeIf { it >= 0 } ?: free(skipLive = false)
                if (slot < 0) return
                ids[slot] = waiting[at + 3]
                record(slot, waiting[at], waiting[at + 1], waiting[at + 2], false, waiting[at + 4])
            }
        }

        private fun free(skipLive: Boolean): Int {
            for (slot in 0 until dynamic.capacity) {
                if (!present[slot] && !(skipLive && live[slot])) return slot
            }
            return -1
        }

        private fun walk(sprite: LayerContent.Sprite, geometry: PathGeometry, motion: SimulatedMotion, frame: Int, time: Int) {
            val path = checkNotNull(sprite.adaptive.path)
            val frames = sprite.framesOf(dynamic.alternative)
            for (walker in 0 until path.walkers) {
                val spot = geometry.walker(time, walker)
                val shown = if (spot.frame == PathMotion.NORMAL_FRAME) motion.state(layer, frame, false) else spot.frame
                val pixels = frames[shown]
                record(
                    walker,
                    dynamic.alternative * SpriteAdaptive.STATE_STRIDE + shown,
                    sprite.alongPathX(spot.x, pixels),
                    sprite.alongPathY(spot.y, pixels),
                    spot.flip,
                    LayerPose.OPAQUE,
                )
            }
        }

        private fun record(slot: Int, state: Int, x: Int, y: Int, flip: Boolean, opacity: Int) {
            xs[slot] = x
            ys[slot] = y
            states[slot] = state
            fades[slot] = opacity
            flips[slot] = flip
            present[slot] = true
        }

        private companion object {
            const val WAITING = 5
        }
    }
}
