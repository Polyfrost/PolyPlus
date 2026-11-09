package org.polyfrost.polyplus.client.party

import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.notifications.v1.Notification
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.PartyApi
import org.polyfrost.polyplus.client.network.http.PartyApiException
import org.polyfrost.polyplus.client.network.http.responses.MemberState
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.PartyInvite
import org.polyfrost.polyplus.client.network.http.responses.PartyInvitePolicy
import org.polyfrost.polyplus.client.network.http.responses.PartyInviteStatus
import org.polyfrost.polyplus.client.network.http.responses.PartyLeftReason
import org.polyfrost.polyplus.client.network.http.responses.PartyMember
import org.polyfrost.polyplus.client.network.http.responses.PartyVoiceMode
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.NotificationDedup
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SocialErrors
import org.polyfrost.polyplus.client.social.SocialOverlay
import org.polyfrost.polyplus.events.WebSocketMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object PartyRepository {
    private val LOGGER = LogManager.getLogger()

    const val MAX_MEMBERS = 8

    private val _party = MutableStateFlow<Party?>(null)
    val party = _party.asStateFlow()

    private val _incomingInvites = MutableStateFlow<List<PartyInvite>>(emptyList())
    val incomingInvites = _incomingInvites.asStateFlow()

    private val _outgoingInvites = MutableStateFlow<List<PartyInvite>>(emptyList())
    val outgoingInvites = _outgoingInvites.asStateFlow()

    private val _confirmLeave = MutableStateFlow<PartyInvite?>(null)
    val confirmLeave = _confirmLeave.asStateFlow()

    private val _left = MutableSharedFlow<PartyLeftReason>(extraBufferCapacity = 4)
    val left = _left.asSharedFlow()

    private val _refreshed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val refreshed = _refreshed.asSharedFlow()

    private val inviteNotices = ConcurrentHashMap<String, Notification>()

    private val partyLock = Any()
    @Volatile private var partyVersion = 0L

    fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.PartyUpdated -> applyParty(packet.party)
                is ClientboundPacket.PartyMemberStateChanged -> applyMemberState(packet.partyId, packet.player, packet.state)
                is ClientboundPacket.PartyLeft -> onLeft(packet.partyId, packet.reason)
                is ClientboundPacket.PartyInviteReceived -> onInviteReceived(packet.invite)
                is ClientboundPacket.PartyInviteUpdated -> onInviteUpdated(packet.inviteId, packet.status)
                else -> Unit
            }
        }
    }

    fun localId(): String = ChatIdentity.localId()

    fun isSelf(player: String): Boolean = samePlayer(player, localId())

    fun samePlayer(a: String, b: String): Boolean {
        if (a.equals(b, ignoreCase = true)) return true
        val left = runCatching { UUID.fromString(a) }.getOrNull() ?: return false
        val right = runCatching { UUID.fromString(b) }.getOrNull() ?: return false
        return left == right
    }

    fun isLeader(party: Party? = _party.value): Boolean = party != null && isSelf(party.leader)

    fun canInvite(party: Party? = _party.value): Boolean = when {
        party == null -> true
        party.members.size >= MAX_MEMBERS -> false
        isLeader(party) -> true
        else -> party.settings.invitePolicy == PartyInvitePolicy.Members
    }

    fun self(party: Party? = _party.value): PartyMember? = party?.members?.firstOrNull { isSelf(it.player) }

    fun others(party: Party? = _party.value): List<PartyMember> = party?.members?.filterNot { isSelf(it.player) }.orEmpty()

    fun refresh() = PolyPlusClient.SCOPE.launch {
        val since = partyVersion
        PartyApi.current()
            .onSuccess { applyParty(it, since) }
            .onFailure { if (missing(it)) applyParty(null, since) else LOGGER.error("Failed to refresh party", it) }
        refreshInvites()
        _refreshed.emit(Unit)
    }

    private suspend fun refreshInvites() {
        PartyApi.incomingInvites()
            .onSuccess { invites -> _incomingInvites.value = invites.filter { it.status == PartyInviteStatus.Pending } }
            .onFailure { if (missing(it)) { _incomingInvites.value = emptyList() } else { LOGGER.error("Failed to refresh incoming party invites", it) } }
        PartyApi.outgoingInvites()
            .onSuccess { invites -> _outgoingInvites.value = invites.filter { it.status == PartyInviteStatus.Pending } }
            .onFailure { if (missing(it)) { _outgoingInvites.value = emptyList() } else { LOGGER.error("Failed to refresh outgoing party invites", it) } }
        PlayerNamesRepository.resolve(_incomingInvites.value.map { it.sender } + _outgoingInvites.value.map { it.recipient })
    }

    private fun missing(error: Throwable): Boolean = error is PartyApiException && error.status == 404

    fun reset() {
        updateParty { null }
        _incomingInvites.value = emptyList()
        _outgoingInvites.value = emptyList()
        _confirmLeave.value = null
        inviteNotices.keys.toList().forEach(::retireNotice)
    }

    private fun updateParty(since: Long? = null, transform: (Party?) -> Party?): Boolean = synchronized(partyLock) {
        if (since != null && since != partyVersion) return false
        val previous = _party.value
        val next = transform(previous)
        if (next != previous) {
            partyVersion++
            _party.value = next
        }
        true
    }

    private fun applyParty(party: Party?, since: Long? = null) {
        val accepted = party?.takeIf { it.members.any { member -> isSelf(member.player) } }
        if (!updateParty(since) { accepted }) return
        accepted?.let { PlayerNamesRepository.resolve(it.members.map(PartyMember::player)) }
    }

    private fun applyMemberState(partyId: String, player: String, state: MemberState) {
        updateParty { current ->
            if (current == null || current.id != partyId) return@updateParty current
            current.copy(members = current.members.map { if (samePlayer(it.player, player)) it.copy(state = state) else it })
        }
    }

    private fun onLeft(partyId: String, reason: PartyLeftReason) {
        if (_party.value?.id != partyId) return
        updateParty { null }
        _left.tryEmit(reason)
        when (reason) {
            PartyLeftReason.Kicked -> SocialErrors.emit("You were removed from the party")
            PartyLeftReason.Disbanded -> SocialErrors.emit("The party was disbanded")
            PartyLeftReason.Expired -> SocialErrors.emit("You left the party while offline")
            else -> Unit
        }
        PolyPlusClient.SCOPE.launch { refreshInvites() }
    }

    private fun onInviteReceived(invite: PartyInvite) {
        _incomingInvites.update { list -> list.filterNot { it.id == invite.id } + invite }
        if (SocialOverlay.isShowing()) return
        if (!NotificationDedup.shouldNotify("party_invite:${invite.id}")) return
        PlayerNamesRepository.resolve(listOf(invite.sender))
        val name = PlayerNamesRepository.nameOr(invite.sender)
        val notice = PartyNotices.send(
            "$name invited you to their party",
            PartyNotices.INVITE_MS,
            "Join party",
            { acceptFromNotice(invite) },
            "Decline",
            { decline(invite) },
        )
        inviteNotices.put(invite.id, notice)?.let(PartyNotices::retire)
    }

    private fun retireNotice(inviteId: String) {
        inviteNotices.remove(inviteId)?.let(PartyNotices::retire)
    }

    private fun acceptFromNotice(invite: PartyInvite) {
        if (_party.value != null) {
            _confirmLeave.value = invite
            PartyNotices.showDialogs()
            return
        }
        acceptNow(invite, leaveCurrent = false, fromNotice = true)
    }

    private fun onInviteUpdated(inviteId: String, status: PartyInviteStatus) {
        if (status == PartyInviteStatus.Pending) return
        _incomingInvites.update { list -> list.filterNot { it.id == inviteId } }
        _outgoingInvites.update { list -> list.filterNot { it.id == inviteId } }
        if (_confirmLeave.value?.id == inviteId) _confirmLeave.value = null
        retireNotice(inviteId)
    }

    private fun report(action: String, fallback: String, error: Throwable) {
        LOGGER.error("Party action '{}' failed", action, error)
        SocialErrors.emit(PartyErrors.message(error, fallback))
    }

    fun create(onDone: (Party) -> Unit = {}) = PolyPlusClient.SCOPE.launch {
        PartyApi.create()
            .onSuccess { applyParty(it); onDone(it) }
            .onFailure { report("create", "Couldn't start a party", it) }
    }

    fun invite(player: String) = PolyPlusClient.SCOPE.launch {
        PartyApi.invite(player)
            .onSuccess { invite ->
                _outgoingInvites.update { list -> list.filterNot { it.id == invite.id } + invite }
                if (_party.value?.id != invite.partyId) refresh()
            }
            .onFailure { report("invite", "Couldn't send party invite", it) }
    }

    fun accept(invite: PartyInvite) {
        if (_party.value != null) {
            _confirmLeave.value = invite
            return
        }
        acceptNow(invite, leaveCurrent = false)
    }

    fun confirmAccept(leaveCurrent: Boolean) {
        val invite = _confirmLeave.value ?: return
        _confirmLeave.value = null
        if (leaveCurrent) acceptNow(invite, leaveCurrent = true)
    }

    private val accepting = ConcurrentHashMap.newKeySet<String>()

    private fun acceptNow(invite: PartyInvite, leaveCurrent: Boolean, fromNotice: Boolean = false) = PolyPlusClient.SCOPE.launch {
        if (!accepting.add(invite.id)) return@launch
        try {
            acceptInvite(invite, leaveCurrent, fromNotice)
        } finally {
            accepting.remove(invite.id)
        }
    }

    private suspend fun acceptInvite(invite: PartyInvite, leaveCurrent: Boolean, fromNotice: Boolean) {
        PartyApi.acceptInvite(invite.id, leaveCurrent)
            .onSuccess { party ->
                _incomingInvites.update { list -> list.filterNot { it.id == invite.id } }
                retireNotice(invite.id)
                applyParty(party)
            }
            .onFailure { error ->
                if (error is PartyApiException && error.code == PartyErrors.ALREADY_IN_PARTY && !leaveCurrent) {
                    _confirmLeave.value = invite
                    if (fromNotice) PartyNotices.showDialogs()
                    refresh()
                    return@onFailure
                }
                if (error is PartyApiException && (error.status == 404 || error.status == 410)) {
                    _incomingInvites.update { list -> list.filterNot { it.id == invite.id } }
                }
                report("accept", "Couldn't join the party", error)
            }
    }

    fun decline(invite: PartyInvite) = PolyPlusClient.SCOPE.launch {
        _incomingInvites.update { list -> list.filterNot { it.id == invite.id } }
        retireNotice(invite.id)
        PartyApi.declineInvite(invite.id).onFailure { error ->
            if (error is PartyApiException && (error.status == 404 || error.status == 410)) return@onFailure
            report("decline", "Couldn't decline the invite", error)
            refreshInvites()
        }
    }

    fun cancel(invite: PartyInvite) = PolyPlusClient.SCOPE.launch {
        _outgoingInvites.update { list -> list.filterNot { it.id == invite.id } }
        PartyApi.cancelInvite(invite.id).onFailure { error ->
            if (error is PartyApiException && error.status == 404) return@onFailure
            report("cancel", "Couldn't cancel the invite", error)
            refreshInvites()
        }
    }

    fun leave() = PolyPlusClient.SCOPE.launch {
        PartyApi.leave()
            .onSuccess {
                updateParty { null }
                _left.emit(PartyLeftReason.Left)
            }
            .onFailure { error ->
                if (error is PartyApiException && error.status == 404) updateParty { null } else report("leave", "Couldn't leave the party", error)
            }
    }

    fun disband() = PolyPlusClient.SCOPE.launch {
        PartyApi.disband()
            .onSuccess {
                updateParty { null }
                _left.emit(PartyLeftReason.Disbanded)
            }
            .onFailure { report("disband", "Couldn't disband the party", it) }
    }

    fun kick(player: String) = PolyPlusClient.SCOPE.launch {
        PartyApi.kick(player)
            .onSuccess { updateParty { it?.copy(members = it.members.filterNot { member -> samePlayer(member.player, player) }) } }
            .onFailure { report("kick", "Couldn't remove that player", it) }
    }

    fun makeLeader(player: String) = PolyPlusClient.SCOPE.launch {
        PartyApi.transferLeader(player)
            .onSuccess { applyParty(it) }
            .onFailure { report("leader", "Couldn't change the leader", it) }
    }

    fun setHardMuted(player: String, muted: Boolean) = PolyPlusClient.SCOPE.launch {
        PartyApi.setHardMuted(player, muted)
            .onSuccess {
                updateParty { it?.copy(members = it.members.map { member -> if (samePlayer(member.player, player)) member.copy(voiceHardMuted = muted) else member }) }
            }
            .onFailure { report("hard mute", "Couldn't change their voice", it) }
    }

    fun setVoiceMode(mode: PartyVoiceMode) = PolyPlusClient.SCOPE.launch {
        PartyApi.updateSettings(voiceMode = mode)
            .onSuccess { applyParty(it) }
            .onFailure { report("settings", "Couldn't change party settings", it) }
    }

    fun setInvitePolicy(policy: PartyInvitePolicy) = PolyPlusClient.SCOPE.launch {
        PartyApi.updateSettings(invitePolicy = policy)
            .onSuccess { applyParty(it) }
            .onFailure { report("settings", "Couldn't change party settings", it) }
    }
}
