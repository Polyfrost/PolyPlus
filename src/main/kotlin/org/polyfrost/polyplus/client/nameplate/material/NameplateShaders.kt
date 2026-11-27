package org.polyfrost.polyplus.client.nameplate.material

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

class NameplateSampler(val name: String, val unit: Int, val kind: Int, val index: Int, val texture: String) {
    companion object {
        const val KIND_BUNDLE: Int = 0
        const val KIND_VANILLA: Int = 1
        const val KIND_ATLAS: Int = 2
    }
}

class NameplateLooseUniform(val name: String, val ints: Boolean, val word: Int, val count: Int)

class NameplateShader(
    val key: String,
    val material: Int,
    val vertex: String,
    val fragment: String,
    val samplers: Array<NameplateSampler>,
    val uniformWords: Int,
    val additive: Boolean,
    val depthPrepass: Boolean,
    val loose: Array<NameplateLooseUniform>,
    val supported: Boolean,
) {
    val readsScreen: Boolean = fragment.contains(SCREEN_SIZE)
    val atlasSamplers: Boolean = samplers.any { it.kind == NameplateSampler.KIND_ATLAS }

    companion object {
        const val SCREEN_SIZE: String = "ScreenSize"
    }
}

class NameplateShaderSet(val dialect: Int, val shaders: List<NameplateShader>) {
    val materialCount: Int = (shaders.maxOfOrNull { it.material } ?: -1) + 1

    fun gameTextures(): List<String> {
        val names = LinkedHashSet<String>()
        for (shader in shaders) {
            for (sampler in shader.samplers) {
                if (sampler.kind != NameplateSampler.KIND_BUNDLE) names += sampler.texture
            }
        }
        return names.toList()
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        fun parse(dialect: Int, json: String): NameplateShaderSet {
            val root = JSON.parseToJsonElement(json) as? JsonObject ?: return NameplateShaderSet(dialect, emptyList())
            val list = root["shaders"] as? JsonArray ?: return NameplateShaderSet(dialect, emptyList())
            val shaders = ArrayList<NameplateShader>(list.size)
            for (element in list) {
                val shader = element as? JsonObject ?: continue
                shaders += shader(shader) ?: continue
            }
            return NameplateShaderSet(dialect, shaders)
        }

        private fun shader(o: JsonObject): NameplateShader? {
            val key = o.string("key") ?: return null
            val material = o.int("material") ?: return null
            val samplers = (o["samplers"] as? JsonArray).orEmpty().mapNotNull { sampler(it as? JsonObject) }
            val loose = (o["loose_uniforms"] as? JsonArray).orEmpty().mapNotNull { loose(it as? JsonObject) }
            val unsupported = (o["unsupported"] as? JsonArray).orEmpty().isNotEmpty()
            return NameplateShader(
                key,
                material,
                o.string("vertex").orEmpty(),
                o.string("fragment").orEmpty(),
                samplers.toTypedArray(),
                o.int("uniform_words") ?: 0,
                o.string("blend") == "additive",
                (o["depth_prepass"] as? JsonPrimitive)?.booleanOrNull == true,
                loose.toTypedArray(),
                !unsupported && samplers.all { it.kind >= 0 },
            )
        }

        private fun sampler(o: JsonObject?): NameplateSampler? {
            o ?: return null
            val name = o.string("name") ?: return null
            val unit = o.int("unit") ?: return null
            val texture = o["texture"] as? JsonObject ?: return null
            return when (texture.string("kind")) {
                "bundle" -> NameplateSampler(name, unit, NameplateSampler.KIND_BUNDLE, texture.int("index") ?: 0, "")
                "vanilla" -> NameplateSampler(name, unit, NameplateSampler.KIND_VANILLA, 0, texture.string("name").orEmpty())
                "atlas" -> NameplateSampler(name, unit, NameplateSampler.KIND_ATLAS, 0, texture.string("name").orEmpty())
                else -> NameplateSampler(name, unit, -1, 0, "")
            }
        }

        private fun loose(o: JsonObject?): NameplateLooseUniform? {
            o ?: return null
            val name = o.string("name") ?: return null
            return NameplateLooseUniform(
                name,
                (o["ints"] as? JsonPrimitive)?.booleanOrNull == true,
                o.int("word") ?: return null,
                o.int("count") ?: 1,
            )
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
    }
}
