package org.polyfrost.polyplus.client.gui.screens.packs

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object PackLinks {
    private const val FILE = "packed_packs.folderpack.json"
    private const val KEY = "packIds"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun read(packDir: Path, maxDepth: Int): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((path, file) in metas(packDir, maxDepth)) {
            val ids = readObject(file)?.get(KEY) as? JsonArray ?: continue
            for (element in ids) {
                val id = element.takeIf { it.isJsonPrimitive }?.asString ?: continue
                if (linkable(id)) out.putIfAbsent(id, path)
            }
        }
        return out
    }

    fun remap(packDir: Path, maxDepth: Int, renames: Map<String, String>): Boolean =
        rewrite(packDir, maxDepth) { _, id -> renames[id] ?: id }

    fun unlink(packDir: Path, maxDepth: Int, links: Map<String, String>): Boolean =
        rewrite(packDir, maxDepth) { path, id -> if (links[id] == path) null else id }

    private fun linkable(id: String): Boolean =
        !id.startsWith("relative/") && (!id.startsWith("file/") || id.indexOf('/', "file/".length) < 0)

    private fun rewrite(packDir: Path, maxDepth: Int, transform: (String, String) -> String?): Boolean {
        var ok = true
        for ((path, file) in metas(packDir, maxDepth)) {
            val obj = readObject(file) ?: continue
            val ids = obj.get(KEY) as? JsonArray ?: continue
            val updated = JsonArray()
            var changed = false
            for (element in ids) {
                val id = element.takeIf { it.isJsonPrimitive }?.asString
                if (id == null) {
                    updated.add(element)
                    continue
                }
                val target = transform(path, id)
                if (target != id) changed = true
                if (target != null) updated.add(target)
            }
            if (!changed) continue
            obj.add(KEY, updated)
            if (!write(file, obj)) ok = false
        }
        return ok
    }

    private fun metas(packDir: Path, maxDepth: Int): List<Pair<String, Path>> {
        val out = ArrayList<Pair<String, Path>>()
        fun walk(dir: Path, prefix: String, depth: Int) {
            if (depth > maxDepth) return
            val children = runCatching {
                Files.list(dir).use { stream ->
                    stream
                        .filter { Files.isDirectory(it, LinkOption.NOFOLLOW_LINKS) && !it.fileName.toString().startsWith(".") }
                        .filter { !Files.isRegularFile(it.resolve("pack.mcmeta")) }
                        .toList()
                }
            }.getOrDefault(emptyList())
            for (child in children.sortedBy { it.fileName.toString() }) {
                val path = PackFolders.join(prefix, child.fileName.toString())
                val file = child.resolve(FILE)
                if (Files.isRegularFile(file)) out.add(path to file)
                walk(child, path, depth + 1)
            }
        }
        walk(packDir, "", 1)
        return out
    }

    private fun readObject(path: Path): JsonObject? =
        runCatching { Files.newBufferedReader(path).use { JsonParser.parseReader(it) } }.getOrNull() as? JsonObject

    private fun write(target: Path, obj: JsonObject): Boolean {
        var temp: Path? = null
        return try {
            val dir = target.toAbsolutePath().parent
            val created = Files.createTempFile(dir, target.fileName.toString(), ".tmp")
            temp = created
            Files.newBufferedWriter(created).use { gson.toJson(obj, it) }
            try {
                Files.move(created, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(created, target, StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            temp?.let { runCatching { Files.deleteIfExists(it) } }
            false
        }
    }
}
