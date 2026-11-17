@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MenuBackdrop
import org.polyfrost.polyplus.client.gui.MenuGlass
import org.polyfrost.polyplus.client.gui.MenuPanorama
import org.polyfrost.polyplus.client.gui.MenuScale
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialWindowScrim
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.BackButton
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyDarkChrome
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalGlassRegistry
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.PageScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.PageTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.Wordmark
import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost
import org.polyfrost.polyplus.client.gui.pausescreen.pauseGlass
import org.polyfrost.polyplus.client.gui.scaledDensity
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.max

private const val REVEAL_MILLIS = 220
private const val REVEAL_LIFT = 12f
private const val MODAL_GROW = 0.04f
private const val WORLD_DIM = 0.45f
private const val OPAQUE_FLOOR = 0.94f
private val FrameMaxWidth = 1240.dp
private val HeaderHeight = 38.dp
private val NavHead = 34.dp

@Composable
fun ShellFrame(host: ScreenHost, title: String, content: @Composable () -> Unit) {
    val chrome = (host as? ShellScreen)?.chrome ?: true
    when (host.shell) {
        ScreenShell.Lobby -> PageViewport { LobbyFrame(title, host::back, chrome, content) }
        ScreenShell.Menu -> ModalViewport { MenuFrame(title, host::back, chrome, content) }
        ScreenShell.InGame -> ModalViewport { InGameFrame(title, host::back, chrome, content) }
    }
}

@Composable
internal fun ShellPanel(
    title: String,
    onBack: () -> Unit,
    shell: ScreenShell,
    modifier: Modifier = Modifier,
    close: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shellSurface(shell)
            .padding(start = if (close) 20.dp else 16.dp, end = if (close) 12.dp else 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(HeaderHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!close) BackButton(onBack)
            PageTitle(title, Modifier.weight(1f))
            if (close) IconButton(SOCIAL_ASSETS + "x-close.svg", "Close", onBack, tooltipPlacement = TooltipPlacement.BelowEnd)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

@Composable
private fun Modifier.shellSurface(shell: ScreenShell): Modifier {
    val shape = OneConfigTheme.shape(ShellModal.Radius)
    return when (shell) {
        ScreenShell.Lobby -> panel(ShellModal.Radius)
        ScreenShell.Menu -> {
            val surface = PanelSurface
            sceneFrost(shape)
                .clip(shape)
                .background(if (liquidGlassTheme) surface else surface.copy(alpha = max(surface.alpha, OPAQUE_FLOOR)), shape)
                .border(1.dp, SocialBorderColor, shape)
        }
        ScreenShell.InGame -> pauseGlass(ShellModal.Radius).clip(shape)
    }
}

@Composable
private fun RootSize(content: @Composable BoxScope.(widthPx: Float, heightPx: Float) -> Unit) {
    var measured by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { measured = it }) {
        val outer = LocalDensity.current
        val widthPx = if (measured.width > 0) measured.width.toFloat() else maxWidth.value * outer.density
        val heightPx = if (measured.height > 0) measured.height.toFloat() else maxHeight.value * outer.density
        if (usableSize(widthPx, heightPx)) content(widthPx, heightPx)
    }
}

@Composable
private fun PageViewport(content: @Composable BoxScope.() -> Unit) {
    RootSize { widthPx, heightPx ->
        val outer = LocalDensity.current
        val scale = MenuScale.scale(minOf(widthPx / LobbyLayout.DESIGN_WIDTH, heightPx / LobbyLayout.DESIGN_HEIGHT))
        val density = remember(scale, outer.fontScale) { Density(usableScale(scale), usableScale(outer.fontScale)) }
        CompositionLocalProvider(LocalDensity provides density, LocalRootSize provides Size(widthPx, heightPx)) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
private fun ModalViewport(content: @Composable BoxScope.() -> Unit) {
    RootSize { widthPx, heightPx ->
        CompositionLocalProvider(LocalDensity provides scaledDensity(ShellModal.UI_SCALE), LocalRootSize provides Size(widthPx, heightPx)) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
private fun rememberReveal(): Float {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val reveal by Motion.animateFloatAsState(if (shown) 1f else 0f, tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
    return reveal
}

@Composable
private fun BoxScope.PageContent(title: String, onBack: () -> Unit, chrome: Boolean, content: @Composable () -> Unit) {
    val reveal = rememberReveal()
    val motion = Modifier.graphicsLayer {
        alpha = reveal
        translationY = (1f - reveal) * REVEAL_LIFT * density
    }
    if (!chrome) {
        Box(Modifier.matchParentSize().then(motion)) { content() }
        return
    }
    LobbyDarkChrome { NavRow(Modifier.graphicsLayer { alpha = reveal }) }
    ShellPanel(
        title,
        onBack,
        ScreenShell.Lobby,
        Modifier
            .align(Alignment.TopCenter)
            .padding(start = LobbyLayout.Margin, end = LobbyLayout.Margin, top = LobbyLayout.Top, bottom = LobbyLayout.Margin)
            .widthIn(max = FrameMaxWidth)
            .fillMaxSize()
            .then(motion),
    ) { content() }
}

@Composable
private fun BoxScope.ModalContent(title: String, onBack: () -> Unit, shell: ScreenShell, chrome: Boolean, content: @Composable () -> Unit) {
    val reveal = rememberReveal()
    if (!chrome) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = reveal }) { content() }
        return
    }
    Box(
        Modifier
            .matchParentSize()
            .background(SocialWindowScrim)
            .notFocusable()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        ShellPanel(
            title,
            onBack,
            shell,
            Modifier
                .shellModalSize()
                .graphicsLayer {
                    alpha = reveal
                    val grow = 1f - MODAL_GROW * (1f - reveal)
                    scaleX = grow
                    scaleY = grow
                }
                .notFocusable()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            close = true,
        ) { content() }
    }
}

@Composable
private fun NavRow(modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().height(LobbyLayout.NavbarHeight).padding(horizontal = LobbyLayout.Margin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f)) { Wordmark() }
        Text(
            ClientPlatform.localPlayerName(),
            style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
        PlayerHead(playerId = ClientPlatform.localPlayerUuid(), size = NavHead, shape = headShape(NavHead))
    }
}

@Composable
private fun BoxScope.Shade() {
    LobbyDarkChrome {
        val ink = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(0f to ink.copy(alpha = 0.92f), 0.42f to ink.copy(alpha = 0.74f), 1f to ink.copy(alpha = 0f))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = 0.6f)))),
        )
    }
}

@Composable
private fun BoxScope.LobbyFrame(title: String, onBack: () -> Unit, chrome: Boolean, content: @Composable () -> Unit) {
    PageScrim()
    if (chrome) Shade()
    PageContent(title, onBack, chrome, content)
}

@Composable
private fun BoxScope.MenuFrame(title: String, onBack: () -> Unit, chrome: Boolean, content: @Composable () -> Unit) {
    val glass = remember { MenuGlass() }
    if (!MenuPanorama.panoramaBackdrop()) MenuBackdrop(Modifier.fillMaxSize(), glass)
    CompositionLocalProvider(LocalGlassRegistry provides glass) {
        Box(Modifier.fillMaxSize()) { ModalContent(title, onBack, ScreenShell.Menu, chrome, content) }
    }
}

@Composable
private fun BoxScope.InGameFrame(title: String, onBack: () -> Unit, chrome: Boolean, content: @Composable () -> Unit) {
    LobbyDarkChrome {
        val ink = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        Box(Modifier.matchParentSize().background(ink.copy(alpha = WORLD_DIM)))
        Shade()
        Box(Modifier.fillMaxSize()) { ModalContent(title, onBack, ScreenShell.InGame, chrome, content) }
    }
}
