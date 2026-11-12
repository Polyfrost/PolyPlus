package org.polyfrost.polyplus.client

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.pausescreen.PauseScreenStyle

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

    @JvmStatic
    @Dropdown(
        title = "Style",
        description = "Picks how the pause screen looks.",
        options = ["Step Back", "Step Back (Flat)", "Compact", "Vanilla"],
        subcategory = "Style",
    )
    var pauseScreenStyle: PauseScreenStyle = PauseScreenStyle.STEP_BACK_FLAT

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
        title = "Host World button",
        description = "Swaps Open to LAN for Host World.",
        subcategory = "Menu",
    )
    var replacePauseLanButton = true

    @JvmStatic
    @Switch(
        title = "Recording widget",
        description = "Shows recording controls.",
        subcategory = "Widgets",
    )
    var pauseRecordingWidget = true

    @JvmStatic
    @Switch(
        title = "Performance widget",
        description = "Shows performance stats.",
        subcategory = "Widgets",
    )
    var pausePerformanceWidget = true

    @JvmStatic
    @Switch(
        title = "Scene check widget",
        description = "Rates your spot as a lobby backdrop.",
        subcategory = "Widgets",
    )
    var pauseSceneWidget = true

    init {
        preload()
        migrateFromSplitConfigs()
        hideIf("flatPausePlayerPreview") { pauseScreenStyle != PauseScreenStyle.STEP_BACK_FLAT }
        hideIf("vanillaPausePlayerPanel") { pauseScreenStyle != PauseScreenStyle.VANILLA }
        hideIf("compactPauseMenuPanel") { pauseScreenStyle != PauseScreenStyle.VANILLA }
        addDependency("compactPauseMenuPanel", "vanillaPausePlayerPanel", true)
    }

    private fun migrateFromSplitConfigs() {
        if (migratedFromSplitConfigs) return
        loadPolyPlusOptions("${PolyPlusConstants.ID}.json", "pause screen", LOGGER, ::loadFrom)
        loadPolyPlusOptions("${PolyPlusConstants.ID}-mainmenu.json", "pause screen", LOGGER, ::loadFrom)
        migratedFromSplitConfigs = true
        save()
    }
}
