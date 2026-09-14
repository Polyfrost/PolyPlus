package org.polyfrost.polyplus.client.emotes.conditions

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.InputStream
import java.io.InputStreamReader

internal object EmoteRulesParser {
    fun parseStream(stream: InputStream): Map<String, EmoteRules> {
        val root = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        val animations = root.getAsJsonObject("animations") ?: return emptyMap()

        val result = LinkedHashMap<String, EmoteRules>()
        for ((animationName, element) in animations.entrySet()) {
            if (!element.isJsonObject) {
                continue
            }
            result[animationName] = parseRules(element.asJsonObject)
        }
        return result
    }

    private fun parseRules(entry: JsonObject): EmoteRules {
        val rules = entry.getAsJsonObject("rules") ?: return EmoteRules()
        val defaults = EmoteRules()

        return EmoteRules(
            allowWalking = rules.readBoolean("allow_walking", "allowWalking") ?: defaults.allowWalking,
            allowSprinting = rules.readBoolean("allow_sprinting", "allowSprinting") ?: defaults.allowSprinting,
            allowCrouching = rules.readBoolean("allow_crouching", "allowCrouching") ?: defaults.allowCrouching,
            allowFalling = rules.readBoolean("allow_falling", "allowFalling") ?: defaults.allowFalling,
            allowElytraFlying = rules.readBoolean("allow_elytra_flying", "allowElytraFlying") ?: defaults.allowElytraFlying,
            allowSwimming = rules.readBoolean("allow_swimming", "allowSwimming") ?: defaults.allowSwimming,
            allowVanillaPose = rules.readBoolean("allow_vanilla_pose", "allowVanillaPose") ?: defaults.allowVanillaPose,
        )
    }

    private fun JsonObject.readBoolean(vararg keys: String): Boolean? {
        for (key in keys) {
            if (has(key)) {
                return get(key).asBoolean
            }
        }
        return null
    }
}
