@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
//? if > 1.8.9 {
import com.mojang.realmsclient.RealmsMainScreen
import com.mojang.realmsclient.client.RealmsClient
import com.mojang.realmsclient.dto.RealmsServer
import com.mojang.realmsclient.exception.RealmsServiceException
import com.mojang.realmsclient.gui.RealmsDataFetcher
import net.minecraft.client.Minecraft
import org.polyfrost.polyplus.mixin.client.access.MinecraftRealmsAccessor
import org.polyfrost.polyplus.mixin.client.access.RealmsAvailabilityAccessor
//?}
//? if >= 1.21.5
import org.polyfrost.polyplus.mixin.client.access.RealmsClientAccessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.ControlSize
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MenuAction
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.PolyPlusRecentServers
import org.polyfrost.polyplus.client.cosmetics.MojangAuth
import org.polyfrost.polyplus.client.gui.SOCIAL_ASSETS
import org.polyfrost.polyplus.client.lobby.LOBBY_LOGGER
import org.polyfrost.polyplus.client.utils.ClientPlatform
import java.util.UUID

internal enum class RealmState { Open, Closed, Expired, Unset }

internal class RealmEntry(
    val id: Long,
    val name: String,
    val motd: String,
    val state: RealmState,
    val daysLeft: Int,
    val owner: String,
    val ownerId: UUID?,
    val mine: Boolean,
    val online: Int?,
    val warning: String?,
    internal val handle: Any,
) {
    val key: String get() = PolyPlusRecentServers.realmKey(id)

    val joinable: Boolean get() = state == RealmState.Open || state == RealmState.Closed

    val expiring: Boolean get() = mine && state != RealmState.Expired && daysLeft in 0..EXPIRY_WARNING_DAYS

    val status: String
        get() = when {
            state == RealmState.Expired -> "Expired"
            state == RealmState.Unset -> "Not set up"
            expiring -> if (daysLeft == 1) "1 day left" else "$daysLeft days left"
            state == RealmState.Closed -> "Closed"
            else -> "Open"
        }

    val tone: BadgeTone
        get() = when {
            state == RealmState.Expired -> BadgeTone.Danger
            expiring -> BadgeTone.Warning
            state == RealmState.Open -> BadgeTone.Success
            else -> BadgeTone.Neutral
        }

    val detail: String
        get() = listOfNotNull(
            if (mine) "Your realm" else owner.takeIf { it.isNotBlank() }?.let { "By $it" },
            online?.takeIf { it > 0 }?.let { "$it online" },
        ).joinToString(" · ")

    private companion object {
        const val EXPIRY_WARNING_DAYS = 7
    }
}

private class RealmsFetch(val realms: List<RealmEntry>, val invites: Int)

private class RealmsFailure(val status: Int, cause: Throwable?) : Exception(cause)

internal object LobbyRealms {
    private const val CACHE_MILLIS = 60_000L
    private const val BUSY_BACKOFF_MILLIS = 30_000L
    private const val FAILURE_BACKOFF_MILLIS = 10_000L
    private const val BUSY = 429
    private const val UNAUTHORIZED = 401
    private const val FORBIDDEN = 403
    private const val JWT_DOTS = 2

    var realms by mutableStateOf<List<RealmEntry>>(emptyList())
        private set
    var invites by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set
    var problem by mutableStateOf<String?>(null)
        private set
    var refusedSignIn by mutableStateOf(false)
        private set
    var collapsed by mutableStateOf(PolyPlusRecentServers.realmsCollapsed())
        private set

    private var job: Job? = null
    private var generation = 0
    private var fetchedFor: UUID? = null
    private var fetchedAt = 0L
    private var retryAt = 0L
    private var refused: UUID? = null

    val visible: Boolean
        get() = PolyPlusMainMenuConfig.realmsSupported() && !PolyPlusMainMenuConfig.hideMainMenuRealms && signedIn()

    fun available(): Boolean = visible

    fun shown(): Boolean = visible

    fun find(id: Long): RealmEntry? = realms.firstOrNull { it.id == id }

    fun toggleCollapsed() {
        collapsed = !collapsed
        PolyPlusRecentServers.setRealmsCollapsed(collapsed)
    }

    fun refresh(force: Boolean = false) {
        val account = account()
        if (account != fetchedFor) {
            clear()
            retryAt = 0L
        }
        if (!visible) {
            clear()
            return
        }
        if (refused == account && !force) return
        val now = System.currentTimeMillis()
        if (job?.isActive == true || (!force && now < retryAt)) return
        if (!force && loaded && now - fetchedAt < CACHE_MILLIS) return
        val ticket = ++generation
        loading = true
        job = PolyPlusClient.SCOPE.launch(Dispatchers.IO) {
            val result = runCatching { fetchRealms(account) }
            ClientPlatform.runOnMain { finish(ticket, account, result) }
        }
    }

    fun onAccountSwitched() = ClientPlatform.runOnMain {
        generation++
        job?.cancel()
        job = null
        loading = false
        refused = null
        retryAt = 0L
        clear()
        resetVanillaRealms()
        refresh(force = true)
    }

    private fun finish(ticket: Int, account: UUID, result: Result<RealmsFetch>) {
        if (ticket != generation) return
        loading = false
        if (account != account()) return
        val now = System.currentTimeMillis()
        fetchedFor = account
        result.onSuccess { fetched ->
            realms = fetched.realms
            invites = fetched.invites
            problem = null
            refusedSignIn = false
            refused = null
            loaded = true
            fetchedAt = now
            fetched.realms.forEach { PolyPlusRecentServers.renameRealmPin(it.id, it.name) }
        }.onFailure { error ->
            val status = (error as? RealmsFailure)?.status ?: 0
            when (status) {
                UNAUTHORIZED, FORBIDDEN -> {
                    refused = account
                    realms = emptyList()
                    invites = 0
                    refusedSignIn = true
                    problem = "Realms didn't accept this account"
                    LOBBY_LOGGER.info("Realms refused the current account ({})", status)
                }
                BUSY -> {
                    retryAt = now + BUSY_BACKOFF_MILLIS
                    problem = "Realms is busy, try again soon"
                }
                else -> {
                    retryAt = now + FAILURE_BACKOFF_MILLIS
                    problem = "Couldn't load realms"
                    LOBBY_LOGGER.warn("Unable to list realms", error.cause ?: error)
                }
            }
        }
    }

    private fun clear() {
        realms = emptyList()
        invites = 0
        problem = null
        refusedSignIn = false
        loaded = false
        fetchedAt = 0L
        fetchedFor = account()
    }

    private fun account(): UUID = ClientPlatform.localPlayerUuid()

    private fun signedIn(): Boolean = MojangAuth.accessToken()?.let { token -> token.count { it == '.' } >= JWT_DOTS } ?: false

    fun play(realm: RealmEntry, parent: Screen) {
        playRealm(realm.handle, parent)
    }
}

//? if >= 1.21.5 {
private fun realmsClient(): RealmsClient = RealmsClient.getOrCreate()
//?} elif > 1.8.9 {
/*private fun realmsClient(): RealmsClient = RealmsClient.create()
*///?}

//? if > 1.8.9 {
private fun fetchRealms(me: UUID): RealmsFetch {
    val client = realmsClient()
    val servers = try {
        client.listRealms().servers.orEmpty()
    } catch (error: RealmsServiceException) {
        throw RealmsFailure(error.realmsError.errorCode(), error)
    }
    val invites = runCatching { client.pendingInvitesCount() }.getOrDefault(0)
    val realms = servers
        .filter { it.parentRealmId <= 0L }
        .map { realmEntry(it, me) }
        .sortedWith(compareByDescending<RealmEntry> { it.state == RealmState.Open }.thenBy { it.state == RealmState.Expired }.thenBy { it.name.lowercase() })
    return RealmsFetch(realms, invites)
}

private fun realmEntry(server: RealmsServer, me: UUID): RealmEntry {
    val state = when {
        server.expired -> RealmState.Expired
        server.state == RealmsServer.State.OPEN -> RealmState.Open
        server.state == RealmsServer.State.CLOSED -> RealmState.Closed
        else -> RealmState.Unset
    }
    val online = server.players?.count { it.online }
    val compatibility = server.compatibility
    val version = server.activeVersion?.takeIf { it.isNotBlank() }
    val warning = when (compatibility) {
        RealmsServer.Compatibility.NEEDS_UPGRADE -> version?.let { "Older version, $it" } ?: "Older version"
        RealmsServer.Compatibility.NEEDS_DOWNGRADE -> version?.let { "Newer version, $it" } ?: "Newer version"
        RealmsServer.Compatibility.INCOMPATIBLE, RealmsServer.Compatibility.RELEASE_TYPE_INCOMPATIBLE -> version?.let { "Incompatible, $it" } ?: "Incompatible"
        else -> null
    }
    val motd = if (server.isMinigameActive) server.minigameName?.let { "Minigame: $it" }.orEmpty() else server.motd.orEmpty()
    return RealmEntry(
        id = server.id,
        name = server.name?.takeIf { it.isNotBlank() } ?: "Realm",
        motd = motd,
        state = state,
        daysLeft = server.daysLeft,
        owner = server.owner.orEmpty(),
        ownerId = server.ownerUUID,
        mine = server.ownerUUID == me,
        online = online,
        warning = warning,
        handle = server,
    )
}

private fun playRealm(handle: Any, parent: Screen) {
    val server = handle as? RealmsServer ?: return
    RealmsMainScreen.play(server, parent)
}

private fun resetVanillaRealms() {
    runCatching {
        //? if >= 1.21.5
        RealmsClientAccessor.`polyplus$setInstance`(null)
        RealmsAvailabilityAccessor.`polyplus$setFuture`(null)
        (Minecraft.getInstance() as MinecraftRealmsAccessor).`polyplus$setRealmsDataFetcher`(RealmsDataFetcher(realmsClient()))
    }.onFailure { LOBBY_LOGGER.warn("Unable to reset Realms for the new account", it) }
}
//?} else {
/*private fun fetchRealms(me: UUID): RealmsFetch = RealmsFetch(emptyList(), 0)

private fun playRealm(handle: Any, parent: Screen) = Unit

private fun resetVanillaRealms() = Unit
*///?}

internal const val REALM_ICON = SOCIAL_ASSETS + "minecraft-block.svg"

@Composable
internal fun RealmHead(realm: RealmEntry, size: Dp) {
    PlayerHead(playerId = realm.ownerId, size = size, modifier = Modifier, name = realm.owner.ifBlank { null }, shape = headShape(size))
}

@Composable
internal fun RealmStatusBadge(realm: RealmEntry, modifier: Modifier = Modifier) {
    Badge(realm.status, modifier.height(18.dp), tone = realm.tone, dot = realm.state == RealmState.Open && !realm.expiring)
}

internal fun realmMenuItems(lobby: LobbyShell, realm: RealmEntry, includeJoin: Boolean = true): List<MenuAction> {
    val servers = lobby.servers
    val pinned = servers.isRealmPinned(realm.id)
    return listOfNotNull(
        MenuAction(if (realm.joinable) "Join" else "Open in Realms", { lobby.playRealm(realm) }, icon = SOCIAL_ASSETS + "log-in-04.svg").takeIf { includeJoin },
        MenuAction(
            if (pinned) "Unpin from home" else "Pin to home",
            { servers.setRealmPinned(realm, !pinned) },
            icon = if (pinned) PINNED_ICON else PIN_ICON,
            enabled = pinned || servers.canPin(),
        ),
        MenuAction("Manage realms", lobby::openRealms, icon = REALM_ICON),
    )
}

internal fun realmsGroupMenuItems(lobby: LobbyShell): List<MenuAction> = listOf(
    MenuAction("Manage realms", lobby::openRealms, icon = REALM_ICON, badge = LobbyRealms.invites.takeIf { it > 0 }?.toString()),
    MenuAction("Create a realm", lobby::openRealms, icon = LobbyIcons.PLUS),
    MenuAction("Refresh", { LobbyRealms.refresh(force = true) }, icon = LobbyIcons.REFRESH),
)

@Composable
internal fun RealmsGroupBody(lobby: LobbyShell, modifier: Modifier = Modifier) {
    if (LobbyRealms.collapsed || LobbyRealms.realms.isNotEmpty()) return
    val problem = LobbyRealms.problem
    val busy = problem == null && (LobbyRealms.loading || !LobbyRealms.loaded)
    val title = when {
        problem != null -> problem
        busy -> "Loading realms..."
        else -> "You're not in any realms yet"
    }
    val text = when {
        problem != null && LobbyRealms.refusedSignIn -> "Try signing in again, or open Realms to check."
        problem != null -> "Check your connection and try again."
        busy -> ""
        else -> "Create one, or ask a friend to invite you."
    }
    Row(
        modifier.fillMaxWidth().cardFrame(selected = false, hovered = false).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OneConfigTheme.current.typography.body.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
            if (text.isNotEmpty()) {
                Text(text, color = OneConfigTheme.current.mutedText, style = OneConfigTheme.current.typography.small, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            problem != null -> {
                TextButton(text = "Try again", onClick = { LobbyRealms.refresh(force = true) }, size = ControlSize.Large, style = ControlStyle.Secondary)
                TextButton(text = "Open Realms", onClick = lobby::openRealms, size = ControlSize.Large, style = ControlStyle.Secondary)
            }
            !busy -> {
                TextButton(text = "Manage realms", onClick = lobby::openRealms, size = ControlSize.Large, style = ControlStyle.Secondary)
                TextButton(text = "Create a realm", icon = LobbyIcons.PLUS, onClick = lobby::openRealms, size = ControlSize.Large, style = ControlStyle.Primary)
            }
        }
    }
}
