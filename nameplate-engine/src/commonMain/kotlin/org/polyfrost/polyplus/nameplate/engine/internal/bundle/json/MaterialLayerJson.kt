package org.polyfrost.polyplus.nameplate.engine.internal.bundle.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("material")
internal data class MaterialLayerJson(
    val material: String,
    val of: String? = null,
    val drift: Boolean = false,
    val mirror: Boolean = false,
    val mirrorDelay: Int = 0,
    val mirrorSeed: Boolean = false,
) : LayerFileJson
