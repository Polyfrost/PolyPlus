package org.polyfrost.polyplus.client.social

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.responses.GroupLastMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupMessage
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable

object HiddenConversations {
    private val LOGGER = LogManager.getLogger()

    @Serializable
    data class Cutoff(val messageId: Long? = null, val sentAt: String? = null, val hiddenAt: Long = 0L)

    @Serializable
    private data class Stored(
        val hidden: Map<Int, Long> = emptyMap(),
        val accounts: Map<String, Map<Int, Cutoff>> = emptyMap(),
    )

    private val file: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/hidden_conversations.json")

    private val lock = Any()

    @Volatile
    private var legacy: Map<Int, Long> = emptyMap()

    private val _hidden = MutableStateFlow(load())
    val hidden: StateFlow<Map<String, Map<Int, Cutoff>>> = _hidden.asStateFlow()

    init {
        migrate(ChatIdentity.selfId())
    }

    fun hide(group: GroupSummary) {
        val identity = ChatIdentity.selfId()
        migrate(identity)
        val last = group.lastMessage
        val known = GroupsRepository.newestMessage(group.id)?.takeIf { last == null || sameMessage(it, last) }
        val cutoff = Cutoff(known?.id, last?.sentAt ?: known?.sentAt, System.currentTimeMillis())
        edit(identity) { it + (group.id to cutoff) }
        if (!group.unread) return
        val notAfter = cutoff.sentAt?.let(::instantOf) ?: Instant.ofEpochMilli(cutoff.hiddenAt)
        GroupsRepository.acknowledge(group.id, known?.id, notAfter) { acked ->
            if (acked == null) {
                edit(identity) { it - group.id }
            } else if (known == null) {
                edit(identity) { entries -> entries[group.id]?.let { entries + (group.id to it.copy(messageId = acked)) } ?: entries }
            }
        }
    }

    fun show(groupId: Int) {
        val identity = ChatIdentity.selfId()
        if (_hidden.value[identity]?.containsKey(groupId) == true) edit(identity) { it - groupId }
    }

    fun isHidden(group: GroupSummary, hidden: Map<String, Map<Int, Cutoff>> = _hidden.value): Boolean {
        val cutoff = hidden[ChatIdentity.selfId()]?.get(group.id) ?: return false
        return !newer(group, cutoff)
    }

    fun reveal(groups: List<GroupSummary>) {
        val identity = ChatIdentity.selfId()
        migrate(identity)
        val current = _hidden.value[identity] ?: return
        val revealed = groups.filter { group -> current[group.id]?.let { newer(group, it) } == true }.mapTo(HashSet()) { it.id }
        if (revealed.isNotEmpty()) edit(identity) { it - revealed }
    }

    internal fun messageArrived(groupId: Int, messageId: Long) {
        val identity = ChatIdentity.selfId()
        val cutoff = _hidden.value[identity]?.get(groupId) ?: return
        if (cutoff.messageId == null || messageId > cutoff.messageId) edit(identity) { it - groupId }
    }

    private fun newer(group: GroupSummary, cutoff: Cutoff): Boolean {
        val sent = group.lastMessage?.sentAt?.let(::instantOf) ?: return false
        val since = cutoff.sentAt?.let(::instantOf) ?: return sent.toEpochMilli() > cutoff.hiddenAt
        return sent.isAfter(since)
    }

    private fun sameMessage(message: GroupMessage, last: GroupLastMessage): Boolean {
        val sent = instantOf(message.sentAt) ?: return false
        return message.sender == last.sender && sent == instantOf(last.sentAt)
    }

    private fun instantOf(sentAt: String): Instant? = runCatching { Instant.parse(sentAt) }.getOrNull()

    private fun migrate(identity: String) {
        if (legacy.isEmpty() || identity.isBlank()) return
        val moved = synchronized(lock) {
            legacy.also { legacy = emptyMap() }
        }
        if (moved.isEmpty()) return
        edit(identity) { entries -> moved.mapValues { (_, at) -> Cutoff(hiddenAt = at) } + entries }
    }

    private fun edit(identity: String, change: (Map<Int, Cutoff>) -> Map<Int, Cutoff>) {
        _hidden.updateAndGet { all ->
            val entries = change(all[identity].orEmpty())
            if (entries.isEmpty()) all - identity else all + (identity to entries)
        }
        save()
    }

    private fun save() {
        synchronized(lock) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(PolyPlusClient.JSON.encodeToString(Stored.serializer(), Stored(legacy, _hidden.value)))
            }.onFailure { LOGGER.warn("Could not save hidden conversations", it) }
        }
    }

    private fun load(): Map<String, Map<Int, Cutoff>> {
        val stored = runCatching {
            file.takeIf(File::isFile)?.readText()?.let { PolyPlusClient.JSON.decodeFromString(Stored.serializer(), it) }
        }.onFailure { LOGGER.warn("Could not read hidden conversations", it) }.getOrNull() ?: return emptyMap()
        legacy = stored.hidden
        return stored.accounts
    }
}
