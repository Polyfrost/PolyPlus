package org.polyfrost.polyplus.nameplate.engine.internal.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelRect
import org.polyfrost.polyplus.nameplate.engine.api.tag.LayerPose
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Burst
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fade
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpawnShape
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule
import kotlin.math.abs

internal fun interface ParticleSink {
    fun particle(state: Int, x: Int, y: Int, id: Int, opacity: Int)
}

internal class ParticleClock(
    private var emitter: ParticleEmitter,
    private var bursts: List<Burst>,
    private var rect: PixelRect,
    private var seed: Int,
    private var idHash: Int,
    private var cycle: Int,
    var state: TagState = TagState.NONE,
    private var layerStep: Int = 1,
    private var phase: Int = 0,
    private var route: EdgeRoute? = null,
    private var rows: EdgeRows? = null,
) {
    fun reset(
        emitter: ParticleEmitter,
        bursts: List<Burst>,
        rect: PixelRect,
        seed: Int,
        idHash: Int,
        cycle: Int,
        state: TagState,
        layerStep: Int,
        phase: Int,
        route: EdgeRoute?,
        rows: EdgeRows?,
    ): ParticleClock {
        this.emitter = emitter
        this.bursts = bursts
        this.rect = rect
        this.seed = seed
        this.idHash = idHash
        this.cycle = cycle
        this.state = state
        this.layerStep = layerStep
        this.phase = phase
        this.route = route
        this.rows = rows
        return this
    }

    fun emit(time: Int, sink: ParticleSink) {
        steady(time, sink)
        for (source in bursts.indices) {
            val burst = bursts[source]
            val schedule = burst.schedule
            val id = NameplateHash.derive(idHash, Particles.SALT_BURST + source)
            val reaction = burst.reaction
            when {
                burst.shot != Burst.NO_SHOT -> fired(burst, id, time, sink)
                reaction != null -> held(burst, reaction, id, time, sink)
                schedule == null -> looped(burst, id, time, sink)
                else -> scheduled(burst, schedule, id, time, sink)
            }
        }
    }

    private fun held(burst: Burst, reaction: Reaction, id: Int, time: Int, sink: ParticleSink) {
        if (state.isReduced) return
        val start = startOf(state.since(reaction))
        val stop = state.stopped(reaction)
        when {
            state.inputs.has(reaction) -> looped(burst, id, time, sink, start)
            stop != TagState.NO_STOP -> looped(burst, id, time, sink, start, startOf(stop))
        }
    }

    private fun startOf(since: Int): Int = if (since == TagState.ALWAYS) Int.MIN_VALUE else (since + phase).floorDiv(layerStep)

    private fun fired(burst: Burst, id: Int, time: Int, sink: ParticleSink) {
        if (state.isReduced) return
        for (i in 0 until state.oneShotCount) {
            if (state.oneShot(i) != burst.shot) continue
            firedAt(burst, id, state.start(i), time, sink)
            val prior = state.prior(i)
            if (prior != TagState.NO_PRIOR) {
                firedAt(burst, id, prior, time, sink)
            }
        }
    }

    private fun firedAt(burst: Burst, id: Int, start: Int, time: Int, sink: ParticleSink) {
        val shotId = NameplateHash.derive(id, start)
        val steps = burst.steps
        for (key in steps.indices) {
            val spawned = (start + steps[key] + phase).floorDiv(layerStep)
            spawn(shotId, key * Particles.MAX_BURST, burst.counts[key], time - spawned, sink)
        }
    }

    private fun steady(time: Int, sink: ParticleSink) {
        val every = emitter.every
        if (every <= 0) return

        val jitter = emitter.jitter
        val count = emitter.count
        val longest = emitter.life.max
        val first = (time - jitter - longest).floorDiv(every) + 1
        for (spawn in first..time.floorDiv(every)) {
            for (j in 0 until count) {
                val hash = NameplateHash.hash(seed, idHash, spawn * count + j)
                val at = spawn * every + pick(hash, Particles.SALT_TIME, jitter + 1)
                place(hash, time - at, sink)
            }
        }
    }

    private fun pick(hash: Int, salt: Int, range: Int): Int =
        if (range == 1) 0 else Particles.uniform(NameplateHash.derive(hash, salt), range)

    private fun looped(burst: Burst, id: Int, time: Int, sink: ParticleSink, from: Int = Int.MIN_VALUE, end: Int = Int.MAX_VALUE) {
        for ((key, step) in burst.steps.withIndex()) {
            val first = (time - step - emitter.life.max).floorDiv(cycle) + 1
            for (lap in first..(time - step).floorDiv(cycle)) {
                val at = lap * cycle + step
                if (at in from until end) {
                    spawn(id, (lap * burst.steps.size + key) * Particles.MAX_BURST, burst.counts[key], time - at, sink)
                }
            }
        }
    }

    private fun scheduled(burst: Burst, schedule: Schedule, id: Int, time: Int, sink: ParticleSink) {
        val window = schedule.window
        val first = (time - schedule.jitter - burst.lastStep - emitter.life.max).floorDiv(window) + 1
        for (k in first..(time - burst.firstStep).floorDiv(window)) {
            val event = windowEvent(schedule, seed, k)
            if (event == NO_EVENT || eventOf(event) != burst.event) continue
            val start = k * window + localOf(event)
            for ((key, step) in burst.steps.withIndex()) {
                spawn(id, (k * burst.steps.size + key) * Particles.MAX_BURST, burst.counts[key], time - (start + step), sink)
            }
        }
    }

    private fun spawn(id: Int, base: Int, count: Int, age: Int, sink: ParticleSink) {
        if (age < 0 || age >= emitter.life.max) return
        for (j in 0 until count) {
            place(NameplateHash.hash(seed, id, base + j), age, sink)
        }
    }

    private fun place(hash: Int, age: Int, sink: ParticleSink) {
        val life = emitter.life.min + pick(hash, Particles.SALT_LIFE, emitter.life.span)
        if (age < 0 || age >= life) return

        val spawn = emitter.spawn
        var x = spawn.dx
        var y = spawn.dy
        when (spawn.shape) {
            SpawnShape.POINT -> {
                x += spawn.anchor.x(rect)
                y += spawn.anchor.y(rect)
            }
            SpawnShape.LINE -> {
                val at = pick(hash, Particles.SALT_SPOT_X, maxOf(1, spawn.edge.length(rect)))
                x += spawn.edge.x(rect, at)
                y += spawn.edge.y(rect, at)
            }
            SpawnShape.AREA -> {
                val rows = rows
                if (rows == null) {
                    x += rect.x0 + pick(hash, Particles.SALT_SPOT_X, maxOf(1, rect.width))
                    y += rect.y0 + pick(hash, Particles.SALT_SPOT_Y, maxOf(1, rect.height))
                } else {
                    val row = pick(hash, Particles.SALT_SPOT_Y, rows.count)
                    val left = rows.lefts[row]
                    x += left + pick(hash, Particles.SALT_SPOT_X, rows.rights[row] - left + 1) + emitter.boxWidth / 2
                    y += rows.top + row + emitter.boxHeight / 2
                }
            }
            SpawnShape.BORDER -> {
                val route = route
                if (route == null) {
                    val at = pick(hash, Particles.SALT_SPOT_X, maxOf(1, 2 * (rect.width + rect.height)))
                    x += Particles.borderX(rect, at)
                    y += Particles.borderY(rect, at)
                } else {
                    val at = pick(hash, Particles.SALT_SPOT_X, maxOf(1, route.length))
                    val segment = route.segmentAt(at)
                    x += route.x(segment, at) + emitter.boxWidth / 2
                    y += route.y(segment, at) + emitter.boxHeight / 2
                }
            }
        }

        val vx = emitter.velocityX.min + pick(hash, Particles.SALT_VX, emitter.velocityX.span)
        val vy = emitter.velocityY.min + pick(hash, Particles.SALT_VY, emitter.velocityY.span)
        x += Particles.offset(vx + emitter.driftX, emitter.gravityX, age)
        y += Particles.offset(vy + emitter.driftY, emitter.gravityY, age)
        val wobble = emitter.wobble
        if (wobble != null) {
            val phase = age + pick(hash, Particles.SALT_WOBBLE, wobble.period)
            x += Particles.wave(wobble.x, phase, wobble.period)
            y += Particles.wave(wobble.y, phase, wobble.period)
        }

        val sprite = pick(hash, Particles.SALT_SPRITE, emitter.pool.size)
        val frames = emitter.pool[sprite].size
        val frame = if (emitter.stretch) age * frames / life else age % frames
        sink.particle(sprite * SpriteAdaptive.STATE_STRIDE + frame, x, y, hash, Particles.opacity(emitter.fade, age, life))
    }
}

internal object Particles {
    const val UNIT = 256
    const val MAX_BURST = 64
    const val SALT_TIME = 11
    const val SALT_LIFE = 12
    const val SALT_SPOT_X = 13
    const val SALT_SPOT_Y = 14
    const val SALT_VX = 15
    const val SALT_VY = 16
    const val SALT_SPRITE = 17
    const val SALT_WOBBLE = 18
    const val SALT_BURST = 32

    fun uniform(hash: Int, range: Int): Int = (hash ushr 1) % range

    fun opacity(fade: Fade?, age: Int, life: Int): Int {
        if (fade == null) return LayerPose.OPAQUE

        val start = (fade.after * life).toInt()
        if (age < start) return LayerPose.OPAQUE

        val level = ((age - start + 1) * fade.steps / (life - start)).coerceIn(1, fade.steps)
        return LayerPose.OPAQUE - (LayerPose.OPAQUE - fade.to) * level / fade.steps
    }

    fun offset(velocity: Int, gravity: Int, age: Int): Int {
        val a = age.toLong()
        val doubled = 2L * velocity * a + gravity.toLong() * a * a
        return (doubled + UNIT).floorDiv(2L * UNIT).toInt()
    }

    fun wave(amplitude: Int, phase: Int, period: Int): Int {
        val u = phase.mod(period)
        val v = abs(2 * u - period)
        return amplitude * (period - 2 * v) / period
    }

    fun borderX(rect: PixelRect, at: Int): Int {
        val w = rect.width
        val h = rect.height
        return when {
            at < w -> rect.x0 + at
            at < w + h -> rect.x1
            at < 2 * w + h -> rect.x1 - (at - w - h)
            else -> rect.x0
        }
    }

    fun borderY(rect: PixelRect, at: Int): Int {
        val w = rect.width
        val h = rect.height
        return when {
            at < w -> rect.y0
            at < w + h -> rect.y0 + (at - w)
            at < 2 * w + h -> rect.y1
            else -> rect.y1 - (at - 2 * w - h)
        }
    }

    fun maxLive(
        emitter: ParticleEmitter,
        bursts: List<Burst>,
        cycle: Int,
        layerStep: Int = 1,
        copies: (Int) -> Int = { 1 },
    ): Int {
        val longest = emitter.life.max
        var live = if (emitter.every > 0) emitter.count * ceilDiv(longest + emitter.jitter, emitter.every) else 0
        for (burst in bursts) {
            if (burst.shot != Burst.NO_SHOT) {
                live += copies(burst.shot) * firing(burst, longest, layerStep)
            }
        }
        val looped = bursts.filter { it.schedule == null && it.shot == Burst.NO_SHOT }
        if (looped.isNotEmpty()) {
            var most = 0
            for (time in 0 until cycle) {
                var alive = 0
                for (burst in looped) {
                    for ((key, step) in burst.steps.withIndex()) {
                        val laps = (time - step).floorDiv(cycle) - (time - step - longest).floorDiv(cycle)
                        alive += laps * burst.counts[key]
                    }
                }
                most = maxOf(most, alive)
            }
            live += most
        }
        val scheduled = bursts.mapNotNull { burst -> burst.schedule?.let { it to burst } }.groupBy { it.first.id }
        for (group in scheduled.values) {
            val timing = group.first().first
            val windows = group.maxOf { (_, burst) -> ceilDiv(longest + timing.jitter + burst.lastStep - burst.firstStep, timing.window) }
            live += windows * group.maxOf { it.second.total }
        }
        return live
    }

    private fun firing(burst: Burst, longest: Int, layerStep: Int): Int {
        val steps = burst.steps
        var most = 0
        for (first in steps.indices) {
            var alive = 0
            for (key in first until steps.size) {
                if ((steps[key] - steps[first]).floorDiv(layerStep) >= longest) break
                alive += burst.counts[key]
            }
            most = maxOf(most, alive)
        }
        return most
    }

    private fun ceilDiv(a: Int, b: Int): Int = -(-a).floorDiv(b)
}
