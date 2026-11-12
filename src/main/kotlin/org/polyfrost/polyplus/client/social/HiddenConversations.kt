package org.polyfrost.polyplus.client.social

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

object HiddenConversations {
    private val LOGGER = LogManager.getLogger()

    @Serializable
    private data class Stored(val hidden: Map<Int, Long> = emptyMap())

    private val file: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/hidden_conversations.json")

    private val _hidden = MutableStateFlow(load())
    val hidden: StateFlow<Map<Int, Long>> = _hidden.asStateFlow()

    fun hide(group: GroupSummary) {
        val now = System.currentTimeMillis()
        val since = group.lastMessage?.sentAt?.let(::millisOf) ?: 0L
        update(_hidden.value + (group.id to maxOf(now, since)))
        if (group.unread) GroupsRepository.markLatestRead(group.id)
    }

    fun show(groupId: Int) {
        if (groupId in _hidden.value) update(_hidden.value - groupId)
    }

    fun isHidden(group: GroupSummary, hidden: Map<Int, Long> = _hidden.value): Boolean {
        val hiddenAt = hidden[group.id] ?: return false
        return !newerThan(group, hiddenAt)
    }

    fun reveal(groups: List<GroupSummary>) {
        val current = _hidden.value
        val revealed = groups.filter { group -> current[group.id]?.let { newerThan(group, it) } == true }.map { it.id }
        if (revealed.isNotEmpty()) update(current - revealed.toSet())
    }

    private fun newerThan(group: GroupSummary, hiddenAt: Long): Boolean {
        val sent = group.lastMessage?.sentAt?.let(::millisOf) ?: return false
        return sent > hiddenAt
    }

    private fun millisOf(sentAt: String): Long? = runCatching { Instant.parse(sentAt).toEpochMilli() }.getOrNull()

    private fun update(next: Map<Int, Long>) {
        _hidden.value = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(Stored.serializer(), Stored(next)))
        }.onFailure { LOGGER.warn("Could not save hidden conversations", it) }
    }

    private fun load(): Map<Int, Long> = runCatching {
        file.takeIf(File::isFile)?.readText()?.let { PolyPlusClient.JSON.decodeFromString(Stored.serializer(), it).hidden }
    }.onFailure { LOGGER.warn("Could not read hidden conversations", it) }.getOrNull() ?: emptyMap()
}
