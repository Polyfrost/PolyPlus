@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.TabActivation
import org.polyfrost.oneconfig.api.ui.v1.components.TabBadgePlacement
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TabRow
import org.polyfrost.oneconfig.api.ui.v1.components.TabStyle
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.ModIntegrationButton
import org.polyfrost.polyplus.client.gui.ModIntegrationButtons
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.oneconfig.api.ui.v1.components.Text

private val NAV_UNDERLINE_WIDTH = 27.dp
private val NAV_UNDERLINE_HEIGHT = 2.dp

@Composable
internal fun Wordmark(modifier: Modifier = Modifier, mark: Modifier = Modifier, word: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(mark) { LobbyIcon("logo", size = 28.dp) }
        Box(word) { Text("ONECLIENT", style = OneConfigTheme.current.typography.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = Outfit), maxLines = 1, softWrap = false) }
    }
}

@Composable
internal fun <T> NavTabRow(tabs: List<TabItem<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    TabRow(
        tabs,
        selected,
        onSelect,
        modifier,
        style = TabStyle.Underline,
        size = ControlSize.Large,
        fill = false,
        track = false,
        underlineWidth = NAV_UNDERLINE_WIDTH,
        badgePlacement = TabBadgePlacement.Corner,
        activation = TabActivation.Manual,
        underlineThickness = NAV_UNDERLINE_HEIGHT,
    )
}

internal fun navTabBadge(unread: Boolean): String? = if (unread) "" else null

internal fun socialDotLabel(unread: Boolean, invites: Boolean = false): String? = when {
    unread && invites -> "Unread messages and invites"
    unread -> "Unread messages"
    invites -> "New invites"
    else -> null
}

internal fun navUnderline(coordinates: LayoutCoordinates, density: Density): StartupOverlay.Box {
    val bounds = coordinates.boundsInRoot()
    val width = with(density) { NAV_UNDERLINE_WIDTH.toPx() }
    val height = with(density) { NAV_UNDERLINE_HEIGHT.toPx() }
    return StartupOverlay.Box(bounds.center.x - width / 2f, bounds.bottom - height, width, height)
}

@Composable
internal fun rememberModMenu(): Pair<ModIntegrationButton, ImageBitmap?>? {
    val button = if (PolyPlusMainMenuConfig.hideMainMenuModButtons) null else ModIntegrationButtons.availableModMenu()
    val logo = remember(button) { button?.let { decodeModLogo(it.logo) } }
    return button?.let { it to logo }
}
