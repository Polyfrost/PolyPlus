package org.polyfrost.polyplus.client.emotes

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.EmoteRevision
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.emotes.playback.EmoteScene
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Long as JavaLong
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

internal object EmoteBundles {
    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/emotes")

    private val CAPABILITIES = arrayOf(
        "reactions",
        "palettes",
        "controllers",
        "springs",
        "effects",
        "particles",
        "trails",
        "sounds",
        "custom_colors",
        "rigs",
        "roles",
        "events",
    )

    private const val KIND_EMOTE = "emote"
    private const val MANIFEST = "manifest.json"
    private const val LOOP_ONCE = "once"
    private const val SKIPPED_PREFIX = "._"
    private const val ANIMATION_SUFFIX = ".animation.json"
    private const val HASH_HEX = 64
    private const val IDENTITY_HEX = 16
    private const val NATIVES_PROPERTY = "plusrenderer.natives.dir"
    private val HEX = Regex("[0-9a-fA-F]+")

    fun fromDirectory(cosmeticId: Int, hash: String?, dir: Path): Emote? {
        val bytes = try {
            zip(dir)
        } catch (ex: Exception) {
            LOGGER.error("Could not read emote {} from {}", cosmeticId, dir, ex)
            return null
        }
        val scope = hash?.let { "$cosmeticId/${EmoteRevision(cosmeticId, it).tag}" } ?: "$cosmeticId"
        val id = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$scope/emote")
        val props = Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "cosmetics/$scope/emote_props")
        return load(id, "#$cosmeticId", "emotes/$scope", bytes, identity(hash, bytes), manifest(dir), props, startDelays(dir))
    }

    internal fun startDelays(dir: Path): Map<String, Float> {
        val out = HashMap<String, Float>()
        val files = try {
            Files.walk(dir).use { paths ->
                paths.filter { it.isRegularFile() && it.fileName.toString().lowercase().endsWith(ANIMATION_SUFFIX) }
                    .filter { path -> dir.relativize(path).none { it.toString().startsWith(SKIPPED_PREFIX) } }
                    .toList()
            }
        } catch (ex: Exception) {
            LOGGER.warn("Could not list the animations of emote bundle {}", dir, ex)
            return out
        }
        for (file in files) {
            val animations = try {
                JsonParser.parseString(file.readText()).takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("animations")?.takeIf { it.isJsonObject }?.asJsonObject
            } catch (ex: Exception) {
                LOGGER.warn("Could not read {}", file, ex)
                null
            } ?: continue
            startDelays(animations, out)
        }
        return out
    }

    private fun startDelays(animations: JsonObject, out: MutableMap<String, Float>) {
        for ((name, body) in animations.entrySet()) {
            val delay = body.takeIf { it.isJsonObject }?.asJsonObject?.get("start_delay") ?: continue
            val primitive = delay.takeIf { it.isJsonPrimitive }?.asJsonPrimitive
            val seconds = when {
                primitive == null -> Float.NaN
                primitive.isNumber -> primitive.asFloat
                primitive.isString -> primitive.asString.trim().toFloatOrNull() ?: Float.NaN
                else -> Float.NaN
            }
            out[name] = if (seconds.isNaN()) Float.NaN else seconds.coerceAtLeast(0f)
        }
    }

    fun fromAnimations(json: String, id: (String) -> Identifier): Map<String, Emote> {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not read built-in emote animations", ex)
            return emptyMap()
        }
        val animations = root.get("animations")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, Emote>()
        for ((name, element) in animations.entrySet()) {
            if (!element.isJsonObject) continue
            val single = JsonObject()
            root.get("format_version")?.let { single.add("format_version", it) }
            single.add("animations", JsonObject().apply { add(name, element) })
            val bytes = zipOf(stem(name) + ANIMATION_SUFFIX, single.toString().toByteArray(Charsets.UTF_8))
            val delays = HashMap<String, Float>()
            startDelays(single.getAsJsonObject("animations"), delays)
            load(id(name), name, "emotes/builtin/${stem(name)}", bytes, identity(null, bytes), null, null, delays)?.let { result[name] = it }
        }
        return result
    }

    private fun load(
        id: Identifier,
        label: String,
        name: String,
        bytes: ByteArray,
        identity: Long,
        manifest: JsonObject?,
        propsTexture: Identifier?,
        delays: Map<String, Float>,
    ): Emote? {
        ensureNativesDir()
        val cosmetic = try {
            CosmeticAsset.parse(bytes, CosmeticAsset.MODE_CLIENT, identity, CAPABILITIES)
        } catch (ex: RuntimeException) {
            LOGGER.error("plusrenderer could not parse emote {}", label, ex)
            return null
        } catch (ex: LinkageError) {
            LOGGER.error("plusrenderer is not available to parse emote {}", label, ex)
            return null
        }
        try {
            if (!cosmetic.loaded()) {
                LOGGER.warn("Emote {} did not load (status {}): {}", label, cosmetic.status(), cosmetic.issuesJson())
                return null
            }
            val meta = JsonParser.parseString(cosmetic.metaJson()).asJsonObject
            val kind = meta.get("kind")?.takeIf { it.isJsonPrimitive }?.asString
            if (kind != KIND_EMOTE) {
                LOGGER.warn("Emote {} is a {} bundle", label, kind)
                return null
            }
            val rules = meta.get("rules")?.takeIf { it.isJsonObject }?.asJsonObject
            val vanillaPose = rules?.get("allow_vanilla_pose")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false
            val walking = rule(rules, "allow_walking", false)
            val clips = clips(meta, delays)
            val requirement = EmoteRequirement.of(string(meta, "requirement") ?: manifest?.let { string(it, "requirement") })
            val named = (string(meta, "fallback") ?: manifest?.let { string(it, "fallback") })
                ?.let { name -> clips.indexOfFirst { it.name == name } }
                ?: -1
            val mainIndex = clips.indices.firstOrNull { it != named } ?: 0
            val main = clips.getOrNull(mainIndex)
            val fallback = if (named >= 0 && named != mainIndex) named else Emote.NO_FALLBACK
            val scene = EmoteScene(cosmetic, meta, label, name)
            val texture = if (scene.props != null && propsTexture != null) props(cosmetic, propsTexture, label) else null
            return Emote(
                id,
                scene,
                vanillaPose,
                main?.length ?: 0f,
                roles(meta, clips),
                main?.loop != null && main.loop != LOOP_ONCE,
                requirement,
                fallback,
                texture,
                walking,
                mainIndex,
                rule(rules, "allow_sprinting", false),
                rule(rules, "allow_crouching", false),
                rule(rules, "allow_falling", true),
                rule(rules, "allow_elytra_flying", false),
                rule(rules, "allow_swimming", false),
                main?.delay ?: 0f,
                meta.get("controllers")?.takeIf { it.isJsonArray }?.asJsonArray?.size()?.let { it > 0 } ?: false,
            )
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not set up emote {}", label, ex)
            return null
        } catch (ex: LinkageError) {
            LOGGER.error("plusrenderer is not available to play emote {}", label, ex)
            return null
        } finally {
            cosmetic.close()
        }
    }

    private class Clip(val name: String, val length: Float, val loop: String?, val delay: Float)

    private fun clips(meta: JsonObject, delays: Map<String, Float>): List<Clip> {
        val list = meta.get("clips")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val clip = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val name = string(clip, "name") ?: return@mapNotNull null
            val length = clip.get("length")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: 0f
            Clip(name, length, string(clip, "loop"), delays[name] ?: 0f)
        }
    }

    private fun rule(rules: JsonObject?, key: String, default: Boolean): Boolean =
        rules?.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

    private fun string(json: JsonObject, key: String): String? =
        json.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun manifest(dir: Path): JsonObject? {
        val file = dir.resolve(MANIFEST)
        if (!file.isRegularFile()) return null
        return try {
            JsonParser.parseString(file.readText()).takeIf { it.isJsonObject }?.asJsonObject
        } catch (ex: Exception) {
            LOGGER.warn("Could not read the manifest of emote bundle {}", dir, ex)
            null
        }
    }

    private fun props(cosmetic: CosmeticAsset, texture: Identifier, label: String): Identifier? {
        if (cosmetic.textureCount() == 0) {
            LOGGER.warn("Emote {} has props but no texture", label)
            return null
        }
        return try {
            val info = IntArray(CosmeticAsset.TEXTURE_INFO_INTS)
            val rgba = ByteArray(cosmetic.textureInfo(0, info))
            cosmetic.resolveTexture(0, CosmeticAsset.NO_COLORWAY, null, null, null, rgba)
            RemoteTextures.registerPixels(
                texture,
                info[CosmeticAsset.TEXTURE_WIDTH].coerceAtLeast(1),
                info[CosmeticAsset.TEXTURE_HEIGHT].coerceAtLeast(1),
                rgba,
            )
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not upload the props texture of emote {}", label, ex)
            null
        }
    }

    private fun roles(meta: JsonObject, clips: List<Clip>): List<EmoteMark> {
        val list = meta.get("roles")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val role = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = role.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
            val offset = role.get("offset")?.takeIf { it.isJsonArray }?.asJsonArray
            fun axis(index: Int): Float =
                offset?.takeIf { it.size() == 3 }?.get(index)?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
            val yaw = role.get("yaw")?.takeIf { it.isJsonPrimitive }?.asFloat ?: 0f
            val clip = string(role, "animation")?.let { name -> clips.firstOrNull { it.name == name } }
            EmoteMark(id, axis(0), axis(1), axis(2), yaw, clip?.length ?: 0f, clip?.let { it.loop != null && it.loop != LOOP_ONCE }, clip?.delay ?: 0f)
        }
    }

    private fun ensureNativesDir() {
        if (System.getProperty(NATIVES_PROPERTY) == null) {
            System.setProperty(NATIVES_PROPERTY, File("${PolyPlusConstants.NAME}/natives").absolutePath)
        }
    }

    private fun stem(name: String): String =
        name.substringAfterLast('.').lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifEmpty { KIND_EMOTE }

    private fun zip(dir: Path): ByteArray {
        val files = Files.walk(dir).use { paths ->
            paths.filter { it.isRegularFile() }
                .map { dir.relativize(it).toString().replace('\\', '/') to it }
                .filter { (name, _) -> name.split('/').none { it.startsWith(SKIPPED_PREFIX) } }
                .toList()
        }.sortedBy { it.first }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, file) in files) {
                zip.putNextEntry(ZipEntry(name))
                Files.copy(file, zip)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun zipOf(name: String, bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun identity(hash: String?, bytes: ByteArray): Long {
        if (hash != null && hash.length == HASH_HEX && HEX.matches(hash)) {
            return JavaLong.parseUnsignedLong(hash.substring(0, IDENTITY_HEX), 16)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        var value = 0L
        for (i in 0 until Long.SIZE_BYTES) value = (value shl 8) or (digest[i].toLong() and 0xFF)
        return value
    }
}
