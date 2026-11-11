package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.compat.OneConfigHudInstances.Request
import java.nio.file.Files
import java.nio.file.Path

/** Translates Lunar's HUD modules (placement, scale, colors) into OneConfig HUD instances. */
internal object LunarHudTranslator {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync/Hud")
    private val json = Json

    private val targets = with(OneConfigHudInstances) {
        mapOf(
            "FPS" to FPS,
            "COORDINATES" to COORDINATES,
            "DIRECTION_HUD" to DIRECTION,
            "DAY_COUNTER" to DAY,
            "ARMORSTATUS" to ARMOR,
            "POTION_EFFECTS" to POTIONS,
            "KEYSTROKES" to KEYSTROKES,
            "CPS" to CPS,
            "PING" to PING,
            "MEMORY" to MEMORY,
            "COMBO" to COMBO,
            "REACH_DISPLAY" to REACH,
            "SERVER_ADDRESS" to SERVER,
            "PACK_DISPLAY" to RESOURCE_PACK,
            "CLOCK" to CLOCK,
            "ITEM_COUNTER" to ITEM_COUNTER,
            "MEDIA_PLAYER" to MEDIA,
            "SCOREBOARD" to SCOREBOARD,
            "ACTION_BAR" to ACTION_BAR,
            "TITLES" to TITLES,
            "BOSSBAR" to BOSS_BAR,
        )
    }

    private val anchors = mapOf(
        "topleft" to "TopLeft",
        "topcenter" to "TopCenter",
        "topright" to "TopRight",
        "middleleft" to "CenterLeft",
        "middlecenter" to "Center",
        "center" to "Center",
        "middleright" to "CenterRight",
        "bottomleft" to "BottomLeft",
        "bottomcenter" to "BottomCenter",
        "bottomright" to "BottomRight",
    )

    // Lunar option -> OneConfig HUD property. Lunar only saves options the user changed.
    private val sharedBooleans = mapOf("background" to "showBackground", "textShadow" to "showShadow", "brackets" to "brackets")
    private val sharedColors = mapOf("backgroundColor" to ("bgColor" to "bgChroma"), "textColor" to ("textColor" to "textChroma"))
    private val moduleBooleans = mapOf(
        "COORDINATES" to mapOf("showDirection" to "showDirection", "showAxisLabels" to "showAxis"),
    )

    fun import(sources: List<Path>, alreadyImported: Set<String>): Int {
        val requests = requests(sources, scaled = true) ?: return 0
        return OneConfigHudInstances.apply(requests, alreadyImported)
    }

    fun preview(sources: List<Path>): List<String> {
        val source = sources.firstOrNull(::isLunarModsFile) ?: return emptyList()
        val requests = requests(sources, scaled = false) ?: return emptyList()
        val result = requests.filter { request ->
            OneConfigHudInstances.wouldApply(request).also {
                logger.info(
                    "Lunar HUD mapping {} -> {} (enabled={}, placement={}, style={}, importable={})",
                    request.source, request.target.hudClass, request.enabled,
                    request.section ?: "none", request.style.keys, it,
                )
            }
        }.map { it.source.removePrefix("Lunar ") }.distinct()
        logger.info("Lunar HUD elements eligible for import from {}: {}", source, result.joinToString().ifEmpty { "none" })
        return result
    }

    private fun requests(sources: List<Path>, scaled: Boolean): List<Request>? {
        val source = sources.firstOrNull(::isLunarModsFile) ?: return null
        val root = runCatching { json.parseToJsonElement(Files.readString(source)).jsonObject }
            .onFailure { logger.debug("Could not read Lunar HUD modules from {}", source, it) }
            .getOrNull() ?: return null
        val (factorX, factorY) = if (scaled) OneConfigHudInstances.guiPixelFactors(sources, "Lunar") else 1.0 to 1.0
        return root.mapNotNull { (rawName, value) ->
            val module = value as? JsonObject ?: return@mapNotNull null
            val name = rawName.uppercase()
            val target = targets[name] ?: return@mapNotNull null

            // Some modules (e.g. PING) keep their placement on a child element instead of the module itself.
            val placed = module.takeIf { it.hasPlacement() }
                ?: module.entries.firstNotNullOfOrNull { (key, child) ->
                    (child as? JsonObject)?.takeIf { key.startsWith(name) && it.hasPlacement() }
                }
                ?: module
            val section = placed["position"]?.jsonPrimitive?.content?.let { anchors[it.normalizedAnchor()] }
            val x = placed.number("x")
            val y = placed.number("y")
            // If Lunar saved a HUD placement but omitted its default-on flag, import it as visible.
            val enabled = module["enabled"]?.jsonPrimitive?.booleanOrNull
                ?: (section != null || x != null || y != null).takeIf { it }
            Request(
                source = "Lunar $name",
                target = target,
                enabled = enabled,
                section = section,
                x = x?.times(factorX),
                y = y?.times(factorY),
                scale = module.number("scale") ?: placed.number("scale"),
                style = module.style(name),
            )
        }
    }

    private fun JsonObject.hasPlacement() = containsKey("position") || containsKey("x") || containsKey("y")

    private fun JsonObject.style(name: String): Map<String, JsonElement> = buildMap {
        val options = this@style["options"] as? JsonObject ?: return@buildMap
        (sharedBooleans + moduleBooleans[name].orEmpty()).forEach { (lunarKey, hudKey) ->
            options[lunarKey]?.jsonPrimitive?.booleanOrNull?.let { put(hudKey, JsonPrimitive(it)) }
        }
        sharedColors.forEach { (lunarKey, keys) ->
            val color = options[lunarKey] as? JsonObject ?: return@forEach
            color["value"]?.jsonPrimitive?.content?.toIntOrNull()?.let { put(keys.first, JsonPrimitive(it)) }
            color["chroma"]?.jsonPrimitive?.booleanOrNull?.let { put(keys.second, JsonPrimitive(it)) }
        }
    }

    private fun isLunarModsFile(path: Path): Boolean =
        path.fileName?.toString() == "mods.json" && path.parent?.parent?.fileName?.toString() == "game"

    private fun JsonObject.number(key: String): Double? =
        this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun String.normalizedAnchor(): String = lowercase().filter(Char::isLetter)
}
