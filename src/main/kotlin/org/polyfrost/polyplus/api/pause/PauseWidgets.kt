package org.polyfrost.polyplus.api.pause

import org.jetbrains.annotations.ApiStatus
import org.polyfrost.polyplus.client.gui.pausescreen.PauseWidgetRegistry
import java.util.Optional

@ApiStatus.Experimental
object PauseWidgets {
    const val API_VERSION = 1

    @JvmStatic
    fun register(widget: PauseWidget): Boolean = PauseWidgetRegistry.register(widget)

    @JvmStatic
    fun unregister(id: String): Boolean = PauseWidgetRegistry.unregister(id)

    @JvmStatic
    fun get(id: String): Optional<PauseWidget> = Optional.ofNullable(PauseWidgetRegistry.find(id))

    @JvmStatic
    fun all(): List<PauseWidget> = PauseWidgetRegistry.all()

    @JvmStatic
    fun isBuiltIn(id: String): Boolean = PauseWidgetRegistry.isBuiltIn(id)

    @JvmStatic
    fun isAdded(id: String): Boolean = PauseWidgetRegistry.isAdded(id)
}
