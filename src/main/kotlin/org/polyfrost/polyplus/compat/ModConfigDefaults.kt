package org.polyfrost.polyplus.compat

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

object ModConfigDefaults {

    private val logger = LogManager.getLogger("PolyPlus/ModConfigDefaults")

    private const val APPLIED_FILE = "polyplus-mod-defaults.txt"

    private val EARLY_MODS = setOf("modernfix", "ferritecore")

    internal class Default(val modId: String, val file: String, val key: String, val value: Any) {
        val id get() = "$file $key = ${render(value)}"
    }

    internal val DEFAULTS = listOf(
        Default("modernfix", "modernfix-mixins.properties", "mixin.perf.dynamic_entity_renderers", true),
        Default("modernfix", "modernfix-mixins.properties", "mixin.perf.dynamic_resources", true),
        Default("modernfix", "modernfix-mixins.properties", "mixin.perf.faster_item_rendering", true),
        Default("ferritecore", "ferritecore.mixin.properties", "useSmallThreadingDetector", true),
        Default("vmp", "vmp.properties", "show_async_loading_messages", false),
        Default("fastquit", "fastquit.toml", "showToasts", false),
        Default("dynamic_fps", "dynamic_fps.json", "states.unfocused.frame_rate_target", 10),
        Default("dynamic_fps", "dynamic_fps.json", "states.unfocused.volume_multipliers.master", 1.0),
        Default("dynamic_fps", "dynamic_fps.json", "states.invisible.run_garbage_collector", true),
        Default("dynamic_fps", "dynamic_fps.json", "states.invisible.volume_multipliers.master", 1.0),
        Default("citresewn", "citresewn.json", "broken_paths", true),
    )

    fun applyEarly() = applyMatching { it.modId in EARLY_MODS }

    fun apply() = applyMatching { it.modId !in EARLY_MODS }

    internal fun everyEarlyDefaultIsFlat() = DEFAULTS.none { it.modId in EARLY_MODS && it.file.endsWith(".json") }

    private fun applyMatching(select: (Default) -> Boolean) {
        val loader = FabricLoader.getInstance()
        val applied = readApplied(loader.configDir.resolve(APPLIED_FILE))
        val pending = DEFAULTS.filter { select(it) && it.id !in applied && loader.isModLoaded(it.modId) }
        if (pending.isEmpty()) return

        val done = mutableListOf<String>()
        pending.groupBy(Default::file).forEach { (file, defaults) ->
            runCatching { merge(loader.configDir.resolve(file), defaults) }
                .onSuccess {
                    done += defaults.map(Default::id)
                    logger.info("Applied {} default {} settings", defaults.size, file)
                }
                .onFailure { logger.warn("Could not apply the default {} settings", file, it) }
        }
        if (done.isNotEmpty()) writeApplied(loader.configDir.resolve(APPLIED_FILE), applied + done)
    }

    private fun merge(path: Path, defaults: List<Default>) {
        val text = if (path.exists()) path.readText() else ""
        val merged =
            if (path.fileName.toString().endsWith(".json")) mergeJson(text, defaults)
            else mergeFlat(text, defaults, toml = path.fileName.toString().endsWith(".toml"))

        path.createParentDirectories()
        writeAtomically(path, merged)
    }

    private fun writeAtomically(path: Path, text: String) {
        val temp = path.resolveSibling("${path.fileName}.polyplus-tmp")
        try {
            temp.writeText(text)
            runCatching { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE) }
                .recoverCatching { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING) }
                .getOrThrow()
        } finally {
            runCatching { temp.deleteIfExists() }
        }
    }

    internal fun mergeFlat(text: String, defaults: List<Default>, toml: Boolean): String {
        val lines = text.lines().dropLastWhile(String::isBlank).toMutableList()
        defaults.forEach { default ->
            val pattern = Regex("""^(\s*)${Regex.escape(default.key)}\s*=.*$""")
            val index = lines.indexOfFirst(pattern::matches)
            val assignment = "${default.key} = ${render(default.value)}"
            if (index >= 0) lines[index] = pattern.matchEntire(lines[index])!!.groupValues[1] + assignment
            else lines.add(if (toml) firstTableHeader(lines) else lines.size, assignment)
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private val TABLE_HEADER = Regex("""^\[{1,2}[^\[\]]+]{1,2}\s*(#.*)?$""")

    private fun firstTableHeader(lines: List<String>): Int =
        lines.indexOfFirst { TABLE_HEADER.matches(it.trim()) }.takeIf { it >= 0 } ?: lines.size

    internal fun mergeJson(text: String, defaults: List<Default>): String {
        val root = if (text.isBlank()) JsonObject() else JsonParser.parseString(text).asJsonObject
        defaults.forEach { default ->
            val path = default.key.split('.')
            var node = root
            path.dropLast(1).forEach { name ->
                val existing = node.get(name)
                node = if (existing != null && existing.isJsonObject) existing.asJsonObject
                else JsonObject().also { node.add(name, it) }
            }
            node.add(path.last(), primitive(default.value))
        }
        return GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n"
    }

    internal fun render(value: Any): String = when (value) {
        is Boolean, is Number -> value.toString()
        else -> error("Cannot write a ${value.javaClass.simpleName} into a flat config")
    }

    private fun primitive(value: Any): JsonPrimitive = when (value) {
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> error("Cannot write a ${value.javaClass.simpleName} into a json config")
    }

    private fun readApplied(path: Path): Set<String> =
        if (!path.exists()) emptySet()
        else runCatching { path.readLines().filter(String::isNotBlank).toSet() }
            .onFailure { logger.warn("Could not read {}, the defaults may be reapplied", APPLIED_FILE, it) }
            .getOrDefault(emptySet())

    private fun writeApplied(path: Path, applied: Collection<String>) {
        runCatching {
            path.createParentDirectories()
            writeAtomically(path, applied.joinToString("\n", postfix = "\n"))
        }.onFailure { logger.warn("Could not write {}, the defaults may be reapplied", APPLIED_FILE, it) }
    }

}
