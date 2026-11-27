package org.polyfrost.polyplus.client.cosmetics.assets

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.polyfrost.plusrenderer.CosmeticAsset
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.cosmetics.CosmeticRevision
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

internal object CosmeticBundles {
    private val LOGGER: Logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/cosmetics")

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
        "events",
    )

    private val WORN_KINDS = setOf("item", "effect")
    private const val MANIFEST = "manifest.json"
    private const val SKIPPED_PREFIX = "._"
    private const val IDENTITY_HEX = 16
    private const val NATIVES_PROPERTY = "plusrenderer.natives.dir"

    class Parsed(val asset: CosmeticAsset, val meta: JsonObject)

    sealed interface Worn {
        class Loaded(val parsed: Parsed) : Worn
        object Legacy : Worn
        object Rejected : Worn
    }

    fun parseWorn(id: Int, hash: String?, dir: Path): Worn {
        if (!Files.isRegularFile(dir.resolve(MANIFEST))) return Worn.Legacy
        if (System.getProperty(NATIVES_PROPERTY) == null) {
            System.setProperty(NATIVES_PROPERTY, File("${PolyPlusConstants.NAME}/natives").absolutePath)
        }
        val bytes = try {
            zip(dir)
        } catch (ex: Exception) {
            LOGGER.error("Could not read cosmetic {} from {}", id, dir, ex)
            return Worn.Rejected
        }
        val asset = try {
            CosmeticAsset.parse(bytes, CosmeticAsset.MODE_CLIENT, identity(hash, bytes), CAPABILITIES)
        } catch (ex: RuntimeException) {
            LOGGER.error("plusrenderer could not parse cosmetic {}", id, ex)
            return Worn.Rejected
        } catch (ex: LinkageError) {
            LOGGER.error("plusrenderer is not available to parse cosmetic {}", id, ex)
            return Worn.Rejected
        }
        var kept = false
        try {
            when (asset.status()) {
                CosmeticAsset.STATUS_LEGACY -> return Worn.Legacy
                CosmeticAsset.STATUS_SKIPPED -> {
                    LOGGER.warn("Skipping cosmetic {}: it needs a newer PolyPlus ({})", id, asset.issuesJson())
                    return Worn.Rejected
                }
            }
            if (!asset.loaded()) {
                LOGGER.warn("Cosmetic {} did not load (status {}): {}", id, asset.status(), asset.issuesJson())
                return Worn.Rejected
            }
            val meta = JsonParser.parseString(asset.metaJson()).asJsonObject
            val kind = meta.get("kind")?.takeIf { it.isJsonPrimitive }?.asString
            if (kind !in WORN_KINDS) {
                LOGGER.warn("Cosmetic {} is worn but its bundle is a {}", id, kind)
                return Worn.Rejected
            }
            kept = true
            return Worn.Loaded(Parsed(asset, meta))
        } catch (ex: RuntimeException) {
            LOGGER.error("Could not read cosmetic {}", id, ex)
            return Worn.Rejected
        } finally {
            if (!kept) asset.close()
        }
    }

    fun array(meta: JsonObject, vararg path: String): JsonArray? {
        var node: JsonObject = meta
        for (key in path.dropLast(1)) node = node.get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        return node.get(path.last())?.takeIf { it.isJsonArray }?.asJsonArray
    }

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

    fun isContentHash(hash: String?): Boolean = CosmeticRevision.isContentHash(hash)

    private fun identity(hash: String?, bytes: ByteArray): Long {
        if (hash != null && isContentHash(hash)) {
            return JavaLong.parseUnsignedLong(hash.substring(0, IDENTITY_HEX), 16)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        var value = 0L
        for (i in 0 until Long.SIZE_BYTES) value = (value shl 8) or (digest[i].toLong() and 0xFF)
        return value
    }
}
