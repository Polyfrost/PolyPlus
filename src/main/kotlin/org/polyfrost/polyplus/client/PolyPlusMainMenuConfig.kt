package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.Property.Display
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.MainMenuBackground

private const val MAIN_MENU_FPS_HEADROOM = 60
private const val FALLBACK_MONITOR_REFRESH_RATE = 60
private const val VANILLA_MAIN_MENU_FPS_LIMIT = 60
private const val MAX_CUSTOM_MAIN_MENU_FPS_LIMIT = 260f

object PolyPlusMainMenuConfig : Config(
    "${PolyPlusConstants.ID}-mainmenu.json",
    "Main Menu (OneClient)",
    Category.OTHER,
) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var migratedFromLegacyConfig = false

    @JvmStatic
    @Switch(
        title = "Vanilla Main Menu",
        description = "Disable the PolyPlus main menu and use the vanilla Minecraft title screen instead.",
    )
    var useVanillaMainMenu = false

    @JvmStatic
    @Switch(
        title = "Hide Quickplay",
        description = "Hide the Quickplay recent servers panel on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuQuickplay = false

    @JvmStatic
    @Switch(
        title = "Hide Player Preview",
        description = "Hide the 3D player preview on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuPlayerPreview = false

    @JvmStatic
    @Switch(
        title = "Hide Alt Manager",
        description = "Hide the account/alt manager pill on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuAltManager = false

    @JvmStatic
    @Switch(
        title = "Hide Social Button",
        description = "Hide the Social button on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuSocial = false

    @JvmStatic
    @Switch(
        title = "Hide Cosmetics Button",
        description = "Hide the Cosmetics button on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuCosmetics = false

    @JvmStatic
    @Switch(
        title = "Hide Realms Button",
        description = "Hide the Realms button on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuRealms = false

    @JvmStatic
    @Switch(
        title = "Hide Host World Button",
        description = "Hide the Host World button (opens a world to LAN via e4mc) on the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuHostWorld = false

    @JvmStatic
    @Switch(
        title = "Hide Mod Buttons",
        description = "Hide the row of buttons for supported mods at the bottom of the PolyPlus main menu.",
        subcategory = "Elements",
    )
    var hideMainMenuModButtons = false

    @JvmStatic
    @Dropdown(
        title = "Main Menu FPS Limit",
        description = "Choose how the PolyPlus main menu frame cap is selected.",
        options = ["Vanilla (60 FPS limit)", "Smart (monitor refresh rate + 60)", "Custom (15-260 value)"],
        subcategory = "Performance",
    )
    var mainMenuFpsLimitMode: MainMenuFpsLimitMode = MainMenuFpsLimitMode.SMART

    @JvmStatic
    @Slider(
        title = "Custom Main Menu FPS Limit",
        description = "Frame cap used when Main Menu FPS Limit is set to Custom.",
        min = 15f,
        max = MAX_CUSTOM_MAIN_MENU_FPS_LIMIT,
        step = 5f,
        subcategory = "Performance",
    )
    var mainMenuFpsLimit = 260

    @JvmStatic
    @Dropdown(
        title = "Menu Backdrop",
        description = "Choose what appears behind the PolyPlus main menu.",
        options = ["PolyPlus", "Minecraft Panorama"],
        subcategory = "Background",
    )
    var mainMenuBackground: MainMenuBackground = MainMenuBackground.PANORAMA

    @JvmStatic
    @Switch(
        title = "Custom Panorama",
        description = "Use the PolyPlus panorama instead of the vanilla Minecraft one.",
        subcategory = "Background",
    )
    var customPanorama = true

    @JvmStatic
    @Switch(
        title = "Panorama In All Menus",
        description = "Keep the panorama behind every menu (settings, multiplayer, singleplayer, etc.) instead of the dirt background.",
        subcategory = "Background",
    )
    var panoramaInAllMenus = true

    init {
        preload()
        migrateFromLegacyConfig()

        addDependency("mainMenuFpsLimit", "Main Menu FPS Limit") {
            if (mainMenuFpsLimitMode == MainMenuFpsLimitMode.CUSTOM) Display.SHOWN else Display.DISABLED
        }

        addDependency("customPanorama", "Menu Backdrop") {
            if (!customPanoramaSupported()) Display.HIDDEN
            else if (mainMenuBackground == MainMenuBackground.PANORAMA) Display.SHOWN
            else Display.DISABLED
        }

        addCallback("customPanorama") {
            //? if >= 1.21.11
            if (customPanorama) org.polyfrost.polyplus.client.gui.panorama.CustomPanorama.initialize()
        }

        addDependency("hideMainMenuRealms", "Realms is unavailable") {
            if (realmsSupported()) Display.SHOWN else Display.HIDDEN
        }
    }

    private fun migrateFromLegacyConfig() {
        if (migratedFromLegacyConfig) return
        runCatching {
            Tree.beginFailureCollection()
            try {
                loadFrom(ConfigManager.active().folder.resolve("${PolyPlusConstants.ID}.json"))
            } finally {
                val failed = Tree.endFailureCollection()
                if (failed.isNotEmpty()) LOGGER.warn("Left {} main menu option(s) at their default", failed)
            }
        }.onFailure { LOGGER.warn("Could not migrate main menu options from the legacy PolyPlus config", it) }
        migratedFromLegacyConfig = true
        save()
    }

    @JvmStatic
    fun customPanoramaSupported(): Boolean =
        //? if >= 1.21.11 {
        org.polyfrost.polyplus.client.gui.panorama.CustomPanorama.isAvailable()
        //?} else {
        /*false
        *///?}

    @JvmStatic
    fun realmsSupported(): Boolean =
        runCatching { Minecraft.getInstance().allowsRealms() }.getOrDefault(false)

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
