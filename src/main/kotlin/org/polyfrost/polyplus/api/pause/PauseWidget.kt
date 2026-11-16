package org.polyfrost.polyplus.api.pause

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.screens.Screen
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function

@ApiStatus.Experimental
interface PauseWidget {
    val id: String

    val title: String

    val icon: String

    fun isAvailable(): Boolean = true

    @Deprecated("The pause menu no longer shows a widget bar. Implement Card instead.")
    @Composable
    fun Bar(context: PauseWidgetContext)

    @Composable
    fun Card(context: PauseWidgetContext) {
        @Suppress("DEPRECATION")
        Bar(context)
    }

    val supportsPanel: Boolean get() = false

    @Deprecated("Pause widgets now live in one column. Panel still renders as a large card there.")
    @Composable
    fun Panel(context: PauseWidgetContext) {
        Card(context)
    }
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface PauseWidgetContext {
    val pauseScreen: Screen

    val reducedMotion: Boolean

    fun openScreen(factory: Function<Screen, Screen?>)

    fun closePauseScreen()
}
