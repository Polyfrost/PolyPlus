package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.polyfrost.polyplus.libs.ktor.client.plugins.timeout
import org.polyfrost.polyplus.libs.ktor.client.request.prepareGet
import org.polyfrost.polyplus.libs.ktor.client.statement.bodyAsChannel
import org.polyfrost.polyplus.libs.ktor.http.contentLength
import org.polyfrost.polyplus.libs.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.polyfrost.polyplus.client.cosmetics.assets.ArchiveLimits
import org.polyfrost.polyplus.client.cosmetics.assets.AssetArchive
import org.polyfrost.polyplus.client.cosmetics.assets.CosmeticBundles
import org.polyfrost.polyplus.client.cosmetics.assets.DiskAssetReader
import org.polyfrost.polyplus.client.cosmetics.assets.OutOfDiskSpaceException
import org.polyfrost.polyplus.client.cosmetics.assets.RejectedArchiveException
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.assets.detectVerticalTextureFrameCount
import org.polyfrost.polyplus.client.cosmetics.assets.readAtMost
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalettes
import org.polyfrost.polyplus.client.cosmetics.outfits.SkinLibrary
import org.polyfrost.polyplus.client.cosmetics.render.PaletteTextures
import org.polyfrost.polyplus.client.cosmetics.sets.BonusEffects
import org.polyfrost.polyplus.client.cosmetics.sets.BonusState
import org.polyfrost.polyplus.client.gui.preview.PreviewTryOn
import org.polyfrost.polyplus.client.nameplate.NameplateBundles
import org.polyfrost.polyplus.client.network.http.readLimited
import org.polyfrost.polyplus.client.utils.ClientPlatform
//? if >= 1.21.1 || = 1.8.9 {
import org.polyfrost.polyplus.client.cosmetics.assets.AttachedCosmeticParser
import org.polyfrost.polyplus.client.cosmetics.runtime.AttachedCosmetic
import org.polyfrost.polyplus.client.emotes.Emote
import org.polyfrost.polyplus.client.emotes.EmoteBundles
import org.polyfrost.polyplus.client.pets.PetAssets
//?}
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.BonusRef
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.client.utils.HashManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
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
    private val emotes = ConcurrentHashMap<EmoteRevision, Emote>()
    private val attachedById = ConcurrentHashMap<Int, AttachedCosmetic>()
    private val attachedKeys = ConcurrentHashMap<Int, String>()
    private val bonusEffects = ConcurrentHashMap<String, AttachedCosmetic>()
    private val failedBonuses: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val retiredTextures = RetiredTextures<Identifier>()
    //?}

    private val parsedRevisions = ConcurrentHashMap<Int, CosmeticRevision>()
    private val parsedSources = ConcurrentHashMap<Int, String>()

    private class InstallTicket(
        val id: Int,
        val hash: String,
        val revision: CosmeticRevision,
        val source: String,
        val emote: Boolean,
        val local: Boolean,
    ) {
        val key: Any = if (emote) EmoteRevision(id, hash) else id
        val done = CompletableDeferred<Boolean>()
        var bundleWideFailure = false

        fun sameAs(other: InstallTicket?): Boolean =
            other != null && other.id == id && other.hash == hash && other.revision == revision &&
                other.source == source && other.emote == emote
    }

    private val installing = ConcurrentHashMap<Any, InstallTicket>()
    private val openTickets: MutableSet<InstallTicket> = ConcurrentHashMap.newKeySet()

    // cache key to the asset hash whose bundle failed to parse
    // the same bundle would only fail again
    private val failedParses = ParseFailures()

    private class DownloadFailure(val hash: String, val url: String?, val attempts: Int, val retryAtMs: Long)

    private class DownloadStatusException(message: String, val status: Int) : IOException(message)

    private val failedDownloads = ConcurrentHashMap<String, DownloadFailure>()

    @JvmStatic
    fun getCapeTexture(uuid: UUID): Identifier? {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Cape, uuid)) return null
        val id = CosmeticCatalog.getActiveId(uuid, BodySlot.Cape) ?: return null
        return capes[id]?.asResource()
    }

    fun getCapeResource(id: Int): Identifier? = capes[id]?.asResource()

    fun isCapeLoaded(id: Int): Boolean = capes.containsKey(id)

    fun isCurrent(id: Int): Boolean {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return false
        return isLoaded(definition) && isParsed(definition)
    }

    fun isCapeAnimated(id: Int): Boolean = capes[id]?.isAnimated == true

    fun isEngineCape(id: Int): Boolean =
        //? if >= 1.21.1 || = 1.8.9 {
        attachedById[id]?.slot == BodySlot.Cape
        //?} else {
        /*false*/
        //?}

    @JvmStatic
    fun hasEngineCape(uuid: UUID): Boolean {
        if (!PolyPlusCosmeticsConfig.isVisible(BodySlot.Cape, uuid)) return false
        val id = CosmeticCatalog.getActiveId(uuid, BodySlot.Cape) ?: return false
        return isEngineCape(id)
    }

    //? if >= 1.21.1 || = 1.8.9 {
    fun getEmote(emoteId: Int): Emote? {
        val definition = CosmeticCatalog.getEmoteDefinition(emoteId)
        if (definition != null) return emotes[EmoteRevision.of(definition)]
        return emotes.entries.lastOrNull { it.key.id == emoteId }?.value
    }

    fun getEmote(revision: EmoteRevision): Emote? = emotes[revision]

    fun isEmoteCurrent(emoteId: Int): Boolean {
        val definition = CosmeticCatalog.getEmoteDefinition(emoteId) ?: return true
        if (definition.failedToParse() || definition.downloadDeferred()) return true
        return isParsed(definition)
    }

    fun getAttachedCosmetic(id: Int): AttachedCosmetic? = attachedById[id]

    fun getBonusEffect(ref: BonusRef): AttachedCosmetic? = bonusKey(ref)?.let(bonusEffects::get)
    //?}

    fun reset() = ClientPlatform.runOnMainSync(::resetOnMain)

    private fun resetOnMain() {
        generation++
        val stale = capes.keys.toList().mapNotNull(capes::remove)
        if (stale.isNotEmpty()) {
            ClientPlatform.runOnMain {
                for (cape in stale) cape.release()
            }
        }
        parsedRevisions.clear()
        parsedSources.clear()
        openTickets.toList().forEach { it.abandoned() }
        installing.clear()
        failedParses.clear()
        failedDownloads.clear()
        NameplateBundles.clear()
        //? if >= 1.21.1 || = 1.8.9 {
        emotes.values.forEach { it.scene.close() }
        emotes.clear()
        attachedById.clear()
        attachedKeys.clear()
        bonusEffects.values.forEach { it.scene.close() }
        bonusEffects.clear()
        BonusEffects.reset()
        failedBonuses.clear()
        retiredTextures.clear()
        PetAssets.clear()
        PaletteTextures.clear()
        RemoteTextures.releaseAll()
        SkinLibrary.onTexturesReleased()
        MojangCapeService.onTexturesReleased()
        //?}
        installs++
    }

    // main thread only, and never while a preview may be showing a cosmetic outside `keep`
    // anything evicted reloads from the disk cache the next time it is needed
    fun trim(keep: Set<Int>, inUse: Set<Identifier>, keepBonuses: Collection<BonusRef>, keepEmotes: Set<EmoteRevision> = emptySet()) {
        var evicted = false
        for ((id, cape) in capes) {
            if (id in keep) continue
            capes.remove(id)
            cape.release()
            evicted = true
        }
        //? if >= 1.21.1 || = 1.8.9 {
        // a texture id belongs to one cosmetic id, whose entries are always kept or evicted together
        fun <K : Any, T : Any> MutableMap<K, T>.evict(kept: Set<K>, freed: (T) -> Unit = {}, textures: (T) -> List<Identifier>) {
            for ((id, asset) in this) {
                val owned = textures(asset)
                if (id in kept || owned.any(inUse::contains)) continue
                remove(id)
                owned.forEach(RemoteTextures::release)
                freed(asset)
                evicted = true
            }
        }
        val dropped = ArrayList<AttachedCosmetic>()
        for ((id, attached) in attachedById) {
            if (id in keep || attached.textures().any(inUse::contains)) continue
            attachedById.remove(id)
            attachedKeys.remove(id)
            dropped += attached
            evicted = true
        }
        if (dropped.isNotEmpty()) {
            val shared = attachedById.values.flatMapTo(HashSet()) { it.textures() }
            dropped.flatMapTo(HashSet()) { it.textures() }.filterNot(shared::contains).forEach {
                retiredTextures.forget(it)
                RemoteTextures.release(it)
            }
        }
        if (PetAssets.trim(keep)) evicted = true
        val keptEmotes = HashSet(keepEmotes)
        keptEmotes += CosmeticCatalog.pinnedEmoteRevisions()
        emotes.keys.filterTo(keptEmotes) { it.id in keep }
        emotes.evict(keptEmotes, { it.scene.close() }) { listOfNotNull(it.propsTexture) + it.scene.effectTextures }
        bonusEffects.evict(keepBonuses.mapNotNullTo(HashSet(), ::bonusKey), { it.scene.close() }) { it.textures() }
        BonusEffects.retainLoaded()
        //?}
        if (NameplateBundles.retain(keep)) evicted = true
        // lets open previews notice what they lost
        if (evicted) installs++
    }

    const val PRELOAD_STEPS_PER_DEFINITION = 2

    private const val MAX_PARALLEL_DOWNLOADS = 8

    private const val CAPE_SHEET_EXTENSION = "sheet"
    private const val CAPE_TEXTURE = "cape.png"
    private const val MANIFEST = "manifest.json"
    private val CAPE_HELPER_SUFFIXES = listOf(".idx.png", "_glow.png", ".glow.png", "_emissive.png", ".emissive.png")
    private const val APPLE_DOUBLE_PREFIX = "._"

    private const val MIB = 1024L * 1024
    private val CAPE_LIMITS = ArchiveLimits(maxDownloadBytes = 16 * MIB, maxExtractedBytes = 32 * MIB, maxEntries = 16)
    private val MODEL_LIMITS = ArchiveLimits(maxDownloadBytes = 8 * MIB, maxExtractedBytes = 16 * MIB, maxEntries = 64)
    private val EMOTE_LIMITS = ArchiveLimits(maxDownloadBytes = 8 * MIB, maxExtractedBytes = 16 * MIB, maxEntries = 128)
    private val NAMEPLATE_LIMITS =
        ArchiveLimits(maxDownloadBytes = 2 * MIB, maxExtractedBytes = 8 * MIB, maxEntries = 128, maxEntryBytes = MIB)
    private val EFFECT_LIMITS = ArchiveLimits(maxDownloadBytes = 4 * MIB, maxExtractedBytes = 8 * MIB, maxEntries = 128)
    private val BONUS_SLOT = BodySlot.EffectA

    private const val NAMEPLATE_BUNDLE_FILE = "bundle.zip"
    private const val ASSET_DIGEST_BYTES = 8

    private const val DISK_EVICT_GRACE_MS = 10 * 60_000L
    private val evicting = AtomicBoolean(false)
    private const val DOWNLOAD_RETRY_BASE_MS = 5_000L
    private const val DOWNLOAD_RETRY_MAX_MS = 300_000L

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

                for (definition in definitions) {
                    loadLockFor(definition).withLock {
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

    suspend fun evictObsoleteDirs() = withContext(Dispatchers.IO) {
        if (!CosmeticCatalog.isCatalogListed() || !CosmeticCatalog.isOwnershipListed()) return@withContext
        if (!evicting.compareAndSet(false, true)) return@withContext
        try {
            val removed = parseLock.withLock {
                if (!CosmeticCatalog.isOwnershipListed()) return@withLock 0
                val keep = keptCacheKeys()
                val names = baseDir.list()?.toList().orEmpty()
                val now = System.currentTimeMillis()
                val obsolete = CacheEviction.obsolete(names, keep) { now - baseDir.resolve(it).lastModified() < DISK_EVICT_GRACE_MS }
                var removed = 0
                for (name in obsolete) {
                    val lock = downloadLocks.computeIfAbsent(name) { Mutex() }
                    if (!lock.tryLock()) continue
                    try {
                        if (baseDir.resolve(name).deleteRecursively()) {
                            hashManager.remove(name)
                            failedParses.forget(name)
                            removed++
                        }
                    } finally {
                        lock.unlock()
                    }
                }
                for (key in hashManager.keys()) {
                    if (CacheEviction.managed(key) && key !in keep && !baseDir.resolve(key).exists()) hashManager.remove(key)
                }
                removed
            }
            hashManager.save()
            if (removed > 0) LOGGER.info("Removed {} outdated cosmetic download(s) from disk", removed)
        } finally {
            evicting.set(false)
        }
    }

    private fun keptCacheKeys(): Set<String> {
        val keep = HashSet<String>()
        CosmeticCatalog.allDefinitions().mapTo(keep) { it.cacheKey() }
        CosmeticCatalog.pinnedEmoteRevisions().mapTo(keep) { emoteCacheKey(it.id, it.hash) }
        NameplateBundles.loaded().mapTo(keep) { it.key }
        //? if >= 1.21.1 || = 1.8.9 {
        emotes.keys.mapTo(keep) { emoteCacheKey(it.id, it.hash) }
        keep += bonusEffects.keys
        (BonusState.activeRefs() + PreviewTryOn.previewBonuses()).mapNotNullTo(keep, ::bonusKey)
        //?}
        return keep
    }

    private fun ensureBaseDir(): Boolean {
        if (baseDir.exists() || baseDir.mkdirs()) return true
        LOGGER.error("Failed to create cosmetics directory at ${baseDir.absolutePath}")
        return false
    }

    private fun downloadLockFor(definition: CosmeticDefinition): Mutex =
        downloadLocks.computeIfAbsent(definition.cacheKey()) { Mutex() }

    private fun loadLockFor(definition: CosmeticDefinition): Mutex =
        if (definition.type == CosmeticType.Nameplate) downloadLockFor(definition) else parseLock

    suspend fun ensureCosmeticLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return false
        return ensureLoaded(definition)
    }

    suspend fun installLocal(definition: CosmeticDefinition, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runSuspendCatching {
            if (!ensureBaseDir()) return@runSuspendCatching false
            val key = definition.cacheKey()
            val dir = baseDir.resolve(key).toPath()
            downloadLockFor(definition).withLock {
                dir.toFile().deleteRecursively()
                if (definition.type == CosmeticType.Nameplate) {
                    storeNameplateBundle(bytes, dir)
                } else {
                    AssetArchive.materialize(bytes, dir, definition.type.archiveLimits())
                }
                hashManager.updateHash(key, definition.hash)
            }
            parsedRevisions.remove(definition.id)
            parsedSources.remove(definition.id)
            installing.remove(installKey(definition))
            failedParses.forget(key)
            failedDownloads.remove(key)
            val ticket = loadLockFor(definition).withLock { loadCosmeticAssetsLocked(definition, local = true) }
            hashManager.save()
            ticket?.done?.await()
            !definition.failedToParse() && isLoaded(definition) && isParsed(definition)
        }.getOrElse {
            LOGGER.error("Failed to install local cosmetic {}", definition.id, it)
            false
        }
    }

    //? if >= 1.21.1 || = 1.8.9 {
    suspend fun ensureEmoteLoaded(id: Int): Boolean {
        val definition = CosmeticCatalog.getEmoteDefinition(id) ?: return false
        return ensureLoaded(definition)
    }

    suspend fun ensureEmoteLoaded(revision: EmoteRevision): Boolean {
        val definition = CosmeticCatalog.getEmoteDefinition(revision) ?: return false
        return ensureLoaded(definition)
    }
    //?}

    suspend fun reloadChanged(definitions: Collection<CosmeticDefinition>) {
        val changed = definitions.filter {
            isPresent(it) && !(isLoaded(it) && isParsed(it))
        }
        if (changed.isNotEmpty()) preloadDefinitions(changed)
    }

    private fun isParsed(definition: CosmeticDefinition): Boolean {
        if (definition.type == CosmeticType.Emote) return isLoaded(definition)
        return parsedRevisions[definition.id] == CosmeticRevision.of(definition) &&
            parsedSources[definition.id] == definition.cacheKey()
    }

    private fun isPresent(definition: CosmeticDefinition): Boolean = when (definition.type) {
        CosmeticType.Nameplate -> NameplateBundles.get(definition.id) != null
        //? if >= 1.21.1 || = 1.8.9 {
        CosmeticType.Emote -> emotes.keys.any { it.id == definition.id }
        //?}
        else -> isLoaded(definition)
    }

    private fun isLoaded(definition: CosmeticDefinition): Boolean {
        if (definition.type == CosmeticType.Cape) return capes.containsKey(definition.id) || isEngineCape(definition.id)
        if (definition.type == CosmeticType.Nameplate) return NameplateBundles.isLoaded(definition.id, definition.hash, definition.variantKey)
        //? if >= 1.21.1 || = 1.8.9 {
        return when (definition.type) {
            CosmeticType.Emote -> emotes.containsKey(EmoteRevision.of(definition))
            CosmeticType.Pet -> PetAssets.has(definition.id)
            CosmeticType.Unknown -> false
            else -> attachedById.containsKey(definition.id)
        }
        //?}
    }

    private suspend fun ensureLoaded(definition: CosmeticDefinition): Boolean {
        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                if (definition.failedToParse()) return@runSuspendCatching false
                if (isLoaded(definition) && isParsed(definition) &&
                    hashManager.isCurrent(definition.cacheKey(), definition.hash)
                ) {
                    return@runSuspendCatching true
                }
                if (definition.downloadDeferred()) return@runSuspendCatching false
                val materialized = downloadLockFor(definition).withLock { materializeCosmeticLocked(definition) }
                if (!materialized) return@runSuspendCatching false
                val ticket = loadLockFor(definition).withLock { loadCosmeticAssetsLocked(definition) }
                hashManager.save()
                ticket?.done?.await()
                !definition.failedToParse() && isLoaded(definition) && isParsed(definition)
            }.getOrElse {
                LOGGER.error("Failed to ensure cosmetic {} is loaded", definition.id, it)
                false
            }
        }
    }

    private suspend fun materializeCosmeticLocked(definition: CosmeticDefinition): Boolean {
        val url = definition.url
        val cosmeticDir = baseDir.resolve(definition.cacheKey()).toPath()
        val hashKey = definition.cacheKey()
        val hashChanged = !hashManager.isCurrent(hashKey, definition.hash)
        val needsDownload = hashChanged || !cosmeticDir.toFile().exists()

        if (url == null) {
            if (!needsDownload) return true
            if (definition.downloadDeferred()) return false
            val failure = recordDownloadFailure(hashKey, definition.hash, null, null)
            if (failure.attempts == 1 && definition.type != CosmeticType.Cape) LOGGER.warn("Cosmetic {} has no download URL", definition.id)
            return false
        }

        if (needsDownload) {
            if (definition.downloadDeferred()) return false
            val cosmeticDirFile = cosmeticDir.toFile()
            if (cosmeticDirFile.exists()) {
                cosmeticDirFile.deleteRecursively()
            }
            val limits = definition.type.archiveLimits()
            try {
                val bytes = download(url, limits.maxDownloadBytes)
                verifyHash(definition.hash, "Cosmetic ${definition.id}", bytes)
                if (definition.type == CosmeticType.Nameplate) {
                    storeNameplateBundle(bytes, cosmeticDir)
                } else {
                    AssetArchive.materialize(bytes, cosmeticDir, limits)
                }
            } catch (error: Exception) {
                if (error !is CancellationException) recordDownloadFailure(hashKey, definition.hash, url, error)
                throw error
            }
            failedDownloads.remove(hashKey)
            hashManager.updateHash(hashKey, definition.hash)
        }
        return true
    }

    private fun CosmeticDefinition.downloadDeferred(): Boolean {
        val failure = failedDownloads[cacheKey()] ?: return false
        return failure.hash == hash && failure.url == url && System.currentTimeMillis() < failure.retryAtMs
    }

    private fun recordDownloadFailure(key: String, hash: String, url: String?, error: Exception?): DownloadFailure {
        val permanent = error is RejectedArchiveException ||
            (error is DownloadStatusException && error.status in 400..499 && error.status != 408 && error.status != 429)
        return failedDownloads.compute(key) { _, previous ->
            val attempts = if (previous != null && previous.hash == hash && previous.url == url) previous.attempts + 1 else 1
            val retryAtMs = if (permanent) {
                Long.MAX_VALUE
            } else {
                System.currentTimeMillis() + minOf(DOWNLOAD_RETRY_BASE_MS shl minOf(attempts - 1, 10), DOWNLOAD_RETRY_MAX_MS)
            }
            DownloadFailure(hash, url, attempts, retryAtMs)
        }!!
    }

    private fun storeNameplateBundle(bytes: ByteArray, dir: Path) {
        AssetArchive.verify(bytes, NAMEPLATE_LIMITS)
        Files.createDirectories(dir)
        val partial = dir.resolve("$NAMEPLATE_BUNDLE_FILE.part")
        Files.write(partial, bytes)
        Files.move(partial, dir.resolve(NAMEPLATE_BUNDLE_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun verifyHash(expected: String, label: String, bytes: ByteArray) {
        if (!CosmeticBundles.isContentHash(expected)) return
        val actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
        if (!actual.equals(expected, ignoreCase = true)) {
            throw RejectedArchiveException("$label does not match its hash")
        }
    }

    private suspend fun download(url: String, maxBytes: Long): ByteArray =
        PolyPlusClient.HTTP.prepareGet(url) { timeout { requestTimeoutMillis = 60_000 } }.execute { response ->
            if (!response.status.isSuccess()) throw DownloadStatusException("Downloading $url failed with ${response.status}", response.status.value)
            val length = response.contentLength()
            if (length != null && length > maxBytes) {
                throw RejectedArchiveException("$url is $length bytes, over the $maxBytes byte limit")
            }
            response.bodyAsChannel().readLimited(maxBytes)
                ?: throw RejectedArchiveException("$url is over the $maxBytes byte limit")
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
        CosmeticType.Effect -> EFFECT_LIMITS
        CosmeticType.Emote -> EMOTE_LIMITS
        CosmeticType.Nameplate -> NAMEPLATE_LIMITS
    }

    private fun loadCosmeticAssetsLocked(definition: CosmeticDefinition, local: Boolean = false): InstallTicket? {
        val cosmeticDir = baseDir.resolve(definition.cacheKey()).toPath()
        if (!cosmeticDir.toFile().exists()) return null
        if (isLoaded(definition) && isParsed(definition)) return null
        if (definition.failedToParse()) return null

        val ticket = InstallTicket(
            definition.id,
            definition.hash,
            CosmeticRevision.of(definition),
            definition.cacheKey(),
            definition.type == CosmeticType.Emote,
            local,
        )
        val pending = installing[ticket.key]
        if (ticket.sameAs(pending)) return pending
        if (!ticket.wanted()) return null
        openTickets += ticket
        installing[ticket.key] = ticket
        val parsed = try {
            parseAndInstall(definition, cosmeticDir, ticket)
        } catch (error: Throwable) {
            ticket.abandoned()
            throw error
        }
        if (parsed != true) ticket.abandoned()
        // a download that died halfway may leave a partial bundle behind, which the next attempt replaces
        if (parsed == false && hashManager.isCurrent(definition.cacheKey(), definition.hash)) {
            failedParses.record(definition.cacheKey(), definition.hash, definition.failureVariant(), ticket.bundleWideFailure)
        }
        return ticket
    }

    private fun installKey(definition: CosmeticDefinition): Any =
        if (definition.type == CosmeticType.Emote) EmoteRevision.of(definition) else definition.id

    private fun parseAndInstall(definition: CosmeticDefinition, cosmeticDir: Path, ticket: InstallTicket): Boolean? {
        if (definition.variantIndependent(cosmeticDir)) ticket.bundleWideFailure = true
        return when (definition.type) {
            CosmeticType.Cape ->
                if (!Files.isRegularFile(cosmeticDir.resolve(MANIFEST))) {
                    loadCape(ticket, cosmeticDir)
                } else {
                    //? if >= 1.21.1 || = 1.8.9 {
                    loadAttachedCosmetic(definition, cosmeticDir, BodySlot.Cape, ticket)
                    //?} else {
                    /*skip("Capes with a manifest require Minecraft 1.21.1+")*/
                    //?}
                }
            CosmeticType.Backpack,
            CosmeticType.Glasses,
            CosmeticType.Wings,
            CosmeticType.Glove,
            CosmeticType.Hat,
            CosmeticType.Aura,
            CosmeticType.Boots,
            CosmeticType.Shoulder ->
                //? if >= 1.21.1 || = 1.8.9 {
                loadAttachedCosmetic(definition, cosmeticDir, definition.preferredSlot() ?: return null, ticket)
                //?} else {
                /*skip("Attached cosmetics require Minecraft 1.21.1+")*/
                //?}
            CosmeticType.Unknown -> skip("Ignoring cosmetic ${definition.id} with unknown type/slot")
            CosmeticType.Effect ->
                //? if >= 1.21.1 || = 1.8.9 {
                loadAttachedCosmetic(definition, cosmeticDir, definition.preferredSlot() ?: return null, ticket)
                //?} else {
                /*skip("Effect cosmetics require Minecraft 1.21.1+")*/
                //?}
            CosmeticType.Nameplate -> loadNameplate(definition, cosmeticDir, ticket)
            //? if >= 1.21.1 || = 1.8.9 {
            CosmeticType.Emote -> loadEmote(definition.id, definition.hash, cosmeticDir, ticket)
            CosmeticType.Pet -> loadPet(definition, cosmeticDir, ticket)
            //?} else {
            /*CosmeticType.Emote -> skip("Emotes require Minecraft 1.21.1+")*/
            /*CosmeticType.Pet -> skip("Pets require Minecraft 1.21.1+")*/
            //?}
        }
    }

    private fun CosmeticDefinition.variantIndependent(dir: Path): Boolean = when (type) {
        CosmeticType.Nameplate, CosmeticType.Emote, CosmeticType.Unknown -> true
        CosmeticType.Cape -> !Files.isRegularFile(dir.resolve(MANIFEST))
        else -> false
    }

    private fun CosmeticDefinition.failedToParse(): Boolean = failedParses.failed(cacheKey(), hash, failureVariant())

    private fun CosmeticDefinition.failureVariant(): String? = variantKey?.let { CosmeticRevision.of(this).tag }

    private fun skip(reason: String): Boolean {
        LOGGER.warn(reason)
        return false
    }

    private fun InstallTicket.installed() {
        if (!emote) {
            parsedRevisions[id] = revision
            parsedSources[id] = source
        }
        installing.remove(key, this)
        openTickets -= this
        done.complete(true)
    }

    private fun InstallTicket.abandoned() {
        installing.remove(key, this)
        openTickets -= this
        done.complete(false)
    }

    private fun InstallTicket.wanted(): Boolean {
        if (emote || local) return true
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return true
        return CosmeticRevision.of(definition) == revision && definition.cacheKey() == source
    }

    private fun InstallTicket.current(): Boolean = installing[key] === this && wanted()

    private fun installOnMain(
        stamp: Int,
        ticket: InstallTicket?,
        vararg textures: Identifier,
        discard: () -> Unit = {},
        install: () -> Unit,
    ) {
        try {
            ClientPlatform.runOnMain {
                if (stamp != generation || ticket?.current() == false || !textures.all(RemoteTextures::isRegistered)) {
                    discard()
                    ticket?.abandoned()
                    return@runOnMain
                }
                try {
                    install()
                } catch (error: Exception) {
                    LOGGER.error("Failed to install cosmetic {}", ticket?.id, error)
                    ticket?.abandoned()
                    return@runOnMain
                }
                ticket?.installed()
                installs++
            }
        } catch (error: Throwable) {
            ticket?.abandoned()
            throw error
        }
    }

    private fun loadNameplate(definition: CosmeticDefinition, dir: Path, ticket: InstallTicket): Boolean {
        val stamp = generation
        val file = dir.resolve(NAMEPLATE_BUNDLE_FILE)
        if (!Files.isRegularFile(file)) return skip("Nameplate cosmetic ${definition.id} has no bundle")
        val key = definition.cacheKey()
        installOnMain(stamp, ticket) {
            NameplateBundles.install(definition.id, definition.variantKey, key, definition.hash, file)
        }
        return true
    }

    private fun loadCape(ticket: InstallTicket, dir: Path): Boolean {
        val id = ticket.id
        val stamp = generation
        val files = dir.toFile().walkTopDown()
            .filter { it.isFile && !it.name.startsWith(APPLE_DOUBLE_PREFIX) }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()
        val png = capeTexture(files)
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
                detectVerticalTextureFrameCount(image.width, image.height, sheet.width, sheet.height)
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
        installOnMain(stamp, ticket) {
            //? if >= 1.21.1 || = 1.8.9 {
            dropAttached(id)
            //?}
            capes.put(
                id,
                CachedCape(
                    id,
                    source,
                    frames,
                    if (frames > 1 && sheetFile != null) capeMillisPerFrameFromName(sheetFile.name) else DEFAULT_MILLIS_PER_FRAME,
                ),
            )?.release()
        }
        return true
    }

    private fun capeTexture(files: List<File>): File? {
        val pngs = files.filter { file ->
            val name = file.name.lowercase()
            name.endsWith(".png") && CAPE_HELPER_SUFFIXES.none(name::endsWith)
        }
        return pngs.firstOrNull { it.name.equals(CAPE_TEXTURE, ignoreCase = true) } ?: pngs.firstOrNull()
    }

    //? if >= 1.21.1 || = 1.8.9 {
    private fun dropAttached(id: Int) {
        val dropped = attachedById.remove(id) ?: return
        attachedKeys.remove(id)
        retiredTextures.retire(dropped.textures())
    }

    fun releaseRetired(worn: Collection<AttachedCosmetic>) {
        if (retiredTextures.isEmpty()) return
        sweepRetired(worn.flatMapTo(HashSet()) { it.textures() })
    }

    private fun sweepRetired(held: Set<Identifier>) {
        if (retiredTextures.isEmpty()) return
        val current = attachedById.values.flatMapTo(HashSet()) { it.textures() }
        retiredTextures.sweep(current, held, RemoteTextures::release)
    }

    private fun loadAttachedCosmetic(definition: CosmeticDefinition, dir: Path, slot: BodySlot, ticket: InstallTicket): Boolean {
        val stamp = generation
        val id = definition.id
        val key = shareKey(definition.cacheKey(), dir, definition.variantKey)
        val shared = sharedAttached(definition, key, slot, dir)
        if (shared != null) {
            installOnMain(stamp, ticket, *shared.textures().toTypedArray()) {
                replaceAttached(id, shared, key, slot)
            }
            return true
        }
        val parsed = AttachedCosmeticParser.parse(
            id,
            dir,
            slot,
            hash = definition.hash,
            variantKey = definition.variantKey,
            revision = CosmeticRevision.of(definition),
            bundleFailure = { ticket.bundleWideFailure = true },
        )
            ?: return false
        installOnMain(stamp, ticket, discard = parsed::discard) {
            replaceAttached(id, parsed.publish(), key, slot)
        }
        return true
    }

    private fun replaceAttached(id: Int, attached: AttachedCosmetic, key: String, slot: BodySlot) {
        if (slot == BodySlot.Cape) capes.remove(id)?.release()
        val previous = attachedById.put(id, attached)
        attachedKeys[id] = key
        if (previous == null || previous === attached) return
        retiredTextures.retire(previous.textures())
    }

    private fun shareKey(cacheKey: String, dir: Path, variantKey: String?): String {
        val texture = DiskAssetReader.findTexture(dir, variantKey)?.let { dir.relativize(it).toString().replace('\\', '/') }
        return "$cacheKey|${texture.orEmpty()}"
    }

    private fun sharedAttached(definition: CosmeticDefinition, key: String, slot: BodySlot, dir: Path): AttachedCosmetic? {
        val sibling = attachedById.entries.firstOrNull { (id, attached) ->
            id != definition.id && attachedKeys[id] == key && attached.slot == slot &&
                parsedRevisions[id]?.sameContent(CosmeticRevision.of(definition)) == true && parsedSources[id] == definition.cacheKey() &&
                attached.textures().all(RemoteTextures::isRegistered)
        }?.value ?: return null
        return sibling.copy(
            id = AttachedCosmeticParser.attachedCosmeticId(definition.id),
            cosmeticId = definition.id,
            colorway = sibling.scene.colorwayIndex(CosmeticPalettes.designedColorway(definition.id, dir, definition.variantKey)),
        )
    }

    private fun AttachedCosmetic.textures(): List<Identifier> = listOf(texture) + scene.effectTextures

    suspend fun ensureBonusLoaded(ref: BonusRef): Boolean = withContext(Dispatchers.IO) {
        val key = bonusKey(ref) ?: return@withContext false
        if (bonusEffects.containsKey(key)) return@withContext true
        if (key in failedBonuses) return@withContext false
        val url = ref.url ?: return@withContext false
        runSuspendCatching {
            if (!ensureBaseDir()) return@runSuspendCatching false
            val dir = baseDir.resolve(key).toPath()
            downloadLocks.computeIfAbsent(key) { Mutex() }.withLock {
                if (!hashManager.isCurrent(key, ref.hash) || !dir.toFile().exists()) {
                    dir.toFile().deleteRecursively()
                    val bytes = download(url, EFFECT_LIMITS.maxDownloadBytes)
                    verifyHash(ref.hash, "Bonus ${ref.id}", bytes)
                    AssetArchive.materialize(bytes, dir, EFFECT_LIMITS)
                    hashManager.updateHash(key, ref.hash)
                    hashManager.save()
                }
            }
            val stamp = generation
            val parsed = parseLock.withLock {
                AttachedCosmeticParser.parse(-ref.id, dir, BONUS_SLOT, hash = ref.hash, name = "bonuses/$key", revision = CosmeticRevision.of(ref.hash, ref.url))
            }
            if (parsed == null) {
                failedBonuses += key
                return@runSuspendCatching false
            }
            parsed.cosmetic.scene.bonus = true
            installOnMain(stamp, null, discard = parsed::discard) {
                bonusEffects[key] = parsed.publish()
                if (!bonusWanted(key)) CosmeticSync.assetsDeparted()
            }
            true
        }.getOrElse {
            if (it is RejectedArchiveException) failedBonuses += key
            LOGGER.error("Failed to load the effect of bonus {}", ref.id, it)
            false
        }
    }

    private fun bonusKey(ref: BonusRef): String? = contentDigest(ref.hash, ref.url)?.let { "bonus-$it" }

    private fun bonusWanted(key: String): Boolean =
        (BonusState.activeRefs() + PreviewTryOn.previewBonuses()).any { bonusKey(it) == key }

    private fun loadEmote(id: Int, hash: String, dir: Path, ticket: InstallTicket): Boolean {
        val stamp = generation
        val emote = EmoteBundles.fromDirectory(id, hash, dir) ?: run {
            LOGGER.warn("No emotes parsed for cosmetic {}", id)
            return false
        }

        val revision = EmoteRevision(id, hash)
        installOnMain(
            stamp,
            ticket,
            *listOfNotNull(emote.propsTexture).toTypedArray(),
            *emote.scene.effectTextures.toTypedArray(),
            discard = {
                val installed = emotes[revision]
                if (installed !== emote) emote.scene.close()
                if (installed == null) (listOfNotNull(emote.propsTexture) + emote.scene.effectTextures).forEach(RemoteTextures::release)
            },
        ) {
            emotes.put(revision, emote)?.takeIf { it !== emote }?.scene?.close()
        }
        return true
    }

    private fun loadPet(definition: CosmeticDefinition, dir: Path, ticket: InstallTicket): Boolean {
        val stamp = generation
        val id = definition.id
        val variants = listOfNotNull(definition.variantKey, definition.variantName)
        val parsed = PetAssets.parse(id, CosmeticRevision.of(definition), dir, variants, definition.variantKey) ?: return skip("Failed to parse pet cosmetic $id")
        installOnMain(stamp, ticket, discard = { parsed.close() }) {
            check(PetAssets.install(parsed)) { "Could not upload pet cosmetic $id" }
        }
        return true
    }
    //?}

    private fun CosmeticDefinition.cacheKey(): String =
        when (type) {
            CosmeticType.Emote -> emoteCacheKey(id, hash)
            CosmeticType.Nameplate -> "nameplate-$groupId-${assetDigest(hash, url)}"
            else -> "bundle-${contentDigest(hash, url) ?: "id-$id"}"
        }

    private fun emoteCacheKey(id: Int, hash: String): String = "emote-$id-${assetDigest(hash.lowercase(), null)}"

    private fun contentDigest(hash: String, url: String?): String? = when {
        CosmeticBundles.isContentHash(hash) -> assetDigest(hash.lowercase(), null)
        url != null -> assetDigest("", url)
        else -> null
    }

    private fun assetDigest(hash: String, url: String?): String {
        val source = "$hash|${url?.let(CosmeticRevision::stableUrl).orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return HexFormat.of().formatHex(digest, 0, ASSET_DIGEST_BYTES)
    }

    fun readBundleFile(id: Int, name: String): ByteArray? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return null
        val dir = baseDir.resolve(definition.cacheKey())
        if (definition.type == CosmeticType.Nameplate) {
            val bundle = dir.resolve(NAMEPLATE_BUNDLE_FILE).takeIf { it.isFile } ?: return null
            return ZipFile(bundle).use { zip ->
                val entry = zip.getEntry(name) ?: zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith("/$name") }
                    .minByOrNull { it.name.count { c -> c == '/' } }
                entry?.let { found -> zip.getInputStream(found).use { it.readAtMost(NAMEPLATE_LIMITS.maxEntryBytes.toInt()) } }
            }
        }
        if (!dir.isDirectory) return null
        dir.resolve(name).takeIf { it.isFile }?.let { return it.readBytes() }
        return dir.walkTopDown()
            .filter { it.isFile && it.name == name }
            .minByOrNull { it.invariantSeparatorsPath.count { c -> c == '/' } }
            ?.readBytes()
    }

    fun primaryTextureFile(id: Int): File? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return null
        val dir = baseDir.resolve(definition.cacheKey())
        if (!dir.isDirectory) return null
        val pngs = dir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(APPLE_DOUBLE_PREFIX) && it.extension.equals("png", ignoreCase = true) }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()
        val picked = if (definition.type == CosmeticType.Cape) capeTexture(pngs) else pngs.firstOrNull()
        return picked ?: dir.resolve("asset.bin").takeIf { it.isFile }
    }
}

private const val MAX_CAPE_FRAME_PIXELS = 1024L * 512

internal fun capeFrameWithinBudget(frameWidth: Int, frameHeight: Int): Boolean =
    frameWidth.toLong() * frameHeight <= MAX_CAPE_FRAME_PIXELS
