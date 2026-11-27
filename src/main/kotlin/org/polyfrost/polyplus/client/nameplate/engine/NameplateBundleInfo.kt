package org.polyfrost.polyplus.client.nameplate.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class NameplateBundleInfo(
    val slots: List<String>,
    private val badge: Int,
    private val variantBadges: Map<String, Int>,
) {
    fun badge(variant: String?): Int = variantBadges[variant] ?: badge

    fun slot(key: String): Int {
        val at = slots.indexOf(key)
        if (at >= 0) return at
        return key.toIntOrNull()?.takeIf { it in slots.indices } ?: -1
    }

    companion object {
        const val BADGE_RESERVE: Int = 0
        const val BADGE_CARRIED: Int = 1
        const val BADGE_PLACED: Int = 2

        private const val MANIFEST = "manifest.json"
        private const val PALETTES = "palettes.json"
        private const val MAX_ENTRY_BYTES = 1 shl 20

        val EMPTY: NameplateBundleInfo = NameplateBundleInfo(emptyList(), BADGE_RESERVE, emptyMap())

        fun read(bytes: ByteArray, json: Json): NameplateBundleInfo {
            var manifest: JsonElement? = null
            var palettes: JsonElement? = null
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name
                    if (name != MANIFEST && name != PALETTES) continue
                    val data = zip.readNBytes(MAX_ENTRY_BYTES)
                    val parsed = runCatching { json.parseToJsonElement(data.decodeToString()) }.getOrNull()
                    if (name == MANIFEST) manifest = parsed else palettes = parsed
                }
            }
            val root = manifest as? JsonObject
            val base = placement(root?.get("badge"), BADGE_RESERVE)
            val variants = root?.get("variants") as? JsonObject
            val variantBadges = variants.orEmpty().mapValues { (_, variant) ->
                placement((variant as? JsonObject)?.get("badge"), base)
            }
            val slots = ((palettes as? JsonObject)?.get("slots") as? JsonArray)
                ?.map { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull.orEmpty() }
                .orEmpty()
            return NameplateBundleInfo(slots, base, variantBadges)
        }

        private fun placement(badge: JsonElement?, fallback: Int): Int {
            val value = ((badge as? JsonObject)?.get("placement") as? JsonPrimitive)?.contentOrNull ?: return fallback
            return when (value) {
                "carried" -> BADGE_CARRIED
                "placed" -> BADGE_PLACED
                "reserve" -> BADGE_RESERVE
                else -> fallback
            }
        }
    }
}
