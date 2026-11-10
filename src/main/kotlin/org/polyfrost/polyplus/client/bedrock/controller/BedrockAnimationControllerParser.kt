package org.polyfrost.polyplus.client.bedrock.controller

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.polyplus.client.bedrock.molang.BedrockMolang
import org.polyfrost.polyplus.client.bedrock.molang.MolangScalar
import org.polyfrost.polyplus.client.bedrock.molang.molangScripts
import java.io.InputStream
import java.io.InputStreamReader

object BedrockAnimationControllerParser {
    fun parseStream(stream: InputStream): BedrockAnimationControllerFile {
        val root = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        return BedrockAnimationControllerFile(parseControllers(root))
    }

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
        val molang = BedrockMolang()
        val states = LinkedHashMap<String, ControllerState>()
        val statesObj = obj.getAsJsonObject("states") ?: JsonObject()
        for ((stateName, stateElement) in statesObj.entrySet()) {
            states[stateName] = parseState(stateName, stateElement.asJsonObject, molang)
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

    private fun parseState(name: String, obj: JsonObject, molang: BedrockMolang): ControllerState {
        return ControllerState(
            name = name,
            animations = parseAnimations(obj.getAsJsonArray("animations"), molang),
            onEntry = obj.molangScripts("on_entry", molang),
            onExit = obj.molangScripts("on_exit", molang),
            transitions = parseTransitions(obj.getAsJsonArray("transitions"), molang),
        )
    }

    private fun parseAnimations(array: JsonArray?, molang: BedrockMolang): List<StateAnimation> {
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
                            primitive.asJsonPrimitive.isString -> molang.expression(primitive.asString)
                            primitive.asJsonPrimitive.isNumber -> MolangScalar.Constant(primitive.asDouble)
                            else -> null
                        }
                    } ?: MolangScalar.ONE
                    result += StateAnimation(entry.key, weight)
                }
            }
        }

        return result
    }

    private fun parseTransitions(array: JsonArray?, molang: BedrockMolang): List<ControllerTransition> {
        if (array == null) {
            return emptyList()
        }

        val result = mutableListOf<ControllerTransition>()
        for (element in array) {
            if (!element.isJsonObject) continue
            val entry = element.asJsonObject.entrySet().firstOrNull() ?: continue
            val condition = readExpression(entry.value, molang)
            result += ControllerTransition(entry.key, condition)
        }

        return result
    }

    private fun readExpression(element: JsonElement, molang: BedrockMolang): MolangScalar {
        if (!element.isJsonPrimitive) return MolangScalar.ZERO
        val primitive = element.asJsonPrimitive
        return when {
            primitive.isString -> molang.expression(primitive.asString)
            primitive.isNumber -> MolangScalar.Constant(primitive.asDouble)
            primitive.isBoolean -> if (primitive.asBoolean) MolangScalar.ONE else MolangScalar.ZERO
            else -> MolangScalar.ZERO
        }
    }
}
