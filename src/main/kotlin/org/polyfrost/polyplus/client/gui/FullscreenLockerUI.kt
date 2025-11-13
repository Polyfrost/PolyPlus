package org.polyfrost.polyplus.client.gui

import net.minecraft.client.gui.GuiScreen
import org.polyfrost.oneconfig.api.ui.v1.OCPolyUIBuilder
import org.polyfrost.oneconfig.api.ui.v1.UIManager
import org.polyfrost.polyplus.PolyPlusConstants

object FullscreenLockerUI {
    private val INTENDED_WIDTH = 1920f
    private val INTENDED_HEIGHT = 1080f

    fun create(): GuiScreen {
        val uiManager = UIManager.INSTANCE
        val builder = OCPolyUIBuilder.create()
            .blurs()
            .atResolution(INTENDED_WIDTH, INTENDED_HEIGHT)
            .renderer(uiManager.renderer)
            .translatorDelegate("assets/${PolyPlusConstants.ID}")

        val polyUI = builder.make(
        )

        val screen = uiManager.createPolyUIScreen(polyUI, INTENDED_WIDTH, INTENDED_HEIGHT, false, true) { }
        polyUI.window = uiManager.createWindow()
        return screen
    }
}
