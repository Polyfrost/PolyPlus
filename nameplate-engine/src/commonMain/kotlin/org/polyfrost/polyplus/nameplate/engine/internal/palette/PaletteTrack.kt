package org.polyfrost.polyplus.nameplate.engine.internal.palette

internal sealed interface PaletteTrack {
    val slot: Int

    enum class CycleMode {
        FORWARD,
        BACKWARD,
        PINGPONG,
    }

    enum class Easing {
        STEP,
        LINEAR,
        EASE,
    }

    class Cycle(
        override val slot: Int,
        val every: Int,
        val mode: CycleMode,
    ) : PaletteTrack

    class Keys(
        override val slot: Int,
        val steps: IntArray,
        val easings: Array<Easing>,
        val colors: Array<IntArray>,
        val refs: Array<IntArray>,
        val molang: Array<IntArray>? = null,
        val bases: Array<BooleanArray>? = null,
    ) : PaletteTrack {
        val keyCount: Int
            get() = steps.size

        val toneCount: Int
            get() = colors[0].size

        fun isBase(key: Int, tone: Int): Boolean = bases?.get(key)?.get(minOf(tone, toneCount - 1)) == true

        fun isBase(key: Int): Boolean = bases?.get(key)?.all { it } == true
    }
}
