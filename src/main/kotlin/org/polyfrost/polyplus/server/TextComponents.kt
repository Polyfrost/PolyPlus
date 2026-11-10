package org.polyfrost.polyplus.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object TextComponents {
    private const val MAX_DEPTH = 32
    private const val MAX_LENGTH = 4096
    private val FORMATTING = Regex("§.?")
    private val PLACEHOLDER = Regex("%(?:(\\d+)\\$)?s")

    fun toPlainText(json: String): String {
        val element = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return clean(json)
        val out = StringBuilder()
        append(element, out, 0)
        return clean(out.toString())
    }

    private fun append(element: JsonElement, out: StringBuilder, depth: Int) {
        if (depth > MAX_DEPTH || out.length > MAX_LENGTH) return
        when (element) {
            is JsonPrimitive -> out.append(element.content)
            is JsonArray -> element.forEach { append(it, out, depth + 1) }
            is JsonObject -> {
                val text = element.string("text")
                val translate = element.string("translate")
                when {
                    text != null -> out.append(text)
                    translate != null -> appendTranslation(element.string("fallback") ?: translate, element["with"] as? JsonArray, out, depth)
                    element.string("keybind") != null -> out.append(element.string("keybind"))
                    element.string("selector") != null -> out.append(element.string("selector"))
                    element["score"] is JsonObject -> (element["score"] as JsonObject).string("value")?.let(out::append)
                }
                (element["extra"] as? JsonArray)?.forEach { append(it, out, depth + 1) }
            }
        }
    }

    private fun appendTranslation(format: String, args: JsonArray?, out: StringBuilder, depth: Int) {
        var next = 0
        var last = 0
        for (match in PLACEHOLDER.findAll(format)) {
            out.append(format, last, match.range.first)
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: next++
            args?.getOrNull(index)?.let { append(it, out, depth + 1) }
            last = match.range.last + 1
        }
        out.append(format, last, format.length)
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun clean(text: String): String = FORMATTING.replace(text, "").take(MAX_LENGTH)
}
