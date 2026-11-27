package org.polyfrost.polyplus.client.nameplate

import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle

class BoundNameplate(
    val bundle: NameplateBundle,
    val variant: NameplateBundle.Variant,
    val seed: Int,
)

enum class NameplateMotion {
    FULL,
    REDUCED,
    OFF,
}

class NameplateRenderSettings(
    val enabled: Boolean = true,
    val motion: NameplateMotion = NameplateMotion.FULL,
    val animatedNames: Boolean = true,
    val detailDistance: Int = 24,
    val crowdLimit: Int = 32,
)

object NameplateBinding {
    @Volatile
    var settings: NameplateRenderSettings = NameplateRenderSettings()
}
