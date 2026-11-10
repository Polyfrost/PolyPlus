package org.polyfrost.polyplus.nameplate.engine.api.paint

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.ParticleClock
import org.polyfrost.polyplus.nameplate.engine.internal.anim.ParticleSink
import org.polyfrost.polyplus.nameplate.engine.internal.anim.PathGeometry
import org.polyfrost.polyplus.nameplate.engine.internal.anim.PathMotion
import org.polyfrost.polyplus.nameplate.engine.internal.anim.TriggeredSpeed
import org.polyfrost.polyplus.nameplate.engine.internal.anim.UNSET
import org.polyfrost.polyplus.nameplate.engine.internal.anim.alongPathX
import org.polyfrost.polyplus.nameplate.engine.internal.anim.alongPathY
import org.polyfrost.polyplus.nameplate.engine.internal.anim.frameAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.framesOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.poseAt
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.EdgeSide
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpawnShape
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.PixelSprite
import org.polyfrost.polyplus.nameplate.engine.internal.paint.LayerMemo
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame

internal class AdaptiveOps(
    private val images: (LayerMemo, NameplateBundle, NameplateBundle.Layer, PixelSprite, Int) -> PixelImage,
) {
    class Target {
        var out: MutableList<LayerPainter.Painted> = ArrayList()
        var index: Int = 0
        lateinit var bundle: NameplateBundle
        lateinit var layer: NameplateBundle.Layer
        lateinit var rect: PixelRect
        var frame: Int = 0
        var seed: Int = 0
        var state: TagState = TagState.NONE
        var molang: MolangFrame? = null
        lateinit var memo: LayerMemo
        var follow: LayerPose = LayerPose.IDENTITY

        fun set(
            out: MutableList<LayerPainter.Painted>,
            index: Int,
            bundle: NameplateBundle,
            layer: NameplateBundle.Layer,
            rect: PixelRect,
            frame: Int,
            seed: Int,
            state: TagState,
            molang: MolangFrame?,
            memo: LayerMemo,
            follow: LayerPose,
        ): Target {
            this.out = out
            this.index = index
            this.bundle = bundle
            this.layer = layer
            this.rect = rect
            this.frame = frame
            this.seed = seed
            this.state = state
            this.molang = molang
            this.memo = memo
            this.follow = follow
            return this
        }
    }

    private var clock: ParticleClock? = null
    private val emitted = Emitted()

    private inner class Emitted : ParticleSink {
        lateinit var target: Target
        lateinit var emitter: ParticleEmitter
        var dx: Int = 0
        var dy: Int = 0
        var opacity: Int = 0

        override fun particle(state: Int, x: Int, y: Int, id: Int, opacity: Int) {
            val pixels = emitter.spriteOf(state)
            val target = target
            val layer = target.layer
            target.out.add(
                LayerPainter.Painted(
                    target.index,
                    layer.depth,
                    x - pixels.width / 2 + dx,
                    y - pixels.height / 2 + dy,
                    false,
                    pixels.width,
                    images(target.memo, target.bundle, layer, pixels, state),
                    (this.opacity * opacity + OPACITY_HALF) / LayerPose.OPAQUE,
                ),
            )
        }
    }

    fun walkers(target: Target, sprite: LayerContent.Sprite, alternative: Int) {
        val path = checkNotNull(sprite.adaptive.path)
        val layer = target.layer
        val pose = poseAt(target.bundle, layer, target.frame, target.seed, state = target.state, molang = target.molang)
        if (!pose.visible) return

        val frames = sprite.framesOf(alternative)
        val geometry = if (path.follow == null) {
            PathGeometry(path, target.rect)
        } else {
            target.memo.edgeRoutes().geometry(path, target.rect, frames[0].width, frames[0].height)
        }
        val time = target.frame.floorDiv(layer.step)
        val normal = if (path.hasFacings) 0 else frameAt(
            target.bundle,
            layer,
            target.frame,
            target.seed,
            state = target.state,
            molang = target.molang,
        )
        val speedIndex = target.bundle.triggers.speedIndex(layer.id)
        val shift = if (speedIndex < 0) 0L else target.state.shift(speedIndex)
        val speed = if (speedIndex < 0) {
            UNSET
        } else {
            TriggeredSpeed.at(target.bundle, layer.id, target.state, time, layer.step, phaseOffset(target.bundle, target.seed))
        }
        for (walker in 0 until path.walkers) {
            val spot = geometry.walker(time, walker, shift, speed)
            val shown = if (spot.frame == PathMotion.NORMAL_FRAME) normal else spot.frame
            val pixels = frames[shown]
            val x = sprite.alongPathX(spot.x, pixels) + pose.dx + target.follow.dx
            val y = sprite.alongPathY(spot.y, pixels) + pose.dy + target.follow.dy
            val state = alternative * SpriteAdaptive.STATE_STRIDE + shown
            target.out.add(
                LayerPainter.Painted(
                    target.index,
                    layer.depth,
                    x,
                    y,
                    spot.flip,
                    pixels.width,
                    images(target.memo, target.bundle, layer, pixels, state),
                    pose.opacity,
                ),
            )
        }
    }

    fun repeats(target: Target, sprite: LayerContent.Sprite, alternative: Int) {
        val repeat = checkNotNull(sprite.adaptive.repeat)
        val layer = target.layer
        val pose = poseAt(target.bundle, layer, target.frame, target.seed, state = target.state, molang = target.molang)
        if (!pose.visible) return

        val shown = frameAt(target.bundle, layer, target.frame, target.seed, state = target.state, molang = target.molang)
        val pixels = sprite.framesOf(alternative)[shown]
        val pixelsImage = images(target.memo, target.bundle, layer, pixels, alternative * SpriteAdaptive.STATE_STRIDE + shown)
        val rect = target.rect
        val length = repeat.edge.length(rect)
        val route = sprite.follow?.let { target.memo.edgeRoutes().route(it, repeat.side, rect, pixels.width, pixels.height) }
        val count = repeat.count(rect, pixels.width, pixels.height, route?.length ?: length)
        for (copy in 0 until count) {
            val x: Int
            val y: Int
            if (route == null) {
                val along = (2 * copy + 1) * length / (2 * count)
                x = repeat.edge.x(rect, along) - sprite.pivot.pivotX(pixels.width)
                y = repeat.edge.y(rect, along) - sprite.pivot.pivotY(pixels.height)
            } else {
                val along = (2 * copy + 1) * route.length / (2 * count)
                val segment = route.segmentAt(along)
                x = route.x(segment, along)
                y = route.y(segment, along)
            }
            target.out.add(
                LayerPainter.Painted(
                    target.index,
                    layer.depth,
                    x + sprite.dx + pose.dx + target.follow.dx,
                    y + sprite.dy + pose.dy + target.follow.dy,
                    false,
                    pixels.width,
                    pixelsImage,
                    pose.opacity,
                ),
            )
        }
    }

    fun particles(target: Target, emitter: ParticleEmitter) {
        val layer = target.layer
        val bundle = target.bundle
        val pose = poseAt(bundle, layer, target.frame, target.seed, state = target.state, molang = target.molang)
        if (!pose.visible) return

        val memo = target.memo
        val cycle = bundle.loop / layer.step
        val phase = phaseOffset(bundle, target.seed)
        val spawn = emitter.spawn
        val route = spawn.follow?.takeIf { spawn.shape == SpawnShape.BORDER }?.let {
            memo.edgeRoutes().route(it, EdgeSide.PERIMETER, target.rect, emitter.boxWidth, emitter.boxHeight)
        }
        val rows = if (spawn.shape == SpawnShape.AREA && spawn.follow != null) {
            memo.edgeRoutes().rows(target.rect, emitter.boxWidth, emitter.boxHeight)
        } else {
            null
        }
        val clock = clock
            ?.reset(emitter, memo.bursts, target.rect, target.seed, memo.idHash, cycle, target.state, layer.step, phase, route, rows)
            ?: ParticleClock(
                emitter,
                memo.bursts,
                target.rect,
                target.seed,
                memo.idHash,
                cycle,
                target.state,
                layer.step,
                phase,
                route,
                rows,
            ).also { clock = it }
        val sink = emitted
        sink.target = target
        sink.emitter = emitter
        sink.dx = pose.dx + target.follow.dx
        sink.dy = pose.dy + target.follow.dy
        sink.opacity = pose.opacity
        clock.emit(target.frame.floorDiv(layer.step), sink)
    }

    private companion object {
        const val OPACITY_HALF = 127
    }
}
