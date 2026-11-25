package org.polyfrost.polyplus.client

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Property.Display
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.MainMenuBackground
import org.polyfrost.polyplus.client.gui.MenuPanorama
import org.polyfrost.polyplus.client.utils.ClientPlatform

//? if >= 1.21.11 {
import org.polyfrost.polyplus.client.gui.panorama.CustomPanorama
//?}

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

    @JvmStatic @Include
    var lastSeenNewsMillis = 0L

    @JvmStatic
    @Dropdown(
        title = "Main Menu",
        description = "Choose which main menu the game starts on.",
        options = ["Lobby", "OneClient v1", "Vanilla"],
    )
    var mainMenu: MainMenuChoice = MainMenuChoice.LOBBY

    @JvmStatic
    @Switch(
        title = "Hide Quickplay",
        description = "Hide the Quickplay recent servers panel in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuQuickplay = false

    @JvmStatic
    @Switch(
        title = "Swap Quickplay and Player Preview",
        description = "Show Quickplay on the right and your player preview on the left in the OneClient v1 main menu.",
        subcategory = "Elements",
    )
    var swapMainMenuSideColumns = false

    @JvmStatic
    @Switch(
        title = "Hide Alt Manager",
        description = "Hide the account/alt manager in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuAltManager = false

    @JvmStatic
    @Switch(
        title = "Hide Social Button",
        description = "Hide the Social tab in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuSocial = false

    @JvmStatic
    @Switch(
        title = "Hide Wardrobe Button",
        description = "Hide the Wardrobe tab in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuCosmetics = false

    @JvmStatic
    @Switch(
        title = "Hide Realms Button",
        description = "Hide the Realms button in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuRealms = false

    @JvmStatic
    @Switch(
        title = "Hide Host World Button",
        description = "Hide the Host World button in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuHostWorld = false

    @JvmStatic
    @Switch(
        title = "Hide Mod Buttons",
        description = "Hide the buttons for supported mods in the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuModButtons = false

    @JvmStatic
    @Switch(
        title = "Hide Footer",
        description = "Hide the footer row at the bottom of the lobby.",
        subcategory = "Elements",
    )
    var hideMainMenuFooter = false

    @JvmStatic
    @Switch(
        title = "Intro Animation",
        description = "Play the OneClient intro when the game starts, into the lobby, the PolyPlus menu or the vanilla menu.",
        subcategory = "Vanilla Style",
    )
    var introAnimation = true

    @JvmStatic
    @Switch(
        title = "OneClient Buttons In Vanilla Menu",
        description = "Add Social, Wardrobe, Host World, Accounts, Quickplay and Mods buttons to the vanilla main menu.",
        subcategory = "Vanilla Style",
    )
    var vanillaMenuButtons = true

    @JvmStatic
    @Switch(
        title = "Hide Essential's Title Screen Buttons",
        description = "With Essential installed, show only our menu on the title screen. Essential's Social, Wardrobe, Pictures and Settings can be added as buttons with Edit Main Menu.",
        subcategory = "Vanilla Style",
    )
    var hideEssentialTitleMenu = true

    @JvmStatic
    @Dropdown(
        title = "Vanilla Menu Layout",
        description = "Preset for the vanilla menu. Pick one, then move pieces around with Edit Main Menu.",
        options = ["Centered", "Essential style", "OneClient style"],
        subcategory = "Vanilla Style",
    )
    var vanillaMenuLayout: VanillaMenuLayout = VanillaMenuLayout.CENTERED

    @JvmStatic
    @Switch(
        title = "Top Right Dropdown",
        description = "Put the lobby, menu, notification and theme buttons in one dropdown. Turn off to show them all in a row.",
        subcategory = "Vanilla Style",
    )
    var vanillaMenuDropdown = true

    @JvmStatic
    @Switch(
        title = "Dark Vanilla Buttons",
        description = "Use the dark button pack in vanilla menus. Follows the light/dark theme button.",
        subcategory = "Vanilla Style",
    )
    var vanillaDarkTheme = false

    @JvmStatic
    @Switch(
        title = "Vanilla Singleplayer And Multiplayer",
        description = "Open the vanilla style world and server lists, also from the lobby and the PolyPlus menu.",
        subcategory = "Vanilla Style",
    )
    var vanillaListScreens = false

    @JvmStatic
    @Switch(
        title = "World Version Sections",
        description = "Split the world list into worlds for this version and worlds from other versions, and confirm before opening another version.",
        subcategory = "Vanilla Style",
    )
    var worldVersionSections = true

    @JvmStatic
    @Switch(
        title = "Multiplayer Tabs",
        description = "Show Servers, Friends and Discover tabs at the top of the vanilla multiplayer screen.",
        subcategory = "Vanilla Style",
    )
    var multiplayerTabs = true

    // the old menu editor's moved pieces (scope/id=x,y;...), moved to config/polyplus/menu-layout.json on first start
    @JvmStatic @Include
    var menuLayoutOffsets = ""

    // the old menu editor's hidden pieces (scope/id,...), moved to menu-layout.json too
    @JvmStatic @Include
    var menuHidden = ""

    // newest first, one address per line, at most 3
    @JvmStatic @Include
    var recentDirectConnects = ""

    @JvmStatic
    @Dropdown(
        title = "Main Menu FPS Limit",
        description = "Choose how the lobby frame cap is selected.",
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
        description = "Choose what appears behind the OneClient v1 main menu.",
        options = ["PolyPlus", "Minecraft Panorama", "Vanilla Panorama"],
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
        title = "Hide Panorama Overlay",
        description = "Remove the blue glow and darkening drawn on top of the panorama.",
        subcategory = "Background",
    )
    var hidePanoramaOverlay = false

    @JvmStatic
    @Switch(
        title = "Panorama In All Menus",
        description = "Keep the panorama behind every menu (settings, multiplayer, singleplayer, etc.) instead of the dirt background.",
        subcategory = "Background",
    )
    var panoramaInAllMenus = true

    //? if = 1.8.9 {
    /*@JvmStatic
    @Switch(
        title = "Modern In-Game Menus",
        description = "Use a flat darkened background behind in-game menus instead of the dark gradient and dirt.",
        subcategory = "Background",
    )
    var modernInGameMenus = true
    *///?}

    init {
        PolyPlusPauseConfig.preload()
        PolyPlusLobbyConfig.preload()
        preload()
        migrateFromLegacyConfig()

        addDependency("mainMenuFpsLimit", "Main Menu FPS Limit") {
            if (mainMenuFpsLimitMode == MainMenuFpsLimitMode.CUSTOM) Display.SHOWN else Display.DISABLED
        }

        addDependency("mainMenuBackground", "Main Menu") {
            if (mainMenu == MainMenuChoice.POLYPLUS) Display.SHOWN else Display.DISABLED
        }

        addDependency("customPanorama", "Custom Panorama is unavailable") {
            if (!customPanoramaSupported()) Display.HIDDEN
            else if (MenuPanorama.vanillaPanorama()) Display.DISABLED
            else Display.SHOWN
        }

        addCallback("customPanorama") {
            //? if >= 1.21.11
            if (customPanorama) CustomPanorama.initialize()
        }

        addDependency("hideMainMenuRealms", "Realms is unavailable") {
            if (realmsSupported()) Display.SHOWN else Display.HIDDEN
        }
    }

    private fun migrateFromLegacyConfig() {
        if (migratedFromLegacyConfig) return
        loadLegacyPolyPlusOptions("main menu", LOGGER, ::loadFrom)
        migratedFromLegacyConfig = true
        save()
    }

    @JvmStatic
    fun customPanoramaSupported(): Boolean =
        //? if >= 1.21.11 {
        CustomPanorama.isAvailable()
        //?} else {
        /*false
        *///?}

    @JvmStatic
    fun realmsSupported(): Boolean =
        //? if > 1.8.9 {
        runCatching { Minecraft.getInstance().allowsRealms() }.getOrDefault(false)
        //?} else {
        /*false // Realms isn't usable on 1.8.9
        *///?}

    @JvmStatic
    fun defaultMainMenuFpsLimit(): Int {
        val fallback = FALLBACK_MONITOR_REFRESH_RATE + MAIN_MENU_FPS_HEADROOM
        return runCatching {
            val refreshRate = ClientPlatform.monitorRefreshRate()
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

enum class MainMenuChoice {
    LOBBY,
    POLYPLUS,
    VANILLA,
}

enum class VanillaMenuLayout {
    CENTERED,
    SIDES,
    ONECLIENT,
}
