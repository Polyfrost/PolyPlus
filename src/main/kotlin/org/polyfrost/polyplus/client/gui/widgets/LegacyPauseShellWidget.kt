package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.polyplus.api.pause.PauseWidget
import org.polyfrost.polyplus.api.pause.PauseWidgetContext
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.function.Function

internal class LegacyPauseShellWidget(
    val delegate: PauseWidget,
    override val id: String,
    override val title: String,
    override val icon: String,
) : ShellWidget {
    private val panel: Boolean = runCatching { delegate.supportsPanel }.getOrDefault(false)

    override val size: WidgetSize = if (panel) WidgetSize.CARD else WidgetSize.ROW

    override val surfaces: Set<WidgetSurface> = setOf(WidgetSurface.PAUSE)

    override fun isAvailable(surface: WidgetSurface): Boolean =
        surface == WidgetSurface.PAUSE && runCatching { delegate.isAvailable() }.getOrDefault(false)

    @Composable
    override fun Content(scope: WidgetScope) {
        val screen = ClientPlatform.currentScreen() ?: return
        val context = remember(scope, screen) { ShellPauseContext(scope, screen) }
        @Suppress("DEPRECATION")
        if (panel) delegate.Panel(context) else delegate.Card(context)
    }
}

private class ShellPauseContext(private val scope: WidgetScope, override val pauseScreen: Screen) : PauseWidgetContext {
    override val reducedMotion: Boolean get() = scope.reducedMotion

    override fun openScreen(factory: Function<Screen, Screen?>) = scope.openScreen { factory.apply(it) }

    override fun closePauseScreen() = scope.closeHost()
}
