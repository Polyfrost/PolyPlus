package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeyModifiers
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.emotes.EmoteWheelKeybind
import org.polyfrost.polyplus.client.gui.ClientWatermark
import org.polyfrost.polyplus.client.network.websocket.PolyConnection
import org.polyfrost.polyplus.client.social.SocialOverlay

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
        title = "Replace Pause Menu LAN Button",
        description = "Replace the vanilla 'Open to LAN' pause menu button with the PolyPlus Host World flow, for hosting your current world over EOS P2P.",
        category = "Multiplayer",
    )
    var replacePauseLanButton = true

    @JvmStatic
    @Switch(
        title = "OneClient Watermark",
        description = "Show the OneClient watermark in your inventory and pause menu.",
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkEnabled = true

    @JvmStatic
    @Dropdown(
        title = "Position",
        description = "Where the watermark sits on your screen.",
        options = [
            "Top Left", "Top Right", "Bottom Left", "Bottom Right",
            "Top Center", "Bottom Center", "Left Center", "Right Center",
        ],
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkPosition = ClientWatermark.POSITION_TOP_CENTER

    @JvmStatic
    @Slider(
        title = "Scale",
        description = "Watermark size, as a percentage of normal.",
        min = 50f,
        max = 400f,
        step = 5f,
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkScale = 100f

    @JvmStatic
    @Slider(
        title = "Horizontal Offset",
        description = "How far the watermark sits from the left or right screen edge, in pixels.",
        min = 0f,
        max = 200f,
        step = 1f,
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkOffsetX = 6f

    @JvmStatic
    @Slider(
        title = "Vertical Offset",
        description = "How far the watermark sits from the top or bottom screen edge, in pixels.",
        min = 0f,
        max = 200f,
        step = 1f,
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkOffsetY = 6f

    @JvmStatic
    @Switch(
        title = "Use Accent Color",
        description = "Draw the watermark in your OneConfig accent color.",
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkAccentColor = false

    @JvmStatic
    @Color(
        title = "Color",
        description = "Color of the watermark logo and text.",
        category = "Interface",
        subcategory = "Watermark",
    )
    var watermarkColor: PolyColor = PolyColor.WHITE

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
    var socialsMenuKeybind = OneConfigKeybind(intArrayOf(GLFW.GLFW_KEY_P), null, KeyModifiers.SHIFT, 0L) { state ->
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

        for (option in listOf("watermarkPosition", "watermarkScale", "watermarkOffsetX", "watermarkOffsetY", "watermarkAccentColor")) {
            hideIf(option) { !watermarkEnabled }
        }
        hideIf("watermarkOffsetX") {
            watermarkPosition == ClientWatermark.POSITION_TOP_CENTER ||
                watermarkPosition == ClientWatermark.POSITION_BOTTOM_CENTER
        }
        hideIf("watermarkOffsetY") {
            watermarkPosition == ClientWatermark.POSITION_LEFT_CENTER ||
                watermarkPosition == ClientWatermark.POSITION_RIGHT_CENTER
        }
        hideIf("watermarkColor") { !watermarkEnabled || watermarkAccentColor }

        addCallback("acceptedLegalTerms") {
            org.polyfrost.polyplus.client.privacy.PrivacyEnforcement.onConfigChanged(acceptedLegalTerms)
        }

        addCallback("apiUrl") {
            LOGGER.info("API URL changed to $apiUrl, refreshing API data...")
            PolyConnection.reconnect()
            PolyPlusClient.refresh()
        }
    }
}
