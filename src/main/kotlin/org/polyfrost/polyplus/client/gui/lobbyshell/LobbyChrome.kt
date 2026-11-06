@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.AccountSwitcherContent
import org.polyfrost.polyplus.client.gui.BadModsDialog
import org.polyfrost.polyplus.client.gui.FooterBrandText
import org.polyfrost.polyplus.client.gui.MainMenuRasterAssets
import org.polyfrost.polyplus.client.gui.ModIntegrationButtons
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialPopupBackground
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPage
import org.polyfrost.polyplus.client.gui.marketplace.MarketplacePage
import org.polyfrost.polyplus.client.gui.menuwardrobe.WardrobePage
import org.polyfrost.polyplus.client.gui.platformLabel
import org.polyfrost.polyplus.client.gui.rememberAccountActions
import org.polyfrost.polyplus.client.gui.serveredit.ServerEditDialog
import org.polyfrost.polyplus.client.gui.singleplayer.SingleplayerPage
import org.polyfrost.polyplus.client.gui.stats.StatsPage
import org.polyfrost.polyplus.client.gui.stats.formatDuration
import org.polyfrost.polyplus.client.gui.switchToVanillaMenu
import org.polyfrost.polyplus.client.gui.themeToggle
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.toggleFullscreen
import org.polyfrost.polyplus.client.lobby.LobbyGlass
import org.polyfrost.polyplus.client.lobby.LobbyHologram
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbyWardrobe
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.stats.PlaySessions
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

private const val FROST_MILLIS = 220
private const val REVEAL_MILLIS = 700

private class NavTab(val label: String, val page: LobbyPage)

private val TABS = listOf(
    NavTab("Play", LobbyPage.Home),
    NavTab("Wardrobe", LobbyPage.Wardrobe),
    NavTab("Marketplace", LobbyPage.Marketplace),
    NavTab("Social", LobbyPage.Social),
    NavTab("Stats", LobbyPage.Stats),
)

private fun NavTab.activeOn(page: LobbyPage): Boolean = when (this.page) {
    LobbyPage.Home -> page != LobbyPage.Wardrobe && page != LobbyPage.Social && page != LobbyPage.Marketplace && page != LobbyPage.Stats
    else -> this.page == page
}

@Composable
internal fun LobbyRoot(lobby: LobbyShell, screen: Screen) {
    var labels by remember { mutableStateOf(emptyList<LobbyScene.Label>()) }
    var chrome by remember { mutableFloatStateOf(LobbyScene.overlayAlpha) }
    var depart by remember { mutableFloatStateOf(0f) }
    val frost = animateFloatAsState(if (lobby.page.frosted) 1f else 0f, tween(FROST_MILLIS))
    val reveal = animateFloatAsState(if (lobby.setup) 0f else 1f, tween(REVEAL_MILLIS))
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            MainMenuRasterAssets.preload()
            Outfit
        }
        while (true) {
            withFrameNanos { }
            labels = LobbyScene.labels
            chrome = if (LobbyWardrobe.open) 1f else LobbyScene.overlayAlpha
            depart = LobbyScene.departProgress
            LobbyGlass.pageFrost = frost.value
            LobbyGlass.chrome = if (lobby.setup) chrome else chrome * reveal.value
        }
    }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { measured = it }) {
        val outer = LocalDensity.current
        val widthPx = if (measured.width > 0) measured.width.toFloat() else maxWidth.value * outer.density
        val heightPx = if (measured.height > 0) measured.height.toFloat() else maxHeight.value * outer.density
        StartupOverlay.rootWidth = widthPx
        val scale = minOf(widthPx / LobbyLayout.DESIGN_WIDTH, heightPx / LobbyLayout.DESIGN_HEIGHT)
        val density = remember(scale, outer.fontScale) { Density(scale, outer.fontScale) }
        CompositionLocalProvider(LocalDensity provides density, LocalRootSize provides Size(widthPx, heightPx)) {
            if (lobby.setup) {
                if (chrome > 0f || StartupOverlay.active) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = chrome }) { LobbySetup(lobby) }
                }
            } else {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = chrome * reveal.value }) {
                    LobbyChrome(lobby, screen, labels, IntSize(widthPx.roundToInt(), heightPx.roundToInt()))
                }
                lobby.tour?.let { tour -> TourLayer(tour, lobby.anchors) { lobby.tour = null } }
            }
            if (lobby.accountsOpen) AccountsDialog(lobby)
            if (depart > 0f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = depart * depart }.background(Color.White))
            }
        }
        BadModsDialog()
    }
    LinkedModNotices(screen, lobby.setup || lobby.tour != null)
}

@Composable
private fun AccountsDialog(lobby: LobbyShell) {
    val actions = rememberAccountActions()
    DisposableEffect(actions) {
        lobby.accountActions = actions
        onDispose { lobby.accountActions = null }
    }
    val active = actions.accounts?.firstOrNull { it.active }?.id
    LaunchedEffect(active) { if (active != null) lobby.syncAccount() }
    AccountSwitcherContent(actions, lobby::closeAccounts)
}

@Composable
private fun BoxScope.LobbyChrome(lobby: LobbyShell, screen: Screen, labels: List<LobbyScene.Label>, root: IntSize) {
    LobbyDarkChrome {
        val sceneScrim = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(0f to sceneScrim.copy(alpha = 0.92f), 0.42f to sceneScrim.copy(alpha = 0.74f), 1f to Color.Transparent)),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(200.dp)
                .background(Brush.verticalGradient(listOf(sceneScrim.copy(alpha = 0f), sceneScrim.copy(alpha = 0.6f)))),
        )
    }
    when (lobby.page) {
        LobbyPage.Home -> {
            SceneTags(labels, root)
            LobbyCornerCard(lobby, Modifier.align(Alignment.TopStart).padding(start = LobbyLayout.Margin, top = LobbyLayout.Top))
        }
        LobbyPage.Wardrobe -> WardrobePage(onBack = { lobby.show(LobbyPage.Home) }, onMarketplace = { lobby.show(LobbyPage.Marketplace) })
        LobbyPage.Multiplayer -> MultiplayerPage(lobby)
        LobbyPage.Singleplayer -> SingleplayerPage(
            selectedWorldId = lobby.selectedWorldId,
            onBack = { lobby.show(LobbyPage.Home) },
            onSelect = lobby::selectWorld,
            onHost = lobby::host,
        )
        LobbyPage.Marketplace -> MarketplacePage(onBack = { lobby.show(LobbyPage.Home) }, onWardrobe = { lobby.show(LobbyPage.Wardrobe) })
        LobbyPage.Social -> SocialPage { lobby.show(LobbyPage.Home) }
        LobbyPage.Stats -> StatsPage(lobby) { lobby.show(LobbyPage.Home) }
    }
    SceneControl(lobby)
    LobbyDarkChrome { Navbar(lobby, screen) }
    val chatShown = lobby.page != LobbyPage.Social && !LobbyPage.Social.hidden
    val chatWidth by animateDpAsState(if (chatShown) LobbyLayout.ChatWidth else 0.dp)
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = LobbyLayout.Margin, end = LobbyLayout.BarEnd, bottom = LobbyLayout.Margin),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.width(chatWidth), verticalArrangement = Arrangement.spacedBy(LobbyLayout.Gap)) {
            if (lobby.page == LobbyPage.Home) LinkedModButton(Modifier)
            if (chatShown) ChatPanel(open = lobby.page == LobbyPage.Home)
        }
        Spacer(Modifier.width(chatWidth.coerceAtMost(LobbyLayout.Gap)))
        key(lobby.profileVersion) { PlayerBar(lobby, Modifier.weight(1f, fill = false)) }
    }
    PlayUnit(
        lobby,
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = LobbyLayout.Margin, bottom = LobbyLayout.Margin)
            .width(LobbyLayout.UnitWidth)
            .tourAnchor(lobby.anchors, TourAnchor.Play),
    )
    if (!PolyPlusMainMenuConfig.hideMainMenuFooter) LobbyDarkChrome { Footer() }
    if (lobby.page != LobbyPage.Social) {
        SocialToastHost(Modifier.align(Alignment.TopCenter).padding(top = LobbyLayout.Top))
    }
    PlayTogetherBanner(Modifier.align(Alignment.TopCenter).padding(top = LobbyLayout.Top + LobbyLayout.Gap, start = LobbyLayout.Margin, end = LobbyLayout.Margin))
    PlayTogetherHooks(lobby)
    lobby.hostWorldId?.let { worldId -> HostWorldDialog(worldId, screen) { lobby.hostWorldId = null } }
    lobby.editing?.let { edit ->
        ServerEditDialog(
            server = edit.server,
            onDone = lobby::finishEdit,
            onDelete = edit.server?.let { lobby::finishDelete },
            onDismiss = { lobby.editing = null },
        )
    }
}

@Composable
private fun BoxScope.Footer() {
    Row(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(LobbyLayout.Margin)
            .padding(horizontal = LobbyLayout.Margin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LobbyIcon("footer-logo", size = 14.dp)
        FooterBrandText(platformLabel(), 11.sp, SocialTextPrimary)
        Spacer(Modifier.weight(1f))
        SocialText("Copyright Mojang AB. Do not distribute!", 11.sp, color = SocialTextSecondary, maxLines = 1)
    }
}

@Composable
private fun Navbar(lobby: LobbyShell, screen: Screen) {
    val groups by GroupsRepository.groups.collectAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(LobbyLayout.NavbarHeight)
            .padding(horizontal = LobbyLayout.Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(NAV_SIDE.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.onGloballyPositioned { StartupOverlay.navMark = StartupOverlay.place(it) }) { LobbyIcon("logo", size = 28.dp) }
            Box(Modifier.onGloballyPositioned { StartupOverlay.navWord = StartupOverlay.place(it) }) {
                SocialText("ONECLIENT", 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.5.sp, fontFamily = Outfit, maxLines = 1)
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.filterNot { it.page.hidden }.forEach { tab ->
                val anchor = when (tab.page) {
                    LobbyPage.Wardrobe -> Modifier.tourAnchor(lobby.anchors, TourAnchor.Wardrobe)
                    LobbyPage.Social -> Modifier.tourAnchor(lobby.anchors, TourAnchor.Social)
                    else -> Modifier
                }
                NavButton(tab, tab.activeOn(lobby.page), tab.page == LobbyPage.Social && groups.any { it.unread }, anchor) { lobby.show(tab.page) }
            }
        }
        Box(Modifier.width(NAV_SIDE.dp), contentAlignment = Alignment.CenterEnd) {
            key(lobby.profileVersion) { ProfileCluster(lobby, screen) }
        }
    }
}

private const val NAV_SIDE = 490
private const val NAV_INACTIVE_ALPHA = 0.6f
private const val NAV_UNDERLINE_WIDTH = 27

@Composable
private fun NavButton(tab: NavTab, active: Boolean, unread: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Box(
        modifier
            .height(38.dp)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Label(tab.label, 14.sp, color = if (active || hovered) SocialTextPrimary else SocialTextPrimary.copy(alpha = NAV_INACTIVE_ALPHA), weight = FontWeight.Bold)
        if (active) {
            Box(Modifier.align(Alignment.BottomCenter).width(NAV_UNDERLINE_WIDTH.dp).height(2.dp).background(OneConfigTheme.accent).onGloballyPositioned { if (tab.page == LobbyPage.Home) StartupOverlay.navLine = StartupOverlay.place(it) })
        }
        if (unread) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 12.dp)
                    .size(8.dp)
                    .clip(OneConfigTheme.current.circleShape)
                    .background(OneConfigTheme.accent),
            )
        }
    }
}

@Composable
private fun ProfileCluster(lobby: LobbyShell, screen: Screen) {
    Row(Modifier.tourAnchor(lobby.anchors, TourAnchor.Tools), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val mc = Minecraft.getInstance()
        NotificationsButton()
        ModMenuSlot(screen)
        SocialIconButton("settings", tooltip = "Mods") { PolyPlusOneConfigIntegration.openMods() }
        SocialIconButton("cog", tooltip = "Minecraft settings") {
            //? if >= 26.3 || < 26.1 {
            ClientPlatform.setScreen(OptionsScreen(screen, mc.options))
            //?} else {
            /*ClientPlatform.setScreen(OptionsScreen(screen, mc.options, false))
            *///?}
        }
        MoreButton(screen)
        Spacer(Modifier.width(2.dp))
        ProfileChip(lobby, Modifier.weight(1f, fill = false))
        if (!ClientPlatform.isMac) SocialIconButton("maximise", tooltip = "Toggle fullscreen", onClick = ::toggleFullscreen)
        QuitButton()
    }
}

@Composable
private fun ModMenuSlot(screen: Screen) {
    val modMenu = if (PolyPlusMainMenuConfig.hideMainMenuModButtons) null else ModIntegrationButtons.availableModMenu()
    if (modMenu != null) {
        val logo = remember(modMenu) { decodeModLogo(modMenu.logo) }
        if (logo == null) {
            SocialIconButton(modMenu.icon, tooltip = modMenu.tooltip) { modMenu.onClick(screen) }
        } else {
            SocialIconButton(tooltip = modMenu.tooltip, onClick = { modMenu.onClick(screen) }) { ModLogo(logo, MOD_LOGO_SIZE.dp, SocialTextPrimary) }
        }
    }
}

@Composable
private fun ProfileChip(lobby: LobbyShell, modifier: Modifier) {
    val switcher = !PolyPlusMainMenuConfig.hideMainMenuAltManager
    val (interaction, hovered) = rememberSocialHover()
    Row(
        modifier
            .rowFrame(false, hovered && switcher)
            .hoverable(interaction)
            .then(if (switcher) Modifier.clickableWithSound(lobby::openAccounts) else Modifier)
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(ClientPlatform.localPlayerName(), 14.sp, Modifier.weight(1f, fill = false).widthIn(max = PROFILE_NAME_WIDTH.dp), weight = FontWeight.Medium)
        Head(ClientPlatform.localPlayerUuid(), 38.dp, realSkin = true)
    }
}

private const val PROFILE_NAME_WIDTH = 130
private const val MOD_LOGO_SIZE = 16
private const val TAG_EASE_MILLIS = 160
private const val TAG_TWO_LINE_HEIGHT = 46
private const val TAG_WHEREABOUTS_WIDTH = 180
private const val TAG_FOCUS_SCALE = 1.08f
private const val MORE_MENU_WIDTH = 270
private const val QUIT_ICON = SOCIAL_ASSETS + "x-close.svg"
private const val QUIT_CARD_WIDTH = 330
private const val MIN_SHOWN_PLAYTIME = 60_000L

@Composable
private fun NotificationsButton() {
    BarPopupButton("bell", "Notifications") { NotificationsCenter() }
}

@Composable
private fun QuitButton() {
    val mc = Minecraft.getInstance()
    if (PolyPlusMainMenuConfig.quitWithoutConfirmation) {
        SocialIconButton(QUIT_ICON, tooltip = "Quit game", onClick = mc::stop)
    } else {
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { PlaySessions.log(ClientPlatform.localPlayerUuid()) } }
        BarPopupButton(QUIT_ICON, "Quit game") { close -> QuitConfirm(mc::stop, close) }
    }
}

@Composable
private fun QuitConfirm(onQuit: () -> Unit, onCancel: () -> Unit) {
    val player = ClientPlatform.localPlayerUuid()
    val log = remember(player) { PlaySessions.log(player) }
    val today = log.days[LocalDate.now().toEpochDay()]?.takeIf { it >= MIN_SHOWN_PLAYTIME }
    val lastPlace = log.spots.values.filter { it.lastPlayed > 0L }.maxByOrNull { it.lastPlayed }?.name
    Column(Modifier.width(QUIT_CARD_WIDTH.dp).popup()) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            QuitPortrait(player)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                PanelTitle("Quit the game?")
                Label(ClientPlatform.localPlayerName(), 12.sp, color = MutedText)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (today != null || lastPlace != null) QuitRecap(today, lastPlace)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SocialButton("Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                DangerButton("Quit", Modifier.weight(1f), icon = QUIT_ICON, onClick = onQuit)
            }
        }
    }
}

@Composable
private fun QuitPortrait(player: UUID) {
    Box {
        Head(player, 58.dp, realSkin = true)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 8.dp)
                .size(28.dp)
                .clip(OneConfigTheme.current.circleShape)
                .background(SocialPopupBackground)
                .padding(2.dp)
                .clip(OneConfigTheme.current.circleShape)
                .background(SocialDangerColor),
            contentAlignment = Alignment.Center,
        ) {
            LobbyIcon("x-close", OneConfigTheme.current.accentTextColor, 14.dp)
        }
    }
}

@Composable
private fun QuitRecap(today: Long?, lastPlace: String?) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).card(8.dp).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        today?.let { QuitStat("Played today", formatDuration(it)) }
        if (today != null && lastPlace != null) Box(Modifier.width(1.dp).fillMaxHeight().background(SocialBorderColor))
        lastPlace?.let { QuitStat("Last played", it, Modifier.weight(1f)) }
    }
}

@Composable
private fun QuitStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(label, 11.sp, color = MutedText)
        Label(value, 15.sp, weight = FontWeight.Medium)
    }
}

@Composable
private fun MoreButton(screen: Screen) {
    BarPopupButton(SOCIAL_ASSETS + "dots-vertical.svg", "More") { close -> MoreMenu(screen, close) }
}

@Composable
private fun BarPopupButton(icon: String, tooltip: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box {
        SocialIconButton(
            icon,
            Modifier.onSizeChanged { size = it },
            tooltip = if (open) null else tooltip,
            onClick = { open = !open },
        )
        if (open) {
            EscapeCloses { open = false }
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, size.height + with(LocalDensity.current) { 12.dp.roundToPx() }),
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                LobbyUserTheme { content { open = false } }
            }
        }
    }
}

@Composable
private fun MoreMenu(screen: Screen, onClose: () -> Unit) {
    val theme = themeToggle()
    Column(Modifier.width(MORE_MENU_WIDTH.dp).popup().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        MoreRow(theme.icon, theme.label, theme.toggle)
        MoreRow(SOCIAL_ASSETS + "minecraft-block.svg", "Switch to vanilla main menu") {
            onClose()
            switchToVanillaMenu()
        }
        if (!PolyPlusMainMenuConfig.hideMainMenuModButtons) {
            ModIntegrationButtons.withoutModMenu().forEach { button ->
                MoreRow(button.icon, button.tooltip) {
                    onClose()
                    button.onClick(screen)
                }
            }
        }
    }
}

@Composable
internal fun MoreRow(icon: String, label: String, onClick: () -> Unit) {
    val (interaction, hovered) = rememberSocialHover()
    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .rowFrame(false, hovered)
            .hoverable(interaction)
            .clickableWithSound(onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ThemedIcon(icon, SocialTextPrimary, 18.dp)
        Label(label, 14.sp, Modifier.weight(1f))
    }
}

@Composable
private fun BoxScope.SceneTags(labels: List<LobbyScene.Label>, root: IntSize) {
    val accent = OneConfigTheme.accent
    SideEffect { LobbyHologram.accent = accent.toArgb() and 0xFFFFFF }
    labels.forEach { label ->
        key(label.name) {
            val x = ((label.x + 1f) * 0.5f * root.width).roundToInt()
            val y = ((1f - label.y) * 0.5f * root.height).roundToInt()
            SceneTag(
                label,
                Modifier.align(Alignment.TopStart).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) { placeable.place(x - placeable.width / 2, y - placeable.height) }
                },
            )
        }
    }
}

@Composable
private fun SceneTag(label: LobbyScene.Label, modifier: Modifier) {
    val shape = SocialFieldShape
    val hover = lobbyHover(label.name)
    val focused = LobbyScene.focused == label.name
    val speaking = label.name in partySpeakingNames()
    val ring by animateColorAsState(
        if (speaking) SocialSuccessColor else if (label.isLocal || focused) OneConfigTheme.accent else SocialBorderColor,
        tween(TAG_EASE_MILLIS, easing = FastOutSlowInEasing),
    )
    val lift by animateFloatAsState(if (focused) TAG_FOCUS_SCALE else 1f, tween(TAG_EASE_MILLIS, easing = FastOutSlowInEasing))
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            hover
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .height(if (label.whereabouts != null) TAG_TWO_LINE_HEIGHT.dp else 30.dp)
                .sceneFrost(8.dp, floating = false)
                .clip(shape)
                .background(OneConfigTheme.current.popupBackground)
                .border(1.dp, ring, shape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(label.name, 14.sp, weight = FontWeight.Medium)
                label.whereabouts?.let { Label(it, 11.sp, Modifier.widthIn(max = TAG_WHEREABOUTS_WIDTH.dp), color = MutedText) }
            }
            if (label.isLocal) Label("You", 12.sp, color = MutedText)
            label.invite?.let { SceneTagInvite(it) }
        }
    }
}
