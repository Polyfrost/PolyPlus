package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Property.Display
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.BackendUrl
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.discord.DiscordPresence
import org.polyfrost.polyplus.client.gui.MainMenuBackground
import org.polyfrost.polyplus.client.network.websocket.PolyConnection

private const val MAIN_MENU_FPS_HEADROOM = 60
private const val FALLBACK_MONITOR_REFRESH_RATE = 60
private const val VANILLA_MAIN_MENU_FPS_LIMIT = 60
private const val MAX_CUSTOM_MAIN_MENU_FPS_LIMIT = 260f

object PolyPlusConfig : Config("${PolyPlusConstants.ID}.json", PolyPlusConstants.NAME, Category.OTHER) {
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
    var onboardingEvergreenApplied = false

    @JvmStatic @Include
    var onboardingLightTheme = false

    @JvmStatic @Include
    var onboardingUiStyle = 0

    @JvmStatic @Include
    var onboardingHudStyle = 0

    @JvmStatic @Include
    var onboardingToggleSprint = true

    @JvmStatic @Include
    var onboardingHudFps = true

    @JvmStatic @Include
    var onboardingHudCps = false

    @JvmStatic @Include
    var onboardingHudPing = true

    @JvmStatic @Include
    var onboardingHudTime = true

    @JvmStatic @Include
    var onboardingHudCoords = false

    @JvmStatic @Include
    var onboardingHudDirection = false

    @JvmStatic @Include
    var onboardingMotionBlur = 5

    @JvmStatic
    @Switch(
        title = "Vanilla Main Menu",
        description = "Disable the PolyPlus main menu and use the vanilla Minecraft title screen instead.",
        category = "Main Menu",
    )
    var useVanillaMainMenu = false

    @JvmStatic
    @Switch(
        title = "Hide Quickplay",
        description = "Hide the Quickplay recent servers panel on the PolyPlus main menu.",
        category = "Main Menu",
        subcategory = "Elements",
    )
    var hideMainMenuQuickplay = false

    @JvmStatic
    @Switch(
        title = "Hide Player Preview",
        description = "Hide the 3D player preview on the PolyPlus main menu.",
        category = "Main Menu",
        subcategory = "Elements",
    )
    var hideMainMenuPlayerPreview = false

    @JvmStatic
    @Switch(
        title = "Hide Alt Manager",
        description = "Hide the account/alt manager pill on the PolyPlus main menu.",
        category = "Main Menu",
        subcategory = "Elements",
    )
    var hideMainMenuAltManager = false

    @JvmStatic
    @Switch(
        title = "Hide Social Button",
        description = "Hide the Social button on the PolyPlus main menu.",
        category = "Main Menu",
        subcategory = "Elements",
    )
    var hideMainMenuSocial = false

    @JvmStatic
    @Switch(
        title = "Hide Cosmetics Button",
        description = "Hide the Cosmetics button on the PolyPlus main menu.",
        category = "Main Menu",
        subcategory = "Elements",
    )
    var hideMainMenuCosmetics = false

    @JvmStatic
    @Switch(
        title = "Hide Host World Button",
        description = "Hide the Host World button (opens a world to LAN via e4mc) on the PolyPlus main menu.",
        category = "Main Menu",
        subcategory = "Elements",
    )
    var hideMainMenuHostWorld = false

    @JvmStatic @Switch(title = "Discord RPC")
    var isDiscordEnabled = true

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
        title = "Hide Head Cosmetics With Helmet",
        description = "Automatically hide hat cosmetics when a helmet is equipped to avoid clipping.",
        category = "Cosmetics",
    )
    var hideHeadCosmeticsWithHelmet = false

    @JvmStatic
    @Switch(
        title = "Hide Feet Cosmetics With Boots",
        description = "Automatically hide boots cosmetics when boots are equipped to avoid clipping.",
        category = "Cosmetics",
    )
    var hideFeetCosmeticsWithBoots = true

    @JvmStatic
    @Dropdown(
        title = "Main Menu FPS Limit",
        description = "Choose how the PolyPlus main menu frame cap is selected.",
        options = ["Vanilla (60 FPS limit)", "Smart (monitor refresh rate + 60)", "Custom (15-260 value)"],
        category = "Main Menu",
    )
    var mainMenuFpsLimitMode: MainMenuFpsLimitMode = MainMenuFpsLimitMode.SMART

    @JvmStatic
    @Slider(
        title = "Custom Main Menu FPS Limit",
        description = "Frame cap used when Main Menu FPS Limit is set to Custom.",
        min = 15f,
        max = MAX_CUSTOM_MAIN_MENU_FPS_LIMIT,
        step = 5f,
        category = "Main Menu",
    )
    var mainMenuFpsLimit = 260

    @JvmStatic
    @Dropdown(
        title = "Menu Backdrop",
        description = "Choose what appears behind the PolyPlus main menu.",
        options = ["PolyPlus", "Minecraft Panorama"],
        category = "Main Menu",
    )
    var mainMenuBackground: MainMenuBackground = MainMenuBackground.PANORAMA

    @Dropdown(title = "API URL", description = "The URL used for the PolyPlus API. Only change if you know what you're doing.")
    var apiUrl: BackendUrl = BackendUrl.PRODUCTION

    init {
        addDependency("mainMenuFpsLimit", "Main Menu FPS Limit") {
            if (mainMenuFpsLimitMode == MainMenuFpsLimitMode.CUSTOM) Display.SHOWN else Display.DISABLED
        }

        addCallback("isDiscordEnabled") {
            DiscordPresence.start() // Ensure that Discord RPC is restarted if the setting was toggled
        }

        addCallback("apiUrl") {
            LOGGER.info("API URL changed to $apiUrl, refreshing API data...")
            PolyConnection.reconnect() // Reconnect WebSocket under new URL
            PolyPlusClient.refresh() // Refresh API tokens, cosmetic data, etc.
        }
    }

    @JvmStatic
    fun defaultMainMenuFpsLimit(): Int {
        val fallback = FALLBACK_MONITOR_REFRESH_RATE + MAIN_MENU_FPS_HEADROOM
        return runCatching {
            val refreshRate = Minecraft.getInstance().window.refreshRate
            if (refreshRate > 0) refreshRate + MAIN_MENU_FPS_HEADROOM else fallback
        }.getOrDefault(fallback)
    }

    @JvmStatic
    fun activeMainMenuFpsLimit(): Int =
        when (mainMenuFpsLimitMode) {
            MainMenuFpsLimitMode.VANILLA -> VANILLA_MAIN_MENU_FPS_LIMIT
            MainMenuFpsLimitMode.SMART -> defaultMainMenuFpsLimit()
            MainMenuFpsLimitMode.CUSTOM -> mainMenuFpsLimit
        }
}
