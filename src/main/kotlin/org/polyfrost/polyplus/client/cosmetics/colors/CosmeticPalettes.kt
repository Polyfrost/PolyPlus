package org.polyfrost.polyplus.client.cosmetics.colors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.cosmetics.CosmeticCatalog
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isRegularFile

class PaletteSlot(
    val id: String,
    val name: String,
    val stops: List<Int>,
    val interpolate: Int?,
    val customizable: Boolean,
)

class CosmeticPalette(
    val slots: List<PaletteSlot>,
    val colorways: Map<String, Map<String, List<Int>>>,
    private val variantColorways: Map<String, String?>,
    private val implicitColorway: String?,
    private val undeclaredUseFirstVariant: Boolean = false,
) {
    val customizable: List<PaletteSlot> get() = slots.filter { it.customizable }

    val hasCustomizable: Boolean get() = slots.any { it.customizable }

    fun colorwayFor(variantKey: String?): String? = when {
        variantKey == null -> implicitColorway
        variantKey in variantColorways -> variantColorways[variantKey]
        undeclaredUseFirstVariant -> implicitColorway
        variantKey in colorways -> variantKey
        else -> implicitColorway
    }

    fun designedColorway(variantKey: String?): String? =
        if (variantKey == null || variantKey in variantColorways || undeclaredUseFirstVariant) {
            colorwayFor(variantKey)
        } else {
            colorwayFor(variantKey) ?: variantKey
        }

    fun ramp(slot: PaletteSlot, colorway: String?): List<Int> {
        val stops = colorway?.let { colorways[it]?.get(slot.id) } ?: slot.stops
        val count = slot.interpolate
        return if (count != null && stops.size >= 2 && count > stops.size) interpolate(stops, count) else stops
    }

    fun baseColor(slot: PaletteSlot, colorway: String?): Int {
        val ramp = ramp(slot, colorway)
        return ramp.getOrElse((ramp.size - 1) / 2) { ramp.firstOrNull() ?: 0 } and RGB_MASK
    }

    fun baseColors(colorway: String?): Map<String, Int> =
        customizable.associate { it.id to baseColor(it, colorway) }

    private fun interpolate(stops: List<Int>, count: Int): List<Int> = List(count) { along(stops, it, count) }

    private fun along(stops: List<Int>, position: Int, positions: Int): Int {
        if (stops.size == 1 || positions <= 1) return stops[0]
        val segments = (stops.size - 1).toLong()
        val span = positions.toLong() - 1
        val scaled = position.coerceAtMost(positions - 1).toLong() * segments
        val segment = (scaled / span).coerceAtMost(segments - 1)
        return lerp(stops[segment.toInt()], stops[segment.toInt() + 1], scaled - segment * span, span)
    }

    private fun lerp(from: Int, to: Int, num: Long, den: Long): Int {
        var out = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            val a = ((from ushr shift) and 0xFF).toLong()
            val b = ((to ushr shift) and 0xFF).toLong()
            val mixed = a + Math.floorDiv((b - a) * num, den)
            out = out or (mixed.coerceIn(0, 255).toInt() shl shift)
        }
        return out
    }

    private companion object {
        const val RGB_MASK = 0x00FF_FFFF
    }
}

object CosmeticPalettes {
    private val LOGGER = LogManager.getLogger("${PolyPlusConstants.NAME}/Palettes")
    private const val PALETTES = "palettes.json"
    private const val MANIFEST = "manifest.json"
    private val BASE_RAMP_KINDS = setOf("item", "pet", "effect", "emote")
    private const val MAX_TONES = 16
    private const val NAMEPLATE_KIND = "nameplate"

    private class Entry(val revision: CosmeticRevision, val palette: CosmeticPalette?)

    private val cache = ConcurrentHashMap<Int, Entry>()

    fun cached(cosmeticId: Int): CosmeticPalette? {
        val revision = CosmeticCatalog.getCosmeticDefinition(cosmeticId)?.let(CosmeticRevision::of) ?: return null
        return cache[cosmeticId]?.takeIf { it.revision == revision }?.palette
    }

    fun isSettled(cosmeticId: Int): Boolean {
        val revision = CosmeticCatalog.getCosmeticDefinition(cosmeticId)?.let(CosmeticRevision::of) ?: return false
        return cache[cosmeticId]?.revision == revision
    }

    suspend fun load(cosmeticId: Int): CosmeticPalette? {
        val definition = CosmeticCatalog.getCosmeticDefinition(cosmeticId) ?: return null
        val revision = CosmeticRevision.of(definition)
        cache[cosmeticId]?.takeIf { it.revision == revision }?.let { return it.palette }
        if (definition.type == CosmeticType.Emote || definition.type == CosmeticType.Unknown) {
            cache[cosmeticId] = Entry(revision, null)
            return null
        }
        if (!CosmeticAssetCache.ensureCosmeticLoaded(cosmeticId)) return null
        val palette = try {
            val manifest = CosmeticAssetCache.readBundleFile(cosmeticId, MANIFEST)
            if (definition.type == CosmeticType.Aura && manifest == null) {
                null
            } else {
                CosmeticAssetCache.readBundleFile(cosmeticId, PALETTES)?.let { bytes -> parse(bytes, manifest) }
            }
        } catch (ex: Exception) {
            LOGGER.warn("Could not read the colours of cosmetic {}", cosmeticId, ex)
            null
        }
        cache[cosmeticId] = Entry(revision, palette)
        return palette
    }

    fun clear() {
        cache.clear()
    }

    fun designedColorway(cosmeticId: Int, dir: Path, variantKey: String?): String? {
        val palettes = dir.resolve(PALETTES)
        if (!palettes.isRegularFile()) return variantKey
        return try {
            val manifest = dir.resolve(MANIFEST).takeIf { it.isRegularFile() }?.let(Files::readAllBytes)
            val palette = parse(Files.readAllBytes(palettes), manifest) ?: return variantKey
            palette.designedColorway(variantKey)
        } catch (ex: Exception) {
            LOGGER.warn("Could not read the colours of cosmetic {}", cosmeticId, ex)
            variantKey
        }
    }

    internal fun parse(palettes: ByteArray, manifest: ByteArray?): CosmeticPalette? {
        val root = JsonParser.parseString(palettes.toString(Charsets.UTF_8)).objectOrNull() ?: return null
        val slots = root.get("slots")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { element ->
            val slot = element.objectOrNull() ?: return@mapNotNull null
            val id = slot.string("id") ?: return@mapNotNull null
            val stops = ramp(slot.get("ramp")) ?: return@mapNotNull null
            PaletteSlot(
                id = id,
                name = slot.string("name") ?: id,
                stops = stops,
                interpolate = interpolate(slot.get("interpolate"), stops.size),
                customizable = slot.get("customizable")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true,
            )
        }.orEmpty()
        if (slots.isEmpty()) return null
        val colorways = LinkedHashMap<String, Map<String, List<Int>>>()
        root.get("colorways")?.objectOrNull()?.entrySet()?.forEach { (name, overrides) ->
            val map = overrides.objectOrNull() ?: return@forEach
            colorways[name] = map.entrySet().mapNotNull { (slot, value) -> ramp(value)?.let { slot to it } }.toMap()
        }
        val variants = LinkedHashMap<String, String?>()
        var implicit: String? = null
        val manifestRoot = manifest
            ?.let { runCatching { JsonParser.parseString(it.toString(Charsets.UTF_8)).objectOrNull() }.getOrNull() }
        val kind = manifestRoot?.string("kind")
        val declared = manifestRoot?.get("variants")?.objectOrNull()
        val firstVariantDefault = kind == NAMEPLATE_KIND && declared != null && declared.size() > 0
        if (declared == null || declared.size() == 0) {
            if (kind !in BASE_RAMP_KINDS) implicit = colorways.keys.firstOrNull()
        } else {
            for ((key, spec) in declared.entrySet()) {
                variants[key] = spec.objectOrNull()?.string("palette")?.takeIf { it in colorways }
            }
            if (firstVariantDefault) implicit = variants.values.first()
        }
        return CosmeticPalette(slots, colorways, variants, implicit, firstVariantDefault)
    }

    private fun interpolate(element: JsonElement?, stops: Int): Int? {
        val value = element?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asDouble ?: return null
        val count = value.toInt()
        return count.takeIf { stops >= 2 && it.toDouble() == value && it in stops..MAX_TONES }
    }

    private fun ramp(element: JsonElement?): List<Int>? {
        val array = element?.takeIf { it.isJsonArray }?.asJsonArray ?: return null
        val colors = array.map { item ->
            item.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(::hex) ?: return null
        }
        return colors.takeIf { it.isNotEmpty() }
    }

    private fun hex(text: String): Int? {
        val digits = text.removePrefix("#").takeIf { text.startsWith("#") } ?: return null
        if (digits.length != 6 && digits.length != 8) return null
        val value = digits.toLongOrNull(16) ?: return null
        return if (digits.length == 6) (value or 0xFF00_0000L).toInt() else value.toInt()
    }

    private fun JsonElement.objectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
