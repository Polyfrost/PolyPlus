package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants

object PolyPlusScreensConfig : Config(
    "${PolyPlusConstants.ID}-screens.json",
    "Screens (OneClient)",
    Category.OTHER,
) {
    @JvmStatic
    @Switch(
        title = "Settings",
        description = "Uses OneClient's settings, controls and resource packs pages outside the lobby.",
        subcategory = "Screens",
    )
    var customSettings = true

    @JvmStatic
    @Switch(
        title = "Create and edit world",
        description = "Uses OneClient's screens for making and editing worlds.",
        subcategory = "Screens",
    )
    var customWorlds = true

    @JvmStatic
    @Switch(
        title = "Connecting",
        description = "Uses OneClient's screens while joining and leaving servers and worlds.",
        subcategory = "Screens",
    )
    var customConnecting = true

    @JvmStatic
    @Switch(
        title = "Death screen",
        description = "Uses OneClient's death screen.",
        subcategory = "Screens",
    )
    var customDeath = true

    @JvmStatic
    //? if > 1.8.9 {
    @Switch(
        title = "Realms",
        description = "Uses OneClient's Realms screens.",
        subcategory = "Screens",
    )
    //?} else {
    /*@Include
    *///?}
    var customRealms = true

    @JvmStatic @Include
    var onboardingAsked = false

    init {
        preload()
    }
}
