@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsMainScreen
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen
import net.minecraft.client.gui.screens.options.OptionsScreen
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.ServerList
import net.minecraft.client.multiplayer.ServerStatusPinger
import net.minecraft.client.multiplayer.resolver.ServerAddress
//?} else {
/*import net.minecraft.client.gui.screen.menu.multiplayer.MultiplayerScreen as JoinMultiplayerScreen
import net.minecraft.client.gui.screen.menu.options.OptionsScreen
import net.minecraft.client.gui.screen.menu.world.SelectWorldScreen
import net.minecraft.client.network.MultiplayerServerListPinger as ServerStatusPinger
import net.minecraft.client.options.ServerListEntry as ServerData
import net.minecraft.client.options.ServerList
import net.minecraft.realms.RealmsBridge
*///?}
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPopup
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.api.ui.v1.theme.ProvideOneConfigTheme
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.oneconfig.internal.ui.compose.ComposeScreen
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.RecentServer
import org.polyfrost.polyplus.client.featured.FeaturedServers
import org.polyfrost.polyplus.client.featured.MainMenuFeaturedServer
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalGlassRegistry
import org.polyfrost.polyplus.client.gui.lobbyshell.LocalRootSize
import org.polyfrost.polyplus.client.host.HostWorldManager
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.privacy.PrivacyConsent
import java.util.Base64
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

//? if >= 26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor
//?}

//? if >= 1.21.11 {
import net.minecraft.server.network.EventLoopGroupHolder
//?}

//? if < 26.1 && > 1.8.9 {
/*import net.minecraft.client.gui.GuiGraphics
*///?}

class PolyPlusMainMenuScreen : ComposeScreen(RenderMode.CONTINUOUS) {
    private val openedAt = System.currentTimeMillis()
    private var menuGuiScale by mutableStateOf(mcGuiScale())

    private fun syncGuiScaleState() {
        val gs = mcGuiScale()
        if (gs != menuGuiScale) menuGuiScale = gs
    }

    //? if > 1.8.9 {
    override fun shouldCloseOnEsc(): Boolean = false
    //?} else {
    /*override fun render(mouseX: Int, mouseY: Int, tickDelta: Float) {
        syncGuiScaleState()
        super.render(mouseX, mouseY, tickDelta)
    }
    *///?}

    //? if <26.1 && > 1.8.9 {
    /*override fun render(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
        syncGuiScaleState()
        super.render(ctx, mouseX, mouseY, tickDelta)
    }

    override fun renderBackground(ctx: GuiGraphics, mouseX: Int, mouseY: Int, tickDelta: Float) {
    }
    *///?} elif >= 26.1 {
    override fun extractRenderState(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
        syncGuiScaleState()
        super.extractRenderState(ctx, mouseX, mouseY, tickDelta)
    }

    override fun extractBackground(ctx: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, tickDelta: Float) {
    }
    //?}

    @Composable
    override fun compose() {
        val mc = Minecraft.getInstance()
        var quickplay by remember { mutableStateOf<List<QuickplayEntry>?>(null) }

        LaunchedEffect(Unit) {
            withFrameNanos { }
            launch(Dispatchers.IO) { FeaturedServers.warmUp() }
            val serverLoad = async(Dispatchers.IO) {
                val saved = runCatching { ServerList(mc).apply { load() } }.getOrNull()
                PolyPlusRecentServers.recent().take(QUICKPLAY_MAX).map { QuickplayEntry.Server(recentServerData(it, saved), it.lastPlayed) }
            }
            val worldLoad = async {
                HostWorldManager.loadWorlds()
                    .filter { it.compat != HostWorldManager.Compat.INCOMPATIBLE && !it.requiresConversion }
                    .map { QuickplayEntry.World(it) }
            }
            val assetLoad = async(Dispatchers.IO) {
                MainMenuRasterAssets.preload()
                Outfit
            }
            quickplay = (serverLoad.await() + worldLoad.await()).sortedByDescending { it.lastPlayed }.take(QUICKPLAY_MAX)
            assetLoad.await()
        }

        var pingTick by remember { mutableStateOf(0) }
        LaunchedEffect(quickplay) {
            MainMenuServerPings.start(this, quickplay.orEmpty().filterIsInstance<QuickplayEntry.Server>().map { it.data })
            while (true) {
                MainMenuServerPings.tick()
                pingTick++
                delay(200L)
            }
        }

        ProvideOneConfigTheme {
            MainMenu(
                screen = this,
                guiScale = menuGuiScale,
                actions = MenuActions(
                    singleplayer = {
                        //? if >= 26.2 {
                        mc.gui.setScreen(SelectWorldScreen(this))
                        //?} elif > 1.8.9 {
                        /*mc.setScreen(SelectWorldScreen(this))
                        *///?} else {
                        /*mc.openScreen(SelectWorldScreen(this))
                        *///?}
                    },
                    openWorld = { id ->
                        //? if = 1.8.9 {
                        /*HostWorldManager.openWorld(id)
                        *///?} else {
                        mc.createWorldOpenFlows().openWorld(id) {
                            //? if >= 26.2 {
                            mc.gui.setScreen(this)
                            //?} else {
                            /*mc.setScreen(this)
                            *///?}
                        }
                        //?}
                    },
                    multiplayer = {
                        //? if >= 26.2 {
                        mc.gui.setScreen(JoinMultiplayerScreen(this))
                        //?} elif > 1.8.9 {
                        /*mc.setScreen(JoinMultiplayerScreen(this))
                        *///?} else {
                        /*mc.openScreen(JoinMultiplayerScreen(this))
                        *///?}
                    },
                    realms = if (PolyPlusMainMenuConfig.realmsSupported() && !PolyPlusMainMenuConfig.hideMainMenuRealms) {
                        {
                            //? if >= 26.2 {
                            mc.gui.setScreen(RealmsMainScreen(this))
                            //?} elif > 1.8.9 {
                            /*mc.setScreen(RealmsMainScreen(this))
                            *///?} else {
                            /*RealmsBridge().createMainMenuScreen(this)
                            *///?}
                        }
                    } else null,
                    settings = {
                        //? if >= 26.3 {
                        mc.gui.setScreen(OptionsScreen(this, mc.options))
                        //?} elif >= 26.2 {
                        /*mc.gui.setScreen(OptionsScreen(this, mc.options, false))
                        *///?} elif >= 26.1 {
                        /*mc.setScreen(OptionsScreen(this, mc.options, false))
                        *///?} elif > 1.8.9 {
                        /*mc.setScreen(OptionsScreen(this, mc.options))
                        *///?} else {
                        /*mc.openScreen(OptionsScreen(this, mc.options))
                        *///?}
                    },
                    mods = { PolyPlusOneConfigIntegration.openMods() },
                    fullscreen = ::toggleFullscreen,
                    quit = { if (System.currentTimeMillis() - openedAt >= QUIT_COOLDOWN_MILLIS) mc.stop() },
                    connect = { server -> connectTo(mc, server) },
                ),
                quickplay = quickplay,
                pingTick = pingTick,
            )
            BadModsDialog()
        }
    }

    private fun recentServerData(server: RecentServer, saved: ServerList?): ServerData =
        saved?.let { list -> (0 until list.size()).map { list.get(it) }.firstOrNull { it.ip.equals(server.ip, ignoreCase = true) } } ?:
        //? if > 1.8.9 {
        ServerData(server.name, server.ip, ServerData.Type.OTHER)
        //?} else {
        /*ServerData(server.name, server.ip, false)
        *///?}

    private fun connectTo(mc: Minecraft, server: ServerData) {
        //? if > 1.8.9 {
        val address = ServerAddress.parseString(server.ip)
        ConnectScreen.startConnecting(this, mc, address, server, false, null)
        //?} else {
        /*mc.openScreen(ConnectScreen(this, mc, server))
        *///?}
    }
}

private object MainMenuServerPings {
    private const val REFRESH_AFTER_MILLIS = 5 * 60 * 1000L
    private val pinger = ServerStatusPinger()
    private val lastPinged = Collections.synchronizedMap(WeakHashMap<ServerData, Long>())

    fun start(scope: CoroutineScope, servers: List<ServerData>) {
        val now = System.currentTimeMillis()
        servers.forEach { data ->
            val last = lastPinged[data]
            if (last == null || now - last >= REFRESH_AFTER_MILLIS) {
                lastPinged[data] = now
                scope.launch(Dispatchers.IO) {
                    val ok = runCatching {
                        //? if >= 1.21.11 {
                        val elg = EventLoopGroupHolder.remote(false)
                        pinger.pingServer(data, Runnable {}, Runnable {}, elg)
                        //?} elif > 1.8.9 {
                        /*pinger.pingServer(data, Runnable {}, Runnable {})
                        *///?} else {
                        /*pinger.add(data)
                        *///?}
                    }.isSuccess
                    if (!ok) lastPinged.remove(data)
                }
            }
        }
    }

    @Synchronized
    fun tick() {
        runCatching { pinger.tick() }
    }
}

private class MenuActions(
    val singleplayer: () -> Unit,
    val openWorld: (String) -> Unit,
    val multiplayer: () -> Unit,
    val realms: (() -> Unit)?,
    val settings: () -> Unit,
    val mods: () -> Unit,
    val fullscreen: () -> Unit,
    val quit: () -> Unit,
    val connect: (ServerData) -> Unit,
)

internal fun mcGuiScale(): Int = Minecraft.getInstance().window.guiScale.toInt()

@Composable
private fun MainMenu(
    screen: Screen,
    guiScale: Int,
    actions: MenuActions,
    quickplay: List<QuickplayEntry>?,
    pingTick: Int,
) {
    var accountsOpen by remember { mutableStateOf(false) }
    val glass = remember { MenuGlass() }
    var rootSize by remember { mutableStateOf(Size.Zero) }
    Box(modifier = Modifier.fillMaxSize().onSizeChanged { rootSize = Size(it.width.toFloat(), it.height.toFloat()) }.trackScreenFocus()) {
        MenuBackdrop(Modifier.fillMaxSize(), glass)
        CompositionLocalProvider(LocalGlassRegistry provides glass, LocalRootSize provides rootSize) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val containFit = minOf(maxWidth.value / BASE_WIDTH, maxHeight.value / BASE_HEIGHT)
                val scale = minOf(guiScale / REFERENCE_GUI_SCALE * GUI_DENSITY_TRIM, containFit)
                val safeScale = usableScale(scale)
                val innerWidth = maxWidth.value / safeScale
                val innerHeight = maxHeight.value / safeScale
                val edgeX = { dp: Float -> minOf(dp / safeScale, dp + (innerWidth - BASE_WIDTH) / 2f).coerceAtLeast(0f).dp }
                val edgeY = { dp: Float -> minOf(dp / safeScale, dp + (innerHeight - BASE_HEIGHT) / 2f).coerceAtLeast(0f).dp }
                CompositionLocalProvider(LocalDensity provides scaledDensity(scale)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .requiredSize(BASE_WIDTH.dp, BASE_HEIGHT.dp),
                    ) {
                        CenterColumn(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = COLUMN_INSET.dp),
                            actions,
                        )
                    }
                    val sideTop = ((innerHeight - BASE_HEIGHT) / 2f + COLUMN_INSET).coerceAtLeast(0f).dp
                    val swapped = PolyPlusMainMenuConfig.swapMainMenuSideColumns
                    val quickplaySide = if (swapped) Alignment.TopEnd else Alignment.TopStart
                    val previewSide = if (swapped) Alignment.TopStart else Alignment.TopEnd
                    Column(
                        modifier = Modifier
                            .align(quickplaySide)
                            .padding(top = sideTop, start = edgeX(50f), end = edgeX(50f)),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        FeaturedServerCard(Modifier, pingTick, actions)
                        if (!PolyPlusMainMenuConfig.hideMainMenuQuickplay) {
                            LeftColumn(Modifier, quickplay, pingTick, actions)
                        }
                    }
                    RightColumn(
                        Modifier
                            .align(previewSide)
                            .padding(top = sideTop, start = edgeX(50f), end = edgeX(50f)),
                        screen,
                        onAccounts = { accountsOpen = true },
                    )
                    WindowControls(
                        Modifier.align(Alignment.TopEnd).padding(horizontal = edgeX(16f), vertical = edgeY(16f)),
                        actions,
                    )
                    if (!PolyPlusMainMenuConfig.hideMainMenuModButtons) {
                        ModIntegrationBar(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = edgeY(18f)),
                            screen,
                        )
                    }
                    if (!PolyPlusMainMenuConfig.hideMainMenuFooter) Footer(Modifier.fillMaxSize(), edgeX, edgeY)
                }
            }
            if (accountsOpen) AccountsDialog { accountsOpen = false }
        }
    }
}

private const val BASE_WIDTH = 1240f
private const val QUICKPLAY_MAX = 3
private const val QUIT_COOLDOWN_MILLIS = 500L

private sealed interface QuickplayEntry {
    val lastPlayed: Long

    class Server(val data: ServerData, override val lastPlayed: Long) : QuickplayEntry

    class World(val world: HostWorldManager.HostWorldEntry) : QuickplayEntry {
        override val lastPlayed get() = world.lastPlayed
    }
}
private const val BASE_HEIGHT = 720f
private const val FULL_SIDE_COLUMN_HEIGHT = 446f
private const val COLUMN_INSET = (BASE_HEIGHT - FULL_SIDE_COLUMN_HEIGHT) / 2f

@Composable
private fun Icon(path: String, color: Color, modifier: Modifier) =
    org.polyfrost.oneconfig.api.ui.v1.components.Icon(themedIcon(path), color, modifier)

@Composable
private fun CenterColumn(modifier: Modifier, actions: MenuActions) {
    Column(modifier = modifier.width(440.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        MainLogo()
        Spacer(Modifier.height(16.dp))
        Box(contentAlignment = Alignment.Center) {
            SocialText("ONECLIENT", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, color = Color(0x33000000), fontFamily = Outfit, modifier = Modifier.offset(y = 3.dp), textAlign = TextAlign.Center)
            SocialText("ONECLIENT", fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, color = Color.White, fontFamily = Outfit, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(48.dp))
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PillButton("Singleplayer", ASSETS + "user-01.svg", Modifier.fillMaxWidth(), actions.singleplayer)
            PillButton("Multiplayer", ASSETS + "users-01.svg", Modifier.fillMaxWidth(), actions.multiplayer)
            actions.realms?.let { realms ->
                PillButton("Realms", ASSETS + "globe-01.svg", Modifier.fillMaxWidth(), realms)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                PillButton("Settings", ASSETS + "settings-02.svg", Modifier.weight(1f), actions.settings)
                PillButton("Mods", "settings", Modifier.weight(1f), actions.mods)
            }
        }
    }
}

@Composable
private fun MainLogo() {
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Icon(ASSETS + "logo.svg", Color(0x33000000), Modifier.size(96.dp).offset(y = 3.dp))
        Icon(ASSETS + "logo.svg", Color.White, Modifier.size(96.dp))
    }
}

@Composable
private fun LeftColumn(
    modifier: Modifier,
    entries: List<QuickplayEntry>?,
    pingTick: Int,
    actions: MenuActions,
) {
    var expanded by remember { mutableStateOf(true) }
    Column(modifier = modifier.width(300.dp)) {
        DropdownPill(
            label = "Quickplay",
            leadingIcon = ASSETS + "log-in-04.svg",
            expanded = expanded,
            onClick = { expanded = !expanded },
        )
        @Suppress("UNUSED_EXPRESSION") pingTick
        AnimatedVisibility(
            visible = expanded,
            enter = Motion.enter(expandVertically() + fadeIn()),
            exit = Motion.exit(shrinkVertically() + fadeOut()),
        ) {
            Column {
                if (entries?.isEmpty() == true) {
                    Spacer(Modifier.height(12.dp))
                    ServerRow(
                        title = "Nothing played yet",
                        subtitle = "Recents show up here",
                        favicon = null,
                        fallbackPng = ASSETS + "server.png",
                        onClick = actions.singleplayer,
                    )
                }
                entries?.forEach { entry ->
                    Spacer(Modifier.height(12.dp))
                    when (entry) {
                        is QuickplayEntry.Server -> ServerRow(
                            title = entry.data.name,
                            subtitle = serverStatusText(entry.data),
                            //? if > 1.8.9 {
                            favicon = rememberFavicon(entry.data.iconBytes),
                            //?} else {
                            /*favicon = rememberFavicon(entry.data.icon),
                            *///?}
                            fallbackPng = ASSETS + if (entry.data.ip.contains("hypixel", true)) "hypixel.png" else "server.png",
                            onClick = { actions.connect(entry.data) },
                        )
                        is QuickplayEntry.World -> ServerRow(
                            title = entry.world.name,
                            //? if > 1.8.9 {
                            subtitle = "Singleplayer · " + entry.world.gameMode.shortDisplayName.string,
                            //?} else {
                            /*subtitle = "Singleplayer · " + entry.world.gameMode.key.replaceFirstChar { it.uppercase() },
                            *///?}
                            favicon = rememberFavicon(entry.world.iconBytes),
                            fallbackPng = ASSETS + "server.png",
                            onClick = { actions.openWorld(entry.world.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun serverStatusText(server: ServerData): String {
    //? if > 1.8.9 {
    val players = server.players
    return when {
        players != null -> "%,d players online".format(players.online())
        else -> server.ip
    }
    //?} else {
    /*val online = server.onlinePlayers?.replace(Regex("\u00a7."), "")?.substringBefore('/')?.trim()?.toIntOrNull()
    return if (online != null) "%,d players online".format(online) else server.ip
    *///?}
}

@Composable
private fun RightColumn(modifier: Modifier, screen: Screen, onAccounts: () -> Unit) {
    Column(
        modifier = modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MenuPlayerPreview(Modifier.fillMaxWidth())
        if (!PolyPlusMainMenuConfig.hideMainMenuAltManager) {
            AccountPill(name = playerName(), onClick = onAccounts)
        }
        if (!PolyPlusMainMenuConfig.hideMainMenuHostWorld && PrivacyConsent.allowsOnlineServices()) {
            HostWorldButton(screen)
        }
        if (!PolyPlusMainMenuConfig.hideMainMenuSocial && PrivacyConsent.allowsOnlineServices()) {
            SocialPill(screen, Modifier.fillMaxWidth())
        }
        if (!PolyPlusMainMenuConfig.hideMainMenuCosmetics) {
            WardrobePill(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ModIntegrationBar(
    modifier: Modifier,
    screen: Screen,
) {
    val buttons = ModIntegrationButtons.available()
    if (buttons.isEmpty()) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        buttons.forEach { button ->
            IconButton(
                icon = button.icon,
                tooltip = button.tooltip,
                tooltipPlacement = TooltipPlacement.AboveCenter,
                onClick = { button.onClick(screen) },
            )
        }
    }
}

@Composable
private fun WindowControls(modifier: Modifier, actions: MenuActions) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconButton(ASSETS + "logo.svg", tooltip = "Switch to the lobby", onClick = MainMenuReplacement::switchToLobby)
        IconButton(ASSETS + "minecraft-block.svg", tooltip = "Switch to vanilla main menu", onClick = MainMenuReplacement::switchToVanilla)
        ThemeToggleButton()
        NotificationBell()
        if (!ClientPlatform.isMac) {
            IconButton("maximise", tooltip = "Toggle fullscreen", onClick = actions.fullscreen)
        }
        IconButton(ASSETS + "x-close.svg", danger = true, tooltip = "Quit game", onClick = actions.quit)
    }
}

@Composable
private fun ThemeToggleButton() {
    val theme = themeToggle()
    IconButton(icon = theme.icon, tooltip = theme.label, onClick = theme.toggle)
}

@Composable
private fun FeaturedServerCard(modifier: Modifier, pingTick: Int, actions: MenuActions) {
    val snapshot by FeaturedServers.state.collectAsState()
    val server = MainMenuFeaturedServer.current(snapshot) ?: return
    val campaign = server.featured ?: return
    val data = remember(server.id, server.address) { MainMenuFeaturedServer.serverData(server) }

    LaunchedEffect(data) { MainMenuServerPings.start(this, listOf(data)) }
    @Suppress("UNUSED_EXPRESSION") pingTick

    val catalogIcon = rememberRemoteImage(campaign.imageUrl ?: FeaturedServers.iconUrl(server.id))
    //? if > 1.8.9 {
    val favicon = rememberFavicon(data.iconBytes)
    //?} else {
    /*val favicon = rememberFavicon(data.icon)
    *///?}

    Column(
        modifier = modifier
            .width(300.dp)
            .menuFrost(OneConfigTheme.current.modCardShape, MenuCardRadius, OneConfigTheme.current.modCardBackground, false)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SocialText("SPONSORED", 11.sp, color = SocialTextSecondary, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            if (MainMenuFeaturedServer.isDismissible(server)) {
                DismissButton { MainMenuFeaturedServer.dismiss(server) }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardThumbnail(catalogIcon ?: favicon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SocialText(campaign.title, 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SocialText(campaign.description, 13.sp, color = SocialTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        CallToActionButton(campaign.ctaLabel) { actions.connect(data) }
    }
}

@Composable
private fun CardThumbnail(image: ImageBitmap?) {
    val modifier = Modifier.size(42.dp).clip(ppShape(6.dp))
    if (image != null) {
        Image(
            image,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.Medium,
        )
    } else {
        RasterImage(ASSETS + "server.png", modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
private fun DismissButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color by animateColorAsState(if (hovered) SocialTextPrimary else SocialTextSecondary, menuFade())
    Row(
        modifier = Modifier
            .clip(ppShape(4.dp))
            .hoverable(interaction)
            .clickableTextWithSound(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SocialText("Dismiss", 11.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(ASSETS + "x-close.svg", color, Modifier.size(11.dp))
    }
}

@Composable
private fun CallToActionButton(label: String, onClick: () -> Unit) {
    TextButton(label, null, Modifier.fillMaxWidth().height(34.dp), ButtonStyle.Primary, true, onClick)
}

@Composable
private fun Footer(modifier: Modifier, edgeX: (Float) -> Dp, edgeY: (Float) -> Dp) {
    Box(modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = edgeX(24f), bottom = edgeY(18f)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(ASSETS + "footer-logo.svg", Color.White, Modifier.size(25.dp))
            FooterBrandText(platformLabel())
        }
        SocialText(
            "Copyright Mojang AB. Do not distribute!",
            fontSize = 13.sp,
            color = SocialTextSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = edgeX(24f), bottom = edgeY(18f)),
            textAlign = TextAlign.Center,
        )
    }
}
@Composable
private fun DropdownPill(label: String, leadingIcon: String, expanded: Boolean, onClick: () -> Unit) {
    val chevronAngle by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    MenuButtonSurface(label, Modifier.fillMaxWidth().height(45.dp), onClick) { ink, hovered ->
        val chevronTint by animateColorAsState(if (hovered) ink else MenuMutedIcon, menuFade())
        Icon(leadingIcon, ink, Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(18.dp))
        MenuButtonLabel(label, ink, Modifier.padding(horizontal = 40.dp))
        Icon(
            ASSETS + "chevron-up.svg",
            chevronTint,
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(16.dp).rotate(chevronAngle),
        )
    }
}

@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    favicon: ImageBitmap?,
    fallbackPng: String,
    onClick: () -> Unit = {},
) {
    val (interaction, hovered) = rememberSocialHover()
    val chevronTint by animateColorAsState(if (hovered) SocialTextPrimary else MenuMutedIcon, menuFade())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .menuListRow(OneConfigTheme.current.buttonShape, OneConfigTheme.current.modCardBackground, "$title, $subtitle", interaction, hovered, onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val iconModifier = Modifier.size(42.dp).clip(ppShape(6.dp))
        if (favicon != null) {
            Image(favicon, contentDescription = null, modifier = iconModifier, contentScale = ContentScale.Crop)
        } else {
            RasterImage(fallbackPng, iconModifier, contentScale = ContentScale.Crop)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SocialText(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SocialText(subtitle, fontSize = 13.sp, color = SocialTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(ASSETS + "chevron-right.svg", chevronTint, Modifier.size(20.dp))
    }
}

@Composable
private fun AccountPill(name: String, onClick: () -> Unit) {
    val localId = runCatching { Minecraft.getInstance().user.profileId }.getOrNull()
    val headShape = OneConfigTheme.current.checkBoxShape
    MenuButtonSurface("Accounts, $name", Modifier.fillMaxWidth().height(45.dp), onClick) { ink, hovered ->
        val chevronTint by animateColorAsState(if (hovered) ink else MenuMutedIcon, menuFade())
        if (localId != null) {
            PlayerHead(localId, 28.dp, Modifier.align(Alignment.CenterStart).padding(start = 10.dp), headShape)
        } else {
            RasterImage(ASSETS + "avatar.png", Modifier.align(Alignment.CenterStart).padding(start = 10.dp).size(28.dp).clip(headShape), contentScale = ContentScale.Crop)
        }
        MenuButtonLabel(name, ink, Modifier.padding(horizontal = 44.dp))
        Icon(
            ASSETS + "chevron-up.svg",
            chevronTint,
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp).size(16.dp).rotate(180f),
        )
    }
}

@Composable
private fun AccountsDialog(onClose: () -> Unit) {
    val actions = rememberAccountActions()
    AccountSwitcherContent(actions, onClose)
}

@Composable
private fun IconButton(
    icon: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    tooltip: String? = null,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.BelowEnd,
    onClick: () -> Unit = {},
) {
    MenuButtonSurface(tooltip, modifier.size(45.dp), onClick) { ink, hovered ->
        val tint by animateColorAsState(if (danger && hovered) SocialDangerText else ink, menuFade())
        Icon(icon, tint, Modifier.size(18.dp))
        if (tooltip != null && hovered) TooltipPopup(tooltip, tooltipPlacement, Dp.Unspecified)
    }
}

@Composable
private fun NotificationBell() {
    var expanded by remember { mutableStateOf(false) }
    var bellSize by remember { mutableStateOf(IntSize.Zero) }
    Box {
        IconButton(
            ASSETS + "bell-01.svg",
            modifier = Modifier.onSizeChanged { bellSize = it },
            tooltip = if (expanded) null else "Notifications",
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, bellSize.height + with(LocalDensity.current) { 12.dp.roundToPx() }),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                NotificationsCenter()
            }
        }
    }
}
//? if = 1.8.9 {
/*@Composable
private fun rememberFavicon(icon: String?): ImageBitmap? = remember(icon) {
    if (icon.isNullOrEmpty()) null
    else runCatching { PolyBitmap.decode(Base64.getDecoder().decode(icon))!!.asComposeImageBitmap() }.getOrNull()
}
*///?}
