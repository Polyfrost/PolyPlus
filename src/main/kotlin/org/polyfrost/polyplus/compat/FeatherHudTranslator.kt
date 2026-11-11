package org.polyfrost.polyplus.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.compat.OneConfigHudInstances.Request
import org.polyfrost.polyplus.compat.OneConfigHudInstances.Target
import java.nio.file.Files
import java.nio.file.Path

/**
 * Translates Feather HUD modules (as shipped inside Dawn) into OneConfig HUD instances.
 *
 * Feather saves only `hudRelativeX/Y`: GUI-pixel offsets from a fixed, per-module anchor, measured to the HUD's
 * matching point (left edge, center or right edge). Defaults are not saved, so a missing `enabled` means the
 * module's default state.
 */
internal object FeatherHudTranslator {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync/Hud")
    private val json = Json

    /** [anchor] is a OneConfig section name; null falls back to the sign of the offsets. */
    private data class Module(val target: Target, val anchor: String?, val enabledByDefault: Boolean = false)

    private val modules = with(OneConfigHudInstances) {
        mapOf(
            // Anchors checked against a Dawn 1.8.9 screenshot.
            "fps" to Module(FPS, "TopLeft", enabledByDefault = true),
            "coordinates" to Module(COORDINATES, "TopLeft"),
            "cps" to Module(CPS, "TopLeft"),
            "ping" to Module(PING, "TopRight", enabledByDefault = true),
            "playtime" to Module(PLAYTIME, "TopRight"),
            "direction" to Module(DIRECTION, "TopCenter"),
            "keystrokes" to Module(KEYSTROKES, "BottomCenter"),
            "mousestrokes" to Module(MOUSE_STROKES, "TopCenter"),
            // Anchors below are inferred from the offsets' signs.
            "comboDisplay" to Module(COMBO, null),
            "packdisplay" to Module(RESOURCE_PACK, null),
            "armorStatus" to Module(ARMOR, null),
            "potionEffects" to Module(POTIONS, null, enabledByDefault = true),
            "reachDisplay" to Module(REACH, null),
            "time" to Module(CLOCK, null),
            "serverAddress" to Module(SERVER, null),
            "itemCounter" to Module(ITEM_COUNTER, null),
            "tps" to Module(TPS, null),
            "speedMeter" to Module(SPEED, null),
            "systemresources" to Module(MEMORY, null),
            "scoreboard" to Module(SCOREBOARD, null, enabledByDefault = true),
            "bossBar" to Module(BOSS_BAR, null, enabledByDefault = true),
            "tablist" to Module(TAB_LIST, null, enabledByDefault = true),
        )
    }

    fun import(sources: List<Path>, alreadyImported: Set<String>): Int {
        val requests = requests(sources, scaled = true) ?: return 0
        return OneConfigHudInstances.apply(requests, alreadyImported)
    }

    fun preview(sources: List<Path>): List<String> {
        val requests = requests(sources, scaled = false) ?: return emptyList()
        return requests.filter { request ->
            OneConfigHudInstances.wouldApply(request).also {
                logger.info(
                    "Feather HUD mapping {} -> {} (enabled={}, anchor={}, offset={},{}, importable={})",
                    request.source, request.target.hudClass, request.enabled, request.section ?: "none",
                    request.x, request.y, it,
                )
            }
        }.distinctBy { it.target.hudClass }.map { it.source.removePrefix("Feather ") }
    }

    private fun requests(sources: List<Path>, scaled: Boolean): List<Request>? {
        val profile = selectedProfile(sources) ?: return null
        val root = runCatching { json.parseToJsonElement(Files.readString(profile)).jsonObject }
            .onFailure { logger.warn("Could not read Feather profile {}", profile, it) }
            .getOrNull() ?: return null
        if (scaled) logger.info("Importing Feather HUD layout from {}", profile)
        val (factorX, factorY) = if (scaled) {
            OneConfigHudInstances.guiPixelFactors(sources, "Feather", guiScaleNear(profile))
        } else {
            1.0 to 1.0
        }

        return requests(root, factorX, factorY)
    }

    /** Pure translation of a Feather profile; [factorX]/[factorY] turn Feather GUI pixels into HUD units. */
    internal fun requests(root: JsonObject, factorX: Double, factorY: Double): List<Request> =
        root.mapNotNull { (key, value) ->
            val module = modules[key] ?: return@mapNotNull null
            val settings = value as? JsonObject ?: return@mapNotNull null
            val x = settings.number("hudRelativeX")
            val y = settings.number("hudRelativeY")
            val placed = x != null && y != null
            val enabled = settings["enabled"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
                ?: module.enabledByDefault.takeIf { placed }
            val request = Request(source = "Feather $key", target = module.target, enabled = enabled)
            if (x == null || y == null) return@mapNotNull request
            request.copy(
                section = module.anchor ?: inferredSection(x, y),
                x = x * factorX,
                y = y * factorY,
            )
        }

    /** Positive offsets are measured from the left/top edge, negative ones from the right/bottom edge. */
    private fun inferredSection(x: Double, y: Double): String =
        (if (y < 0) "Bottom" else "Top") + (if (x < 0) "Right" else "Left")

    /** Uses the profile named in `current.profile`, falling back to the newest one found. */
    private fun selectedProfile(sources: List<Path>): Path? {
        val profiles = sources.filter(::isFeatherProfile)
        if (profiles.isEmpty()) return null
        val current = profiles.first().parent?.parent?.resolve("current.profile")
            ?.let { runCatching { Files.readString(it).trim() }.getOrNull() }
        return profiles.firstOrNull { it.fileName.toString() == "$current.json" } ?: profiles.first()
    }

    /** Feather keeps its own copy of the game options next to its configuration folder. */
    private fun guiScaleNear(profile: Path): Int? {
        val feather = profile.parent?.parent?.parent ?: return null
        return listOf(feather.resolve("mc_options_v2.txt"), feather.resolve("mc_options_v1.txt"), feather.resolveSibling("options.txt"))
            .firstNotNullOfOrNull { path ->
                runCatching {
                    Files.readAllLines(path).firstOrNull { it.startsWith("guiScale:") }
                        ?.substringAfter(':')?.trim()?.toIntOrNull()
                }.getOrNull()
            }
    }

    fun isFeatherProfile(path: Path): Boolean =
        path.fileName?.toString()?.endsWith(".json") == true &&
            path.parent?.fileName?.toString() == "profiles" &&
            path.parent?.parent?.fileName?.toString() == "configuration" &&
            path.parent?.parent?.parent?.fileName?.toString() == "feather"

    private fun JsonObject.number(key: String): Double? =
        this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
