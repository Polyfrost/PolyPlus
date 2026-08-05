package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.network.websocket.PolyConnection

object PolyPlusConfig : Config("${PolyPlusConstants.ID}.json", "${PolyPlusConstants.NAME} (OneClient)", Category.OTHER) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var defaultSettingsApplied = false

    @JvmStatic @Include
    var appliedDefaults = ""

    @JvmStatic @Include
    var onboardingCompleted = false

    @JvmStatic @Include
    var onboardingFeaturesApplied = false

    @JvmStatic @Include
    var onboardingSprintApplied = false

    @JvmStatic @Include
    var onboardingPolyBlurApplied = false

    @JvmStatic @Include
    var adaptiveBlurApplied = false

    @JvmStatic @Include
    var onboardingLightTheme = false

    @JvmStatic @Include
    var onboardingUiStyle = 0

    @JvmStatic @Include
    var onboardingToggleSprint = true

    @JvmStatic @Include
    var onboardingMotionBlurMode = -1

    @JvmStatic @Include
    var onboardingMotionBlur = 3

    @JvmStatic @Include
    var onboardingGuiScale = 0

    @JvmStatic
    @Switch(
        title = "PolyPlus User Indicators",
        description = "Show a badge on the nametag and tab list of players who are using PolyPlus.",
    )
    var showPolyPlusIndicator = true

    @JvmStatic
    @Switch(
        title = "Chat Emoji",
        description = "Render :shortcode: and unicode emoji (e.g. :sob:) as Twemoji images in chat, and suggest them as you type.",
    )
    var showChatEmoji = true

    @JvmStatic
    @Switch(
        title = "Accept Terms of Service & Privacy Policy",
        description = "Required for crash reporting and online features (cosmetics, client indicator, etc.). ",
        category = "Privacy",
    )
    var acceptedLegalTerms = true

    @Dropdown(title = "API URL", description = "The URL used for the PolyPlus API. Only change if you know what you're doing.")
    var apiUrl: BackendUrl = BackendUrl.PRODUCTION

    init {
        addCallback("acceptedLegalTerms") {
            org.polyfrost.polyplus.client.privacy.PrivacyEnforcement.onConfigChanged(acceptedLegalTerms)
        }

        addCallback("apiUrl") {
            LOGGER.info("API URL changed to $apiUrl, refreshing API data...")
            PolyConnection.reconnect() // Reconnect WebSocket under new URL
            PolyPlusClient.refresh() // Refresh API tokens, cosmetic data, etc.
        }
    }
}
