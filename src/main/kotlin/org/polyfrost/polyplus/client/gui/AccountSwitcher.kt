// OneConfig's public UI API is still marked experimental
@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui

import org.polyfrost.polyplus.client.gui.screens.ShellModal
import org.polyfrost.polyplus.client.gui.screens.shellModalSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import org.polyfrost.compose.host.controller.ControllerButton
import org.polyfrost.compose.host.controller.controllerAction
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.minecraft.client.Minecraft
//? if = 1.8.9 {
/*import net.minecraft.client.gui.screens.Screen
*///?}
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.a11y.Announce
import org.polyfrost.oneconfig.api.ui.v1.a11y.Motion
import org.polyfrost.oneconfig.api.ui.v1.a11y.notFocusable
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Divider
import org.polyfrost.oneconfig.api.ui.v1.components.EscapeCloses
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.lobbyshell.ConsentStep
import org.polyfrost.polyplus.client.gui.lobbyshell.CustomizeKit
import org.polyfrost.polyplus.client.gui.lobbyshell.DrillRow
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelHeader
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelHint
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageColumn
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelPageSwitch
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelSection
import org.polyfrost.polyplus.client.gui.lobbyshell.PanelToggle
import org.polyfrost.polyplus.client.gui.lobbyshell.card
import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.polyplus.client.gui.lobbyshell.panel
import org.polyfrost.polyplus.client.gui.preview.PlayerPreview
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.launcher.OneLauncherAccounts
import org.polyfrost.polyplus.client.social.DiscordLink
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

internal enum class AccountsPage(val title: String) {
    Home("Accounts"),
    AddMicrosoft("Add Microsoft account"),
    AddOffline("Add offline account"),
    OnlineServices("Online services"),
}

internal class AccountsPanelState(val actions: AccountActions) {
    var page by mutableStateOf(AccountsPage.Home)
        private set
    var forward by mutableStateOf(true)
        private set
    var consentVersion by mutableIntStateOf(0)
        private set
    var pendingId by mutableStateOf<UUID?>(null)
        private set
    private var loginSeen = false

    fun open(target: AccountsPage) {
        if (target == page) return
        if (target != AccountsPage.Home && !actions.idle) return
        leave()
        forward = target != AccountsPage.Home
        page = target
        when (target) {
            AccountsPage.AddMicrosoft -> startLogin()
            AccountsPage.AddOffline -> actions.openOfflineEntry()
            else -> actions.clearError()
        }
    }

    fun back(): Boolean {
        if (page == AccountsPage.Home) return false
        open(AccountsPage.Home)
        return true
    }

    fun reset() {
        leave()
        forward = false
        page = AccountsPage.Home
    }

    fun startLogin() {
        loginSeen = false
        actions.addMicrosoft()
    }

    fun switchTo(account: OneLauncherAccounts.Account) {
        if (!actions.idle || account.active) return
        pendingId = account.id
        actions.switchTo(account)
    }

    fun remove(account: OneLauncherAccounts.Account) {
        if (!actions.idle) return
        pendingId = account.id
        actions.remove(account)
    }

    fun refresh(account: OneLauncherAccounts.Account) {
        if (!actions.idle) return
        actions.refresh(account)
    }

    fun consentGiven() {
        consentVersion++
        DiscordLink.refresh()
        open(AccountsPage.Home)
    }

    internal fun sync() {
        if (actions.idle) pendingId = null
        when (page) {
            AccountsPage.AddMicrosoft -> {
                if (!actions.idle) loginSeen = true
                if (loginSeen && actions.idle && actions.loginSession == null && actions.error == null) finish()
            }
            AccountsPage.AddOffline -> if (!actions.offlineEntry) finish()
            else -> Unit
        }
    }

    private fun finish() {
        forward = false
        page = AccountsPage.Home
    }

    private fun leave() {
        when (page) {
            AccountsPage.AddMicrosoft -> actions.cancelLogin()
            AccountsPage.AddOffline -> actions.cancelOfflineEntry()
            else -> Unit
        }
    }
}

@Composable
internal fun rememberAccountsPanelState(): AccountsPanelState {
    val actions = rememberAccountActions()
    return remember(actions) { AccountsPanelState(actions) }
}

@Composable
internal fun AccountsPanelContent(state: AccountsPanelState) {
    val actions = state.actions
    LaunchedEffect(actions.busy, actions.loginSession, actions.offlineEntry, actions.error) { state.sync() }
    PanelPageSwitch(state.page, state.forward) { page ->
        when (page) {
            AccountsPage.Home -> AccountsHome(state)
            AccountsPage.AddMicrosoft -> MicrosoftPage(state)
            AccountsPage.AddOffline -> OfflinePage(state)
            AccountsPage.OnlineServices -> PanelPageColumn {
                ConsentStep("Linking Discord needs online services.", Modifier.fillMaxWidth(), { state.back() }, state::consentGiven)
            }
        }
    }
}

@Composable
internal fun AccountSwitcherContent(actions: AccountActions, onClose: () -> Unit) {
    val state = remember(actions) { AccountsPanelState(actions) }
    LaunchedEffect(Unit) { actions.reload() }
    val close = {
        state.reset()
        onClose()
    }
    EscapeCloses { if (!state.back()) close() }
    CompositionLocalProvider(LocalDensity provides scaledDensity(ShellModal.UI_SCALE)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SocialWindowScrim)
                .notFocusable()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = close),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                Modifier
                    .shellModalSize()
                    .panel(CARD_RADIUS)
                    .notFocusable()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                if (!PolyPlusMainMenuConfig.hideMainMenuAltManager) {
                    key(actions.accounts?.firstOrNull { it.active }?.id) {
                        PlayerPreview(
                            Modifier.fillMaxWidth(PREVIEW_SHARE).widthIn(max = PREVIEW_MAX_WIDTH).fillMaxHeight(),
                            source = PlayerPreviewSource.LocalLive,
                            initialYaw = 180f + 22.9f,
                            live = true,
                            modelScale = 0.35F,
                        )
                    }
                    Divider(vertical = true)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Announce("Accounts")
                    val page = state.page
                    PanelHeader(page.title, close, if (page != AccountsPage.Home) ({ state.back() }) else null)
                    Box(Modifier.weight(1f).fillMaxWidth()) { AccountsPanelContent(state) }
                }
            }
        }
    }
}

@Composable
private fun AccountsHome(state: AccountsPanelState) {
    val actions = state.actions
    val showAccounts = !PolyPlusMainMenuConfig.hideMainMenuAltManager
    PanelPageColumn {
        if (showAccounts) {
            PanelSection("Minecraft accounts", divider = false) {
                val accounts = actions.accounts
                when {
                    accounts == null -> repeat(SKELETON_ROWS) { SkeletonRow() }
                    accounts.isEmpty() -> PanelHint("No accounts yet. Add one below.")
                    else -> accounts.forEach { account -> key(account.id) { AccountRow(state, account) } }
                }
                if (state.page == AccountsPage.Home) AccountError(actions.error, actions.errorSteps)
                DrillRow("globe-01", "Add Microsoft account", null, { state.open(AccountsPage.AddMicrosoft) }, enabled = actions.idle)
                DrillRow("user-plus-01", "Add offline account", null, { state.open(AccountsPage.AddOffline) }, enabled = actions.idle)
            }
        }
        PanelSection("Connected", divider = showAccounts) {
            ConnectedRows(state)
            PanelHint("More connections are coming.")
        }
    }
}

@Composable
private fun AccountRow(state: AccountsPanelState, account: OneLauncherAccounts.Account) {
    val actions = state.actions
    val theme = OneConfigTheme.current
    val hover = rememberHoverState()
    var confirmRemove by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val revealed = hover.hovered || focused
    val busy = actions.refreshingId == account.id || state.pendingId == account.id
    val idle = actions.idle
    val shape = OneConfigTheme.shape(CustomizeKit.RowRadius)
    val kind = if (account.microsoft) "Microsoft" else "Offline"
    val subtitle = when {
        confirmRemove -> "Remove?"
        busy -> actions.busy ?: kind
        account.expired -> "Signed out. Sign in again to play online."
        else -> kind
    }
    val warn = confirmRemove || (account.expired && !busy)
    Row(
        Modifier
            .fillMaxWidth()
            .height(CustomizeKit.RowHeight)
            .graphicsLayer { alpha = if (idle || busy) 1f else theme.disabledAlpha }
            .onFocusChanged { focused = it.hasFocus }
            .controllerAction(ControllerButton.X, "Remove", enabled = idle && !busy && !confirmRemove) { confirmRemove = true }
            .controllerAction(ControllerButton.Y, "Refresh", enabled = idle && !busy && !confirmRemove && account.refreshable) { state.refresh(account) }
            .then(if (account.active) Modifier.focusableOnController() else Modifier)
            .hoverable(hover.interactionSource)
            .selectableRow(
                false,
                { state.switchTo(account) },
                "${account.username}, $kind account" + if (account.active) ", active" else "",
                shape,
                idle && !account.active && !confirmRemove,
                role = A11yRole.Button,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerHead(account.id, HEAD_SIZE, Modifier, shape = headShape(HEAD_SIZE), contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(
                account.username,
                color = theme.textColor,
                style = theme.typography.body.copy(fontWeight = if (account.active) FontWeight.Medium else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text(
                subtitle,
                color = if (warn) SocialDangerText else theme.textColorSecondary,
                style = theme.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        when {
            busy -> Spinner(OneConfigTheme.accent)
            confirmRemove -> {
                RowIcon("check", "Remove ${account.username}", SocialDangerText, idle) {
                    confirmRemove = false
                    state.remove(account)
                }
                RowIcon("x-close", "Keep ${account.username}", theme.textColorSecondary, true) { confirmRemove = false }
            }
            else -> {
                if (account.expired && account.refreshable) {
                    TextButton(text = "Sign in again", onClick = { state.refresh(account) }, size = ControlSize.Compact, enabled = idle)
                } else if (revealed && account.refreshable) {
                    RowIcon("refresh-cw-01", "Refresh ${account.username}", theme.textColorSecondary, idle) { state.refresh(account) }
                }
                if (revealed) {
                    RowIcon("trash-01", "Remove ${account.username}", theme.textColorSecondary, idle) { confirmRemove = true }
                } else if (account.active) {
                    LobbyIcon("check", OneConfigTheme.accent, 18.dp)
                }
            }
        }
    }
}

@Composable
private fun ConnectedRows(state: AccountsPanelState) {
    val online = remember(state.consentVersion) { DiscordLink.available() }
    if (!online) {
        DrillRow("globe-01", "Turn on online services", null, { state.open(AccountsPage.OnlineServices) })
        PanelHint("Linking Discord needs online services.")
        return
    }
    val active = state.actions.accounts?.firstOrNull { it.active }
    if (active != null && !active.microsoft) {
        ServiceRow("Discord", "Discord linking needs a Microsoft account", warn = false, enabled = false) {}
        return
    }
    DiscordRow()
}

@Composable
private fun DiscordRow() {
    LaunchedEffect(ClientPlatform.localPlayerUuid()) { DiscordLink.refresh() }
    val state by DiscordLink.state.collectAsState()
    val link = state.link
    var confirmUnlink by remember { mutableStateOf(false) }
    LaunchedEffect(link) { if (link == null) confirmUnlink = false }
    val name = with(DiscordLink) { link?.label() }
    val signedOut = state.unavailable == DiscordLink.Unavailable.SignedOut
    val subtitle = when {
        signedOut -> "Couldn't sign in to PolyPlus"
        state.waiting -> "Finish in your browser"
        state.unlinking -> "Unlinking…"
        confirmUnlink -> "Unlink?"
        name != null -> name
        !state.loaded -> "Checking…"
        else -> "Not linked"
    }
    ServiceRow("Discord", subtitle, confirmUnlink || signedOut) {
        val theme = OneConfigTheme.current
        when {
            signedOut -> TextButton(text = "Retry", onClick = DiscordLink::refresh, size = ControlSize.Compact)
            state.waiting -> TextButton(text = "Cancel", onClick = DiscordLink::cancelLink, size = ControlSize.Compact)
            state.unlinking -> Spinner(OneConfigTheme.accent)
            confirmUnlink && link != null -> {
                RowIcon("check", "Unlink Discord", SocialDangerText, true) {
                    confirmUnlink = false
                    DiscordLink.unlink()
                }
                RowIcon("x-close", "Keep Discord linked", theme.textColorSecondary, true) { confirmUnlink = false }
            }
            link != null -> TextButton(text = "Unlink", onClick = { confirmUnlink = true }, size = ControlSize.Compact)
            state.loaded -> TextButton(text = "Link", onClick = DiscordLink::startLink, size = ControlSize.Compact, style = ControlStyle.Primary)
            else -> Spinner(theme.textColorSecondary)
        }
    }
    if (link == null && state.loaded && !signedOut) {
        PanelToggle("Join the Polyfrost Discord", state.joinServer, DiscordLink::setJoinServer)
    }
}

@Composable
private fun ServiceRow(title: String, subtitle: String, warn: Boolean, enabled: Boolean = true, trailing: @Composable () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(CustomizeKit.RowHeight)
            .graphicsLayer { alpha = if (enabled) 1f else theme.disabledAlpha }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(HEAD_SIZE), contentAlignment = Alignment.Center) { LobbyIcon("discord", theme.textColor, 20.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            Text(
                subtitle,
                color = if (warn) SocialDangerText else theme.textColorSecondary,
                style = theme.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        trailing()
    }
}

@Composable
private fun MicrosoftPage(state: AccountsPanelState) {
    val actions = state.actions
    val theme = OneConfigTheme.current
    val session = actions.loginSession
    PanelPageColumn {
        when {
            session != null -> {
                PanelHint("We opened the Microsoft sign-in page in your browser. Finish there and you'll come back here.")
                Box(
                    Modifier.fillMaxWidth().card(12.dp).padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SelectionContainer {
                        Text(
                            session.userCode,
                            style = theme.typography.body.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "Copy code",
                        icon = ASSETS + "copy-01.svg",
                        onClick = { copyToClipboard(session.userCode) },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "Open browser",
                        icon = ASSETS + "link-external-01.svg",
                        onClick = { ClientPlatform.openUri(session.verificationUri) },
                        modifier = Modifier.weight(1f),
                        style = ControlStyle.Primary,
                    )
                }
                PanelHint("If the page didn't open, go to this link and enter the code:")
                SelectionContainer {
                    Text(session.verificationUri, Modifier.fillMaxWidth(), color = OneConfigTheme.accent, style = theme.typography.small)
                }
                actions.busy?.let { BusyLine(it) }
            }
            actions.busy != null -> BusyLine(actions.busy ?: "")
            else -> {
                AccountError(actions.error ?: "Sign-in stopped.", actions.errorSteps)
                TextButton(text = "Try again", onClick = state::startLogin, modifier = Modifier.fillMaxWidth(), style = ControlStyle.Primary)
            }
        }
    }
}

@Composable
private fun OfflinePage(state: AccountsPanelState) {
    val actions = state.actions
    var username by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf<String?>(null) }
    val trimmed = username.trim()
    val validChars = trimmed.all { it.isLetterOrDigit() && it.code < 128 || it == '_' }
    val valid = trimmed.length in 3..16 && validChars
    val idle = actions.idle
    val submit = {
        if (valid && idle) {
            submitted = trimmed
            actions.addOffline(trimmed)
        }
    }
    PanelPageColumn {
        PanelHint("Offline accounts can join singleplayer worlds and offline servers.")
        TextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Username",
            leadingIcon = ASSETS + "user-01.svg",
            maxLength = 16,
            enabled = idle,
            invalid = trimmed.isNotEmpty() && !valid,
            onSubmit = submit,
        )
        val error = actions.error
        val hint = when {
            error != null && submitted == trimmed -> error
            trimmed.isNotEmpty() && !validChars -> "Only letters, digits and underscores"
            trimmed.isNotEmpty() && trimmed.length < 3 -> "At least 3 characters"
            else -> null
        }
        if (hint != null) DangerText(hint)
        TextButton(
            text = "Add account",
            icon = ASSETS + "plus.svg",
            onClick = submit,
            modifier = Modifier.fillMaxWidth(),
            style = ControlStyle.Primary,
            enabled = valid && idle,
        )
        actions.busy?.let { BusyLine(it) }
    }
}

@Composable
private fun AccountError(error: String?, steps: List<String>?) {
    if (error == null && steps.isNullOrEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (error != null) DangerText(error)
        steps?.forEach { step -> DangerText("• $step", secondary = true) }
    }
}

@Composable
private fun DangerText(text: String, secondary: Boolean = false) {
    Text(
        text,
        Modifier.fillMaxWidth(),
        color = if (secondary) SocialDangerText.copy(alpha = 0.8f) else SocialDangerText,
        style = OneConfigTheme.current.typography.small,
    )
}

@Composable
private fun BusyLine(text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Spinner(OneConfigTheme.accent)
        Text(text, color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.current.typography.small)
    }
}

@Composable
private fun SkeletonRow() {
    val pulse by Motion.animateInfiniteFloat(
        initial = SKELETON_LOW,
        target = SKELETON_HIGH,
        spec = infiniteRepeatable(tween(SKELETON_MS), RepeatMode.Reverse),
    )
    val fill = OneConfigTheme.current.textColor.copy(alpha = pulse)
    val bar = OneConfigTheme.shape(4.dp)
    Row(
        Modifier.fillMaxWidth().height(CustomizeKit.RowHeight).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(HEAD_SIZE).clip(headShape(HEAD_SIZE)).background(fill))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(120.dp).height(10.dp).clip(bar).background(fill))
            Box(Modifier.width(70.dp).height(8.dp).clip(bar).background(fill))
        }
    }
}

@Composable
private fun Spinner(color: Color, size: Dp = 16.dp) {
    val angle by Motion.animateInfiniteFloat(
        initial = 0f,
        target = 360f,
        spec = infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing)),
    )
    LobbyIcon("loading-02", color, size, Modifier.rotate(angle))
}

@Composable
private fun RowIcon(icon: String, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        icon = "$ASSETS$icon.svg",
        contentDescription = label,
        onClick = onClick,
        enabled = enabled,
        tooltip = null,
        tint = tint,
        frameSize = 28.dp,
        iconSize = 16.dp,
    )
}

private fun copyToClipboard(text: String) {
    runCatching {
        //? if > 1.8.9 {
        Minecraft.getInstance().keyboardHandler.setClipboard(text)
        //?} else {
        /*Screen.setClipboard(text)
        *///?}
    }
}

private val HEAD_SIZE = 28.dp
private val PREVIEW_MAX_WIDTH = 320.dp
private val CARD_RADIUS = 20.dp
private const val PREVIEW_SHARE = 0.38f
private const val SKELETON_ROWS = 2
private const val SKELETON_MS = 700
private const val SKELETON_LOW = 0.06f
private const val SKELETON_HIGH = 0.14f
private const val SPIN_MS = 800
