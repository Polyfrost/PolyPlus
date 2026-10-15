package org.polyfrost.polyplus.client.emotes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import java.io.File

object EmoteWheels {
    const val SLOT_COUNT = 8
    const val MAX_WHEELS = 8

    private val LOGGER = LogManager.getLogger("polyplus/emote-wheels")
    private val file = File("${PolyPlusConstants.NAME}/emote-wheels.json")

    @Serializable
    private data class Stored(val active: Int = 0, val wheels: List<List<Int?>> = emptyList())

    private var stored by mutableStateOf(load())

    val wheelCount: Int get() = stored.wheels.size.coerceAtLeast(1)

    val activeIndex: Int get() = stored.active.coerceIn(0, wheelCount - 1)

    fun slots(index: Int = activeIndex): List<Int?> {
        val owned = CosmeticCatalog.ownedEmoteIds()
        val wheel = stored.wheels.getOrNull(index) ?: return defaultSlots(owned)
        return List(SLOT_COUNT) { slot -> wheel.getOrNull(slot)?.takeIf { it in owned } }
    }

    fun assign(index: Int, slot: Int, emoteId: Int?) {
        if (slot !in 0 until SLOT_COUNT) return
        val wheels = materialized().toMutableList()
        while (wheels.size <= index) wheels += List(SLOT_COUNT) { null }
        val next = MutableList(SLOT_COUNT) { wheels[index].getOrNull(it) }
        if (emoteId != null) {
            for (i in next.indices) if (next[i] == emoteId) next[i] = null
        }
        next[slot] = emoteId
        wheels[index] = next
        update(stored.copy(wheels = wheels))
    }

    fun addToFirstEmpty(index: Int, emoteId: Int): Int? {
        val current = slots(index)
        if (emoteId in current) return current.indexOf(emoteId)
        val raw = stored.wheels.getOrNull(index)?.let { wheel -> List(SLOT_COUNT) { wheel.getOrNull(it) } } ?: current
        val slot = raw.indexOfFirst { it == null }.takeIf { it >= 0 }
            ?: current.indexOfFirst { it == null }.takeIf { it >= 0 }
            ?: return null
        assign(index, slot, emoteId)
        return slot
    }

    fun addWheel(): Boolean {
        if (wheelCount >= MAX_WHEELS) return false
        val wheels = materialized() + listOf(List(SLOT_COUNT) { null })
        update(Stored(active = wheels.lastIndex, wheels = wheels))
        return true
    }

    fun removeWheel(index: Int) {
        val wheels = materialized()
        if (wheels.size <= 1 || index !in wheels.indices) return
        val next = wheels.filterIndexed { i, _ -> i != index }
        update(Stored(active = activeIndex.coerceAtMost(next.lastIndex), wheels = next))
    }

    fun setActive(index: Int) {
        if (index == activeIndex || index !in 0 until wheelCount) return
        update(stored.copy(active = index))
    }

    fun cycle(delta: Int) {
        if (wheelCount <= 1) return
        setActive(Math.floorMod(activeIndex + delta, wheelCount))
    }

    private fun defaultSlots(owned: Set<Int>): List<Int?> {
        val ids = CosmeticCatalog.allEmoteDefinitions()
            .filter { it.id in owned }
            .sortedBy { it.id }
            .take(SLOT_COUNT)
            .map { it.id }
        return List(SLOT_COUNT) { ids.getOrNull(it) }
    }

    private fun materialized(): List<List<Int?>> =
        stored.wheels.ifEmpty { listOf(slots(0)) }

    private fun update(next: Stored) {
        stored = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(Stored.serializer(), next))
        }.onFailure { LOGGER.warn("Failed to save emote wheels", it) }
    }

    private fun load(): Stored = runCatching {
        if (file.isFile) PolyPlusClient.JSON.decodeFromString(Stored.serializer(), file.readText()) else Stored()
    }.onFailure { LOGGER.warn("Failed to read emote wheels; starting fresh", it) }.getOrDefault(Stored())
}
