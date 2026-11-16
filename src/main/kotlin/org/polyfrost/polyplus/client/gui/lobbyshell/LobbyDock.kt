@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipBox
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private val DOCK_RADIUS = 20.dp
private val DOCK_INSET = 6.dp
private val DOCK_GAP = 3.dp
private val TILE_RADIUS = 14.dp
internal val DockTileWidth = LobbyLayout.BarHeight - DOCK_INSET * 2

@Composable
internal fun LobbyDock(lobby: LobbyShell, onTiles: (Int) -> Unit = {}) {
    if (lobby.page != LobbyPage.Home) return
    Row(Modifier.height(LobbyLayout.BarHeight).panel(DOCK_RADIUS).onSizeChanged { onTiles(it.width) }, verticalAlignment = Alignment.CenterVertically) {
        DockTile("Customize lobby", DockTileWidth, first = true, last = false, active = lobby.customizeOpen, onClick = lobby.customize::toggle) {
            LobbyIcon("image-01")
        }
        LobbyMusicButton(lobby, first = false, last = true)
    }
}

@Composable
internal fun DockTile(
    label: String,
    width: Dp,
    first: Boolean,
    last: Boolean,
    active: Boolean = false,
    tooltip: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val hover = rememberHoverState()
    val shape = OneConfigTheme.shape(TILE_RADIUS)
    TooltipBox(if (tooltip && !active) label else null, Modifier.fillMaxHeight(), TooltipPlacement.AboveCenter) {
        Box(
            Modifier
                .fillMaxHeight()
                .hoverable(hover.interactionSource)
                .oneConfigClickable(label, onClick, shape = shape)
                .padding(start = if (first) DOCK_INSET else DOCK_GAP, end = if (last) DOCK_INSET else DOCK_GAP, top = DOCK_INSET, bottom = DOCK_INSET)
                .width(width),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.matchParentSize().rowFrame(false, hover.hovered || active, shape))
            content()
        }
    }
}
