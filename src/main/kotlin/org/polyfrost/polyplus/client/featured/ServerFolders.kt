package org.polyfrost.polyplus.client.featured

import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import java.io.File
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable

object ServerFolders {
    private val logger = LogManager.getLogger("PolyPlus/ServerFolders")
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "PolyPlus server folders").apply { isDaemon = true } }

    @Volatile
    private var state: State? = null

    @Volatile
    private var revision = 0

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
    fun revision(): Int = revision

    @JvmStatic
    fun folderOf(address: String): String? = load().folders[normalizeServerAddress(address)]

    @JvmStatic
    @Synchronized
    fun setFolder(address: String, folder: String) {
        if (address.isBlank()) return
        val key = normalizeServerAddress(address)
        val current = load()
        val name = canonical(current, folder)
        if (current.folders[key] == name) return
        val folders = if (name == null) current.folders - key else current.folders + (key to name)
        save(prune(current.copy(folders = folders)))
    }

    @JvmStatic
    fun forget(address: String) = setFolder(address, "")

    @JvmStatic
    @Synchronized
    fun moveAddress(from: String, to: String) {
        val folder = folderOf(from) ?: return
        if (normalizeServerAddress(from) == normalizeServerAddress(to)) return
        val current = load()
        val folders = current.folders - normalizeServerAddress(from) + (normalizeServerAddress(to) to folder)
        save(current.copy(folders = folders))
    }

    @JvmStatic
    fun hasAny(): Boolean = load().folders.isNotEmpty()

    @JvmStatic
    fun names(): List<String> = load().folders.values.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    @JvmStatic
    fun sort(): Sort = load().sort

    @JvmStatic
    @Synchronized
    fun setSort(sort: Sort) {
        val current = load()
        if (current.sort != sort) save(current.copy(sort = sort))
    }

    @JvmStatic
    fun cycleSort() = setSort(Sort.entries[(sort().ordinal + 1) % Sort.entries.size])

    @JvmStatic
    @Synchronized
    fun moveFolder(folder: String, by: Int, shown: List<String>) {
        val from = shown.indexOf(folder)
        val to = from + by
        if (from < 0 || to !in shown.indices) return
        val order = shown.toMutableList()
        order[from] = order[to].also { order[to] = folder }
        val current = load()
        save(current.copy(folderOrder = order + current.folderOrder.filterNot { it in order }))
    }

    @JvmStatic
    @Synchronized
    fun renameFolder(folder: String, to: String) {
        val current = load()
        val target = to.trim()
        if (target.isEmpty() || target == folder || folder !in current.folders.values) return
        val name = current.folders.values.firstOrNull { it != folder && it.equals(target, ignoreCase = true) } ?: target
        val merging = name != target || current.folders.values.any { it == name }
        val folders = current.folders.mapValues { (_, value) -> if (value == folder) name else value }
        val collapsed = if (folder in current.collapsed && !merging) current.collapsed - folder + name else current.collapsed - folder
        val order = if (merging) current.folderOrder - folder else current.folderOrder.map { if (it == folder) name else it }
        save(current.copy(folders = folders, collapsed = collapsed, folderOrder = order))
    }

    @JvmStatic
    @Synchronized
    fun deleteFolder(folder: String) {
        val current = load()
        save(prune(current.copy(folders = current.folders.filterValues { it != folder })))
    }

    @JvmStatic
    fun <T> layout(items: List<T>, address: (T) -> String, name: (T) -> String): List<Group<T>> {
        val current = load()
        return layoutBy(items, { current.folders[normalizeServerAddress(address(it))] }, name, current.sort, current.folderOrder)
    }

    internal fun <T> layoutBy(
        items: List<T>,
        folder: (T) -> String?,
        name: (T) -> String,
        sort: Sort,
        folderOrder: List<String>,
    ): List<Group<T>> {
        val byFolder = LinkedHashMap<String?, MutableList<T>>()
        for (item in items) byFolder.getOrPut(folder(item)) { mutableListOf() }.add(item)

        val found = byFolder.keys.filterNotNull()
        val folders = when (sort) {
            Sort.CUSTOM -> found.sortedBy { folderOrder.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            Sort.A_Z -> found.sortedWith(String.CASE_INSENSITIVE_ORDER)
            Sort.Z_A -> found.sortedWith(String.CASE_INSENSITIVE_ORDER.reversed())
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
    @Synchronized
    fun toggle(folder: String) {
        val current = load()
        save(current.copy(collapsed = if (folder in current.collapsed) current.collapsed - folder else current.collapsed + folder))
    }

    private fun canonical(current: State, folder: String): String? {
        val name = folder.trim().ifEmpty { return null }
        return current.folders.values.firstOrNull { it.equals(name, ignoreCase = true) } ?: name
    }

    private fun prune(next: State): State {
        val live = next.folders.values.toSet()
        return next.copy(collapsed = next.collapsed.filterTo(LinkedHashSet()) { it in live }, folderOrder = next.folderOrder.filter { it in live })
    }

    private fun load(): State = state ?: synchronized(this) {
        state ?: (runCatching {
            file.takeIf(File::isFile)?.readText()?.let { PolyPlusClient.JSON.decodeFromString(State.serializer(), it) }
        }.onFailure { logger.warn("Could not read server folders", it) }.getOrNull() ?: State()).also { state = it }
    }

    private fun save(next: State) {
        state = next
        revision++
        writer.execute {
            runCatching {
                val target = file
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, target.name + ".tmp")
                temp.writeText(PolyPlusClient.JSON.encodeToString(State.serializer(), next))
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) error("Could not replace ${target.name}")
                }
            }.onFailure { logger.warn("Could not save server folders", it) }
        }
    }
}
