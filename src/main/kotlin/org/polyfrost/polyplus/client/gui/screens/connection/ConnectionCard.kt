@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.connection

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import org.polyfrost.polyplus.client.PolyPlusScreensConfig
import org.polyfrost.polyplus.client.gui.screens.ScreenRouter
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.MenuScale
import org.polyfrost.polyplus.client.gui.PanelSurface
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.liquidGlassTheme
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyLayout
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.gui.lobbyshell.ServerIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbyshell.sceneFrost
import org.polyfrost.polyplus.client.gui.pausescreen.pauseGlass
import org.polyfrost.polyplus.client.gui.screens.ScreenShell
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize
import org.polyfrost.polyplus.client.lobby.LobbyGlass
import org.polyfrost.polyplus.client.lobby.LobbyScene
import androidx.compose.runtime.snapshots.Snapshot
//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?} elif > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}
//? if > 1.8.9 {
import net.minecraft.client.gui.screens.LevelLoadingScreen
//?} else {
/*import net.minecraft.client.gui.GuiElement
import net.minecraft.client.gui.screen.menu.multiplayer.DownloadingTerrainScreen
*///?}
//? if < 1.21.10 && > 1.8.9
/*import net.minecraft.client.gui.screens.ReceivingLevelScreen*/
import org.polyfrost.oneconfig.api.ui.v1.platform.ComposeHudLayer
import org.polyfrost.polyplus.client.gui.MenuBackdrop
import org.polyfrost.polyplus.client.gui.MenuGlass
import org.polyfrost.polyplus.client.gui.MenuPanorama
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalGlassRegistry
import org.polyfrost.polyplus.client.gui.scaledDensity
import org.polyfrost.polyplus.client.gui.screens.ShellModal
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlin.math.max

private val CardRadius = 20.dp
private val CardWidth = 420.dp
private val IconSize = 64.dp
private val StepHeight = 3.dp
private const val PULSE_MILLIS = 900
private const val OPAQUE_FLOOR = 0.94f
private const val SCENE_DIM = 0.22f
private const val SKY_FROST = 0.4f
private const val SETTLE_MILLIS = 300
private const val COVER_MILLIS = 400
private const val CARD_LEAD = 0.35f
private const val CARD_LIFT = 14f

internal class SceneHold(private val owner: Any) {
    val start = LobbyGlass.pageFrost.coerceIn(0f, 1f)

    var kept by mutableStateOf(false)
        private set

    @Volatile
    var frost = 0f

    private var frosting = false

    fun attach(shell: ScreenShell) {
        kept = shell == ScreenShell.Lobby && LobbyScene.keep(owner)
        frosting = frosting || kept
    }

    fun tick() {
        if (kept && !LobbyScene.held()) kept = false
    }

    fun frame() {
        if (kept && LobbyScene.held()) LobbyGlass.pageFrost = frost
    }

    private var pending: (() -> Unit)? = null

    val leaving: Boolean get() = pending != null

    fun leave(action: () -> Unit) {
        if (pending != null) return
        if (kept && LobbyScene.whiteOut()) pending = action else action()
    }

    fun settle() {
        val action = pending ?: return
        if (!LobbyScene.whitedOut() && LobbyScene.held()) return
        pending = null
        action()
    }

    //? if >= 26.1 {
    fun veil(graphics: GuiGraphicsExtractor, width: Int, height: Int) {
        if (!kept) return
        val alpha = LobbyScene.veil()
        if (alpha <= 0f) return
        graphics.nextStratum()
        graphics.fill(0, 0, width, height, ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or 0xFFFFFF)
    }
    //?} elif > 1.8.9 {
    /*fun veil(graphics: GuiGraphics, width: Int, height: Int) {
        if (!kept) return
        val alpha = LobbyScene.veil()
        if (alpha <= 0f) return
        //? if >= 1.21.8 {
        graphics.nextStratum()
        //?} else {
        /^graphics.flush()
        ^///?}
        graphics.fill(0, 0, width, height, ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or 0xFFFFFF)
    }
    *///?} else {
    /*fun veil(width: Int, height: Int) {
        if (!kept) return
        val alpha = LobbyScene.veil()
        if (alpha <= 0f) return
        GuiElement.fill(0, 0, width, height, ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or 0xFFFFFF)
    }
    *///?}

    fun detach() {
        LobbyScene.letGo(owner)
        if (frosting) LobbyGlass.pageFrost = 0f
        frosting = false
    }
}

@Composable
internal fun HeldSceneFrame(hold: SceneHold, cover: Boolean = false, content: @Composable () -> Unit) {
    val fade = remember { Animatable(if (Motion.reduced) 1f else hold.start) }
    LaunchedEffect(Unit) {
        if (fade.value < 1f) fade.animateTo(1f, tween(SETTLE_MILLIS, easing = FastOutSlowInEasing))
    }
    val covered by Motion.animateFloatAsState(if (cover) 1f else 0f, tween(COVER_MILLIS, easing = FastOutSlowInEasing))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        if (!usableSize(widthPx, heightPx)) return@BoxWithConstraints
        val outer = LocalDensity.current
        val scale = MenuScale.scale(minOf(widthPx / LobbyLayout.DESIGN_WIDTH, heightPx / LobbyLayout.DESIGN_HEIGHT))
        val density = remember(scale, outer.fontScale) { Density(usableScale(scale), usableScale(outer.fontScale)) }
        val ink = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        CompositionLocalProvider(LocalDensity provides density, LocalRootSize provides Size(widthPx, heightPx)) {
            Box(
                Modifier.fillMaxSize().drawBehind {
                    hold.frost = fade.value * SKY_FROST
                    drawRect(ink, alpha = maxOf(fade.value * SCENE_DIM, covered))
                },
            )
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val shown = ((fade.value - CARD_LEAD) / (1f - CARD_LEAD)).coerceIn(0f, 1f)
                    alpha = shown
                    translationY = (1f - shown) * CARD_LIFT * this.density
                },
            ) { content() }
        }
    }
}

@Composable
internal fun ConnectionCard(
    shell: ScreenShell,
    title: String,
    server: ServerData? = null,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit = {},
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = CardWidth).fillMaxWidth().connectionSurface(shell).padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (server?.iconBytes != null) {
                ServerIcon(server, 0, IconSize)
                Spacer(Modifier.height(16.dp))
            }
            Text(
                title,
                color = SocialTextPrimary,
                style = OneConfigTheme.current.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    color = OneConfigTheme.current.mutedText,
                    style = OneConfigTheme.current.typography.small,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            ScrollContainer(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = content)
            }
            Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
internal fun ConnectionSteps(label: String, step: Int, total: Int, progress: Float? = null) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            color = SocialTextSecondary,
            style = OneConfigTheme.current.typography.body,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val theme = OneConfigTheme.current
        val fill by Motion.animateFloatAsState(((step + (progress?.coerceIn(0f, 1f) ?: 1f)) / total).coerceIn(0f, 1f), tween(Motion.standard * 2))
        val shape = theme.shapes.pill
        Box(Modifier.fillMaxWidth().height(StepHeight).clip(shape).background(theme.componentBackground, shape)) {
            if (progress == null) {
                val done by Motion.animateFloatAsState((step.toFloat() / total).coerceIn(0f, 1f), tween(Motion.standard * 2))
                val pulse by Motion.animateInfiniteFloat(1f, 0.35f, infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Reverse))
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).graphicsLayer { alpha = pulse }.background(theme.accentBrush, shape))
                Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(theme.accentBrush, shape))
            } else {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(theme.accentBrush, shape))
            }
        }
    }
}

@Composable
internal fun ConnectionMessage(text: String) {
    Text(
        text,
        color = SocialTextSecondary,
        style = OneConfigTheme.current.typography.body,
        textAlign = TextAlign.Center,
    )
}

@Composable
internal fun ConnectionAction(label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(
        text = label,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        style = if (primary) ControlStyle.Primary else ControlStyle.Secondary,
        size = ControlSize.Large,
        enabled = enabled,
    )
}

@Composable
private fun Modifier.connectionSurface(shell: ScreenShell): Modifier {
    val shape = OneConfigTheme.shape(CardRadius)
    return when (shell) {
        ScreenShell.Lobby -> panel(CardRadius)
        ScreenShell.Menu -> {
            val surface = PanelSurface
            sceneFrost(shape)
                .clip(shape)
                .background(if (liquidGlassTheme) surface else surface.copy(alpha = max(surface.alpha, OPAQUE_FLOOR)), shape)
                .border(1.dp, SocialBorderColor, shape)
        }
        ScreenShell.InGame -> pauseGlass(CardRadius).clip(shape)
    }
}
object TerrainCard {
    class Claim internal constructor(
        internal val shell: ScreenShell,
        internal val title: String,
        internal val subtitle: String?,
        internal val server: ServerData?,
        internal val joining: Boolean,
    )

    @JvmField
    val PREPARING: Component = Component.literal("Preparing the world")

    @Volatile
    private var pendingWorld: Claim? = null

    private class Shown(val claim: Claim, val progress: Float?)

    private var shown by mutableStateOf<Shown?>(null)

    private var layer: ComposeHudLayer? = null

    @JvmStatic
    fun claim(fresh: Boolean): Claim? {
        val world = pendingWorld
        pendingWorld = null
        if (!fresh) return null
        return world ?: (ClientPlatform.currentScreen() as? ConnectingScreen)?.terrainClaim()
    }

    @JvmStatic
    fun noteWorldLoad(name: String) {
        ConnectionTarget.clear()
        val origin = ScreenRouter.originOf(ClientPlatform.currentScreen())
        pendingWorld = if (PolyPlusScreensConfig.customConnecting) Claim(origin ?: ScreenRouter.fallbackShell(), name, null, null, false) else null
    }

    @JvmStatic
    fun worldPending(): Boolean = pendingWorld != null

    @JvmStatic
    fun carry(claim: Claim?) {
        if (claim != null) pendingWorld = claim
    }

    fun forget() {
        pendingWorld = null
    }

    internal fun preparing(title: Component): Claim? = pendingWorld?.takeIf { title === PREPARING }

    //? if >= 26.1 {
    @JvmStatic
    fun draw(graphics: GuiGraphicsExtractor, claim: Claim, progress: Float): Boolean = hudLayer(claim, progress).draw(graphics)
    //?} elif > 1.8.9 {
    /*@JvmStatic
    fun draw(graphics: GuiGraphics, claim: Claim, progress: Float): Boolean = hudLayer(claim, progress).draw(graphics)
    *///?} else {
    /*@JvmStatic
    fun draw(claim: Claim, progress: Float): Boolean = hudLayer(claim, progress).draw()
    *///?}

    private fun hudLayer(claim: Claim, progress: Float): ComposeHudLayer {
        val next = progress.takeIf { it >= 0f }
        val current = shown
        if (current == null || current.claim !== claim || current.progress != next) shown = Shown(claim, next)
        Snapshot.sendApplyNotifications()
        return layer ?: ComposeHudLayer("polyplus_terrain") { Content() }.apply { acceptsInput = false }.also { layer = it }
    }

    @JvmStatic
    fun release() {
        shown = null
        val closing = layer ?: return
        layer = null
        Snapshot.sendApplyNotifications()
        runCatching { closing.close() }
    }

    @JvmStatic
    fun sweep(screen: Any?) {
        //? if >= 1.21.10 {
        if (layer != null && screen !is LevelLoadingScreen) release()
        //?} elif > 1.8.9 {
        /*if (layer != null && screen !is LevelLoadingScreen && screen !is ReceivingLevelScreen) release()
        *///?} else {
        /*if (layer != null && screen !is DownloadingTerrainScreen) release()
        *///?}
    }

    @Composable
    private fun Content() {
        val current = shown ?: return
        val claim = current.claim
        val glass = remember { MenuGlass() }
        val ink = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        CompositionLocalProvider(LocalGlassRegistry provides glass) {
            if (claim.shell == ScreenShell.Menu) {
                if (!MenuPanorama.panoramaBackdrop()) MenuBackdrop(Modifier.fillMaxSize(), glass)
            } else {
                Box(Modifier.fillMaxSize().background(ink))
            }
            CardViewport(claim.shell) { TerrainBody(claim, TERRAIN_LABEL, current.progress) }
        }
    }
}

@Composable
internal fun TerrainBody(claim: TerrainCard.Claim, label: String, progress: Float?) {
    val step = if (claim.joining) TERRAIN_STEP else 0
    val total = if (claim.joining) CONNECTION_STEPS else 1
    ConnectionCard(
        claim.shell,
        claim.title,
        claim.server,
        claim.subtitle,
        content = { ConnectionSteps(label, step, total, progress) },
    ) {
        if (claim.joining) {
            Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 0f }) {
                ConnectionAction(CommonComponents.GUI_CANCEL.string, enabled = false) {}
            }
        }
    }
}

internal const val TERRAIN_LABEL = "Loading terrain"

@Composable
private fun CardViewport(shell: ScreenShell, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        if (!usableSize(widthPx, heightPx)) return@BoxWithConstraints
        val outer = LocalDensity.current
        val density = if (shell == ScreenShell.Lobby) {
            val scale = MenuScale.scale(minOf(widthPx / LobbyLayout.DESIGN_WIDTH, heightPx / LobbyLayout.DESIGN_HEIGHT))
            remember(scale, outer.fontScale) { Density(usableScale(scale), usableScale(outer.fontScale)) }
        } else {
            scaledDensity(ShellModal.UI_SCALE)
        }
        CompositionLocalProvider(LocalDensity provides density, LocalRootSize provides Size(widthPx, heightPx)) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

//? if > 1.8.9 {
internal const val CONNECTION_STEPS = 5
internal const val TERRAIN_STEP = 4
//?} else {
/*internal const val CONNECTION_STEPS = 4
internal const val TERRAIN_STEP = 3
*///?}
