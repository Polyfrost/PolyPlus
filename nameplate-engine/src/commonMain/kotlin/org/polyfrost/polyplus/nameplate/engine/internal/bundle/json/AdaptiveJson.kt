package org.polyfrost.polyplus.nameplate.engine.internal.bundle.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class PathJson(
    val preset: String? = null,
    val points: List<PathPointJson>? = null,
    val speed: JsonElement? = null,
    val end: String = "wrap",
    val frames: Map<String, List<Int>>? = null,
    val follow: String? = null,
    val laps: Int? = null,
    val reverse: Boolean = false,
    val stops: List<PathStopJson>? = null,
)

@Serializable
internal data class PathStopJson(
    val at: Double,
    val pause: Int,
)

@Serializable
internal data class PathPointJson(
    val anchor: String,
    val offset: List<Int> = listOf(0, 0),
    val pause: Int = 0,
)

@Serializable
internal data class WalkersJson(
    val count: Int = 1,
    val spacing: Int = 0,
    val even: Boolean = false,
)

@Serializable
internal data class FadeJson(
    val after: Double = 0.0,
    val to: Double = 0.0,
    val steps: Int = 4,
)

@Serializable
internal data class RepeatJson(
    val edge: String,
    val spacing: Int,
    val min: Int = 1,
    val max: Int = 1,
)

@Serializable
internal data class BreakpointJson(
    val min: Int = 0,
    val max: Int? = null,
    val sprite: String? = null,
)

@Serializable
@SerialName("emitter")
internal data class EmitterLayerJson(
    val sprites: List<String>,
    val of: String? = null,
    val spawn: SpawnJson = SpawnJson(),
    val rate: RateJson? = null,
    val lifetime: List<Int> = listOf(1, 1),
    val velocity: VelocityJson = VelocityJson(),
    val gravity: List<Double> = listOf(0.0, 0.0),
    val drift: List<Double> = listOf(0.0, 0.0),
    val wobble: WobbleJson? = null,
    val frames: String = "loop",
    val breakpoints: List<BreakpointJson>? = null,
    val fade: FadeJson? = null,
) : LayerFileJson

@Serializable
internal data class SpawnJson(
    val shape: String = "point",
    val anchor: String = "top_left",
    val edge: String = "top",
    val offset: List<Int> = listOf(0, 0),
    val follow: String? = null,
)

@Serializable
internal data class RateJson(
    val every: Int,
    val count: Int = 1,
    val jitter: Int = 0,
)

@Serializable
internal data class VelocityJson(
    val x: List<Double> = listOf(0.0, 0.0),
    val y: List<Double> = listOf(0.0, 0.0),
)

@Serializable
internal data class WobbleJson(
    val x: Int = 0,
    val y: Int = 0,
    val period: Int = 1,
)
