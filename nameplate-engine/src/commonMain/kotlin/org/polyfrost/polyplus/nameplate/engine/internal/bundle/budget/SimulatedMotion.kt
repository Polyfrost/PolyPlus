package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.EdgeBadgeAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.anim.NO_EVENT
import org.polyfrost.polyplus.nameplate.engine.internal.anim.UNSET
import org.polyfrost.polyplus.nameplate.engine.internal.anim.clampFrame
import org.polyfrost.polyplus.nameplate.engine.internal.anim.eventOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.localOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.mirrorFrame
import org.polyfrost.polyplus.nameplate.engine.internal.anim.mirrorSeedOf
import org.polyfrost.polyplus.nameplate.engine.internal.anim.triggered
import org.polyfrost.polyplus.nameplate.engine.internal.track.ChannelTracks
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangBounds
import org.polyfrost.polyplus.nameplate.engine.internal.track.MolangFrame
import org.polyfrost.polyplus.nameplate.engine.internal.track.PoseTarget
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule
import org.polyfrost.polyplus.nameplate.engine.internal.track.Track

internal fun interface EventPlan {
    fun slot(schedule: Schedule, frame: Int, step: Int, reseeded: Boolean): Long
}

internal object NoEvents : EventPlan {
    override fun slot(schedule: Schedule, frame: Int, step: Int, reseeded: Boolean): Long = NO_EVENT
}

internal class BackToBack(private val active: Set<String>, private val event: Int = CYCLE) : EventPlan {
    override fun slot(schedule: Schedule, frame: Int, step: Int, reseeded: Boolean): Long {
        if (schedule.id !in active) return NO_EVENT

        val spacing = spacing(schedule)
        val time = frame.floorDiv(step)
        val count = schedule.events.size
        val index = if (event == CYCLE) (time.floorDiv(spacing) + if (reseeded) 1 else 0).mod(count) else event
        val local = time.mod(spacing)
        if (index !in 0 until count || local >= schedule.events[index].length) return NO_EVENT

        return (index.toLong() shl INDEX_SHIFT) or local.toLong()
    }

    companion object {
        const val CYCLE = -1

        private const val INDEX_SHIFT = 32

        fun spacing(schedule: Schedule): Int = maxOf(1, schedule.window - schedule.jitter)
    }
}

internal class PoseRange(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int) {
    val reach: Int
        get() = maxOf(-minX, maxX, -minY, maxY)

    fun mirrored(): PoseRange = PoseRange(minOf(minX, -maxX), maxOf(maxX, -minX), minY, maxY)

    operator fun plus(other: PoseRange): PoseRange =
        PoseRange(minX + other.minX, maxX + other.maxX, minY + other.minY, maxY + other.maxY)

    companion object {
        private val ZERO = PoseRange(0, 0, 0, 0)

        fun of(bundle: NameplateBundle, layer: NameplateBundle.Layer, molang: MolangBounds? = null): PoseRange =
            own(bundle, layer, molang) + follow(bundle, layer, molang) + plate(bundle, molang)

        fun ofMirror(bundle: NameplateBundle, layer: NameplateBundle.Layer, molang: MolangBounds? = null): PoseRange =
            own(bundle, layer, molang).mirrored() + follow(bundle, layer, molang) + plate(bundle, molang)

        fun own(bundle: NameplateBundle, layer: NameplateBundle.Layer, molang: MolangBounds? = null): PoseRange =
            chain(bundle, bundle.poseChain(layer.id), molang)

        fun follow(bundle: NameplateBundle, layer: NameplateBundle.Layer, molang: MolangBounds? = null): PoseRange =
            chain(bundle, bundle.followChain(layer.id), molang)

        fun plate(bundle: NameplateBundle, molang: MolangBounds? = null): PoseRange = chain(bundle, bundle.plateChain, molang)

        fun badge(bundle: NameplateBundle, variant: NameplateBundle.Variant, molang: MolangBounds? = null): PoseRange {
            val body = (variant.badge.placement.anchor as? EdgeBadgeAnchor)?.of
            val follow = if (body == null) ZERO else chain(bundle, bundle.poseChain(body), molang)
            return chain(bundle, bundle.badgeChain, molang) + follow + plate(bundle, molang)
        }

        private fun chain(bundle: NameplateBundle, chain: Array<PoseTarget>, molang: MolangBounds?): PoseRange {
            var minX = 0
            var maxX = 0
            var minY = 0
            var maxY = 0
            for (target in chain) {
                val events = target.schedule?.events.orEmpty().mapNotNull { it.channels(target.id) } +
                    bundle.triggers.channelsOf(target.id)
                val xs = bounds(target.loop?.positionX, events.map { it.positionX }, molang)
                val ys = bounds(target.loop?.positionY, events.map { it.positionY }, molang)
                minX += xs[0]
                maxX += xs[1]
                minY += ys[0]
                maxY += ys[1]
            }

            return PoseRange(minX, maxX, minY, maxY)
        }

        private fun bounds(loop: Track?, events: List<Track?>, molang: MolangBounds?): IntArray {
            val result = intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE)
            if (loop == null) {
                result[0] = 0
                result[1] = 0
            } else {
                loop.range(molang, result)
            }
            events.forEach { track -> track?.range(molang, result) }

            return result
        }
    }
}

internal class SimulatedMotion(private val bundle: NameplateBundle, private val plan: EventPlan) {
    var tagState: TagState = TagState.NONE
    var molang: MolangFrame? = null

    fun pose(layer: NameplateBundle.Layer, frame: Int, mirrored: Boolean): LayerPose {
        val chain = bundle.poseChain(layer.id)
        val follow = follow(layer, frame)
        if (chain.isEmpty()) return follow

        val shifted = mirrorFrame(layer, frame, mirrored)
        val reseeded = reseeded(layer, mirrored)
        val loopTime = shifted.mod(bundle.loop)
        var dx = 0
        var dy = 0
        var opacity = LayerPose.OPAQUE
        var visible = true
        for (target in chain) {
            val slot = slotOf(target, shifted, reseeded)
            val event = channelsAt(target, slot)
            val local = localOf(slot)
            dx += sample(target, event, local, shifted, loopTime, 0) { it.positionX }
            dy += sample(target, event, local, shifted, loopTime, 0) { it.positionY }
            opacity = multiply(opacity, sample(target, event, local, shifted, loopTime, LayerPose.OPAQUE) { it.opacity })
            visible = visible && sample(target, event, local, shifted, loopTime, 1) { it.visible } != 0
        }

        return LayerPose(dx + follow.dx, dy + follow.dy, opacity, visible)
    }

    private fun follow(layer: NameplateBundle.Layer, frame: Int): LayerPose {
        val chain = bundle.followChain(layer.id)
        if (chain.isEmpty()) return LayerPose.IDENTITY

        val loopTime = frame.mod(bundle.loop)
        var dx = 0
        var dy = 0
        for (target in chain) {
            val slot = slotOf(target, frame, false)
            val event = channelsAt(target, slot)
            val local = localOf(slot)
            dx += sample(target, event, local, frame, loopTime, 0) { it.positionX }
            dy += sample(target, event, local, frame, loopTime, 0) { it.positionY }
        }
        return LayerPose(dx, dy, LayerPose.OPAQUE, true)
    }

    private fun slotOf(target: PoseTarget, time: Int, reseeded: Boolean): Long {
        val schedule = target.schedule ?: return NO_EVENT
        return plan.slot(schedule, time, target.step, reseeded)
    }

    private fun channelsAt(target: PoseTarget, slot: Long): ChannelTracks? {
        val schedule = target.schedule
        return if (schedule == null || slot == NO_EVENT) null else schedule.events[eventOf(slot)].channels(target.id)
    }

    fun state(layer: NameplateBundle.Layer, frame: Int, mirrored: Boolean): Int {
        val sprite = layer.content as? LayerContent.Sprite ?: return 0
        val shifted = mirrorFrame(layer, frame, mirrored)
        val reacted = triggered(bundle.triggers, layer.id, tagState, shifted, shifted.mod(bundle.loop), molang) { it.frame }
        if (reacted != UNSET) return clampFrame(sprite, reacted)

        val schedule = bundle.scheduleOf(layer.id)
        if (schedule != null) {
            val slot = plan.slot(schedule, shifted, layer.step, reseeded(layer, mirrored))
            if (slot != NO_EVENT) {
                schedule.events[eventOf(slot)].track(layer.id)?.at(localOf(slot), molang)?.let { return clampFrame(sprite, it) }
            }
        }

        val loopTime = shifted.mod(bundle.loop)
        bundle.loopChannels[layer.id]?.frame?.at(loopTime, molang)?.let { return clampFrame(sprite, it) }
        val step = loopTime / layer.step
        return if (sprite.hasTimeline) sprite.timelineFrame(step) else step % sprite.frames.size
    }

    private fun reseeded(layer: NameplateBundle.Layer, mirrored: Boolean): Boolean =
        mirrored && mirrorSeedOf(layer)

    private fun multiply(a: Int, b: Int): Int = (a * b + OPACITY_HALF) / LayerPose.OPAQUE

    private inline fun sample(
        target: PoseTarget,
        event: ChannelTracks?,
        local: Int,
        time: Int,
        loopTime: Int,
        default: Int,
        select: (ChannelTracks) -> Track?,
    ): Int {
        val reacted = triggered(bundle.triggers, target.id, tagState, time, loopTime, molang, select)
        if (reacted != UNSET) return reacted

        val scheduled = event?.let { select(it) }
        val looped = target.loop?.let { select(it) }
        return when {
            scheduled != null -> scheduled.at(local, molang)
            looped != null -> looped.at(loopTime, molang)
            else -> default
        }
    }

    private companion object {
        const val OPACITY_HALF = 127
    }
}
