//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.bedrock.geometry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.polyplus.client.utils.intAt
import org.polyfrost.polyplus.client.utils.optionalArray
import org.polyfrost.polyplus.client.utils.optionalFloat
import org.polyfrost.polyplus.client.utils.optionalInt
import org.polyfrost.polyplus.client.utils.optionalObject
import org.polyfrost.polyplus.client.utils.optionalString
import org.polyfrost.polyplus.client.utils.toVec2
import org.polyfrost.polyplus.client.utils.toVec3
import org.joml.Vector3f
import java.io.InputStream
import java.io.InputStreamReader

object BedrockGeometryParser {
    private const val DEFAULT_TEXTURE_SIZE = 64
    private const val UNKNOWN_IDENTIFIER = "geometry.unknown"
    private const val NO_PARENT = ""

    fun parse(stream: InputStream): BedrockGeometry {
        val root = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        return parseGeometry(resolveGeometryObject(root))
    }

    private fun resolveGeometryObject(root: JsonObject): JsonObject {
        if (root.has("minecraft:geometry")) {
            val array = root.getAsJsonArray("minecraft:geometry")

            require(array.size() > 0) {
                "minecraft:geometry array is empty"
            }

            return array[0].asJsonObject
        }

        if (root.has("geometry")) {
            val geometry = root.get("geometry")
            if (geometry.isJsonArray)
                return geometry.asJsonArray[0].asJsonObject

            if (geometry.isJsonObject) {
                val obj = geometry.asJsonObject
                if (obj.has("bones"))
                    return obj

                val firstEntry = obj.entrySet().firstOrNull()
                    ?: error("geometry object has no entries")

                return firstEntry.value.asJsonObject
            }
        }

        if (root.has("bones"))
            return root

        error("Unrecognized Bedrock geometry format")
    }

    private fun parseGeometry(geometry: JsonObject): BedrockGeometry {
        val description = parseDescription(geometry.optionalObject("description"))
        val bones = parseBones(geometry.optionalArray("bones"))

        return BedrockGeometry(description, bones)
    }

    private fun parseDescription(description: JsonObject): BedrockGeometryDescription {
        return BedrockGeometryDescription(
            identifier = description.optionalString("identifier", UNKNOWN_IDENTIFIER),
            textureWidth = description.optionalInt("texture_width", DEFAULT_TEXTURE_SIZE),
            textureHeight = description.optionalInt("texture_height", DEFAULT_TEXTURE_SIZE),
            visibleBounds = parseVisibleBounds(description),
        )
    }

    private fun parseVisibleBounds(description: JsonObject): BedrockVisibleBounds {
        return BedrockVisibleBounds(
            width = description.optionalFloat("visible_bounds_width", 0f),
            height = description.optionalFloat("visible_bounds_height", 0f),
            offset = description.optionalArray("visible_bounds_offset").toVec3(),
        )
    }

    private fun parseBones(bonesArray: JsonArray): Map<String, BedrockBone> {
        val bones = LinkedHashMap<String, BedrockBone>()

        for (element in bonesArray) {
            val bone = parseBone(element.asJsonObject)
            bones[bone.name] = bone
        }

        return bones
    }

    private fun parseBone(obj: JsonObject): BedrockBone {
        return BedrockBone(
            name = obj.optionalString("name"),
            pivot = obj.optionalArray("pivot").toVec3(),
            parent = obj.optionalString("parent", NO_PARENT),
            rotation = obj.optionalArray("rotation").toVec3(),
            cubes = obj.getAsJsonArray("cubes")?.map { parseCube(it.asJsonObject) } ?: emptyList(),
        )
    }

    private fun parseCube(obj: JsonObject): BedrockCube {
        return BedrockCube(
            origin = obj.optionalArray("origin").toVec3(),
            size = obj.optionalArray("size").toVec3(Vector3f(1f, 1f, 1f)),
            uv = parseCubeUv(obj.get("uv")),
            inflate = obj.optionalFloat("inflate", 0f),
            pivot = if (obj.has("pivot")) obj.getAsJsonArray("pivot").toVec3() else null,
            rotation = obj.optionalArray("rotation").toVec3(),
            mirror = obj.has("mirror") && obj.get("mirror").asBoolean,
        )
    }

    private fun parseCubeUv(uvElement: JsonElement?): BedrockCubeUv {
        if (uvElement == null || !uvElement.isJsonArray && !uvElement.isJsonObject) {
            return BedrockCubeUv(emptyList(), emptyMap())
        }

        if (uvElement.isJsonArray) {
            val uv = uvElement.asJsonArray
            return BedrockCubeUv(
                box = listOf(uv.intAt(0), uv.intAt(1)),
                faces = emptyMap(),
            )
        }

        val faces = LinkedHashMap<BedrockCubeFace, BedrockFaceUv>()
        for ((name, value) in uvElement.asJsonObject.entrySet()) {
            val face = runCatching { BedrockCubeFace.valueOf(name.uppercase()) }.getOrNull() ?: continue

            val faceObj = value.asJsonObject

            faces[face] = BedrockFaceUv(
                uv = faceObj.optionalArray("uv").toVec2(),
                size = faceObj.optionalArray("uv_size").toVec2(),
            )

        }
        return BedrockCubeUv(box = emptyList(), faces = faces)
    }


}
//?}
