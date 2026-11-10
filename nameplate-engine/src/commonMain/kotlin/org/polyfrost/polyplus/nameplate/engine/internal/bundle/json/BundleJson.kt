package org.polyfrost.polyplus.nameplate.engine.internal.bundle.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class ManifestJson(
    val format: Int,
    val id: String,
    val requires: List<String> = emptyList(),
    val fps: Int,
    val loop: Int,
    val ownsBackground: Boolean = false,
    val params: Map<String, Double> = emptyMap(),
    val text: TextJson = TextJson(),
    val badge: BadgeJson = BadgeJson(),
    val layers: List<LayerJson> = emptyList(),
    val variants: Map<String, VariantJson> = emptyMap(),
    val phase: JsonElement? = null,
    val groups: List<GroupJson>? = null,
    val animations: Map<String, AnimationJson> = emptyMap(),
)

@Serializable
internal data class GroupJson(
    val id: String,
    val parent: String? = null,
    val layers: List<String> = emptyList(),
    val breakpoints: List<BreakpointJson>? = null,
)

@Serializable
internal data class AnimationJson(
    val schedule: ScheduleJson? = null,
    val trigger: JsonElement? = null,
    val length: Int? = null,
    val tracks: Map<String, Map<String, JsonElement>> = emptyMap(),
    val restart: Boolean? = null,
    val reduced: String? = null,
)

@Serializable
internal data class ScheduleJson(
    val window: Int,
    val jitter: Int = 0,
    val chance: Double = 1.0,
    val id: String? = null,
    val weight: Int = 1,
)

@Serializable
internal data class TextJson(
    val mode: String = "vanilla",
    val color: String = "none",
    val shadow: String = "none",
    val outline: String = "none",
    val formatting: String = "adjust",
    val candidates: List<String>? = null,
    val backdropHint: List<String> = emptyList(),
    val motion: String? = null,
)

@Serializable
internal data class BadgeJson(
    val placement: String = "reserve",
    val anchor: String? = null,
    val offset: List<Int> = listOf(0, 0),
    val tint: String = "none",
    val decoration: String = "none",
    val decorationColor: String? = null,
    val candidates: List<String>? = null,
    val of: String? = null,
    val follow: String? = null,
    val at: Double? = null,
)

@Serializable
internal data class LayerJson(
    val id: String,
    val depth: String = "background",
    val step: Int = 1,
    val mask: String? = null,
    val clip: String? = null,
)

@Serializable
internal data class VariantJson(
    val palette: String,
    val hide: List<String> = emptyList(),
    val swap: Map<String, String> = emptyMap(),
    val flag: String? = null,
    val ownsBackground: Boolean? = null,
    val text: JsonObject? = null,
    val badge: JsonObject? = null,
    val params: Map<String, Double> = emptyMap(),
)

@Serializable
internal data class PalettesJson(
    val slots: List<SlotJson>,
    val colorways: Map<String, Map<String, List<String>>> = emptyMap(),
)

@Serializable
internal data class SlotJson(
    val id: String,
    val name: String = id,
    val ramp: List<String>,
    val interpolate: Int? = null,
)

@Serializable
internal sealed interface LayerFileJson

@Serializable
@SerialName("body")
internal data class BodyLayerJson(
    val padding: List<Int> = listOf(0, 0, 0, 0),
    val minWidth: Int = 0,
    val radius: Int = 0,
    val corners: List<Int>? = null,
    val fill: FillJson? = null,
    val rings: List<FillJson> = emptyList(),
    val art: BodyArtJson? = null,
    val of: String? = null,
) : LayerFileJson

@Serializable
@SerialName("sprite")
internal data class SpriteLayerJson(
    val sprite: String,
    val of: String? = null,
    val anchor: String = "top_left",
    val at: Double? = null,
    val pivot: String? = null,
    val offset: List<Int> = listOf(0, 0),
    val mirror: Boolean = false,
    val timeline: List<TimelineStepJson>? = null,
    val mirrorDelay: Int = 0,
    val mirrorSeed: Boolean = false,
    val path: PathJson? = null,
    val walkers: WalkersJson? = null,
    val repeat: RepeatJson? = null,
    val breakpoints: List<BreakpointJson>? = null,
    val follow: String? = null,
) : LayerFileJson

@Serializable
internal data class TimelineStepJson(
    val frame: Int,
    val hold: Int,
)

@Serializable
internal data class FillJson(
    val color: String? = null,
    val gradient: String? = null,
    val blend: String? = null,
    val flag: Boolean = false,
    val sweep: String? = null,
    val axis: String? = null,
)

@Serializable
internal data class FlagsJson(
    val flags: List<FlagJson>,
)

@Serializable
internal data class FlagJson(
    val id: String,
    val bands: List<BandJson>,
    val overlay: OverlayJson? = null,
    val rowsOverride: Map<String, List<Int>> = emptyMap(),
    val symmetric: Boolean = false,
    val axis: String = "rows",
)

@Serializable
internal data class BandJson(
    val color: String,
    val weight: Int = 1,
)

@Serializable
internal data class OverlayJson(
    val kind: String,
    val colors: List<String>,
    val width: Int = 1,
)

@Serializable
internal data class SpriteFileJson(
    val colors: Map<String, String>,
    val rows: List<String>? = null,
    val frames: List<List<String>>? = null,
)
