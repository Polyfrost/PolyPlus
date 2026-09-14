package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
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
        runCatching {
            Tree.beginFailureCollection()
            try {
                loadFrom(ConfigManager.active().folder.resolve("${PolyPlusConstants.ID}.json"))
            } finally {
                val failed = Tree.endFailureCollection()
                if (failed.isNotEmpty()) LOGGER.warn("Left {} cosmetics option(s) at their default", failed)
            }
        }.onFailure { LOGGER.warn("Could not migrate cosmetics options from the legacy PolyPlus config", it) }
        migratedFromLegacyConfig = true
        save()
    }
}
