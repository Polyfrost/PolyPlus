package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.libs.ktor.client.plugins.timeout
import org.polyfrost.polyplus.libs.ktor.client.request.prepareGet
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsChannel
import org.polyfrost.polyplus.libs.ktor.http.contentLength
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import org.polyfrost.polyplus.libs.ktor.utils.io.readAvailable
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
import org.polyfrost.polyplus.client.PolyPlusCosmeticsConfig
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.bedrock.geometry.BedrockGeometry
import org.polyfrost.polyplus.client.cosmetics.assets.ArchiveLimits
import org.polyfrost.polyplus.client.cosmetics.assets.AssetArchive
import org.polyfrost.polyplus.client.cosmetics.assets.OutOfDiskSpaceException
import org.polyfrost.polyplus.client.cosmetics.assets.RejectedArchiveException
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.assets.detectVerticalTextureFrameCount
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if >= 1.21.1 || = 1.8.9 {
import org.polyfrost.polyplus.client.bedrock.geometry.PlayerModelBone
import org.polyfrost.polyplus.client.cosmetics.assets.AttachedCosmeticParser
import org.polyfrost.polyplus.client.cosmetics.assets.BedrockPlayerGeometryCache
import org.polyfrost.polyplus.client.cosmetics.assets.EmoteAssetParser
import org.polyfrost.polyplus.client.cosmetics.assets.PetAssetParser
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.emotes.Emote
//?}
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.client.utils.HashManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path
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
    private val capes = ConcurrentHashMap<Int, CachedCape>()

    @Volatile
    private var generation = 0

    var installs by mutableIntStateOf(0)
        private set
    //? if >= 1.21.1 || = 1.8.9 {
    private val emotesById = ConcurrentHashMap<Int, Emote>()
    private val attachedById = ConcurrentHashMap<Int, AttachedCosmetic>()
    private val petsById = ConcurrentHashMap<Int, PetDefinition>()
    //?}

    private val parsedHashes = ConcurrentHashMap<Int, String>()

    // cache key to the asset hash whose bundle failed to parse
    // the same bundle would only fail again
    private val failedParses = ConcurrentHashMap<String, String>()

    @JvmStatic
    fun getCapeTexture(uuid: UUID): Identifier? {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Cape, uuid)) return null
        val id = CosmeticCatalog.getActiveId(uuid, BodySlot.Cape) ?: return null
        return capes[id]?.asResource()
    }

    fun getCapeResource(id: Int): Identifier? = capes[id]?.asResource()

    fun isCapeLoaded(id: Int): Boolean = capes.containsKey(id)

    fun isCapeAnimated(id: Int): Boolean = capes[id]?.isAnimated == true

    //? if >= 1.21.1 || = 1.8.9 {
    fun getEmote(emoteId: Int): Emote? = emotesById[emoteId]

    fun getAttachedCosmetic(id: Int): AttachedCosmetic? = attachedById[id]

    fun getPetDefinition(id: Int): PetDefinition? = petsById[id]
    //?}

    fun reset() {
        generation++
        val stale = capes.keys.toList().mapNotNull(capes::remove)
        if (stale.isNotEmpty()) {
            ClientPlatform.runOnMain {
                for (cape in stale) cape.release()
            }
        }
        parsedHashes.clear()
        failedParses.clear()
        //? if >= 1.21.1 || = 1.8.9 {
        emotesById.clear()
        attachedById.clear()
        petsById.clear()
        RemoteTextures.releaseAll()
        BedrockPlayerGeometryCache.reset()
        //?}
    }

    // main thread only, and never while a preview may be showing a cosmetic outside `keep`
    // anything evicted reloads from the disk cache the next time it is needed
    fun trim(keep: Set<Int>, inUse: Set<Identifier>) {
        var evicted = false
        for ((id, cape) in capes) {
            if (id in keep) continue
            capes.remove(id)
            cape.release()
            evicted = true
        }
        //? if >= 1.21.1 || = 1.8.9 {
        // a texture id belongs to one cosmetic id, whose entries are always kept or evicted together
        fun <T : Any> MutableMap<Int, T>.evict(textures: (T) -> List<Identifier>) {
            for ((id, asset) in this) {
                val owned = textures(asset)
                if (id in keep || owned.any(inUse::contains)) continue
                remove(id)
                owned.forEach(RemoteTextures::release)
                evicted = true
            }
        }
        attachedById.evict { listOf(it.texture) }
        petsById.evict { listOf(it.texture) }
        emotesById.evict { emote -> emote.effects.map { it.texture } }
        //?}
        // lets open previews notice what they lost
        if (evicted) installs++
    }

    const val PRELOAD_STEPS_PER_DEFINITION = 2

    private const val MAX_PARALLEL_DOWNLOADS = 8

    private const val CAPE_SHEET_EXTENSION = "sheet"
    private const val APPLE_DOUBLE_PREFIX = "._"

    private const val MIB = 1024L * 1024
    private val CAPE_LIMITS = ArchiveLimits(maxDownloadBytes = 16 * MIB, maxExtractedBytes = 32 * MIB, maxEntries = 16)
    private val MODEL_LIMITS = ArchiveLimits(maxDownloadBytes = 8 * MIB, maxExtractedBytes = 16 * MIB, maxEntries = 64)
    private val EMOTE_LIMITS = ArchiveLimits(maxDownloadBytes = 8 * MIB, maxExtractedBytes = 16 * MIB, maxEntries = 128)

    suspend fun preloadDefinitions(
        definitions: Collection<CosmeticDefinition>,
        trackProgress: Boolean = false,
    ) {
        withContext(Dispatchers.IO) {
            if (!ensureBaseDir()) return@withContext

            try {
                val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)
                val outOfSpace = AtomicBoolean(false)
                definitions.map { definition ->
                    async {
                        if (!outOfSpace.get()) {
                            gate.withPermit {
                                downloadLockFor(definition).withLock {
                                    runSuspendCatching { materializeCosmeticLocked(definition) }
                                        .onFailure { error ->
                                            if (error is OutOfDiskSpaceException) {
                                                if (outOfSpace.compareAndSet(false, true)) {
                                                    LOGGER.error("Out of disk space caching cosmetics; aborting the batch", error)
                                                }
                                            } else {
                                                LOGGER.error("Failed to download cosmetic {}", definition.id, error)
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

                //? if >= 1.21.1 || = 1.8.9 {
                parseLock.withLock { BedrockPlayerGeometryCache.scanCosmeticDirs(baseDir) }
                //?}

                for (definition in definitions) {
                    parseLock.withLock {
                        runCatching { loadCosmeticAssetsLocked(definition) }
                            .onFailure { LOGGER.error("Failed to load cosmetic {}", definition.id, it) }
                    }
                    if (trackProgress) CosmeticLoadProgress.stepAssets()
                }
            } finally {
                hashManager.save()
            }
        }
    }

    private fun ensureBaseDir(): Boolean {
        if (baseDir.exists() || baseDir.mkdirs()) return true
        LOGGER.error("Failed to create cosmetics directory at ${baseDir.absolutePath}")
        return false
    }

    private fun downloadLockFor(definition: CosmeticDefinition): Mutex =
        downloadLocks.computeIfAbsent(definition.cacheKey()) { Mutex() }

    suspend fun ensureCosmeticLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return false
        return ensureLoaded(definition)
    }

    //? if >= 1.21.1 || = 1.8.9 {
    suspend fun ensureEmoteLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getEmoteDefinition(id) ?: return false
        return ensureLoaded(definition)
    }
    //?}

    private fun isLoaded(definition: CosmeticDefinition): Boolean {
        if (definition.type == CosmeticType.Cape) return capes.containsKey(definition.id)
        //? if >= 1.21.1 || = 1.8.9 {
        return when (definition.type) {
            CosmeticType.Emote -> emotesById.containsKey(definition.id)
            CosmeticType.Pet -> petsById.containsKey(definition.id) || attachedById.containsKey(definition.id)
            CosmeticType.Unknown -> false
            else -> attachedById.containsKey(definition.id)
        }
        //?}
    }

    private suspend fun ensureLoaded(definition: CosmeticDefinition): Boolean {
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                if (isLoaded(definition) && hashManager.isCurrent(definition.cacheKey(), definition.hash)) {
                    return@runSuspendCatching true
                }
                if (definition.failedToParse()) return@runSuspendCatching false
                downloadLockFor(definition).withLock { materializeCosmeticLocked(definition) }
                parseLock.withLock { loadCosmeticAssetsLocked(definition) }
                hashManager.save()
                !definition.failedToParse()
            }.getOrElse {
                LOGGER.error("Failed to ensure cosmetic {} is loaded", definition.id, it)
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
            val limits = definition.type.archiveLimits()
            val bytes = download(url, limits.maxDownloadBytes)
            AssetArchive.materialize(bytes, cosmeticDir, limits)
            hashManager.updateHash(hashKey, definition.hash)
        }
    }

    private suspend fun download(url: String, maxBytes: Long): ByteArray =
        PolyPlusClient.HTTP.prepareGet(url) { timeout { requestTimeoutMillis = 60_000 } }.execute { response ->
            if (!response.status.isSuccess()) throw IOException("Downloading $url failed with ${response.status}")
            val length = response.contentLength()
            if (length != null && length > maxBytes) {
                throw RejectedArchiveException("$url is $length bytes, over the $maxBytes byte limit")
            }
            val channel = response.bodyAsChannel()
            val body = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read < 0) break
                if (body.size() + read > maxBytes) {
                    throw RejectedArchiveException("$url is over the $maxBytes byte limit")
                }
                body.write(buffer, 0, read)
            }
            body.toByteArray()
        }

    private fun CosmeticType.archiveLimits(): ArchiveLimits = when (this) {
        CosmeticType.Cape -> CAPE_LIMITS
        CosmeticType.Backpack,
        CosmeticType.Glasses,
        CosmeticType.Wings,
        CosmeticType.Glove,
        CosmeticType.Hat,
        CosmeticType.Aura,
        CosmeticType.Boots,
        CosmeticType.Shoulder,
        CosmeticType.Pet,
        CosmeticType.Unknown -> MODEL_LIMITS
        CosmeticType.Emote -> EMOTE_LIMITS
    }

    private fun loadCosmeticAssetsLocked(definition: CosmeticDefinition) {
        val cosmeticDir = baseDir.resolve(definition.cacheKey()).toPath()
        if (!cosmeticDir.toFile().exists()) return
        if (isLoaded(definition) && parsedHashes[definition.id] == definition.hash) return
        if (definition.failedToParse()) return

        parsedHashes[definition.id] = definition.hash
        val parsed = when (definition.type) {
            CosmeticType.Cape -> loadCape(definition.id, cosmeticDir)
            CosmeticType.Backpack,
            CosmeticType.Glasses,
            CosmeticType.Wings,
            CosmeticType.Glove,
            CosmeticType.Hat,
            CosmeticType.Aura,
            CosmeticType.Boots,
            CosmeticType.Shoulder ->
                //? if >= 1.21.1 || = 1.8.9 {
                loadAttachedCosmetic(definition.id, cosmeticDir, definition.preferredSlot() ?: return)
                //?} else {
                /*skip("Attached cosmetics require Minecraft 1.21.1+")*/
                //?}
            CosmeticType.Unknown -> skip("Ignoring cosmetic ${definition.id} with unknown type/slot")
            //? if >= 1.21.1 || = 1.8.9 {
            CosmeticType.Emote -> loadEmote(definition.id, cosmeticDir)
            CosmeticType.Pet -> loadPet(definition.id, cosmeticDir)
            //?} else {
            /*CosmeticType.Emote -> skip("Emotes require Minecraft 1.21.1+")*/
            /*CosmeticType.Pet -> skip("Pets require Minecraft 1.21.1+")*/
            //?}
        }
        // a download that died halfway may leave a partial bundle behind, which the next attempt replaces
        if (!parsed && hashManager.isCurrent(definition.cacheKey(), definition.hash)) {
            failedParses[definition.cacheKey()] = definition.hash
        }
    }

    private fun CosmeticDefinition.failedToParse(): Boolean = failedParses[cacheKey()] == hash

    private fun skip(reason: String): Boolean {
        LOGGER.warn(reason)
        return false
    }

    private fun installOnMain(stamp: Int, vararg textures: Identifier, install: () -> Unit) {
        ClientPlatform.runOnMain {
            if (stamp != generation) return@runOnMain
            // a trim that evicted an older copy of this cosmetic in the meantime also released its texture id
            if (!textures.all(RemoteTextures::isRegistered)) return@runOnMain
            install()
            installs++
        }
    }

    private fun loadCape(id: Int, dir: Path): Boolean {
        val stamp = generation
        val files = dir.toFile().walkTopDown()
            .filter { it.isFile && !it.name.startsWith(APPLE_DOUBLE_PREFIX) }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()
        val png = files.firstOrNull { it.extension.equals("png", ignoreCase = true) }
            ?: dir.resolve("asset.bin").toFile().takeIf { it.exists() }
            ?: return false

        val image = runCatching { ImageIO.read(png) }.getOrNull()
        if (image == null) {
            LOGGER.warn("Failed to decode cape image for cosmetic {} from {}", id, png)
            return false
        }
        // a sheet's frames are always this size, so this also budgets animated capes
        if (!capeFrameWithinBudget(image.width, image.height)) {
            LOGGER.warn("Ignoring cape for cosmetic {}: a {}x{} cape is too big", id, image.width, image.height)
            return false
        }

        val sheetFile = files.firstOrNull { it.extension.equals(CAPE_SHEET_EXTENSION, ignoreCase = true) }
        val sheet = sheetFile?.let { file ->
            runCatching { ImageIO.read(file) }.getOrNull().also {
                if (it == null) LOGGER.warn("Ignoring cape sheet {} for cosmetic {}: it could not be decoded", file, id)
            }
        }
        val frames = if (sheet == null) {
            1
        } else {
            val detected =
                detectVerticalTextureFrameCount(image.width, image.height, sheet.width, sheet.height, 0f, minFrames = 2)
            when {
                detected < 2 -> {
                    LOGGER.warn(
                        "Ignoring cape sheet {} for cosmetic {}: not a whole-number stack of {}x{} frames",
                        sheetFile,
                        id,
                        image.width,
                        image.height,
                    )
                    1
                }

                else -> detected
            }
        }

        val source = sheet?.takeIf { frames > 1 } ?: image
        installOnMain(stamp) {
            capes[id] = CachedCape(
                id,
                source,
                frames,
                if (frames > 1 && sheetFile != null) capeMillisPerFrameFromName(sheetFile.name) else DEFAULT_MILLIS_PER_FRAME,
            )
        }
        return true
    }

    //? if >= 1.21.1 || = 1.8.9 {
    private fun playerGeometryOrNull(id: Int, dir: Path): BedrockGeometry? {
        BedrockPlayerGeometryCache.tryCaptureFrom(dir)
        BedrockPlayerGeometryCache.ensureFromDisk()
        if (!BedrockPlayerGeometryCache.isReady()) {
            BedrockPlayerGeometryCache.scanCosmeticDirs(baseDir)
        }
        if (!BedrockPlayerGeometryCache.isReady()) {
            LOGGER.warn("Skipping cosmetic {} until player geometry is available", id)
            return null
        }
        return BedrockPlayerGeometryCache.getOrThrow()
    }

    private fun loadAttachedCosmetic(id: Int, dir: Path, slot: BodySlot, scale: Float = 1f, anchor: PlayerModelBone? = null): Boolean {
        val stamp = generation
        val playerGeometry = playerGeometryOrNull(id, dir) ?: return false
        val attached = AttachedCosmeticParser.parse(id, dir, slot, playerGeometry, scale, anchor) ?: return false

        installOnMain(stamp, attached.texture) {
            attachedById[id] = attached
        }
        return true
    }

    private fun loadEmote(id: Int, dir: Path): Boolean {
        val stamp = generation
        val playerGeometry = playerGeometryOrNull(id, dir) ?: return false
        val emote = EmoteAssetParser.parse(id, dir, playerGeometry) ?: run {
            LOGGER.warn("No emotes parsed for cosmetic {}", id)
            return false
        }

        installOnMain(stamp, *emote.effects.map { it.texture }.toTypedArray()) {
            emotesById[id] = emote
        }
        return true
    }

    private fun loadPet(id: Int, dir: Path): Boolean {
        val stamp = generation
        return when (PetAssetParser.peekArchetype(dir)) {
            PetArchetype.Shoulder -> loadAttachedCosmetic(id, dir, BodySlot.Pet, PetAssetParser.peekScale(dir), PetAssetParser.peekAnchor(dir))
            PetArchetype.Flying, PetArchetype.Walking -> {
                val parsed = PetAssetParser.parse(id, dir) ?: run {
                    LOGGER.warn("Failed to parse pet cosmetic {}", id)
                    return false
                }
                installOnMain(stamp, parsed.texture) {
                    petsById[id] = parsed
                }
                true
            }
            null -> skip("Pet cosmetic $id has no valid manifest")
        }
    }
    //?}

    private fun CosmeticDefinition.cacheKey(): String =
        when (type) {
            CosmeticType.Emote -> "emote-$id"
            else -> "cosmetic-$id"
        }

    fun primaryTextureFile(id: Int): File? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return null
        val dir = baseDir.resolve(definition.cacheKey())
        if (!dir.isDirectory) return null
        return dir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(APPLE_DOUBLE_PREFIX) && it.extension.equals("png", ignoreCase = true) }
            .sortedBy { it.invariantSeparatorsPath }
            .firstOrNull()
            ?: dir.resolve("asset.bin").takeIf { it.isFile }
    }
}

private const val MAX_CAPE_FRAME_PIXELS = 1024L * 512

internal fun capeFrameWithinBudget(frameWidth: Int, frameHeight: Int): Boolean =
    frameWidth.toLong() * frameHeight <= MAX_CAPE_FRAME_PIXELS
