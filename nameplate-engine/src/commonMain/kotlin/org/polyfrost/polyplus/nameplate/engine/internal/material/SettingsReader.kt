package org.polyfrost.polyplus.nameplate.engine.internal.material

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.limit.MaterialLimits
import org.polyfrost.polyplus.nameplate.engine.internal.material.MaterialMolang
import org.polyfrost.polyplus.nameplate.engine.internal.palette.ColorRef
import org.polyfrost.polyplus.nameplate.engine.internal.palette.Palette

internal class GraphContext(
    val palette: Palette,
    val params: Set<String>,
    val sprites: (String) -> MaterialSprite?,
    val textures: (String) -> Boolean,
    val molang: MaterialMolang?,
)

internal class SettingsReader(
    private val values: Map<String, JsonElement>,
    private val path: String,
    val context: GraphContext,
    private val issues: MutableList<BundleIssue>,
) {
    val settings: NodeSettings = NodeSettings()
    private val seen = HashSet<String>()

    fun report(
        name: String,
        message: String,
        code: IssueCode? = null,
        values: Map<String, Double> = emptyMap(),
        names: Map<String, String> = emptyMap(),
    ) {
        val at = "$path.$name"
        issues += BundleIssue(at, message, code ?: IssueCode.of(at), values, names)
    }

    fun int(name: String, range: IntRange, default: Int): Int {
        val number = number(name) ?: return default
        val whole = number.toInt()
        val valid = whole.toDouble() == number && whole in range
        if (!valid) {
            report(name, "must be a whole number from ${range.first} to ${range.last}")
        }
        return if (valid) whole else default
    }

    fun pair(name: String, range: IntRange, default: IntArray): IntArray {
        seen += name
        val element = values[name] ?: return default
        val parts = (element as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.doubleOrNull }
        val whole = parts?.mapNotNull { part -> part?.toInt()?.takeIf { it.toDouble() == part && it in range } }
        val valid = whole?.size == 2 && parts.size == 2
        if (!valid) {
            report(name, "must be [x, y], whole numbers from ${range.first} to ${range.last}")
        }
        return if (valid) whole.toIntArray() else default
    }

    fun choice(name: String, options: List<String>, default: Int): Int {
        val value = text(name) ?: return default
        val index = options.indexOf(value)
        if (index < 0) {
            report(name, "must be one of ${options.joinToString()}")
        }
        return if (index < 0) default else index
    }

    fun text(name: String): String? {
        seen += name
        val element = values[name] ?: return null
        val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (text == null) {
            report(name, "must be a string")
        }
        return text
    }

    fun required(name: String): String? {
        val value = text(name)
        if (value == null && name !in values) {
            report(name, "is required", IssueCode.NODE_REQUIRED)
        }
        return value
    }

    fun present(name: String): Boolean = name in values

    fun slot(name: String) {
        val value = required(name) ?: return
        val ref = ColorRef.parse(value) as? ColorRef.Slot
        val index = ref?.let { context.palette.slots.indexOfFirst { slot -> slot.id == it.id } } ?: -1
        when {
            ref == null -> report(name, "must be a slot or slot[tone]")
            index < 0 -> report(name, "no slot '${ref.id}'")
            ref.tone >= context.palette.slots[index].size -> report(name, "slot '${ref.id}' has no tone ${ref.tone}")
            else -> {
                settings.slot = index
                settings.tone = ref.tone
                settings.tones = context.palette.slots[index].size
            }
        }
    }

    fun values(name: String): DoubleArray? {
        seen += name
        val element = values[name]
        val parsed = element?.let(Literals::parse)
        if (parsed == null) {
            report(name, "must be a number or a list of up to 4 numbers, each within ${MaterialLimits.MAX_LITERAL}")
        }
        return parsed
    }

    fun finish() {
        for (key in values.keys) {
            if (key !in seen) {
                report(key, "is not a setting of this node", IssueCode.NODE_SETTING)
            }
        }
    }

    private fun number(name: String): Double? {
        seen += name
        val element = values[name] ?: return null
        val number = (element as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        if (number == null) {
            report(name, "must be a number")
        }
        return number
    }
}
