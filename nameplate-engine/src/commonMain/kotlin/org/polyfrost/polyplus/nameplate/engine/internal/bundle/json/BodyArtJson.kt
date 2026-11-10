package org.polyfrost.polyplus.nameplate.engine.internal.bundle.json

import kotlinx.serialization.Serializable

@Serializable
internal data class BodyArtJson(
    val sprite: String,
    val left: Int = 0,
    val right: Int = 0,
    val mirror: Boolean = false,
    val segment: List<Int>? = null,
    val center: List<Int>? = null,
    val top: Int? = null,
    val bottom: Int? = null,
)
