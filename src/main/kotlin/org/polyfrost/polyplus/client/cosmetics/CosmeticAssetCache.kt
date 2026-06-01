package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.assets.AssetArchive
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
//? if >= 1.21.1 {
import org.polyfrost.polyplus.client.cosmetics.assets.BedrockPlayerGeometryCache
import org.polyfrost.polyplus.client.cosmetics.assets.EmoteAssetParser
import org.polyfrost.polyplus.client.emotes.Emote
//?}
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.utils.HashManager
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

object CosmeticAssetCache {
    private val LOGGER = LogManager.getLogger()
    private val loadLock = Mutex()

    @JvmField
    val baseDir: File = File("${PolyPlusConstants.NAME}/cosmetics")

    private val hashManager = HashManager(baseDir.resolve("hashes.json"))
    private val capes = HashMap<Int, CachedCosmetic>()
    //? if >= 1.21.1 {
    private val emotesByCosmeticId = HashMap<Int, List<Emote>>()
    private val emotesById = HashMap<Int, Emote>()
    //?}

    @JvmStatic
    fun getCapeTexture(uuid: UUID): Identifier? {
        val id = CosmeticCatalog.getActiveId(uuid, CosmeticType.Cape) ?: return null
        return capes[id]?.asResource()
    }

    //? if >= 1.21.1 {
    fun getEmote(cosmeticId: Int): Emote? = emotesById[cosmeticId]

    fun getEmotesForCosmetic(cosmeticId: Int): List<Emote> = emotesByCosmeticId[cosmeticId].orEmpty()
    //?}

    fun reset() {
        capes.clear()
        //? if >= 1.21.1 {
        emotesByCosmeticId.clear()
        emotesById.clear()
        RemoteTextures.releaseAll()
        BedrockPlayerGeometryCache.reset()
        //?}
    }

    suspend fun preloadDefinitions(definitions: Collection<CosmeticDefinition>) {
        withContext(Dispatchers.IO) {
            loadLock.withLock {
                hashManager.awaitHashes()
                if (!baseDir.exists() && !baseDir.mkdirs()) {
                    LOGGER.error("Failed to create cosmetics directory at ${baseDir.absolutePath}")
                    return@withLock
                }

                for (definition in definitions) {
                    runCatching { materializeCosmeticLocked(definition) }
                        .onFailure { LOGGER.error("Failed to download cosmetic {}", definition.id, it) }
                }

                //? if >= 1.21.1 {
                BedrockPlayerGeometryCache.scanCosmeticDirs(baseDir)
                //?}

                for (definition in definitions) {
                    runCatching { loadCosmeticAssetsLocked(definition) }
                        .onFailure { LOGGER.error("Failed to load cosmetic {}", definition.id, it) }
                }

                hashManager.saveHashes()
            }
        }
    }

    suspend fun ensureLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getDefinition(id) ?: return false
        return withContext(Dispatchers.IO) {
            loadLock.withLock {
                runCatching {
                    hashManager.awaitHashes()
                    ensureLoadedLocked(definition)
                    hashManager.saveHashes()
                    true
                }.getOrElse {
                    LOGGER.error("Failed to ensure cosmetic {} is loaded", id, it)
                    false
                }
            }
        }
    }

    private suspend fun ensureLoadedLocked(definition: CosmeticDefinition) {
        materializeCosmeticLocked(definition)
        loadCosmeticAssetsLocked(definition)
    }

    private suspend fun materializeCosmeticLocked(definition: CosmeticDefinition) {
        val url = definition.url
        if (url == null && definition.type != CosmeticType.Cape) {
            LOGGER.warn("Cosmetic {} has no download URL", definition.id)
            return
        }

        val cosmeticDir = baseDir.resolve(definition.id.toString()).toPath()
        val hashKey = definition.id.toString()
        val needsDownload = definition.url != null && hashManager.updateHash(hashKey, definition.hash)

        if (needsDownload) {
            val cosmeticDirFile = cosmeticDir.toFile()
            if (cosmeticDirFile.exists()) {
                cosmeticDirFile.deleteRecursively()
            }
            val bytes = PolyPlusClient.HTTP.get(definition.url!!).bodyAsBytes()
            AssetArchive.materialize(bytes, cosmeticDir)
        } else if (!cosmeticDir.toFile().exists()) {
            if (definition.url == null) return
            val bytes = PolyPlusClient.HTTP.get(definition.url).bodyAsBytes()
            AssetArchive.materialize(bytes, cosmeticDir)
        }
    }

    private fun loadCosmeticAssetsLocked(definition: CosmeticDefinition) {
        val cosmeticDir = baseDir.resolve(definition.id.toString()).toPath()
        if (!cosmeticDir.toFile().exists()) return

        when (definition.type) {
            CosmeticType.Cape -> loadCape(definition.id, cosmeticDir)
            //? if >= 1.21.1 {
            CosmeticType.Emote -> loadEmote(definition.id, cosmeticDir)
            //?} else {
            /*CosmeticType.Emote -> LOGGER.warn("Emotes require Minecraft 1.21.1+")*/
            //?}
        }
    }

    private fun loadCape(id: Int, dir: java.nio.file.Path) {
        val png = dir.toFile().walkTopDown()
            .firstOrNull { it.isFile && it.extension.equals("png", ignoreCase = true) }
            ?: dir.resolve("asset.bin").toFile().takeIf { it.exists() }
            ?: return

        ClientPlatform.runOnMain {
            capes[id] = CachedCosmetic.Cape(ImageIO.read(png))
        }
    }

    //? if >= 1.21.1 {
    private fun loadEmote(id: Int, dir: java.nio.file.Path) {
        BedrockPlayerGeometryCache.tryCaptureFrom(dir)
        BedrockPlayerGeometryCache.ensureFromDisk()
        if (!BedrockPlayerGeometryCache.isReady()) {
            BedrockPlayerGeometryCache.scanCosmeticDirs(baseDir)
        }
        if (!BedrockPlayerGeometryCache.isReady()) {
            LOGGER.warn("Skipping emote cosmetic {} until player geometry is available", id)
            return
        }
        val playerGeometry = BedrockPlayerGeometryCache.getOrThrow()
        val parsed = EmoteAssetParser.parse(id, dir, playerGeometry)
        if (parsed.isEmpty()) {
            LOGGER.warn("No emotes parsed for cosmetic {}", id)
            return
        }

        ClientPlatform.runOnMain {
            emotesByCosmeticId[id] = parsed
            emotesById[id] = parsed.first()
        }
    }
    //?}
}
