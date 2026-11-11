package org.polyfrost.polyplus.client.nameplate.render.legacy

//? if = 1.8.9 {
/*import net.minecraft.client.render.texture.DynamicTexture
import org.apache.logging.log4j.LogManager
import org.polyfrost.polyplus.client.nameplate.render.NameplateCanvas
import org.polyfrost.polyplus.client.nameplate.render.NameplateFrame
import org.polyfrost.polyplus.client.nameplate.render.NameplatePaint
import org.polyfrost.polyplus.client.nameplate.render.material.NameplateMaterialFallbacks
import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import java.util.WeakHashMap

object LegacyMaterialTexture {
    private val LOGGER = LogManager.getLogger("PolyPlus/Nameplates")

    private const val STRIDE = 5
    private const val COLOR = 4
    private const val MAX_SIZE = 1024
    private const val WHITE = -1
    private const val ALPHA_SHIFT = 24
    private const val IDLE_NANOS = 3_000_000_000L
    private const val SWEEP_NANOS = 1_000_000_000L

    private data class Key(val layer: MaterialLayer, val x0: Int, val y0: Int, val width: Int, val height: Int)

    private class Entry(val texture: DynamicTexture, val x0: Int, val y0: Int, val width: Int, val height: Int) {
        var image: PixelImage? = null
        var lastUsed = 0L
    }

    private val entries = HashMap<Key, Entry>()
    private val keys = WeakHashMap<PixelImage, Key>()
    private var lastSweep = 0L
    private var failed = false

    fun submit(
        frame: NameplateFrame,
        bundle: NameplateBundle,
        ops: List<LayerPainter.Painted>,
        palette: ResolvedPalette,
        canvas: (List<LayerPainter.Painted>) -> Unit,
    ) {
        val now = System.nanoTime()
        sweep(now)
        val drawn = ArrayList<LayerPainter.Painted>(ops.size)
        var start = 0
        for ((i, op) in ops.withIndex()) {
            val material = op.material
            if (material == null) {
                drawn += op
                continue
            }
            val gpu = LegacyMaterialGpu.prepare(bundle, op, material, palette)
            if (gpu != null) {
                drawn += op
                if (i > start) {
                    canvas(drawn.subList(start, i))
                }
                LegacyMaterialGpu.submit(frame, op, material, gpu)
                start = i + 1
                continue
            }
            val painted = NameplateMaterialFallbacks.painted(bundle, op, material)
            drawn += painted
            val entry = entry(painted, material.layer) ?: continue
            if (i > start) {
                canvas(drawn.subList(start, i))
            }
            entry.lastUsed = now
            draw(frame, painted, entry)
            start = i + 1
        }
        if (start < drawn.size) {
            canvas(if (start == 0) drawn else drawn.subList(start, drawn.size))
        }
    }

    private fun draw(frame: NameplateFrame, op: LayerPainter.Painted, entry: Entry) {
        var color = NameplatePaint.scaleAlpha(WHITE, op.opacity)
        if (frame.plateAlpha < 1f) {
            color = NameplateFrame.fade(color, frame.plateAlpha)
        }
        if (color ushr ALPHA_SHIFT == 0) {
            return
        }
        var left = entry.x0
        var top = entry.y0
        var right = entry.x0 + entry.width
        var bottom = entry.y0 + entry.height
        val clip = op.clip
        if (clip != null) {
            left = maxOf(left, clip.x0)
            top = maxOf(top, clip.y0)
            right = minOf(right, clip.x1)
            bottom = minOf(bottom, clip.y1)
            if (right <= left || bottom <= top) {
                return
            }
        }
        val u0 = (left - entry.x0).toFloat() / entry.width
        val u1 = (right - entry.x0).toFloat() / entry.width
        val v0 = (top - entry.y0).toFloat() / entry.height
        val v1 = (bottom - entry.y0).toFloat() / entry.height
        val x0 = (if (op.mirrorX) op.x + op.width - left else op.x + left).toFloat()
        val x1 = (if (op.mirrorX) op.x + op.width - right else op.x + right).toFloat()
        val y0 = (op.y + top).toFloat()
        val y1 = (op.y + bottom).toFloat()
        val z = NameplateCanvas.depth(op)
        val minX = minOf(x0, x1)
        val maxX = maxOf(x0, x1)
        val minU = if (op.mirrorX) u1 else u0
        val maxU = if (op.mirrorX) u0 else u1
        LegacyVertexSink.draw(entry.texture.glId, frame.x, frame.y) { _, buffer ->
            buffer.addVertex(minX, y1, z).setUv(minU, v1).setColor(color)
            buffer.addVertex(maxX, y1, z).setUv(maxU, v1).setColor(color)
            buffer.addVertex(maxX, y0, z).setUv(maxU, v0).setColor(color)
            buffer.addVertex(minX, y0, z).setUv(minU, v0).setColor(color)
        }
    }

    private fun entry(op: LayerPainter.Painted, layer: MaterialLayer): Entry? {
        val image = op.image
        if (failed || op.opacity <= 0 || image.usesPalette || image.rectCount == 0) {
            return null
        }
        val key = keys[image] ?: bounds(layer, image)?.also { keys[image] = it } ?: return null
        val entry = entries[key] ?: create(key) ?: return null
        if (entry.image !== image) {
            upload(entry, image)
        }
        return entry
    }

    private fun bounds(layer: MaterialLayer, image: PixelImage): Key? {
        val rects = image.resolve()
        var x0 = Int.MAX_VALUE
        var y0 = Int.MAX_VALUE
        var x1 = Int.MIN_VALUE
        var y1 = Int.MIN_VALUE
        for (i in rects.indices step STRIDE) {
            x0 = minOf(x0, rects[i])
            y0 = minOf(y0, rects[i + 1])
            x1 = maxOf(x1, rects[i + 2])
            y1 = maxOf(y1, rects[i + 3])
        }
        val width = x1 - x0
        val height = y1 - y0
        if (width !in 1..MAX_SIZE || height !in 1..MAX_SIZE) {
            return null
        }
        return Key(layer, x0, y0, width, height)
    }

    private fun create(key: Key): Entry? = try {
        Entry(DynamicTexture(key.width, key.height), key.x0, key.y0, key.width, key.height).also { entries[key] = it }
    } catch (e: RuntimeException) {
        failed = true
        LOGGER.error("Nameplate material fallback textures could not be created; materials draw as quads", e)
        null
    }

    private fun upload(entry: Entry, image: PixelImage) {
        val pixels = entry.texture.pixels
        pixels.fill(0)
        val rects = image.resolve()
        for (i in rects.indices step STRIDE) {
            val argb = rects[i + COLOR]
            for (y in rects[i + 1] - entry.y0 until rects[i + 3] - entry.y0) {
                pixels.fill(argb, y * entry.width + rects[i] - entry.x0, y * entry.width + rects[i + 2] - entry.x0)
            }
        }
        entry.texture.upload()
        entry.image = image
    }

    private fun sweep(now: Long) {
        if (now - lastSweep < SWEEP_NANOS) {
            return
        }
        lastSweep = now
        entries.values.removeAll { entry ->
            val idle = now - entry.lastUsed >= IDLE_NANOS
            if (idle) entry.texture.clearGlId()
            idle
        }
    }
}
*///?}
