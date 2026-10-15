package org.polyfrost.polyplus.client.cosmetics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

sealed interface SwatchArt {
    data class Picture(val bitmap: ImageBitmap) : SwatchArt
    data class Stripes(val colors: List<Color>) : SwatchArt
}

object CosmeticSwatches {
    private val LOGGER = LogManager.getLogger("polyplus/swatches")
    private val art = ConcurrentHashMap<Int, SwatchArt>()
    private val requested = ConcurrentHashMap.newKeySet<Int>()

    private const val RETRY_MS = 60_000L

    private const val CAPE_TEXTURE_WIDTH = 64
    private const val LEGACY_CAPE_TEXTURE_WIDTH = 22
    private const val MAX_STRIPES = 3
    private const val MIN_STRIPE_SHARE = 0.08

    var version by mutableIntStateOf(0)
        private set

    fun get(id: Int): SwatchArt? {
        art[id]?.let { return it }
        if (requested.add(id)) load(id)
        return null
    }

    private fun load(id: Int) {
        PolyPlusClient.SCOPE.launch {
            runSuspendCatching {
                CosmeticAssetCache.ensureCosmeticLoaded(id)
                val built = withContext(Dispatchers.IO) { build(id) }
                if (built == null) {
                    retryLater(id)
                    return@runSuspendCatching
                }
                art[id] = built
                version++
            }.onFailure {
                retryLater(id)
                LOGGER.debug("Could not build a swatch for cosmetic {}", id, it)
            }
        }
    }

    private fun retryLater(id: Int) {
        PolyPlusClient.SCOPE.launch {
            delay(RETRY_MS)
            requested.remove(id)
            version++
        }
    }

    private fun build(id: Int): SwatchArt? {
        val definition = CosmeticCatalog.getCosmeticDefinition(id) ?: return null
        val file = CosmeticAssetCache.primaryTextureFile(id) ?: return null
        val image = ImageIO.read(file) ?: return null
        return if (definition.type == CosmeticType.Cape) {
            capeFront(image)?.let { SwatchArt.Picture(it.toComposeImageBitmap()) }
        } else {
            dominantColors(image).takeIf { it.isNotEmpty() }?.let(SwatchArt::Stripes)
        }
    }

    private fun capeFront(image: BufferedImage): BufferedImage? {
        val unit = if (image.width >= CAPE_TEXTURE_WIDTH) {
            image.width / CAPE_TEXTURE_WIDTH.toFloat()
        } else {
            image.width / LEGACY_CAPE_TEXTURE_WIDTH.toFloat()
        }
        val offset = unit.toInt()
        val width = (unit * 10).toInt()
        val height = (unit * 16).toInt()
        if (width <= 0 || height <= 0 || offset + width > image.width || offset + height > image.height) return null
        return image.getSubimage(offset, offset, width, height)
    }

    private fun dominantColors(image: BufferedImage): List<Color> {
        val counts = HashMap<Int, Int>()
        var total = 0
        for (py in 0 until image.height) {
            for (px in 0 until image.width) {
                val argb = image.getRGB(px, py)
                if ((argb ushr 24) < 128) continue
                val bucket = (argb and 0xF0F0F0) or 0x080808
                counts[bucket] = (counts[bucket] ?: 0) + 1
                total++
            }
        }
        if (total == 0) return emptyList()
        return counts.entries
            .sortedByDescending { it.value }
            .filter { it.value >= total * MIN_STRIPE_SHARE }
            .take(MAX_STRIPES)
            .map { Color(0xFF000000.toInt() or it.key) }
    }
}
