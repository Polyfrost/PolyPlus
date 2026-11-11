package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.compat.OneConfigHudInstances.Request
import org.polyfrost.polyplus.compat.OneConfigHudInstances.Target
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Translates Badlion mod profiles (`.minecraft/BLClient-Mod-Profiles/<name>.zip`) into OneConfig HUD instances.
 *
 * A profile's `data.json` keeps every HUD's placement in a top-level `boxes` list: a quadrant (a cell of a 3x3
 * screen grid) plus the fraction inside that cell where the HUD's matching point sits (its right edge for a right
 * cell, its center for a center one). Checked against a Badlion 1.8.9 screenshot.
 * On/off state and styling live on each mod's own settings object.
 */
internal object BadlionHudTranslator {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync/Hud")
    private val json = Json

    /** [modKey] is the mod's settings object in `data.json`, which holds `enabled` and its styling. */
    private data class Module(val modKey: String, val target: Target)

    private val modules = with(OneConfigHudInstances) {
        mapOf(
            "fps" to Module("showFPS", FPS),
            "cps" to Module("showCPS", CPS),
            "ping" to Module("showPing", PING),
            "coordinates" to Module("coordinates", COORDINATES),
            "direction" to Module("showDirection", DIRECTION),
            "armor status" to Module("armorStatus", ARMOR),
            "potion status" to Module("potionStatus", POTIONS),
            "keystrokes" to Module("keyStroke", KEYSTROKES),
            "mouse strokes" to Module("mouse", MOUSE_STROKES),
            "combo counter" to Module("comboCounter", COMBO),
            "reach display" to Module("reachDisplay", REACH),
            "clock" to Module("clock", CLOCK),
            "memory" to Module("memoryDisplay", MEMORY),
            "pack display" to Module("resourcePackDisplay", RESOURCE_PACK),
            "item counter" to Module("itemCounter", ITEM_COUNTER),
            "speedometer" to Module("speedometer", SPEED),
            "saturation" to Module("saturation", SATURATION),
            "boss bar" to Module("bossBar", BOSS_BAR),
        )
    }

    /** Badlion quadrant -> OneConfig section and that section's anchor as a screen fraction. */
    private val quadrants = mapOf(
        "TOP_LEFT" to ("TopLeft" to (0.0 to 0.0)),
        "TOP_CENTER" to ("TopCenter" to (0.5 to 0.0)),
        "TOP_RIGHT" to ("TopRight" to (1.0 to 0.0)),
        "MIDDLE_LEFT" to ("CenterLeft" to (0.0 to 0.5)),
        "MIDDLE_CENTER" to ("Center" to (0.5 to 0.5)),
        "MIDDLE_RIGHT" to ("CenterRight" to (1.0 to 0.5)),
        "BOTTOM_LEFT" to ("BottomLeft" to (0.0 to 1.0)),
        "BOTTOM_CENTER" to ("BottomCenter" to (0.5 to 1.0)),
        "BOTTOM_RIGHT" to ("BottomRight" to (1.0 to 1.0)),
    )

    fun import(sources: List<Path>, alreadyImported: Set<String>): Int {
        val requests = requests(sources) ?: return 0
        return OneConfigHudInstances.apply(requests, alreadyImported)
    }

    fun preview(sources: List<Path>): List<String> {
        val requests = requests(sources) ?: return emptyList()
        return requests.filter { request ->
            OneConfigHudInstances.wouldApply(request).also {
                logger.info(
                    "Badlion HUD mapping {} -> {} (enabled={}, section={}, offset={},{}, style={}, importable={})",
                    request.source, request.target.hudClass, request.enabled, request.section ?: "none",
                    request.x, request.y, request.style.keys, it,
                )
            }
        }.distinctBy { it.target.hudClass }.map { it.source.removePrefix("Badlion ") }
    }

    private fun requests(sources: List<Path>): List<Request>? {
        val profile = sources.firstOrNull(::isBadlionProfile) ?: return null
        val root = readProfile(profile) ?: return null
        val (refW, refH) = OneConfigHudInstances.screen()
        return requests(root, refW, refH)
    }

    /** Pure translation of a Badlion profile's `data.json`; [refW]/[refH] are OneConfig's HUD screen size. */
    internal fun requests(root: JsonObject, refW: Double, refH: Double): List<Request> {
        val boxes = root["boxes"] as? JsonArray ?: return emptyList()
        return boxes.mapNotNull { element ->
            runCatching {
                val box = element as? JsonObject ?: return@runCatching null
                val identifier = box["identifier"]?.jsonPrimitive?.content?.lowercase() ?: return@runCatching null
                val module = modules[identifier] ?: return@runCatching null
                val settings = root[module.modKey] as? JsonObject
                val (section, anchor) = box["quadrant"]?.jsonPrimitive?.content?.let(quadrants::get)
                    ?: return@runCatching null
                val x = box.double("xPosition")?.takeIf { it in -0.5..1.5 } ?: return@runCatching null
                val y = box.double("yPosition")?.takeIf { it in -0.5..1.5 } ?: return@runCatching null
                Request(
                    source = "Badlion $identifier",
                    target = module.target,
                    enabled = settings?.get("enabled")?.jsonPrimitive?.booleanOrNull,
                    section = section,
                    // Fractions are resolution independent, so they map straight onto OneConfig's HUD screen.
                    x = (screenFraction(anchor.first, x) - anchor.first) * refW,
                    y = (screenFraction(anchor.second, y) - anchor.second) * refH,
                    style = settings?.style().orEmpty(),
                )
            }.onFailure { logger.debug("Could not read a Badlion HUD box", it) }.getOrNull()
        }
    }

    /**
     * Badlion quadrants are cells of a 3x3 grid, and positions are fractions inside the cell. [anchor] is the
     * section's screen fraction (0, 0.5 or 1), which also picks the cell's column or row (0, 1 or 2).
     */
    private fun screenFraction(anchor: Double, cellFraction: Double): Double = (anchor * 2 + cellFraction) / 3

    /** Only options the user changed (`"set": true`) are carried over. */
    private fun JsonObject.style(): Map<String, JsonElement> = buildMap {
        val graphic = this@style["graphicModeSettings"] as? JsonObject ?: return@buildMap
        (graphic["backgroundColor"] as? JsonObject)?.takeIf { it.isSet() }?.let { background ->
            background["enabled"]?.jsonPrimitive?.booleanOrNull?.let { put("showBackground", JsonPrimitive(it)) }
            background.argb()?.let { put("bgColor", JsonPrimitive(it)) }
        }
        (graphic["textColor"] as? JsonObject)?.takeIf { it.isSet() }?.argb()?.let { put("textColor", JsonPrimitive(it)) }
        (graphic["textShadow"] as? JsonObject)?.takeIf { it.isSet() }?.get("value")?.jsonPrimitive?.booleanOrNull?.let {
            put("showShadow", JsonPrimitive(it))
        }
    }

    private fun JsonObject.isSet() = this["set"]?.jsonPrimitive?.booleanOrNull == true

    /** Badlion colors are signed bytes (`-1` is 255); OneConfig stores packed ARGB ints. */
    private fun JsonObject.argb(): Int? {
        val color = this["color"] as? JsonObject ?: return null
        fun channel(key: String) = color[key]?.jsonPrimitive?.content?.toIntOrNull()?.and(0xFF)
        val a = channel("alpha") ?: 255
        val r = channel("red") ?: return null
        val g = channel("green") ?: return null
        val b = channel("blue") ?: return null
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun readProfile(path: Path): JsonObject? = runCatching {
        ZipFile(path.toFile()).use { zip ->
            val entry = zip.getEntry("data.json") ?: return@use null
            zip.getInputStream(entry).bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }
        }
    }.onFailure { logger.warn("Could not read Badlion profile {}", path, it) }.getOrNull()

    fun isBadlionProfile(path: Path): Boolean =
        path.fileName?.toString()?.endsWith(".zip") == true &&
            path.parent?.fileName?.toString() == "BLClient-Mod-Profiles"

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
