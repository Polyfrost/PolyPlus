package org.polyfrost.polyplus.client.cosmetics.assets

import org.polyfrost.polyplus.PolyPlusConstants
import org.slf4j.LoggerFactory
import java.io.DataInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

internal object DiskAssetReader {
    private val logger = LoggerFactory.getLogger("${PolyPlusConstants.ID}/assets")

    data class Asset(
        val relativePath: String,
        val file: Path,
    ) {
        fun open(): InputStream = Files.newInputStream(file)
    }

    fun walk(root: Path, predicate: (String) -> Boolean): List<Asset> {
        if (!Files.isDirectory(root)) {
            return emptyList()
        }

        val assets = mutableListOf<Asset>()
        Files.walk(root).use { paths ->
            paths.filter { it.isRegularFile() }.forEach { path ->
                val relative = root.relativize(path).toString().replace('\\', '/')
                if (predicate(relative)) {
                    assets += Asset(relative, path)
                }
            }
        }
        return assets.sortedBy { it.relativePath }
    }

    fun findFirst(root: Path, predicate: (String) -> Boolean): Asset? =
        walk(root, predicate).firstOrNull()

    /** The bundle's texture (preferring one under `textures/`). */
    fun findTexture(root: Path): Path? {
        val pngs = walk(root) { it.endsWith(".png") }
        if (pngs.isEmpty()) return null
        return (pngs.firstOrNull { it.relativePath.startsWith("textures/") } ?: pngs.first()).file
    }

    /** Reads a PNG's dimensions from the IHDR header. */
    fun pngSize(file: Path): Pair<Int, Int>? = try {
        DataInputStream(Files.newInputStream(file)).use { input ->
            input.skipBytes(16)
            val width = input.readInt()
            val height = input.readInt()
            if (width > 0 && height > 0) width to height else null
        }
    } catch (ex: Exception) {
        logger.warn("Failed to read texture dimensions from {}", file, ex)
        null
    }
}
