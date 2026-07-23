package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.roundToInt
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.LocalUiOversample
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.host.E4mcSupport
import org.polyfrost.polyplus.client.launcher.MicrosoftAuthException
import org.polyfrost.polyplus.client.launcher.OneLauncherAccounts
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

    private var menuGuiScale by mutableStateOf(mcGuiScale())

    private fun syncGuiScaleState() {
        val gs = mcGuiScale()
        if (gs != menuGuiScale) menuGuiScale = gs
    }

    override fun shouldCloseOnEsc(): Boolean = false

    //? if <26.1 {
    /*override fun render(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        syncGuiScaleState()
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
        syncGuiScaleState()
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
                guiScale = menuGuiScale,
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

private object MenuHeadCache {
    private val heads = ConcurrentHashMap<java.util.UUID, ImageBitmap>()
    private val requested: MutableSet<java.util.UUID> = Collections.newSetFromMap(ConcurrentHashMap())
    var version by mutableStateOf(0)
        private set

    fun get(id: java.util.UUID, name: String): ImageBitmap? {
        version
        heads[id]?.let { return it }
        if (requested.add(id)) {
            Thread({
                val face = runCatching { loadFaceByUuid(id) }.getOrNull()
                if (face != null) heads[id] = face else requested.remove(id)
                version++ // snapshot state is writable off-thread
            }, "polyplus-account-head").apply {
                isDaemon = true
                start()
            }
        }
        return heads[id]
    }
}

private const val MENU_HEAD_SIZE = 64

private fun loadFaceByUuid(uuid: java.util.UUID): ImageBitmap? {
    val url = mojangSkinUrl(uuid) ?: return defaultSkinFace(uuid)
    val skin = javax.imageio.ImageIO.read(java.net.URI(url).toURL()) ?: return defaultSkinFace(uuid)
    return buildFace(skin)
}

private fun defaultSkinFace(uuid: java.util.UUID): ImageBitmap? = runCatching {
    val skinAsset = net.minecraft.client.resources.DefaultPlayerSkin.get(uuid)
    //? if >= 1.21.10 {
    val location = skinAsset.body().texturePath()
    //?} else {
    /*val location = skinAsset.texture()
    *///?}
    val manager = net.minecraft.client.Minecraft.getInstance().resourceManager
    val resource = manager.getResource(location).orElse(null) ?: return null
    val skin = resource.open().use { javax.imageio.ImageIO.read(it) } ?: return null
    buildFace(skin)
}.getOrNull()

private fun mojangSkinUrl(uuid: java.util.UUID): String? = runCatching {
    val id = uuid.toString().replace("-", "")
    val body = httpGetString("https://sessionserver.mojang.com/session/minecraft/profile/$id")
        ?: return null
    val root = org.polyfrost.polyplus.client.PolyPlusClient.JSON.parseToJsonElement(body).jsonObject
    val props = root["properties"]?.jsonArray ?: return null
    val texturesValue = props.firstOrNull {
        it.jsonObject["name"]?.jsonPrimitive?.content == "textures"
    }?.jsonObject?.get("value")?.jsonPrimitive?.content ?: return null
    val decoded = String(
        java.util.Base64.getDecoder().decode(texturesValue),
        java.nio.charset.StandardCharsets.UTF_8,
    )
    org.polyfrost.polyplus.client.PolyPlusClient.JSON.parseToJsonElement(decoded)
        .jsonObject["textures"]?.jsonObject
        ?.get("SKIN")?.jsonObject
        ?.get("url")?.jsonPrimitive?.content
}.getOrNull()

private fun httpGetString(url: String): String? {
    val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
    return try {
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        if (conn.responseCode == 200) {
            conn.inputStream.bufferedReader(java.nio.charset.StandardCharsets.UTF_8).use { it.readText() }
        } else {
            null
        }
    } finally {
        conn.disconnect()
    }
}

private fun buildFace(skin: java.awt.image.BufferedImage): ImageBitmap {
    val size = MENU_HEAD_SIZE
    val out = ByteArray(size * size * 4)
    for (y in 0 until size) {
        val oy = y * 8 / size
        for (x in 0 until size) {
            val ox = x * 8 / size
            val base = skin.getRGB(8 + ox, 8 + oy)
            val hat = runCatching { skin.getRGB(40 + ox, 8 + oy) }.getOrDefault(0)
            val argb = if ((hat ushr 24) != 0) hat else base
            val i = (y * size + x) * 4
            out[i] = argb.toByte()               // B
            out[i + 1] = (argb ushr 8).toByte()  // G
            out[i + 2] = (argb ushr 16).toByte() // R
            out[i + 3] = 0xFF.toByte()           // A (face is opaque)
        }
    }
    return SkiaImage.makeRaster(org.jetbrains.skia.ImageInfo.makeN32Premul(size, size), out, size * 4)
        .toComposeImageBitmap()
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
private val SuccessColor = Color(0xFF4ADE80)
private val TextPrimary: Color
    @Composable get() = LocalTheme.current.textColor
private val TextSecondary: Color
    @Composable get() = LocalTheme.current.textColorSecondary

private val PanelShape: Shape
    @Composable
    @ReadOnlyComposable
    get() = ppShape(9.dp)
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

internal const val REFERENCE_GUI_SCALE = 2f

internal fun mcGuiScale(): Int = net.minecraft.client.Minecraft.getInstance().window.guiScale.toInt()

internal fun guiScaleFactorFor(guiScale: Int): Float = (guiScale / REFERENCE_GUI_SCALE).coerceIn(0.01f, 1f)

internal const val GUI_DENSITY_TRIM = 0.88f

private fun Modifier.guiScaled(factor: Float, origin: TransformOrigin): Modifier =
    graphicsLayer {
        scaleX = factor
        scaleY = factor
        transformOrigin = origin
    }

@Composable
private fun MainMenu(
    screen: net.minecraft.client.gui.screens.Screen,
    guiScale: Int,
    actions: MenuActions,
    servers: List<net.minecraft.client.multiplayer.ServerData>,
    pingTick: Int,
    assetsReady: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val containFit = minOf(maxWidth.value / BASE_WIDTH, maxHeight.value / BASE_HEIGHT)
            val scale = minOf(guiScale / REFERENCE_GUI_SCALE * GUI_DENSITY_TRIM, containFit)
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
                }
                if (!PolyPlusConfig.hideMainMenuQuickplay) {
                    LeftColumn(
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 50.dp)
                            .guiScaled(scale, TransformOrigin(0f, 0.5f)),
                        servers,
                        pingTick,
                        actions,
                        assetsReady,
                    )
                }
                RightColumn(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 50.dp)
                        .guiScaled(scale, TransformOrigin(1f, 0.5f))
                        .onSizeChanged { rightColumnHeightPx = it.height },
                    assetsReady,
                    screen,
                )
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
            MenuText("ONECLIENT", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, color = Color.White, fontFamily = if (assetsReady) Outfit else FontFamily.Default)
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
        MenuIcon(ASSETS + "logo.svg", Color.White, Modifier.size(96.dp), assetsReady)
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

    DisposableEffect(Unit) {
        org.polyfrost.polyplus.client.gui.preview.PlayerPreviewDim.push()
        onDispose { org.polyfrost.polyplus.client.gui.preview.PlayerPreviewDim.pop() }
    }

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
        val iconModifier = Modifier.size(48.dp).clip(ppShape(4.dp))
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
            MenuIcon(ASSETS + "footer-logo.svg", Color.White, Modifier.size(25.dp), assetsReady)
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
    val primary = Color.White
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
        val iconModifier = Modifier.size(42.dp).clip(ppShape(6.dp))
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
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var accounts by remember { mutableStateOf<List<OneLauncherAccounts.Account>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var errorSteps by remember { mutableStateOf<List<String>?>(null) }
    var loginSession by remember { mutableStateOf<org.polyfrost.polyplus.client.launcher.MicrosoftAuth.MicrosoftLoginSession?>(null) }
    var loginJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }
    var pillBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    suspend fun reload() {
        accounts = withContext(Dispatchers.IO) { OneLauncherAccounts.list() }
    }

    LaunchedEffect(open) {
        if (open) {
            error = null
            errorSteps = null
            reload()
        }
    }

    val onSwitch: (OneLauncherAccounts.Account) -> Unit = { account ->
        if (busy == null && !account.active) {
            scope.launch {
                busy = "Switching…"
                error = null
                errorSteps = null
                val ok = withContext(Dispatchers.IO) { OneLauncherAccounts.switchTo(account.id) }
                if (!ok) error = "Couldn't switch to that account"
                reload()
                busy = null
            }
        }
    }
    val onAddMicrosoft: () -> Unit = {
        if (busy == null) {
            loginJob = scope.launch {
                error = null
                errorSteps = null
                busy = "Starting Microsoft sign-in…"
                val session = runCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.beginLogin() } }
                    .onFailure {
                        error = it.message ?: "Couldn't start sign-in"
                        errorSteps = (it as? MicrosoftAuthException)?.stepsToFix?.takeIf { steps -> steps.isNotEmpty() }
                    }
                    .getOrNull()
                if (session == null) {
                    busy = null
                    loginJob = null
                    return@launch
                }
                loginSession = session
                ClientPlatform.openUri(session.browserAuthUrl)
                busy = "Waiting for you to finish signing in…"
                runCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.finishLogin(session) } }
                    .onFailure {
                        error = it.message ?: "Microsoft sign-in failed"
                        errorSteps = (it as? MicrosoftAuthException)?.stepsToFix?.takeIf { steps -> steps.isNotEmpty() }
                    }
                loginSession = null
                loginJob = null
                reload()
                busy = null
            }
        }
    }
    val onCancelLogin: () -> Unit = {
        loginJob?.cancel()
        loginSession?.let { runCatching { OneLauncherAccounts.cancelLogin(it) } }
        loginJob = null
        loginSession = null
        busy = null
        error = null
        errorSteps = null
    }
    val onRefresh: (OneLauncherAccounts.Account) -> Unit = { account ->
        if (busy == null) {
            scope.launch {
                busy = "Refreshing session…"
                error = null
                errorSteps = null
                runCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.refresh(account.id) } }
                    .onFailure {
                        error = it.message ?: "Couldn't refresh that account"
                        errorSteps = (it as? MicrosoftAuthException)?.stepsToFix?.takeIf { steps -> steps.isNotEmpty() }
                    }
                reload()
                busy = null
            }
        }
    }
    val onAddOffline: (String) -> Unit = { username ->
        if (busy == null) {
            scope.launch {
                busy = "Adding account…"
                error = null
                errorSteps = null
                runCatching { withContext(Dispatchers.IO) { OneLauncherAccounts.addOffline(username) } }
                    .onFailure { error = it.message ?: "Couldn't add that account" }
                reload()
                busy = null
            }
        }
    }
    val onRemove: (OneLauncherAccounts.Account) -> Unit = { account ->
        if (busy == null) {
            scope.launch {
                busy = "Removing account…"
                error = null
                errorSteps = null
                val ok = withContext(Dispatchers.IO) { OneLauncherAccounts.remove(account.id) }
                if (!ok) error = "Couldn't remove that account"
                reload()
                busy = null
            }
        }
    }

    val activeName = accounts?.firstOrNull { it.active }?.username ?: name
    val chevronRotation = if (open) 0f else 180f
    val localId = runCatching { net.minecraft.client.Minecraft.getInstance().user.profileId }.getOrNull()
    val head = localId?.let { MenuHeadCache.get(it, activeName) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { pillSize = it }
            .onGloballyPositioned { pillBounds = it.boundsInWindow() },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .clip(PanelShape)
                .background(PanelBackground)
                .border(BorderWidth, PanelBorderBrush, PanelShape)
                .clickableWithSound { open = !open }
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            val avatarModifier = Modifier.align(Alignment.CenterStart).size(28.dp).clip(ppShape(3.dp))
            val currentHead = head
            if (currentHead != null) {
                Image(currentHead, contentDescription = null, modifier = avatarModifier, contentScale = ContentScale.Crop)
            } else {
                RasterImage(ASSETS + "avatar.png", avatarModifier, assetsReady = assetsReady, contentScale = ContentScale.Crop)
            }
            MenuText(activeName, fontSize = 16.sp)
            MenuIcon(
                ASSETS + "chevron-up.svg",
                TextPrimary,
                Modifier.align(Alignment.CenterEnd).size(16.dp).rotate(chevronRotation),
                assetsReady,
            )
        }
        if (open) {
            val totalScale = if (pillSize.width > 0) pillBounds.width / pillSize.width else 1f
            val positionProvider = remember(pillBounds, totalScale) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val gap = (8f * totalScale).roundToInt()
                        return IntOffset(
                            (pillBounds.right - popupContentSize.width).roundToInt(),
                            (pillBounds.bottom + gap).roundToInt(),
                        )
                    }
                }
            }
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { if (busy == null) open = false },
                properties = PopupProperties(focusable = true, clippingEnabled = false),
            ) {
                AccountSwitcherPanel(
                    panelWidth = with(LocalDensity.current) { pillSize.width.toDp() },
                    scale = totalScale,
                    accounts = accounts,
                    assetsReady = assetsReady,
                    busy = busy,
                    error = error,
                    errorSteps = errorSteps,
                    loginSession = loginSession,
                    onSwitch = onSwitch,
                    onRemove = onRemove,
                    onRefresh = onRefresh,
                    onAddMicrosoft = onAddMicrosoft,
                    onCancelLogin = onCancelLogin,
                    onAddOffline = onAddOffline,
                )
            }
        }
        val session = loginSession
        if (session != null) {
            MicrosoftLoginPopup(
                code = session.userCode,
                verificationUri = session.verificationUri,
                browserAuthUrl = session.browserAuthUrl,
                status = busy,
                assetsReady = assetsReady,
                onCancel = onCancelLogin,
            )
        }
    }
}

@Composable
private fun AccountSwitcherPanel(
    panelWidth: Dp,
    scale: Float,
    accounts: List<OneLauncherAccounts.Account>?,
    assetsReady: Boolean,
    busy: String?,
    error: String?,
    errorSteps: List<String>?,
    loginSession: org.polyfrost.polyplus.client.launcher.MicrosoftAuth.MicrosoftLoginSession?,
    onSwitch: (OneLauncherAccounts.Account) -> Unit,
    onRemove: (OneLauncherAccounts.Account) -> Unit,
    onRefresh: (OneLauncherAccounts.Account) -> Unit,
    onAddMicrosoft: () -> Unit,
    onCancelLogin: () -> Unit,
    onAddOffline: (String) -> Unit,
) {
    var offlineEntry by remember { mutableStateOf(false) }
    val idle = busy == null

    Column(
        modifier = Modifier
            .width(panelWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(1f, 0f)
            }
            .clip(PanelShape)
            .background(PageBackground.copy(alpha = 0.96f))
            .border(BorderWidth, PanelBorderBrush, PanelShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuText(
            "Accounts",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Start),
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PanelBorderBrush))

        when {
            accounts == null -> MenuText(
                "Loading…",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.Start),
            )
            accounts.isEmpty() -> MenuText(
                "No accounts found in the launcher",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.Start),
            )
            else -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                accounts.forEach { account ->
                    AccountRow(
                        account = account,
                        assetsReady = assetsReady,
                        enabled = idle,
                        onClick = { onSwitch(account) },
                        onRemove = { onRemove(account) },
                        onRefresh = { onRefresh(account) },
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PanelBorderBrush))

        if (offlineEntry) {
            OfflineAccountEntry(
                enabled = idle,
                onSubmit = { username ->
                    offlineEntry = false
                    onAddOffline(username)
                },
                onCancel = { offlineEntry = false },
            )
        } else {
            AddAccountButton(
                icon = ASSETS + "log-in-04.svg",
                text = "Add Microsoft account",
                enabled = idle,
                assetsReady = assetsReady,
                onClick = onAddMicrosoft,
            )
            AddAccountButton(
                icon = ASSETS + "user-01.svg",
                text = "Add offline account",
                enabled = idle,
                assetsReady = assetsReady,
                onClick = { offlineEntry = true },
            )
        }

        if (busy != null) {
            MenuText(busy, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.align(Alignment.Start))
        }
        if (error != null) {
            MenuText(error, fontSize = 12.sp, color = DangerColor, modifier = Modifier.align(Alignment.Start))
        }
        if (!errorSteps.isNullOrEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                MenuText(
                    "What you can do:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start),
                )
                errorSteps.forEach { step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        MenuText("•", fontSize = 12.sp, color = TextSecondary)
                        MenuText(step, fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountRow(
    account: OneLauncherAccounts.Account,
    assetsReady: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRefresh: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var confirmRemove by remember { mutableStateOf(false) }
    val head = MenuHeadCache.get(account.id, account.username)
    val clickable = enabled && !account.active && !confirmRemove

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ppShape(8.dp))
            .background(if (hovered && clickable) LocalTheme.current.componentBackground.copy(alpha = 0.4f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (clickable) Modifier.clickableWithSound(onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val avatarModifier = Modifier.size(30.dp).clip(ppShape(4.dp))
        if (head != null) {
            Image(head, contentDescription = null, modifier = avatarModifier, contentScale = ContentScale.Crop)
        } else {
            RasterImage(ASSETS + "avatar.png", avatarModifier, assetsReady = assetsReady, contentScale = ContentScale.Crop)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuText(
                account.username,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Start),
            )
            val subtitle = when {
                confirmRemove -> "Remove this account?"
                account.expired -> "Session expired"
                account.active -> "Active"
                account.microsoft -> "Microsoft"
                else -> "Offline"
            }
            MenuText(
                subtitle,
                fontSize = 11.sp,
                color = when {
                    confirmRemove -> DangerColor
                    account.expired -> WarnColor
                    else -> TextSecondary
                },
                modifier = Modifier.align(Alignment.Start),
            )
        }
        if (confirmRemove) {
            AccountActionIcon(ASSETS + "check-circle.svg", DangerColor, enabled) {
                confirmRemove = false
                onRemove()
            }
            AccountActionIcon(ASSETS + "x-close.svg", TextSecondary, enabled) { confirmRemove = false }
        } else {
            if (account.active) {
                MenuIcon(ASSETS + "check-circle.svg", SuccessColor, Modifier.size(18.dp), assetsReady)
            }
            if (account.expired) {
                AccountActionIcon(ASSETS + "refresh-cw-01.svg", WarnColor, enabled, onRefresh)
            }
            if (hovered) {
                AccountActionIcon(ASSETS + "trash-01.svg", TextSecondary, enabled) { confirmRemove = true }
            }
        }
    }
}

@Composable
private fun AddAccountButton(
    icon: String,
    text: String,
    enabled: Boolean,
    assetsReady: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ppShape(8.dp))
            .background(if (hovered && enabled) LocalTheme.current.componentBackground.copy(alpha = 0.4f) else Color.Transparent)
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MenuIcon(icon, TextPrimary, Modifier.size(18.dp), assetsReady)
        MenuText(text, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun OfflineAccountEntry(
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    val bodyFont = LocalTheme.current.typography.family
    val valid = value.trim().length in 3..16

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(34.dp)
                .clip(ppShape(6.dp))
                .background(LocalTheme.current.componentBackground.copy(alpha = 0.5f))
                .border(BorderWidth, PanelBorderBrush, ppShape(6.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 16) value = it },
                singleLine = true,
                enabled = enabled,
                textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = bodyFont),
                cursorBrush = SolidColor(Accent),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        MenuText("Username", fontSize = 13.sp, color = TextSecondary)
                    }
                    inner()
                },
            )
        }
        AccountActionIcon(ASSETS + "check-circle.svg", SuccessColor, enabled && valid) {
            if (valid) onSubmit(value.trim())
        }
        AccountActionIcon(ASSETS + "x-close.svg", TextSecondary, enabled, onCancel)
    }
}

@Composable
private fun MicrosoftLoginPopup(
    code: String,
    verificationUri: String,
    browserAuthUrl: String,
    status: String?,
    assetsReady: Boolean,
    onCancel: () -> Unit,
) {
    DisposableEffect(Unit) {
        org.polyfrost.polyplus.client.gui.preview.PlayerPreviewDim.push()
        onDispose { org.polyfrost.polyplus.client.gui.preview.PlayerPreviewDim.pop() }
    }
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onCancel,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onCancel() },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .clip(PanelShape)
                    .background(PageBackground.copy(alpha = 0.96f))
                    .border(BorderWidth, PanelBorderBrush, PanelShape)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {}
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MenuText("Sign in to Microsoft", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = if (assetsReady) Outfit else FontFamily.Default)
                MenuText(
                    "We opened the Microsoft sign-in page in your browser. Finish there and you'll be brought back automatically.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                LoginModalButton(
                    label = "Open in browser again",
                    icon = ASSETS + "link-external-01.svg",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    assetsReady = assetsReady,
                    onClick = { ClientPlatform.openUri(browserAuthUrl) },
                )
                OrDivider()
                MenuText(
                    "Or enter this code at the Microsoft sign-in page:",
                    fontSize = 13.sp,
                    color = TextSecondary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ppShape(8.dp))
                        .background(LocalTheme.current.componentBackground.copy(alpha = 0.5f))
                        .border(BorderWidth, Accent, ppShape(8.dp))
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MenuText(code, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = if (assetsReady) Outfit else FontFamily.Default)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoginModalButton(
                        label = "Copy code",
                        icon = ASSETS + "copy-01.svg",
                        filled = false,
                        modifier = Modifier.weight(1f),
                        assetsReady = assetsReady,
                        onClick = {
                            runCatching {
                                net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(code)
                            }
                        },
                    )
                    LoginModalButton(
                        label = "Open in browser",
                        icon = ASSETS + "link-external-01.svg",
                        filled = true,
                        modifier = Modifier.weight(1f),
                        assetsReady = assetsReady,
                        onClick = { ClientPlatform.openUri(verificationUri) },
                    )
                }
                if (status != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LoadingSpinner(Modifier.size(14.dp))
                        MenuText(status, fontSize = 13.sp, color = TextSecondary)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(ppShape(6.dp))
                        .clickableWithSound(onCancel)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    MenuText("Cancel", fontSize = 14.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun LoginModalButton(
    label: String,
    icon: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    assetsReady: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (filled) Color.White else TextPrimary
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(PanelShape)
            .background(if (filled) Accent else PanelBackground)
            .border(BorderWidth, if (filled) SolidColor(Accent) else PanelBorderBrush, PanelShape)
            .clickableWithSound(onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuIcon(icon, contentColor, Modifier.size(16.dp), assetsReady)
        MenuText(label, fontSize = 14.sp, color = contentColor, maxLines = 1)
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(PanelBorderBrush))
        MenuText("or", fontSize = 12.sp, color = TextSecondary)
        Box(Modifier.weight(1f).height(1.dp).background(PanelBorderBrush))
    }
}

@Composable
private fun LoadingSpinner(modifier: Modifier) {
    // OneClient's Loading02 spoke icon (static, like OneClient).
    MenuIcon(ASSETS + "loading-02.svg", Accent, modifier, assetsReady = true)
}

@Composable
private fun DeviceCodeCard(
    code: String,
    verificationUri: String,
    assetsReady: Boolean,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MenuText(
            "We opened Microsoft sign-in in your browser. Finish there, or enter this code instead:",
            fontSize = 12.sp,
            color = TextSecondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ppShape(8.dp))
                .background(LocalTheme.current.componentBackground.copy(alpha = 0.5f))
                .border(BorderWidth, Accent, ppShape(8.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuText(code, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        }
        MenuText(verificationUri, fontSize = 11.sp, color = TextSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddAccountButton(
                icon = ASSETS + "copy-01.svg",
                text = "Copy code",
                enabled = true,
                assetsReady = assetsReady,
                onClick = {
                    runCatching {
                        net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(code)
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddAccountButton(
                icon = ASSETS + "link-external-01.svg",
                text = "Open in browser",
                enabled = true,
                assetsReady = assetsReady,
                onClick = { ClientPlatform.openUri(verificationUri) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddAccountButton(
                icon = ASSETS + "x-close.svg",
                text = "Cancel",
                enabled = true,
                assetsReady = assetsReady,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun AccountActionIcon(icon: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(ppShape(6.dp))
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (enabled) Modifier.clickableWithSound(onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        MenuIcon(icon, color, Modifier.size(18.dp), assetsReady = true)
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
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        softWrap = maxLines != 1,
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
