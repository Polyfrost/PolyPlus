package org.polyfrost.polyplus.client.gui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.oneconfig.internal.ui.components.Icon
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.oneconfig.internal.ui.themes.LocalTheme
import org.polyfrost.oneconfig.internal.ui.themes.Theme
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

class PolyPlusMainMenuScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private var firstFrameDrawn = false

    override fun shouldCloseOnEsc(): Boolean = false

    //? if <26.1 {
    /*override fun render(ctx: net.minecraft.client.gui.GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
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
        if (mainMenuPanoramaEnabled()) {
            net.minecraft.client.Minecraft.getInstance().gameRenderer.getPanorama()
                .extractRenderState(ctx, width, height, true)
            ctx.blurBeforeThisStratum()
        }
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
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
            MainMenuServerPings.start(servers)
            while (true) {
                MainMenuServerPings.tick()
                pingTick++
                delay(200L)
            }
        }

        Theme {
            MainMenu(
                actions = MenuActions(
                    singleplayer = { mc.setScreen(net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this)) },
                    multiplayer = { mc.setScreen(net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this)) },
                    settings = {
                        //? if >= 26.1 {
                        mc.setScreen(net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options, false))
                        //?} else {
                        /*mc.setScreen(net.minecraft.client.gui.screens.options.OptionsScreen(this, mc.options))
                        *///?}
                    },
                    mods = { mc.setScreen(org.polyfrost.oneconfig.internal.ui.compose.impls.OneConfigUIScreen()) },
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
    private val started = Collections.newSetFromMap(IdentityHashMap<net.minecraft.client.multiplayer.ServerData, Boolean>())

    @Synchronized
    fun start(servers: List<net.minecraft.client.multiplayer.ServerData>) {
        servers.forEach { data ->
            if (started.add(data)) {
                //? if >= 1.21.11 {
                val elg = net.minecraft.server.network.EventLoopGroupHolder.remote(false)
                runCatching { pinger.pingServer(data, Runnable {}, Runnable {}, elg) }
                //?} else {
                /*runCatching { pinger.pingServer(data, Runnable {}, Runnable {}) }
                *///?}
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
private val PanelBackground = Color(0x80273137)
private val PanelBorder = Color(0x99FFFFFF)
private val ServerIconBackground = Color(0x33FFFFFF)
private val CloseBackground = Color(0x80FF4444)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xBFFFFFFF)

private val PanelShape = RoundedCornerShape(9.dp)
private val BorderWidth = 0.75.dp

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

@Composable
private fun MainMenu(
    actions: MenuActions,
    servers: List<net.minecraft.client.multiplayer.ServerData>,
    pingTick: Int,
    assetsReady: Boolean,
) {
    val time by rememberFxTime()
    val target = remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    val mouse by rememberParallaxOffset(target, ease = 0.02f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val pos = awaitPointerEvent(PointerEventPass.Initial).changes.lastOrNull()?.position
                        if (pos != null && size.width > 0 && size.height > 0) {
                            target.value = Offset((pos.x / size.width).coerceIn(0f, 1f), (pos.y / size.height).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            .drawBehind {
                if (mainMenuPanoramaEnabled()) {
                    drawPanoramaOverlay()
                } else {
                    drawMenuBackground(time, mouse)
                }
            },
    ) {
        CenterColumn(Modifier.align(Alignment.Center), actions, assetsReady)
        LeftColumn(Modifier.align(Alignment.CenterStart).padding(start = 48.dp), servers, pingTick, actions, assetsReady)
        RightColumn(Modifier.align(Alignment.CenterEnd).padding(end = 48.dp), assetsReady)
        WindowControls(Modifier.align(Alignment.TopEnd).padding(16.dp), actions, assetsReady)
        Footer(Modifier.fillMaxSize(), assetsReady)
    }
}

@Composable
private fun CenterColumn(modifier: Modifier, actions: MenuActions, assetsReady: Boolean) {
    Column(modifier = modifier.width(440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MainLogo(assetsReady)
        Spacer(Modifier.height(16.dp))
        MenuText("ONECLIENT", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = if (assetsReady) Outfit else FontFamily.Default)
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
private fun RightColumn(modifier: Modifier, assetsReady: Boolean) {
    Column(
        modifier = modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
            if (assetsReady) {
                PlayerPreview(
                    Modifier.fillMaxWidth().height(210.dp),
                    source = PlayerPreviewSource.LocalLive,
                )
            }
            Box(
                Modifier.fillMaxWidth().height(70.dp).align(Alignment.BottomCenter).drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, PageBackground)))
                },
            )
        }
        AccountPill(name = playerName(), assetsReady = assetsReady)
        PillButton("Social", ASSETS + "message-chat-circle.svg", Modifier.fillMaxWidth(), assetsReady)
        PillButton("Cosmetics", ASSETS + "diamond-01.svg", Modifier.fillMaxWidth(), assetsReady)
    }
}

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
private fun Footer(modifier: Modifier, assetsReady: Boolean) {
    Box(modifier) {
        Row(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MenuIcon(ASSETS + "footer-logo.svg", TextPrimary, Modifier.size(25.dp), assetsReady)
            FooterBrandText("Fabric 1.21.11", assetsReady)
        }
        MenuText(
            "Copyright Mojang AB, Do Not distribute!",
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun FooterBrandText(platform: String, assetsReady: Boolean) {
    val bodyFont = LocalTheme.current.typography.family

    BasicText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold, fontFamily = if (assetsReady) Outfit else FontFamily.Default)) {
                append("ONECLIENT")
            }
            withStyle(SpanStyle(color = TextSecondary, fontFamily = bodyFont)) {
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
private fun PillButton(label: String, icon: String, modifier: Modifier = Modifier, assetsReady: Boolean, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier
            .height(45.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, PanelBorder, PanelShape)
            .clickable(onClick = onClick)
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
            .border(BorderWidth, PanelBorder, PanelShape)
            .clickable(onClick = onClick)
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
            .border(BorderWidth, PanelBorder, PanelShape)
            .clickable(onClick = onClick)
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(45.dp)
            .clip(PanelShape)
            .background(PanelBackground)
            .border(BorderWidth, PanelBorder, PanelShape)
            .clickable {}
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        RasterImage(
            ASSETS + "avatar.png",
            Modifier.align(Alignment.CenterStart).size(28.dp).clip(RoundedCornerShape(3.dp)),
            assetsReady = assetsReady,
            contentScale = ContentScale.Crop,
        )
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
            .border(BorderWidth, PanelBorder, PanelShape)
            .clickable(onClick = onClick),
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
