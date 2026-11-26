@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11yRole
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.ItemSelectionStyle
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.SelectionDefaults
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialSuccessColor
import org.polyfrost.polyplus.client.gui.lobbysocial.LiveSocialSource
import org.polyfrost.polyplus.client.gui.vanillamenu.LobbyElementPanel
import org.polyfrost.polyplus.client.gui.vanillamenu.LobbyHudMenu
import org.polyfrost.polyplus.client.lobby.LobbyAvatar
import org.polyfrost.polyplus.client.lobby.LobbyPoses
import org.polyfrost.polyplus.client.lobby.LobbySceneStore
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.FriendsRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import kotlin.math.roundToInt

/**
 * Right-click on a character in the lobby: a small menu (profile, hide username, hide player, edit animation),
 * the animation panel and the profile popup. Works on your own character and on other players.
 */
internal object LobbyPlayerMenu {
    /** Where the small menu sits, in pixels of the lobby, or null when it is closed. */
    var at by mutableStateOf<Offset?>(null)
    var posesOpen by mutableStateOf(false)

    /** Whose character the menu and panel edit: a player's uuid, or null for your own. */
    var targetUuid by mutableStateOf<String?>(null)
    var targetName by mutableStateOf("")
    var hideName by mutableStateOf(LobbySceneStore.hideName())
    var pose by mutableStateOf(LobbySceneStore.pose())

    /** The player whose profile popup is open (uuid to name), or null. */
    var profile by mutableStateOf<Pair<String, String>?>(null)

    fun open(x: Float, y: Float, avatar: LobbyAvatar? = null) {
        if (avatar == null || avatar.isLocal) select(null, "") else select(avatar.uuid.toString(), avatar.displayName)
        at = Offset(x, y)
    }

    fun close() {
        at = null
    }

    /** Points the menu and the animation panel at a character (null = your own). */
    fun select(uuid: String?, name: String) {
        if (posesOpen && uuid != targetUuid) LobbyPoses.preview = null
        targetUuid = uuid
        targetName = name
        hideName = if (uuid == null) LobbySceneStore.hideName() else LobbySceneStore.nameHidden(uuid)
        pose = if (uuid == null) LobbySceneStore.pose() else LobbySceneStore.poseOf(uuid)
    }

    /** Opens the animation panel for a character, from the Characters page. */
    fun edit(uuid: String?, name: String) {
        select(uuid, name)
        openPoses()
    }

    /** Opens the animation panel and closes every other side panel, so only one shows at a time. */
    fun openPoses() {
        LobbyElementPanel.close()
        LobbyHudMenu.close()
        posesOpen = true
    }

    fun closePoses() {
        posesOpen = false
        LobbyPoses.preview = null
        LobbyPoses.previewTarget = null
    }

    fun toggleName() {
        hideName = !hideName
        val uuid = targetUuid
        if (uuid == null) LobbySceneStore.setHideName(hideName) else LobbySceneStore.setNameHidden(uuid, hideName)
    }

    /** Hides the selected character from the lobby; it comes back from Customize your lobby > Characters. */
    fun hidePlayer() {
        val uuid = targetUuid
        if (uuid == null) LobbySceneStore.setHideSelf(true) else LobbySceneStore.setPlayerHidden(uuid, targetName, true)
        if (posesOpen) closePoses()
    }

    fun pick(id: String) {
        pose = id
        val uuid = targetUuid
        if (uuid == null) LobbySceneStore.setPose(id) else LobbySceneStore.setPoseOf(uuid, id)
    }

    fun openProfile() {
        val uuid = targetUuid ?: return
        profile = uuid to targetName
    }

    fun closeProfile() {
        profile = null
    }
}

internal val POPUP_MENU_WIDTH = 190.dp
private val MENU_ROW = 34.dp
private val PROFILE_WIDTH = 300.dp
private val PROFILE_HEAD = 64.dp

@Composable
internal fun BoxScope.LobbyPlayerMenuHost() {
    val at = LobbyPlayerMenu.at
    if (at != null) {
        // a click anywhere else closes the small menu
        ClickCatcher(LobbyPlayerMenu::close)
        Column(
            Modifier
                .align(Alignment.TopStart)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(0, 0) {
                        val x = at.x.roundToInt().coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                        val y = at.y.roundToInt().coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0))
                        placeable.place(x, y)
                    }
                }
                .width(POPUP_MENU_WIDTH)
                .panel(12.dp)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val other = LobbyPlayerMenu.targetUuid != null
            if (other) {
                MenuRow("Profile") {
                    LobbyPlayerMenu.close()
                    LobbyPlayerMenu.openProfile()
                }
            }
            MenuRow(if (LobbyPlayerMenu.hideName) "Show username" else "Hide username") {
                LobbyPlayerMenu.toggleName()
                LobbyPlayerMenu.close()
            }
            MenuRow("Hide player") {
                LobbyPlayerMenu.close()
                LobbyPlayerMenu.hidePlayer()
            }
            MenuRow("Edit animation", LobbyPoses.name(LobbyPlayerMenu.pose)) {
                LobbyPlayerMenu.close()
                LobbyPlayerMenu.openPoses()
            }
        }
    }
    val profile = LobbyPlayerMenu.profile
    if (profile != null) {
        ClickCatcher(LobbyPlayerMenu::closeProfile)
        ProfileDialog(profile.first, profile.second, Modifier.align(Alignment.Center))
    }
}

/** Fills the lobby and eats the next press, so a click outside a popup only closes it. */
@Composable
private fun ClickCatcher(onPress: () -> Unit) {
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press) {
                        event.changes.forEach { it.consume() }
                        onPress()
                    }
                }
            }
        },
    )
}

private fun sameId(a: String, b: String): Boolean = a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)

private fun sinceLabel(since: String): String? = runCatching {
    DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.parse(since))
}.getOrNull()

/** The profile popup in the middle of the lobby: head, name, online state, friends since, Message and Invite to party. */
@Composable
private fun ProfileDialog(uuid: String, name: String, modifier: Modifier) {
    val theme = OneConfigTheme.current
    val friends by FriendsRepository.friends.collectAsState()
    val friend = friends.firstOrNull { sameId(it.player, uuid) }
    val online = friend?.online == true
    val playerId = remember(uuid) { seedOf(uuid) }
    Column(
        modifier
            .width(PROFILE_WIDTH)
            .popup()
            // clicks inside the popup must not reach the scrim behind it
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) event.changes.forEach { it.consume() }
                    }
                }
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(PROFILE_HEAD)) {
                PlayerHead(playerId = playerId, size = PROFILE_HEAD, modifier = Modifier, shape = headShape(PROFILE_HEAD), contentDescription = null)
                if (online) {
                    val dot = 16.dp
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(dot / 3, dot / 3)
                            .size(dot)
                            .clip(CircleShape)
                            .background(SocialSuccessColor)
                            .border(2.dp, theme.componentBackground, CircleShape),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, color = theme.textColor, style = theme.typography.body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
                Text(if (online) "Online" else "Offline", color = if (online) SocialSuccessColor else theme.textColorSecondary, style = theme.typography.small, maxLines = 1, softWrap = false)
            }
            IconButton(icon = SOCIAL_ASSETS + "dots-vertical.svg", contentDescription = "More", onClick = {
                LobbyPlayerMenu.closeProfile()
                LobbyPlayerMenu.select(uuid, name)
                LobbyPlayerMenu.openPoses()
            }, tooltip = null, frameSize = 28.dp, iconSize = 16.dp)
            IconButton(icon = SOCIAL_ASSETS + "x-close.svg", contentDescription = "Close", onClick = LobbyPlayerMenu::closeProfile, tooltip = null, frameSize = 28.dp, iconSize = 16.dp)
        }
        val since = friend?.since?.let(::sinceLabel)
        if (since != null) Text("Friends since $since", color = theme.textColorSecondary, style = theme.typography.small)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "Message",
                icon = SOCIAL_ASSETS + "message-chat-circle.svg",
                onClick = {
                    LobbyPlayerMenu.closeProfile()
                    LiveSocialSource().openDirect(uuid)
                },
                modifier = Modifier.fillMaxWidth(),
                style = ControlStyle.Primary,
                size = ControlSize.Large,
            )
            TextButton(
                text = "Invite to party",
                icon = SOCIAL_ASSETS + "user-plus-01.svg",
                onClick = {
                    LobbyPlayerMenu.closeProfile()
                    PartyRepository.invite(uuid)
                },
                modifier = Modifier.fillMaxWidth(),
                style = ControlStyle.Secondary,
                size = ControlSize.Large,
            )
        }
    }
}

/** The character panel; composed after the Play card so it draws on top of it. */
@Composable
internal fun LobbyPosePanelHost() {
    PosePanel()
}

@Composable
internal fun MenuRow(label: String, value: String? = null, onClick: () -> Unit) {
    val theme = OneConfigTheme.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(MENU_ROW)
            .selectableRow(false, onClick, label, OneConfigTheme.shape(8.dp), true, role = A11yRole.Button)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), color = theme.textColor, style = theme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        if (value != null) Text(value, color = theme.textColorSecondary, style = theme.typography.small, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun PosePanel() {
    val open = LobbyPlayerMenu.posesOpen
    DisposableEffect(Unit) {
        onDispose {
            LobbyPoses.preview = null
            LobbyPoses.previewTarget = null
        }
    }
    val title = if (LobbyPlayerMenu.targetUuid == null) "Your character" else LobbyPlayerMenu.targetName
    DockedPanel(open, title, LobbyPlayerMenu::closePoses) {
        PanelPageColumn {
            PanelSection(null, divider = false) {
                PanelToggle("Hide username", LobbyPlayerMenu.hideName, { LobbyPlayerMenu.toggleName() })
            }
            PanelSection("Animation") {
                PanelHint("Hover one to preview it on the character, click to keep it.")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    LobbyPoses.all.forEach { pose -> PoseRow(pose) }
                }
            }
        }
    }
}

@Composable
private fun PoseRow(pose: LobbyPoses.Pose) {
    val theme = OneConfigTheme.current
    val on = LobbyPlayerMenu.pose == pose.id
    Row(
        Modifier
            .fillMaxWidth()
            .height(CustomizeKit.RowHeight - 6.dp)
            .pointerInput(pose.id) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> {
                                LobbyPoses.previewTarget = LobbyPlayerMenu.targetUuid
                                LobbyPoses.preview = pose.id
                            }
                            PointerEventType.Exit -> if (LobbyPoses.preview == pose.id) LobbyPoses.preview = null
                            else -> Unit
                        }
                    }
                }
            }
            .selectableRow(on, { LobbyPlayerMenu.pick(pose.id) }, pose.name, OneConfigTheme.shape(CustomizeKit.RowRadius), true, ItemSelectionStyle.Accent, A11yRole.RadioButton)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pose.name,
            Modifier.weight(1f),
            color = SelectionDefaults.itemContentColor(on, ItemSelectionStyle.Accent),
            style = theme.typography.body.copy(fontWeight = FontWeight.Medium.takeIf { on } ?: FontWeight.Normal),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
