package org.polyfrost.polyplus.client.cosmetics

import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import org.polyfrost.polyplus.client.utils.ClientPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.assets.AssetArchive
import org.polyfrost.polyplus.client.cosmetics.assets.OutOfDiskSpaceException
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.imageio.ImageIO

object CosmeticAssetCache {
    private val LOGGER = LogManager.getLogger()
    private val downloadLocks = ConcurrentHashMap<String, Mutex>()

    private val parseLock = Mutex()

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

    const val PRELOAD_STEPS_PER_DEFINITION = 2

    private const val MAX_PARALLEL_DOWNLOADS = 8

    suspend fun preloadDefinitions(
        definitions: Collection<CosmeticDefinition>,
        trackProgress: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            hashManager.awaitHashes()
            if (!ensureBaseDir()) return@withContext

            try {
                val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)
                val outOfSpace = AtomicBoolean(false)
                definitions.map { definition ->
                    async {
                        if (!outOfSpace.get()) {
                            gate.withPermit {
                                downloadLockFor(definition).withLock {
                                    runCatching { materializeCosmeticLocked(definition) }
                                        .onFailure { error ->
                                            if (error is OutOfDiskSpaceException) {
                                                if (outOfSpace.compareAndSet(false, true)) {
                                                    LOGGER.error("Out of disk space caching cosmetics; aborting the batch", error)
                                                    org.polyfrost.polyplus.client.PolyPlusSentry.capture(error)
                                                }
                                            } else {
                                                LOGGER.error("Failed to download cosmetic {}", definition.id, error)
                                                org.polyfrost.polyplus.client.PolyPlusSentry.capture(error)
                                            }
                                        }
                                }
                            }
                        }
                        if (trackProgress) CosmeticLoadProgress.stepAssets()
                    }
                }.awaitAll()

                if (outOfSpace.get()) {
                    CosmeticLoadProgress.fail("Out of disk space while downloading cosmetics")
                    return@withContext
                }

                //? if >= 1.21.1 {
                parseLock.withLock { BedrockPlayerGeometryCache.scanCosmeticDirs(baseDir) }
                //?}

                for (definition in definitions) {
                    parseLock.withLock {
                        runCatching { loadCosmeticAssetsLocked(definition) }
                            .onFailure { LOGGER.error("Failed to load cosmetic {}", definition.id, it); org.polyfrost.polyplus.client.PolyPlusSentry.capture(it) }
                    }
                    if (trackProgress) CosmeticLoadProgress.stepAssets()
                }
            } finally {
                hashManager.saveHashes()
            }
        }
    }

    private fun ensureBaseDir(): Boolean {
        if (baseDir.exists() || baseDir.mkdirs()) return true
        LOGGER.error("Failed to create cosmetics directory at ${baseDir.absolutePath}")
        org.polyfrost.polyplus.client.PolyPlusSentry.captureMessage("Failed to create cosmetics directory at ${baseDir.absolutePath}")
        return false
    }

    private fun downloadLockFor(definition: CosmeticDefinition): Mutex =
        downloadLocks.computeIfAbsent(definition.cacheKey()) { Mutex() }

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
            runCatching {
                hashManager.awaitHashes()
                downloadLockFor(definition).withLock { materializeCosmeticLocked(definition) }
                parseLock.withLock { loadCosmeticAssetsLocked(definition) }
                hashManager.saveHashes()
                true
            }.getOrElse {
                LOGGER.error("Failed to ensure cosmetic {} is loaded", definition.id, it)
                org.polyfrost.polyplus.client.PolyPlusSentry.capture(it)
                false
            }
        }
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
