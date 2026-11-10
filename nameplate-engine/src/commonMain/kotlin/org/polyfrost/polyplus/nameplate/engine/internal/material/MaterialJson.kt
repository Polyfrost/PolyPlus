package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class MaterialFileJson(
    val resolution: String = "pixel",
    val margins: List<Int> = listOf(0, 0, 0, 0),
    val outline: String? = null,
    val emissive: Boolean = false,
    val toneHint: List<String> = emptyList(),
    val nodes: List<NodeJson> = emptyList(),
)

@Serializable
internal data class NodeJson(
    val id: String,
    val type: String,
    @SerialName("in") val inputs: Map<String, JsonElement> = emptyMap(),
    val set: Map<String, JsonElement> = emptyMap(),
)
