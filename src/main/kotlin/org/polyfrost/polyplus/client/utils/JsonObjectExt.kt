package org.polyfrost.polyplus.client.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.joml.Vector2f
import org.joml.Vector3f

fun JsonElement?.optionalString(key: String, default: String = ""): String =
    this?.asJsonObject?.get(key)?.asString ?: default

fun JsonElement?.optionalFloat(key: String, default: Float = 0f): Float =
    this?.asJsonObject?.get(key)?.asFloat ?: default

fun JsonElement?.optionalInt(key: String, default: Int = 0): Int =
    this?.asJsonObject?.get(key)?.asInt ?: default

fun JsonElement?.optionalObject(key: String, default: JsonObject = JsonObject()): JsonObject =
    this?.asJsonObject?.get(key)?.asJsonObject ?: default

fun JsonElement?.optionalArray(key: String, default: JsonArray = JsonArray()): JsonArray =
    this?.asJsonObject?.get(key)?.asJsonArray ?: default

fun JsonArray?.floatAt(index: Int, default: Float = 0f): Float =
    this?.let {
        if (index < it.size())
            it.get(index).asFloat
        else default
    } ?: default


fun JsonArray?.intAt(index: Int, default: Int = 0): Int =
    this?.let {
        if (index < it.size())
            it.get(index).asInt
        else
            default
    } ?: default

fun JsonArray?.toVec3(default: Vector3f = Vector3f()): Vector3f {
    return Vector3f(floatAt(0, default.x), floatAt(1, default.y), floatAt(2, default.z))
}

fun JsonArray?.toVec2(default: Vector2f = Vector2f()): Vector2f =
    Vector2f(floatAt(0, default.x), floatAt(1, default.y))
