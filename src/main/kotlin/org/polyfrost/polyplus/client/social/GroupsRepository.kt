package org.polyfrost.polyplus.client.social

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.GroupsApi
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupMessageSessionInvite
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import org.polyfrost.polyplus.client.network.websocket.ClientboundPacket
import org.polyfrost.polyplus.events.WebSocketMessage
import org.polyfrost.polyplus.utils.EarlyInitializable

object GroupsRepository : EarlyInitializable {
    private val LOGGER = LogManager.getLogger()

    private val _groups = MutableStateFlow<List<GroupSummary>>(emptyList())
    val groups = _groups.asStateFlow()

    private val messagesByGroup = ConcurrentHashMap<Int, MutableStateFlow<List<GroupMessage>>>()

    private const val PENDING_ID_BASE = Long.MAX_VALUE - 1_000_000L
    private val pendingIdCounter = AtomicLong(0)

    fun isPending(messageId: Long): Boolean = messageId >= PENDING_ID_BASE

    override fun earlyInitialize() {
        eventHandler<WebSocketMessage> { event ->
            when (val packet = event.packet) {
                is ClientboundPacket.GroupMessageReceived -> onMessageReceived(packet)
                is ClientboundPacket.GroupMessageEdited -> onMessageEdited(packet)
                is ClientboundPacket.GroupMessageDeleted -> onMessageDeleted(packet)
                else -> Unit
            }
        }.register()
    }

    /** Flow of loaded messages for a group. Empty until [loadMessages] has been called at least once. */
    fun messagesFlow(groupId: Int): StateFlow<List<GroupMessage>> =
        messagesByGroup.getOrPut(groupId) { MutableStateFlow(emptyList()) }.asStateFlow()

    fun refreshGroups() = PolyPlusClient.SCOPE.launch {
        GroupsApi.list()
            .onSuccess { _groups.value = it }
            .onFailure { LOGGER.error("Failed to refresh groups", it) }
    }

    fun openDirectMessage(player: String, onResult: (Result<GroupSummary>) -> Unit = {}) = PolyPlusClient.SCOPE.launch {
        val result = GroupsApi.openDirectMessage(player)
        result.onSuccess { summary -> upsertGroup(summary) }
            .onFailure {
                LOGGER.error("Failed to open DM with $player", it)
                SocialErrors.emit("Couldn't open that conversation", it)
            }
        onResult(result)
    }

    fun createGroup(name: String, members: List<String>, onResult: (Result<GroupSummary>) -> Unit = {}) = PolyPlusClient.SCOPE.launch {
        val result = GroupsApi.createGroup(name, members)
        result.onSuccess { summary -> upsertGroup(summary) }
            .onFailure {
                LOGGER.error("Failed to create group $name", it)
                SocialErrors.emit("Couldn't create group", it)
            }
        onResult(result)
    }

    fun addMember(groupId: Int, player: String) = PolyPlusClient.SCOPE.launch {
        GroupsApi.addMember(groupId, player).onSuccess { refreshGroups() }
            .onFailure {
                LOGGER.error("Failed to add $player to group $groupId", it)
                SocialErrors.emit("Couldn't add member", it)
            }
    }

    fun removeMember(groupId: Int, player: String) = PolyPlusClient.SCOPE.launch {
        GroupsApi.removeMember(groupId, player).onSuccess { refreshGroups() }
            .onFailure {
                LOGGER.error("Failed to remove $player from group $groupId", it)
                SocialErrors.emit("Couldn't remove member", it)
            }
    }

    fun loadMessages(groupId: Int, before: Long? = null, limit: Long? = null) = PolyPlusClient.SCOPE.launch {
        GroupsApi.messages(groupId, before, limit)
            .onSuccess { page ->
                val flow = messagesByGroup.getOrPut(groupId) { MutableStateFlow(emptyList()) }
                flow.value = if (before == null) {
                    page.sortedBy { it.id }
                } else {
                    (page + flow.value).distinctBy { it.id }.sortedBy { it.id }
                }
            }
            .onFailure { LOGGER.error("Failed to load messages for group $groupId", it) }
    }

    fun sendMessage(groupId: Int, content: String, idempotencyKey: String? = null) = PolyPlusClient.SCOPE.launch {
        val selfId = runCatching { net.minecraft.client.Minecraft.getInstance().user.profileId.toString() }.getOrDefault("")
        val tempId = PENDING_ID_BASE + pendingIdCounter.getAndIncrement()
        appendOrReplace(
            groupId,
            GroupMessage(id = tempId, sender = selfId, content = content, sentAt = java.time.Instant.now().toString(), editedAt = null),
        )

        GroupsApi.sendMessage(groupId, content, idempotencyKey)
            .onSuccess { message ->
                removeMessage(groupId, tempId)
                appendOrReplace(groupId, message)
            }
            .onFailure {
                LOGGER.error("Failed to send message to group $groupId", it)
                SocialErrors.emit("Couldn't send message", it)
                removeMessage(groupId, tempId)
            }
    }

    fun editMessage(groupId: Int, messageId: Long, content: String) = PolyPlusClient.SCOPE.launch {
        GroupsApi.editMessage(groupId, messageId, content)
            .onSuccess { message -> appendOrReplace(groupId, message) }
            .onFailure { LOGGER.error("Failed to edit message $messageId in group $groupId", it) }
    }

    fun deleteMessage(groupId: Int, messageId: Long) = PolyPlusClient.SCOPE.launch {
        GroupsApi.deleteMessage(groupId, messageId)
            .onSuccess { removeMessage(groupId, messageId) }
            .onFailure { LOGGER.error("Failed to delete message $messageId in group $groupId", it) }
    }

    fun markRead(groupId: Int, messageId: Long) = PolyPlusClient.SCOPE.launch {
        GroupsApi.markRead(groupId, messageId)
            .onSuccess { refreshGroups() }
            .onFailure { LOGGER.error("Failed to mark group $groupId read up to $messageId", it) }
    }

    fun claimSpecialChatGroup(groupId: Int) = PolyPlusClient.SCOPE.launch {
        GroupsApi.claim(groupId)
            .onSuccess { summary ->
                upsertGroup(summary)
                SpecialChatRepository.refreshTargets()
            }
            .onFailure {
                LOGGER.error("Failed to convert group $groupId into a normal chat", it)
                SocialErrors.emit("Couldn't convert this chat", it)
            }
    }

    private fun upsertGroup(summary: GroupSummary) {
        _groups.value = _groups.value.filterNot { it.id == summary.id } + summary
    }

    private fun appendOrReplace(groupId: Int, message: GroupMessage) {
        val flow = messagesByGroup.getOrPut(groupId) { MutableStateFlow(emptyList()) }
        flow.value = (flow.value.filterNot { it.id == message.id } + message).sortedBy { it.id }
        refreshGroups()
    }

    private fun removeMessage(groupId: Int, messageId: Long) {
        messagesByGroup[groupId]?.let { flow ->
            flow.value = flow.value.filterNot { it.id == messageId }
        }
    }

    private fun onMessageReceived(packet: ClientboundPacket.GroupMessageReceived) {
        appendOrReplace(
            packet.groupId,
            GroupMessage(
                id = packet.messageId,
                sender = packet.sender,
                content = packet.content,
                sentAt = java.time.Instant.now().toString(),
                editedAt = null,
                sessionInvite = packet.sessionInviteId?.let {
                    GroupMessageSessionInvite(id = it, sessionId = "", status = packet.sessionInviteStatus ?: "pending")
                },
            ),
        )
        if (packet.sessionInviteId == null) notifyMessageReceived(packet.sender, packet.content)
    }

    private fun notifyMessageReceived(sender: String, content: String) {
        val selfId = runCatching { net.minecraft.client.Minecraft.getInstance().user.profileId.toString() }.getOrDefault("")
        if (sender == selfId) return

        PlayerNamesRepository.resolve(listOf(sender))
        val name = PlayerNamesRepository.names.value[sender] ?: sender.take(8)
        val preview = if (content.length > 80) content.take(77) + "..." else content
        Notifications.success(name, preview)
    }

    private fun onMessageEdited(packet: ClientboundPacket.GroupMessageEdited) {
        val flow = messagesByGroup[packet.groupId] ?: return
        flow.value = flow.value.map { message ->
            if (message.id != packet.messageId) return@map message
            val invite = message.sessionInvite
            message.copy(
                content = packet.content,
                sessionInvite = if (invite != null && packet.sessionInviteStatus != null) {
                    invite.copy(status = packet.sessionInviteStatus)
                } else {
                    invite
                },
            )
        }
    }

    private fun onMessageDeleted(packet: ClientboundPacket.GroupMessageDeleted) {
        removeMessage(packet.groupId, packet.messageId)
    }
}
