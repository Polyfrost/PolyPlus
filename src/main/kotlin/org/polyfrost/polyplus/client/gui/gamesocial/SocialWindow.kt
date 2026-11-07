@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.gamesocial

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.keybind.TextInputFocus
import org.polyfrost.polyplus.client.gui.ContextMenuHost
import org.polyfrost.polyplus.client.gui.HostFlowState
import org.polyfrost.polyplus.client.gui.LocalComposerFocus
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialBorderWidth
import org.polyfrost.polyplus.client.gui.SocialButton
import org.polyfrost.polyplus.client.gui.SocialIconButton
import org.polyfrost.polyplus.client.gui.SocialToastHost
import org.polyfrost.polyplus.client.gui.SocialWindowScrim
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelTitle
import org.polyfrost.polyplus.client.gui.lobbyshell.PlayTogetherHooks
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.lobbysocial.BlockedList
import org.polyfrost.polyplus.client.gui.lobbysocial.BlockedMessage
import org.polyfrost.polyplus.client.gui.lobbysocial.ConversationPane
import org.polyfrost.polyplus.client.gui.lobbysocial.FollowSocialChanges
import org.polyfrost.polyplus.client.gui.lobbysocial.FriendDetailPanel
import org.polyfrost.polyplus.client.gui.lobbysocial.InboxItem
import org.polyfrost.polyplus.client.gui.lobbysocial.LeaderJoinEffect
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyInvitePrompt
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialAvailability
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialChatLink
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDetail
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialDialogHost
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialEscape
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHosting
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialPageState
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSnapshot
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialThread
import org.polyfrost.polyplus.client.gui.lobbysocial.WorldAction
import org.polyfrost.polyplus.client.gui.lobbysocial.inviteItems
import org.polyfrost.polyplus.client.gui.lobbysocial.partyMoveItems
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberAvailability
import org.polyfrost.polyplus.client.gui.lobbysocial.rememberSocialPageState
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.party.PartyJoin
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.ChatIdentity
import kotlin.math.max
import kotlin.math.min
import org.polyfrost.polyplus.client.gui.usableScale
import org.polyfrost.polyplus.client.gui.usableSize

private const val DESIGN_WIDTH = 1280f
private const val DESIGN_HEIGHT = 720f
private const val MIN_SCALE = 0.8f

private const val LEFT_SHARE = 0.34f
private const val LAN_POLL_MILLIS = 250L

private val FullWidth = 1120.dp
private val FullHeight = 640.dp
private val WideLeft = 380.dp
private val NarrowLeft = 300.dp
private val WideCanvas = 1176.dp
private val TwoPaneCanvas = 720.dp
private val ShortCanvas = 520.dp
private val WideMargin = 56.dp
private val TightMargin = 24.dp

internal class SocialWindowLayout(canvasWidth: Dp, canvasHeight: Dp) {
    val singlePane = canvasWidth < TwoPaneCanvas
    val short = canvasHeight < ShortCanvas
    val width = when {
        canvasWidth >= WideCanvas -> FullWidth
        singlePane -> canvasWidth - TightMargin
        else -> canvasWidth - WideMargin
    }
    val height = minOf(FullHeight, canvasHeight - if (short) TightMargin else WideMargin)
    val left = if (singlePane) width else (width * LEFT_SHARE).coerceIn(NarrowLeft, WideLeft)
    val x = (canvasWidth - width) / 2
    val y = (canvasHeight - height) / 2
}

internal enum class WindowView { Party, Voice, World }

internal class SocialWindowState(val screen: Screen, openWorld: Boolean) {
    val composer = FocusRequester()
    val hostFlow = HostFlowState(hostingCurrent = true, preselected = false, inline = true)
    var blockedOpen by mutableStateOf(false)
    var view by mutableStateOf(if (openWorld) WindowView.World else null)
    var singlePane by mutableStateOf(false)
    var lanOpen by mutableStateOf(SocialHosting.lanOpen)
    var page: SocialPageState? = null
    private var behindVoice: WindowView? = null

    fun showBlocked() {
        page?.selected = null
        SocialChatLink.close()
        view = null
        blockedOpen = true
    }

    fun showParty() {
        page?.selected = null
        blockedOpen = false
        view = WindowView.Party
    }

    fun closeParty() {
        SocialChatLink.close()
        view = null
    }

    fun showWorld(invitees: Collection<String> = emptyList()) {
        page?.selected = null
        blockedOpen = false
        SocialChatLink.close()
        if (invitees.isNotEmpty()) {
            hostFlow.invitees = hostFlow.invitees + invitees
            hostFlow.forFriends = true
        }
        view = WindowView.World
    }

    fun closeWorld() {
        view = null
    }

    fun showVoice() {
        if (view == WindowView.Voice) return
        behindVoice = view
        view = WindowView.Voice
    }

    fun closeVoice() {
        view = behindVoice
        behindVoice = null
    }

    private fun back(): Boolean {
        if (!singlePane) return false
        val state = page
        return when {
            view == WindowView.Voice -> {
                closeVoice()
                true
            }
            view == WindowView.Party -> {
                closeParty()
                true
            }
            view == WindowView.World -> {
                closeWorld()
                true
            }
            blockedOpen -> {
                blockedOpen = false
                true
            }
            state?.selected != null -> {
                state.selected = null
                true
            }
            else -> false
        }
    }

    fun escape(): Boolean = SocialEscape.window(::back)

    fun enter(): Boolean {
        val covered = SocialEscape.popupOpen || SocialPageState.dialogOpen || TextInputFocus.isTyping
        if (covered || SocialChatLink.active == null) return false
        return runCatching { composer.requestFocus() }.isSuccess
    }
}

private object WindowHost : SocialHost {
    override fun join(data: ServerData) = PartyJoin.server(data)
}

@Composable
internal fun SocialWindow(window: SocialWindowState, onClose: () -> Unit) {
    val state = rememberSocialPageState()
    val availability = rememberAvailability()
    DisposableEffect(window, state) {
        window.page = state
        SocialChatLink.close()
        SocialPageState.closeDialog()
        onDispose {
            window.page = null
            SocialChatLink.close()
            PartyJoin.cancel()
        }
    }
    LaunchedEffect(Unit) { PartyRepository.refresh() }
    LaunchedEffect(window) {
        while (true) {
            window.lanOpen = SocialHosting.lanOpen
            delay(LAN_POLL_MILLIS)
        }
    }
    var reached by remember { mutableStateOf(false) }
    LaunchedEffect(availability) { if (availability == SocialAvailability.Ready) reached = true }
    val connecting = availability == SocialAvailability.Connecting
    val blocked = if (connecting) !reached else availability != SocialAvailability.Ready
    var measured by remember { mutableStateOf(IntSize.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { measured = it }) {
        val outer = LocalDensity.current
        val widthPx = if (measured.width > 0) measured.width.toFloat() else maxWidth.value * outer.density
        val heightPx = if (measured.height > 0) measured.height.toFloat() else maxHeight.value * outer.density
        if (!usableSize(widthPx, heightPx)) return@BoxWithConstraints
        val scale = max(min(widthPx / DESIGN_WIDTH, heightPx / DESIGN_HEIGHT), MIN_SCALE)
        val density = remember(scale, outer.fontScale) { Density(usableScale(scale), usableScale(outer.fontScale)) }
        val layout = remember(widthPx, heightPx, scale) { SocialWindowLayout((widthPx / scale).dp, (heightPx / scale).dp) }
        window.singlePane = layout.singlePane
        val look = remember(layout.singlePane) { SocialLook.InGame.copy(backButton = layout.singlePane) }
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalSocialLook provides look,
            LocalComposerFocus provides window.composer,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SocialWindowScrim)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose),
            ) {
                ContextMenuHost {
                    val snapshot = state.source.snapshot()
                    if (blocked) {
                        BlockedWindow(window, layout, availability, state, snapshot, onClose)
                    } else {
                        FollowSocialChanges(state, snapshot)
                        SelectionEffects(window, state)
                        TwoPanes(window, layout, state, snapshot, connecting, onClose)
                    }
                    SocialToastHost(Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp))
                    SocialDialogHost(state)
                    PlayTogetherHooks(WindowHost)
                    LeaderJoinEffect(WindowHost)
                }
            }
        }
    }
}

@Composable
private fun SelectionEffects(window: SocialWindowState, state: SocialPageState) {
    val active = SocialChatLink.active
    val selected = state.selected
    val party by PartyRepository.party.collectAsState()
    val inParty = party != null
    LaunchedEffect(active) {
        if (active != null) {
            state.selected = null
            window.blockedOpen = false
            if (active.id != PartyRepository.party.value?.chatGroupId) window.view = null
        }
    }
    LaunchedEffect(selected) {
        if (selected != null) {
            SocialChatLink.close()
            window.blockedOpen = false
            window.view = null
        }
    }
    LaunchedEffect(inParty) {
        if (!inParty && window.view == WindowView.Party) window.closeParty()
    }
}

@Composable
private fun Modifier.windowFrame(layout: SocialWindowLayout): Modifier {
    val focus = LocalFocusManager.current
    return this
        .offset(layout.x, layout.y)
        .width(layout.width)
        .height(layout.height)
        .panel()
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { focus.clearFocus() }
}

@Composable
private fun BlockedWindow(
    window: SocialWindowState,
    layout: SocialWindowLayout,
    availability: SocialAvailability,
    state: SocialPageState,
    snapshot: SocialSnapshot,
    onClose: () -> Unit,
) {
    Column(Modifier.windowFrame(layout), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelfHeader(state, snapshot, false, onClose, Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp))
        val body = Modifier.fillMaxWidth().weight(1f)
        if (window.view == WindowView.World && availability != SocialAvailability.Declined) {
            WorldPane(window, state, snapshot, body, closable = false)
        } else {
            BlockedMessage(availability, state.source, onClose, body)
        }
    }
}

@Composable
private fun TwoPanes(
    window: SocialWindowState,
    layout: SocialWindowLayout,
    state: SocialPageState,
    snapshot: SocialSnapshot,
    reconnecting: Boolean,
    onClose: () -> Unit,
) {
    val detail = state.detail(snapshot)
    val opened = detail != null || window.blockedOpen || window.view != null
    val invites = inviteItems(snapshot, state.source) + partyMoveItems()
    Row(Modifier.windowFrame(layout)) {
        if (!layout.singlePane || !opened) {
            LeftPane(window, state, snapshot, invites, reconnecting, onClose, Modifier.width(layout.left).fillMaxHeight())
        }
        if (layout.singlePane) {
            if (opened) DetailPane(window, layout, state, snapshot, detail, invites, reconnecting, Modifier.fillMaxSize())
        } else {
            Box(Modifier.width(SocialBorderWidth).fillMaxHeight().background(SocialBorderColor))
            DetailPane(window, layout, state, snapshot, detail, invites, reconnecting, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun DetailPane(
    window: SocialWindowState,
    layout: SocialWindowLayout,
    state: SocialPageState,
    snapshot: SocialSnapshot,
    detail: SocialDetail?,
    invites: List<InboxItem>,
    reconnecting: Boolean,
    modifier: Modifier,
) {
    val current by PartyRepository.party.collectAsState()
    val party = current
    when {
        window.view == WindowView.Voice -> VoicePane(window, modifier)
        window.view == WindowView.Party && party != null -> PartyPane(window, WindowHost, state, snapshot, party, reconnecting, modifier)
        window.view == WindowView.World -> WorldPane(window, state, snapshot, modifier)
        window.blockedOpen -> Column(modifier) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PanelTitle("Blocked", Modifier.weight(1f))
                SocialIconButton(LobbyIcons.CLOSE, tooltip = "Close") { window.blockedOpen = false }
            }
            PaneRule()
            BlockedList(state, snapshot, Modifier.fillMaxWidth().weight(1f))
        }
        detail is SocialDetail.Friend -> FriendDetailPanel(state, snapshot, detail.person, modifier)
        detail is SocialDetail.Conversation -> ConversationPane(
            state,
            detail.chat,
            detail.thread,
            modifier,
            subtitle = memberLine(detail.thread),
            offlineReason = "Reconnecting".takeIf { reconnecting },
            actions = { group -> HeaderAction(window, state, snapshot, detail.thread, group) },
            trailing = {
                if (!layout.singlePane) SocialIconButton(LobbyIcons.CLOSE, tooltip = "Close chat") { SocialChatLink.close() }
            },
        )
        else -> OverviewPane(WindowHost, state, snapshot, invites, window::showVoice, modifier)
    }
}

@Composable
internal fun PaneRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(SocialBorderWidth).background(SocialBorderColor))
}

private fun memberLine(thread: SocialThread): String? {
    if (!thread.group) return null
    return "${thread.people.size + 1} members · ${thread.people.count { it.online } + 1} online"
}

@Composable
private fun RowScope.HeaderAction(window: SocialWindowState, state: SocialPageState, snapshot: SocialSnapshot, thread: SocialThread, group: GroupSummary) {
    val person = thread.people.singleOrNull().takeIf { !thread.group }
    when {
        person != null && snapshot.hosting != null -> WorldAction(state, snapshot, person, filled = true)
        snapshot.hosting == null && SocialHosting.singleplayer -> StartHostingButton(window, group)
        person != null && person.online -> PartyInvitePrompt(person.id, filled = true)
        snapshot.hosting != null -> InviteGroupButton(group)
    }
}

@Composable
private fun InviteGroupButton(group: GroupSummary) {
    SocialButton("Invite to world", filled = true) { SocialHosting.inviteGroup(group, ChatIdentity.selfId()) }
}

@Composable
private fun StartHostingButton(window: SocialWindowState, group: GroupSummary) {
    SocialButton("Invite to world", filled = true) { window.showWorld(group.members.filterNot { it == ChatIdentity.selfId() }) }
}
