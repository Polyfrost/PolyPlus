package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

/**
 * Turns another client's hitbox settings into PolyHitbox config values.
 *
 * PolyHitbox has a `DEFAULT` category plus one per entity type (`PLAYER`, `MOB`, ...). An entity category uses its
 * own colors and thickness only with `<CATEGORY>_overwriteVisuals`, and its own show condition only with
 * `<CATEGORY>_overwriteLogic`. Newer versions nest each category and use one `_override` flag.
 */
internal object HitboxSettingsTranslator {
    private const val ALWAYS = 0
    private const val NEVER = 3

    // Badlion's entity group name -> PolyHitbox category.
    private val badlionCategories = mapOf(
        "player" to "PLAYER",
        "monster" to "MONSTER",
        "animal" to "MOB",
        "itemDrop" to "ITEM",
        "itemFrame" to "FRAMES",
        "projectile" to "PROJECTILE",
        "fireball" to "FIREBALL",
        "witherskull" to "WITHER_SKULL",
        "arrow" to "ARROW",
        "xporb" to "XP",
    )

    // NoRisk's group name -> PolyHitbox categories; its MOBS group covers both passive mobs and monsters.
    private val noRiskCategories = mapOf(
        "SELF" to listOf("SELF"),
        "PLAYER" to listOf("PLAYER"),
        "MOBS" to listOf("MOB", "MONSTER"),
        "ITEM" to listOf("ITEM"),
        "PROJECTILE" to listOf("PROJECTILE"),
        "EXPERIENCE" to listOf("XP"),
    )

    /** Lunar's player style applies to both other players and the local player. */
    fun fromLunar(options: JsonObject?): Map<String, JsonElement> = buildMap {
        val color = (options?.get("hitboxPlayerLineColor") as? JsonObject)?.get("value")
            ?.jsonPrimitive?.content?.toIntOrNull()
        for (category in listOf("PLAYER", "SELF")) {
            // Older PolyHitbox versions keep separate visual overrides at the root.
            put("${category}_overwriteVisuals", JsonPrimitive(true))
            color?.let { put("${category}_outlineColor", rgba(it)) }
            // Self defaults to Never, so a visual override alone cannot make it visible.
            put("${category}_overwriteLogic", JsonPrimitive(true))
            put("${category}_showCondition", JsonPrimitive(ALWAYS))
        }
    }.withCategoryPaths()

    /**
     * Badlion saves every hitbox setting, defaults included. Per-entity colors and sizes are only carried over
     * where they differ from the main ones, so PolyHitbox's general settings keep applying everywhere else.
     */
    fun fromBadlion(settings: JsonObject?): Map<String, JsonElement> = buildMap {
        if (settings == null) return@buildMap
        val mainColor = settings.badlionColor("main")
        val mainSize = settings.badlionSize("main")
        mainColor?.let { put("DEFAULT_outlineColor", rgba(it)) }
        mainSize?.let { put("DEFAULT_outlineThickness", JsonPrimitive(thickness(it))) }

        badlionCategories.forEach { (group, category) ->
            settings.ignoringCase("${group}HitboxesEnabled")?.flag()?.let { visible ->
                put("${category}_overwriteLogic", JsonPrimitive(true))
                put("${category}_showCondition", JsonPrimitive(if (visible) ALWAYS else NEVER))
            }
            val color = settings.badlionColor(group)?.takeIf { it != mainColor }
            val size = settings.badlionSize(group)?.takeIf { it != mainSize }
            if (color != null || size != null) {
                put("${category}_overwriteVisuals", JsonPrimitive(true))
                put("${category}_outlineColor", rgba(color ?: mainColor ?: WHITE))
                put("${category}_outlineThickness", JsonPrimitive(thickness(size ?: mainSize ?: BADLION_DEFAULT_SIZE)))
            }
        }
    }.withCategoryPaths()

    /** NoRisk keeps a full style per entity group; each becomes that category's own visuals. */
    fun fromNoRisk(settings: JsonObject?): Map<String, JsonElement> = buildMap {
        if (settings == null) return@buildMap
        noRiskCategories.forEach { (group, categories) ->
            val style = settings[group] as? JsonObject ?: return@forEach
            categories.forEach { category ->
                style["visible"]?.jsonPrimitive?.booleanOrNull?.let { visible ->
                    put("${category}_overwriteLogic", JsonPrimitive(true))
                    put("${category}_showCondition", JsonPrimitive(if (visible) ALWAYS else NEVER))
                }
                put("${category}_overwriteVisuals", JsonPrimitive(true))
                style.noRiskColor("multiColor")?.let { put("${category}_outlineColor", rgba(it)) }
                style.noRiskColor("facingColor")?.let { put("${category}_viewRayColor", rgba(it)) }
                style["showLookVector"]?.jsonPrimitive?.booleanOrNull?.let { put("${category}_showViewRay", JsonPrimitive(it)) }
                style["lineWidth"]?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let {
                    put("${category}_outlineThickness", JsonPrimitive(it.coerceIn(MIN_THICKNESS, MAX_THICKNESS)))
                }
            }
        }
    }.withCategoryPaths()

    /** Emit the flat format and the accordion format, so both installed mod generations can apply the patch live. */
    private fun Map<String, JsonElement>.withCategoryPaths(): Map<String, JsonElement> = buildMap {
        putAll(this@withCategoryPaths)
        for ((key, value) in this@withCategoryPaths) {
            val category = key.substringBeforeLast('_')
            val property = key.substringAfterLast('_')
            if (property == "overwriteVisuals" || property == "overwriteLogic") {
                if (value == JsonPrimitive(true)) put("$category.${category}_override", value)
            } else {
                put("$category.$key", value)
            }
        }
    }

    /** OneConfig stores colors as `[r, g, b, a]`. */
    private fun rgba(argb: Int): JsonArray = JsonArray(
        listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF, argb ushr 24).map(::JsonPrimitive),
    )

    /** Badlion's default 2px line looks like vanilla's; PolyHitbox thickness is a multiple of vanilla's line. */
    private fun thickness(badlionSize: Double): Double =
        (badlionSize / BADLION_DEFAULT_SIZE).coerceIn(MIN_THICKNESS, MAX_THICKNESS)

    private fun JsonObject.badlionColor(group: String): Int? {
        val setting = ignoringCase("${group}HitboxColor") as? JsonObject ?: return null
        if (setting["enabled"]?.jsonPrimitive?.booleanOrNull == false) return null
        val color = setting["color"] as? JsonObject ?: return null
        // Badlion stores channels as signed bytes, so -1 is 255.
        fun channel(key: String) = color[key]?.jsonPrimitive?.content?.toIntOrNull()?.and(0xFF)
        val r = channel("red") ?: return null
        val g = channel("green") ?: return null
        val b = channel("blue") ?: return null
        val a = channel("alpha") ?: 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun JsonObject.badlionSize(group: String): Double? =
        (ignoringCase("${group}HitboxSize") as? JsonObject)?.get("value")?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0 }

    private fun JsonObject.noRiskColor(key: String): Int? {
        val color = (this[key] as? JsonObject)?.get("color") as? JsonObject ?: return null
        fun channel(name: String) = color[name]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)?.times(255)?.roundToInt()
        val r = channel("red") ?: return null
        val g = channel("green") ?: return null
        val b = channel("blue") ?: return null
        val a = channel("alpha") ?: 255
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Badlion's key casing is inconsistent (`animalHitBoxesEnabled`, `witherSkullHitboxSize`). */
    private fun JsonObject.ignoringCase(key: String): JsonElement? =
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    private fun JsonElement.flag(): Boolean? =
        ((this as? JsonObject)?.get("value") ?: this).let { (it as? JsonPrimitive)?.booleanOrNull }

    private const val WHITE = -1
    private const val BADLION_DEFAULT_SIZE = 2.0
    private const val MIN_THICKNESS = 0.5
    private const val MAX_THICKNESS = 3.0
}
