package org.polyfrost.polyplus.nameplate.engine.js

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.khronos.webgl.get
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.bundle.SampleBundle
import org.polyfrost.polyplus.nameplate.engine.canvas.Composite
import org.polyfrost.polyplus.nameplate.engine.canvas.Rects
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey

class NameplateEngineJsTest {
    private val capabilities = arrayOf("canvas", "sprites")

    @Test
    fun paintsTheSameLayersAsTheLayerPainter() {
        val preview = assertNotNull(nameplateLoad(files(), capabilities).preview)
        val bundle = loadedBundle()
        val painter = LayerPainter(PixelGrid())

        for (variant in preview.variants) {
            val palette = bundle.variant(variant).resolved
            for (frame in 0 until bundle.loop) {
                val layers = preview.paint(variant, nameplateLineBlock(20, 0, 20), frame, 0)
                val expected = painter.paint(bundle, bundle.variant(variant), TagBlock.line(20, 0, 20), frame, 0)
                assertEquals(expected.size, layers.size)
                for ((layer, painted) in layers.zip(expected)) {
                    assertEquals(painted.index, layer.index)
                    assertEquals(painted.depth.name.lowercase(), layer.depth)
                    assertEquals(painted.x, layer.x)
                    assertEquals(painted.y, layer.y)
                    assertEquals(painted.mirrorX, layer.mirrorX)
                    assertEquals(painted.width, layer.width)
                    assertEquals(Rects.pixels(painted.image.resolve(palette)), rasterPixels(layer))
                }
                assertEquals(Composite.pixels(expected, palette), composite(layers))
            }
        }
    }

    @Test
    fun mirroredPairSharesOneRaster() {
        val preview = assertNotNull(nameplateLoad(files(), capabilities).preview)
        val layers = preview.paint("sand", nameplateLineBlock(20, 0, 20), 0, 0)
        val ornaments = layers.filter { it.index == 1 }
        assertEquals(listOf(false, true), ornaments.map { it.mirrorX })
        assertSame(ornaments[0].rgba, ornaments[1].rgba)
        assertEquals(ornaments[0].width, ornaments[1].width)
        assertEquals(ornaments[0].left, ornaments[1].left)
        assertEquals(ornaments[0].top, ornaments[1].top)
        assertEquals(ornaments[0].rasterWidth, ornaments[1].rasterWidth)
        assertEquals(ornaments[0].rasterHeight, ornaments[1].rasterHeight)
        assertTrue(ornaments[0].x != ornaments[1].x)
    }

    @Test
    fun layersComeInBundleOrder() {
        val preview = assertNotNull(nameplateLoad(files(), capabilities).preview)
        val layers = preview.paint("sand", nameplateLineBlock(20, 0, 20), 0, 0)
        assertEquals(listOf(0, 1, 1, 2, 3), layers.map { it.index })
        assertEquals("over_background", layers.last().depth)
    }

    @Test
    fun bodyRasterReachesIntoNegativeSpaceForItsRings() {
        val preview = assertNotNull(nameplateLoad(files(), capabilities).preview)
        val bundle = loadedBundle()
        val block = TagBlock.line(20, 0, 20)
        val content = bundle.layer("body").content as NameplateBundle.LayerContent.Body
        val rect = content.rect(block)

        val body = preview.paint("sand", nameplateLineBlock(20, 0, 20), 0, 0).single { it.index == 2 }
        assertEquals(rect.x0, body.x)
        assertEquals(rect.y0, body.y)
        assertEquals(false, body.mirrorX)
        assertEquals(rect.width, body.width)
        assertEquals(-1, body.left)
        assertEquals(-1, body.top)
        assertEquals(rect.width + 2, body.rasterWidth)
        assertEquals(rect.height + 2, body.rasterHeight)
        assertEquals(body.rasterWidth * body.rasterHeight * 4, body.rgba.length)
    }

    @Test
    fun exposesTimingAndVariants() {
        val preview = assertNotNull(nameplateLoad(files(), capabilities).preview)
        assertEquals("sample_plate", preview.id)
        assertEquals(8, preview.fps)
        assertEquals(16, preview.loop)
        assertContentEquals(arrayOf("sand", "dusk", "moss"), preview.variants)
    }

    @Test
    fun reportsIssuesForAnInvalidBundle() {
        val load = nameplateLoad(files().filter { it.path != "sprites/knot.json" }.toTypedArray(), capabilities)
        assertNull(load.preview)
        assertTrue(load.issues.any { it.path == "sprites/knot.json" && it.message == "missing" })
    }

    @Test
    fun skipsABundleThatNeedsMissingCapabilities() {
        val load = nameplateLoad(files(), arrayOf("canvas"))
        assertNull(load.preview)
        assertEquals("needs sprites", load.skipped)
        assertEquals(0, load.issues.size)
    }

    @Test
    fun displayBlocksStretchOverEveryLine() {
        val block = nameplateDisplayBlock(intArrayOf(30, 20), 30, "center", 1, 0, 20, badge = false)
        assertEquals(30, block.width)
        assertEquals(10 + TagBlock.GLYPH_HEIGHT, block.height)
    }

    private fun files(): Array<NameplateFile> {
        val all = mapOf("manifest.json" to SampleBundle.MANIFEST, "palettes.json" to SampleBundle.PALETTES) +
            SampleBundle.CONTENT
        return all.map { (path, text) -> NameplateFile(path, text) }.toTypedArray()
    }

    private fun loadedBundle(): NameplateBundle =
        (BundleLoader(capabilities.toSet()).load(SampleBundle.files()) as BundleLoad.Loaded).bundle

    private fun rasterPixels(layer: NameplateLayer): Map<Long, Int> {
        val pixels = HashMap<Long, Int>()
        for (row in 0 until layer.rasterHeight) {
            for (column in 0 until layer.rasterWidth) {
                val argb = argbAt(layer, column, row)
                if (argb ushr 24 != 0) {
                    pixels[pixelKey(layer.left + column, layer.top + row)] = argb
                }
            }
        }
        return pixels
    }

    private fun composite(layers: Array<NameplateLayer>): Map<Long, Int> {
        val pixels = HashMap<Long, Int>()
        for (layer in layers) {
            for (row in 0 until layer.rasterHeight) {
                for (column in 0 until layer.rasterWidth) {
                    val argb = argbAt(layer, column, row)
                    if (argb ushr 24 == 0) {
                        continue
                    }
                    val imageX = layer.left + column
                    val x = if (layer.mirrorX) layer.x + layer.width - 1 - imageX else layer.x + imageX
                    val key = pixelKey(x, layer.y + layer.top + row)
                    pixels[key] = Composite.over(argb, pixels[key])
                }
            }
        }
        return pixels
    }

    private fun argbAt(layer: NameplateLayer, column: Int, row: Int): Int {
        val p = (row * layer.rasterWidth + column) * 4
        val r = layer.rgba[p].toInt() and 0xFF
        val g = layer.rgba[p + 1].toInt() and 0xFF
        val b = layer.rgba[p + 2].toInt() and 0xFF
        val a = layer.rgba[p + 3].toInt() and 0xFF
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
