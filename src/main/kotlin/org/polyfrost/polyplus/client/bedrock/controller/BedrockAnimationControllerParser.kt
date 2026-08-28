package org.polyfrost.polyplus.client.bedrock.controller

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.polyfrost.polyplus.client.bedrock.molang.MolangExpr
import org.polyfrost.polyplus.client.bedrock.molang.MolangExpr.Number
import org.polyfrost.polyplus.client.bedrock.molang.MolangParser
import org.polyfrost.polyplus.client.bedrock.molang.MolangStatement
import java.io.InputStream
import java.io.InputStreamReader

object BedrockAnimationControllerParser {
    fun parseStream(stream: InputStream): BedrockAnimationControllerFile {
        val root = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        return BedrockAnimationControllerFile(parseControllers(root))
    }

    fun parseJson(json: String): BedrockAnimationControllerFile = parseStream(json.byteInputStream())

    private fun parseControllers(root: JsonObject): Map<String, BedrockAnimationController> {
        if (!root.has("animation_controllers")) {
            return emptyMap()
        }

        val result = LinkedHashMap<String, BedrockAnimationController>()
        for ((name, element) in root.getAsJsonObject("animation_controllers").entrySet()) {
            runCatching {
                result[name] = parseController(name, element.asJsonObject)
            }
        }

        return result
    }

    private fun parseController(name: String, obj: JsonObject): BedrockAnimationController {
        val states = LinkedHashMap<String, ControllerState>()
        val statesObj = obj.getAsJsonObject("states") ?: JsonObject()
        for ((stateName, stateElement) in statesObj.entrySet()) {
            states[stateName] = parseState(stateName, stateElement.asJsonObject)
        }

        val initialState = obj.get("initial_state")?.takeIf { it.isJsonPrimitive }?.asString
            ?: states.keys.firstOrNull()
            ?: "default"

        return BedrockAnimationController(
            name = name,
            initialState = initialState,
            states = states,
        )
    }

    private fun parseState(name: String, obj: JsonObject): ControllerState {
        return ControllerState(
            name = name,
            animations = parseAnimations(obj.getAsJsonArray("animations")),
            onEntry = readMolangScripts(obj, "on_entry"),
            onExit = readMolangScripts(obj, "on_exit"),
            transitions = parseTransitions(obj.getAsJsonArray("transitions")),
        )
    }

    private fun parseAnimations(array: JsonArray?): List<StateAnimation> {
        if (array == null) {
            return emptyList()
        }

        val result = mutableListOf<StateAnimation>()
        for (element in array) {
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                    result += StateAnimation(element.asString)

                element.isJsonObject -> {
                    val entry = element.asJsonObject.entrySet().firstOrNull() ?: continue
                    val weight = entry.value.takeIf { it.isJsonPrimitive }?.let { primitive ->
                        when {
                            primitive.asJsonPrimitive.isString -> MolangParser.parseExpression(primitive.asString)
                            primitive.asJsonPrimitive.isNumber -> Number(primitive.asDouble)
                            else -> null
                        }
                    } ?: Number(1.0)
                    result += StateAnimation(entry.key, weight)
                }
            }
        }

        return result
    }

    private fun parseTransitions(array: JsonArray?): List<ControllerTransition> {
        if (array == null) {
            return emptyList()
        }

        val result = mutableListOf<ControllerTransition>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val entry = element.asJsonObject.entrySet().firstOrNull() ?: continue
            val condition = readExpression(entry.value)
            result += ControllerTransition(entry.key, condition)
        }

        return result
    }

    private fun readExpression(element: com.google.gson.JsonElement): MolangExpr {
        if (!element.isJsonPrimitive) return Number(0.0)
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isString -> MolangParser.parseExpression(primitive.asString)
            primitive.isNumber -> Number(primitive.asDouble)
            primitive.isBoolean -> Number(if (primitive.asBoolean) 1.0 else 0.0)
            else -> Number(0.0)
        }
    }

    private fun readMolangScripts(obj: JsonObject, field: String): List<MolangStatement> {
        if (!obj.has(field)) {
            return emptyList()
        }

        return when (val element = obj.get(field)) {
            is JsonPrimitive if element.isString -> MolangParser.parseStatementBlock(element.asString)
            is JsonArray -> element.flatMap { item ->
                if (item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                    MolangParser.parseStatementBlock(item.asString)
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
