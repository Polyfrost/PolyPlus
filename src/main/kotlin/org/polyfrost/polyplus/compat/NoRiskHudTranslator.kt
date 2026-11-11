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
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.roundToInt

/** Translates NoRisk HUD modules (placement, scale, colors) into OneConfig HUD instances. */
internal object NoRiskHudTranslator {
    private val logger = LogManager.getLogger("PolyPlus/ClientConfigSync/Hud")
    private val json = Json

    private val chat = Target("chatting", "org.polyfrost.chatting.hud.ChatWindowHud", "chat", "chat_window.json")

    private val targets = with(OneConfigHudInstances) {
        mapOf(
            "action bar" to ACTION_BAR,
            "actionbar" to ACTION_BAR,
            "armorstatus" to ARMOR,
            "armor status" to ARMOR,
            "boss bar" to BOSS_BAR,
            "bossbar" to BOSS_BAR,
            "chat" to chat,
            "chat window" to chat,
            "clock" to CLOCK,
            "combo counter" to COMBO,
            "coordinates" to COORDINATES,
            "cps" to CPS,
            "day counter" to DAY,
            "direction hud" to DIRECTION,
            "fps" to FPS,
            "fps counter" to FPS,
            "hot bar" to HOTBAR,
            "hotbar" to HOTBAR,
            "inventory" to INVENTORY,
            "item counter" to ITEM_COUNTER,
            "item tooltip" to ITEM_TOOLTIP,
            "itemtooltip" to ITEM_TOOLTIP,
            "keystrokes" to KEYSTROKES,
            "ping" to PING,
            "potion effects" to POTIONS,
            "potion status" to POTIONS,
            "status effects" to POTIONS,
            "reach display" to REACH,
            "resource pack display" to RESOURCE_PACK,
            "scoreboard" to SCOREBOARD,
            "server" to SERVER,
            "speedometer" to SPEED,
            "spotifyoverlay" to MEDIA,
            "tab" to TAB_LIST,
            "tab list" to TAB_LIST,
            "title" to TITLES,
            "titles" to TITLES,
            "tps" to TPS,
            "uptime" to PLAYTIME,
        )
    }

    private val anchorSections = mapOf(
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
        return requests.filter(OneConfigHudInstances::wouldApply)
            .distinctBy { it.target.hudClass }
            .map { it.source.removePrefix("NoRisk ") }
    }

    private fun requests(sources: List<Path>): List<Request>? {
        // Pick the newest NoRisk profile that actually holds HUD modules.
        val modules = sources.asSequence().filter(::isNoRiskProfile).firstNotNullOfOrNull { source ->
            runCatching { json.parseToJsonElement(Files.readString(source)).jsonObject["modules"] as? JsonArray }
                .onFailure { logger.debug("Could not read NoRisk HUD profile {}", source, it) }
                .getOrNull()
        } ?: return null
        val (refW, refH) = OneConfigHudInstances.screen()
        return requests(modules, refW, refH)
    }

    /** Pure translation of a NoRisk profile's `modules`; [refW]/[refH] are OneConfig's HUD screen size. */
    internal fun requests(modules: JsonArray, refW: Double, refH: Double): List<Request> =
        modules.mapNotNull { element ->
            runCatching {
                val module = element.jsonObject
                val displayName = module["name"]?.jsonPrimitive?.content?.trim() ?: return@runCatching null
                val target = targets[displayName.lowercase()] ?: return@runCatching null
                val values = module["values"] as? JsonArray ?: return@runCatching null
                fun setting(key: String): JsonElement? = values.firstNotNullOfOrNull { value ->
                    val item = value as? JsonObject ?: return@firstNotNullOfOrNull null
                    if (item["name"]?.jsonPrimitive?.content == key) item["value"] else null
                }

                // NoRisk stores positions as fractions of the screen from the anchor's corner.
                val anchor = setting("anchorPosition") as? JsonObject
                val (section, base) = anchor?.get("anchor")?.jsonPrimitive?.content?.let(anchorSections::get)
                    ?: (null to (0.0 to 0.0))
                val x = anchor?.double("relativX")?.takeIf { it in -1.0..2.0 }
                val y = anchor?.double("relativY")?.takeIf { it in -1.0..2.0 }
                val placed = section != null && x != null && y != null

                Request(
                    source = "NoRisk $displayName",
                    target = target,
                    enabled = (setting("isEnabled") as? JsonPrimitive)?.booleanOrNull,
                    section = section.takeIf { placed },
                    x = x?.takeIf { placed }?.let { (it - base.first) * refW },
                    y = y?.takeIf { placed }?.let { (it - base.second) * refH },
                    scale = (setting("hudScale") as? JsonPrimitive)?.content?.toDoubleOrNull(),
                    style = style(setting("background") as? JsonObject, setting("multiColor") as? JsonObject),
                )
            }.onFailure { logger.debug("Could not read a NoRisk HUD element", it) }.getOrNull()
        }

    private fun style(background: JsonObject?, text: JsonObject?): Map<String, JsonElement> = buildMap {
        background?.let {
            it["backgroundType"]?.jsonPrimitive?.content?.let { type -> put("showBackground", JsonPrimitive(type != "BLANK")) }
            (it["color"] as? JsonObject)?.let { color ->
                color.argb()?.let { argb -> put("bgColor", JsonPrimitive(argb)) }
                color["isRainbow"]?.jsonPrimitive?.booleanOrNull?.let { rainbow -> put("bgChroma", JsonPrimitive(rainbow)) }
            }
        }
        text?.let { color ->
            color.argb()?.let { argb -> put("textColor", JsonPrimitive(argb)) }
            color["isRainbow"]?.jsonPrimitive?.booleanOrNull?.let { rainbow -> put("textChroma", JsonPrimitive(rainbow)) }
        }
    }

    /** NoRisk colors are `{"color": {"red": 0..1, ...}}`; OneConfig stores packed ARGB ints. */
    private fun JsonObject.argb(): Int? {
        val color = this["color"] as? JsonObject ?: return null
        fun channel(key: String) = color.double(key)?.coerceIn(0.0, 1.0)?.times(255)?.roundToInt()
        val a = channel("alpha") ?: 255
        val r = channel("red") ?: return null
        val g = channel("green") ?: return null
        val b = channel("blue") ?: return null
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun isNoRiskProfile(path: Path): Boolean =
        path.parent?.fileName?.toString() == "profiles" &&
            generateSequence(path.parent) { it.parent }.any {
                it.fileName?.toString()?.startsWith("NoRiskClient") == true
            } &&
            path.fileName?.toString()?.endsWith(".json") == true

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
}
