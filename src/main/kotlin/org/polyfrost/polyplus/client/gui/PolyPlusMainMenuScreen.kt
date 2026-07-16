package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.LocalUiOversample
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.host.E4mcSupport
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.oneconfig.internal.ui.themes.Accent
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class PolyPlusMainMenuScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private var firstFrameDrawn = false

    override fun shouldCloseOnEsc(): Boolean = false

    //? if <26.1 {
    /*override fun render(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        MenuBackgroundPass.enqueue(mainMenuPanoramaEnabled())
        if (mainMenuPanoramaEnabled()) {
            renderPanorama(ctx, tickDelta)
            if (firstFrameDrawn) {
                val gameRenderer = net.minecraft.client.Minecraft.getInstance().gameRenderer
                //? if <1.21.4 {
                /*gameRenderer.processBlurEffect(tickDelta)
                *///?} else {
                gameRenderer.processBlurEffect()
                //?}
            }
        }
        super.render(ctx, mouseX, mouseY, tickDelta)
        firstFrameDrawn = true
    }
    *///?} else {
    override fun extractRenderState(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        MenuBackgroundPass.enqueue(mainMenuPanoramaEnabled())
        if (mainMenuPanoramaEnabled()) {
            net.minecraft.client.Minecraft.getInstance().gameRenderer
                //? if >= 26.2 {
                /*.panorama()
                .extractRenderState(ctx, width, height)
                *///?} else {
                .getPanorama()
                .extractRenderState(ctx, width, height, true)
                //?}
            ctx.blurBeforeThisStratum()
        }
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }
    //?}

    //? if <26.1 {
    /*override fun renderBackground(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (mainMenuPanoramaEnabled()) return
        super.renderBackground(ctx, mouseX, mouseY, tickDelta)
    }
    *///?} else {
    override fun extractBackground(ctx: net.minecraft.client.gui.GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        if (mainMenuPanoramaEnabled()) return
        super.extractBackground(ctx, mouseX, mouseY, tickDelta)
    }
    //?}

    @Composable
    override fun compose() {
        val mc = net.minecraft.client.Minecraft.getInstance()
        var assetsReady by remember { mutableStateOf(false) }
        var servers by remember { mutableStateOf<List<net.minecraft.client.multiplayer.ServerData>>(emptyList()) }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            val serverLoad = async(Dispatchers.IO) {
                org.polyfrost.polyplus.client.PolyPlusRecentServers.displayServers()
            }
            val assetLoad = async(Dispatchers.IO) {
                MainMenuRasterAssets.preload()
                Outfit
            }
            servers = serverLoad.await()
            assetLoad.await()
            assetsReady = true
        }

        var pingTick by remember { mutableStateOf(0) }
        LaunchedEffect(servers) {
            if (servers.isEmpty()) return@LaunchedEffect
            MainMenuServerPings.start(this, servers)
            while (true) {
                MainMenuServerPings.tick()
                pingTick++
                delay(200L)
            }
        }

        Theme {
            MainMenu(
                screen = this,
                actions = MenuActions(
                    singleplayer = {
                        //? if >= 26.2 {
                        /*mc.gui.setScreen(net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))
                        *///?} else {
                        mc.setScreen(net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))
                        //?}
                    },
                    multiplayer = {
                        //? if >= 26.2 {
                        /*mc.gui.setScreen(net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this))
                        *///?} else {
                        mc.setScreen(net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this))
                        //?}
                    },
                    settings = {
                        //? if >= 26.1 {
                        //? if >= 26.2 {
                        /*mc.gui.setScreen(net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options, false))
                        *///?} else {
                        mc.setScreen(net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options, false))
                        //?}
                        //?} else {
                        /*mc.setScreen(net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options))
                        *///?}
                    },
                    mods = { PolyPlusOneConfigIntegration.openMods() },
                    fullscreen = { mc.window.toggleFullScreen() },
                    quit = { mc.stop() },
                    connect = { server -> connectTo(mc, server) },
                ),
                servers = servers,
                pingTick = pingTick,
                assetsReady = assetsReady,
            )
        }
    }

    private fun connectTo(mc: net.minecraft.client.Minecraft, server: net.minecraft.client.multiplayer.ServerData) {
        val address = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(server.ip)
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(this, mc, address, server, false, null)
    }
}

private object MainMenuServerPings {
    private val pinger = net.minecraft.client.multiplayer.ServerStatusPinger()
    private val started = Collections.newSetFromMap(ConcurrentHashMap<net.minecraft.client.multiplayer.ServerData, Boolean>())

    fun start(scope: CoroutineScope, servers: List<net.minecraft.client.multiplayer.ServerData>) {
        servers.forEach { data ->
            if (started.add(data)) {
                scope.launch(Dispatchers.IO) {
                    val ok = runCatching {
                        //? if >= 1.21.11 {
                        val elg = net.minecraft.server.network.EventLoopGroupHolder.remote(false)
                        pinger.pingServer(data, Runnable {}, Runnable {}, elg)
                        //?} else {
                        /*pinger.pingServer(data, Runnable {}, Runnable {})
                        *///?}
                    }.isSuccess
                    if (!ok) started.remove(data)
                }
            }
        }
    }

    @Synchronized
    fun tick() {
        runCatching { pinger.tick() }
    }
}

private object MainMenuRasterAssets {
    private val paths = listOf(
        "avatar.png",
        "hypixel.png",
        "server.png",
    ).map { ASSETS + it }
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun preload() {
        paths.forEach { load(it) }
    }

    fun cached(path: String): ImageBitmap? = cache[path]

    private fun load(path: String): ImageBitmap? =
        cache[path] ?: runCatching {
            val bytes = PolyPlusMainMenuScreen::class.java.getResourceAsStream("/$path")!!.use { it.readBytes() }
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()?.also { cache[path] = it }
}

private object MainMenuPlayerHead {
    @Volatile
    private var head: ImageBitmap? = null
    @Volatile
    private var requested = false
    private const val SIZE = 64

    fun get(): ImageBitmap? = head

    fun ensureLoaded() {
        if (requested) return
        requested = true
        Thread({ runCatching { load() }.onFailure { requested = false } }, "polyplus-menu-head").apply {
            isDaemon = true
            start()
        }
    }

    private fun load() {
        val mc = net.minecraft.client.Minecraft.getInstance()
        val url = skinUrl(mc) ?: return
        val skin = javax.imageio.ImageIO.read(java.net.URI(url).toURL()) ?: return
        head = buildFace(skin)
    }

    private fun skinUrl(mc: net.minecraft.client.Minecraft): String? {
        val service =
            //? if >= 1.21.10 {
            mc.services().sessionService
            //?} else {
            /*mc.minecraftSessionService
            *///?}
        val texture = runCatching { service.getTextures(mc.gameProfile).skin() }.getOrNull() ?: return null
        return texture.url
    }

    private fun buildFace(skin: java.awt.image.BufferedImage): ImageBitmap {
        val out = ByteArray(SIZE * SIZE * 4)
        for (y in 0 until SIZE) {
            val oy = y * 8 / SIZE
            for (x in 0 until SIZE) {
                val ox = x * 8 / SIZE
                val base = skin.getRGB(8 + ox, 8 + oy)
                val hat = runCatching { skin.getRGB(40 + ox, 8 + oy) }.getOrDefault(0)
                val argb = if ((hat ushr 24) != 0) hat else base
                val i = (y * SIZE + x) * 4
                out[i] = argb.toByte()               // B
                out[i + 1] = (argb ushr 8).toByte()  // G
                out[i + 2] = (argb ushr 16).toByte() // R
                out[i + 3] = 0xFF.toByte()           // A (face is opaque)
            }
        }
        return SkiaImage.makeRaster(org.jetbrains.skia.ImageInfo.makeN32Premul(SIZE, SIZE), out, SIZE * 4)
            .toComposeImageBitmap()
    }
}

private class MenuActions(
    val singleplayer: () -> Unit,
    val multiplayer: () -> Unit,
    val settings: () -> Unit,
    val mods: () -> Unit,
    val fullscreen: () -> Unit,
    val quit: () -> Unit,
    val connect: (net.minecraft.client.multiplayer.ServerData) -> Unit,
)

private const val ASSETS = "assets/polyplus/mainmenu/"

private val PageBackground = Color(0xFF11171C)
private val PreviewGradient = Color(0xFF0F1C33)
private val PanelBackground: Color
    @Composable get() = LocalTheme.current.componentBackground.copy(alpha = 0.5f)
private const val PanelBorderAngleDeg = 20.0

private val PanelBorderBrush: Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(PanelBorderAngleDeg)
        val ux = kotlin.math.cos(radians).toFloat()
        val uy = kotlin.math.sin(radians).toFloat()
        val len = size.width * ux + size.height * uy
        return LinearGradientShader(
            from = Offset.Zero,
            to = Offset(ux * len, uy * len),
            colors = listOf(
                Color.White.copy(alpha = 0.5f),
                Color.White.copy(alpha = 0.15f),
                Color.White.copy(alpha = 0.5f),
            ),
            colorStops = listOf(0f, 0.5f, 1f),
        )
    }
}
private val ServerIconBackground = Color(0x33FFFFFF)
private val CloseBackground = Color(0x80FF4444)
private val Color.asSelectedBackground: Color get() = copy(alpha = 0.22f)

private val Scrim = Color(0xB3000000)
private val WarnColor = Color(0xFFF5A623)
private val DangerColor = Color(0xFFFF5A5A)
private val TextPrimary: Color
    @Composable get() = LocalTheme.current.textColor
private val TextSecondary: Color
    @Composable get() = LocalTheme.current.textColorSecondary

private val PanelShape = RoundedCornerShape(9.dp)
private val BorderWidth = 1.5.dp

private val Outfit: FontFamily by lazy {
    runCatching {
        val bytes = PolyPlusMainMenuScreen::class.java
            .getResourceAsStream("/${ASSETS}font/Outfit-Bold.ttf")!!.use { it.readBytes() }
        FontFamily(Font("Outfit-Bold", bytes, FontWeight.Bold))
    }.getOrDefault(FontFamily.Default)
}

internal fun mainMenuPanoramaEnabled(): Boolean {
    return PolyPlusConfig.mainMenuBackground == MainMenuBackground.PANORAMA
}

private fun mcGuiScaleFactor(): Float {
    val mc = net.minecraft.client.Minecraft.getInstance()
    val current = mc.window.guiScale.toFloat()
    val auto = mc.window.calculateScale(0, mc.isEnforceUnicode).toFloat().coerceAtLeast(1f)
    return (current / auto).coerceIn(0.01f, 1f)
}

private fun Modifier.guiScaled(factor: Float, origin: TransformOrigin): Modifier =
    graphicsLayer {
        scaleX = factor
        scaleY = factor
        transformOrigin = origin
    }

@Composable
private fun MainMenu(
    screen: net.minecraft.client.gui.screens.Screen,
    actions: MenuActions,
    servers: List<net.minecraft.client.multiplayer.ServerData>,
    pingTick: Int,
    assetsReady: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val guiScale = mcGuiScaleFactor()
            val scale = minOf(maxWidth.value / BASE_WIDTH, maxHeight.value / BASE_HEIGHT) * guiScale
            CompositionLocalProvider(
                LocalUiOversample provides (LocalUiOversample.current * scale.coerceAtLeast(1f)),
            ) {
                val density = LocalDensity.current
                var rightColumnHeightPx by remember { mutableStateOf(0) }
                val rightColumnHeightDp = rightColumnHeightPx / density.density
                val columnBottomPadding = ((BASE_HEIGHT - rightColumnHeightDp) / 2f).coerceAtLeast(0f)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(BASE_WIDTH.dp, BASE_HEIGHT.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    CenterColumn(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = columnBottomPadding.dp),
                        actions,
                        assetsReady,
                    )
                    if (!PolyPlusConfig.hideMainMenuQuickplay) {
                        LeftColumn(Modifier.align(Alignment.CenterStart).padding(start = 48.dp), servers, pingTick, actions, assetsReady)
                    }
                    RightColumn(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 48.dp, bottom = columnBottomPadding.dp)
                            .onSizeChanged { rightColumnHeightPx = it.height },
                        assetsReady,
                        screen,
                    )
                }
                WindowControls(
                    Modifier.align(Alignment.TopEnd).padding(16.dp).guiScaled(scale, TransformOrigin(1f, 0f)),
                    actions,
                    assetsReady,
                )
                Footer(Modifier.fillMaxSize(), scale, assetsReady)
            }
        }
    }
}

private const val BASE_WIDTH = 1240f
private const val BASE_HEIGHT = 720f

@Composable
private fun CenterColumn(modifier: Modifier, actions: MenuActions, assetsReady: Boolean) {
    Column(modifier = modifier.width(440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MainLogo(assetsReady)
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            MenuText("ONECLIENT", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, color = Color(0x33000000), fontFamily = if (assetsReady) Outfit else FontFamily.Default, modifier = Modifier.offset(y = 3.dp))
            MenuText("ONECLIENT", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = if (assetsReady) Outfit else FontFamily.Default)
        }
        Spacer(Modifier.height(48.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton("Singleplayer", ASSETS + "user-01.svg", Modifier.fillMaxWidth(), assetsReady, actions.singleplayer)
            PillButton("Multiplayer", ASSETS + "users-01.svg", Modifier.fillMaxWidth(), assetsReady, actions.multiplayer)
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                PillButton("Settings", ASSETS + "settings-02.svg", Modifier.weight(1f), assetsReady, actions.settings)
                PillButton("Mods", ASSETS + "settings-04.svg", Modifier.weight(1f), assetsReady, actions.mods)
            }
        }
    }
}

@Composable
private fun MainLogo(assetsReady: Boolean) {
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        MenuIcon(ASSETS + "logo.svg", Color(0x33000000), Modifier.size(96.dp).offset(y = 3.dp), assetsReady)
        MenuIcon(ASSETS + "logo.svg", TextPrimary, Modifier.size(96.dp), assetsReady)
    }
}

@Composable
private fun LeftColumn(
    modifier: Modifier,
    servers: List<net.minecraft.client.multiplayer.ServerData>,
    pingTick: Int,
    actions: MenuActions,
    assetsReady: Boolean,
) {
    var expanded by remember { mutableStateOf(true) }
    Column(modifier = modifier.width(300.dp)) {
        DropdownPill(
            label = "Quickplay",
            leadingIcon = ASSETS + "log-in-04.svg",
            expanded = expanded,
            assetsReady = assetsReady,
            onClick = { expanded = !expanded },
        )
        @Suppress("UNUSED_EXPRESSION") pingTick
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                servers.forEach { server ->
                    Spacer(Modifier.height(12.dp))
                    ServerRow(
                        title = server.name,
                        subtitle = serverStatusText(server),
                        favicon = rememberFavicon(server.iconBytes),
                        fallbackPng = ASSETS + if (server.ip.contains("hypixel", true)) "hypixel.png" else "server.png",
                        assetsReady = assetsReady,
                        onClick = { actions.connect(server) },
                    )
                }
            }
        }
    }
}

private fun serverStatusText(server: net.minecraft.client.multiplayer.ServerData): String {
    val players = server.players
    return when {
        players != null -> "%,d players online".format(players.online())
        else -> server.ip
    }
}

@Composable
private fun RightColumn(modifier: Modifier, assetsReady: Boolean, screen: net.minecraft.client.gui.screens.Screen) {
    Column(
        modifier = modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!PolyPlusConfig.hideMainMenuPlayerPreview) {
        val hasHeadCosmetic = org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
            .localEquipped().equipped
            .containsKey(org.polyfrost.polyplus.client.network.http.responses.BodySlot.Hat)
        val previewHeight = if (hasHeadCosmetic) 270.dp else 210.dp
        val previewScale = if (hasHeadCosmetic) 0.82f else 1.05f
        val previewFadeStart = if (hasHeadCosmetic) 0.784f else 0.72243f
        Box(modifier = Modifier.fillMaxWidth().height(previewHeight)) {
            if (assetsReady) {
                PlayerPreview(
                    Modifier.fillMaxWidth().height(previewHeight),
                    source = PlayerPreviewSource.LocalLive,
                    bottomFade = Brush.verticalGradient(
                        previewFadeStart to PreviewGradient.copy(alpha = 0f),
                        1f to PreviewGradient.copy(alpha = 0.84f),
                    ),
                    modelScale = previewScale,
                    verticalAnchor = 1.0f,
                    initialYaw = 180f + 22.9f,
                    live = true,
                    bottomFadeFraction = 1f - previewFadeStart,
                )
            }
        }
        }
        if (!PolyPlusConfig.hideMainMenuAltManager) {
            AccountPill(name = playerName(), assetsReady = assetsReady)
        }
        if (!PolyPlusConfig.hideMainMenuHostWorld) {
            HostWorldButton(assetsReady, screen)
        }
        // if (!PolyPlusConfig.hideMainMenuSocial) {
        //     PillButton("Social", ASSETS + "message-chat-circle.svg", Modifier.fillMaxWidth(), assetsReady)
        // }
        if (!PolyPlusConfig.hideMainMenuCosmetics) {
            PillButton(
                "Cosmetics",
                ASSETS + "diamond-01.svg",
                Modifier.fillMaxWidth(),
                assetsReady,
                onClick = { PolyPlusOneConfigIntegration.openCosmetics() },
            )
        }
    }
}

@Composable
private fun HostWorldButton(assetsReady: Boolean, screen: net.minecraft.client.gui.screens.Screen) {
    val enabled = E4mcSupport.isPresent
    var showPopup by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(false) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { buttonSize = it }) {
        PillButton(
            label = "Host World",
            icon = ASSETS + "log-in-04.svg",
            modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f),
            assetsReady = assetsReady,
            onClick = {
                if (enabled) {
                    showPopup = true
                } else {
                    showHint = !showHint
                }
            },
        )
        if (showPopup && enabled) {
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showPopup = false },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Scrim)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { showPopup = false },
                    contentAlignment = Alignment.Center,
                ) {
                    HostWorldPopup(
                        screen = screen,
                        assetsReady = assetsReady,
                        onDismiss = { showPopup = false },
                    )
                }
            }
        }
        if (showHint && !enabled) {
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, buttonSize.height + 8),
                onDismissRequest = { showHint = false },
            ) {
                HintBubble("Install the e4mc mod to host worlds")
            }
        }
    }
}

@Composable
private fun HintBubble(text: String) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, PanelBorderBrush, PanelShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        MenuText(text, fontSize = 13.sp, color = TextSecondary)
    }
}

@Composable
private fun HostWorldPopup(
    screen: net.minecraft.client.gui.screens.Screen,
    assetsReady: Boolean,
    onDismiss: () -> Unit,
) {
    var worlds by remember { mutableStateOf<List<HostWorldManager.HostWorldEntry>?>(null) }
    var selected by remember { mutableStateOf<HostWorldManager.HostWorldEntry?>(null) }
    var gameMode by remember { mutableStateOf(net.minecraft.world.level.GameType.SURVIVAL) }
    var allowCheats by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = HostWorldManager.loadWorlds()
        worlds = loaded
        selected = loaded.firstOrNull()
    }
    LaunchedEffect(selected) {
        selected?.let { gameMode = it.gameMode }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.56f)
            .widthIn(max = 560.dp)
            .fillMaxHeight(0.86f)
            .clip(PanelShape)
            .background(PageBackground.copy(alpha = 0.9f))
            .border(BorderWidth, LocalTheme.current.borderColor, PanelShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MenuText("Host World", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }

        val loadedWorlds = worlds
        when {
            loadedWorlds == null -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    MenuText("Loading worlds…", fontSize = 15.sp, color = TextSecondary, fontWeight = FontWeight.Light)
                }
            }
            loadedWorlds.isEmpty() -> {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    MenuText("No singleplayer worlds found", fontSize = 15.sp, color = TextSecondary, fontWeight = FontWeight.Light)
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    loadedWorlds.forEach { entry ->
                        WorldRow(
                            entry = entry,
                            selected = entry.id == selected?.id,
                            assetsReady = assetsReady,
                            onClick = { selected = entry },
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { GameModeDropdown(gameMode, assetsReady) { gameMode = it } }
            Box(Modifier.weight(1f)) { CheatsToggle(allowCheats) { allowCheats = !allowCheats } }
        }

        val chosen = selected
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(
                label = "Cancel",
                icon = ASSETS + "x-close.svg",
                modifier = Modifier.weight(1f),
                assetsReady = assetsReady,
                onClick = onDismiss,
            )
            PillButton(
                label = "Host",
                icon = ASSETS + "log-in-04.svg",
                modifier = Modifier.weight(1f).alpha(if (chosen != null) 1f else 0.5f),
                assetsReady = assetsReady,
                onClick = {
                    if (chosen != null) {
                        onDismiss()
                        HostWorldManager.host(screen, chosen, gameMode, allowCheats)
                    }
                },
            )
        }
    }
}

@Composable
private fun WorldRow(
    entry: HostWorldManager.HostWorldEntry,
    selected: Boolean,
    assetsReady: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(PanelShape)
            .background(if (selected) Accent.asSelectedBackground else PanelBackground)
            .border(BorderWidth, if (selected) Accent else LocalTheme.current.borderColor, PanelShape)
            .clickableWithSound(onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val iconModifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
        val favicon = rememberFavicon(entry.iconBytes)
        if (favicon != null) {
            Image(favicon, contentDescription = null, modifier = iconModifier, contentScale = ContentScale.Crop)
        } else {
            Box(iconModifier.background(ServerIconBackground))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuText(entry.name, fontSize = 15.sp, fontWeight = FontWeight.Light)
            MenuText(worldSubtitle(entry), fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Light)
            MenuText(worldVersionLine(entry), fontSize = 12.sp, color = compatColor(entry.compat), fontWeight = FontWeight.Light)
        }
    }
}

@Composable
private fun compatColor(compat: HostWorldManager.Compat): Color = when (compat) {
    HostWorldManager.Compat.CURRENT -> TextSecondary
    HostWorldManager.Compat.OLDER -> WarnColor
    HostWorldManager.Compat.NEWER -> DangerColor
    HostWorldManager.Compat.INCOMPATIBLE -> DangerColor
}

@Composable
private fun GameModeDropdown(
    selected: net.minecraft.world.level.GameType,
    assetsReady: Boolean,
    onSelect: (net.minecraft.world.level.GameType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        net.minecraft.world.level.GameType.SURVIVAL,
        net.minecraft.world.level.GameType.CREATIVE,
        net.minecraft.world.level.GameType.ADVENTURE,
        net.minecraft.world.level.GameType.SPECTATOR,
    )
    Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .clip(PanelShape)
                .background(PanelBackground)
                .border(BorderWidth, LocalTheme.current.borderColor, PanelShape)
                .clickableWithSound { expanded = !expanded }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "gamemode-chevron")
            MenuText("Mode", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterStart))
            MenuText(gameModeLabel(selected), fontSize = 15.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
            MenuIcon(ASSETS + "chevron-up.svg", TextPrimary, Modifier.align(Alignment.CenterEnd).size(16.dp).rotate(chevronAngle), assetsReady)
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { mode ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(PanelShape)
                            .background(if (mode == selected) Accent.asSelectedBackground else PanelBackground)
                            .border(BorderWidth, if (mode == selected) Accent else LocalTheme.current.borderColor, PanelShape)
                            .clickableWithSound { onSelect(mode); expanded = false }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        MenuText(gameModeLabel(mode), fontSize = 14.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }
    }
}

@Composable
private fun CheatsToggle(checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, LocalTheme.current.borderColor, PanelShape)
            .clickableWithSound(onToggle)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuText("Allow Cheats", fontSize = 15.sp, fontWeight = FontWeight.Light, modifier = Modifier.weight(1f))
        val theme = LocalTheme.current
        val interaction = remember { MutableInteractionSource() }
        val isHovered by interaction.collectIsHoveredAsState()
        val boxColor by animateColorAsState(if (checked) Accent else theme.componentBackground, label = "cheatsBox")
        val boxBorder by animateColorAsState(
            when {
                checked -> Accent
                isHovered -> theme.textColorSecondary
                else -> theme.borderColor
            },
            label = "cheatsBoxBorder",
        )
        val tickColor = theme.textColor
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(theme.checkBoxShape)
                .background(boxColor)
                .border(1.5.dp, boxBorder, theme.checkBoxShape)
                .hoverable(interaction),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Canvas(Modifier.size(13.dp)) {
                    val w = size.width
                    val h = size.height
                    val tick = Path().apply {
                        moveTo(w * 0.2f, h * 0.52f)
                        lineTo(w * 0.42f, h * 0.72f)
                        lineTo(w * 0.8f, h * 0.3f)
                    }
                    drawPath(tick, color = tickColor, style = Stroke(width = w * 0.15f, cap = StrokeCap.Round))
                }
            }
        }
    }
}

private fun gameModeLabel(mode: net.minecraft.world.level.GameType): String =
    mode.getName().replaceFirstChar { it.uppercase() }

private fun worldSubtitle(entry: HostWorldManager.HostWorldEntry): String {
    if (entry.lastPlayed <= 0L) return entry.id
    val date = runCatching {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(entry.lastPlayed))
    }.getOrNull() ?: return entry.id
    return "${entry.id} ($date)"
}

private fun worldVersionLine(entry: HostWorldManager.HostWorldEntry): String =
    "${entry.versionName} · ${gameModeLabel(entry.gameMode)}"

@Composable
private fun WindowControls(modifier: Modifier, actions: MenuActions, assetsReady: Boolean) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(ASSETS + "moon-star.svg", assetsReady = assetsReady)
        NotificationBell(assetsReady = assetsReady)
        if (!ClientPlatform.isMac) {
            IconButton(ASSETS + "maximize-02.svg", assetsReady = assetsReady, onClick = actions.fullscreen)
        }
        IconButton(ASSETS + "x-close.svg", background = CloseBackground, assetsReady = assetsReady, onClick = actions.quit)
    }
}

@Composable
private fun Footer(modifier: Modifier, guiScale: Float, assetsReady: Boolean) {
    Box(modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 18.dp)
                .guiScaled(guiScale, TransformOrigin(0f, 1f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuIcon(ASSETS + "footer-logo.svg", TextPrimary, Modifier.size(25.dp), assetsReady)
            FooterBrandText(platformLabel(), assetsReady)
        }
        MenuText(
            "Copyright Mojang AB, Do Not distribute!",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 18.dp)
                .guiScaled(guiScale, TransformOrigin(1f, 1f)),
        )
    }
}

@Composable
private fun FooterBrandText(platform: String, assetsReady: Boolean) {
    val bodyFont = LocalTheme.current.typography.family
    val primary = TextPrimary
    val secondary = TextSecondary

    BasicText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = primary, fontWeight = FontWeight.Bold, fontFamily = if (assetsReady) Outfit else FontFamily.Default)) {
                append("ONECLIENT")
            }
            withStyle(SpanStyle(color = secondary, fontFamily = bodyFont)) {
                append("   ")
                append(platform)
            }
        },
        style = TextStyle(
            fontSize = 13.sp,
            fontFamily = bodyFont,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun PillButton(label: String, icon: String, modifier: Modifier = Modifier, assetsReady: Boolean, onClick: () -> Unit = {}, borderBrush: Brush = PanelBorderBrush) {
    Row(
        modifier = modifier
            .height(45.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, borderBrush, PanelShape)
            .clickableWithSound(onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon(icon, TextPrimary, Modifier.size(20.dp), assetsReady)
        MenuText(label, fontSize = 16.sp)
    }
}

@Composable
private fun DropdownPill(label: String, leadingIcon: String, expanded: Boolean, assetsReady: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, PanelBorderBrush, PanelShape)
            .clickableWithSound(onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
        MenuIcon(leadingIcon, TextPrimary, Modifier.align(Alignment.CenterStart).size(20.dp), assetsReady)
        MenuText(label, fontSize = 16.sp)
        MenuIcon(
            ASSETS + "chevron-up.svg",
            TextPrimary,
            Modifier.align(Alignment.CenterEnd).size(16.dp).rotate(chevronAngle),
            assetsReady,
        )
    }
}

@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    favicon: ImageBitmap?,
    fallbackPng: String,
    assetsReady: Boolean,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, PanelBorderBrush, PanelShape)
            .clickableWithSound(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val iconModifier = Modifier.size(42.dp).clip(RoundedCornerShape(6.dp))
        if (favicon != null) {
            Image(favicon, contentDescription = null, modifier = iconModifier, contentScale = ContentScale.Crop)
        } else {
            RasterImage(fallbackPng, iconModifier, assetsReady = assetsReady, contentScale = ContentScale.Crop)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuText(title, fontSize = 16.sp)
            MenuText(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        MenuIcon(ASSETS + "chevron-right.svg", TextSecondary, Modifier.size(20.dp).rotate(90f), assetsReady)
    }
}

@Composable
private fun AccountPill(name: String, assetsReady: Boolean) {
    var head by remember { mutableStateOf(MainMenuPlayerHead.get()) }
    LaunchedEffect(Unit) {
        MainMenuPlayerHead.ensureLoaded()
        while (head == null) {
            delay(150L)
            head = MainMenuPlayerHead.get() ?: continue
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, PanelBorderBrush, PanelShape)
            .clickable {}
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        val avatarModifier = Modifier.align(Alignment.CenterStart).size(28.dp).clip(RoundedCornerShape(3.dp))
        val currentHead = head
        if (currentHead != null) {
            Image(currentHead, contentDescription = null, modifier = avatarModifier, contentScale = ContentScale.Crop)
        } else {
            RasterImage(ASSETS + "avatar.png", avatarModifier, assetsReady = assetsReady, contentScale = ContentScale.Crop)
        }
        MenuText(name, fontSize = 16.sp)
        MenuIcon(ASSETS + "chevron-up.svg", TextPrimary, Modifier.align(Alignment.CenterEnd).size(16.dp).rotate(180f), assetsReady)
    }
}

@Composable
private fun IconButton(
    icon: String,
    background: Color = PanelBackground,
    assetsReady: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .size(45.dp)
            .clip(PanelShape)
            .background(background)
            .border(BorderWidth, PanelBorderBrush, PanelShape)
            .clickableWithSound(onClick),
        contentAlignment = Alignment.Center,
    ) {
        MenuIcon(icon, TextPrimary, Modifier.size(20.dp), assetsReady)
    }
}

@Composable
private fun NotificationBell(assetsReady: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    var bellSize by remember { mutableStateOf(IntSize.Zero) }
    Box {
        IconButton(
            ASSETS + "bell-01.svg",
            assetsReady = assetsReady,
            modifier = Modifier.onSizeChanged { bellSize = it },
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, bellSize.height + 12),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                NotificationsCenter()
            }
        }
    }
}

@Composable
private fun MenuText(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily = LocalTheme.current.typography.family,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        ),
    )
}

@Composable
private fun MenuIcon(path: String, color: Color, modifier: Modifier, assetsReady: Boolean) {
    if (assetsReady) {
        Icon(path, color, modifier)
    } else {
        Spacer(modifier)
    }
}

@Composable
private fun RasterImage(
    path: String,
    modifier: Modifier,
    assetsReady: Boolean,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
) {
    val bitmap = if (assetsReady) rememberRaster(path) else null
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = modifier, alignment = alignment, contentScale = contentScale)
    } else {
        Box(modifier.background(ServerIconBackground))
    }
}

@Composable
private fun rememberFavicon(bytes: ByteArray?): ImageBitmap? = remember(bytes) {
    if (bytes == null || bytes.isEmpty()) null
    else runCatching { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
}

@Composable
private fun rememberRaster(path: String): ImageBitmap? = remember(path) {
    MainMenuRasterAssets.cached(path)
}

private fun playerName(): String = runCatching {
    net.minecraft.client.Minecraft.getInstance().user.name
}.getOrDefault("Player")

private fun platformLabel(): String = runCatching {
    //? if fabric {
    val loaderName = "Fabric"
    val mcVersion = net.fabricmc.loader.api.FabricLoader.getInstance()
        .getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("")
    //?} else {
    /*val loaderName = "NeoForge"
    val mcVersion = net.minecraft.SharedConstants.getCurrentVersion().name
    *///?}
    if (mcVersion.isBlank()) loaderName else "$loaderName $mcVersion"
}.getOrDefault("Fabric")
