package org.polyfrost.polyplus.client.gui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.minecraft.client.gui.screens.Screen

enum class WidgetSurface { LOBBY, PAUSE }

enum class WidgetSize(val height: Dp) {
    ROW(52.dp),
    BLOCK(96.dp),
    CARD(124.dp),
}

interface WidgetScope {
    val surface: WidgetSurface
    val width: Dp
    val reducedMotion: Boolean
    fun openScreen(factory: (Screen) -> Screen?)
    fun closeHost()
    fun openCustomize()
}

interface ShellWidget {
    val id: String
    val title: String
    val icon: String
    val size: WidgetSize
    val surfaces: Set<WidgetSurface>
    val bleed: Boolean get() = false

    fun isAvailable(surface: WidgetSurface): Boolean = true

    @Composable
    fun Content(scope: WidgetScope)
}
