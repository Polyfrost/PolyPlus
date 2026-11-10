package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

internal sealed interface Fill {
    class Solid(val ref: Int) : Fill

    class Gradient(val slot: Int, val tones: Int) : Fill

    class Blend(val slot: Int) : Fill

    class Sweep(val slot: Int, val tones: Int, val axis: SweepAxis) : Fill

    data object ActiveFlag : Fill
}

internal enum class SweepAxis {
    X,
    Y,
}
