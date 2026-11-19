@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.screens.realms

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
//? if > 1.8.9 {
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mojang.realmsclient.RealmsAvailability
import com.mojang.realmsclient.dto.PendingInvite
import com.mojang.realmsclient.dto.RealmsServer
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.LoadState
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.TabItem
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.rememberHoverState
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.gui.CardSurface
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.gui.SocialBorderColor
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.ThemedIcon
import org.polyfrost.polyplus.client.gui.lobbyshell.LobbyIcons
import org.polyfrost.polyplus.client.gui.lobbyshell.PageGrid
import org.polyfrost.polyplus.client.gui.lobbyshell.REALM_ICON
import org.polyfrost.polyplus.client.gui.lobbyshell.RealmEntry
import org.polyfrost.polyplus.client.gui.lobbyshell.RealmHead
import org.polyfrost.polyplus.client.gui.lobbyshell.RealmState
import org.polyfrost.polyplus.client.gui.lobbyshell.RealmStatusBadge
import org.polyfrost.polyplus.client.gui.lobbyshell.UnderlineTabs
import org.polyfrost.polyplus.client.gui.lobbyshell.cardFrame
import org.polyfrost.polyplus.client.gui.lobbyshell.headShape
import org.polyfrost.polyplus.client.gui.rememberFavicon
import org.polyfrost.polyplus.client.social.FriendsRepository
import java.util.Base64
import java.util.UUID

private val CardMinWidth = 280.dp
private val BannerHeight = 92.dp
private val OwnerHead = 40.dp
private val MotdHeight = 34.dp
private val RowHeight = 64.dp
private val RowHead = 40.dp
private val FriendHead = 18.dp
private val RowGap = 6.dp
private const val MAX_FRIEND_HEADS = 3
private const val STRIP_WASH = 0.3f
private const val BANNER_TOP_ALPHA = 0.85f
private const val BANNER_LOW_ALPHA = 0.35f
private const val EXPIRED_IMAGE_ALPHA = 0.45f
private const val MARK_ALPHA = 0.4f
private const val HUE_STEP = 47L
private const val HUE_SHIFT = 48f
private const val FULL_TURN = 360f
private const val USERS_ICON = SOCIAL_ASSETS + "users-01.svg"
private const val CONFIGURE_ICON = SOCIAL_ASSETS + "settings-02.svg"
private const val INVITE_ICON = SOCIAL_ASSETS + "user-plus-01.svg"
private const val INVITES_ICON = SOCIAL_ASSETS + "log-in-04.svg"
private const val OFFLINE_ICON = SOCIAL_ASSETS + "globe-01.svg"

@Composable
internal fun RealmsBody(screen: RealmsView) {
    val availability = screen.availability
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(PageGrid.Gap)) {
        when {
            availability == null -> StateBlock("Checking Realms", loading = true)
            availability.type() != RealmsAvailability.Type.SUCCESS -> Unavailable(screen, availability)
            else -> {
                Toolbar(screen)
                screen.notices.forEach { key(it.id) { NoticeCard(screen, it) } }
                when (screen.tab) {
                    RealmsTab.Realms -> RealmList(screen)
                    RealmsTab.Invites -> InviteList(screen)
                }
            }
        }
    }
    screen.leaving?.let { server ->
        ConfirmDialog(
            title = "Leave ${server.name ?: "this realm"}?",
            text = "You'll need a new invite to join again.",
            confirmLabel = "Leave",
            onConfirm = { screen.leave(server) },
            onDismiss = { screen.leaving = null },
            danger = true,
        )
    }
}

@Composable
internal fun ColumnScope.StateBlock(
    title: String,
    text: String? = null,
    icon: String? = REALM_ICON,
    loading: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().weight(1f).cardFrame(selected = false, hovered = false).padding(24.dp), contentAlignment = Alignment.Center) {
        EmptyState(
            title,
            icon = icon.takeIf { !loading },
            text = text,
            state = if (loading) LoadState.Loading else LoadState.Idle,
            action = action,
        )
    }
}

@Composable
private fun ColumnScope.Unavailable(screen: RealmsView, result: RealmsAvailability.Result) {
    when (result.type()) {
        RealmsAvailability.Type.INCOMPATIBLE_CLIENT -> StateBlock(
            "Realms doesn't support this version",
            "Realms only works on the latest release of Minecraft.",
        ) { TextButton("More info", onClick = screen::openAvailabilityDetails, size = ControlSize.Large, style = ControlStyle.Primary) }
        RealmsAvailability.Type.NEEDS_PARENTAL_CONSENT -> StateBlock(
            "A parent needs to allow Realms",
            "This account needs permission from a parent or guardian before it can use Realms.",
        ) { TextButton("Learn more", onClick = screen::openAvailabilityDetails, size = ControlSize.Large, style = ControlStyle.Primary) }
        RealmsAvailability.Type.AUTHENTICATION_ERROR -> StateBlock(
            "Realms couldn't verify your account",
            "Sign in again, then reopen Realms.",
        )
        else -> StateBlock(
            "Couldn't reach Realms",
            result.exception()?.realmsError?.errorMessage()?.string ?: "Check your connection and try again.",
            OFFLINE_ICON,
        ) { TextButton("Try again", icon = LobbyIcons.REFRESH, onClick = screen::recheck, size = ControlSize.Large, style = ControlStyle.Primary) }
    }
}

@Composable
private fun Toolbar(screen: RealmsView) {
    val tabs = listOf(
        TabItem(RealmsTab.Realms, "Your realms"),
        TabItem(RealmsTab.Invites, "Invites", badge = screen.inviteCount.takeIf { it > 0 }?.toString()),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { UnderlineTabs(tabs, screen.tab, screen::selectTab) }
        IconButton(LobbyIcons.REFRESH, "Refresh", screen::refresh, size = ControlSize.Large)
        if (screen.newsLink != null) {
            TextButton(
                "News",
                onClick = screen::openNews,
                size = ControlSize.Large,
                trailing = if (screen.unreadNews) ({ Badge("New", tone = BadgeTone.Accent) }) else null,
            )
        }
        if (RealmsCompat.JOIN_WITH_CODE) TextButton("Join with code", onClick = screen::joinWithCode, size = ControlSize.Large)
        BuyButton(screen)
    }
}

@Composable
private fun BuyButton(screen: RealmsView) {
    TextButton(
        if (screen.trialAvailable) "Start free trial" else "Buy a realm",
        icon = LobbyIcons.PLUS,
        onClick = screen::buy,
        size = ControlSize.Large,
        style = ControlStyle.Primary,
    )
}

@Composable
private fun NoticeCard(screen: RealmsView, notice: RealmsNotice) {
    Row(
        Modifier
            .fillMaxWidth()
            .cardFrame(selected = false, hovered = false)
            .background(Brush.horizontalGradient(listOf(OneConfigTheme.accent.copy(alpha = STRIP_WASH), Color.Transparent)))
            .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(notice.message, Modifier.weight(1f), style = OneConfigTheme.current.typography.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
        TextButton(notice.label, onClick = { screen.openNotice(notice) }, size = ControlSize.Large)
        if (notice.dismissable) IconButton(SOCIAL_ASSETS + "x-close.svg", "Dismiss", { screen.dismissNotice(notice.id) })
    }
}

@Composable
private fun ColumnScope.RealmList(screen: RealmsView) {
    val servers = screen.servers
    when {
        servers == null && screen.slow -> StateBlock(
            "Realms is taking a while",
            "Check your connection, then try again.",
            OFFLINE_ICON,
        ) { TextButton("Try again", icon = LobbyIcons.REFRESH, onClick = screen::refresh, size = ControlSize.Large, style = ControlStyle.Primary) }
        servers == null -> StateBlock("Loading realms", loading = true)
        servers.isEmpty() -> StateBlock(
            "You're not in any realms yet",
            emptyRealmsText(screen.trialAvailable),
        ) { BuyButton(screen) }
        else -> ScrollContainer(modifier = Modifier.fillMaxWidth().weight(1f)) {
            RealmGrid(screen, servers)
        }
    }
}

@Composable
private fun RealmGrid(screen: RealmsView, servers: List<RealmsServer>) {
    val friends by FriendsRepository.friends.collectAsState()
    @Suppress("UNUSED_EXPRESSION") friends
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = PageGrid.Gap
        val columns = ((maxWidth + gap) / (CardMinWidth + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            servers.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { server -> key(server.id) { RealmCard(screen, server, Modifier.weight(1f)) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun RealmCard(screen: RealmsView, server: RealmsServer, modifier: Modifier) {
    val hover = rememberHoverState()
    val mine = screen.isMine(server)
    val here = screen.onlinePlayers?.playersIn(server.id).orEmpty()
    val friendsHere = here.filter(FriendsRepository::isFriend)
    val entry = realmEntry(server, mine, here.size)
    val setup = server.state == RealmsServer.State.UNINITIALIZED
    Column(modifier.cardFrame(selected = false, hovered = hover.hovered).hoverable(hover.interactionSource)) {
        Banner(server, entry)
        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (setup) "New realm" else entry.name,
                    Modifier.weight(1f),
                    style = OneConfigTheme.current.typography.heading.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false,
                )
                RealmStatusBadge(entry)
            }
            val warning = entry.warning
            Text(
                warning ?: detail(server, entry),
                color = if (warning != null) SocialDangerText else OneConfigTheme.current.mutedText,
                style = OneConfigTheme.current.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Box(Modifier.fillMaxWidth().height(MotdHeight)) {
                Text(cardLine(server, entry, mine, setup), color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            PeopleLine(here.size, friendsHere)
            RealmActions(screen, server, mine, setup)
        }
    }
}

@Composable
private fun Banner(server: RealmsServer, entry: RealmEntry) {
    val source = bannerSource(server)
    val bytes = remember(source) { source?.let { runCatching { Base64.getMimeDecoder().decode(it) }.getOrNull() } }
    val image = rememberFavicon(bytes)
    val dim = entry.state == RealmState.Expired
    Box(Modifier.fillMaxWidth().height(BannerHeight)) {
        if (image != null) {
            Image(
                image,
                null,
                Modifier.fillMaxSize().alpha(if (dim) EXPIRED_IMAGE_ALPHA else 1f),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
            )
        } else {
            val (top, low) = realmHues(server.id)
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(if (dim) EXPIRED_IMAGE_ALPHA else 1f)
                    .background(Brush.linearGradient(listOf(top.copy(alpha = BANNER_TOP_ALPHA), low.copy(alpha = BANNER_LOW_ALPHA)))),
                contentAlignment = Alignment.Center,
            ) {
                ThemedIcon(REALM_ICON, Color.White.copy(alpha = MARK_ALPHA), 30.dp)
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, CardSurface))))
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 6.dp)
                .clip(headShape(OwnerHead))
                .border(1.dp, SocialBorderColor, headShape(OwnerHead)),
        ) { RealmHead(entry, OwnerHead) }
    }
}

@Composable
private fun PeopleLine(online: Int, friends: List<UUID>) {
    Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ThemedIcon(USERS_ICON, OneConfigTheme.current.mutedText, 14.dp)
        Text(
            if (online > 0) "$online online" else "No one online",
            color = OneConfigTheme.current.mutedText,
            style = OneConfigTheme.current.typography.small,
            maxLines = 1,
            softWrap = false,
        )
        if (friends.isNotEmpty()) {
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                friends.take(MAX_FRIEND_HEADS).forEach { PlayerHead(playerId = it, size = FriendHead, shape = headShape(FriendHead)) }
            }
            Text(
                if (friends.size == 1) "1 friend" else "${friends.size} friends",
                color = OneConfigTheme.current.textColor,
                style = OneConfigTheme.current.typography.small.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun RealmActions(screen: RealmsView, server: RealmsServer, mine: Boolean, setup: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val primary = Modifier.weight(1f)
        when {
            setup -> TextButton("Set up", onClick = { screen.setUp(server) }, modifier = primary, size = ControlSize.Large, style = ControlStyle.Primary, fillWidth = true)
            server.expired && mine -> TextButton("Renew", onClick = { screen.renew(server) }, modifier = primary, size = ControlSize.Large, style = ControlStyle.Primary, fillWidth = true)
            else -> TextButton(
                if (mine) "Play" else "Join",
                onClick = { screen.play(server) },
                modifier = primary,
                size = ControlSize.Large,
                style = ControlStyle.Primary,
                enabled = server.playable,
                fillWidth = true,
            )
        }
        when {
            mine && !setup -> {
                if (!server.expired) IconButton(INVITE_ICON, "Invite players", { screen.invite(server) }, size = ControlSize.Large)
                IconButton(CONFIGURE_ICON, "Configure", { screen.configure(server) }, size = ControlSize.Large)
            }
            !mine -> TextButton("Leave", onClick = { screen.leaving = server }, size = ControlSize.Large, style = ControlStyle.Ghost)
        }
    }
}

private fun emptyRealmsText(trial: Boolean): String = when {
    !RealmsCompat.JOIN_WITH_CODE -> if (trial) "Try a realm free, or ask a friend for an invite." else "Start your own realm, or ask a friend for an invite."
    trial -> "Try a realm free, or join a friend's with an invite code."
    else -> "Start your own realm, or join a friend's with an invite code."
}

private fun cardLine(server: RealmsServer, entry: RealmEntry, mine: Boolean, setup: Boolean): String = when {
    setup -> "Set it up to start playing."
    server.expired && mine -> if (server.expiredTrial) "Your trial has ended." else "Your subscription has ended. Renew it to keep playing."
    server.expired -> "This realm has expired."
    else -> entry.motd.ifBlank { "No message set" }
}

private fun detail(server: RealmsServer, entry: RealmEntry): String = listOfNotNull(
    if (entry.mine) "Your realm" else entry.owner.takeIf { it.isNotBlank() }?.let { "By $it" },
    server.gameModeLabel,
    server.activeVersion.takeIf { it.isNotBlank() && !server.isMinigameActive },
).joinToString(" · ")

private fun bannerSource(server: RealmsServer): String? =
    if (server.isMinigameActive) server.minigameImage?.takeIf { it.isNotBlank() }
    else server.slotImage?.takeIf { it.isNotBlank() }

private fun realmHues(id: Long): Pair<Color, Color> {
    val hue = Math.floorMod(id * HUE_STEP, FULL_TURN.toLong()).toFloat()
    return Color.hsv(hue, 0.55f, 0.85f) to Color.hsv((hue + HUE_SHIFT) % FULL_TURN, 0.65f, 0.5f)
}

private fun realmEntry(server: RealmsServer, mine: Boolean, online: Int): RealmEntry {
    val state = when {
        server.expired -> RealmState.Expired
        server.state == RealmsServer.State.OPEN -> RealmState.Open
        server.state == RealmsServer.State.CLOSED -> RealmState.Closed
        else -> RealmState.Unset
    }
    val version = server.activeVersion.takeIf { it.isNotBlank() }
    val warning = when (server.compatibility) {
        RealmsServer.Compatibility.NEEDS_UPGRADE -> version?.let { "Older version, $it" } ?: "Older version"
        RealmsServer.Compatibility.NEEDS_DOWNGRADE -> version?.let { "Newer version, $it" } ?: "Newer version"
        RealmsServer.Compatibility.INCOMPATIBLE, RealmsServer.Compatibility.RELEASE_TYPE_INCOMPATIBLE -> version?.let { "Incompatible, $it" } ?: "Incompatible"
        else -> null
    }.takeIf { server.state != RealmsServer.State.UNINITIALIZED }
    val motd = if (server.isMinigameActive) server.minigameName?.let { "Minigame: $it" }.orEmpty() else server.motd.orEmpty()
    return RealmEntry(
        id = server.id,
        name = server.name?.takeIf { it.isNotBlank() } ?: "Realm",
        motd = motd,
        state = state,
        daysLeft = server.daysLeft,
        owner = server.owner.orEmpty(),
        ownerId = server.ownerUUID,
        mine = mine,
        online = online,
        warning = warning,
        handle = server,
    )
}

@Composable
private fun ColumnScope.InviteList(screen: RealmsView) {
    val invites = screen.invites
    screen.inviteProblem?.let {
        Text(it, color = SocialDangerText, style = OneConfigTheme.current.typography.small)
    }
    when {
        invites == null -> StateBlock("Loading invites", loading = true)
        screen.invitesFailed -> StateBlock(
            "Couldn't load invites",
            "Check your connection and try again.",
            OFFLINE_ICON,
        ) { TextButton("Try again", icon = LobbyIcons.REFRESH, onClick = screen::loadInvites, size = ControlSize.Large, style = ControlStyle.Primary) }
        invites.isEmpty() -> StateBlock(
            "No invites right now",
            "When a friend invites you to their realm, it shows up here.",
            INVITES_ICON,
            action = if (RealmsCompat.JOIN_WITH_CODE) ({ TextButton("Join with code", onClick = screen::joinWithCode, size = ControlSize.Large, style = ControlStyle.Primary) }) else null,
        )
        else -> ScrollContainer(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(RowGap)) {
                invites.forEach { invite -> key(invite.inviteId) { InviteRow(screen, invite) } }
            }
        }
    }
}

@Composable
private fun InviteRow(screen: RealmsView, invite: PendingInvite) {
    val busy = invite.inviteId in screen.answering
    val hover = rememberHoverState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .cardFrame(selected = false, hovered = hover.hovered)
            .hoverable(hover.interactionSource)
            .background(Brush.horizontalGradient(listOf(OneConfigTheme.accent.copy(alpha = STRIP_WASH), Color.Transparent)))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InviteHead(invite, RowHead)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                invite.inviteRealm.ifBlank { "Realm" },
                style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
            Text(
                listOfNotNull(invite.inviteOwner.takeIf { it.isNotBlank() }?.let { "From $it" }, invite.inviteAge)
                    .joinToString(" · "),
                color = OneConfigTheme.current.mutedText,
                style = OneConfigTheme.current.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
            )
        }
        TextButton("Decline", onClick = { screen.answer(invite, false) }, size = ControlSize.Large, style = ControlStyle.Ghost, enabled = !busy)
        TextButton("Accept", onClick = { screen.answer(invite, true) }, size = ControlSize.Large, style = ControlStyle.Primary, enabled = !busy)
    }
}

@Composable
private fun InviteHead(invite: PendingInvite, size: Dp) {
    PlayerHead(playerId = invite.inviteOwnerId, size = size, name = invite.inviteOwner.ifBlank { null }, shape = headShape(size))
}
//?}
