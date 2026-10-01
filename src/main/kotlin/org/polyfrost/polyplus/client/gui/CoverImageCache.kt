package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.apache.logging.log4j.LogManager
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.polyfrost.polyplus.client.utils.runSuspendCatching

private const val MAX_IMAGES = 32

// composables only call get() when they enter composition, so this spaces out retries of a failing image
// across screen opens instead of blocking it for the rest of the session
private const val RETRY_DELAY_MS = 60_000L

private fun <K> lruImageCache(): MutableMap<K, ImageBitmap> =
    Collections.synchronizedMap(object : LinkedHashMap<K, ImageBitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, ImageBitmap>) = size > MAX_IMAGES
    })

object CoverImageCache {
    private val LOGGER = LogManager.getLogger()

    private val cache = lruImageCache<Int>()
    private val retryAt = ConcurrentHashMap<Int, Long>()

    fun cached(assetId: Int): ImageBitmap? = cache[assetId]

    suspend fun get(assetId: Int): ImageBitmap? {
        cache[assetId]?.let { return it }
        if ((retryAt[assetId] ?: 0L) > System.currentTimeMillis()) return null

        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val bytes = PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/asset/$assetId").body<ByteArray>()
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.onFailure {
                retryAt[assetId] = System.currentTimeMillis() + RETRY_DELAY_MS
                LOGGER.error("Failed to load cover asset {}", assetId, it)
            }.getOrNull()?.also {
                cache[assetId] = it
                retryAt.remove(assetId)
            }
        }
    }
}

@Composable
fun rememberCoverImage(assetId: Int?): ImageBitmap? =
    produceState(assetId?.let { CoverImageCache.cached(it) }, assetId) {
        if (assetId != null && value == null) value = CoverImageCache.get(assetId)
    }.value

object RemoteImageCache {
    private val LOGGER = LogManager.getLogger()
    private const val MAX_BYTES = 2 * 1024 * 1024

    private val cache = lruImageCache<String>()
    private val retryAt = ConcurrentHashMap<String, Long>()

    fun cached(url: String): ImageBitmap? = cache[url]

    suspend fun get(url: String): ImageBitmap? {
        cache[url]?.let { return it }
        if ((retryAt[url] ?: 0L) > System.currentTimeMillis()) return null

        return withContext(Dispatchers.IO) {
            runSuspendCatching {
                val bytes = PolyPlusClient.HTTP.get(url).body<ByteArray>()
                require(bytes.size <= MAX_BYTES) { "image exceeds 2 MiB" }
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.onFailure {
                retryAt[url] = System.currentTimeMillis() + RETRY_DELAY_MS
                LOGGER.warn("Failed to load remote image {}", url, it)
            }.getOrNull()?.also {
                cache[url] = it
                retryAt.remove(url)
            }
        }
    }
}

@Composable
fun rememberRemoteImage(url: String?): ImageBitmap? =
    produceState(url?.let { RemoteImageCache.cached(it) }, url) {
        if (url != null && value == null) value = RemoteImageCache.get(url)
    }.value
