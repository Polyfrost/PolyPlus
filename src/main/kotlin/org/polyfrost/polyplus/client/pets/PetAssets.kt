package org.polyfrost.polyplus.client.pets

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
import org.polyfrost.polyplus.client.cosmetics.RetiredTextures
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalettes
import org.polyfrost.polyplus.client.cosmetics.assets.detectVerticalTextureFrameCount
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Long as JavaLong
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import kotlin.io.path.isRegularFile

object PetAssets {
    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/pets")
    private val byId = ConcurrentHashMap<Int, PetAsset>()
    private val retired = RetiredTextures<PetAsset>()
    private val scenes = AtomicInteger()
    private val revision = AtomicInteger()

    private val CAPABILITIES = arrayOf(
        "nameplate",
        "nameplate_materials",
        "nameplate_flags",
        "nameplate_particles",
        "nameplate_paths",
        "nameplate_letters",
        "nameplate_owned_text",
        "nameplate_reactions",
        "nameplate_one_shots",
        "nameplate_schedules",
        "nameplate_palette_animation",
        "nameplate_masks",
        "nameplate_body_art",
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
        "pets",
        "roles",
        "events",
    )

    private const val KIND_PET = "pet"
    private const val SKIPPED_PREFIX = "._"
    private const val CONTENT_PREFIX = "sha256:"
    private const val IDENTITY_HEX = 16

    internal val version: Int get() = revision.get()

    fun get(id: Int): PetAsset? = byId[id]

    fun has(id: Int): Boolean = byId.containsKey(id)

    internal fun current(asset: PetAsset): PetAsset = byId[asset.id] ?: asset

    internal fun texturePrefix(id: Int, revision: CosmeticRevision): String = "pets/$id/${revision.tag}"

    internal fun effectPrefix(id: Int, revision: CosmeticRevision, scene: Int): String = "${texturePrefix(id, revision)}/scene_$scene"

    internal fun nextScene(): Int = scenes.incrementAndGet()

    internal fun ids(): Set<Int> = byId.keys

    internal fun parse(
        id: Int,
        revision: CosmeticRevision,
        dir: Path,
        variants: List<String> = emptyList(),
        variantKey: String? = null,
    ): PetAsset? {
        ensureNativesDir()
        var skin: ColourSkin? = null
        var files: List<Pair<String, Path>> = emptyList()
        val bytes = try {
            files = bundleFiles(dir)
            skin = colourSkin(files, variants)
            zip(files, skin?.dropped.orEmpty())
        } catch (ex: Exception) {
            LOGGER.error("Could not read pet cosmetic {} from {}", id, dir, ex)
            return null
        }
        val cosmetic = try {
            CosmeticAsset.parse(bytes, CosmeticAsset.MODE_CLIENT, identity(revision, bytes), CAPABILITIES)
        } catch (ex: RuntimeException) {
            LOGGER.error("plusrenderer could not parse pet cosmetic {}", id, ex)
            return null
        } catch (ex: LinkageError) {
            LOGGER.error("plusrenderer is not available to parse pet cosmetic {}", id, ex)
            return null
        }
        var kept = false
        try {
            if (!cosmetic.loaded()) {
                LOGGER.warn("Pet cosmetic {} did not load (status {}): {}", id, cosmetic.status(), cosmetic.issuesJson())
                return null
            }
            val meta = JsonParser.parseString(cosmetic.metaJson()).asJsonObject
            if (meta.text("kind") != KIND_PET) {
                LOGGER.warn("Cosmetic {} is listed as a pet but its bundle is a {}", id, meta.text("kind"))
                return null
            }
            val pet = meta.get("pet")?.takeIf { it.isJsonObject }?.asJsonObject ?: run {
                LOGGER.warn("Pet cosmetic {} has no pet settings", id)
                return null
            }
            val archetype = PetArchetype.fromName(pet.text("archetype")) ?: run {
                LOGGER.warn("Pet cosmetic {} has an unknown archetype {}", id, pet.text("archetype"))
                return null
            }
            if (cosmetic.textureCount() == 0) {
                LOGGER.warn("Pet cosmetic {} has no texture", id)
                return null
            }
            val info = IntArray(CosmeticAsset.TEXTURE_INFO_INTS)
            val bytesNeeded = cosmetic.textureInfo(0, info)
            val width = info[CosmeticAsset.TEXTURE_WIDTH]
            val height = info[CosmeticAsset.TEXTURE_HEIGHT]
            val rgba = ByteArray(bytesNeeded)
            val frames = stripFrames(dir, width, height)
            val palette = meta.get("palette")?.takeIf { it.isJsonObject }?.asJsonObject
            val colorways = palette?.strings("colorways").orEmpty()
            val slots = palette?.get("slots")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.map { slot -> slot.takeIf { it.isJsonObject }?.asJsonObject?.text("id").orEmpty() }
                .orEmpty()
            val indexed = info[CosmeticAsset.TEXTURE_INDEXED] != 0
            cosmetic.resolveTexture(0, CosmeticAsset.NO_COLORWAY, null, null, IntArray(2), rgba)
            val asset = PetAsset(
                id = id,
                revision = revision,
                cosmetic = cosmetic,
                archetype = archetype,
                leashRadius = pet.number("leash_radius", DEFAULT_LEASH_RADIUS),
                scale = scaleOf(id, pet),
                anchor = pet.text("anchor"),
                frames = frames,
                isAnimated = frames > 1 || meta.count("animations") > 0 || meta.count("controllers") > 0,
                width = width,
                height = height,
                pixels = rgba,
                interactions = interactions(pet, dir),
                sharedRoles = sharedRoles(meta, files),
                meta = meta,
                paletteSlots = slots,
                colorways = colorways.size,
                colorway = colorways.indexOf(CosmeticPalettes.designedColorway(id, dir, variantKey)),
                paintable = indexed && slots.isNotEmpty(),
                glowPixels = glowLayer(files, width, height, skin),
            )
            kept = true
            return asset
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not read pet cosmetic {}", id, ex)
            return null
        } finally {
            if (!kept) cosmetic.close()
        }
    }

    internal fun install(asset: PetAsset): Boolean {
        try {
            asset.upload()
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not upload the texture of pet cosmetic {}", asset.id, ex)
            asset.close()
            return false
        }
        val previous = byId.put(asset.id, asset)
        revision.incrementAndGet()
        if (previous != null && previous !== asset) {
            PetManager.forgetAsset(previous)
            retired.retire(listOf(previous))
        }
        return true
    }

    internal fun releaseRetired() {
        if (retired.isEmpty()) return
        val held = HashSet<PetAsset>()
        for (pet in PetManager.pets()) {
            pet.asset?.let(held::add)
            pet.painted.asset?.let(held::add)
        }
        retired.sweep(byId.values.toHashSet(), held, PetAsset::close)
    }

    internal fun clear() {
        for (id in byId.keys.toList()) byId.remove(id)?.let(::retire)
        revision.incrementAndGet()
        retired.sweep(emptySet(), emptySet(), PetAsset::close)
    }

    internal fun trim(keep: Set<Int>): Boolean {
        val active = PetManager.cosmeticIds()
        var evicted = false
        for (id in byId.keys.toList()) {
            if (id in keep || id in active) continue
            byId.remove(id)?.let(::retire)
            evicted = true
        }
        if (evicted) revision.incrementAndGet()
        return evicted
    }

    internal fun ensureNativesDir() {
        if (System.getProperty(NATIVES_PROPERTY) == null) {
            System.setProperty(NATIVES_PROPERTY, File("${PolyPlusConstants.NAME}/natives").absolutePath)
        }
    }

    private fun retire(asset: PetAsset) {
        PetManager.forgetAsset(asset)
        asset.close()
    }

    private class ColourSkin(val stem: String, val dropped: Set<String>)

    private fun bundleFiles(dir: Path): List<Pair<String, Path>> =
        Files.walk(dir).use { paths ->
            paths.filter { it.isRegularFile() }
                .map { dir.relativize(it).toString().replace('\\', '/') to it }
                .filter { (name, _) -> name.split('/').none { it.startsWith(SKIPPED_PREFIX) } }
                .toList()
        }.sortedBy { it.first }

    private fun colourSkin(files: List<Pair<String, Path>>, variants: List<String>): ColourSkin? {
        if (variants.isEmpty() || files.any { (name, _) -> name == MANIFEST }) return null
        val skins = files.map { (name, _) -> name }.filter { name ->
            val lower = name.lowercase()
            lower.endsWith(PNG_SUFFIX) && !lower.endsWith(GLOW_SUFFIX)
        }
        if (skins.size < 2) return null
        for (token in variantTokens(variants)) {
            for (name in skins) {
                val stem = pngStem(name)
                if (!stem.endsWith("_$token")) continue
                val family = stem.removeSuffix(token)
                val dropped = skins.filterTo(HashSet()) { it != name && pngStem(it).startsWith(family) }
                if (dropped.isEmpty()) continue
                return ColourSkin(stem, dropped)
            }
        }
        return null
    }

    private fun variantTokens(variants: List<String>): List<String> {
        val tokens = LinkedHashSet<String>()
        for (variant in variants) tokens += normalized(variant)
        for (variant in variants) tokens += normalized(variant).split('_').asReversed()
        tokens.remove("")
        return tokens.toList()
    }

    private fun normalized(text: String): String =
        text.lowercase().replace(NOT_WORD, "_").trim('_')

    private fun pngStem(name: String): String =
        name.substringAfterLast('/').lowercase().removeSuffix(PNG_SUFFIX)

    private fun zip(files: List<Pair<String, Path>>, dropped: Set<String>): ByteArray {
        val bare = files.none { (name, _) -> name == MANIFEST || name == LEGACY_PET }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, file) in files) {
                if (name in dropped) continue
                zip.putNextEntry(ZipEntry(name))
                Files.copy(file, zip)
                zip.closeEntry()
            }
            if (bare) {
                zip.putNextEntry(ZipEntry(LEGACY_PET))
                zip.write(BARE_PET)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun glowLayer(files: List<Pair<String, Path>>, width: Int, height: Int, skin: ColourSkin?): ByteArray? {
        val dropped = skin?.dropped.orEmpty().mapTo(HashSet(), ::pngStem)
        val layers = files.filter { (name, _) ->
            name.lowercase().endsWith(GLOW_SUFFIX) && pngStem(name).removeSuffix(GLOW_STEM) !in dropped
        }
        val own = skin?.let { "${it.stem}$GLOW_SUFFIX" }
        val file = (layers.firstOrNull { (name, _) -> name.substringAfterLast('/').lowercase() == own } ?: layers.firstOrNull())
            ?.second ?: return null
        val image = try {
            ImageIO.read(file.toFile())
        } catch (ex: Exception) {
            LOGGER.warn("Could not read the glow layer {}", file, ex)
            null
        } ?: return null
        if (image.width != width || image.height != height) {
            LOGGER.warn("Ignoring the glow layer {}: it is {}x{}, not {}x{}", file, image.width, image.height, width, height)
            return null
        }
        val rgba = ByteArray(width * height * 4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getRGB(x, y)
                val alpha = argb ushr 24
                val at = (y * width + x) * 4
                val scale = if (PREMULTIPLIED_GLOW) alpha else 0xFF
                rgba[at] = (((argb shr 16) and 0xFF) * scale / 255).toByte()
                rgba[at + 1] = (((argb shr 8) and 0xFF) * scale / 255).toByte()
                rgba[at + 2] = ((argb and 0xFF) * scale / 255).toByte()
                rgba[at + 3] = alpha.toByte()
            }
        }
        return rgba
    }

    internal fun identity(revision: CosmeticRevision, bytes: ByteArray): Long {
        if (revision.content.startsWith(CONTENT_PREFIX)) {
            return JavaLong.parseUnsignedLong(revision.content.substring(CONTENT_PREFIX.length, CONTENT_PREFIX.length + IDENTITY_HEX), 16)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        var value = 0L
        for (i in 0 until Long.SIZE_BYTES) value = (value shl 8) or (digest[i].toLong() and 0xFF)
        return value
    }

    private fun interactions(pet: JsonObject, dir: Path): Map<PetInteraction, PetRoles> {
        val written = readJson(dir.resolve(MANIFEST))?.get("interactions")?.takeIf { it.isJsonObject }?.asJsonObject
        val declared = pet.get("interactions")?.takeIf { it.isJsonObject }?.asJsonObject ?: written
        val out = HashMap<PetInteraction, PetRoles>()
        declared?.let { roles(it, out, false) }
        written?.let { roles(it, out, true) }
        return out
    }

    private fun roles(declared: JsonObject, out: MutableMap<PetInteraction, PetRoles>, optOuts: Boolean) {
        for ((key, value) in declared.entrySet()) {
            val interaction = PetInteraction.fromKey(key) ?: continue
            if (!value.isJsonArray) continue
            val list = value.asJsonArray
            if (optOuts) {
                if (list.size() == 0) out.putIfAbsent(interaction, PetRoles(asA = false, asB = false))
                continue
            }
            val roles = list.mapNotNull { role ->
                role.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            }
            val asA = ROLE_A in roles
            val asB = ROLE_B in roles
            if (asA || asB) out[interaction] = PetRoles(asA, asB)
        }
    }

    private fun sharedRoles(meta: JsonObject, files: List<Pair<String, Path>>): List<PetSharedRole> {
        val list = meta.get("roles")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        if (list.size() == 0) return emptyList()
        val delays = startDelays(files)
        val clips = HashMap<String, JsonObject>()
        meta.get("clips")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { clip ->
            val entry = clip.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            entry.text("name")?.let { clips[it] = entry }
        }
        val out = ArrayList<PetSharedRole>()
        for (index in 0 until list.size()) {
            val entry = list[index].takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val interaction = entry.text("interaction")?.let(PetInteraction::fromKey) ?: continue
            val role = when (entry.text("id")) {
                ROLE_A -> PetInteractions.ROLE_A
                ROLE_B -> PetInteractions.ROLE_B
                else -> continue
            }
            val offset = entry.get("offset")?.takeIf { it.isJsonArray }?.asJsonArray?.takeIf { it.size() == 3 }
            val axis = { i: Int ->
                offset?.get(i)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: 0.0
            }
            val clip = entry.text("animation")?.let(clips::get)
            out += PetSharedRole(
                index,
                interaction,
                role,
                axis(0),
                axis(1),
                axis(2),
                entry.number("yaw", 0f),
                clip?.number("length", 0f)?.toDouble() ?: 0.0,
                clip?.text("loop") == LOOP_REPEAT,
                clip?.text("name")?.let(delays::get) ?: 0.0,
            )
        }
        return out
    }

    internal fun startDelays(files: List<Pair<String, Path>>): Map<String, Double> {
        val out = HashMap<String, Double>()
        for ((name, path) in files) {
            if (!name.lowercase().endsWith(JSON_SUFFIX)) continue
            val animations = readJson(path)?.get("animations")?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            for ((clip, body) in animations.entrySet()) {
                val delay = body.takeIf { it.isJsonObject }?.asJsonObject?.get("start_delay") ?: continue
                if (!delay.isJsonPrimitive) continue
                val primitive = delay.asJsonPrimitive
                val seconds = when {
                    primitive.isNumber -> primitive.asDouble
                    primitive.isString -> primitive.asString.trim().toDoubleOrNull()
                    else -> null
                } ?: continue
                if (seconds.isFinite() && seconds > 0.0) out[clip] = seconds
            }
        }
        return out
    }

    private fun readJson(file: Path): JsonObject? {
        if (!file.isRegularFile()) return null
        return try {
            Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.takeIf { it.isJsonObject }?.asJsonObject
        } catch (ex: Exception) {
            LOGGER.warn("Could not read {}", file, ex)
            null
        }
    }

    private fun stripFrames(dir: Path, width: Int, height: Int): Int {
        val geometry = Files.walk(dir).use { paths ->
            paths.filter { it.isRegularFile() }
                .map { dir.relativize(it).toString().replace('\\', '/') to it }
                .filter { (name, _) -> name.endsWith(".geo.json") && !name.endsWith("player.geo.json") }
                .toList()
        }.minByOrNull { it.first }?.second ?: return 1
        val size = runCatching { geometrySize(geometry) }.getOrNull() ?: return 1
        return detectVerticalTextureFrameCount(size.first, size.second, width, height).coerceAtLeast(1)
    }

    private fun geometrySize(file: Path): Pair<Int, Int>? {
        val root = Files.newBufferedReader(file).use { JsonParser.parseReader(it) }.asJsonObject
        val modern = root.get("minecraft:geometry")?.takeIf { it.isJsonArray }?.asJsonArray
        if (modern != null && modern.size() > 0) {
            val description = modern[0].asJsonObject.get("description")?.asJsonObject ?: return null
            return description.int("texture_width") to description.int("texture_height")
        }
        val legacy = root.entrySet().firstOrNull { it.key.startsWith("geometry.") }?.value?.asJsonObject ?: return null
        return legacy.int("texturewidth") to legacy.int("textureheight")
    }

    private fun scaleOf(id: Int, pet: JsonObject): Float {
        val scale = pet.number("scale", 1f)
        if (!scale.isFinite() || scale <= 0f) return 1f
        val kept = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        if (kept != scale) LOGGER.warn("Pet cosmetic {} has scale {}, using {}", id, scale, kept)
        return kept
    }

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.number(key: String, fallback: Float): Float =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asFloat ?: fallback

    private fun JsonObject.strings(key: String): List<String> =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { item -> item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString }
            .orEmpty()

    private fun JsonObject.count(key: String): Int =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.size() ?: 0

    private fun JsonObject.int(key: String): Int =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: 0

    private const val NATIVES_PROPERTY = "plusrenderer.natives.dir"
    private const val DEFAULT_LEASH_RADIUS = 3f
    private const val MIN_SCALE = 0.01f
    private const val MAX_SCALE = 8f
    private const val MANIFEST = "manifest.json"
    private const val LEGACY_PET = "pet.json"
    private const val GLOW_SUFFIX = "_glow.png"
    private const val GLOW_STEM = "_glow"
    //? if > 1.8.9 && < 1.21.5 {
    /*private const val PREMULTIPLIED_GLOW = true
    *///?} else {
    private const val PREMULTIPLIED_GLOW = false
    //?}
    private const val PNG_SUFFIX = ".png"
    private val NOT_WORD = Regex("[^a-z0-9]+")
    private val BARE_PET = "{}".toByteArray(Charsets.UTF_8)
    private const val ROLE_A = "a"
    private const val ROLE_B = "b"
    private const val LOOP_REPEAT = "repeat"
    private const val JSON_SUFFIX = ".json"
}
