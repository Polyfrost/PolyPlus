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

    @Composable
    fun Bar(context: PauseWidgetContext)

    @Composable
    fun Card(context: PauseWidgetContext) {
        Bar(context)
    }

    val supportsPanel: Boolean get() = false

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
