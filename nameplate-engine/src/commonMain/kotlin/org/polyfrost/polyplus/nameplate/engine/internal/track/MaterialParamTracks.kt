package org.polyfrost.polyplus.nameplate.engine.internal.track

internal class ParamTrack(
    private val steps: IntArray,
    private val values: DoubleArray,
    private val bases: BooleanArray,
    private val modes: IntArray,
) {
    fun at(step: Int, base: Double): Double {
        var i = 0
        while (i + 1 < steps.size && steps[i + 1] <= step) {
            i++
        }
        val mode = if (i + 1 < steps.size) modes[i + 1] else Interpolation.STEP
        val from = value(i, base)
        if (mode == Interpolation.STEP) return from
        val to = value(i + 1, base)
        val t = maxOf(step - steps[i], 0).toDouble() / (steps[i + 1] - steps[i])
        val eased = if (mode == Interpolation.LINEAR) t else t * t * (THREE - 2 * t)
        return from + (to - from) * eased
    }

    fun first(base: Double): Double = value(0, base)

    private fun value(index: Int, base: Double): Double = if (bases[index]) base else values[index]

    private companion object {
        const val THREE = 3.0
    }
}

internal class ParamChannels(val names: Array<String>, val tracks: Array<ParamTrack>) {
    fun track(name: String): ParamTrack? {
        val at = names.indexOf(name)
        return if (at < 0) null else tracks[at]
    }
}

internal class MaterialParamTracks(
    private val loop: Map<String, ParamChannels>,
    private val shots: List<Map<String, ParamChannels>>,
    private val whiles: List<Map<String, ParamChannels>>,
) {
    private val names: Map<String, Array<String>> = buildMap {
        val all = LinkedHashMap<String, MutableSet<String>>()
        for (source in listOf(loop) + shots + whiles) {
            for ((layerId, channels) in source) {
                all.getOrPut(layerId) { LinkedHashSet() }.addAll(channels.names)
            }
        }
        for ((layerId, set) in all) {
            put(layerId, set.sorted().toTypedArray())
        }
    }

    val isEmpty: Boolean
        get() = names.isEmpty()

    val targets: Set<String>
        get() = names.keys

    fun animated(layerId: String): List<String> = names[layerId]?.toList().orEmpty()

    fun namesOf(layerId: String): Array<String>? = names[layerId]

    fun loopOf(layerId: String): ParamChannels? = loop[layerId]

    fun shotOf(index: Int, layerId: String): ParamChannels? = shots.getOrNull(index)?.get(layerId)

    fun whileOf(index: Int, layerId: String): ParamChannels? = whiles.getOrNull(index)?.get(layerId)

    val shotCount: Int
        get() = shots.size

    val whileCount: Int
        get() = whiles.size

    companion object {
        const val PREFIX = "param."
        const val BASE = "base"

        val NONE: MaterialParamTracks = MaterialParamTracks(emptyMap(), emptyList(), emptyList())

        fun isChannel(channel: String): Boolean = channel.startsWith(PREFIX)

        fun paramOf(channel: String): String = channel.removePrefix(PREFIX)
    }
}
