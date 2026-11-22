package org.polyfrost.polyplus.nameplate.engine.flag

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.canvas.PixelGrid
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.paint.LayerPainter
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.canvas.Composite
import org.polyfrost.polyplus.nameplate.engine.canvas.Rects
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.OverlayKind
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas
import org.polyfrost.polyplus.nameplate.engine.support.CountingCache
import org.polyfrost.polyplus.nameplate.engine.support.pixelKey

class FlagBundleTest {
    private val loader = BundleLoader(setOf("canvas", "flags"))
    private val painter = LayerPainter(PixelGrid())
    private val block = TagBlock.line(30, 0, 30)

    @Test
    fun threeStructurallyDifferentFlagsLoad() {
        val bundle = loaded(files())
        assertEquals(listOf("three", "five", "ring"), bundle.flags.map { it.id })
        assertEquals("three", bundle.variant("three").flag?.id)
        assertEquals(OverlayKind.CHEVRON, bundle.variant("five").flag?.overlay?.kind)
        assertEquals(OverlayKind.CIRCLE, bundle.variant("ring").flag?.overlay?.kind)
        assertEquals("three", bundle.variant(null).flag?.id)
    }

    @Test
    fun overridesWinForTheirRowCount() {
        val five = loaded(files()).flags[1]
        assertContentEquals(intArrayOf(4, 5, 4, 5, 4), five.rows(22))
        assertContentEquals(intArrayOf(2, 3, 2, 3, 2), five.rows(12))
    }

    @Test
    fun bandsStackOverTheBody() {
        val pixels = pixels("three")
        assertEquals(A, pixels[key(10, -2)])
        assertEquals(B, pixels[key(10, 2)])
        assertEquals(C, pixels[key(10, 6)])
        assertEquals(E, pixels[key(10, -3)])
    }

    @Test
    fun flagBodiesPaintRelativeToTheirRect() {
        val bundle = loaded(files())
        val variant = bundle.variant("three")
        val body = painter.paint(bundle, variant, block, 0, 0).single()
        assertEquals(0, body.index)
        assertEquals(-3, body.x)
        assertEquals(-2, body.y)
        assertEquals(36, body.width)
        assertEquals(false, body.mirrorX)
        val local = Rects.pixels(body.image.resolve(variant.resolved))
        assertEquals(A, local[key(5, 1)])
        assertEquals(B, local[key(5, 5)])
        assertEquals(C, local[key(5, 9)])
        assertEquals(E, local[key(5, -1)])
    }

    @Test
    fun chevronSitsOnTheLeftEdge() {
        val pixels = pixels("five")
        assertEquals(D, pixels[key(-3, -2)])
        assertEquals(E, pixels[key(-1, -2)])
        assertEquals(A, pixels[key(1, -2)])
        assertEquals(D, pixels[key(3, 3)])
        assertEquals(E, pixels[key(4, 3)])
        assertEquals(C, pixels[key(6, 3)])
    }

    @Test
    fun circleRingsTheCentre() {
        val pixels = pixels("ring")
        assertEquals(C, pixels[key(19, 3)])
        assertEquals(A, pixels[key(15, 3)])
        assertEquals(A, pixels[key(21, 3)])
    }

    @Test
    fun flagRules() {
        assertIssue(files(flags = null), "flags.json")
        assertIssue(files(manifest = MANIFEST.replace("\"flag\": \"ring\"", "\"flag\": \"square\"")), "manifest.json#variants.ring.flag")
        assertIssue(files(flags = FLAGS.replace("\"rowsOverride\": { \"22\": [4, 5, 4, 5, 4] }", "\"rowsOverride\": { \"22\": [4, 5, 4, 5] }")), "flags.json#flags.five.rowsOverride.22")
        assertIssue(files(flags = FLAGS.replace("\"kind\": \"circle\", \"colors\": [\"c\"]", "\"kind\": \"triangle\", \"colors\": [\"c\", \"d\"]")), "flags.json#flags.ring.overlay.colors")
        assertIssue(files(flags = FLAGS.replace("\"kind\": \"circle\"", "\"kind\": \"star\"")), "flags.json#flags.ring.overlay.kind")
        assertIssue(files(flags = FLAGS.replace("{ \"color\": \"c\", \"weight\": 1 }", "{ \"color\": \"c\", \"weight\": 0 }")), "flags.json#flags.five.bands[2]")
        val lopsided = FLAGS.replace(
            "\"id\": \"ring\", \"bands\": [ { \"color\": \"a\" }, { \"color\": \"b\" } ]",
            "\"id\": \"ring\", \"symmetric\": true, \"bands\": [ { \"color\": \"a\", \"weight\": 2 }, { \"color\": \"b\" } ]",
        )
        assertIssue(files(flags = lopsided), "flags.json#flags.ring.symmetric")
        val thirteen = (0 until 13).joinToString { """{ "color": "a" }""" }
        assertIssue(files(flags = FLAGS.replace("\"id\": \"three\", \"bands\": [", "\"id\": \"three\", \"bands\": [$thirteen, ")), "flags.json#flags.three.bands")
        val tall = (0 until 11).joinToString { """{ "color": "b" }""" }
        assertIssue(files(flags = FLAGS.replace("\"id\": \"three\", \"bands\": [", "\"id\": \"three\", \"bands\": [$tall, ")), "layers/body.json#fill")
    }

    @Test
    fun eachFlagGetsItsOwnBodyEntry() {
        val bundle = loaded(files())
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        for (key in listOf("three", "five", "ring")) {
            cached.paint(bundle, bundle.variant(key), block, 0, 0)
        }
        assertEquals(3, cache.size)
        for (key in listOf("ring", "three", "five")) {
            cached.paint(bundle, bundle.variant(key), block, 0, 0)
        }
        assertEquals(3, cache.size)
        assertEquals(3, cache.misses)
    }

    @Test
    fun eachFlagGetsItsOwnEntryWhenOnlyARingUsesIt() {
        val bundle = loaded(files(body = FLAG_RING_BODY))
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        for (key in listOf("three", "five", "ring")) {
            cached.paint(bundle, bundle.variant(key), block, 0, 0)
        }
        assertEquals(3, cache.size)
    }

    @Test
    fun aBodyWithoutAFlagFillSharesOneEntryAcrossFlags() {
        val bundle = loaded(files(body = PLAIN_BODY))
        val cache = newCache()
        val cached = LayerPainter(PixelGrid(), cache.cache)
        for (key in listOf("three", "five", "ring")) {
            cached.paint(bundle, bundle.variant(key), block, 0, 0)
        }
        assertEquals(1, cache.size)
        assertEquals(1, cache.misses)
    }

    @Test
    fun flagBodiesPaintTheSameFromTheCacheAsFresh() {
        val bundle = loaded(files())
        val cached = LayerPainter(PixelGrid(), newCache().cache)
        repeat(2) {
            for (key in listOf("three", "five", "ring")) {
                val variant = bundle.variant(key)
                assertEquals(
                    Composite.pixels(LayerPainter(PixelGrid()).paint(bundle, variant, block, 0, 0), variant.resolved),
                    Composite.pixels(cached.paint(bundle, variant, block, 0, 0), variant.resolved),
                )
            }
        }
    }

    private fun newCache() = CountingCache()

    private fun pixels(variantKey: String): Map<Long, Int> {
        val bundle = loaded(files())
        val variant = bundle.variant(variantKey)
        return Composite.pixels(painter.paint(bundle, variant, block, 0, 0), variant.resolved)
    }

    private fun loaded(source: BundleSource): NameplateBundle {
        val result = loader.load(source)
        return assertIs<BundleLoad.Loaded>(result, result.toString()).bundle
    }

    private fun assertIssue(source: BundleSource, path: String) {
        val invalid = assertIs<BundleLoad.Invalid>(loader.load(source))
        assertTrue(invalid.issues.any { it.path == path }, "expected $path in ${invalid.issues}")
    }

    private fun key(x: Int, y: Int): Long = pixelKey(x, y)

    private fun files(manifest: String = MANIFEST, flags: String? = FLAGS, body: String = BODY): BundleSource {
        val files = mutableMapOf(
            "manifest.json" to manifest,
            "palettes.json" to PALETTES,
            "layers/body.json" to body,
        )
        if (flags != null) {
            files["flags.json"] = flags
        }
        return BundleSource.of(files)
    }

    private companion object {
        val A = 0xFFAA2222.toInt()
        val B = 0xFF22AA22.toInt()
        val C = 0xFF2222AA.toInt()
        val D = 0xFFEEEE22.toInt()
        val E = 0xFF222222.toInt()

        val MANIFEST = """
            { "format": 1, "id": "banners", "requires": ["canvas", "flags"], "fps": 1, "loop": 1, "ownsBackground": true,
              "layers": [ { "id": "body" } ],
              "variants": {
                "three": { "palette": "default", "flag": "three" },
                "five": { "palette": "default", "flag": "five" },
                "ring": { "palette": "default", "flag": "ring" }
              } }
        """.trimIndent()

        val PALETTES = """
            { "slots": [
              { "id": "a", "ramp": ["#AA2222"] }, { "id": "b", "ramp": ["#22AA22"] }, { "id": "c", "ramp": ["#2222AA"] },
              { "id": "d", "ramp": ["#EEEE22"] }, { "id": "e", "ramp": ["#222222"] }
            ] }
        """.trimIndent()

        val BODY = """
            { "kind": "body", "padding": [3, 2, 3, 2], "fill": { "flag": true }, "rings": [ { "color": "e" } ] }
        """.trimIndent()

        val PLAIN_BODY = """
            { "kind": "body", "padding": [3, 2, 3, 2], "fill": { "color": "a" }, "rings": [ { "color": "e" } ] }
        """.trimIndent()

        val FLAG_RING_BODY = """
            { "kind": "body", "padding": [3, 2, 3, 2], "fill": { "color": "a" }, "rings": [ { "flag": true } ] }
        """.trimIndent()

        val FLAGS = """
            { "flags": [
              { "id": "three", "bands": [ { "color": "a" }, { "color": "b" }, { "color": "c" } ] },
              { "id": "five", "bands": [ { "color": "a", "weight": 1 }, { "color": "b", "weight": 2 }, { "color": "c", "weight": 1 }, { "color": "b", "weight": 2 }, { "color": "a", "weight": 1 } ],
                "overlay": { "kind": "chevron", "colors": ["d", "e"], "width": 2 },
                "rowsOverride": { "22": [4, 5, 4, 5, 4] } },
              { "id": "ring", "bands": [ { "color": "a" }, { "color": "b" } ],
                "overlay": { "kind": "circle", "colors": ["c"], "width": 1 } }
            ] }
        """.trimIndent()
    }
}
