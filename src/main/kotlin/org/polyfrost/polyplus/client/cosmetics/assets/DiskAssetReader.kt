package org.polyfrost.polyplus.client.cosmetics.assets

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.relativeTo

internal object DiskAssetReader {
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

    fun open(asset: Asset): InputStream = asset.open()
}
