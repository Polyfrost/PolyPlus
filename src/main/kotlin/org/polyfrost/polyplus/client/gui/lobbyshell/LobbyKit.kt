@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.floor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.delay
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.liquidGlass
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.components.popupSurface
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.translucentSurfaces
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialWindowShape
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.lobby.GlassRegistry
import org.polyfrost.polyplus.client.lobby.LobbyGlass
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform

internal object LobbyLayout {
    const val DESIGN_WIDTH = 1600f
    const val DESIGN_HEIGHT = 900f

    val Margin = 28.dp
    val Gap = 16.dp
    val Top = 72.dp
    val NavbarHeight = 64.dp
    val ChatWidth = 340.dp
    val ChatHeight = 236.dp
    val UnitWidth = 350.dp
    val UnitHeight = 340.dp
    val PlayHeight = 60.dp
    val BarHeight = 52.dp
    val BarStart = Margin + ChatWidth + Gap
    val BarEnd = Margin + UnitWidth + Gap
    val PageBottom = Margin + BarHeight + Gap
    val RightColumnBottom = Margin + UnitHeight + Gap
}

internal val LocalRootSize = staticCompositionLocalOf { Size.Zero }

internal val LocalGlassRegistry = staticCompositionLocalOf<GlassRegistry> { LobbyGlass }

private const val HOVER_LINGER_MILLIS = 1400L

private fun cornerRadius(shape: Shape, size: Size, direction: LayoutDirection, density: Density): Float =
    (shape.createOutline(size, direction, density) as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f

private fun Modifier.glassBounds(root: Size, shape: Shape, density: Density, direction: LayoutDirection, register: (LobbyGlass.Panel) -> Unit): Modifier = onGloballyPositioned {
    val bounds = it.boundsInRoot()
    if (root.width > 0f && root.height > 0f) {
        register(
            LobbyGlass.Panel(
                bounds.center.x / root.width,
                bounds.center.y / root.height,
                bounds.width / 2f / root.width,
                bounds.height / 2f / root.height,
                cornerRadius(shape, it.size.toSize(), direction, density) / root.height,
            ),
        )
    }
}

internal fun Modifier.centerVerticallyExact(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
    val offset = (height - placeable.height) / 2f
    val top = floor(offset).toInt()
    layout(constraints.constrainWidth(placeable.width), height) {
        placeable.placeWithLayer(0, top) { translationY = offset - top }
    }
}

@Composable
private fun Modifier.sceneGlass(shape: Shape, active: Boolean, floating: Boolean): Modifier {
    val root = LocalRootSize.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val key = remember { Any() }
    val registry = LocalGlassRegistry.current
    DisposableEffect(key, active, registry) { onDispose { registry.remove(key) } }
    if (!active) return this
    return glassBounds(root, shape, density, direction) { if (floating) registry.putFloating(key, it) else registry.put(key, it) }
}

@Composable
internal fun Modifier.panel(radius: Dp = 20.dp): Modifier {
    val shape = OneConfigTheme.shape(radius)
    val glass = liquidGlassTheme
    return this
        .sceneGlass(shape, !glass, floating = false)
        .then(if (glass) Modifier.liquidGlass(shape, frosted = true) else Modifier)
        .clip(shape)
        .background(PanelSurface)
        .border(1.dp, SocialBorderColor, shape)
}

@Composable
internal fun Modifier.sceneBlur(shape: Shape, floating: Boolean = true): Modifier {
    val active = !liquidGlassTheme && OneConfigTheme.current.translucentSurfaces && LocalRootSize.current != Size.Zero
    return sceneGlass(shape, active, floating)
}

@Composable
internal fun Modifier.sceneFrost(shape: Shape, floating: Boolean = true): Modifier {
    val glass = liquidGlassTheme
    val blurred = sceneBlur(shape, floating)
    return if (glass) liquidGlass(shape, frosted = true) else blurred
}

@Composable
internal fun Modifier.sceneFrost(radius: Dp, floating: Boolean = true): Modifier = sceneFrost(OneConfigTheme.shape(radius), floating)

@Composable
internal fun Modifier.popup(shape: Shape = SocialWindowShape): Modifier = sceneBlur(shape).popupSurface(shape, blur = false)

@Composable
internal fun Modifier.card(radius: Dp = 16.dp, selected: Boolean = false): Modifier =
    themedSurface(SurfaceKind.Card, OneConfigTheme.shape(radius), selected)

@Composable
internal fun lobbyHover(name: String?): Modifier {
    val hover = rememberHoverState()
    val hovered = hover.hovered
    LaunchedEffect(hovered) {
        if (hovered) {
            LobbyScene.focus(name)
        } else if (LobbyScene.focused == name) {
            delay(HOVER_LINGER_MILLIS)
            if (LobbyScene.focused == name) LobbyScene.focus(null)
        }
    }
    DisposableEffect(name) {
        onDispose { if (LobbyScene.focused == name) LobbyScene.clearFocus() }
    }
    return Modifier.hoverable(hover.interactionSource)
}

@Composable
internal fun LobbyIcon(name: String, tint: Color = SocialTextPrimary, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    ThemedIcon("$SOCIAL_ASSETS$name.svg", tint, size, modifier)
}

@Composable
internal fun headShape(size: Dp): Shape = OneConfigTheme.shape(if (size >= 32.dp) 8.dp else 6.dp)

@Composable
internal fun PlayButton(modifier: Modifier, label: String, sub: String?, onClick: () -> Unit) = PlayButton(modifier, label, sub, true, onClick)

@Composable
internal fun PlayButton(modifier: Modifier, label: String, sub: String?, zoom: Boolean, onClick: () -> Unit) {
    val focus = lobbyHover(if (zoom) ClientPlatform.localPlayerName() else null)
    val shape = OneConfigTheme.shape(PLAY_RADIUS)
    Column(
        modifier
            .then(focus)
            .height(LobbyLayout.PlayHeight)
            .then(if (liquidGlassTheme) Modifier.liquidGlass(shape, frosted = false) else Modifier)
            .clip(shape)
            .background(OneConfigTheme.current.accentBrush, shape)
            .oneConfigClickable(if (sub == null) label else "$label, $sub", onClick, shape = shape, recipe = ButtonDefaults.Lift),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        val ink = ButtonDefaults.contentColor(ControlStyle.Primary)
        Text(
            label,
            color = ink,
            style = OneConfigTheme.current.typography.body.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        sub?.let {
            Text(it, color = ink.copy(alpha = 0.8f), style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        }
    }
}

private val PLAY_RADIUS = 12.dp
