package org.polyfrost.polyplus.nameplate.engine.internal.bundle.model

import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor

internal enum class SpawnShape {
    POINT,
    LINE,
    AREA,
    BORDER,
}

internal class Spread(val min: Int, val max: Int) {
    val span: Int
        get() = max - min + 1
}

internal class Spawn(
    val shape: SpawnShape,
    val anchor: Anchor,
    val edge: Edge,
    val dx: Int,
    val dy: Int,
    val follow: EdgeFollow? = null,
)

internal class Wobble(val x: Int, val y: Int, val period: Int)

internal class Fade(val after: Double, val to: Int, val steps: Int)
