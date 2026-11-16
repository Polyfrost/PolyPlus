@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.pausescreen

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.widgets.WidgetColumn
import org.polyfrost.polyplus.client.gui.widgets.WidgetScope
import org.polyfrost.polyplus.client.gui.widgets.WidgetSurface
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.slf4j.LoggerFactory

internal val PAUSE_WIDGET_WIDTH = 320.dp
internal val PAUSE_WIDGET_NARROW_WIDTH = 288.dp
internal val WIDGET_INSET = 12.dp
private val WIDGET_POPUP_GAP = 10.dp
private const val WIDGET_HOVER_ALPHA = 0.06f
private val LOGGER = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pause-widgets")

internal fun pauseWidgetWidth(narrow: Boolean): Dp = if (narrow) PAUSE_WIDGET_NARROW_WIDTH else PAUSE_WIDGET_WIDTH

private class PauseWidgetScope(private val screen: OneClientPauseScreen, override val width: Dp) : WidgetScope {
    override val surface: WidgetSurface get() = WidgetSurface.PAUSE

    override val reducedMotion: Boolean get() = Motion.reduced

    override fun openScreen(factory: (Screen) -> Screen?) {
        val next = try {
            factory(screen)
        } catch (t: Throwable) {
            LOGGER.error("A pause widget failed to build the screen it asked to open", t)
            return
        }
        ClientPlatform.setScreen(next)
    }

    override fun closeHost() = screen.closeIfCurrent()

    override fun openCustomize() {
        if (!screen.customizeOpen) screen.toggleCustomize()
    }
}

@Composable
internal fun PauseWidgetColumn(screen: OneClientPauseScreen, width: Dp, maxHeight: Dp, modifier: Modifier = Modifier) {
    val scope = remember(screen, width) { PauseWidgetScope(screen, width) }
    WidgetColumn(WidgetSurface.PAUSE, width, maxHeight, { radius -> pauseGlass(radius) }, scope, modifier)
}

@Composable
internal fun WidgetFace(
    height: Dp,
    modifier: Modifier = Modifier,
    label: String? = null,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(lit: Boolean) -> Unit,
) {
    val hover = rememberHoverState()
    val interaction = hover.interactionSource
    val lit = (hover.hovered || active) && onClick != null
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(if (lit) SocialTextPrimary.copy(alpha = WIDGET_HOVER_ALPHA) else Color.Transparent)
            .hoverable(interaction)
            .then(if (onClick != null) Modifier.oneConfigClickable(label, onClick, indication = false, interactionSource = interaction, focusRing = false) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        content(lit)
    }
}

private class WidgetAnchor(private val gap: Int, private val margin: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val above = anchorBounds.top - gap - popupContentSize.height
        if (anchorBounds.center.y > windowSize.height / 2 && above >= margin) {
            return IntOffset(anchorBounds.left.coerceIn(margin, maxX), above)
        }
        val x = if (anchorBounds.center.x > windowSize.width / 2) anchorBounds.left - gap - popupContentSize.width else anchorBounds.right + gap
        return IntOffset(x.coerceIn(margin, maxX), anchorBounds.top.coerceIn(margin, maxY))
    }
}

@Composable
internal fun rememberWidgetAnchor(): PopupPositionProvider {
    val density = LocalDensity.current
    val gap = with(density) { WIDGET_POPUP_GAP.roundToPx() }
    val margin = with(density) { POPUP_EDGE.roundToPx() }
    return remember(gap, margin) { WidgetAnchor(gap, margin) }
}
