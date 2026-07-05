package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Property.Display
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
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

    @JvmStatic @Switch(title = "Discord RPC")
    var isDiscordEnabled = true

    @JvmStatic
    @Switch(
        title = "PolyPlus User Indicators",
        description = "Show a badge on the nametag and tab list of players who are using PolyPlus.",
    )
    var showPolyPlusIndicator = true

    @JvmStatic
    @Dropdown(
        title = "Main Menu FPS Limit",
        description = "Choose how the PolyPlus main menu frame cap is selected.",
        options = ["Vanilla (60 FPS limit)", "Smart (monitor refresh rate + 60)", "Custom (15-260 value)"],
    )
    var mainMenuFpsLimitMode: MainMenuFpsLimitMode = MainMenuFpsLimitMode.SMART

    @JvmStatic
    @Slider(
        title = "Custom Main Menu FPS Limit",
        description = "Frame cap used when Main Menu FPS Limit is set to Custom.",
        min = 15f,
        max = MAX_CUSTOM_MAIN_MENU_FPS_LIMIT,
        step = 5f,
    )
    var mainMenuFpsLimit = 260

    @JvmStatic
    @Dropdown(
        title = "Menu Backdrop",
        description = "Choose what appears behind the PolyPlus main menu.",
        options = ["PolyPlus", "Minecraft Panorama"],
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
