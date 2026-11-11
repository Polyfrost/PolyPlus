package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import net.fabricmc.loader.api.FabricLoader
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import java.nio.file.Files

/**
 * Applies settings imported from another client to OneConfig mod configs, e.g. turning on PolySprint's
 * toggle sprint because it was on in Lunar.
 *
 * Each change is written to the config file and, when the config is already loaded, to its live properties too;
 * otherwise the game would save its old in-memory values over the file on exit.
 */
internal object ModConfigPatcher {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync/Mods")
    private val json = Json { prettyPrint = true }

    /**
     * Settings for one mod config. [configId] is the file name inside the OneConfig profile (`polysprint.json`).
     * Keys in [values] may be dot paths into nested objects (`droppedItem.glintColor`).
     */
    data class Patch(
        val source: String,
        val modId: String,
        val configId: String,
        val values: Map<String, JsonElement>,
        /** Skip the patch when the config file is missing, e.g. when its name differs between game versions. */
        val onlyIfExists: Boolean = false,
    )

    /** Whether [patch] would change anything here; used for onboarding previews. */
    fun wouldApply(patch: Patch): Boolean =
        patch.values.isNotEmpty() && FabricLoader.getInstance().isModLoaded(patch.modId) &&
            (!patch.onlyIfExists || Files.isRegularFile(ConfigManager.active().folder.resolve(patch.configId)))

    fun apply(patches: List<Patch>): Int {
        val folder = ConfigManager.active().folder
        var applied = 0
        // Several sources may touch one config; later ones win.
        patches.filter(::wouldApply).groupBy { it.configId }.forEach { (configId, group) ->
            runCatching {
                val values = group.fold(emptyMap<String, JsonElement>()) { acc, patch -> acc + patch.values }
                val path = folder.resolve(configId)
                val current = if (Files.isRegularFile(path)) {
                    json.parseToJsonElement(Files.readString(path)) as? JsonObject ?: run {
                        logger.warn("Mod config {} is not a JSON object; skipped", path)
                        return@runCatching
                    }
                } else {
                    JsonObject(emptyMap())
                }
                ClientConfigSync.backup(path)
                Files.createDirectories(path.parent)
                Files.writeString(path, json.encodeToString(JsonElement.serializer(), patched(current, values)))
                val live = updateLive(configId, values)
                applied++
                logger.info(
                    "Applied {} imported setting(s) to {} from {} ({} set live)",
                    values.size, configId, group.joinToString { it.source }, live,
                )
            }.onFailure { logger.warn("Could not apply imported settings to {}", configId, it) }
        }
        return applied
    }

    /**
     * Returns [root] with [values] applied. Missing objects along a dot path are created. When the existing value
     * is OneConfig's `{"class": ..., "value": ...}` wrapper (enums, colors), only its `value` is replaced.
     */
    internal fun patched(root: JsonObject, values: Map<String, JsonElement>): JsonObject =
        values.entries.fold(root) { acc, (key, value) -> acc.with(key.split('.'), value) }

    private fun JsonObject.with(path: List<String>, value: JsonElement): JsonObject {
        val key = path.first()
        val existing = this[key]
        val replacement = when {
            path.size > 1 -> ((existing as? JsonObject) ?: JsonObject(emptyMap())).with(path.drop(1), value)
            existing is JsonObject && "value" in existing && value !is JsonObject ->
                JsonObject(existing + ("value" to value))
            else -> value
        }
        return JsonObject(this + (key to replacement))
    }

    /** Sets [values] on the loaded config, converting each to its property's type. Returns how many were set. */
    private fun updateLive(configId: String, values: Map<String, JsonElement>): Int {
        val tree = runCatching { ConfigManager.active().get(configId) }.getOrNull() ?: return 0
        return values.count { (key, value) ->
            runCatching {
                val property = tree.getProp(*key.split('.').toTypedArray()) ?: return@runCatching false
                val converted = convert(value, property.get()) ?: return@runCatching false
                property.setAs(converted)
            }.onFailure { logger.debug("Could not set {} in {} live", key, configId, it) }.getOrDefault(false)
        }
    }

    /** Converts a JSON value to the same type as the property's [current] value, or null when it cannot. */
    private fun convert(value: JsonElement, current: Any?): Any? {
        val primitive = value as? JsonPrimitive ?: return null
        return when (current) {
            is Boolean -> primitive.booleanOrNull
            is Int -> primitive.intOrNull ?: primitive.doubleOrNull?.toInt()
            is Long -> primitive.longOrNull
            is Float -> primitive.floatOrNull
            is Double -> primitive.doubleOrNull
            is String -> primitive.content.takeIf { primitive.isString }
            is Enum<*> -> current.declaringJavaClass.enumConstants.firstOrNull { it.name == primitive.content }
            else -> null
        }
    }
}
