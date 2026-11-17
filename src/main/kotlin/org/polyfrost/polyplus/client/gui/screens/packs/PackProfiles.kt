package org.polyfrost.polyplus.client.gui.screens.packs

//? if >= 26.3 || = 1.8.9 {
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

internal class PackProfile(val name: String, val packs: List<String>, val external: Boolean)

internal object PackProfiles {
    private const val PACKED_PACKS_DIR = "packed_packs/profiles/resourcepacks"
    private const val PACKED_PACKS_EXTENSION = ".profile.json"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val file: Path get() = FabricLoader.getInstance().gameDir.resolve("polyplus").resolve("pack-profiles.json")

    fun all(): List<PackProfile> = own() + packedPacks()

    fun save(name: String, packs: List<String>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        write(own().filter { !it.name.equals(trimmed, ignoreCase = true) } + PackProfile(trimmed, packs, false))
    }

    fun delete(profile: PackProfile) {
        if (profile.external) return
        write(own().filter { it.name != profile.name })
    }

    fun rename(renames: Map<String, String>) {
        val profiles = own()
        if (profiles.none { profile -> profile.packs.any { it in renames } }) return
        write(profiles.map { profile -> PackProfile(profile.name, profile.packs.map { renames[it] ?: it }, false) })
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

    private fun write(profiles: List<PackProfile>) {
        val list = JsonArray()
        for (profile in profiles) {
            val obj = JsonObject()
            obj.addProperty("name", profile.name)
            obj.add("packs", JsonArray().also { array -> profile.packs.forEach(array::add) })
            list.add(obj)
        }
        val root = JsonObject().also { it.add("profiles", list) }
        runCatching {
            Files.createDirectories(file.parent)
            Files.newBufferedWriter(file).use { gson.toJson(root, it) }
        }
    }

    private fun packedPacks(): List<PackProfile> {
        val dir = FabricLoader.getInstance().configDir.resolve(PACKED_PACKS_DIR)
        if (!Files.isDirectory(dir)) return emptyList()
        val files = runCatching { Files.list(dir).use { stream -> stream.filter { it.fileName.toString().endsWith(PACKED_PACKS_EXTENSION) }.toList() } }.getOrNull() ?: return emptyList()
        return files.mapNotNull { path ->
            val obj = runCatching { Files.newBufferedReader(path).use { JsonParser.parseReader(it) } }.getOrNull() as? JsonObject ?: return@mapNotNull null
            val fallback = path.fileName.toString().removeSuffix(PACKED_PACKS_EXTENSION)
            val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() } ?: fallback
            PackProfile(name, ids(obj.get("packIds") as? JsonArray), true)
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun ids(array: JsonArray?): List<String> =
        array?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }.orEmpty()
}
//?}
