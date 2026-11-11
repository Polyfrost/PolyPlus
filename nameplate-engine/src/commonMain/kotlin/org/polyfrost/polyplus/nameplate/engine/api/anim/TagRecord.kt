package org.polyfrost.polyplus.nameplate.engine.api.anim

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Triggers
import org.polyfrost.polyplus.nameplate.engine.api.tag.NameplateHash
import org.polyfrost.polyplus.nameplate.engine.api.tag.Reaction
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagInputs
import org.polyfrost.polyplus.nameplate.engine.api.tag.TagState
import org.polyfrost.polyplus.nameplate.engine.internal.anim.TriggeredSpeed
import org.polyfrost.polyplus.nameplate.engine.internal.anim.burstEnd
import org.polyfrost.polyplus.nameplate.engine.internal.anim.mirrorTail
import org.polyfrost.polyplus.nameplate.engine.internal.anim.phaseOffset
import org.polyfrost.polyplus.nameplate.engine.internal.anim.tail
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Burst
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Trigger
import kotlin.time.TimeSource

class TagRecord<K : Any>(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = monotonicMillis(),
) {
    init {
        require(capacity >= 1) { "A record holds at least one tag" }
    }

    private class Entry {
        var bundle: NameplateBundle? = null
        var variant: NameplateBundle.Variant? = null
        var identity = 0
        var inputs = 0
        internal var lastFrame = 0
        var lastSeen = 0L
        val shots = IntArray(TagState.MAX_ONE_SHOTS)
        val starts = IntArray(TagState.MAX_ONE_SHOTS)
        internal val priors = IntArray(TagState.MAX_ONE_SHOTS) { TagState.NO_PRIOR }
        val since = IntArray(Reaction.entries.size)
        val stops = IntArray(Reaction.entries.size) { TagState.NO_STOP }
        internal var shifts: LongArray? = null
        var count = 0
        var state: TagState = TagState.NONE
        var dirty = false
    }

    private val entries = HashMap<K, Entry>()
    private var lastSweep = Long.MIN_VALUE

    val size: Int
        get() = entries.size

    fun update(
        key: K,
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        frame: Int,
        inputs: TagInputs,
        seed: Int = 0,
    ): TagState {
        val now = clock()
        if (lastSweep == Long.MIN_VALUE || now - lastSweep >= SWEEP_MILLIS) {
            sweep(now)
        }

        var entry = entries[key]
        if (entry != null && now - entry.lastSeen >= TTL_MILLIS) {
            entries.remove(key)
            entry = null
        }

        val triggers = bundle.triggers
        if (entry == null) {
            entry = insert(key)
            entry.bundle = bundle
            entry.variant = variant
            entry.identity = identity(bundle, variant)
            entry.inputs = inputs.mask
            entry.lastFrame = frame
            entry.since.fill(frame)
            entry.shifts = shiftsFor(triggers)
            entry.dirty = true
            fireAll(entry, bundle, frame) { it is Trigger.Appear }
        } else {
            advance(entry, bundle, variant, frame, inputs.mask, now, seed)
        }
        entry.lastSeen = now
        return stateOf(entry)
    }

    fun fire(key: K, bundle: NameplateBundle, animation: String, frame: Int): Boolean {
        val entry = entries[key] ?: return false
        if (entry.bundle !== bundle) return false

        val index = bundle.triggers.indexOf(animation)
        if (index < 0) return false

        prune(entry, bundle, frame)
        return fire(entry, bundle, index, frame, honorRestart = true)
    }

    fun state(key: K): TagState {
        val entry = entries[key] ?: return TagState.NONE
        return if (clock() - entry.lastSeen >= TTL_MILLIS) TagState.NONE else stateOf(entry)
    }

    fun remove(key: K) {
        entries.remove(key)
    }

    fun clear() {
        entries.clear()
    }

    internal fun fireIgnoringRestart(key: K, bundle: NameplateBundle, index: Int, frame: Int): Boolean {
        val entry = entries[key] ?: return false
        prune(entry, bundle, frame)
        return fire(entry, bundle, index, frame, honorRestart = false)
    }

    private fun advance(
        entry: Entry,
        bundle: NameplateBundle,
        variant: NameplateBundle.Variant,
        frame: Int,
        mask: Int,
        now: Long,
        seed: Int,
    ) {
        val triggers = bundle.triggers
        if (entry.bundle !== bundle || entry.variant !== variant) {
            val identity = identity(bundle, variant)
            val equipped = identity != entry.identity
            clearShots(entry)
            clearStops(entry)
            entry.bundle = bundle
            entry.variant = variant
            entry.identity = identity
            entry.shifts = shiftsFor(triggers)
            if (equipped) {
                fireAll(entry, bundle, frame) { it == Trigger.Equip }
            }
        } else if (frame < entry.lastFrame) {
            clearShots(entry)
            clearStops(entry)
            entry.shifts = shiftsFor(triggers)
        } else {
            integrate(entry, bundle, frame, seed)
        }
        prune(entry, bundle, frame)

        val unseen = now - entry.lastSeen
        fireAll(entry, bundle, frame) { it is Trigger.Appear && unseen >= it.seconds * MILLIS_PER_SECOND }
        val changed = entry.inputs xor mask
        if (changed != 0) {
            val before = entry.inputs
            fireAll(entry, bundle, frame) { trigger ->
                when (trigger) {
                    is Trigger.Start -> changed and trigger.reaction.bit != 0 && mask and trigger.reaction.bit != 0
                    is Trigger.Stop -> changed and trigger.reaction.bit != 0 && before and trigger.reaction.bit != 0
                    else -> false
                }
            }
            for (reaction in Reaction.entries) {
                if (changed and mask and reaction.bit != 0) {
                    entry.since[reaction.ordinal] = frame
                    entry.stops[reaction.ordinal] = TagState.NO_STOP
                } else if (changed and before and reaction.bit != 0) {
                    entry.stops[reaction.ordinal] = frame
                }
            }
            entry.inputs = mask
            entry.dirty = true
        }
        pruneStops(entry, bundle, frame)
        entry.lastFrame = frame
    }

    private fun integrate(entry: Entry, bundle: NameplateBundle, frame: Int, seed: Int) {
        val shifts = entry.shifts ?: return
        if (frame == entry.lastFrame) return
        val state = stateOf(entry)
        if (state.isIdle) return
        if (TriggeredSpeed.integrate(bundle, state, shifts, entry.lastFrame, frame, phaseOffset(bundle, seed))) {
            entry.dirty = true
        }
    }

    private inline fun fireAll(entry: Entry, bundle: NameplateBundle, frame: Int, matches: (Trigger) -> Boolean) {
        for ((index, shot) in bundle.triggers.oneShots.withIndex()) {
            if (matches(shot.trigger)) {
                fire(entry, bundle, index, frame, honorRestart = true)
            }
        }
    }

    private fun fire(entry: Entry, bundle: NameplateBundle, index: Int, frame: Int, honorRestart: Boolean): Boolean {
        val shot = bundle.triggers.oneShots[index]
        for (slot in 0 until entry.count) {
            if (entry.shots[slot] == index) {
                val playing = frame - entry.starts[slot] < shot.length + mirrorTail(bundle)
                if (playing && (!honorRestart || !shot.restart)) return false
                val before = entry.starts[slot]
                if (frame > before) {
                    entry.priors[slot] = if (frame - before < bundle.triggers.burstEnd(bundle, index)) before else TagState.NO_PRIOR
                }
                entry.starts[slot] = frame
                entry.dirty = true
                return true
            }
        }
        if (entry.count == TagState.MAX_ONE_SHOTS) return false

        entry.shots[entry.count] = index
        entry.starts[entry.count] = frame
        entry.priors[entry.count] = TagState.NO_PRIOR
        entry.count++
        entry.dirty = true
        return true
    }

    private fun prune(entry: Entry, bundle: NameplateBundle, frame: Int) {
        if (entry.count == 0) return

        val triggers = bundle.triggers
        val shots = triggers.oneShots
        var kept = 0
        for (slot in 0 until entry.count) {
            val index = entry.shots[slot]
            val shot = shots.getOrNull(index)
            val age = frame - entry.starts[slot]
            if (shot != null && age >= 0 && age < shot.length + triggers.tail(bundle, index)) {
                val prior = entry.priors[slot]
                if (prior != TagState.NO_PRIOR && (frame < prior || frame - prior >= triggers.burstEnd(bundle, index))) {
                    entry.priors[slot] = TagState.NO_PRIOR
                    entry.dirty = true
                }
                entry.shots[kept] = index
                entry.starts[kept] = entry.starts[slot]
                entry.priors[kept] = entry.priors[slot]
                kept++
            }
        }
        if (kept != entry.count) {
            entry.count = kept
            entry.dirty = true
        }
    }

    private fun pruneStops(entry: Entry, bundle: NameplateBundle, frame: Int) {
        for (reaction in Reaction.entries) {
            val stop = entry.stops[reaction.ordinal]
            if (stop != TagState.NO_STOP && (frame < stop || frame - stop >= burstTrail(bundle, reaction))) {
                entry.stops[reaction.ordinal] = TagState.NO_STOP
                entry.dirty = true
            }
        }
    }

    private fun clearStops(entry: Entry) {
        if (entry.stops.any { it != TagState.NO_STOP }) {
            entry.stops.fill(TagState.NO_STOP)
            entry.dirty = true
        }
    }

    private fun clearShots(entry: Entry) {
        if (entry.count != 0) {
            entry.count = 0
            entry.dirty = true
        }
        if (entry.shifts?.any { it != 0L } == true) {
            entry.dirty = true
        }
    }

    private fun stateOf(entry: Entry): TagState {
        if (entry.dirty) {
            entry.state = TagState.recorded(
                TagInputs.ofMask(entry.inputs),
                entry.shots.copyOf(entry.count),
                entry.starts.copyOf(entry.count),
                entry.since.copyOf(),
                entry.shifts?.copyOf(),
                entry.stops.copyOf(),
                if (hasPrior(entry)) entry.priors.copyOf(entry.count) else null,
            )
            entry.dirty = false
        }

        return entry.state
    }

    private fun hasPrior(entry: Entry): Boolean {
        for (slot in 0 until entry.count) {
            if (entry.priors[slot] != TagState.NO_PRIOR) return true
        }
        return false
    }

    private fun insert(key: K): Entry {
        if (entries.size >= capacity) {
            sweep(clock())
        }
        if (entries.size >= capacity) {
            var stalest: K? = null
            var oldest = Long.MAX_VALUE
            for ((candidate, entry) in entries) {
                if (entry.lastSeen < oldest) {
                    oldest = entry.lastSeen
                    stalest = candidate
                }
            }
            stalest?.let(entries::remove)
        }

        return Entry().also { entries[key] = it }
    }

    private fun sweep(now: Long) {
        lastSweep = now
        val iterator = entries.values.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().lastSeen >= TTL_MILLIS) {
                iterator.remove()
            }
        }
    }

    companion object {
        internal const val DEFAULT_CAPACITY = 1024
        internal const val TTL_MILLIS = 30_000L

        private const val SWEEP_MILLIS = 1_000L
        private const val MILLIS_PER_SECOND = 1_000L

        private fun identity(bundle: NameplateBundle, variant: NameplateBundle.Variant): Int =
            NameplateHash.hash(NameplateHash.text(bundle.id), NameplateHash.text(variant.key), bundle.triggers.oneShots.size)

        internal fun burstTrail(bundle: NameplateBundle, reaction: Reaction): Int {
            var trail = 0
            for ((layerId, bursts) in bundle.bursts) {
                if (bursts.none { it.reaction == reaction && it.shot == Burst.NO_SHOT }) continue
                val layer = bundle.layers.firstOrNull { it.id == layerId } ?: continue
                val emitter = layer.content as? ParticleEmitter ?: continue
                trail = maxOf(trail, (emitter.life.max + 1) * layer.step)
            }
            return trail
        }

        private fun shiftsFor(triggers: Triggers): LongArray? =
            if (triggers.speedTargets.isEmpty()) null else LongArray(triggers.speedTargets.size)

        private fun monotonicMillis(): () -> Long {
            val origin = TimeSource.Monotonic.markNow()
            return { origin.elapsedNow().inWholeMilliseconds }
        }
    }
}
