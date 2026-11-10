package org.polyfrost.polyplus.client.nameplate

import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusSentry
import org.polyfrost.polyplus.client.cosmetics.assets.ArchiveLimits
//? if >= 1.21.1 || = 1.8.9 {
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialAssets
//?}
import org.polyfrost.polyplus.nameplate.engine.api.bundle.Layout
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleBytes
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

object NameplateLoader {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    val CAPABILITIES: Set<String> = setOf("canvas", "sprites", "flags", MaterialLayer.CAPABILITY, Layout.CAPABILITY)

    private const val MIB = 1024L * 1024

    internal val ARCHIVE_LIMITS = ArchiveLimits(maxDownloadBytes = 2 * MIB, maxExtractedBytes = 8 * MIB, maxEntries = 128)

    private const val MANIFEST = "manifest.json"
    private const val JSON_EXTENSION = ".json"
    private const val PNG_EXTENSION = ".png"
    private const val APPLE_DOUBLE_PREFIX = "._"
    private const val MAX_JSON_BYTES = MIB
    private const val MAX_PNG_BYTES = MIB
    private const val MAX_REPORTED_ISSUES = 50

    private class Parsed(val hash: String, val bundle: NameplateBundle?)

    private class BundleFiles(val texts: Map<String, String>, val bytes: Map<String, ByteArray>)

    private val parsed = ConcurrentHashMap<String, Parsed>()
    private val loggedSkips: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val reported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun loadDownloaded(key: String, hash: String, cosmeticId: Int, dir: Path): NameplateBundle? {
        parsed[key]?.takeIf { it.hash == hash }?.let { return it.bundle }
        val bundle = when (val result = parse(dir)) {
            is BundleLoad.Loaded -> result.bundle
            is BundleLoad.Skipped -> {
                if (loggedSkips.add("$key@$hash")) {
                    LOGGER.info("Skipping nameplate cosmetic {}: {}", cosmeticId, result.reason)
                }
                null
            }
            is BundleLoad.Invalid -> {
                LOGGER.warn(
                    "Disabling nameplate cosmetic {}: its bundle is invalid\n{}",
                    cosmeticId,
                    result.issues.joinToString("\n") { "  $it" },
                )
                if (reported.add("$key@$hash")) {
                    PolyPlusSentry.captureNameplateIssues(
                        cosmeticId,
                        hash,
                        result.issues.take(MAX_REPORTED_ISSUES).map(BundleIssue::toString),
                    )
                }
                null
            }
        }
        parsed[key] = Parsed(hash, bundle)
        return bundle
    }

    fun parse(dir: Path): BundleLoad = try {
        val files = readFiles(dir)
        val result = BundleLoader(CAPABILITIES).load(BundleSource.of(files.texts), BundleBytes.of(files.bytes))
        //? if >= 1.21.1 || = 1.8.9 {
        NameplateMaterialAssets.prepare(result, files.bytes)
        //?} else {
        /*result
        *///?}
    } catch (e: Exception) {
        BundleLoad.Invalid(listOf(BundleIssue("bundle", "could not be read: $e")))
    }

    fun retain(keys: Set<String>) {
        parsed.keys.retainAll(keys)
    }

    private fun readFiles(dir: Path): BundleFiles {
        val root = bundleRoot(dir).toAbsolutePath().normalize()
        val texts = HashMap<String, String>()
        val bytes = HashMap<String, ByteArray>()
        var total = 0L
        Files.walk(root).use { paths ->
            for (path in paths) {
                if (!path.isRegularFile(LinkOption.NOFOLLOW_LINKS) || path.name.startsWith(APPLE_DOUBLE_PREFIX)) continue
                val json = path.name.endsWith(JSON_EXTENSION, ignoreCase = true)
                val png = path.name.endsWith(PNG_EXTENSION, ignoreCase = true)
                if (!json && !png) continue
                if (texts.size + bytes.size >= ARCHIVE_LIMITS.maxEntries) break
                val normalized = path.toAbsolutePath().normalize()
                if (!normalized.startsWith(root)) continue
                val size = Files.size(normalized)
                val limit = if (json) MAX_JSON_BYTES else MAX_PNG_BYTES
                if (size > limit) {
                    LOGGER.warn("Ignoring nameplate file {}: larger than {} bytes", normalized, limit)
                    continue
                }
                if (total + size > ARCHIVE_LIMITS.maxExtractedBytes) {
                    LOGGER.warn("Ignoring nameplate file {}: the bundle is over {} bytes", normalized, ARCHIVE_LIMITS.maxExtractedBytes)
                    continue
                }
                total += size
                val relative = root.relativize(normalized).toString().replace('\\', '/')
                if (json) {
                    texts[relative] = Files.readString(normalized)
                } else {
                    bytes[relative] = Files.readAllBytes(normalized)
                }
            }
        }
        return BundleFiles(texts, bytes)
    }

    private fun bundleRoot(dir: Path): Path {
        if (Files.isRegularFile(dir.resolve(MANIFEST))) return dir
        val nested = Files.list(dir).use { children ->
            children.filter { it.isDirectory() && Files.isRegularFile(it.resolve(MANIFEST)) }.toList()
        }
        return nested.singleOrNull() ?: dir
    }
}
