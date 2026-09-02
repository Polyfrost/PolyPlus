package org.polyfrost.polyplus.client.social

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.GroupsApi
import org.polyfrost.polyplus.client.network.http.responses.GroupSummary

object SpecialChatResponses {
    enum class Filter { All, Responded, Awaiting }

    private const val CONCURRENCY = 16
    private const val MESSAGE_LIMIT = 200L

    private val _responded = MutableStateFlow<Set<Int>>(emptySet())
    val responded = _responded.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    private val scanned = ConcurrentHashMap.newKeySet<Int>()
    private var job: Job? = null

    internal fun targets(specialChats: List<GroupSummary>, selfId: String): Set<String> =
        if (specialChats.size < 2) setOf(selfId)
        else specialChats.map { it.members.toSet() }.reduce { common, members -> common intersect members }

    internal fun respondedFromSummaries(specialChats: List<GroupSummary>, targets: Set<String>): Set<Int> =
        specialChats.filter { chat -> chat.lastMessage?.sender?.let { it !in targets } == true }
            .map { it.id }
            .toSet()

    fun refresh(groups: List<GroupSummary>, selfId: String) {
        val specialChats = groups.filter { it.special }
        val targets = targets(specialChats, selfId)
        _responded.value = _responded.value + respondedFromSummaries(specialChats, targets)

        if (job?.isActive == true) return
        val pending = specialChats.map { it.id }.filterNot { it in scanned }
        if (pending.isEmpty()) return

        job = PolyPlusClient.SCOPE.launch {
            _scanning.value = true
            try {
                pending.chunked(CONCURRENCY).forEach { chunk ->
                    val batch = coroutineScope {
                        chunk.map { id -> async { id to GroupsApi.messages(id, limit = MESSAGE_LIMIT).getOrNull() } }.awaitAll()
                    }
                    val replied = batch.mapNotNull { (id, messages) ->
                        if (messages == null) return@mapNotNull null
                        scanned += id
                        id.takeIf { messages.any { message -> message.sender !in targets } }
                    }
                    if (replied.isNotEmpty()) _responded.value = _responded.value + replied
                }
            } finally {
                _scanning.value = false
            }
        }
    }
}
