@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.polyplus.client.gui.KitDangerColor
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.OptionsScreen
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.components.AnchoredPopupButton
import org.polyfrost.oneconfig.api.ui.v1.components.ButtonDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.DialogTitle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.MenuEntry
import org.polyfrost.oneconfig.api.ui.v1.components.MenuItemSize
import org.polyfrost.oneconfig.api.ui.v1.components.MenuList
import org.polyfrost.oneconfig.api.ui.v1.components.NotificationsCenterPanel
import org.polyfrost.oneconfig.api.ui.v1.components.Placement
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.TooltipPlacement
import org.polyfrost.oneconfig.api.ui.v1.components.oneConfigClickable
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.PolyPlusLobbyConfig
import org.polyfrost.polyplus.client.gui.SocialDangerColor
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.AccountSwitcherContent
import org.polyfrost.polyplus.client.gui.BadModsDialog
import org.polyfrost.polyplus.client.gui.FooterBrandText
import org.polyfrost.polyplus.client.gui.MainMenuReplacement
import org.polyfrost.polyplus.client.gui.MainMenuRasterAssets
import org.polyfrost.polyplus.client.gui.MenuScale
import org.polyfrost.polyplus.client.gui.ModIntegrationButtons
import org.polyfrost.polyplus.client.gui.Outfit
import org.polyfrost.polyplus.client.gui.PolyPlusOneConfigIntegration
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialFieldShape
import org.polyfrost.polyplus.client.gui.CopyrightText
import org.polyfrost.polyplus.client.gui.SocialHoverBorder
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.SocialTextPrimary
import org.polyfrost.polyplus.client.gui.SocialTextSecondary
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.oneconfig.api.ui.v1.components.ToastDefaults
import org.polyfrost.polyplus.client.gui.A11yRegion
import org.polyfrost.polyplus.client.gui.SocialNotices
import org.polyfrost.polyplus.client.gui.a11yRegion
import org.polyfrost.polyplus.client.gui.a11yRoot
import org.polyfrost.polyplus.client.social.SocialErrors
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
import org.polyfrost.polyplus.compat.LinkedModMenus
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import androidx.compose.ui.text.style.TextOverflow

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
        val scale = MenuScale.scale(minOf(widthPx / LobbyLayout.DESIGN_WIDTH, (heightPx - notchPx) / LobbyLayout.DESIGN_HEIGHT))
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
    LobbyDarkChrome {
        val sceneScrim = OneConfigTheme.current.pageBackground.copy(alpha = 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    val height = 150.dp.roundToPx() + notch
                    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
                    layout(placeable.width, placeable.height - notch) { placeable.place(0, -notch) }
                }
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
    LobbyDarkChrome { Navbar(lobby, screen) }
    val home = lobby.page == LobbyPage.Home
    val social = !LobbyPage.Social.hidden
    val stackChat by Motion.animateFloatAsState(if (home && social) 1f else 0f, spring())
    val barChatShown = !home && lobby.page != LobbyPage.Social && social
    val barChatWidth by Motion.animateDpAsState(if (barChatShown) LobbyLayout.ChatWidth else 0.dp, spring(visibilityThreshold = Dp.VisibilityThreshold))
    val media = remember { LobbyMediaState() }
    val track = if (home) rememberLobbyMedia() else null
    val large = rememberLobbyMediaLarge()
    if (home) {
        media.card = track != null && large
        media.tile = track != null && !large
    }
    if (track != null) media.track = track
    val cardShown by Motion.animateFloatAsState(if (media.card) 1f else 0f, mediaSwapSpec())
    val tileShown by Motion.animateFloatAsState(if (media.tile) 1f else 0f, mediaSwapSpec())
    var dockTiles by remember { mutableIntStateOf(0) }
    var dockMedia by remember { mutableIntStateOf(0) }
    BoxWithConstraints(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = LobbyLayout.Margin, end = LobbyLayout.BarEnd, bottom = LobbyLayout.Margin)
            .a11yRegion(A11yRegion.Bar),
    ) {
        val density = LocalDensity.current
        val row = STACK_BAR_MIN + LobbyLayout.Gap + with(density) { dockTiles.toDp() }
        val cardWidth = maxOf(LobbyMediaCardWidth, row).coerceAtMost(maxWidth)
        val rowWidth = row + with(density) { dockMedia.toDp() } * tileShown
        val stackWidth = maxOf(lerp(LobbyLayout.ChatWidth, cardWidth, cardShown), rowWidth).coerceAtMost(maxWidth)
        Column {
            if (home) {
                val shownTrack = media.track
                if (cardShown > 0f && shownTrack != null) LobbyMediaCard(shownTrack, cardShown, LobbyLayout.Gap, Modifier.width(cardWidth))
                if (LinkedModMenus.entries.isNotEmpty()) {
                    LinkedModButton(Modifier.width(stackWidth))
                    Spacer(Modifier.height(LobbyLayout.Gap))
                }
            }
            if (stackChat > 0f) {
                ChatDock(lobby, stackWidth * stackChat, stackWidth, fill = true)
                Spacer(Modifier.height(LobbyLayout.Gap))
            }
            if (home) {
                Row(Modifier.width(stackWidth), verticalAlignment = Alignment.Bottom) {
                    key(lobby.profileVersion) { PlayerBar(lobby, Modifier.weight(1f).onGloballyPositioned { barStart = it.positionInRoot().x }, fill = true) }
                    Spacer(Modifier.width(LobbyLayout.Gap))
                    LobbyDock(lobby, media.track, tileShown, { dockTiles = it }, { dockMedia = it })
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (barChatShown || barChatWidth > 0.dp) {
                        ChatDock(lobby, barChatWidth, LobbyLayout.ChatWidth, fill = false)
                        Spacer(Modifier.width(barChatWidth.coerceAtMost(LobbyLayout.Gap)))
                    }
                    key(lobby.profileVersion) { PlayerBar(lobby, Modifier.weight(1f, fill = false)) }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize().a11yRegion(A11yRegion.Scene)) { SceneControl(lobby) { barStart } }
    PlayUnit(
        lobby,
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = LobbyLayout.Margin, bottom = LobbyLayout.Margin)
            .width(LobbyLayout.UnitWidth)
            .tourAnchor(lobby.anchors, TourAnchor.Play)
            .a11yRegion(A11yRegion.Play),
    )
    if (!PolyPlusMainMenuConfig.hideMainMenuFooter) LobbyDarkChrome { Footer(screen) }
    if (lobby.page != LobbyPage.Social) {
        SocialToastHost(Modifier.padding(top = LobbyLayout.Top - ToastDefaults.margin))
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
}

@Composable
private fun ChatDock(lobby: LobbyShell, width: Dp, panelWidth: Dp, fill: Boolean) {
    val open = lobby.page == LobbyPage.Home
    val party by PartyRepository.party.collectAsState()
    val prompt = !open || (SocialChatLink.active == null && party?.chatGroupId == null)
    val hover = rememberHoverState()
    val shape = OneConfigTheme.shape(20.dp)
    val action = if (prompt) {
        Modifier
            .border(1.dp, if (hover.hovered) SocialHoverBorder else Color.Transparent, shape)
            .hoverable(hover.interactionSource)
            .oneConfigClickable("Open Social", { lobby.openSocial() }, shape = shape)
    } else {
        Modifier
    }
    Box(Modifier.clip(shape).revealWidth(width, panelWidth).then(action)) {
        ChatPanel(open, panelWidth, fill)
    }
}

private fun Modifier.revealWidth(width: Dp, panelWidth: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = panelWidth.roundToPx().coerceAtMost(constraints.maxWidth)))
    val shown = minOf(placeable.width, width.roundToPx()).coerceIn(constraints.minWidth, constraints.maxWidth)
    layout(shown, placeable.height) { placeable.place(0, 0) }
}

@Composable
private fun BoxScope.Footer(screen: Screen) {
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
        FooterBrandText("${platformLabel()}   PolyPlus ${PolyPlusConstants.VERSION}", 11.sp, SocialTextPrimary)
        Spacer(Modifier.weight(1f))
        CopyrightText(screen, 11.sp, SocialTextSecondary, maxLines = 1)
    }
}

@Composable
private fun Navbar(lobby: LobbyShell, screen: Screen) {
    val groups by GroupsRepository.groups.collectAsState()
    val requests by FriendsRepository.incomingRequests.collectAsState()
    val invites by SessionsRepository.incomingInvites.collectAsState()
    val partyInvites by PartyRepository.incomingInvites.collectAsState()
    val unread = groups.any { it.unread } || requests.isNotEmpty() || partyInvites.isNotEmpty() || invites.any { it.status == INVITE_PENDING }
    NavbarRow(lobby, screen, unread)
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
        val density = LocalDensity.current
        val visible = TABS.filterNot { it.page.hidden }
        val items = visible.map { tab ->
            val modifier = when (tab.page) {
                LobbyPage.Home -> Modifier.onGloballyPositioned { StartupOverlay.navLine = navUnderline(it, density) }
                LobbyPage.Wardrobe -> Modifier.tourAnchor(lobby.anchors, TourAnchor.Wardrobe)
                LobbyPage.Social -> Modifier.tourAnchor(lobby.anchors, TourAnchor.Social)
                else -> Modifier
            }
            val dot = when {
                tab.page == LobbyPage.Social -> socialDotLabel(unread)
                tab.page == LobbyPage.News && NewsFeed.unread -> "Unread news"
                else -> null
            }
            TabItem(tab.page, tab.label, badge = navTabBadge(dot != null), modifier = modifier, badgeDescription = dot)
        }
        Box(Modifier.weight(1f).a11yRegion(A11yRegion.Nav), contentAlignment = Alignment.Center) {
            NavTabRow(items, visible.firstOrNull { it.activeOn(lobby.page) }?.page ?: lobby.page, lobby::show)
        }
        Box(Modifier.width(NAV_SIDE.dp), contentAlignment = Alignment.CenterEnd) {
            key(lobby.profileVersion) { ProfileCluster(lobby, screen) }
        }
    }
}

private const val NAV_SIDE = 490
private const val INVITE_PENDING = "pending"

@Composable
private fun ProfileCluster(lobby: LobbyShell, screen: Screen) {
    Row(Modifier.tourAnchor(lobby.anchors, TourAnchor.Tools).a11yRegion(A11yRegion.Tools), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val mc = Minecraft.getInstance()
        NotificationsButton()
        ModMenuSlot(screen)
        NavIconButton("settings", "Mods", { PolyPlusOneConfigIntegration.openMods() })
        NavIconButton("cog", "Minecraft settings", {
            //? if >= 26.3 || < 26.1 {
            ClientPlatform.setScreen(OptionsScreen(screen, mc.options))
            //?} else {
            /*ClientPlatform.setScreen(OptionsScreen(screen, mc.options, false))
            *///?}
        })
        MoreButton(lobby, screen)
        Spacer(Modifier.width(2.dp))
        ProfileChip(lobby, Modifier.weight(1f, fill = false))
        if (!ClientPlatform.isMac) NavIconButton("maximise", "Toggle fullscreen", ::toggleFullscreen)
        QuitButton()
    }
}

internal val NavButtonSize = 34.dp

@Composable
internal fun NavIconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    active: Boolean = false,
    tooltip: String? = contentDescription,
    tooltipPlacement: TooltipPlacement = TooltipPlacement.BelowStart,
    frameSize: Dp = NavButtonSize,
) {
    IconButton(
        icon,
        contentDescription,
        onClick,
        modifier,
        style = IconButtonStyle.Hairline,
        tooltip = tooltip,
        tooltipPlacement = tooltipPlacement,
        tint = tint,
        restTint = SocialTextPrimary,
        active = active,
        frameSize = frameSize,
    )
}

@Composable
internal fun NavPopupButton(
    icon: String,
    contentDescription: String,
    gap: Dp = BAR_POPUP_GAP,
    placement: Placement = Placement.BelowEnd,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    AnchoredPopupButton(
        contentDescription,
        { open, toggle, triggerModifier ->
            NavIconButton(icon, contentDescription, toggle, triggerModifier, active = open, tooltip = if (open) null else contentDescription, tooltipPlacement = TooltipPlacement.BelowCenter)
        },
        placement = placement,
        gap = gap,
        content = content,
    )
}

@Composable
private fun ModMenuSlot(screen: Screen) {
    rememberModMenu()?.let { (modMenu, logo) ->
        if (logo == null) {
            NavIconButton(modMenu.icon, modMenu.tooltip, { modMenu.onClick(screen) })
        } else {
            IconButton(
                modMenu.tooltip,
                { modMenu.onClick(screen) },
                style = IconButtonStyle.Hairline,
                tooltipPlacement = TooltipPlacement.BelowStart,
                restTint = SocialTextPrimary,
                frameSize = NavButtonSize,
            ) { tint -> ModLogo(logo, MOD_LOGO_SIZE, tint) }
        }
    }
}

@Composable
private fun ProfileChip(lobby: LobbyShell, modifier: Modifier) {
    val switcher = !PolyPlusMainMenuConfig.hideMainMenuAltManager
    val hover = rememberHoverState()
    Row(
        modifier
            .rowFrame(false, hover.hovered && switcher)
            .hoverable(hover.interactionSource)
            .then(if (switcher) Modifier.oneConfigClickable("Accounts, ${ClientPlatform.localPlayerName()}", lobby::openAccounts) else Modifier)
            .padding(start = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ClientPlatform.localPlayerName(), Modifier.weight(1f, fill = false), style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        PlayerHead(playerId = ClientPlatform.localPlayerUuid(), size = 38.dp, modifier = Modifier, shape = headShape(38.dp))
    }
}

private const val TAG_EASE_MILLIS = 160
private const val TAG_TWO_LINE_HEIGHT = 46
private const val TAG_WHEREABOUTS_WIDTH = 180
private const val TAG_FOCUS_SCALE = 1.08f
private const val QUIT_ICON = SOCIAL_ASSETS + "x-close.svg"
private const val QUIT_CARD_WIDTH = 330
private const val MORE_MENU_WIDTH = 300
private const val MIN_SHOWN_PLAYTIME = 60_000L
private const val QUIT_ARM_MILLIS = 3_000L
private val BAR_POPUP_GAP = 12.dp
private val MOD_LOGO_SIZE = 16.dp
private val STACK_BAR_MIN = 220.dp

@Composable
private fun NotificationsButton() {
    NavPopupButton("bell", "Notifications") { LobbyUserTheme { NotificationsCenterPanel() } }
}

@Composable
private fun QuitButton() {
    val mc = Minecraft.getInstance()
    when {
        PolyPlusLobbyConfig.quitWithoutConfirmation -> NavIconButton(QUIT_ICON, "Quit game", mc::stop)
        PolyPlusLobbyConfig.quitClickTwice -> QuitTwiceButton(mc::stop)
        else -> {
            LaunchedEffect(Unit) { withContext(Dispatchers.IO) { PlaySessions.log(ClientPlatform.localPlayerUuid()) } }
            NavPopupButton(QUIT_ICON, "Quit game") { close -> LobbyUserTheme { QuitConfirm(mc::stop, close) } }
        }
    }
}

@Composable
private fun QuitTwiceButton(onQuit: () -> Unit) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(QUIT_ARM_MILLIS)
            armed = false
        }
    }
    NavIconButton(
        QUIT_ICON,
        if (armed) "Click again to quit" else "Quit game",
        {
            if (armed) {
                onQuit()
            } else {
                armed = true
                A11y.announce("Click again to quit")
            }
        },
        tint = if (armed) KitDangerColor else Color.Unspecified,
        active = armed,
    )
}

@Composable
private fun QuitConfirm(onQuit: () -> Unit, onCancel: () -> Unit) {
    var skipNextTime by remember { mutableStateOf(false) }
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
                Text(ClientPlatform.localPlayerName(), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, top = 12.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (today != null || lastPlace != null) QuitRecap(today, lastPlace)
            ToggleRow(label = "Don't ask again", checked = skipNextTime, onCheckedChange = { skipNextTime = it }, modifier = Modifier)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f), size = ControlSize.Large)
                TextButton(text = "Quit", icon = QUIT_ICON, onClick = {
                    if (skipNextTime) {
                        PolyPlusLobbyConfig.quitWithoutConfirmation = true
                        PolyPlusLobbyConfig.save()
                    }
                    onQuit()
                }, modifier = Modifier.weight(1f), style = ControlStyle.Danger, size = ControlSize.Large, dangerColor = KitDangerColor)
            }
        }
    }
}

@Composable
private fun QuitPortrait(player: UUID) {
    Box {
        PlayerHead(playerId = player, size = 58.dp, modifier = Modifier, shape = headShape(58.dp))
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 8.dp)
                .size(28.dp)
                .clip(OneConfigTheme.current.circleShape)
                .background(OneConfigTheme.current.popupBackground)
                .padding(2.dp)
                .clip(OneConfigTheme.current.circleShape)
                .background(SocialDangerColor),
            contentAlignment = Alignment.Center,
        ) {
            LobbyIcon("x-close", ButtonDefaults.contentColor(ControlStyle.DangerFilled), 14.dp)
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
        Text(label, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Text(value, style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
    }
}

@Composable
private fun MoreButton(lobby: LobbyShell, screen: Screen) {
    NavPopupButton(SOCIAL_ASSETS + "dots-vertical.svg", "More") { close -> LobbyUserTheme { MoreMenu(lobby, screen, close) } }
}

@Composable
private fun MoreMenu(lobby: LobbyShell, screen: Screen, onClose: () -> Unit) {
    val theme = themeToggle()
    val entries = buildList<MenuEntry> {
        if (theme != null) add(MenuAction(theme.label, theme.toggle, icon = theme.icon, size = MenuItemSize.Regular))
        add(MenuAction("Switch to PolyPlus main menu", { onClose(); MainMenuReplacement.switchToPolyPlusMenu() }, icon = SOCIAL_ASSETS + "logo.svg", size = MenuItemSize.Regular))
        add(MenuAction("Switch to vanilla main menu", { onClose(); MainMenuReplacement.switchToVanilla() }, icon = SOCIAL_ASSETS + "minecraft-block.svg", size = MenuItemSize.Regular))
        add(MenuAction("Replay tour", { onClose(); lobby.replayTour() }, icon = SOCIAL_ASSETS + "play-circle.svg", size = MenuItemSize.Regular))
        if (!PolyPlusMainMenuConfig.hideMainMenuModButtons) {
            ModIntegrationButtons.withoutModMenu().forEach { button ->
                add(MenuAction(button.tooltip, { onClose(); button.onClick(screen) }, icon = button.icon, size = MenuItemSize.Regular))
            }
        }
    }
    MenuList(entries, {}, Modifier.width(MORE_MENU_WIDTH.dp), MenuItemSize.Regular)
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
    val ring by Motion.animateColorAsState(
        if (speaking) SocialSuccessColor else if (label.isLocal || focused) OneConfigTheme.accent else SocialBorderColor,
        tween(TAG_EASE_MILLIS, easing = FastOutSlowInEasing),
    )
    val lift by Motion.animateFloatAsState(if (focused) TAG_FOCUS_SCALE else 1f, tween(TAG_EASE_MILLIS, easing = FastOutSlowInEasing))
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
                Text(label.name, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                label.whereabouts?.let { Text(it, Modifier.widthIn(max = TAG_WHEREABOUTS_WIDTH.dp), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false) }
            }
            if (label.isLocal) Text("You", color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            label.invite?.let { SceneTagInvite(it) }
        }
    }
}
