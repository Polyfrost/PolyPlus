package org.polyfrost.polyplus.client.cosmetics.outfits

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpMethod
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangSkin
import org.polyfrost.polyplus.client.cosmetics.MojangSkinException
import org.polyfrost.polyplus.client.cosmetics.MojangSkinService
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
    private const val DOWNLOAD_FAILED = "Couldn't download that image."
    private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_]{1,16}$")
    private val HASH_PATTERN = Regex("^[0-9a-f]{32,64}$")
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private val requestLock = Mutex()
    private val recordLock = Mutex()
    private val recordJobLock = Mutex()
    private val cacheDir = File("${PolyPlusConstants.NAME}/skins")
    private val bitmaps = ConcurrentHashMap<String, ImageBitmap>()
    private val loading = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
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

    @Volatile
    private var loadedFor: UUID? = null

    @Volatile
    private var recordJob: Deferred<Result<PlayerSkinInfo?>>? = null

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

    suspend fun refresh(): Result<Unit> = refreshLocked().also { recordInBackground() }

    suspend fun recordCurrent(): Result<PlayerSkinInfo?> = recordShared(fresh = false)

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

    suspend fun setModel(id: Int, model: SkinModel): Result<PlayerSkinInfo> =
        patch(id, buildJsonObject { put("model", model.serializedName) })

    suspend fun delete(id: Int): Result<Unit> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<Unit>(HttpMethod.Delete, "/skins/$id")
            .onSuccess {
                skins = skins.filter { it.id != id }
                OutfitLibrary.onSkinDeleted(id)
            }
            .onFailure { LOGGER.error("Failed to delete skin {}", id, it) }
    }

    fun wornSkinId(): Int? = OutfitLibrary.activeOutfit()?.skinId

    fun bitmap(skin: PlayerSkinInfo): ImageBitmap? {
        val key = skin.hash.ifBlank { skin.url }
        bitmaps[key]?.let { return it }
        if (key !in failed && loading.add(key)) loadBitmap(skin, key)
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
        recordInBackground(force = true)
    }

    internal fun recordInBackground(force: Boolean = false) {
        if (!OutfitApi.allowed()) return
        val owner = runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() ?: return
        if (!force && lastRecordOwner == owner && System.currentTimeMillis() - lastRecordAt < RECORD_INTERVAL_MS) return
        PolyPlusClient.SCOPE.launch {
            recordShared(fresh = force).onFailure { LOGGER.warn("Could not save the current Minecraft skin", it) }
        }
    }

    private suspend fun recordShared(fresh: Boolean): Result<PlayerSkinInfo?> {
        if (!OutfitApi.allowed()) return OutfitApi.privacyFailure()
        val job = recordJobLock.withLock {
            val running = recordJob?.takeIf { it.isActive }
            if (running != null && !fresh) {
                running
            } else {
                PolyPlusClient.SCOPE.async {
                    running?.join()
                    recordLock.withLock { recordActive() }
                }.also { recordJob = it }
            }
        }
        return job.await()
    }

    private suspend fun recordActive(): Result<PlayerSkinInfo?> {
        val owner = ClientPlatform.localPlayerUuid()
        try {
            val current = MojangSkinService.activeSkin()
                .getOrElse { return Result.failure(mojangFailure(it)) }
                ?: return Result.success(null)
            return record(current, null, owner)
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
                val worn = upload().getOrElse { return Result.failure(mojangFailure(it)) }
                    ?: return Result.failure(
                        OutfitException("Your skin changed, but Minecraft hasn't reported it yet. Try again in a moment."),
                    )
                record(worn, name, owner).also {
                    lastRecordOwner = owner
                    lastRecordAt = System.currentTimeMillis()
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

    private suspend fun record(skin: MojangSkin, name: String?, owner: UUID): Result<PlayerSkinInfo> {
        val hash = skin.textureHash
            ?: return Result.failure(OutfitException("Minecraft didn't report a usable skin texture."))
        if (name == null && loadedFor == owner) {
            skins.firstOrNull { it.hash == hash }?.let { return Result.success(it) }
        }
        val label = name?.trim()?.takeIf { it.isNotEmpty() }
        val body = buildJsonObject {
            put("texture_hash", hash)
            put("model", skin.model.serializedName)
            if (label != null) put("name", label)
        }
        return OutfitApi.send<SkinResponse>(HttpMethod.Post, "/skins", body)
            .map { it.toInfo() }
            .onSuccess { recorded -> if (loadedFor == owner) replace(recorded) }
            .onFailure { LOGGER.error("Failed to save skin {}", hash, it) }
    }

    private suspend fun refreshLocked(): Result<Unit> = requestLock.withLock {
        failed.clear()
        if (!OutfitApi.allowed()) {
            skins = emptyList()
            state = LibraryState.Unavailable(OutfitApi.PRIVACY_MESSAGE)
            return@withLock OutfitApi.privacyFailure()
        }
        val owner = ClientPlatform.localPlayerUuid()
        if (state !is LibraryState.Loaded || loadedFor != owner) state = LibraryState.Loading
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
    }

    private suspend fun patch(id: Int, body: JsonObject): Result<PlayerSkinInfo> {
        if (!OutfitApi.allowed()) return unavailable()
        return OutfitApi.send<SkinResponse>(HttpMethod.Patch, "/skins/$id", body)
            .map { it.toInfo() }
            .onSuccess(::replace)
            .onFailure { LOGGER.error("Failed to update skin {}", id, it) }
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
        if (png.size < PNG_SIGNATURE.size || !PNG_SIGNATURE.indices.all { png[it] == PNG_SIGNATURE[it] }) {
            throw OutfitException("Skins must be PNG images.")
        }
        val image = runCatching { ImageIO.read(ByteArrayInputStream(png)) }.getOrNull()
            ?: throw OutfitException(NOT_A_SKIN)
        if (image.width != 64 || (image.height != 64 && image.height != 32)) throw OutfitException(NOT_A_SKIN)
        if (png.size > MAX_SKIN_BYTES) throw OutfitException("That skin file is too large. Skins can be at most 64 KB.")
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
                bitmaps[key] = image.toComposeImageBitmap()
                bitmapVersion++
            }.onFailure {
                failed.add(key)
                LOGGER.debug("Could not load the preview for skin {}", skin.id, it)
            }
            loading.remove(key)
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
