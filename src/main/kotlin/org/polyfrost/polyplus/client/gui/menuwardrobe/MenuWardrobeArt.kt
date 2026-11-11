@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.polyplus.client.gui.menuwardrobe

import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mojang.blaze3d.platform.NativeImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
//? if >= 1.21.10
import net.minecraft.core.ClientAsset
import net.minecraft.resources.Identifier
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.cosmetics.CosmeticAssetCache
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewRenderer
import org.polyfrost.polyplus.client.gui.preview.PlayerPreviewSource
import org.polyfrost.polyplus.client.gui.preview.PreviewSkin
import org.polyfrost.polyplus.client.gui.wardrobe.BACK_YAW_DEG
import org.polyfrost.polyplus.client.gui.wardrobe.CosmeticUiItem
import org.polyfrost.polyplus.client.gui.wardrobe.FRONT_YAW_DEG
import org.polyfrost.polyplus.client.gui.wardrobe.rememberCosmeticPreviewSource
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType
import kotlin.math.min
import kotlin.math.roundToInt

private const val SKIN_SIZE = 64
private const val PORTRAIT_SIZE = 384
private const val CAPTURE_ATTEMPTS = 60
private const val CAPTURE_SETTLE_ATTEMPTS = 4
private const val CAPTURE_INTERVAL_MS = 20L
private const val ALPHA_FLOOR = 0.04f
private const val FILL = 0.9f
private const val CACHE_LIMIT = 48

private class Portrait(val image: ImageBitmap, val offset: IntOffset, val size: IntSize)

private class Framing(val yaw: Float, val scale: Float, val anchor: Float)

private object BareSkin {
    val skin: PreviewSkin by lazy {
        val id = Identifier.fromNamespaceAndPath("polyplus", "lobby/bare_skin")
        //? if >= 1.21.5 {
        val texture = DynamicTexture({ id.toString() }, NativeImage(SKIN_SIZE, SKIN_SIZE, true))
        //?} elif > 1.8.9 {
        /*val texture = DynamicTexture(NativeImage(SKIN_SIZE, SKIN_SIZE, true))
        *///?} else {
        /*val texture = DynamicTexture(SKIN_SIZE, SKIN_SIZE)
        *///?}
        //? if >= 1.21.10 {
        Minecraft.getInstance().textureManager.register(ClientAsset.ResourceTexture(id).texturePath(), texture)
        //?} else {
        /*Minecraft.getInstance().textureManager.register(id, texture)
        *///?}
        PreviewSkin(id, false)
    }
}

private class LruCache<K, V>(private val limit: Int) {
    private val map = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean = size > limit
    }

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        map[key] = value
    }
}

private object Portraits {
    private val cache = LruCache<String, Portrait>(CACHE_LIMIT)
    private val sprites = LruCache<Int, Portrait>(CACHE_LIMIT)

    fun cached(key: String): Portrait? = cache[key]

    fun cachedSprite(id: Int): Portrait? = sprites[id]

    suspend fun capture(key: String, source: PlayerPreviewSource, framing: Framing): Portrait? {
        PlayerPreviewRenderer.retain(key)
        try {
            repeat(CAPTURE_ATTEMPTS) { attempt ->
                val image = PlayerPreviewRenderer.capture(source, framing.yaw, 0f, PORTRAIT_SIZE, PORTRAIT_SIZE, framing.scale, framing.anchor, key)
                if (image != null && attempt >= CAPTURE_SETTLE_ATTEMPTS) {
                    val portrait = crop(image)
                    if (portrait != null) {
                        cache[key] = portrait
                        return portrait
                    }
                }
                delay(CAPTURE_INTERVAL_MS)
            }
            return null
        } finally {
            PlayerPreviewRenderer.release(key)
        }
    }

    fun sprite(id: Int): Portrait? {
        sprites[id]?.let { return it }
        val file = CosmeticAssetCache.primaryTextureFile(id) ?: return null
        val sprite = PolyBitmap.decode(file.readBytes())?.asComposeImageBitmap()?.let(::crop) ?: return null
        sprites[id] = sprite
        return sprite
    }

    private fun crop(image: ImageBitmap): Portrait? {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.readPixels(pixels, 0, 0, width, height, 0, width)
        val floor = ALPHA_FLOOR * 255f
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if ((pixels[y * width + x] ushr 24) <= floor) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                if (y > bottom) bottom = y
            }
        }
        if (right < left) return null
        val cropWidth = right - left + 1
        val cropHeight = bottom - top + 1
        val out = ByteArray(cropWidth * cropHeight * 4)
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val argb = pixels[(top + y) * width + left + x]
                val di = (y * cropWidth + x) * 4
                out[di] = (argb ushr 16).toByte()
                out[di + 1] = (argb ushr 8).toByte()
                out[di + 2] = argb.toByte()
                out[di + 3] = (argb ushr 24).toByte()
            }
        }
        return Portrait(PolyBitmap(cropWidth, cropHeight, out).asComposeImageBitmap(), IntOffset.Zero, IntSize(cropWidth, cropHeight))
    }
}

private fun framing(type: CosmeticType): Framing = when (type) {
    CosmeticType.Hat, CosmeticType.Glasses -> Framing(FRONT_YAW_DEG, 0.9f, 1.35f)
    CosmeticType.Boots -> Framing(FRONT_YAW_DEG, 0.8f, 0.3f)
    CosmeticType.Shoulder -> Framing(FRONT_YAW_DEG, 0.55f, 0.62f)
    CosmeticType.Wings -> Framing(BACK_YAW_DEG, 0.42f, 0.62f)
    CosmeticType.Backpack -> Framing(BACK_YAW_DEG, 0.55f, 0.55f)
    CosmeticType.Cape -> Framing(BACK_YAW_DEG, 0.6f, 0.5f)
    CosmeticType.Glove -> Framing(FRONT_YAW_DEG, 0.7f, 0.42f)
    else -> Framing(FRONT_YAW_DEG, 0.42f, 0.52f)
}

internal fun bareSkin(): PreviewSkin = BareSkin.skin

@Composable
internal fun CosmeticArt(item: CosmeticUiItem, variantId: Int?, modifier: Modifier) {
    val id = variantId ?: item.variants.firstOrNull()?.id
    if (id == null || item.type == CosmeticType.Emote) {
        ItemArt(item, variantId, modifier)
        return
    }
    val (source, tick) = rememberCosmeticPreviewSource(id, item.type)
    if (item.type == CosmeticType.Aura) {
        AuraArt(id, tick, modifier)
        return
    }
    val bare = remember(source) { (source as? PlayerPreviewSource.Override)?.copy(skin = BareSkin.skin) }
    val key = "portrait-$id-$tick"
    val portrait = remember(key, bare) { mutableStateOf(Portraits.cached(key)) }
    LaunchedEffect(portrait) {
        if (portrait.value == null && bare != null) portrait.value = withContext(Dispatchers.Default) { Portraits.capture(key, bare, framing(item.type)) }
    }
    val shown = portrait.value
    if (shown == null) {
        Box(modifier)
        return
    }
    Canvas(modifier) { drawFitted(shown) }
}

private fun DrawScope.drawFitted(portrait: Portrait) {
    val scale = min(size.width / portrait.size.width, size.height / portrait.size.height) * FILL
    val width = (portrait.size.width * scale).roundToInt()
    val height = (portrait.size.height * scale).roundToInt()
    drawImage(
        portrait.image,
        srcOffset = portrait.offset,
        srcSize = portrait.size,
        dstOffset = IntOffset(((size.width - width) / 2f).roundToInt(), ((size.height - height) / 2f).roundToInt()),
        dstSize = IntSize(width, height),
    )
}

private class Scatter(val x: Float, val y: Float, val size: Float, val alpha: Float)

private val AURA_SCATTER = listOf(
    Scatter(0.5f, 0.52f, 0.44f, 1f),
    Scatter(0.22f, 0.3f, 0.24f, 0.75f),
    Scatter(0.8f, 0.26f, 0.2f, 0.65f),
    Scatter(0.2f, 0.76f, 0.16f, 0.55f),
    Scatter(0.78f, 0.74f, 0.26f, 0.8f),
)

@Composable
private fun AuraArt(id: Int, tick: Int, modifier: Modifier) {
    val sprite = remember(id, tick) { mutableStateOf(Portraits.cachedSprite(id)) }
    LaunchedEffect(sprite) {
        if (sprite.value == null) sprite.value = withContext(Dispatchers.IO) { runCatching { Portraits.sprite(id) }.getOrNull() }
    }
    val shown = sprite.value
    if (shown == null) {
        Box(modifier)
        return
    }
    Canvas(modifier) {
        val unit = min(size.width, size.height)
        val origin = Offset((size.width - unit) / 2f, (size.height - unit) / 2f)
        for (mote in AURA_SCATTER) {
            val side = (unit * mote.size).roundToInt()
            drawImage(
                shown.image,
                srcOffset = shown.offset,
                srcSize = shown.size,
                dstOffset = IntOffset(
                    (origin.x + unit * mote.x - side / 2f).roundToInt(),
                    (origin.y + unit * mote.y - side / 2f).roundToInt(),
                ),
                dstSize = IntSize(side, side),
                alpha = mote.alpha,
                filterQuality = FilterQuality.None,
            )
        }
    }
}
