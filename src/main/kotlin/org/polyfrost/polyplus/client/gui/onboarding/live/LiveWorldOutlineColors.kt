package org.polyfrost.polyplus.client.gui.onboarding.live

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.polyplus.client.features.BlockHighlightPresets

internal fun outlinePresetColors(name: String): List<Int> = runCatching {
    val root = JsonParser.parseString(BlockHighlightPresets.presetJson(name)).asJsonObject
    val sources = buildList {
        if (root.flag("enableModRendering", true)) {
            OUTLINE_LAYERS.forEach { key ->
                val layer = root.getAsJsonObject(key) ?: return@forEach
                if (layer.flag("enabled", false)) layer.getAsJsonObject("color")?.let(::add)
            }
            if (root.flag("fillEnabled", false)) root.getAsJsonObject("fillCol")?.let(::add)
        }
    }
    sources.flatMap { color -> COLOR_KEYS.mapNotNull { color.number(it) } }
        .map { OPAQUE or (it and RGB) }
        .distinct()
}.getOrDefault(emptyList())

private fun JsonObject.flag(key: String, default: Boolean): Boolean =
    get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default

private fun JsonObject.number(key: String): Int? =
    get(key)?.takeIf { it.isJsonPrimitive }?.asInt

private val OUTLINE_LAYERS = listOf("primary", "secondary", "tertiary")
private val COLOR_KEYS = listOf("col1", "col2")
private const val OPAQUE = 0xFF shl 24
private const val RGB = 0xFFFFFF
