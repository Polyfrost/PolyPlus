package org.polyfrost.polyplus.client.gui.screens.packs

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import java.lang.reflect.Method
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

internal class PackProfile(val name: String, val packs: List<String>, val external: Boolean)

internal object PackProfiles {
    private const val PACKED_PACKS_DIR = "packed_packs/profiles/resourcepacks"
    private const val PACKED_PACKS_EXTENSION = ".profile.json"
    private const val PACKED_PACKS_ID = "packed_packs"
    private const val PACKED_PACKS_PROFILES = "io.github.fishstiz.packed_packs.config.ProfileManager"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val file: Path get() = FabricLoader.getInstance().gameDir.resolve("polyplus").resolve("pack-profiles.json")

    fun all(): List<PackProfile> = own() + packedPacks()

    fun save(name: String, packs: List<String>): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return true
        return write(own().filter { !it.name.equals(trimmed, ignoreCase = true) } + PackProfile(trimmed, packs, false))
    }

    fun exists(name: String): Boolean {
        val trimmed = name.trim()
        return own().any { it.name.equals(trimmed, ignoreCase = true) }
    }

    fun delete(profile: PackProfile): Boolean {
        if (profile.external) return true
        return write(own().filter { it.name != profile.name })
    }

    fun rename(renames: Map<String, String>): Boolean {
        var ok = true
        val profiles = own()
        if (profiles.any { profile -> profile.packs.any { it in renames } }) {
            ok = write(profiles.map { profile -> PackProfile(profile.name, profile.packs.map { renames[it] ?: it }, false) })
        }
        if (renames.isEmpty()) return ok
        if (FabricLoader.getInstance().isModLoaded(PACKED_PACKS_ID)) {
            val remap = packedPacksRemap ?: return ok
            for ((from, to) in renames) {
                if (runCatching { remap.second.invoke(remap.first, from, to) }.isFailure) break
            }
            return ok
        }
        for (path in packedPacksFiles()) {
            val obj = readObject(path) ?: continue
            var changed = false
            val array = obj.get("packIds") as? JsonArray
            if (array != null) {
                val updated = JsonArray()
                for (element in array) {
                    val id = element.takeIf { it.isJsonPrimitive }?.asString
                    val target = id?.let { renames[it] }
                    if (target != null) {
                        updated.add(target)
                        changed = true
                    } else {
                        updated.add(element)
                    }
                }
                obj.add("packIds", updated)
            }
            val overrides = obj.get("overrides") as? JsonObject
            if (overrides != null && overrides.keySet().any { it in renames }) {
                val updated = JsonObject()
                for ((key, value) in overrides.entrySet()) updated.add(renames[key] ?: key, value)
                obj.add("overrides", updated)
                changed = true
            }
            if (!changed) continue
            if (!writeJson(path, obj)) ok = false
        }
        return ok
    }

    private val packedPacksRemap: Pair<Any, Method>? by lazy {
        runCatching {
            val type = Class.forName(PACKED_PACKS_PROFILES)
            val manager = type.getMethod("clientResources").invoke(null) ?: return@runCatching null
            manager to type.getMethod("remapAndSavePackIds", String::class.java, String::class.java)
        }.getOrNull()
    }

    private fun own(): List<PackProfile> {
        val root = runCatching { Files.newBufferedReader(file).use { JsonParser.parseReader(it) } }.getOrNull() as? JsonObject ?: return emptyList()
        val list = root.get("profiles") as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            PackProfile(name, ids(obj.get("packs") as? JsonArray), false)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun write(profiles: List<PackProfile>): Boolean {
        val list = JsonArray()
        for (profile in profiles) {
            val obj = JsonObject()
            obj.addProperty("name", profile.name)
            obj.add("packs", JsonArray().also { array -> profile.packs.forEach(array::add) })
            list.add(obj)
        }
        val root = JsonObject().also { it.add("profiles", list) }
        return writeJson(file, root)
    }

    private fun writeJson(target: Path, element: JsonElement): Boolean {
        var temp: Path? = null
        return try {
            val dir = target.toAbsolutePath().parent
            Files.createDirectories(dir)
            val created = Files.createTempFile(dir, target.fileName.toString(), ".tmp")
            temp = created
            Files.newBufferedWriter(created).use { gson.toJson(element, it) }
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

    private fun readObject(path: Path): JsonObject? =
        runCatching { Files.newBufferedReader(path).use { JsonParser.parseReader(it) } }.getOrNull() as? JsonObject

    private fun packedPacksFiles(): List<Path> {
        val dir = FabricLoader.getInstance().configDir.resolve(PACKED_PACKS_DIR)
        if (!Files.isDirectory(dir)) return emptyList()
        return runCatching { Files.list(dir).use { stream -> stream.filter { it.fileName.toString().endsWith(PACKED_PACKS_EXTENSION) }.toList() } }.getOrNull().orEmpty()
    }

    private fun packedPacks(): List<PackProfile> =
        packedPacksFiles().mapNotNull { path ->
            val obj = readObject(path) ?: return@mapNotNull null
            val fallback = path.fileName.toString().removeSuffix(PACKED_PACKS_EXTENSION)
            val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: fallback
            PackProfile(name, ids(obj.get("packIds") as? JsonArray), true)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }

    private fun ids(array: JsonArray?): List<String> =
        array?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
}

internal object PackProfileNotice {
    fun show() {
        runCatching { Notifications.error("Couldn't save profiles", "The profile file could not be written. Check free disk space and try again.") }
    }
}
