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
        title = "Pause Screen",
        description = "Step Back shows your player in your world. Step Back (Flat) keeps the same menu over a blurred, dimmed view, for tight spaces. Compact keeps the world in view with a bar above the hotbar. Vanilla is the normal Minecraft pause screen.",
        options = ["Step Back", "Step Back (Flat)", "Compact", "Vanilla"],
    )
    var pauseScreenStyle: PauseScreenStyle = PauseScreenStyle.STEP_BACK_FLAT

    @JvmStatic
    @Switch(
        title = "Vanilla Pause Menu Player Preview",
        description = "When the pause screen is set to Vanilla, also show your player with Social and Wardrobe buttons beside it.",
    )
    var vanillaPausePlayerPanel = false

    @JvmStatic
    @Switch(
        title = "Replace Pause Menu LAN Button",
        description = "Replace the vanilla 'Open to LAN' pause menu button with the PolyPlus Host World flow, for hosting your current world over EOS P2P.",
    )
    var replacePauseLanButton = true

    init {
        preload()
        migrateFromSplitConfigs()
    }

    private fun migrateFromSplitConfigs() {
        if (migratedFromSplitConfigs) return
        loadPolyPlusOptions("${PolyPlusConstants.ID}.json", "pause screen", LOGGER, ::loadFrom)
        loadPolyPlusOptions("${PolyPlusConstants.ID}-mainmenu.json", "pause screen", LOGGER, ::loadFrom)
        migratedFromSplitConfigs = true
        save()
    }
}
