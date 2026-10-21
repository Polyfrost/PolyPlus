package org.polyfrost.polyplus.client.cosmetics.outfits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangSkin
import org.polyfrost.polyplus.client.cosmetics.MojangSkinException
import org.polyfrost.polyplus.client.cosmetics.MojangSkinService
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

object SkinLibrary {
    private val LOGGER = LogManager.getLogger("polyplus/skins")

    private const val MAX_SKIN_BYTES = 64 * 1024
    private const val MAX_DOWNLOAD_BYTES = 1024L * 1024L
    private const val RECORD_INTERVAL_MS = 30_000L
    private const val NOT_A_SKIN = "That image isn't a Minecraft skin."
    internal const val TOO_LARGE = "That skin file is too large. Skins can be at most 64 KB."
    internal const val MAX_FILE_BYTES = 64L * 1024L
    private const val BITMAP_RETRY_MS = 60_000L
    private const val RECORD_FAILURE_BACKOFF_MS = 10 * 60_000L
    private const val DOWNLOAD_FAILED = "Couldn't download that image."
    private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_]{1,16}$")
    private val HASH_PATTERN = Regex("^[0-9a-f]{32,64}$")
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val LEGACY_LIMB_COPIES = listOf(
        intArrayOf(24, 48, 20, 52, 4, 16, 8, 20),
        intArrayOf(28, 48, 24, 52, 8, 16, 12, 20),
        intArrayOf(20, 52, 16, 64, 8, 20, 12, 32),
        intArrayOf(24, 52, 20, 64, 4, 20, 8, 32),
        intArrayOf(28, 52, 24, 64, 0, 20, 4, 32),
        intArrayOf(32, 52, 28, 64, 12, 20, 16, 32),
        intArrayOf(40, 48, 36, 52, 44, 16, 48, 20),
        intArrayOf(44, 48, 40, 52, 48, 16, 52, 20),
        intArrayOf(36, 52, 32, 64, 48, 20, 52, 32),
        intArrayOf(40, 52, 36, 64, 44, 20, 48, 32),
        intArrayOf(44, 52, 40, 64, 40, 20, 44, 32),
        intArrayOf(48, 52, 44, 64, 52, 20, 56, 32),
    )

    private val requestLock = Mutex()
    private val recordLock = Mutex()
    private val recordJobLock = Mutex()
    private val cacheDir = File("${PolyPlusConstants.NAME}/skins")
    private val bitmaps = ConcurrentHashMap<String, ImageBitmap>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap<String, Long>()
    private val textures = ConcurrentHashMap<String, Identifier>()
    private val textureLoading = ConcurrentHashMap.newKeySet<String>()
    private val textureFailed = ConcurrentHashMap<String, Long>()
    private val signedInFor = AtomicReference<UUID?>(null)

    var skins by mutableStateOf<List<PlayerSkinInfo>>(emptyList())
        private set

    var state by mutableStateOf<LibraryState>(LibraryState.Idle)
        private set

    var limit by mutableIntStateOf(LIBRARY_LIMIT)
        private set

    var busy by mutableStateOf(false)
        private set

    var bitmapVersion by mutableIntStateOf(0)
        private set

    var textureVersion by mutableIntStateOf(0)
        private set

    @Volatile
    private var loadedFor: UUID? = null

    @Volatile
    private var recordJob: Deferred<Result<PlayerSkinInfo?>>? = null

    @Volatile
    private var recordJobBackground = false

    @Volatile
    private var refreshJob: Deferred<Result<Unit>>? = null

    private data class RecordFailure(val at: Long, val conflict: Boolean)

    private class SkinRecordSkipped(reason: String) : Exception(reason)

    private val recordFailures = ConcurrentHashMap<Pair<UUID, String>, RecordFailure>()

    private var wornHash by mutableStateOf<Pair<UUID, String>?>(null)

    @Volatile
    private var lastRecordOwner: UUID? = null

    @Volatile
    private var lastRecordAt = 0L

    suspend fun refreshIfNeeded() {
        val stale = loadedFor != null && loadedFor != ClientPlatform.localPlayerUuid()
        if (stale || state is LibraryState.Idle || state is LibraryState.Unavailable) {
            refresh()
        } else {
            recordInBackground()
        }
    }

    suspend fun refresh(): Result<Unit> {
        val job = synchronized(this) {
            refreshJob?.takeIf { it.isActive } ?: PolyPlusClient.SCOPE.async { refreshLocked() }.also { refreshJob = it }
        }
        return job.await().also { recordInBackground() }
    }

    suspend fun recordCurrent(background: Boolean = false): Result<PlayerSkinInfo?> =
        recordShared(fresh = false, background = background)

    suspend fun addFromBytes(png: ByteArray, name: String?, model: SkinModel): Result<PlayerSkinInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        runSuspendCatching { withContext(Dispatchers.IO) { validate(png) } }
            .onFailure { return Result.failure(it) }
        return uploadAndRecord(name) { MojangSkinService.uploadSkin(png, model) }
    }

    suspend fun addFromUrl(url: String, name: String?, model: SkinModel): Result<PlayerSkinInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        val link = url.trim()
        if (!link.startsWith("https://", ignoreCase = true) && !link.startsWith("http://", ignoreCase = true)) {
            return Result.failure(OutfitException("Enter a link that starts with https://."))
        }
        return uploadAndRecord(name) { MojangSkinService.setSkin(link, model) }
    }

    suspend fun addFromUsername(username: String): Result<PlayerSkinInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        val name = username.trim()
        if (!USERNAME_PATTERN.matches(name)) {
            return Result.failure(OutfitException("That isn't a valid Minecraft username."))
        }
        val uuid = MojangSkinService.uuidForName(name)
            .getOrElse { return Result.failure(mojangFailure(it)) }
            ?: return Result.failure(OutfitException("No Minecraft account is called $name."))
        val skin = MojangSkinService.profileSkin(uuid)
            .getOrElse { return Result.failure(mojangFailure(it)) }
            ?: return Result.failure(OutfitException("$name is using a default skin."))
        busy = true
        try {
            return recordLock.withLock { record(skin, name, ClientPlatform.localPlayerUuid()) }
        } finally {
            busy = false
        }
    }

    suspend fun wear(id: Int): Result<Unit> {
        if (!OutfitApi.allowed()) return unavailable()
        val skin = skins.firstOrNull { it.id == id }
            ?: return Result.failure(OutfitException("That skin is no longer in your library."))
        busy = true
        try {
            MojangSkinService.setSkin(skin.url, skin.model)
                .getOrElse { return Result.failure(mojangFailure(it)) }
            wornHash = ClientPlatform.localPlayerUuid() to skin.hash
            val patched = OutfitLibrary.onSkinWorn(id)
            recordInBackground(force = true)
            patched.onFailure {
                return Result.failure(OutfitException("Your skin changed, but the active outfit couldn't be updated."))
            }
            return Result.success(Unit)
        } finally {
            busy = false
        }
    }

    suspend fun rename(id: Int, name: String): Result<PlayerSkinInfo> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(OutfitException("Give the skin a name."))
        return patch(id, buildJsonObject { put("name", trimmed) })
    }

    suspend fun setFavorite(id: Int, favorite: Boolean): Result<PlayerSkinInfo> =
        patch(id, buildJsonObject { put("favorite", favorite) })

    suspend fun setModel(id: Int, model: SkinModel): Result<PlayerSkinInfo> {
        val worn = wornSkinId() == id
        val updated = patch(id, buildJsonObject { put("model", model.serializedName) })
            .getOrElse { return Result.failure(it) }
        if (!worn) return Result.success(updated)
        busy = true
        try {
            MojangSkinService.setSkin(updated.url, updated.model).getOrElse {
                return Result.failure(
                    OutfitException("${updated.name} was updated, but your Minecraft skin wasn't changed (${MojangSkinService.failureMessage(it)})."),
                )
            }
            recordInBackground(force = true)
        } finally {
            busy = false
        }
        return Result.success(updated)
    }

    suspend fun delete(id: Int): Result<Unit> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<Unit>(HttpMethod.Delete, "/skins/$id")
            .onSuccess {
                forget(id)
                val owner = ClientPlatform.localPlayerUuid()
                recordFailures.entries.removeIf { it.key.first == owner && it.value.conflict }
            }
            .onFailure {
                LOGGER.error("Failed to delete skin {}", id, it)
                forgetIfMissing(id, it)
            }
    }

    fun wornSkinId(): Int? {
        val owner = runCatching { ClientPlatform.localPlayerUuid() }.getOrNull()
        val hash = wornHash?.takeIf { it.first == owner }?.second ?: return OutfitLibrary.activeOutfit()?.skinId
        return skins.firstOrNull { it.hash == hash }?.id
    }

    fun bitmap(skin: PlayerSkinInfo): ImageBitmap? {
        val key = skin.hash.ifBlank { skin.url }
        bitmaps[key]?.let { return it }
        if (System.currentTimeMillis() >= (failed[key] ?: 0L) && loading.add(key)) loadBitmap(skin, key)
        return null
    }

    fun texture(hash: String, url: String): Identifier? {
        val key = hash.lowercase().takeIf { HASH_PATTERN.matches(it) } ?: return null
        textures[key]?.let { return it }
        if (System.currentTimeMillis() >= (textureFailed[key] ?: 0L) && textureLoading.add(key)) loadTexture(key, url)
        return null
    }

    fun failureMessage(error: Throwable): String = OutfitApi.failureMessage(
        error,
        notFound = "That skin no longer exists.",
        conflict = "Your skin library is full ($limit skins). Delete one to make room.",
        badRequest = NOT_A_SKIN,
    )

    internal fun remember(skin: PlayerSkinInfo) {
        if (state !is LibraryState.Loaded) return
        replace(skin)
    }

    internal fun onSignedIn(profileId: UUID) {
        if (signedInFor.getAndSet(profileId) == profileId) return
        recordInBackground(force = true, background = true)
    }

    internal fun recordInBackground(force: Boolean = false, background: Boolean = !force) {
        if (!OutfitApi.allowed()) return
        val owner = runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() ?: return
        if (!force && lastRecordOwner == owner && System.currentTimeMillis() - lastRecordAt < RECORD_INTERVAL_MS) return
        PolyPlusClient.SCOPE.launch {
            recordShared(fresh = force, background = background).onFailure {
                if (it is SkinRecordSkipped) {
                    LOGGER.debug("Skipped saving the current Minecraft skin: {}", it.message)
                } else {
                    LOGGER.warn("Could not save the current Minecraft skin", it)
                }
            }
        }
    }

    private suspend fun recordShared(fresh: Boolean, background: Boolean): Result<PlayerSkinInfo?> {
        if (!OutfitApi.allowed()) return OutfitApi.privacyFailure()
        val job = recordJobLock.withLock {
            val running = recordJob?.takeIf { it.isActive }
            if (running != null && !fresh && (background || !recordJobBackground)) {
                running
            } else {
                PolyPlusClient.SCOPE.async {
                    running?.join()
                    recordLock.withLock { recordActive(background) }
                }.also {
                    recordJob = it
                    recordJobBackground = background
                }
            }
        }
        return job.await()
    }

    private suspend fun recordActive(background: Boolean): Result<PlayerSkinInfo?> {
        val owner = ClientPlatform.localPlayerUuid()
        try {
            val current = MojangSkinService.activeSkin()
                .getOrElse { return Result.failure(mojangFailure(it)) }
                ?: return Result.success(null)
            current.textureHash?.let { wornHash = owner to it }
            return record(current, null, owner, background, correctModel = true)
        } finally {
            lastRecordOwner = owner
            lastRecordAt = System.currentTimeMillis()
        }
    }

    private suspend fun uploadAndRecord(
        name: String?,
        upload: suspend () -> Result<MojangSkin?>,
    ): Result<PlayerSkinInfo> {
        busy = true
        try {
            val recorded = recordLock.withLock {
                val owner = ClientPlatform.localPlayerUuid()
                if (loadedFor == owner && skins.size >= limit) {
                    return Result.failure(OutfitException("Your skin library is full ($limit skins). Delete one to make room."))
                }
                val worn = upload().getOrElse { return Result.failure(mojangFailure(it)) }
                    ?: return Result.failure(
                        OutfitException("Your skin changed, but Minecraft hasn't reported it yet. Try again in a moment."),
                    )
                worn.textureHash?.let { wornHash = owner to it }
                record(worn, name, owner, correctModel = true).also {
                    lastRecordOwner = owner
                    lastRecordAt = System.currentTimeMillis()
                }.recoverCatching {
                    throw OutfitException("Your skin changed, but it couldn't be saved to your library (${failureMessage(it)}).")
                }
            }
            recorded.onSuccess { skin ->
                OutfitLibrary.onSkinWorn(skin.id)
                    .onFailure { LOGGER.warn("Could not move the active outfit to skin {}", skin.id, it) }
            }
            return recorded
        } finally {
            busy = false
        }
    }

    private suspend fun record(
        skin: MojangSkin,
        name: String?,
        owner: UUID,
        background: Boolean = false,
        correctModel: Boolean = false,
    ): Result<PlayerSkinInfo> {
        val hash = skin.textureHash
            ?: return Result.failure(OutfitException("Minecraft didn't report a usable skin texture."))
        val key = owner to hash
        if (background) {
            recordFailures[key]?.let { failure ->
                if (System.currentTimeMillis() - failure.at < RECORD_FAILURE_BACKOFF_MS) {
                    val reason = if (failure.conflict) "the skin library is full" else "the last attempt failed recently"
                    return Result.failure(SkinRecordSkipped(reason))
                }
            }
        }
        val label = name?.trim()?.takeIf { it.isNotEmpty() }
        val body = buildJsonObject {
            put("texture_hash", hash)
            put("model", skin.model.serializedName)
            if (label != null) put("name", label)
        }
        val recorded = OutfitApi.send<SkinResponse>(HttpMethod.Post, "/skins", body)
            .map { it.toInfo() }
            .onSuccess { recorded ->
                recordFailures.remove(key)
                if (loadedFor == owner) replace(recorded)
            }
            .onFailure {
                LOGGER.error("Failed to save skin {}", hash, it)
                val conflict = (it as? ClientRequestException)?.response?.status == HttpStatusCode.Conflict
                recordFailures[key] = RecordFailure(System.currentTimeMillis(), conflict)
            }
            .getOrElse { return Result.failure(it) }
        if (!correctModel || recorded.model == skin.model) return Result.success(recorded)
        return OutfitApi.send<SkinResponse>(HttpMethod.Patch, "/skins/${recorded.id}", buildJsonObject { put("model", skin.model.serializedName) })
            .map { it.toInfo() }
            .onSuccess { updated -> if (loadedFor == owner) replace(updated) }
            .onFailure { LOGGER.warn("Failed to update the arm model of skin {}", recorded.id, it) }
            .recover { recorded }
    }

    private suspend fun refreshLocked(): Result<Unit> = requestLock.withLock {
        failed.clear()
        bitmapVersion++
        if (!OutfitApi.allowed()) {
            skins = emptyList()
            state = LibraryState.Unavailable(OutfitApi.PRIVACY_MESSAGE)
            return@withLock OutfitApi.privacyFailure()
        }
        val owner = ClientPlatform.localPlayerUuid()
        if (state !is LibraryState.Loaded || loadedFor != owner) state = LibraryState.Loading
        try {
            OutfitApi.send<SkinListResponse>(HttpMethod.Get, "/skins")
                .onSuccess { response ->
                    skins = response.skins.map { it.toInfo() }
                    limit = response.limit
                    loadedFor = owner
                    state = LibraryState.Loaded
                }
                .onFailure { error ->
                    LOGGER.error("Failed to fetch skins", error)
                    if (state !is LibraryState.Loaded) state = LibraryState.Unavailable(failureMessage(error))
                }
                .map { }
        } finally {
            if (state is LibraryState.Loading) state = LibraryState.Idle
        }
    }

    private suspend fun patch(id: Int, body: JsonObject): Result<PlayerSkinInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<SkinResponse>(HttpMethod.Patch, "/skins/$id", body)
            .map { it.toInfo() }
            .onSuccess(::replace)
            .onFailure {
                LOGGER.error("Failed to update skin {}", id, it)
                forgetIfMissing(id, it)
            }
    }

    private fun forgetIfMissing(id: Int, error: Throwable) {
        if ((error as? ClientRequestException)?.response?.status == HttpStatusCode.NotFound) forget(id)
    }

    private fun forget(id: Int) {
        skins = skins.filter { it.id != id }
        OutfitLibrary.onSkinDeleted(id)
    }

    private suspend fun download(url: String): Result<ByteArray> = runSuspendCatching {
        val response = runSuspendCatching { PolyPlusClient.HTTP.get(url) }
            .getOrElse { throw OutfitException(DOWNLOAD_FAILED) }
        if (!response.status.isSuccess()) throw OutfitException(DOWNLOAD_FAILED)
        val length = response.contentLength()
        if (length != null && length > MAX_DOWNLOAD_BYTES) throw OutfitException(NOT_A_SKIN)
        runSuspendCatching { response.bodyAsBytes() }.getOrElse { throw OutfitException(DOWNLOAD_FAILED) }
    }.onFailure { LOGGER.warn("Failed to download a skin from {}", url, it) }

    private fun validate(png: ByteArray) {
        if (png.size > MAX_SKIN_BYTES) throw OutfitException(TOO_LARGE)
        if (png.size < PNG_SIGNATURE.size || !PNG_SIGNATURE.indices.all { png[it] == PNG_SIGNATURE[it] }) {
            throw OutfitException("Skins must be PNG images.")
        }
        val size = runCatching {
            ImageIO.createImageInputStream(ByteArrayInputStream(png)).use { input ->
                val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return@use null
                try {
                    reader.input = input
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }
        }.getOrNull() ?: throw OutfitException(NOT_A_SKIN)
        if (size.first != 64 || (size.second != 64 && size.second != 32)) throw OutfitException(NOT_A_SKIN)
        runCatching { ImageIO.read(ByteArrayInputStream(png)) }.getOrNull() ?: throw OutfitException(NOT_A_SKIN)
    }

    private fun loadBitmap(skin: PlayerSkinInfo, key: String) {
        PolyPlusClient.SCOPE.launch {
            runSuspendCatching {
                val image = withContext(Dispatchers.IO) { readCache(skin.hash) }
                    ?: download(skin.url).getOrThrow().let { fresh ->
                        withContext(Dispatchers.IO) {
                            val decoded = decode(fresh) ?: throw OutfitException(NOT_A_SKIN)
                            writeCache(skin.hash, fresh)
                            decoded
                        }
                    }
                bitmaps[key] = previewImage(image).toComposeImageBitmap()
                failed.remove(key)
                bitmapVersion++
            }.onFailure {
                failed[key] = System.currentTimeMillis() + BITMAP_RETRY_MS
                LOGGER.debug("Could not load the preview for skin {}", skin.id, it)
            }
            loading.remove(key)
            if (failed.containsKey(key)) {
                delay(BITMAP_RETRY_MS)
                bitmapVersion++
            }
        }
    }

    private fun loadTexture(hash: String, url: String) {
        PolyPlusClient.SCOPE.launch {
            runSuspendCatching {
                val file = File(cacheDir, "textures/$hash.png")
                if (!file.isFile) {
                    val image = withContext(Dispatchers.IO) { readCache(hash) }
                        ?: download(url).getOrThrow().let { fresh ->
                            withContext(Dispatchers.IO) {
                                val decoded = decode(fresh) ?: throw OutfitException(NOT_A_SKIN)
                                writeCache(hash, fresh)
                                decoded
                            }
                        }
                    withContext(Dispatchers.IO) {
                        file.parentFile.mkdirs()
                        ImageIO.write(modernLayout(previewImage(image)), "png", file)
                    }
                }
                RemoteTextures.register(texturePath(hash), file.toPath())
                textures[hash] = textureLocation(hash)
                textureFailed.remove(hash)
                textureVersion++
            }.onFailure {
                textureFailed[hash] = System.currentTimeMillis() + BITMAP_RETRY_MS
                LOGGER.debug("Could not load the texture for skin {}", hash, it)
            }
            textureLoading.remove(hash)
            if (textureFailed.containsKey(hash)) {
                delay(BITMAP_RETRY_MS)
                textureVersion++
            }
        }
    }

    private fun textureLocation(hash: String): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "skin/$hash")

    private fun texturePath(hash: String): Identifier {
        //? if >= 1.21.10 {
        return Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/skin/$hash.png")
        //?} else {
        /*return textureLocation(hash)
        *///?}
    }

    private fun modernLayout(source: BufferedImage): BufferedImage {
        if (source.height != 32) return source
        val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.drawImage(source, 0, 0, null)
        for (r in LEGACY_LIMB_COPIES) graphics.drawImage(source, r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], null)
        graphics.dispose()
        return image
    }

    private fun previewImage(source: BufferedImage): BufferedImage {
        val image = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().apply { drawImage(source, 0, 0, null) }.dispose()
        opaque(image, 0, 0, 32, 16)
        if (image.height <= 32) clearIfOpaque(image, 32, 0, 64, 32)
        opaque(image, 0, 16, 64, 32)
        opaque(image, 16, 48, 48, 64)
        return image
    }

    private fun clearIfOpaque(image: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (x in x0 until minOf(x1, image.width)) {
            for (y in y0 until minOf(y1, image.height)) {
                if (image.getRGB(x, y) ushr 24 < 128) return
            }
        }
        for (x in x0 until minOf(x1, image.width)) {
            for (y in y0 until minOf(y1, image.height)) {
                image.setRGB(x, y, image.getRGB(x, y) and 0xFFFFFF)
            }
        }
    }

    private fun opaque(image: BufferedImage, x0: Int, y0: Int, x1: Int, y1: Int) {
        for (x in x0 until minOf(x1, image.width)) {
            for (y in y0 until minOf(y1, image.height)) {
                image.setRGB(x, y, image.getRGB(x, y) or (0xFF shl 24))
            }
        }
    }

    private fun cacheFile(hash: String): File? =
        hash.lowercase().takeIf { HASH_PATTERN.matches(it) }?.let { File(cacheDir, "$it.png") }

    private fun readCache(hash: String): BufferedImage? {
        val file = cacheFile(hash)?.takeIf { it.isFile } ?: return null
        val image = runCatching { file.readBytes() }.getOrNull()?.let(::decode)
        if (image == null) runCatching { file.delete() }
        return image
    }

    private fun decode(bytes: ByteArray): BufferedImage? =
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    private fun writeCache(hash: String, bytes: ByteArray) {
        val file = cacheFile(hash) ?: return
        if (file.isFile) return
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) return
        file.writeBytes(bytes)
    }

    private fun replace(skin: PlayerSkinInfo) {
        val existing = skins
        skins = if (existing.any { it.id == skin.id }) {
            existing.map { if (it.id == skin.id) skin else it }
        } else {
            listOf(skin) + existing
        }
    }

    private fun mojangFailure(error: Throwable): Throwable = MojangSkinException(MojangSkinService.failureMessage(error))

    private fun <T> unavailable(): Result<T> {
        state = LibraryState.Unavailable(OutfitApi.PRIVACY_MESSAGE)
        return OutfitApi.privacyFailure()
    }
}
