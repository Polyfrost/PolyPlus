package org.polyfrost.polyplus.client.gui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.apache.logging.log4j.LogManager
import org.polyfrost.compose.backend.asComposeImageBitmap
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.polyplus.client.PolyPlusClient
import org.polyfrost.polyplus.client.utils.runSuspendCatching
import org.polyfrost.polyplus.libs.ktor.client.call.body
import org.polyfrost.polyplus.libs.ktor.client.request.get

class AnimatedPreview(
    val sheet: ImageBitmap,
    val frameWidth: Int,
    val frameHeight: Int,
    private val columns: Int,
    durationsMs: IntArray,
) {
    val frameCount: Int = durationsMs.size

    private val ends = IntArray(durationsMs.size).also { ends ->
        var total = 0
        for (i in durationsMs.indices) {
            total += durationsMs[i]
            ends[i] = total
        }
    }

    val totalMs: Int get() = ends.last()

    fun frameAt(elapsedMs: Long): Int {
        val t = (elapsedMs % totalMs).toInt()
        val index = ends.indexOfFirst { t < it }
        return if (index < 0) frameCount - 1 else index
    }

    fun frameOffset(index: Int): IntOffset = IntOffset((index % columns) * frameWidth, (index / columns) * frameHeight)
}

object AnimatedPreviewCache {
    private val LOGGER = LogManager.getLogger()
    private const val MAX_PREVIEWS = 3
    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val MAX_FRAME_HEIGHT = 200
    private const val RETRY_DELAY_MS = 60_000L

    private val cache: MutableMap<String, AnimatedPreview> =
        Collections.synchronizedMap(object : LinkedHashMap<String, AnimatedPreview>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, AnimatedPreview>) = size > MAX_PREVIEWS
        })
    private val pending = ConcurrentHashMap<String, Deferred<AnimatedPreview?>>()
    private val retryAt = ConcurrentHashMap<String, Long>()

    fun cached(url: String): AnimatedPreview? = cache[url]

    suspend fun get(url: String): AnimatedPreview? {
        cache[url]?.let { return it }
        if ((retryAt[url] ?: 0L) > System.currentTimeMillis()) return null
        val job = pending.computeIfAbsent(url) {
            PolyPlusClient.SCOPE.async(Dispatchers.IO) { load(url) }.also { job ->
                job.invokeOnCompletion { pending.remove(url, job) }
            }
        }
        return job.await()
    }

    private suspend fun load(url: String): AnimatedPreview? =
        runSuspendCatching {
            val bytes = PolyPlusClient.HTTP.get(url).body<ByteArray>()
            require(bytes.size <= MAX_BYTES) { "preview exceeds 16 MiB" }
            val (frames, durations) = requireNotNull(AnimatedWebp.decode(bytes, MAX_FRAME_HEIGHT)) { "not a WebP image" }
            packSheet(frames, durations)
        }.onFailure {
            retryAt[url] = System.currentTimeMillis() + RETRY_DELAY_MS
            LOGGER.warn("Failed to load animated preview {}", url, it)
        }.getOrNull()?.also {
            cache[url] = it
            retryAt.remove(url)
        }
}

private const val MAX_SHEET_SIZE = 4096

private fun packSheet(frames: List<PolyBitmap>, durations: IntArray): AnimatedPreview {
    val width = frames.first().width
    val height = frames.first().height
    val columns = (MAX_SHEET_SIZE / width).coerceIn(1, frames.size)
    val count = minOf(frames.size, columns * (MAX_SHEET_SIZE / height).coerceAtLeast(1))
    val rows = (count + columns - 1) / columns
    val sheetWidth = columns * width
    val pixels = ByteArray(sheetWidth * rows * height * 4)
    for (i in 0 until count) {
        val frame = frames[i]
        val x = (i % columns) * width
        val y = (i / columns) * height
        for (row in 0 until height) {
            frame.pixels.copyInto(pixels, ((y + row) * sheetWidth + x) * 4, row * width * 4, (row + 1) * width * 4)
        }
    }
    return AnimatedPreview(PolyBitmap(sheetWidth, rows * height, pixels).asComposeImageBitmap(), width, height, columns, durations.copyOf(count))
}

@Composable
fun rememberAnimatedPreview(url: String?): AnimatedPreview? =
    produceState(url?.let { AnimatedPreviewCache.cached(it) }, url) {
        if (url != null && value == null) value = AnimatedPreviewCache.get(url)
    }.value

@Composable
fun AnimatedPreviewImage(preview: AnimatedPreview, modifier: Modifier) {
    val frameNanos by produceState(0L) { while (true) withFrameNanos { value = it } }
    val start = remember(preview) { System.nanoTime() }
    Spacer(
        modifier.drawBehind {
            if (frameNanos == 0L) return@drawBehind
            val frame = preview.frameAt((System.nanoTime() - start) / 1_000_000L)
            val scale = min(size.width / preview.frameWidth, size.height / preview.frameHeight)
            val w = (preview.frameWidth * scale).roundToInt()
            val h = (preview.frameHeight * scale).roundToInt()
            drawImage(
                preview.sheet,
                srcOffset = preview.frameOffset(frame),
                srcSize = IntSize(preview.frameWidth, preview.frameHeight),
                dstOffset = IntOffset(((size.width - w) / 2f).roundToInt(), ((size.height - h) / 2f).roundToInt()),
                dstSize = IntSize(w, h),
                filterQuality = FilterQuality.Low,
            )
        },
    )
}

internal object AnimatedWebp {
    private const val DEFAULT_FRAME_MS = 50
    private const val ALPHA_FLAG = 0x10

    private class Frame(val x: Int, val y: Int, val width: Int, val height: Int, val durationMs: Int, val blend: Boolean, val dispose: Boolean, val data: ByteArray)

    fun decode(bytes: ByteArray, maxHeight: Int): Pair<List<PolyBitmap>, IntArray>? {
        if (bytes.size < 12 || fourcc(bytes, 0) != "RIFF" || fourcc(bytes, 8) != "WEBP") return null
        var canvasWidth = 0
        var canvasHeight = 0
        val frames = ArrayList<Frame>()
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = fourcc(bytes, pos)
            val size = u32(bytes, pos + 4)
            val start = pos + 8
            if (size < 0 || start + size > bytes.size) break
            when (id) {
                "VP8X" -> {
                    canvasWidth = u24(bytes, start + 4) + 1
                    canvasHeight = u24(bytes, start + 7) + 1
                }
                "ANMF" -> frames += Frame(
                    x = u24(bytes, start) * 2,
                    y = u24(bytes, start + 3) * 2,
                    width = u24(bytes, start + 6) + 1,
                    height = u24(bytes, start + 9) + 1,
                    durationMs = u24(bytes, start + 12).takeIf { it > 0 } ?: DEFAULT_FRAME_MS,
                    blend = bytes[start + 15].toInt() and 2 == 0,
                    dispose = bytes[start + 15].toInt() and 1 != 0,
                    data = bytes.copyOfRange(start + 16, start + size),
                )
            }
            pos = start + size + (size and 1)
        }
        if (frames.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) {
            val still = (if (fourcc(bytes, 12) == "VP8L") lossless(bytes, 12) else PolyBitmap.decode(bytes)) ?: return null
            return listOf(downscale(still.pixels, still.width, still.height, factor(still.height, maxHeight))) to intArrayOf(DEFAULT_FRAME_MS)
        }

        val factor = factor(canvasHeight, maxHeight)
        val canvas = ByteArray(canvasWidth * canvasHeight * 4)
        val out = ArrayList<PolyBitmap>(frames.size)
        val durations = IntArray(frames.size)
        frames.forEachIndexed { index, frame ->
            val image = if (fourcc(frame.data, 0) == "VP8L") lossless(frame.data, 0) else PolyBitmap.decode(standalone(frame))
            image?.let { draw(canvas, canvasWidth, canvasHeight, frame, it) }
            out += downscale(canvas, canvasWidth, canvasHeight, factor)
            durations[index] = frame.durationMs
            if (frame.dispose) clear(canvas, canvasWidth, canvasHeight, frame)
        }
        return out to durations
    }

    private fun lossless(bytes: ByteArray, chunk: Int): PolyBitmap? = runCatching {
        val image = WebpLossless.decode(bytes, chunk + 8, minOf(u32(bytes, chunk + 4), bytes.size - chunk - 8))
        val pixels = ByteArray(image.argb.size * 4)
        for (i in image.argb.indices) {
            val c = image.argb[i]
            pixels[i * 4] = (c shr 16).toByte()
            pixels[i * 4 + 1] = (c shr 8).toByte()
            pixels[i * 4 + 2] = c.toByte()
            pixels[i * 4 + 3] = (c ushr 24).toByte()
        }
        PolyBitmap(image.width, image.height, pixels)
    }.getOrNull()

    private fun factor(height: Int, maxHeight: Int): Int = ((height + maxHeight - 1) / maxHeight).coerceAtLeast(1)

    private fun standalone(frame: Frame): ByteArray {
        val hasAlpha = fourcc(frame.data, 0) == "ALPH"
        val body = if (hasAlpha) {
            val header = ByteArray(18)
            "VP8X".toByteArray(Charsets.US_ASCII).copyInto(header, 0)
            putU32(header, 4, 10)
            header[8] = ALPHA_FLAG.toByte()
            putU24(header, 12, frame.width - 1)
            putU24(header, 15, frame.height - 1)
            header + frame.data
        } else {
            frame.data
        }
        val riff = ByteArray(12)
        "RIFF".toByteArray(Charsets.US_ASCII).copyInto(riff, 0)
        putU32(riff, 4, body.size + 4)
        "WEBP".toByteArray(Charsets.US_ASCII).copyInto(riff, 8)
        return riff + body
    }

    private fun draw(canvas: ByteArray, width: Int, height: Int, frame: Frame, image: PolyBitmap) {
        val src = image.pixels
        val w = minOf(image.width, width - frame.x)
        val h = minOf(image.height, height - frame.y)
        for (row in 0 until h) {
            for (col in 0 until w) {
                val s = (row * image.width + col) * 4
                val d = ((frame.y + row) * width + frame.x + col) * 4
                val sa = src[s + 3].toInt() and 0xFF
                if (!frame.blend || sa == 255) {
                    src.copyInto(canvas, d, s, s + 4)
                    continue
                }
                if (sa == 0) continue
                val da = canvas[d + 3].toInt() and 0xFF
                val keep = da * (255 - sa) / 255
                val oa = sa + keep
                for (c in 0 until 3) {
                    val sc = src[s + c].toInt() and 0xFF
                    val dc = canvas[d + c].toInt() and 0xFF
                    canvas[d + c] = ((sc * sa + dc * keep) / oa).toByte()
                }
                canvas[d + 3] = oa.toByte()
            }
        }
    }

    private fun clear(canvas: ByteArray, width: Int, height: Int, frame: Frame) {
        val right = minOf(frame.x + frame.width, width)
        val bottom = minOf(frame.y + frame.height, height)
        for (row in frame.y until bottom) {
            canvas.fill(0, (row * width + frame.x) * 4, (row * width + right) * 4)
        }
    }

    private fun downscale(pixels: ByteArray, width: Int, height: Int, factor: Int): PolyBitmap {
        if (factor == 1) return PolyBitmap(width, height, pixels.copyOf())
        val w = (width / factor).coerceAtLeast(1)
        val h = (height / factor).coerceAtLeast(1)
        val out = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0L
                var g = 0L
                var b = 0L
                var a = 0L
                for (dy in 0 until factor) {
                    val sy = y * factor + dy
                    if (sy >= height) break
                    for (dx in 0 until factor) {
                        val sx = x * factor + dx
                        if (sx >= width) break
                        val i = (sy * width + sx) * 4
                        val pa = pixels[i + 3].toLong() and 0xFF
                        r += (pixels[i].toLong() and 0xFF) * pa
                        g += (pixels[i + 1].toLong() and 0xFF) * pa
                        b += (pixels[i + 2].toLong() and 0xFF) * pa
                        a += pa
                    }
                }
                val o = (y * w + x) * 4
                if (a > 0) {
                    out[o] = (r / a).toByte()
                    out[o + 1] = (g / a).toByte()
                    out[o + 2] = (b / a).toByte()
                    out[o + 3] = (a / (factor * factor)).toByte()
                }
            }
        }
        return PolyBitmap(w, h, out)
    }

    private fun fourcc(bytes: ByteArray, at: Int): String =
        if (at + 4 > bytes.size) "" else String(bytes, at, 4, Charsets.US_ASCII)

    private fun u24(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or ((bytes[at + 2].toInt() and 0xFF) shl 16)

    private fun u32(bytes: ByteArray, at: Int): Int = u24(bytes, at) or ((bytes[at + 3].toInt() and 0xFF) shl 24)

    private fun putU24(bytes: ByteArray, at: Int, value: Int) {
        bytes[at] = value.toByte()
        bytes[at + 1] = (value shr 8).toByte()
        bytes[at + 2] = (value shr 16).toByte()
    }

    private fun putU32(bytes: ByteArray, at: Int, value: Int) {
        putU24(bytes, at, value)
        bytes[at + 3] = (value shr 24).toByte()
    }
}
