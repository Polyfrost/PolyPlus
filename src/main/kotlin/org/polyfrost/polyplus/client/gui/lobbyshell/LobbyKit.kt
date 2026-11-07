@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MenuHeadCache
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialCardBackground
import org.polyfrost.polyplus.client.gui.SocialPopupBackground
import org.polyfrost.polyplus.client.gui.translucentSurfaces
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialWindowShape
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.defaultSkinFace
import org.polyfrost.polyplus.client.gui.ppShape
import org.polyfrost.polyplus.client.lobby.LobbyGlass
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

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

internal object LobbyOverlay {
    private val closers = ArrayList<() -> Unit>()

    val close: (() -> Unit)? get() = closers.lastOrNull()

    fun push(close: () -> Unit) {
        closers += close
    }

    fun remove(close: () -> Unit) {
        closers -= close
    }
}

@Composable
internal fun EscapeCloses(onClose: () -> Unit) {
    val latest by rememberUpdatedState(onClose)
    DisposableEffect(Unit) {
        val close = { latest() }
        LobbyOverlay.push(close)
        onDispose { LobbyOverlay.remove(close) }
    }
}

internal val LocalRootSize = staticCompositionLocalOf { Size.Zero }

private const val HOVER_LINGER_MILLIS = 1400L

private fun Modifier.glassBounds(root: Size, radiusPx: Float, register: (LobbyGlass.Panel) -> Unit): Modifier = onGloballyPositioned {
    val bounds = it.boundsInRoot()
    if (root.width > 0f && root.height > 0f) {
        register(
            LobbyGlass.Panel(
                bounds.center.x / root.width,
                bounds.center.y / root.height,
                bounds.width / 2f / root.width,
                bounds.height / 2f / root.height,
                radiusPx / root.height,
            ),
        )
    }
}

@Composable
internal fun Modifier.panel(radius: Dp = 20.dp): Modifier {
    val shape = ppShape(radius)
    val root = LocalRootSize.current
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val key = remember { Any() }
    DisposableEffect(key) { onDispose { LobbyGlass.remove(key) } }
    return this
        .glassBounds(root, radiusPx) { LobbyGlass.put(key, it) }
        .clip(shape)
        .background(PanelSurface)
        .border(1.dp, SocialBorderColor, shape)
}

@Composable
internal fun Modifier.sceneFrost(radius: Dp, floating: Boolean = true): Modifier {
    val root = LocalRootSize.current
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    val active = OneConfigTheme.current.translucentSurfaces && root != Size.Zero
    val key = remember { Any() }
    DisposableEffect(key, active) { onDispose { LobbyGlass.remove(key) } }
    if (!active) return this
    return glassBounds(root, radiusPx) { if (floating) LobbyGlass.putFloating(key, it) else LobbyGlass.put(key, it) }
}

@Composable
internal fun Modifier.popup(): Modifier {
    val shape = SocialWindowShape
    return this
        .sceneFrost(20.dp)
        .then(if (OneConfigTheme.current.shadowEnabled) Modifier.shadow(12.dp, shape, ambientColor = OneConfigTheme.current.shadowColor, spotColor = OneConfigTheme.current.shadowColor) else Modifier)
        .clip(shape)
        .background(SocialPopupBackground)
        .border(1.dp, SocialBorderColor, shape)
}

@Composable
internal fun Modifier.card(radius: Dp = 16.dp, selected: Boolean = false): Modifier {
    val shape = ppShape(radius)
    return this
        .clip(shape)
        .background(SocialCardBackground)
        .border(1.dp, if (selected) OneConfigTheme.accent else SocialBorderColor, shape)
}

@Composable
internal fun lobbyHover(name: String?): Modifier {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
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
    return Modifier.hoverable(source)
}

@Composable
internal fun Label(text: String, size: TextUnit, modifier: Modifier = Modifier, color: Color = SocialTextPrimary, weight: FontWeight = FontWeight.Normal) {
    SocialText(text, size, modifier, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun LobbyIcon(name: String, tint: Color = SocialTextPrimary, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    ThemedIcon("$SOCIAL_ASSETS$name.svg", tint, size, modifier)
}

@Composable
internal fun Head(seed: UUID, size: Dp, modifier: Modifier = Modifier, realSkin: Boolean = false, speaking: Boolean = false) {
    val fallback by produceState<ImageBitmap?>(null, seed) {
        value = withContext(Dispatchers.IO) { defaultSkinFace(seed) }
    }
    val face = (if (realSkin) MenuHeadCache.get(seed) else null) ?: fallback
    val shape = ppShape(if (size >= 32.dp) 8.dp else 6.dp)
    val clipped = modifier.requiredSize(size).clip(shape).background(OneConfigTheme.current.componentBackground)
    val base = if (speaking) clipped.border(1.dp, SocialSuccessColor, shape) else clipped
    face?.let {
        Image(it, null, base, contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
    } ?: Box(base)
}

@Composable
internal fun PlayButton(modifier: Modifier, label: String, sub: String?, onClick: () -> Unit) = PlayButton(modifier, label, sub, true, onClick)

@Composable
internal fun PlayButton(modifier: Modifier, label: String, sub: String?, zoom: Boolean, onClick: () -> Unit) {
    val focus = lobbyHover(if (zoom) ClientPlatform.localPlayerName() else null)
    val (interaction, hovered) = rememberSocialHover()
    val fill by animateColorAsState(if (hovered) lerp(OneConfigTheme.accent, Color.White, PLAY_HOVER_LIFT) else OneConfigTheme.accent)
    Column(
        modifier
            .then(focus)
            .height(LobbyLayout.PlayHeight)
            .clip(ppShape(12.dp))
            .background(fill)
            .hoverable(interaction)
            .clickableWithSound(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        val ink = OneConfigTheme.current.accentTextColor
        Label(label, 22.sp, color = ink, weight = FontWeight.Medium)
        sub?.let { Label(it, 12.sp, color = ink.copy(alpha = 0.8f)) }
    }
}

private const val PLAY_HOVER_LIFT = 0.1f

private const val HIGHLIGHT_HOVER_ALPHA = 0.08f
private const val HIGHLIGHT_PRESS_ALPHA = 0.14f
private const val HIGHLIGHT_FADE_MILLIS = 120

@Composable
internal fun LobbyHighlight(shape: Shape = SocialFieldShape, content: @Composable () -> Unit) {
    val tint = SocialTextPrimary
    val indication = remember(shape, tint) { ShapedHighlight(shape, tint) }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}

private class ShapedHighlight(private val shape: Shape, private val tint: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = HighlightNode(interactionSource, shape, tint)

    override fun equals(other: Any?): Boolean = other is ShapedHighlight && other.shape == shape && other.tint == tint

    override fun hashCode(): Int = 31 * shape.hashCode() + tint.hashCode()
}

private class HighlightNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
    private val tint: Color,
) : Modifier.Node(), DrawModifierNode {
    private val level = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            var hovers = 0
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release -> presses = (presses - 1).coerceAtLeast(0)
                    is PressInteraction.Cancel -> presses = (presses - 1).coerceAtLeast(0)
                    is HoverInteraction.Enter -> hovers++
                    is HoverInteraction.Exit -> hovers = (hovers - 1).coerceAtLeast(0)
                    else -> return@collect
                }
                val target = when {
                    presses > 0 -> HIGHLIGHT_PRESS_ALPHA
                    hovers > 0 -> HIGHLIGHT_HOVER_ALPHA
                    else -> 0f
                }
                coroutineScope.launch { level.animateTo(target, tween(HIGHLIGHT_FADE_MILLIS)) { invalidateDraw() } }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val alpha = level.value
        if (alpha > 0f) drawOutline(shape.createOutline(size, layoutDirection, this), tint.copy(alpha = alpha * tint.alpha))
    }
}
