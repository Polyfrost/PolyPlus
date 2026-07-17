package org.polyfrost.polyplus.client.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.jetbrains.skia.Image as SkiaImage
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.PolyPlusConfig
import java.util.concurrent.ConcurrentHashMap

object CoverImageCache {
    private val LOGGER = LogManager.getLogger()

    private val cache = ConcurrentHashMap<Int, ImageBitmap>()
    private val failed = ConcurrentHashMap.newKeySet<Int>()

    fun cached(assetId: Int): ImageBitmap? = cache[assetId]

    suspend fun get(assetId: Int): ImageBitmap? {
        cache[assetId]?.let { return it }
        if (assetId in failed) return null

        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = PolyPlusClient.HTTP.get("${PolyPlusConfig.apiUrl}/asset/$assetId").body<ByteArray>()
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.onFailure {
                failed += assetId
                LOGGER.error("Failed to load cover asset {}", assetId, it)
            }.getOrNull()?.also { cache[assetId] = it }
        }
    }
}

@Composable
fun rememberCoverImage(assetId: Int?): ImageBitmap? =
    produceState(assetId?.let { CoverImageCache.cached(it) }, assetId) {
        if (assetId != null && value == null) value = CoverImageCache.get(assetId)
    }.value
