package org.polyfrost.polyplus.client.featured

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File
import kotlinx.serialization.Serializable

/** Folders for saved servers, kept beside servers.dat so vanilla never sees them */
object ServerFolders {
    private val logger = LogManager.getLogger("PolyPlus/ServerFolders")
    private var state: State? = null

    private val file: File
        get() = File(FabricLoader.getInstance().gameDir.toFile(), "polyplus/server_folders.json")

    enum class Sort { CUSTOM, A_Z, Z_A }

    class Group<T>(@JvmField val folder: String?, @JvmField val items: List<T>)

    @Serializable
    private data class State(
        val folders: Map<String, String> = emptyMap(),
        val collapsed: Set<String> = emptySet(),
        val sort: Sort = Sort.CUSTOM,
        val folderOrder: List<String> = emptyList(),
    )

    @JvmStatic
    fun folderOf(address: String): String? = load().folders[normalizeServerAddress(address)]

    @JvmStatic
    fun setFolder(address: String, folder: String) {
        val key = normalizeServerAddress(address)
        val name = folder.trim()
        val current = load()
        if (current.folders[key] == name.ifEmpty { null }) return
        save(current.copy(folders = if (name.isEmpty()) current.folders - key else current.folders + (key to name)))
    }

    @JvmStatic
    fun hasAny(): Boolean = load().folders.isNotEmpty()

    @JvmStatic
    fun names(): List<String> = load().folders.values.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    @JvmStatic
    fun sort(): Sort = load().sort

    @JvmStatic
    fun cycleSort() {
        val current = load()
        save(current.copy(sort = Sort.entries[(current.sort.ordinal + 1) % Sort.entries.size]))
    }

    /** Moves a folder within [shown], the order on screen, and keeps that as the custom order */
    @JvmStatic
    fun moveFolder(folder: String, by: Int, shown: List<String>) {
        val from = shown.indexOf(folder)
        val to = from + by
        if (from < 0 || to !in shown.indices) return
        val order = shown.toMutableList()
        order[from] = order[to].also { order[to] = folder }
        save(load().copy(folderOrder = order))
    }

    @JvmStatic
    fun <T> layout(items: List<T>, address: (T) -> String, name: (T) -> String): List<Group<T>> {
        val current = load()
        return layoutBy(items, { folderOf(address(it)) }, name, current.sort, current.folderOrder)
    }

    /** Unfiled servers first, then the folders, each sorted the same way */
    internal fun <T> layoutBy(
        items: List<T>,
        folder: (T) -> String?,
        name: (T) -> String,
        sort: Sort,
        folderOrder: List<String>,
    ): List<Group<T>> {
        val byFolder = LinkedHashMap<String?, MutableList<T>>()
        for (item in items) byFolder.getOrPut(folder(item)) { mutableListOf() }.add(item)

        val folders = byFolder.keys.filterNotNull().let { found ->
            when (sort) {
                Sort.CUSTOM -> found.sortedBy { folderOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                Sort.A_Z -> found.sortedWith(String.CASE_INSENSITIVE_ORDER)
                Sort.Z_A -> found.sortedWith(String.CASE_INSENSITIVE_ORDER.reversed())
            }
        }
        val byName = compareBy(String.CASE_INSENSITIVE_ORDER, name)
        fun ordered(group: List<T>) = when (sort) {
            Sort.CUSTOM -> group
            Sort.A_Z -> group.sortedWith(byName)
            Sort.Z_A -> group.sortedWith(byName.reversed())
        }

        return (listOf<String?>(null) + folders).mapNotNull { key ->
            byFolder[key]?.let { Group(key, ordered(it)) }
        }
    }

    @JvmStatic
    fun isCollapsed(folder: String): Boolean = folder in load().collapsed

    @JvmStatic
    fun toggle(folder: String) {
        val current = load()
        save(current.copy(collapsed = if (folder in current.collapsed) current.collapsed - folder else current.collapsed + folder))
    }

    private fun load(): State = state ?: (runCatching {
        file.takeIf(File::isFile)?.readText()?.let { PolyPlusClient.JSON.decodeFromString(State.serializer(), it) }
    }.onFailure { logger.warn("Could not read server folders", it) }.getOrNull() ?: State()).also { state = it }

    private fun save(next: State) {
        state = next
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(PolyPlusClient.JSON.encodeToString(State.serializer(), next))
        }.onFailure { logger.warn("Could not save server folders", it) }
    }
}
