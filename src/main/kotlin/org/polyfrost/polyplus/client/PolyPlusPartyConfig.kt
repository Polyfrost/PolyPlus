package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.party.HostJoinPolicy
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
    @Dropdown(
        title = "Let Party Members Join My Hosted World",
        description = "When you lead a party and host a world, choose what happens when a member asks to join.",
        options = ["Ask", "Always", "Never"],
    )
    var partyHostJoin: HostJoinPolicy = HostJoinPolicy.ASK

    @JvmStatic
    @Switch(
        title = "Join When My Leader Presses Play Together",
        description = "Join your party leader after a 5 second countdown you can cancel. When off, you get a prompt instead.",
    )
    var partyPlayTogetherJoin = true

    @JvmStatic
    @Switch(
        title = "Pings",
        description = "Ping what you're looking at. Your party sees your pings when you play on the same server.",
        subcategory = "Pings",
    )
    var pingsEnabled = true

    @JvmStatic
    @Switch(
        title = "Show Party Pings",
        description = "Show pings from your party members.",
        subcategory = "Pings",
    )
    var pingsShowOthers = true

    @JvmStatic
    @Switch(
        title = "Ping Sound",
        description = "Play a short sound when you or a party member pings.",
        subcategory = "Pings",
    )
    var pingsSound = true

    @JvmStatic
    @Slider(
        title = "Ping Marker Size",
        description = "Size of ping markers, in percent.",
        min = 50f,
        max = 200f,
        step = 5f,
        subcategory = "Pings",
    )
    var pingMarkerScale = 100f

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
