package org.polyfrost.polyplus.nameplate.engine.api.tag

class TagState private constructor(
    val inputs: TagInputs,
    private val shots: IntArray,
    private val starts: IntArray,
    private val since: IntArray? = null,
    private val shifts: LongArray? = null,
    internal val isReduced: Boolean = false,
    private val stops: IntArray? = null,
    private val priors: IntArray? = null,
) {
    internal val oneShotCount: Int
        get() = shots.size

    internal val isIdle: Boolean
        get() = inputs.isEmpty && shots.isEmpty()

    fun oneShot(index: Int): Int = shots[index]

    fun start(index: Int): Int = starts[index]

    fun since(reaction: Reaction): Int = since?.get(reaction.ordinal) ?: ALWAYS

    fun stopped(reaction: Reaction): Int = stops?.get(reaction.ordinal) ?: NO_STOP

    internal fun shift(index: Int): Long = shifts?.getOrNull(index) ?: 0L

    internal fun prior(index: Int): Int = priors?.get(index) ?: NO_PRIOR

    internal fun withInputs(inputs: TagInputs): TagState =
        if (inputs == this.inputs) this else TagState(inputs, shots, starts, since, shifts, isReduced, stops, priors)

    fun reduced(): TagState =
        if (isReduced && shots.isEmpty()) this else TagState(inputs, NO_INTS, NO_INTS, since, shifts, true, stops)

    internal fun firing(oneShot: Int, start: Int): TagState {
        val at = shots.indexOf(oneShot)
        if (at >= 0) {
            val moved = starts.copyOf()
            moved[at] = start
            return TagState(inputs, shots, moved, since, shifts, isReduced, stops, priors)
        }

        require(shots.size < MAX_ONE_SHOTS) { "At most $MAX_ONE_SHOTS one-shots run at once" }
        return TagState(inputs, shots + oneShot, starts + start, since, shifts, isReduced, stops, priors?.plus(NO_PRIOR))
    }

    override fun equals(other: Any?): Boolean =
        other is TagState &&
            other.inputs == inputs &&
            other.isReduced == isReduced &&
            other.shots.contentEquals(shots) &&
            other.starts.contentEquals(starts) &&
            other.since.contentEquals(since) &&
            other.shifts.contentEquals(shifts) &&
            other.stops.contentEquals(stops) &&
            other.priors.contentEquals(priors)

    override fun hashCode(): Int {
        var hash = inputs.hashCode() * 31 + shots.contentHashCode()
        hash = hash * 31 + starts.contentHashCode()
        hash = hash * 31 + since.contentHashCode()
        hash = hash * 31 + shifts.contentHashCode()
        hash = hash * 31 + stops.contentHashCode()
        if (priors != null) {
            hash = hash * 31 + priors.contentHashCode()
        }
        return hash * 31 + isReduced.hashCode()
    }

    override fun toString(): String =
        "TagState($inputs, oneShots=${shots.contentToString()}, starts=${starts.contentToString()}, reduced=$isReduced)"

    companion object {
        internal const val MAX_ONE_SHOTS = 8
        const val ALWAYS = Int.MIN_VALUE
        internal const val NO_STOP = Int.MAX_VALUE
        internal const val NO_PRIOR = Int.MIN_VALUE

        private val NO_INTS = IntArray(0)

        val NONE: TagState = TagState(TagInputs.NONE, NO_INTS, NO_INTS)

        fun of(
            inputs: TagInputs,
            oneShots: IntArray = NO_INTS,
            starts: IntArray = NO_INTS,
            reduced: Boolean = false,
        ): TagState {
            require(oneShots.size == starts.size) { "Each one-shot needs one start frame" }
            require(oneShots.size <= MAX_ONE_SHOTS) { "At most $MAX_ONE_SHOTS one-shots run at once" }
            require(oneShots.distinct().size == oneShots.size) { "A one-shot runs once at a time" }
            if (inputs.isEmpty && oneShots.isEmpty() && !reduced) return NONE

            return TagState(
                inputs,
                if (reduced) NO_INTS else oneShots.copyOf(),
                if (reduced) NO_INTS else starts.copyOf(),
                isReduced = reduced,
            )
        }

        internal fun recorded(
            inputs: TagInputs,
            oneShots: IntArray,
            starts: IntArray,
            since: IntArray,
            shifts: LongArray?,
            stops: IntArray? = null,
            priors: IntArray? = null,
        ): TagState {
            val moved = shifts?.takeIf { it.any { shift -> shift != 0L } }
            val trailing = stops?.takeIf { it.any { stop -> stop != NO_STOP } }
            if (inputs.isEmpty && oneShots.isEmpty() && moved == null && trailing == null) return NONE

            val echoes = priors?.takeIf { it.any { prior -> prior != NO_PRIOR } }
            return TagState(
                inputs,
                oneShots,
                starts,
                since.takeUnless { inputs.isEmpty && trailing == null },
                moved,
                stops = trailing,
                priors = echoes,
            )
        }
    }
}
