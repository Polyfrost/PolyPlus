package org.polyfrost.polyplus.client.gui.screens.packs

//? if >= 26.3 || = 1.8.9 {
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal class PackFolderMeta(val description: String, val hidden: Boolean, val icon: String?)

internal class PackFolder(val path: String, val name: String, val description: String, val packs: List<PackItem>) {
    val enabled: Int get() = packs.count { it.selected }
}

internal class PackFolders(private val session: PackSession) {
    private val metas = ConcurrentHashMap<String, PackFolderMeta>()
    private val listings = ConcurrentHashMap<String, List<String>>()

    fun clear() {
        metas.clear()
        listings.clear()
    }

    fun children(all: List<PackItem>, parent: String): List<PackFolder> {
        val prefix = if (parent.isEmpty()) "" else "$parent/"
        val packIds = all.mapTo(HashSet()) { it.id }
        val names = LinkedHashSet<String>()
        for (item in all) {
            if (!item.folder.startsWith(prefix) || item.folder.length <= prefix.length) continue
            names.add(item.folder.substring(prefix.length).substringBefore('/'))
        }
        names.addAll(listing(parent).filter { "file/$prefix$it" !in packIds })
        return names
            .map { name ->
                val path = prefix + name
                val meta = meta(path)
                PackFolder(path, name, meta.description, all.filter { it.folder == path || it.folder.startsWith("$path/") }) to meta.hidden
            }
            .filter { !it.second }
            .map { it.first }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun allPaths(all: List<PackItem>): List<String> {
        val out = ArrayList<String>()
        fun walk(parent: String) {
            for (child in children(all, parent)) {
                out.add(child.path)
                walk(child.path)
            }
        }
        walk("")
        return out
    }

    fun directPacks(items: List<PackItem>, parent: String): List<PackItem> = items.filter { it.folder == parent }

    fun exists(path: String): Boolean = path.isEmpty() || dir(path) != null

    fun dir(path: String): Path? = runCatching { session.packDir.resolve(path) }.getOrNull()?.takeIf { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) }

    fun iconFile(path: String): Path? {
        val dir = dir(path) ?: return null
        val names = listOfNotNull(meta(path).icon, "icon.png", "pack.png")
        return names.asSequence()
            .mapNotNull { runCatching { dir.resolve(it) }.getOrNull() }
            .firstOrNull { Files.isRegularFile(it) }
    }

    private fun listing(parent: String): List<String> = listings.computeIfAbsent(parent) {
        val dir = if (parent.isEmpty()) session.packDir else dir(parent) ?: return@computeIfAbsent emptyList()
        runCatching {
            Files.list(dir).use { stream ->
                stream
                    .filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) && !it.fileName.toString().startsWith(".") }
                    .filter { !Files.isRegularFile(it.resolve("pack.mcmeta")) }
                    .map { it.fileName.toString() }
                    .toList()
            }
        }.getOrDefault(emptyList())
    }

    private fun meta(path: String): PackFolderMeta = metas.computeIfAbsent(path) { read(it) }

    private fun read(path: String): PackFolderMeta {
        val file = dir(path)?.resolve(PACK_MANAGER_META)?.takeIf { Files.isRegularFile(it) } ?: return EMPTY
        val json = runCatching { Files.newBufferedReader(file).use { JsonParser.parseReader(it) } }.getOrNull() as? JsonObject ?: return EMPTY
        return PackFolderMeta(
            description = json.string("description") ?: "",
            hidden = json.get("hidden")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
            icon = json.string("icon"),
        )
    }

    private fun JsonObject.string(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString

    companion object {
        private const val PACK_MANAGER_META = "folder.json"
        private val EMPTY = PackFolderMeta("", false, null)

        fun relative(id: String): String? = id.takeIf { it.startsWith("file/") }?.removePrefix("file/")

        fun folderOf(id: String): String = relative(id)?.substringBeforeLast('/', "") ?: ""

        fun parentOf(path: String): String = path.substringBeforeLast('/', "")

        fun nameOf(path: String): String = path.substringAfterLast('/')

        fun join(parent: String, name: String): String = if (parent.isEmpty()) name else "$parent/$name"
    }
}
//?}
