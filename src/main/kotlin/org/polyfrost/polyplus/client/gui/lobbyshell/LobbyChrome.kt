@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsScreen
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ContextMenuHost
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItem
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuSurface
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.oneconfig.internal.ui.components.NotificationsCenter
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.AccountSwitcherContent
import org.polyfrost.polyplus.client.gui.BadModsDialog
import org.polyfrost.polyplus.client.gui.FooterBrandText
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.MainMenuRasterAssets
import org.polyfrost.polyplus.client.gui.ModIntegrationButtons
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.SocialPopupBackground
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.polyplus.client.gui.clickableWithSound
import org.polyfrost.polyplus.client.gui.A11yRegion
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.a11yRegion
import org.polyfrost.polyplus.client.gui.a11yRoot
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.gui.rememberSocialHover
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPage
import org.polyfrost.polyplus.client.gui.marketplace.MarketplacePage
import org.polyfrost.polyplus.client.gui.menuwardrobe.WardrobePage
import org.polyfrost.polyplus.client.gui.platformLabel
import org.polyfrost.polyplus.client.gui.rememberAccountActions
import org.polyfrost.polyplus.client.gui.serveredit.ServerEditDialog
import org.polyfrost.polyplus.client.gui.singleplayer.SingleplayerPage
import org.polyfrost.polyplus.client.gui.replays.ReplaysPage
import org.polyfrost.polyplus.client.gui.news.NewsFeed
import org.polyfrost.polyplus.client.gui.news.NewsPage
import org.polyfrost.polyplus.client.gui.stats.StatsPage
import org.polyfrost.polyplus.client.gui.stats.formatDuration
import org.polyfrost.polyplus.client.gui.themeToggle
import org.polyfrost.polyplus.client.gui.themedIcon
import org.polyfrost.polyplus.client.gui.toggleFullscreen
import org.polyfrost.polyplus.client.lobby.LobbyAntics
import org.polyfrost.polyplus.client.lobby.LobbyGlass
import org.polyfrost.polyplus.client.lobby.LobbyHologram
import org.polyfrost.polyplus.client.lobby.LobbyScene
import org.polyfrost.polyplus.client.lobby.LobbyWardrobe
import org.polyfrost.polyplus.client.lobby.StartupOverlay
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.stats.PlaySessions
import org.polyfrost.polyplus.client.utils.MacSafeArea
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize

private const val FROST_MILLIS = 220
private const val REVEAL_MILLIS = 700

private class LobbyTab(val label: String, val page: LobbyPage)

private val TABS = listOf(
    LobbyTab("Play", LobbyPage.Home),
    LobbyTab("Wardrobe", LobbyPage.Wardrobe),
    LobbyTab("Marketplace", LobbyPage.Marketplace),
    LobbyTab("Social", LobbyPage.Social),
    LobbyTab("Stats", LobbyPage.Stats),
    LobbyTab("News", LobbyPage.News),
)

private fun LobbyTab.activeOn(page: LobbyPage): Boolean = when (this.page) {
    LobbyPage.Home -> page != LobbyPage.Wardrobe && page != LobbyPage.Social && page != LobbyPage.Marketplace && page != LobbyPage.Stats && page != LobbyPage.News
    else -> this.page == page
}

@Composable
internal fun LobbyRoot(lobby: LobbyShell, screen: Screen) {
    val feed = lobby.feed
    val chrome by feed.chrome
    val depart by feed.depart
    val chromeShown by remember { derivedStateOf { chrome > 0f } }
    val departing by remember { derivedStateOf { depart > 0f } }
    LaunchedEffect(Unit) { NewsFeed.refresh() }
    val frost = animateFloatAsState(if (lobby.page.frosted) 1f else 0f, tween(FROST_MILLIS))
    val reveal = animateFloatAsState(if (lobby.setup) 0f else 1f, tween(REVEAL_MILLIS))
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            MainMenuRasterAssets.preload()
            Outfit
        }
    }
    DisposableEffect(feed, frost, reveal) {
        feed.glass = {
            LobbyGlass.pageFrost = frost.value
            LobbyGlass.chrome = if (lobby.setup) feed.chrome.floatValue else feed.chrome.floatValue * reveal.value
        }
        onDispose {
            feed.glass = null
            LobbyGlass.pageFrost = 0f
            LobbyGlass.chrome = 0f
        }
    }
    LaunchedEffect(Unit) { launch { SocialErrors.events.collect(A11y::announce) }; SocialNotices.events.collect(A11y::announce) }
    var measured by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { measured = it }.a11yRoot()) {
        val outer = LocalDensity.current
        val widthPx = if (measured.width > 0) measured.width.toFloat() else maxWidth.value * outer.density
        val heightPx = if (measured.height > 0) measured.height.toFloat() else maxHeight.value * outer.density
        if (!usableSize(widthPx, heightPx)) return@BoxWithConstraints
        StartupOverlay.rootWidth = widthPx
        val fullscreen = MacSafeArea.fullscreen()
        val notchPx = remember(widthPx, heightPx, fullscreen) { MacSafeArea.topShare() * heightPx }
        val scale = minOf(widthPx / LobbyLayout.DESIGN_WIDTH, (heightPx - notchPx) / LobbyLayout.DESIGN_HEIGHT)
        val density = remember(scale, outer.fontScale) { Density(usableScale(scale), usableScale(outer.fontScale)) }
        val notch = with(density) { notchPx.toDp() }
        CompositionLocalProvider(LocalDensity provides density, LocalRootSize provides Size(widthPx, heightPx)) {
            if (lobby.setup) {
                if (chromeShown || StartupOverlay.active) {
                    Box(Modifier.fillMaxSize().padding(top = notch).graphicsLayer { alpha = chrome }) { LobbySetup(lobby) }
                }
            } else {
                Box(Modifier.fillMaxSize().padding(top = notch).graphicsLayer { alpha = chrome * reveal.value }) {
                    LobbyChrome(lobby, screen, feed, IntSize(widthPx.roundToInt(), heightPx.roundToInt()), notchPx.roundToInt())
                }
                lobby.tour?.let { tour -> TourLayer(tour, lobby.anchors, lobby::endTour) }
            }
            if (lobby.accountsOpen) AccountsDialog(lobby)
            if (departing) {
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
private fun BoxScope.LobbyChrome(lobby: LobbyShell, screen: Screen, feed: LobbyFeed, root: IntSize, notch: Int) {
    LobbyHighlight { ContextMenuHost {
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
        Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Page)) { when (lobby.page) {
            LobbyPage.Home -> {
                Box(Modifier.fillMaxSize().avatarTaps(lobby, notch))
                SceneTags(feed, root, notch)
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
            LobbyPage.Replays -> ReplaysPage(lobby.activeReplay(), onBack = { lobby.show(LobbyPage.Home) }, onSelect = lobby::select, onWatch = lobby::watch)
            LobbyPage.Marketplace -> MarketplacePage(onBack = { lobby.show(LobbyPage.Home) }, onWardrobe = { lobby.show(LobbyPage.Wardrobe) })
            LobbyPage.Social -> SocialPage { lobby.show(LobbyPage.Home) }
            LobbyPage.Stats -> StatsPage(lobby) { lobby.show(LobbyPage.Home) }
            LobbyPage.News -> NewsPage { lobby.show(LobbyPage.Home) }
        } }
        var barStart by remember { mutableFloatStateOf(Float.NaN) }
        Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Scene)) { SceneControl(lobby) { barStart } }
        LobbyDarkChrome { Navbar(lobby, screen) }
        val chatShown = lobby.page != LobbyPage.Social && !LobbyPage.Social.hidden
        val chatWidth by animateDpAsState(
            if (chatShown) LobbyLayout.ChatWidth else 0.dp,
            if (Motion.reduced) snap() else spring(visibilityThreshold = Dp.VisibilityThreshold),
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = LobbyLayout.Margin, end = LobbyLayout.BarEnd, bottom = LobbyLayout.Margin)
                .a11yRegion(A11yRegion.Bar),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.widthIn(max = chatWidth), verticalArrangement = Arrangement.spacedBy(LobbyLayout.Gap)) {
                if (lobby.page == LobbyPage.Home) LinkedModButton(Modifier)
                if (chatShown) ChatPanel(open = lobby.page == LobbyPage.Home)
            }
            Spacer(Modifier.width(chatWidth.coerceAtMost(LobbyLayout.Gap)))
            key(lobby.profileVersion) { PlayerBar(lobby, Modifier.weight(1f, fill = false).onGloballyPositioned { barStart = it.positionInRoot().x }) }
            if (lobby.page == LobbyPage.Home) {
                Spacer(Modifier.width(LobbyLayout.Gap))
                SceneButton(lobby)
            }
        }
        PlayUnit(
            lobby,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = LobbyLayout.Margin, bottom = LobbyLayout.Margin)
                .width(LobbyLayout.UnitWidth)
                .tourAnchor(lobby.anchors, TourAnchor.Play)
                .a11yRegion(A11yRegion.Play),
        )
        if (!PolyPlusMainMenuConfig.hideMainMenuFooter) LobbyDarkChrome { Footer() }
        if (lobby.page != LobbyPage.Social) {
            SocialToastHost(Modifier.align(Alignment.TopCenter).padding(top = LobbyLayout.Top))
        }
        PlayTogetherBanner(Modifier.align(Alignment.TopCenter).padding(top = LobbyLayout.Top + LobbyLayout.Gap, start = LobbyLayout.Margin, end = LobbyLayout.Margin).a11yRegion(A11yRegion.Banner))
        PlayTogetherHooks(lobby)
        lobby.hostWorldId?.let { worldId -> HostWorldDialog(worldId, screen) { lobby.hostWorldId = null } }
        lobby.editing?.let { edit ->
            ServerEditDialog(
                server = edit.server,
                serverIndex = edit.index,
                onDone = lobby::finishEdit,
                onDelete = edit.server?.let { lobby::finishDelete },
                onDismiss = { lobby.editing = null },
            )
        }
    } }
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
    val requests by FriendsRepository.incomingRequests.collectAsState()
    val invites by SessionsRepository.incomingInvites.collectAsState()
    val partyInvites by PartyRepository.incomingInvites.collectAsState()
    val unread = groups.any { it.unread } || requests.isNotEmpty() || partyInvites.isNotEmpty() || invites.any { it.status == INVITE_PENDING }
    LobbyHighlight { NavbarRow(lobby, screen, unread) }
}

@Composable
private fun NavbarRow(lobby: LobbyShell, screen: Screen, unread: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(LobbyLayout.NavbarHeight)
            .padding(horizontal = LobbyLayout.Margin),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wordmark(
            Modifier.width(NAV_SIDE.dp),
            mark = Modifier.onGloballyPositioned { StartupOverlay.navMark = StartupOverlay.place(it) },
            word = Modifier.onGloballyPositioned { StartupOverlay.navWord = StartupOverlay.place(it) },
        )
        Row(
            Modifier.weight(1f).a11yRegion(A11yRegion.Nav),
            horizontalArrangement = Arrangement.spacedBy(NAV_TAB_GAP, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.filterNot { it.page.hidden }.forEach { tab ->
                val anchor = when (tab.page) {
                    LobbyPage.Wardrobe -> Modifier.tourAnchor(lobby.anchors, TourAnchor.Wardrobe)
                    LobbyPage.Social -> Modifier.tourAnchor(lobby.anchors, TourAnchor.Social)
                    else -> Modifier
                }
                NavTab(
                    tab.label,
                    tab.activeOn(lobby.page),
                    anchor,
                    unread = (tab.page == LobbyPage.Social && unread) || (tab.page == LobbyPage.News && NewsFeed.unread),
                    underline = if (tab.page == LobbyPage.Home) Modifier.onGloballyPositioned { StartupOverlay.navLine = StartupOverlay.place(it) } else Modifier,
                    padding = NAV_TAB_PADDING,
                ) { lobby.show(tab.page) }
            }
        }
        Box(Modifier.width(NAV_SIDE.dp), contentAlignment = Alignment.CenterEnd) {
            key(lobby.profileVersion) { ProfileCluster(lobby, screen) }
        }
    }
}

private const val NAV_SIDE = 490
private val NAV_TAB_GAP = 4.dp
private val NAV_TAB_PADDING = 12.dp
private const val INVITE_PENDING = "pending"

@Composable
private fun ProfileCluster(lobby: LobbyShell, screen: Screen) {
    Row(Modifier.tourAnchor(lobby.anchors, TourAnchor.Tools).a11yRegion(A11yRegion.Tools), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val mc = Minecraft.getInstance()
        NotificationsButton()
        ModMenuSlot(screen)
        IconButton(themedIcon("settings"), "Mods", Modifier) { PolyPlusOneConfigIntegration.openMods() }
        IconButton(themedIcon("cog"), "Minecraft settings", Modifier) {
            //? if >= 26.3 || < 26.1 {
            ClientPlatform.setScreen(OptionsScreen(screen, mc.options))
            //?} else {
            /*ClientPlatform.setScreen(OptionsScreen(screen, mc.options, false))
            *///?}
        }
        MoreButton(lobby, screen)
        Spacer(Modifier.width(2.dp))
        ProfileChip(lobby, Modifier.weight(1f, fill = false))
        if (!ClientPlatform.isMac) IconButton(themedIcon("maximise"), "Toggle fullscreen", Modifier, ::toggleFullscreen)
        QuitButton()
    }
}

@Composable
private fun ModMenuSlot(screen: Screen) {
    rememberModMenu()?.let { (modMenu, logo) ->
        if (logo == null) {
            IconButton(themedIcon(modMenu.icon), modMenu.tooltip, Modifier) { modMenu.onClick(screen) }
        } else {
            IconButton(modMenu.tooltip, null, Modifier, null, TooltipPlacement.BelowStart, { modMenu.onClick(screen) }) { ModLogo(logo, MOD_LOGO_SIZE.dp, SocialTextPrimary) }
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
            .then(if (switcher) Modifier.clickableWithSound("Accounts, ${ClientPlatform.localPlayerName()}", onClick = lobby::openAccounts) else Modifier)
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(ClientPlatform.localPlayerName(), 14.sp, Modifier.weight(1f, fill = false).widthIn(max = PROFILE_NAME_WIDTH.dp), weight = FontWeight.Medium)
        PlayerHead(ClientPlatform.localPlayerUuid(), 38.dp, Modifier, headShape(38.dp))
    }
}

private const val PROFILE_NAME_WIDTH = 130
private const val MOD_LOGO_SIZE = 16
private const val TAG_EASE_MILLIS = 160
private const val TAG_TWO_LINE_HEIGHT = 46
private const val TAG_WHEREABOUTS_WIDTH = 180
private const val TAG_FOCUS_SCALE = 1.08f
private const val QUIT_ICON = SOCIAL_ASSETS + "x-close.svg"
private const val QUIT_CARD_WIDTH = 330
private const val MORE_MENU_WIDTH = 300
private const val MIN_SHOWN_PLAYTIME = 60_000L
private val BAR_POPUP_GAP = 12.dp

@Composable
private fun NotificationsButton() {
    AnchoredPopupButton(themedIcon("bell"), "Notifications", Modifier, Alignment.End, BAR_POPUP_GAP) { LobbyUserTheme { NotificationsCenter() } }
}

@Composable
private fun QuitButton() {
    val mc = Minecraft.getInstance()
    if (PolyPlusLobbyConfig.quitWithoutConfirmation) {
        IconButton(themedIcon(QUIT_ICON), "Quit game", Modifier, mc::stop)
    } else {
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { PlaySessions.log(ClientPlatform.localPlayerUuid()) } }
        AnchoredPopupButton(themedIcon(QUIT_ICON), "Quit game", Modifier, Alignment.End, BAR_POPUP_GAP) { close -> LobbyUserTheme { QuitConfirm(mc::stop, close) } }
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
                DialogTitle("Quit the game?")
                Label(ClientPlatform.localPlayerName(), 12.sp, color = MutedText)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (today != null || lastPlace != null) QuitRecap(today, lastPlace)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton("Cancel", Modifier.weight(1f).height(38.dp), onCancel)
                TextButton("Quit", themedIcon(QUIT_ICON), Modifier.weight(1f).height(38.dp), ButtonStyle.Danger, true, onQuit)
            }
        }
    }
}

@Composable
private fun QuitPortrait(player: UUID) {
    Box {
        PlayerHead(player, 58.dp, Modifier, headShape(58.dp))
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
private fun MoreButton(lobby: LobbyShell, screen: Screen) {
    AnchoredPopupButton(themedIcon(SOCIAL_ASSETS + "dots-vertical.svg"), "More", Modifier, Alignment.End, BAR_POPUP_GAP) { close -> LobbyUserTheme { MoreMenu(lobby, screen, close) } }
}

@Composable
private fun MoreMenu(lobby: LobbyShell, screen: Screen, onClose: () -> Unit) {
    val theme = themeToggle()
    MenuSurface(Modifier.width(MORE_MENU_WIDTH.dp)) {
        MenuItem(theme.label, themedIcon(theme.icon), null, MenuItemSize.Regular, false, false, true, null, Modifier, null, theme.toggle)
        MenuItem("Switch to PolyPlus main menu", themedIcon(SOCIAL_ASSETS + "logo.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null) {
            onClose()
            MainMenuReplacement.switchToPolyPlusMenu()
        }
        MenuItem("Switch to vanilla main menu", themedIcon(SOCIAL_ASSETS + "minecraft-block.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null) {
            onClose()
            MainMenuReplacement.switchToVanilla()
        }
        MenuItem("Replay tour", themedIcon(SOCIAL_ASSETS + "play-circle.svg"), null, MenuItemSize.Regular, false, false, true, null, Modifier, null) {
            onClose()
            lobby.replayTour()
        }
        if (!PolyPlusMainMenuConfig.hideMainMenuModButtons) {
            ModIntegrationButtons.withoutModMenu().forEach { button ->
                MenuItem(button.tooltip, themedIcon(button.icon), null, MenuItemSize.Regular, false, false, true, null, Modifier, null) {
                    onClose()
                    button.onClick(screen)
                }
            }
        }
    }
}

private fun Modifier.avatarTaps(lobby: LobbyShell, notch: Int): Modifier = pointerInput(lobby, notch) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Press || !event.buttons.isPrimaryPressed || size.width <= 0 || size.height <= 0) continue
            val at = event.changes.firstOrNull()?.position ?: continue
            if (lobby.calm() && LobbyAntics.click(at.x / size.width.toDouble(), (at.y + notch) / (size.height + notch).toDouble())) event.changes.forEach { it.consume() }
        }
    }
}

@Composable
private fun BoxScope.SceneTags(feed: LobbyFeed, root: IntSize, notch: Int) {
    val accent = OneConfigTheme.accent
    SideEffect {
        LobbyHologram.accent = accent.toArgb() and 0xFFFFFF
        feed.root = root
    }
    val tags by feed.tags
    tags.forEach { label ->
        key(label.name) {
            val spot = remember(feed, label.name) { feed.spot(label.name) }
            SceneTag(
                label,
                Modifier.align(Alignment.TopStart).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) {
                        spot.at.longValue
                        val live = spot.label ?: label
                        placeable.placeWithLayer(live.pixelX(root.width) - placeable.width / 2, live.pixelY(root.height) - placeable.height - notch)
                    }
                },
            )
        }
    }
}

internal class LobbyFeed {
    val chrome = mutableFloatStateOf(LobbyScene.overlayAlpha)
    val depart = mutableFloatStateOf(LobbyScene.departProgress)
    val tags = mutableStateOf(emptyList<LobbyScene.Label>(), SameTags)
    var root = IntSize.Zero
    var glass: (() -> Unit)? = null
    private val spots = HashMap<String, TagSpot>()
    private var placed: List<LobbyScene.Label>? = null
    private var placedRoot = IntSize.Zero

    fun pull() {
        chrome.floatValue = if (LobbyWardrobe.open) 1f else LobbyScene.overlayAlpha
        depart.floatValue = LobbyScene.departProgress
        val labels = LobbyScene.labels
        tags.value = labels
        if (labels === placed && root == placedRoot) return
        placed = labels
        placedRoot = root
        for (i in labels.indices.reversed()) {
            val label = labels[i]
            val spot = spot(label.name)
            spot.label = label
            spot.at.longValue = (label.pixelX(root.width).toLong() shl 32) or (label.pixelY(root.height).toLong() and 0xFFFFFFFFL)
        }
        if (spots.size > labels.size) spots.keys.retainAll { name -> labels.any { it.name == name } }
    }

    fun push() {
        glass?.invoke()
    }

    fun spot(name: String): TagSpot = spots.getOrPut(name) { TagSpot() }
}

internal class TagSpot {
    var label: LobbyScene.Label? = null
    val at = mutableLongStateOf(Long.MIN_VALUE)
}

private fun LobbyScene.Label.pixelX(width: Int): Int = ((x + 1f) * 0.5f * width).roundToInt()

private fun LobbyScene.Label.pixelY(height: Int): Int = ((1f - y) * 0.5f * height).roundToInt()

private object SameTags : SnapshotMutationPolicy<List<LobbyScene.Label>> {
    override fun equivalent(a: List<LobbyScene.Label>, b: List<LobbyScene.Label>): Boolean =
        a.size == b.size && a.indices.all { a[it].sameTag(b[it]) }
}

private fun LobbyScene.Label.sameTag(other: LobbyScene.Label): Boolean =
    name == other.name && isLocal == other.isLocal && invite == other.invite && whereabouts == other.whereabouts

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
    val lift by animateFloatAsState(if (focused) TAG_FOCUS_SCALE else 1f, Motion.spec(tween(TAG_EASE_MILLIS, easing = FastOutSlowInEasing)))
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
