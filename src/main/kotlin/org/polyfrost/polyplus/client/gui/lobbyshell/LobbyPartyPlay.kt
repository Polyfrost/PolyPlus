@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.lobbyshell

import androidx.compose.foundation.layout.height
import org.polyfrost.oneconfig.api.ui.v1.components.PlayerHead
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.minecraft.client.multiplayer.ServerData
import org.polyfrost.polyplus.client.gui.MutedText
import org.polyfrost.oneconfig.api.ui.v1.a11y.A11y
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.polyplus.client.gui.SocialDangerText
import org.polyfrost.polyplus.client.gui.SocialSuccessText
import org.polyfrost.polyplus.client.gui.SocialText
import org.polyfrost.polyplus.client.gui.lobbysocial.LeaderJoinEffect
import org.polyfrost.polyplus.client.gui.lobbysocial.LocalSocialLook
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyCard
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyCardText
import org.polyfrost.polyplus.client.gui.lobbysocial.PartyDialogs
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialHost
import org.polyfrost.polyplus.client.gui.lobbysocial.SocialSurface
import org.polyfrost.polyplus.client.gui.lobbysocial.joinAddress
import org.polyfrost.polyplus.client.gui.lobbysocial.seedOf
import org.polyfrost.polyplus.client.network.http.responses.PlayDestinationKind
import org.polyfrost.polyplus.client.network.http.responses.PlayReason
import org.polyfrost.polyplus.client.network.http.responses.PlayResponse
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.party.HostJoinRequests
import org.polyfrost.polyplus.client.party.LeaderFollow
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.party.PartyVoice
import org.polyfrost.polyplus.client.party.PlayTogether
import org.polyfrost.polyplus.client.party.ServerCheck
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.utils.ClientPlatform

private val BannerWidth = 640.dp
private const val TICK_MS = 200L
private const val SECOND_MS = 1000L

@Composable
internal fun PlayTogetherHooks(host: SocialHost) {
    DisposableEffect(host) {
        val join: (String, String) -> Unit = { address, name -> host.joinAddress(address, name) }
        PlayTogether.joinServer = join
        onDispose { if (PlayTogether.joinServer === join) PlayTogether.joinServer = null }
    }
    PartyDialogs()
}

@Composable
internal fun LeaderMovedCard(host: SocialHost) {
    val card = LeaderFollow.card ?: return
    val incompatible = card.check == ServerCheck.Result.INCOMPATIBLE
    LeaderJoinEffect(host)
    PartyCard(
        card.leader,
        card.address,
        "${card.name} is playing on",
        PartyCardText.DISMISS,
        LeaderFollow::dismiss,
        if (card.checking) PartyCardText.CHECKING else PartyCardText.JOIN,
        LeaderFollow::requestJoin,
        captionFirst = true,
        acceptEnabled = !incompatible && !card.checking && !card.cleared,
        warning = if (incompatible) PartyCardText.needs(card.needs) else null,
    )
}

@Composable
internal fun leaderHostedWorld(): LeaderFollow.Hosted? {
    val hosted = LeaderFollow.hosted?.takeIf { !it.here } ?: return null
    val party by PartyRepository.party.collectAsState()
    return hosted.takeIf { party != null && !PartyRepository.isLeader(party) }
}

@Composable
internal fun LeaderHostingCard(hosted: LeaderFollow.Hosted) {
    val names by PlayerNamesRepository.names.collectAsState()
    val name = names[hosted.leader] ?: hosted.name
    PartyCard(
        hosted.leader,
        PartyCardText.world(name),
        "$name is hosting",
        PartyCardText.DISMISS,
        LeaderFollow::dismissHosted,
        hosted.status ?: PartyCardText.JOIN,
        LeaderFollow::joinHosted,
        captionFirst = true,
        acceptEnabled = !hosted.busy,
    )
}

@Composable
internal fun HostJoinRequestCard(request: HostJoinRequests.Request) {
    val names by PlayerNamesRepository.names.collectAsState()
    PartyCard(
        request.player,
        names[request.player] ?: request.name,
        PartyCardText.WANTS_TO_JOIN,
        PartyCardText.DENY,
        { HostJoinRequests.deny(request.id) },
        PartyCardText.ALLOW,
        { HostJoinRequests.allow(request.id) },
        acceptEnabled = !request.answering,
        dismissEnabled = !request.answering,
    )
}

@Composable
internal fun rejoinableWorld(): SessionInvite? {
    val sessionId by P2PSessionManager.rejoinSessionId.collectAsState()
    val invites by SessionsRepository.incomingInvites.collectAsState()
    return invites.firstOrNull { it.sessionId == sessionId }
}

@Composable
internal fun RejoinWorldCard(invite: SessionInvite) {
    PartyCard(
        invite.sender,
        PartyCardText.world(PlayerNamesRepository.nameOr(invite.sender)),
        "Still running",
        PartyCardText.DISMISS,
        P2PSessionManager::forgetRejoin,
        "Rejoin",
        P2PSessionManager::rejoin,
    )
}

@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_MS)
            now = System.currentTimeMillis()
        }
    }
    return now
}

internal fun destinationLabel(kind: PlayDestinationKind, name: String): String = if (kind == PlayDestinationKind.World) "$name (world)" else name

@Composable
internal fun PlayTogetherBanner(modifier: Modifier) {
    val current = PlayTogether.active ?: return
    val now = rememberNow()
    val seconds = ((current.startsAt - now + SECOND_MS - 1) / SECOND_MS).coerceAtLeast(0)
    val where = destinationLabel(current.destination.kind, current.destination.name)
    val framed = LocalSocialLook.current.surface == SocialSurface.None
    val shell = modifier.widthIn(max = BannerWidth).fillMaxWidth()
    LaunchedEffect(current.stage) { A11y.announce(bannerTitle(current, seconds, where)) }
    Column((if (framed) shell.card() else shell.panel()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayerHead(seedOf(current.leader), 36.dp, Modifier, headShape(36.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label(bannerTitle(current, seconds, where), 16.sp, weight = FontWeight.Medium)
                bannerDetail(current)?.let { Label(it, 12.sp, color = MutedText) }
            }
            BannerActions(current)
        }
        current.warning?.let { SocialText(it, 12.sp, color = SocialDangerText) }
        if (current.leading) LeaderStatus(current)
    }
}

private fun bannerTitle(current: PlayTogether.Active, seconds: Long, where: String): String = when {
    current.stage == PlayTogether.Stage.DONE -> current.message ?: "Play together on $where"
    current.leading && current.stage == PlayTogether.Stage.COUNTDOWN -> "Play together on $where in $seconds..."
    current.leading -> "Starting $where..."
    current.stage == PlayTogether.Stage.COUNTDOWN || current.stage == PlayTogether.Stage.CHECKING -> "Joining ${current.leaderName} on $where in $seconds..."
    current.stage == PlayTogether.Stage.WAITING -> "Waiting for ${current.leaderName} to open $where..."
    else -> "Joining ${current.leaderName} on $where..."
}

private fun bannerDetail(current: PlayTogether.Active): String? = when {
    current.stage == PlayTogether.Stage.DONE -> null
    current.leading -> "Your party joins when the countdown ends"
    current.stage == PlayTogether.Stage.CHECKING -> "Checking the server..."
    current.check != null -> "Checking the server..."
    else -> null
}

@Composable
private fun BannerActions(current: PlayTogether.Active) {
    when {
        current.stage == PlayTogether.Stage.DONE -> TextButton("Close", Modifier.height(38.dp), PlayTogether::dismiss)
        current.leading -> TextButton("Cancel", Modifier.height(38.dp), PlayTogether::cancel)
        current.stage == PlayTogether.Stage.CONNECTING -> Unit
        else -> TextButton("Not now", Modifier.height(38.dp), PlayTogether::cancel)
    }
}

@Composable
private fun LeaderStatus(play: PlayTogether.Active) {
    val party by PartyRepository.party.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    val current = party ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PartyRepository.others(current).forEach { member ->
            key(member.player) {
                val status = play.responses[member.player]
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlayerHead(seedOf(member.player), 22.dp, Modifier, headShape(22.dp))
                    Column {
                        Label(names[member.player] ?: PlayerNamesRepository.nameOr(member.player), 12.sp, weight = FontWeight.Medium)
                        Label(statusText(status?.first, status?.second), 11.sp, color = statusColor(status?.first))
                    }
                }
            }
        }
    }
}

private fun statusText(response: PlayResponse?, reason: PlayReason?): String = when (response) {
    null -> "Waiting"
    PlayResponse.Joining -> "Joining"
    PlayResponse.Cancelled -> "Not now"
    PlayResponse.Busy -> "In a game"
    PlayResponse.Incompatible -> "Can't join: ${PlayTogether.reasonText(reason)}"
    PlayResponse.Joined -> "Joined"
    PlayResponse.Failed -> "Failed"
    PlayResponse.Unknown -> "Waiting"
}

@Composable
private fun statusColor(response: PlayResponse?) = when (response) {
    PlayResponse.Joined -> SocialSuccessText
    PlayResponse.Incompatible, PlayResponse.Failed -> SocialDangerText
    else -> MutedText
}

@Composable
internal fun canPlayTogether(): Boolean {
    val party by PartyRepository.party.collectAsState()
    return party != null && PlayTogether.canStart()
}

internal fun startPlayTogether(target: Any?) {
    when (target) {
        is ServerData -> PlayTogether.start(PlayTogether.serverDestination(target.ip, target.name))
        is LobbyWorld -> if (target.playable) PlayTogether.start(PlayTogether.worldDestination(target.name), target.id)
    }
}

@Composable
internal fun partySpeakingNames(): Set<String> {
    val voice by PartyVoice.ui.collectAsState()
    val names by PlayerNamesRepository.names.collectAsState()
    return voice.speaking.mapNotNullTo(HashSet()) { id -> if (PartyRepository.isSelf(id)) ClientPlatform.localPlayerName() else names[id] }
}
