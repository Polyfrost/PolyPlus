package org.polyfrost.polyplus.client.gui.lobbysocial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.polyfrost.polyplus.client.gui.AddFriendDialog
import org.polyfrost.polyplus.client.gui.ConversationReadEffect
import org.polyfrost.polyplus.client.gui.CreateGroupDialog
import org.polyfrost.polyplus.client.gui.InviteToGroupDialog
import org.polyfrost.polyplus.client.gui.conversationTitle
import org.polyfrost.polyplus.client.network.http.responses.Friend
import org.polyfrost.polyplus.client.network.http.responses.GroupKind
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.http.responses.Party
import org.polyfrost.polyplus.client.network.http.responses.SessionInvite
import org.polyfrost.polyplus.client.network.p2p.P2PSessionManager
import org.polyfrost.polyplus.client.party.PartyJoin
import org.polyfrost.polyplus.client.party.PartyRepository
import org.polyfrost.polyplus.client.social.ChatIdentity
import org.polyfrost.polyplus.client.social.FriendsRepository
import org.polyfrost.polyplus.client.social.GroupsRepository
import org.polyfrost.polyplus.client.social.PlayerNamesRepository
import org.polyfrost.polyplus.client.social.SessionsRepository
import org.polyfrost.polyplus.client.social.SocialRefresh
import org.polyfrost.polyplus.client.social.SpecialChatRepository
import org.polyfrost.polyplus.client.social.SpecialChatResponses
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import org.polyfrost.polyplus.client.utils.ClientPlatform

private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val INVITE_PENDING = "pending"
private const val INVITE_ACCEPTED = "accepted"
private const val WORLD_INVITE_MESSAGE = "Invited you to their world"

private const val DAYS_PER_WEEK = 7L

private val SINCE_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault())
private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val WEEKDAY_FORMAT = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

internal fun seedOf(id: String): UUID =
    runCatching { UUID.fromString(id) }.getOrElse { UUID.nameUUIDFromBytes(id.toByteArray(StandardCharsets.UTF_8)) }

private fun relativeTime(sentAt: String): String? = runCatching {
    val elapsed = Duration.between(Instant.parse(sentAt), Instant.now())
    when {
        elapsed.toMinutes() < 1 -> "Now"
        elapsed.toMinutes() < MINUTES_PER_HOUR -> "${elapsed.toMinutes()}m"
        elapsed.toHours() < HOURS_PER_DAY -> "${elapsed.toHours()}h"
        else -> "${elapsed.toDays()}d"
    }
}.getOrNull()

private fun clockTime(sentAt: String): String? = runCatching {
    val sent = Instant.parse(sentAt).atZone(ZoneId.systemDefault())
    val days = ChronoUnit.DAYS.between(sent.toLocalDate(), LocalDate.now(sent.zone))
    when {
        days < 1 -> CLOCK_FORMAT.format(sent)
        days == 1L -> "Yesterday"
        days < DAYS_PER_WEEK -> WEEKDAY_FORMAT.format(sent)
        else -> DATE_FORMAT.format(sent)
    }
}.getOrNull()

private fun sinceLabel(since: String): String? = runCatching { SINCE_FORMAT.format(Instant.parse(since)) }.getOrNull()

private fun buildLabel(invite: SessionInvite): String? {
    val parts = listOfNotNull(invite.minecraftVersion, invite.loader?.replaceFirstChar { it.uppercase() })
    return parts.joinToString(" ").ifEmpty { null }
}

private class LiveConversation(override val id: Int, override val title: String) : ChatConversation {
    @Composable
    override fun lines(): List<ChatLine> {
        val messages by GroupsRepository.messagesFlow(id).collectAsState()
        val names by PlayerNamesRepository.names.collectAsState()
        ConversationReadEffect(id, messages, atLatest = true)
        LaunchedEffect(messages) { PlayerNamesRepository.resolve(messages.map { it.sender }.distinct()) }
        val local = ChatIdentity.localId()
        return messages.takeLast(CHAT_LINES).map { message ->
            val fallback = if (message.sender == local) ClientPlatform.localPlayerName() else PlayerNamesRepository.nameOr(message.sender)
            ChatLine(names[message.sender] ?: fallback, message.content)
        }
    }

    override fun send(text: String) {
        GroupsRepository.sendMessage(id, text)
        if (SpecialChatRepository.status.value?.groupId == id) SpecialChatRepository.refreshTargets()
    }
}

@Composable
internal fun rememberPartyConversation(groupId: Int): ChatConversation {
    LaunchedEffect(groupId) { GroupsRepository.loadMessages(groupId) }
    return remember(groupId) { LiveConversation(groupId, "Party") }
}

internal class LiveSocialSource : SocialSource {
    @Composable
    override fun snapshot(): SocialSnapshot {
        val friends by FriendsRepository.friends.collectAsState()
        val incoming by FriendsRepository.incomingRequests.collectAsState()
        val outgoing by FriendsRepository.outgoingRequests.collectAsState()
        val blocked by FriendsRepository.blocked.collectAsState()
        val groups by GroupsRepository.groups.collectAsState()
        val invites by SessionsRepository.incomingInvites.collectAsState()
        val names by PlayerNamesRepository.names.collectAsState()
        val hosted by P2PSessionManager.currentSessionIdFlow.collectAsState()
        val worldInvited = SocialHosting.invited(hosted)
        val actAs by ChatIdentity.actAs.collectAsState()
        val delegations by ChatIdentity.delegations.collectAsState()
        val special by SpecialChatRepository.status.collectAsState()
        val responded by SpecialChatResponses.responded.collectAsState()
        val scanning by SpecialChatResponses.scanning.collectAsState()
        val partyInvites by PartyRepository.incomingInvites.collectAsState()
        val party by PartyRepository.party.collectAsState()
        LaunchedEffect(friends, incoming, outgoing, blocked, groups, invites) {
            PlayerNamesRepository.resolve(
                friends.map { it.player } + incoming.map { it.player } + outgoing.map { it.player } + blocked.map { it.player } +
                    groups.flatMap { it.members } + invites.map { it.sender },
            )
        }
        return remember(friends, incoming, outgoing, blocked, groups, invites, names, hosted, worldInvited, actAs, delegations, special, responded, scanning, partyInvites, party) {
            val byId = friends.associateBy { it.player }
            val inviters = invites.filter { it.status == INVITE_PENDING }.map { it.sender } + partyInvites.map { it.sender }
            val person = { id: String -> personOf(id, byId[id], party, names, inviters) }
            SocialSnapshot(
                friends = friends.map { person(it.player) }.sortedWith(compareByDescending<SocialPerson> { it.online }.thenBy { it.name.lowercase() }),
                requests = incoming.map { SocialRequest(it.id, person(it.player), incoming = true) } +
                    outgoing.map { SocialRequest(it.id, person(it.player), incoming = false) },
                blocked = blocked.map { person(it.player) }.sortedBy { it.name.lowercase() },
                threads = threadsOf(groups, person, names, responded),
                worlds = invites.filter { it.status == INVITE_PENDING || it.status == INVITE_ACCEPTED }
                    .sortedBy { it.status == INVITE_ACCEPTED }
                    .map { SocialWorld(it.id, person(it.sender), null, it.status == INVITE_ACCEPTED, buildLabel(it)) },
                hosting = hosted?.let { "Your world" },
                actAs = actAs,
                delegations = delegations.map(person),
                specialTarget = special?.isSpecialChatTarget == true,
                scanningReplies = scanning,
                partyInvites = partyInvites.map { SocialPartyInvite(it, person(it.sender)) },
            )
        }
    }

    private fun personOf(id: String, friend: Friend?, party: Party?, names: Map<String, String>, inviters: List<String>): SocialPerson {
        val online = party?.member(id)?.online ?: friend?.online
        return SocialPerson(
            id = id,
            name = names[id] ?: PlayerNamesRepository.nameOr(id),
            seed = seedOf(id),
            realSkin = true,
            online = online == true,
            worldInvited = id in SocialHosting.invited(P2PSessionManager.currentSessionId),
            since = friend?.since?.let(::sinceLabel),
            presenceKnown = online != null,
            pendingInvite = inviters.any { PartyRepository.samePlayer(it, id) },
        )
    }

    private fun threadsOf(
        groups: List<GroupSummary>,
        person: (String) -> SocialPerson,
        names: Map<String, String>,
        responded: Set<Int>,
    ): List<SocialThread> {
        val self = ChatIdentity.selfId()
        return groups.sortedByDescending { it.lastMessage?.sentAt.orEmpty() }.map { group ->
            val last = group.lastMessage
            val isGroup = group.kind != GroupKind.Dm
            val author = last?.sender?.takeIf { isGroup && it != self }?.let { names[it] ?: PlayerNamesRepository.nameOr(it) }
            val mine = last?.sender == self
            SocialThread(
                id = group.id,
                title = conversationTitle(group, self) { names[it] ?: PlayerNamesRepository.nameOr(it) },
                people = group.members.filterNot { it == self }.map(person),
                group = isGroup,
                preview = last?.content?.let { content -> author?.let { "$it: $content" } ?: content },
                brief = last?.content?.let { content ->
                    val text = if (content == WORLD_INVITE_MESSAGE) "Sent a world invite" else content
                    if (mine) "You: $text" else author?.let { "$it: $text" } ?: text
                },
                party = group.kind == GroupKind.Party,
                time = last?.sentAt?.let(::relativeTime),
                clock = last?.sentAt?.let(::clockTime),
                unread = group.unread,
                special = group.special,
                responded = group.id in responded,
            )
        }
    }

    @Composable
    override fun Dialog(dialog: SocialDialog, onDismiss: () -> Unit) {
        val friends by FriendsRepository.friends.collectAsState()
        when (dialog) {
            SocialDialog.AddFriend -> AddFriendDialog(onDismiss)
            SocialDialog.NewGroup -> CreateGroupDialog(
                friends = friends,
                onCreate = { name, members ->
                    onDismiss()
                    GroupsRepository.createGroup(name, members) { result ->
                        result.onSuccess { group -> ClientPlatform.runOnMain { openChat(group.id, name) } }
                    }
                },
                onDismiss = onDismiss,
            )
            is SocialDialog.InviteToGroup -> {
                val groups by GroupsRepository.groups.collectAsState()
                val group = groups.firstOrNull { it.id == dialog.groupId }
                LaunchedEffect(group == null) { if (group == null) onDismiss() }
                group?.let {
                    InviteToGroupDialog(
                        friends = friends.filterNot { friend -> friend.player in it.members },
                        onInvite = { player -> GroupsRepository.addMember(it.id, player) },
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }

    override fun refresh() {
        SocialRefresh.refreshAll()
        ChatIdentity.refreshDelegations()
    }

    override fun accept(request: SocialRequest) {
        FriendsRepository.acceptRequest(request.id)
    }

    override fun dismiss(request: SocialRequest) {
        if (request.incoming) FriendsRepository.declineRequest(request.id) else FriendsRepository.cancelRequest(request.id)
    }

    private fun openChat(id: Int, title: String) {
        GroupsRepository.loadMessages(id)
        SocialChatLink.open(LiveConversation(id, title))
    }

    override fun open(thread: SocialThread) = openChat(thread.id, thread.title)

    override fun message(person: SocialPerson) = openDirect(person.id)

    override fun openDirect(player: String) {
        GroupsRepository.openDirectMessage(player) { result ->
            result.onSuccess { group -> ClientPlatform.runOnMain { openChat(group.id, PlayerNamesRepository.nameOr(player)) } }
        }
    }

    private fun inviteFor(world: SocialWorld): SessionInvite? = SessionsRepository.incomingInvites.value.firstOrNull { it.id == world.id }

    override fun join(world: SocialWorld) {
        val invite = inviteFor(world) ?: return
        PartyJoin.world(world.name ?: "${world.host.name}'s world") { SessionsRepository.accept(invite) }
    }

    override fun decline(world: SocialWorld) {
        inviteFor(world)?.let(SessionsRepository::decline)
    }

    override fun inviteToWorld(person: SocialPerson) {
        SocialHosting.invite(person.id)
    }

    override fun remove(person: SocialPerson) {
        FriendsRepository.removeFriend(person.id)
    }

    override fun block(person: SocialPerson) {
        FriendsRepository.block(person.id)
    }

    override fun unblock(person: SocialPerson) {
        FriendsRepository.unblock(person.id)
    }

    override fun chatAs(owner: String?) = ChatIdentity.switchTo(owner)

    override fun scanReplies() = SpecialChatResponses.refresh(GroupsRepository.groups.value, ChatIdentity.selfId())
}
