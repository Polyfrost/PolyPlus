package org.polyfrost.polyplus.nameplate.engine.paint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.anim.TimedBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle.Depth
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelImage
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.palette.ResolvedPalette
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.bundle.SampleBundle
import org.polyfrost.polyplus.nameplate.engine.canvas.Composite
import org.polyfrost.polyplus.nameplate.engine.canvas.Rects
import org.polyfrost.polyplus.nameplate.engine.internal.anim.frameAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.paletteCanvas
import org.polyfrost.polyplus.nameplate.engine.support.CountingCache
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey
import org.polyfrost.polyplus.nameplate.engine.support.pixelKeyX
import org.polyfrost.polyplus.nameplate.engine.support.pixelKeyY

class LayerPainterTest {
    private val bundle = load(SampleBundle.files())
    private val painter = LayerPainter(PixelGrid())
    private val block = TagBlock.line(20, 0, 20)

    @Test
    fun everyLayerIsItsOwnImageInManifestOrder() {
        val painted = painter.paint(bundle, bundle.variant("sand"), block, 0, 0)
        assertEquals(listOf(0, 1, 1, 2, 3), painted.map { it.index })
        assertEquals(
            listOf(Depth.BACKGROUND, Depth.BACKGROUND, Depth.BACKGROUND, Depth.BACKGROUND, Depth.OVER_BACKGROUND),
            painted.map { it.depth },
        )
        assertEquals(listOf(false, false, true, false, false), painted.map { it.mirrorX })
    }

    @Test
    fun hiddenLayersAreSkippedAndSwapsKeepTheirSlot() {
        val manifest = SampleBundle.MANIFEST.replace(
            "\"sand\": { \"palette\": \"sand\" },",
            "\"sand\": { \"palette\": \"sand\", \"hide\": [\"trail\"], \"swap\": { \"ribbon\": \"ornaments\" } },",
        )
        val swapped = load(SampleBundle.files(manifest = manifest))
        val variant = swapped.variant("sand")
        val painted = painter.paint(swapped, variant, block, 0, 0)
        assertEquals(listOf(1, 1, 2, 3, 3), painted.map { it.index })
        assertEquals(Depth.BACKGROUND, painted.last().depth)
        assertEquals(painted[0].x, painted[3].x)
        assertEquals(painted[0].y, painted[3].y)
        assertEquals(listOf(false, true, false, false, true), painted.map { it.mirrorX })
    }

    @Test
    fun spritesAreStoredAtTheirOriginWithAnOffset() {
        val spike = painter.paint(bundle, bundle.variant("sand"), block, 0, 0)[1]
        assertEquals(0, spike.x)
        assertEquals(-5, spike.y)
        assertEquals(4, spike.width)
        assertEquals(false, spike.mirrorX)
        val pixels = Rects.pixels(spike.image.resolve(bundle.variant("sand").resolved))
        assertEquals(color("border"), pixels[key(1, 0)])
        assertEquals(color("accent"), pixels[key(1, 1)])
        assertEquals(color("border"), pixels[key(3, 3)])
        assertTrue(pixels.keys.all { pixelKeyX(it) in 0 until 4 && pixelKeyY(it) in 0 until 4 })
        assertEquals(12, pixels.size)
    }

    @Test
    fun aMirroredSpriteIsASecondOpOnTheSameImage() {
        val (spike, mirror) = painter.paint(bundle, bundle.variant("sand"), block, 0, 0).slice(1..2)
        assertSame(spike.image, mirror.image)
        assertEquals(false, spike.mirrorX)
        assertEquals(true, mirror.mirrorX)
        assertEquals(spike.y, mirror.y)
        assertEquals(spike.width, mirror.width)
        assertEquals(16, mirror.x)
    }

    @Test
    fun bodiesAreRelativeToTheirRect() {
        val body = painter.paint(bundle, bundle.variant("sand"), block, 0, 0)[3]
        assertEquals(-5, body.x)
        assertEquals(-2, body.y)
        assertEquals(30, body.width)
        assertEquals(false, body.mirrorX)
        val pixels = Rects.pixels(body.image.resolve(bundle.variant("sand").resolved))
        assertEquals(color("border"), pixels[key(-1, 5)])
        assertEquals(color("fill"), pixels[key(0, 5)])
        assertEquals(color("fill"), pixels[key(29, 5)])
        assertEquals(color("fill"), pixels[key(28, 11)])
        assertEquals(color("border"), pixels[key(29, 11)])
        assertEquals(color("border"), pixels[key(30, 5)])
        assertNull(pixels[key(-2, 5)])
        assertEquals(-1, pixels.keys.minOf { pixelKeyX(it) })
        assertEquals(-1, pixels.keys.minOf { pixelKeyY(it) })
    }

    @Test
    fun bodyPadsTheBlockToItsMinimumWidthWithRings() {
        val pixels = pixels(0)
        val fill = color("fill")
        val border = color("border")
        assertEquals(border, pixels[key(-6, 3)])
        assertEquals(fill, pixels[key(-5, 3)])
        assertEquals(fill, pixels[key(23, 9)])
        assertEquals(border, pixels[key(24, 9)])
        assertEquals(border, pixels[key(25, 3)])
        assertNull(pixels[key(-7, 3)])
        assertNull(pixels[key(-6, -3)])
    }

    @Test
    fun spritesPivotOnTheirAnchorAndMirror() {
        val pixels = pixels(0)
        val border = color("border")
        val accent = color("accent")
        assertEquals(border, pixels[key(1, -5)])
        assertEquals(accent, pixels[key(1, -4)])
        assertEquals(border, pixels[key(18, -5)])
        assertEquals(accent, pixels[key(18, -4)])
        assertEquals(border, pixels[key(1, -3)])
    }

    @Test
    fun animatedSpritesStepWithTheirLayer() {
        val dark = color("trail", 0)
        val light = color("trail", 1)
        val (x, y) = 24 to 10
        assertEquals(dark, pixels(0)[key(x, y)])
        assertEquals(dark, pixels(1)[key(x, y)])
        assertEquals(light, pixels(2)[key(x, y)])
        assertEquals(dark, pixels(16)[key(x, y)])
    }

    @Test
    fun colorwaysPaintTheSameGeometry() {
        val sand = bundle.variant("sand")
        val moss = bundle.variant("moss")
        val sandPixels = Composite.pixels(painter.paint(bundle, sand, block, 0, 0), sand.resolved)
        val mossPixels = Composite.pixels(painter.paint(bundle, moss, block, 0, 0), moss.resolved)
        assertEquals(sandPixels.keys, mossPixels.keys)
        assertNotEquals(sandPixels, mossPixels)
    }

    @Test
    fun depthsCompositeApart() {
        val variant = bundle.variant("sand")
        val painted = painter.paint(bundle, variant, block, 0, 0)
        val ribbon = Composite.pixels(painted, variant.resolved, Depth.OVER_BACKGROUND)
        assertEquals(7, ribbon.size)
        assertEquals(color("ribbon", 4), ribbon[key(-6, -3)])
        assertTrue(Composite.pixels(painted, variant.resolved, Depth.BACKGROUND).values.none { it == color("ribbon", 4) })
    }

    @Test
    fun translucentLayersBlendSourceOver() {
        val grid = PixelGrid()
        val bottom = grid.paletteCanvas().also { it[0, 0] = 1 }.compile()
        val top = grid.paletteCanvas().also { it[0, 0] = 0 }.compile()
        val palette = ResolvedPalette(intArrayOf(0x80FF0000.toInt(), 0xFF0000FF.toInt()))
        val ops = listOf(op(0, bottom), op(1, top))
        assertEquals(0xFF80007F.toInt(), Composite.pixels(ops, palette)[key(0, 0)])
        assertEquals(0xFF0000FF.toInt(), Composite.pixels(ops.take(1), palette)[key(0, 0)])
    }

    @Test
    fun gradientsSpreadTonesOverRows() {
        assertEquals(0, LayerPainter.gradientTone(0, 12, 12))
        assertEquals(5, LayerPainter.gradientTone(5, 12, 12))
        assertEquals(11, LayerPainter.gradientTone(11, 12, 12))
        assertEquals(11, LayerPainter.gradientTone(17, 18, 12))
        assertEquals(0, LayerPainter.gradientTone(0, 1, 12))
    }

    @Test
    fun spritesShowTheFrameTheirTimingPicks() {
        val timed = TimedBundle.bundle()
        val variant = timed.variant(null)
        var scheduled = 0
        for (seed in listOf(1, 42, -90210)) {
            for (frame in 0 until 48) {
                for (op in painter.paint(timed, variant, block, frame, seed)) {
                    val layer = timed.layer(timed.layers[op.index].id)
                    val sprite = layer.content as? NameplateBundle.LayerContent.Sprite ?: continue
                    val shown = frameAt(timed, layer, frame, seed)
                    if (shown != (frame.mod(timed.loop) / layer.step) % sprite.frames.size) {
                        scheduled++
                    }
                    val canvas = PixelGrid().paletteCanvas()
                    canvas.sprite(sprite.frames[shown], 0, 0, mirrorX = false)
                    assertEquals(
                        Rects.pixels(canvas.compile().resolve(variant.resolved)),
                        Rects.pixels(op.image.resolve(variant.resolved)),
                        "${layer.id} at frame $frame, seed $seed",
                    )
                }
            }
        }
        assertTrue(scheduled > 0)
    }

    @Test
    fun aSpriteCostsOneEntryPerStateWhateverTheBlock() {
        val timed = TimedBundle.bundle()
        val variant = timed.variant(null)
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        val body = timed.layers.map { it.content }.filterIsInstance<NameplateBundle.LayerContent.Body>().single()
        val sprites = timed.layers.filter { it.content is NameplateBundle.LayerContent.Sprite }
        val shown = HashSet<Pair<String, Int>>()
        val sizes = HashSet<Pair<Int, Int>>()
        for (block in blocks()) {
            val rect = body.rect(block)
            sizes.add(rect.width to rect.height)
            for (seed in listOf(1, 42, -90210)) {
                for (frame in 0 until 48) {
                    cached.paint(timed, variant, block, frame, seed)
                    for (layer in sprites) {
                        shown.add(layer.id to frameAt(timed, layer, frame, seed))
                    }
                }
            }
        }
        val reachable = sprites.flatMap { layer -> states(timed, layer).map { layer.id to it } }.toSet()
        assertEquals(reachable, shown)
        assertTrue(sizes.size > 10)
        assertEquals(reachable.size + sizes.size, cache.size)
    }

    @Test
    fun aBodyCostsOneEntryPerDistinctRectSize() {
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        val body = bundle.layers.map { it.content }.filterIsInstance<NameplateBundle.LayerContent.Body>().single()
        val sizes = HashSet<Pair<Int, Int>>()
        val blocks = blocks()
        for (block in blocks) {
            val rect = body.rect(block)
            sizes.add(rect.width to rect.height)
            cached.paint(bundle, bundle.variant("sand"), block, 0, 0)
        }
        assertTrue(sizes.size < blocks.size)
        assertTrue(sizes.size > 5)
        assertEquals(sizes.size + 3, cache.size)
    }

    @Test
    fun paintingEveryColourwayAddsNoEntries() {
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        for (frame in 0 until 16) {
            cached.paint(bundle, bundle.variant("sand"), block, frame, 0)
        }
        val size = cache.size
        val misses = cache.misses
        assertTrue(size > 0)
        for (key in listOf("dusk", "moss", "sand")) {
            for (frame in 0 until 16) {
                cached.paint(bundle, bundle.variant(key), block, frame, 0)
            }
        }
        assertEquals(size, cache.size)
        assertEquals(misses, cache.misses)
    }

    @Test
    fun swapsKeyOnTheLayerThatIsPainted() {
        val manifest = SampleBundle.MANIFEST
            .replace("\"sand\": { \"palette\": \"sand\" },", "\"sand\": { \"palette\": \"sand\", \"hide\": [\"trail\"], \"swap\": { \"ribbon\": \"ornaments\" } },")
            .replace("\"moss\": { \"palette\": \"moss\" }", "\"moss\": { \"palette\": \"moss\", \"hide\": [\"trail\"] }")
        val swapped = load(SampleBundle.files(manifest = manifest))
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        cached.paint(swapped, swapped.variant("sand"), block, 0, 0)
        assertEquals(2, cache.size)
        cached.paint(swapped, swapped.variant("moss"), block, 0, 0)
        assertEquals(3, cache.size)
    }

    @Test
    fun twoSeedsShowingTheSameFrameShareOneEntry() {
        val picked = TimedBundle.bundle(TimedBundle.pick("1.0"))
        val variant = picked.variant(null)
        val eye = picked.layer("eye")
        val byFrame = (0 until 64).groupBy { frameAt(picked, eye, 0, it) }
        val (frame, seeds) = byFrame.entries.first { it.value.size > 1 }
        val other = byFrame.entries.first { it.key != frame }.value.first()
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        cached.paint(picked, variant, block, 0, seeds[0])
        assertEquals(2, cache.size)
        val misses = cache.misses
        cached.paint(picked, variant, block, 0, seeds[1])
        assertEquals(2, cache.size)
        assertEquals(misses, cache.misses)
        cached.paint(picked, variant, block, 0, other)
        assertEquals(3, cache.size)
    }

    @Test
    fun cachedOutputMatchesAFreshPainterPixelForPixel() {
        val timed = TimedBundle.bundle()
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        val freshGrid = PixelGrid()
        for ((target, keys) in listOf(bundle to listOf("sand", "dusk", "moss"), timed to listOf(null))) {
            for (key in keys) {
                val variant = target.variant(key)
                for (shape in blocks().filterIndexed { index, _ -> index % 3 == 0 }) {
                    for (seed in listOf(1, 42)) {
                        for (frame in 0 until 32 step 5) {
                            val fresh = LayerPainter(freshGrid, newCache().cache).paint(target, variant, shape, frame, seed)
                            val reused = cached.paint(target, variant, shape, frame, seed)
                            val label = "${target.id} $key at frame $frame, seed $seed"
                            assertEquals(fresh.size, reused.size, label)
                            for ((expected, actual) in fresh.zip(reused)) {
                                assertEquals(expected.copy(image = actual.image), actual, label)
                                assertEquals(Composite.place(expected, variant.resolved), Composite.place(actual, variant.resolved), label)
                            }
                            assertEquals(Composite.pixels(fresh, variant.resolved), Composite.pixels(reused, variant.resolved), label)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun aSecondPaintOfTheSameInputsIsAllHits() {
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        val first = cached.paint(bundle, bundle.variant("sand"), block, 3, 0)
        assertEquals(cache.size.toLong(), cache.misses)
        val misses = cache.misses
        val second = cached.paint(bundle, bundle.variant("sand"), block, 3, 0)
        assertEquals(misses, cache.misses)
        for ((a, b) in first.zip(second)) {
            assertSame(a.image, b.image)
        }
    }

    private fun newCache() = CountingCache()

    private fun blocks(): List<TagBlock> {
        val shapes = ArrayList<TagBlock>()
        for (width in 6..66 step 3) {
            shapes.add(TagBlock.line(width, 0, width))
        }
        for (width in listOf(20, 35, 48)) {
            shapes.add(TagBlock.display(intArrayOf(width, 20), 2, 48, TagBlock.Align.CENTER, 1, 0, 20, false))
            shapes.add(TagBlock.display(intArrayOf(30, 0, width), 3, 48, TagBlock.Align.LEFT, 2, 0, width, false))
        }
        return shapes
    }

    private fun op(index: Int, image: PixelImage) = LayerPainter.Painted(index, Depth.BACKGROUND, 0, 0, false, 1, image, 255)

    private fun load(source: BundleSource): NameplateBundle {
        val result = BundleLoader(setOf("canvas", "sprites")).load(source)
        return assertIs<BundleLoad.Loaded>(result, result.toString()).bundle
    }

    private fun pixels(frame: Int): Map<Long, Int> {
        val variant = bundle.variant("sand")
        return Composite.pixels(painter.paint(bundle, variant, block, frame, 0), variant.resolved, Depth.BACKGROUND)
    }

    private fun color(slot: String, tone: Int = 0): Int = bundle.variant("sand").resolved.color(bundle.palette.ref(slot, tone))

    private fun key(x: Int, y: Int): Long = pixelKey(x, y)
}
