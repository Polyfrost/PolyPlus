package org.polyfrost.polyplus.client.cosmetics.assets

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.math.max

internal object AnimationLength {
    private val CHANNELS = arrayOf("rotation", "position", "scale")

    fun seconds(json: String): Float {
        val root = JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject ?: return 0f
        val animations = root.get("animations")?.takeIf { it.isJsonObject }?.asJsonObject
        val first = when {
            animations != null -> animations.entrySet().firstOrNull { it.value.isJsonObject }?.value?.asJsonObject
            root.has("animation_length") || root.has("bones") -> root
            else -> null
        }
        return first?.let(::seconds) ?: 0f
    }

    fun seconds(animation: JsonObject): Float {
        val declared = animation.get("animation_length")?.let(::number) ?: 0f
        if (declared > 0f) return declared
        val bones = animation.get("bones")?.takeIf { it.isJsonObject }?.asJsonObject ?: return 0f
        var longest = 0f
        for ((_, bone) in bones.entrySet()) {
            if (!bone.isJsonObject) continue
            for (channel in CHANNELS) {
                val keys = bone.asJsonObject.get(channel)?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                for ((time, _) in keys.entrySet()) {
                    time.toFloatOrNull()?.let { longest = max(longest, it) }
                }
            }
        }
        return longest
    }

    private fun number(element: JsonElement): Float? {
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asFloat
            primitive.isString -> primitive.asString.trim().toFloatOrNull()
            else -> null
        }
    }
}
