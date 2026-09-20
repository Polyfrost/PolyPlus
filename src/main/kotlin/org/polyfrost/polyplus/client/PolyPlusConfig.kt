package org.polyfrost.polyplus.client

import com.mojang.blaze3d.platform.InputConstants
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.privacy.PrivacyEnforcement
import org.polyfrost.polyplus.client.social.SocialOverlay
import java.nio.file.Path

object PolyPlusConfig : Config("${PolyPlusConstants.ID}.json", "${PolyPlusConstants.NAME} (OneClient)", Category.OTHER) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var defaultSettingsApplied = false

    @JvmStatic @Include
    var appliedDefaults = ""

    @JvmStatic @Include
    var modOrderSeed = ""

    @JvmStatic @Include
    var onboardingCompleted = false

    @JvmStatic @Include
    var onboardingFeaturesApplied = false

    @JvmStatic @Include
    var onboardingSprintApplied = false

    @JvmStatic @Include
    var onboardingPolyBlurApplied = false

    @JvmStatic @Include
    var onboardingModSettingsVersion = 0

    @JvmStatic @Include
    var adaptiveBlurApplied = false

    @JvmStatic @Include
    var jvmAdviceShownAt = 0L

    @JvmStatic
    @Switch(
        title = "RAM Analysis Notifications",
        description = "Check how much memory this pack actually needs and suggest a change when the current allocation is hurting performance. Shown at most once every two days.",
    )
    var ramAdviceNotifications = true

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

    @JvmStatic @Include
    var onboardingBetterGrassMode = 3

    @JvmStatic @Include
    var onboardingBetterGrassSettled = false

    @JvmStatic @Include
    var onboardingFireOverlayHeight = 0.0

    @JvmStatic @Include
    var onboardingFireOverlayOpacity = 100f

    @JvmStatic @Include
    var onboardingShieldHeight = 0f

    @JvmStatic @Include
    var onboardingFireOverlaySettled = false

    @JvmStatic @Include
    var onboardingShieldHeightSettled = false

    @JvmStatic @Include
    var onboardingHorseOpacity = 100f

    @JvmStatic @Include
    var onboardingMountOpacitySettled = false

    @JvmStatic @Include
    var onboardingWaveyCapes = true

    @JvmStatic @Include
    var onboardingWaveyCapesSettled = false

    @JvmStatic @Include
    var onboardingSkinLayers = true

    @JvmStatic @Include
    var onboardingSkinLayersSettled = false

    @JvmStatic @Include
    var onboardingItemOffsetX = 0f

    @JvmStatic @Include
    var onboardingItemOffsetY = 0f

    @JvmStatic @Include
    var onboardingItemOffsetZ = 0f

    @JvmStatic @Include
    var onboardingItemScale = 1f

    @JvmStatic @Include
    var onboardingItemPositionsSettled = false

    @JvmStatic @Include
    var onboardingGuidesShown = 0

    /** Gamma Utils' brightness as the mod stores it, where 100 is vanilla maximum. */
    @JvmStatic @Include
    var onboardingGamma = 100f

    @JvmStatic @Include
    var onboardingGammaToggled = 1500f

    @JvmStatic @Include
    var onboardingGammaSmooth = false

    @JvmStatic @Include
    var onboardingGammaSettled = false

    @JvmStatic
    @Switch(
        title = "Replace Pause Menu LAN Button",
        description = "Replace the vanilla 'Open to LAN' pause menu button with the PolyPlus Host World flow, for hosting your current world over EOS P2P.",
        category = "Multiplayer",
    )
    var replacePauseLanButton = true

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
        title = "Automatically Refresh Session",
        description = "Automatically refresh your Microsoft account session when it expires.",
    )
    var autoRefreshSession = true

    @JvmStatic
    @Switch(
        title = "Accept Terms of Service & Privacy Policy",
        description = "Required for crash reporting and online features (cosmetics, client indicator, etc.). ",
        category = "Privacy",
    )
    var acceptedLegalTerms = true

    @Keybind(
        title = "Socials Menu",
        subcategory = "Keybinds",
        description = "Open the PolyPlus Socials menu",
    )
    var socialsMenuKeybind = OneConfigKeybind(intArrayOf(InputConstants.KEY_P), null, KeyModifiers.SHIFT, 0L) { state ->
        if (state) {
            SocialOverlay.toggle()
        }
        true
    }

    @Keybind(
        title = "Emote Wheel",
        subcategory = "Keybinds",
        description = "Open the PolyPlus Emote Wheel",
    )
    var emoteWheelKeybind = OneConfigKeybind(null, null, KeyModifiers.NONE, 0L) { state ->
        EmoteWheelKeybind.onKeybindState(state)
        true
    }

    @Dropdown(title = "API URL", description = "The URL used for the PolyPlus API. Only change if you know what you're doing.")
    var apiUrl: BackendUrl = BackendUrl.PRODUCTION
        get() = if (PolyPlusConstants.IS_DEV_ENV) field else BackendUrl.PRODUCTION

    init {
        hideIf("apiUrl") { !PolyPlusConstants.IS_DEV_ENV }

        addCallback("acceptedLegalTerms") {
            PrivacyEnforcement.onConfigChanged(acceptedLegalTerms)
        }

        addCallback("apiUrl") {
            LOGGER.info("API URL changed to $apiUrl, refreshing API data...")
            PolyConnection.reconnect()
            PolyPlusClient.refresh()
        }
    }
}

/** Folds the pre-split polyplus.json into one of the configs made out of it. */
internal fun loadLegacyPolyPlusOptions(label: String, logger: Logger, load: (Path) -> Unit) {
    runCatching {
        Tree.beginFailureCollection()
        try {
            load(ConfigManager.active().folder.resolve("${PolyPlusConstants.ID}.json"))
        } finally {
            val failed = Tree.endFailureCollection()
            if (failed.isNotEmpty()) logger.warn("Left {} $label option(s) at their default", failed)
        }
    }.onFailure { logger.warn("Could not migrate $label options from the legacy PolyPlus config", it) }
}
