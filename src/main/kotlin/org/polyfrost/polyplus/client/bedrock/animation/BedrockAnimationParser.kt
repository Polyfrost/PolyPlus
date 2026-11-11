package org.polyfrost.polyplus.client.bedrock.animation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import org.apache.logging.log4j.LogManager
import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.molang.BedrockMolang
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangScalar
import org.polyfrost.polyplus.client.bedrock.molang.MolangVector3
import org.polyfrost.polyplus.client.bedrock.molang.molangScripts
import org.polyfrost.polyplus.client.utils.optionalString
import java.io.InputStream
import java.io.InputStreamReader

object BedrockAnimationParser {
    private const val TICKS_PER_SECOND = BedrockConstants.TICKS_PER_SECOND

    fun parseStream(stream: InputStream): BedrockAnimationFile {
        val root = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        return BedrockAnimationFile(parseAnimations(root))
    }

    private fun parseAnimations(root: JsonObject): Map<String, BedrockAnimation> {
        val animationsObject = when {
            root.has("animations") -> root.getAsJsonObject("animations")
            root.has("animation_length") || root.has("bones") -> {
                val wrapped = JsonObject()
                wrapped.add("animation", root)
                wrapped
            }

            else -> error("Unrecognized Bedrock animation format")
        }

        val result = LinkedHashMap<String, BedrockAnimation>()
        for ((name, element) in animationsObject.entrySet()) {
            runCatching {
                result[name] = parseAnimation(name, element.asJsonObject)
            }.onFailure { ex ->
                LogManager.getLogger("BedrockAnimationParser")
                    .warn("Failed to parse animation {}", name, ex)
            }
        }

        return result
    }

    private fun parseAnimation(name: String, obj: JsonObject): BedrockAnimation {
        val animationLength = obj.get("animation_length")
        val lengthTicks = if (animationLength != null)
            readDurationSeconds(animationLength) * TICKS_PER_SECOND
        else
            0f

        val molang = BedrockMolang()
        val boneAnimations = parseBones(obj.getAsJsonObject("bones"), molang)
        val resolvedLength = if (lengthTicks > 0f) {
            lengthTicks
        } else {
            calculateLength(boneAnimations)
        }

        return BedrockAnimation(
            name = name,
            lengthTicks = resolvedLength,
            loop = readLoopMode(obj),
            initialize = obj.molangScripts("initialize", molang),
            preAnimation = obj.molangScripts("pre_animation", molang),
            boneAnimations = boneAnimations,
        )
    }

    private fun readLoopMode(obj: JsonObject): LoopMode {
        val loop = obj.get("loop") ?: return LoopMode.ONCE

        return when {
            loop.isJsonPrimitive && loop.asJsonPrimitive.isBoolean -> {
                if (loop.asBoolean)
                    LoopMode.LOOP
                else
                    LoopMode.ONCE
            }

            loop.isJsonPrimitive && loop.asJsonPrimitive.isString -> {
                when (loop.asString.lowercase()) {
                    "true" -> LoopMode.LOOP
                    "hold_on_last_frame", "hold" -> LoopMode.HOLD_LAST
                    else -> LoopMode.ONCE
                }
            }

            else -> LoopMode.ONCE
        }
    }

    private fun parseBones(bonesObj: JsonObject?, molang: BedrockMolang): Map<String, BoneAnimation> {
        if (bonesObj == null) {
            return emptyMap()
        }

        val result = LinkedHashMap<String, BoneAnimation>()
        for ((boneName, element) in bonesObj.entrySet()) {
            val boneObj = element.asJsonObject
            result[boneName] = BoneAnimation(
                rotation = parseVectorChannel(boneObj.get("rotation"), Vector3f(), isRotation = true, molang),
                position = parseVectorChannel(boneObj.get("position"), Vector3f(), isRotation = false, molang),
                scale = parseVectorChannel(boneObj.get("scale"), Vector3f(1f, 1f, 1f), isRotation = false, molang),
            )
        }

        return result
    }

    private fun parseVectorChannel(
        element: JsonElement?,
        default: Vector3f,
        isRotation: Boolean,
        molang: BedrockMolang,
    ): VectorChannel {
        if (element == null) {
            return VectorChannel.empty()
        }

        when (element) {
            is JsonPrimitive, is JsonArray -> {
                return VectorChannel.keyframed(
                    MolangKeyframeTrack.of(
                        listOf(Keyframe(0f, readVector(element, default, isRotation, molang))),
                    ),
                )
            }

            is JsonObject -> {
                when {
                    element.has("vector") -> {
                        return VectorChannel.keyframed(
                            MolangKeyframeTrack.of(
                                listOf(
                                    Keyframe(
                                        0f,
                                        readVector(element.get("vector"), default, isRotation, molang),
                                        readEasing(element),
                                    ),
                                ),
                            ),
                        )
                    }

                    element.has("value") -> {
                        val value = readComponentExpression(element.get("value"), molang)
                        return VectorChannel.keyframed(
                            MolangKeyframeTrack.of(
                                listOf(Keyframe(0f, MolangVector3(value, MolangScalar.ZERO, MolangScalar.ZERO, isRotation))),
                            ),
                        )
                    }

                    else -> {
                        if (element.entrySet().all { isAnimTimeKey(it.key) }) {
                            val frame = element.entrySet().first().value
                            return VectorChannel.procedural(readVectorFrame(frame, default, isRotation, molang))
                        }

                        val keyframes = mutableListOf<Keyframe<MolangVector3>>()
                        for ((timeKey, frameElement) in element.entrySet()) {
                            if (isAnimTimeKey(timeKey)) {
                                continue
                            }

                            val time = readTimestamp(timeKey)
                            keyframes += Keyframe(
                                timeTicks = time * TICKS_PER_SECOND,
                                valuePre = readVectorFrame(frameElement, default, isRotation, molang, preferPre = true),
                                valuePost = readVectorFrame(frameElement, default, isRotation, molang, preferPre = false),
                                easing = if (frameElement is JsonObject) readEasing(frameElement) else EasingMode.LINEAR,
                            )
                        }

                        return VectorChannel.keyframed(MolangKeyframeTrack.of(keyframes))
                    }
                }
            }

            else -> return VectorChannel.empty()
        }
    }

    private fun readVectorFrame(
        frameElement: JsonElement,
        default: Vector3f,
        isRotation: Boolean,
        molang: BedrockMolang,
        preferPre: Boolean = false,
    ): MolangVector3 {
        return when (frameElement) {
            is JsonObject -> {
                val sideKey = if (preferPre) "pre" else "post"
                val otherKey = if (preferPre) "post" else "pre"
                val vectorElement = frameElement.get(sideKey)
                    ?: frameElement.get("vector")
                    ?: frameElement.get("value")
                    ?: frameElement.get(otherKey)
                    ?: frameElement

                readVector(vectorElement, default, isRotation, molang)
            }

            else -> readVector(frameElement, default, isRotation, molang)
        }
    }

    private fun readTimestamp(key: String): Float {
        key.toFloatOrNull()?.let { return it }
        if (isAnimTimeKey(key)) {
            return 0f
        }

        return runCatching {
            BedrockMolang().expression(key).evaluate(MolangContext.forAnimation(0f)).toFloat()
        }.getOrDefault(0f)
    }

    private fun isAnimTimeKey(key: String): Boolean {
        val normalized = key.lowercase()

        return normalized == "query.anim_time" || normalized == "q.anim_time"
    }

    private fun readEasing(obj: JsonObject): EasingMode {
        return EasingMode.of(obj.optionalString("lerp_mode"))
    }

    private fun readVector(element: JsonElement, default: Vector3f, isRotation: Boolean, molang: BedrockMolang): MolangVector3 {
        return when (element) {
            is JsonPrimitive -> {
                val scalar = readComponentExpression(element, molang)
                MolangVector3(scalar, MolangScalar.ZERO, MolangScalar.ZERO, isRotation)
            }

            is JsonArray -> {
                MolangVector3(
                    x = readComponentExpression(element, 0, molang),
                    y = readComponentExpression(element, 1, molang),
                    z = readComponentExpression(element, 2, molang),
                    isRotation = isRotation,
                )
            }

            else -> MolangVector3.constant(default.x, default.y, default.z, isRotation)
        }
    }

    private fun readComponentExpression(array: JsonArray, index: Int, molang: BedrockMolang): MolangScalar {
        if (index >= array.size()) {
            return MolangScalar.ZERO
        }

        return readComponentExpression(array[index], molang)
    }

    private fun readComponentExpression(element: JsonElement, molang: BedrockMolang): MolangScalar {
        if (!element.isJsonPrimitive) {
            return MolangScalar.ZERO
        }

        val primitive = element.asJsonPrimitive
        return when {
            primitive.isString -> molang.expression(primitive.asString)
            primitive.isNumber -> MolangScalar.Constant(primitive.asDouble)
            else -> MolangScalar.ZERO
        }
    }

    private fun readDurationSeconds(element: JsonElement): Float {
        if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive

            if (primitive.isNumber) {
                return primitive.asFloat
            }

            if (primitive.isString) {
                return BedrockMolang().expression(primitive.asString).evaluate(MolangContext.forAnimation(0f)).toFloat()
            }
        }

        return 0f
    }

    private fun calculateLength(bones: Map<String, BoneAnimation>): Float {
        var max = 0f

        for (bone in bones.values) {
            max = maxOf(max, bone.rotation.maxTimeTicks)
            max = maxOf(max, bone.position.maxTimeTicks)
            max = maxOf(max, bone.scale.maxTimeTicks)
        }

        return if (max <= 0f) 1f else max
    }
}
