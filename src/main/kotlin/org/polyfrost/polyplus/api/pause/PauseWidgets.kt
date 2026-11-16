package org.polyfrost.polyplus.api.pause

import org.jetbrains.annotations.ApiStatus
import org.polyfrost.polyplus.client.gui.widgets.LegacyPauseShellWidget
import org.polyfrost.polyplus.client.gui.widgets.ShellWidgetRegistry
import org.polyfrost.polyplus.client.gui.widgets.WidgetSurface
import java.util.Optional

@ApiStatus.Experimental
object PauseWidgets {
    @JvmField
    val API_VERSION: Int = 1

    @JvmStatic
    fun register(widget: PauseWidget): Boolean = ShellWidgetRegistry.registerLegacy(widget)

    @JvmStatic
    fun unregister(id: String): Boolean {
        if (ShellWidgetRegistry.isBuiltIn(id)) return false
        if (ShellWidgetRegistry.find(id) !is LegacyPauseShellWidget) return false
        return ShellWidgetRegistry.unregister(id)
    }

    @JvmStatic
    fun get(id: String): Optional<PauseWidget> = Optional.ofNullable((ShellWidgetRegistry.find(id) as? LegacyPauseShellWidget)?.delegate)

    @JvmStatic
    fun all(): List<PauseWidget> = ShellWidgetRegistry.registered().mapNotNull { (it as? LegacyPauseShellWidget)?.delegate }

    @JvmStatic
    fun isBuiltIn(id: String): Boolean = ShellWidgetRegistry.isBuiltIn(id)

    @JvmStatic
    fun isAdded(id: String): Boolean = ShellWidgetRegistry.contains(WidgetSurface.PAUSE, id)
}
