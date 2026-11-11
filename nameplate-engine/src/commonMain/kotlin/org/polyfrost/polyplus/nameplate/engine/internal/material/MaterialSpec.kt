package org.polyfrost.polyplus.nameplate.engine.internal.material

internal class MaterialSpec(
    val resolution: MaterialResolution,
    val margins: MaterialMargins,
    val outline: Int?,
    val emissive: Boolean,
    val graph: MaterialGraph,
    val backdropRefs: IntArray = IntArray(0),
    val backdropColors: IntArray = IntArray(0),
)

internal enum class MaterialResolution {
    PIXEL,
    SMOOTH,
}

internal data class MaterialMargins(val left: Int, val top: Int, val right: Int, val bottom: Int)
