package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.minecraft.resources.Identifier
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.PROFILE_URL
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.accessToken
import org.polyfrost.polyplus.client.cosmetics.MojangAuth.authorize
import org.polyfrost.polyplus.client.cosmetics.assets.RemoteTextures
import org.polyfrost.polyplus.client.cosmetics.outfits.OutfitLibrary
import org.polyfrost.polyplus.client.utils.ClientPlatform
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

data class MojangCape(val id: String, val name: String, val url: String, val active: Boolean) {
    val textureHash: String get() = url.substringAfterLast('/')
}

sealed interface MojangCapeState {
    data object Idle : MojangCapeState
    data object Loading : MojangCapeState
    data class Loaded(val capes: List<MojangCape>) : MojangCapeState
    data class Unavailable(val reason: String) : MojangCapeState
}

class MojangCapeException(message: String) : Exception(message)

object MojangCapeService {
    private val LOGGER = LogManager.getLogger("polyplus/mojang-capes")

    private const val ACTIVE_CAPE_URL = "$PROFILE_URL/capes/active"
    private const val SIGNED_OUT_MESSAGE = "Sign in with a Microsoft account to choose a Minecraft cape."
    private const val UNREACHABLE_MESSAGE = "Couldn't reach Minecraft's servers."
    private const val TEXTURE_RETRY_MS = 60_000L

    private val requestLock = Mutex()
    private val textures = ConcurrentHashMap<String, Identifier>()
    private val downloading = ConcurrentHashMap.newKeySet<String>()
    private val retryAt = ConcurrentHashMap<String, Long>()
    private val cacheDir = CosmeticAssetCache.baseDir.toPath().resolve("mojang")

    var state by mutableStateOf<MojangCapeState>(MojangCapeState.Idle)
        private set

    var busy by mutableStateOf(false)
        private set

    var textureVersion by mutableIntStateOf(0)
        private set

    @Volatile
    private var changedThisSession = false

    @Volatile
    private var loadedFor: UUID? = null

    @Volatile
    private var refreshJob: Deferred<Result<List<MojangCape>>>? = null

    suspend fun refreshIfNeeded() {
        val stale = loadedFor != null && loadedFor != ClientPlatform.localPlayerUuid()
        if (stale || state is MojangCapeState.Idle || state is MojangCapeState.Unavailable) refresh()
    }

    suspend fun refresh(): Result<List<MojangCape>> {
        val job = synchronized(this) {
            refreshJob?.takeIf { it.isActive } ?: PolyPlusClient.SCOPE.async {
                requestLock.withLock {
                    val owner = ClientPlatform.localPlayerUuid()
                    if (state !is MojangCapeState.Loaded || loadedFor != owner) state = MojangCapeState.Loading
                    try {
                        request { token -> PolyPlusClient.HTTP.get(PROFILE_URL) { authorize(token) } }
                    } finally {
                        if (state is MojangCapeState.Loading) state = MojangCapeState.Idle
                    }
                }
            }.also { refreshJob = it }
        }
        return job.await()
    }

    suspend fun select(capeId: String): Result<List<MojangCape>> = mutate { token ->
        PolyPlusClient.HTTP.put(ACTIVE_CAPE_URL) {
            authorize(token)
            contentType(ContentType.Application.Json)
            setBody(SelectCapeRequest(capeId))
        }
    }

    suspend fun clear(): Result<List<MojangCape>> = mutate { token ->
        PolyPlusClient.HTTP.delete(ACTIVE_CAPE_URL) { authorize(token) }
    }

    fun isLoaded(): Boolean = localCapes() != null

    fun activeCape(): MojangCape? = localCapes()?.firstOrNull { it.active }

    private fun localCapes(): List<MojangCape>? {
        val capes = (state as? MojangCapeState.Loaded)?.capes ?: return null
        val owner = loadedFor ?: return null
        return capes.takeIf { owner == runCatching { ClientPlatform.localPlayerUuid() }.getOrNull() }
    }

    fun texture(cape: MojangCape): Identifier? {
        val hash = cape.textureHash
        val location = textures[hash]
        if (location != null && RemoteTextures.isRegistered(texturePath(hash))) return location
        load(cape)
        return null
    }

    @JvmStatic
    fun localCapeTexture(): Identifier? = activeCape()?.let(::texture)

    @JvmStatic
    fun localCapeHidden(): Boolean = localCapes()?.none { it.active } == true

    @JvmStatic
    fun overridesLocalCape(uuid: UUID): Boolean =
        changedThisSession && state is MojangCapeState.Loaded && uuid == loadedFor && uuid == ClientPlatform.localPlayerUuid()

    fun failureMessage(error: Throwable): String = when (error) {
        is MojangCapeException -> error.message ?: SIGNED_OUT_MESSAGE
        else -> UNREACHABLE_MESSAGE
    }

    private suspend fun mutate(call: suspend (String) -> HttpResponse): Result<List<MojangCape>> =
        requestLock.withLock {
            busy = true
            try {
                request(call).onSuccess { capes ->
                    changedThisSession = true
                    OutfitLibrary.onMojangCapeChanged(capes.firstOrNull { it.active }?.id)
                }
            } finally {
                busy = false
            }
        }

    private suspend fun request(call: suspend (String) -> HttpResponse): Result<List<MojangCape>> {
        val owner = ClientPlatform.localPlayerUuid()
        val result = runSuspendCatching {
            val token = accessToken() ?: throw MojangCapeException(SIGNED_OUT_MESSAGE)
            val response = call(token)
            ensureSuccess(response.status)
            response.body<ProfileResponse>().capes.map { it.toCape() }
        }
        result.onSuccess { capes ->
            if (loadedFor != owner) changedThisSession = false
            loadedFor = owner
            state = MojangCapeState.Loaded(capes)
            capes.forEach(::texture)
        }.onFailure { error ->
            LOGGER.warn("Minecraft cape request failed", error)
            if (state !is MojangCapeState.Loaded) state = MojangCapeState.Unavailable(failureMessage(error))
        }
        return result
    }

    private fun ensureSuccess(status: HttpStatusCode) {
        if (status.isSuccess()) return
        throw MojangCapeException(
            when (status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden -> SIGNED_OUT_MESSAGE
                HttpStatusCode.TooManyRequests -> "Minecraft is limiting cape changes right now. Try again in a minute."
                HttpStatusCode.BadRequest -> "Minecraft couldn't use that cape."
                else -> UNREACHABLE_MESSAGE
            },
        )
    }

    private fun load(cape: MojangCape) {
        val hash = cape.textureHash
        if (hash.isBlank() || System.currentTimeMillis() < (retryAt[hash] ?: 0L) || !downloading.add(hash)) return
        PolyPlusClient.SCOPE.launch {
            val file = cacheDir.resolve("$hash.png")
            runSuspendCatching {
                if (!Files.isRegularFile(file)) {
                    val response = PolyPlusClient.HTTP.get(cape.url)
                    if (!response.status.isSuccess()) throw MojangCapeException("Cape texture request returned ${response.status}")
                    val bytes = response.bodyAsBytes()
                    withContext(Dispatchers.IO) {
                        if (ImageIO.read(ByteArrayInputStream(bytes)) == null) throw MojangCapeException("Cape texture isn't an image")
                        Files.createDirectories(cacheDir)
                        Files.write(file, bytes)
                    }
                }
                RemoteTextures.register(texturePath(hash), file)
                textures[hash] = location(hash)
                retryAt.remove(hash)
                textureVersion++
            }.onFailure {
                LOGGER.warn("Failed to load the texture for Minecraft cape {}", cape.name, it)
                retryAt[hash] = System.currentTimeMillis() + TEXTURE_RETRY_MS
                withContext(Dispatchers.IO) { runCatching { Files.deleteIfExists(file) } }
                PolyPlusClient.SCOPE.launch {
                    delay(TEXTURE_RETRY_MS)
                    textureVersion++
                }
            }
            downloading.remove(hash)
        }
    }

    private fun location(hash: String): Identifier =
        Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "mojang_cape/$hash")

    private fun texturePath(hash: String): Identifier {
        //? if >= 1.21.10 {
        return Identifier.fromNamespaceAndPath(PolyPlusConstants.ID, "textures/mojang_cape/$hash.png")
        //?} else {
        /*return location(hash)
        *///?}
    }

    @Serializable
    private data class ProfileResponse(val capes: List<CapeResponse> = emptyList())

    @Serializable
    private data class CapeResponse(
        val id: String,
        val state: String = "",
        val url: String = "",
        val alias: String = "",
    ) {
        fun toCape(): MojangCape = MojangCape(
            id = id,
            name = alias.ifBlank { "Minecraft cape" },
            url = url,
            active = state.equals("ACTIVE", ignoreCase = true),
        )
    }

    @Serializable
    private data class SelectCapeRequest(val capeId: String)
}
