//? if >= 1.21.1 {
package org.polyfrost.polyplus.client.cosmetics.assets

import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometryParser
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object BedrockPlayerGeometryCache {
    private val logger = LoggerFactory.getLogger("polyplus/player-geometry")
    private var cached: BedrockGeometry? = null

    private val baseDir = File("${PolyPlusConstants.NAME}/cosmetics/_base")

    private val cachedGeometryFile = File(baseDir, "player.geo.json")

    @Volatile
    var playerGeometryFile: File? = null
        private set

    fun isReady(): Boolean = cached != null || playerGeometryFile != null

    fun ensureFromDisk() {
        if (playerGeometryFile == null && cachedGeometryFile.isFile) {
            playerGeometryFile = cachedGeometryFile
            cached = null
        }
    }

    fun scanCosmeticDirs(cosmeticsBase: File) {
        ensureFromDisk()
        if (isReady()) return
        cosmeticsBase.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && it.name != "_base" }
            ?.forEach { dir ->
                tryCaptureFrom(dir.toPath())
                if (isReady()) return
            }
    }

    fun getOrThrow(): BedrockGeometry {
        ensureFromDisk()
        return cached ?: playerGeometryFile?.let { path ->
            Files.newInputStream(path.toPath()).use(BedrockGeometryParser::parse).also { cached = it }
        } ?: throw IllegalStateException("Player geometry has not been downloaded yet")
    }

    fun tryCaptureFrom(root: java.nio.file.Path) {
        val asset = DiskAssetReader.findFirst(root) {
            it.endsWith("player.geo.json") || it == "models/player.geo.json"
        } ?: return

        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        val target = File(baseDir, "player.geo.json")
        Files.copy(asset.file, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        playerGeometryFile = target
        cached = null
        logger.info("Cached player geometry from {}", asset.relativePath)
    }

    fun reset() {
        cached = null
        playerGeometryFile = null
    }
}
//?}
