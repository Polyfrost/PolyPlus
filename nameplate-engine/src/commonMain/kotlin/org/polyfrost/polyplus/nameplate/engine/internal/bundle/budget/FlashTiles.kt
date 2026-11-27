package org.polyfrost.polyplus.nameplate.engine.internal.bundle.budget

import org.polyfrost.polyplus.nameplate.engine.api.bundle.MaterialLayer
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.LayerContent
import org.polyfrost.polyplus.nameplate.engine.api.bundle.ParticleEmitter
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.limit.BundleLimits
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.SpriteAdaptive

internal class Footprint(
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int,
    val xs: IntArray,
    val ys: IntArray,
    val rows: IntArray,
) {
    val size: Int
        get() = xs.size

    companion object {
        val EMPTY: Footprint = Footprint(0, 0, 0, 0, IntArray(0), IntArray(0), IntArray(1))

        fun of(images: List<PixelImage>): Footprint {
            var x0 = Int.MAX_VALUE
            var y0 = Int.MAX_VALUE
            var x1 = Int.MIN_VALUE
            var y1 = Int.MIN_VALUE
            for (image in images) {
                val rects = image.geometry()
                for (r in rects.indices step PixelImage.STRIDE) {
                    x0 = minOf(x0, rects[r])
                    y0 = minOf(y0, rects[r + 1])
                    x1 = maxOf(x1, rects[r + 2])
                    y1 = maxOf(y1, rects[r + 3])
                }
            }
            if (x1 <= x0 || y1 <= y0) return EMPTY

            val width = x1 - x0
            val covered = BooleanArray(width * (y1 - y0))
            var count = 0
            for (image in images) {
                val rects = image.geometry()
                for (r in rects.indices step PixelImage.STRIDE) {
                    for (y in rects[r + 1] until rects[r + 3]) {
                        for (x in rects[r] until rects[r + 2]) {
                            val at = (y - y0) * width + (x - x0)
                            if (!covered[at]) {
                                covered[at] = true
                                count++
                            }
                        }
                    }
                }
            }

            return collect(x0, y0, x1, y1, covered, count)
        }

        private fun collect(x0: Int, y0: Int, x1: Int, y1: Int, covered: BooleanArray, count: Int): Footprint {
            val width = x1 - x0
            val xs = IntArray(count)
            val ys = IntArray(count)
            val rows = IntArray(y1 - y0 + 1)
            var k = 0
            for (y in y0 until y1) {
                rows[y - y0] = k
                for (x in x0 until x1) {
                    if (!covered[(y - y0) * width + (x - x0)]) continue
                    xs[k] = x
                    ys[k] = y
                    k++
                }
            }
            rows[y1 - y0] = k

            return Footprint(x0, y0, x1, y1, xs, ys, rows)
        }
    }
}

internal class Footprints(private val bundle: NameplateBundle, private val shapes: ShapeSet, private val block: TagBlock) {
    private val bodies = HashMap<PixelImage, Footprint>()
    private val sprites = HashMap<String, Footprint>()

    fun of(placed: Placed): Footprint {
        val body = placed.body
        if (body != null) return bodies.getOrPut(body) { Footprint.of(listOf(body)) }

        val layer = placed.layer
        return when (val content = layer.content) {
            is ParticleEmitter -> sprites.getOrPut(layer.id) { Footprint.of(content.states().map { shapes.sprite(layer, it).image }) }
            is LayerContent.Sprite -> sprites.getOrPut(layer.id) {
                val alternative = maxOf(0, content.adaptive.alternative(block.nameWidth))
                val keys = states(bundle, layer).filter { it / SpriteAdaptive.STATE_STRIDE == alternative }
                Footprint.of(keys.map { shapes.sprite(layer, it).image })
            }
            is LayerContent.Body, is MaterialLayer -> Footprint.EMPTY
        }
    }
}

internal class FlashTiles(private val weights: IntArray, private val fps: Int, frames: Int) {
    private val count = weights.size
    private val last = IntArray(count)
    private val anchor = IntArray(count)
    private val changedAt = IntArray(count)
    private val covered = IntArray(count) { -1 }
    private val ringSlot = IntArray(count) { -1 }
    private val ringHead = IntArray(count)
    private val ringSize = IntArray(count)
    private val direction = ByteArray(count)
    private val armed = ByteArray(count)
    private val diff = IntArray(frames + 2)
    private var ring = IntArray(RING_START)
    private var ringTop = 0

    var mostTimes: Int = 0
        private set

    fun start(tile: Int, value: Int, frame: Int) {
        last[tile] = value
        anchor[tile] = value
        changedAt[tile] = frame
    }

    fun step(tile: Int, value: Int, frame: Int) {
        val before = last[tile]
        if (value == before) return

        val toward = if (value > before) 1 else -1
        val run = direction[tile].toInt()
        if (run != 0 && run != toward) {
            val from = anchor[tile]
            val swing = if (before > from) before - from else from - before
            if (swing >= FLASH_DELTA && minOf(before, from) < DARK_LIMIT) {
                change(tile, run, changedAt[tile])
            }
            anchor[tile] = before
        }

        direction[tile] = toward.toByte()
        last[tile] = value
        changedAt[tile] = frame
    }

    fun mostPixels(frames: Int): Int {
        var running = 0
        var most = 0
        for (start in 0 until frames) {
            running += diff[start]
            most = maxOf(most, running)
        }

        return most
    }

    private fun change(tile: Int, toward: Int, time: Int) {
        val pending = armed[tile].toInt()
        if (pending != 0 && pending == -toward) {
            armed[tile] = 0
            flash(tile, time)
        } else {
            armed[tile] = toward.toByte()
        }
    }

    private fun flash(tile: Int, time: Int) {
        val slot = slotFor(tile)
        var head = ringHead[tile]
        var size = ringSize[tile]
        while (size > 0 && ring[slot + head] <= time - fps) {
            head = (head + 1) % fps
            size--
        }

        ring[slot + (head + size) % fps] = time
        size++
        ringHead[tile] = head
        ringSize[tile] = size
        val weight = weights[tile]
        if (weight > 0) {
            mostTimes = maxOf(mostTimes, size)
        }
        val needed = BundleLimits.MAX_FLASHES + 1
        if (size < needed) return

        val first = ring[slot + (head + size - needed) % fps]
        val from = maxOf(time - fps + 1, 0, covered[tile] + 1)
        if (from <= first) {
            diff[from] += weight
            diff[first + 1] -= weight
        }
        covered[tile] = maxOf(covered[tile], first)
    }

    private fun slotFor(tile: Int): Int {
        var slot = ringSlot[tile]
        if (slot < 0) {
            slot = ringTop
            ringTop += fps
            if (ringTop > ring.size) {
                ring = ring.copyOf(maxOf(ringTop, ring.size * 2))
            }
            ringSlot[tile] = slot
            ringHead[tile] = 0
            ringSize[tile] = 0
        }

        return slot
    }

    companion object {
        const val SCALE = 100_000
        private const val FLASH_DELTA = SCALE / 10
        private const val DARK_LIMIT = SCALE * 8 / 10
        private const val RING_START = 4096
    }
}
