package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.plugins.timeout
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
import org.polyfrost.polyplus.client.cosmetics.assets.AttachedCosmeticParser
import org.polyfrost.polyplus.client.cosmetics.assets.BedrockPlayerGeometryCache
import org.polyfrost.polyplus.client.cosmetics.assets.EmoteAssetParser
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.emotes.Emote
//?}
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
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
    private val attachedById = HashMap<Int, AttachedCosmetic>()
    //?}

    @JvmStatic
    fun getCapeTexture(uuid: UUID): Identifier? {
        val id = CosmeticCatalog.getActiveId(uuid, BodySlot.Cape) ?: return null
        return capes[id]?.asResource()
    }

    fun getCapeResource(id: Int): Identifier? = capes[id]?.asResource()

    //? if >= 1.21.1 {
    fun getEmote(emoteId: Int): Emote? = emotesById[emoteId]

    fun getEmotesForCosmetic(emoteId: Int): List<Emote> = emotesByCosmeticId[emoteId].orEmpty()

    fun getAttachedCosmetic(id: Int): AttachedCosmetic? = attachedById[id]

    fun attachedCosmeticId(id: Int): Identifier = AttachedCosmeticParser.attachedCosmeticId(id)
    //?}

    fun reset() {
        capes.clear()
        //? if >= 1.21.1 {
        emotesByCosmeticId.clear()
        emotesById.clear()
        attachedById.clear()
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

                try {
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
                } finally {
                    hashManager.saveHashes()
                }
            }
        }
    }

    suspend fun ensureLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getDefinition(id) ?: return false
        return ensureLoaded(definition)
    }

    suspend fun ensureCosmeticLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return false
        return ensureLoaded(definition)
    }

    //? if >= 1.21.1 {
    suspend fun ensureEmoteLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getEmoteDefinition(id) ?: return false
        return ensureLoaded(definition)
    }
    //?}

    private suspend fun ensureLoaded(definition: CosmeticDefinition): Boolean {
        return withContext(Dispatchers.IO) {
            loadLock.withLock {
                runCatching {
                    hashManager.awaitHashes()
                    ensureLoadedLocked(definition)
                    hashManager.saveHashes()
                    true
                }.getOrElse {
                    LOGGER.error("Failed to ensure cosmetic {} is loaded", definition.id, it)
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

        if (url == null) return

        val cosmeticDir = baseDir.resolve(definition.cacheKey()).toPath()
        val hashKey = definition.cacheKey()
        val hashChanged = !hashManager.isCurrent(hashKey, definition.hash)
        val needsDownload = hashChanged || !cosmeticDir.toFile().exists()

        if (needsDownload) {
            val cosmeticDirFile = cosmeticDir.toFile()
            if (cosmeticDirFile.exists()) {
                cosmeticDirFile.deleteRecursively()
            }
            val bytes = PolyPlusClient.HTTP.get(url) { timeout { requestTimeoutMillis = 60_000 } }.bodyAsBytes()
            AssetArchive.materialize(bytes, cosmeticDir)
            hashManager.updateHash(hashKey, definition.hash)
        }
    }

    private fun loadCosmeticAssetsLocked(definition: CosmeticDefinition) {
        val cosmeticDir = baseDir.resolve(definition.cacheKey()).toPath()
        if (!cosmeticDir.toFile().exists()) return

        when (definition.type) {
            CosmeticType.Cape -> loadCape(definition.id, cosmeticDir)
            CosmeticType.Backpack,
            CosmeticType.Glasses,
            CosmeticType.Wings,
            CosmeticType.Glove,
            CosmeticType.Hat,
            CosmeticType.Aura,
            CosmeticType.Boots,
            CosmeticType.Shoulder ->
                //? if >= 1.21.1 {
                loadAttachedCosmetic(definition.id, cosmeticDir, definition.preferredSlot() ?: return)
                //?} else {
                /*LOGGER.warn("Attached cosmetics require Minecraft 1.21.1+")*/
                //?}
            CosmeticType.Unknown -> LOGGER.warn("Ignoring cosmetic {} with unknown type/slot", definition.id)
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

        val image = runCatching { ImageIO.read(png) }.getOrNull()
        if (image == null) {
            LOGGER.warn("Failed to decode cape image for cosmetic {} from {}", id, png)
            return
        }

        ClientPlatform.runOnMain {
            capes[id] = CachedCosmetic.Cape(image)
        }
    }

    //? if >= 1.21.1 {
    private fun loadAttachedCosmetic(id: Int, dir: java.nio.file.Path, slot: BodySlot) {
        BedrockPlayerGeometryCache.tryCaptureFrom(dir)
        BedrockPlayerGeometryCache.ensureFromDisk()
        if (!BedrockPlayerGeometryCache.isReady()) {
            BedrockPlayerGeometryCache.scanCosmeticDirs(baseDir)
        }
        if (!BedrockPlayerGeometryCache.isReady()) {
            LOGGER.warn("Skipping cosmetic {} until player geometry is available", id)
            return
        }
        val playerGeometry = BedrockPlayerGeometryCache.getOrThrow()
        val attached = AttachedCosmeticParser.parse(id, dir, slot, playerGeometry) ?: return

        ClientPlatform.runOnMain {
            attachedById[id] = attached
        }
    }

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

    private fun CosmeticDefinition.cacheKey(): String =
        when (type) {
            CosmeticType.Emote -> "emote-$id"
            else -> "cosmetic-$id"
        }
}
