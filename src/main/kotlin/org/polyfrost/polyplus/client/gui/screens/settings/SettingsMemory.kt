package org.polyfrost.polyplus.client.gui.screens.settings

//? if > 1.8.9 {
import net.minecraft.client.gui.screens.Screen
//?} else {
/*import net.minecraft.client.gui.screen.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.polyplus.client.gui.pausescreen.OneClientPauseScreen
import org.polyfrost.polyplus.client.gui.pausescreen.PauseRouter
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal data class SettingsSelection(val category: String, val tab: String? = null)

object SettingsMemory {
    private var selection: SettingsSelection? = null
    private var host: Screen? = null
    private var target: Screen? = null
    private var liveScreen: Screen? = null
    private var liveParent: Screen? = null
    private var liveSelection: SettingsSelection? = null
    private var awaitingClose = false
    private var hostInWorld = false

    fun leaving(entry: String, host: Screen?) = leaving(SettingsSelection(entry), host)

    internal fun leaving(selection: SettingsSelection, host: Screen?) {
        this.selection = selection
        this.host = host
        target = host
        hostInWorld = ClientPlatform.inWorld()
        awaitingClose = false
    }

    fun openFrom(host: Screen?, entry: String, screen: Screen) {
        leaving(entry, host)
        SettingsRoutes.pass(screen)
    }

    fun openClosingFrom(host: Screen?, entry: String, open: () -> Unit) {
        leaving(entry, host)
        returnOnClose(host, open)
    }

    internal fun showing(screen: Screen?, parent: Screen?, selection: SettingsSelection) {
        liveScreen = screen
        liveParent = parent
        liveSelection = selection
    }

    internal fun hidden(screen: Screen?) {
        if (liveScreen !== screen) return
        liveScreen = null
        liveParent = null
        liveSelection = null
    }

    fun openReturningTo(host: Screen?, open: () -> Unit) {
        val live = liveSelection
        if (host != null && host === liveScreen && live != null) leaving(live, liveParent) else leaving(SettingsCategory.Video.entryId, host)
        target = host
        awaitingClose = host != null
        if (host is OneClientPauseScreen || ClientPlatform.inWorld()) PauseRouter.holdPause()
        open()
    }

    fun returnOnClose(host: Screen?, open: () -> Unit) {
        awaitingClose = host != null && this.host === host
        if (host is OneClientPauseScreen) PauseRouter.holdPause()
        open()
    }

    @OptIn(ExperimentalOneConfigUiApi::class)
    @JvmStatic
    fun returning(): Screen? {
        if (!awaitingClose) return null
        awaitingClose = false
        if (!OneConfigUI.isOneConfigScreen(ClientPlatform.currentScreen())) return null
        if (ClientPlatform.inWorld() != hostInWorld) return null
        return target
    }

    internal fun restore(host: Any?): SettingsSelection {
        val saved = selection.takeIf { host != null && this.host === host }
        selection = null
        this.host = null
        target = null
        awaitingClose = false
        return saved ?: SettingsSelection(SettingsCategory.Video.entryId)
    }
}
