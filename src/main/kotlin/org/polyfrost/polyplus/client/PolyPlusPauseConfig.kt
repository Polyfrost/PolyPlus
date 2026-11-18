package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.pausescreen.FocusPauseStyle
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle
import org.polyfrost.polyplus.client.gui.widgets.WidgetMigration

object PolyPlusPauseConfig : Config(
    "${PolyPlusConstants.ID}-pause.json",
    "Pause Screen (OneClient)",
    Category.OTHER,
) {
    @Transient
    private val LOGGER = LogManager.getLogger()

    @JvmStatic @Include
    var migratedFromSplitConfigs = false

    @JvmStatic @Include
    var pauseWidgets = ""

    @JvmStatic @Include
    var pauseWidgetPanels = ""

    @JvmStatic @Include
    var pauseWidgetOrder = ""

    @JvmStatic
    @Dropdown(
        title = "Style",
        description = "Picks how the pause screen looks.",
        options = ["Full", "Spotlight", "Compact", "Vanilla", "Classic"],
        subcategory = "Style",
    )
    var pauseScreenStyle: PauseScreenStyle = PauseScreenStyle.STEP_BACK_FLAT

    @JvmStatic
    @Dropdown(
        title = "Unfocused",
        description = "Picks how the pause screen looks when the game pauses because you clicked out of the window.",
        options = ["Same as above", "Full", "Spotlight", "Compact", "Vanilla", "Classic"],
        subcategory = "Style",
    )
    var focusLossPauseStyle: FocusPauseStyle = FocusPauseStyle.SAME

    @JvmStatic
    @Switch(
        title = "Always pan camera",
        description = "Moves the camera to your player when you pause, even with reduced motion on.",
        subcategory = "Style",
    )
    var spotlightCameraPan = false

    @JvmStatic
    @Switch(
        title = "Player preview",
        description = "Shows your player beside the menu.",
        subcategory = "Style",
    )
    var flatPausePlayerPreview = true

    @JvmStatic
    @Switch(
        title = "Player preview",
        description = "Shows your player with Social and Wardrobe beside the menu.",
        subcategory = "Style",
    )
    var vanillaPausePlayerPanel = false

    @JvmStatic
    @Switch(
        title = "Compact buttons",
        description = "Puts the buttons in rows under the menu instead.",
        subcategory = "Style",
    )
    var compactPauseMenuPanel = false

    @JvmStatic
    @Switch(
        title = "Vignette",
        description = "Darkens the top and bottom edges behind the menu.",
        subcategory = "Style",
    )
    var pauseVignette = true

    @JvmStatic
    @Switch(
        title = "Host World button",
        description = "Swaps Open to LAN for Host World.",
        subcategory = "Menu",
    )
    var replacePauseLanButton = true

    @JvmStatic @Include
    var pauseRecordingWidget = true

    @JvmStatic @Include
    var pausePerformanceWidget = true

    @JvmStatic @Include
    var pauseSceneWidget = true

    @JvmStatic @Include
    var widgetsMigrated = false

    init {
        preload()
        migrateFromSplitConfigs()
        migrateWidgets()
        hideIf("spotlightCameraPan") { pauseScreenStyle != PauseScreenStyle.STEP_BACK }
        hideIf("flatPausePlayerPreview") { pauseScreenStyle != PauseScreenStyle.STEP_BACK_FLAT }
        hideIf("vanillaPausePlayerPanel") { pauseScreenStyle != PauseScreenStyle.VANILLA }
        hideIf("compactPauseMenuPanel") { pauseScreenStyle != PauseScreenStyle.VANILLA }
        addDependency("compactPauseMenuPanel", "vanillaPausePlayerPanel", true)
        hideIf("pauseVignette") { !isStepBack(pauseScreenStyle) && !isStepBack(focusLossPauseStyle.style ?: pauseScreenStyle) }
    }

    private fun isStepBack(style: PauseScreenStyle): Boolean = style == PauseScreenStyle.STEP_BACK || style == PauseScreenStyle.STEP_BACK_FLAT

    private fun migrateFromSplitConfigs() {
        if (migratedFromSplitConfigs) return
        loadPolyPlusOptions("${PolyPlusConstants.ID}.json", "pause screen", LOGGER, ::loadFrom)
        loadPolyPlusOptions("${PolyPlusConstants.ID}-mainmenu.json", "pause screen", LOGGER, ::loadFrom)
        migratedFromSplitConfigs = true
        save()
    }

    private fun migrateWidgets() {
        if (widgetsMigrated) return
        pauseWidgets = WidgetMigration.pause(pauseWidgets, pauseWidgetPanels, pauseRecordingWidget, pausePerformanceWidget, pauseSceneWidget)
        widgetsMigrated = true
        save()
    }
}
