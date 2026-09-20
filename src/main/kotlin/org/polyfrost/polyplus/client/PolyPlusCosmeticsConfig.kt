package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants

object PolyPlusCosmeticsConfig : Config(
    "${PolyPlusConstants.ID}-cosmetics.json",
    "Cosmetics (OneClient)",
    Category.OTHER,
) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var migratedFromLegacyConfig = false

    @JvmStatic
    @Switch(
        title = "Hide Head Cosmetics With Helmet",
        description = "Automatically hide hat cosmetics when a helmet is equipped to avoid clipping.",
    )
    var hideHeadCosmeticsWithHelmet = false

    @JvmStatic
    @Switch(
        title = "Hide Feet Cosmetics With Boots",
        description = "Automatically hide boots cosmetics when boots are equipped to avoid clipping.",
    )
    var hideFeetCosmeticsWithBoots = true

    init {
        preload()
        migrateFromLegacyConfig()
    }

    private fun migrateFromLegacyConfig() {
        if (migratedFromLegacyConfig) return
        loadLegacyPolyPlusOptions("cosmetics", LOGGER, ::loadFrom)
        migratedFromLegacyConfig = true
        save()
    }
}
