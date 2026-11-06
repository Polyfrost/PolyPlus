package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.party.SceneSharing

object PolyPlusPartyConfig : Config(
    "${PolyPlusConstants.ID}-party.json",
    "Party (OneClient)",
    Category.OTHER,
) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var migratedFromSplitConfigs = false

    @JvmStatic
    @Switch(
        title = "Share My Server With Party",
        description = "Show your party which server you're on, so they can join you.",
    )
    var partyShareServer = true

    @JvmStatic
    @Switch(
        title = "Keep My Own Backdrop in Parties",
        description = "Keep your own lobby scene and time instead of your party leader's. Party members still stand in it.",
    )
    var partyKeepOwnBackdrop = false

    @JvmStatic
    @Dropdown(
        title = "Share My Captured Scenes With My Party",
        description = "When you lead a party, let members see lobby scenes captured in your own worlds.",
        options = ["Ask", "Always", "Never"],
    )
    var partySceneSharing: SceneSharing = SceneSharing.ASK

    @JvmStatic
    @Switch(
        title = "Join When My Leader Presses Play Together",
        description = "Join your party leader after a 5 second countdown you can cancel. When off, you get a prompt instead.",
    )
    var partyPlayTogetherJoin = true

    @JvmStatic @Include
    var partySceneConsents = ""

    init {
        preload()
        migrateFromSplitConfigs()
    }

    private fun migrateFromSplitConfigs() {
        if (migratedFromSplitConfigs) return
        loadPolyPlusOptions("${PolyPlusConstants.ID}.json", "party", LOGGER, ::loadFrom)
        migratedFromSplitConfigs = true
        save()
    }
}
