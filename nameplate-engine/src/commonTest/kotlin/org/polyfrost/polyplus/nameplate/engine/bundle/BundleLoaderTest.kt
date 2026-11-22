package org.polyfrost.polyplus.nameplate.engine.bundle

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.anim.TimedBundle
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.layout.TagBlock
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.api.text.BadgeStyling
import org.polyfrost.polyplus.nameplate.engine.api.text.TextPaint
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fill
import org.polyfrost.polyplus.nameplate.engine.internal.layout.Anchor
import org.polyfrost.polyplus.nameplate.engine.internal.layout.BlockAnchor
import org.polyfrost.polyplus.nameplate.engine.internal.track.Schedule

class BundleLoaderTest {
    private val loader = BundleLoader(setOf("canvas", "sprites"))

    @Test
    fun sampleLoadsWithThreeColourways() {
        val bundle = loaded(SampleBundle.files())
        assertEquals("sample_plate", bundle.id)
        assertEquals(8, bundle.fps)
        assertEquals(16, bundle.loop)
        assertTrue(bundle.ownsBackground)
        assertEquals(3.0, bundle.params["radius"])
        assertEquals(listOf("sand", "dusk", "moss"), bundle.variants.keys.toList())
        assertEquals(listOf("trail", "ornaments", "body", "ribbon"), bundle.layers.map { it.id })
        assertEquals(NameplateBundle.Depth.BACKGROUND, bundle.layers[0].depth)
        assertEquals(2, bundle.layers[0].step)
        assertEquals(NameplateBundle.Depth.OVER_BACKGROUND, bundle.layers[3].depth)
        assertEquals(NameplateBundle.TextMode.VANILLA, bundle.variant("sand").textMode)

        val moss = bundle.variant("moss")
        assertEquals(0xFFDCEBD8.toInt(), moss.resolved.color(bundle.palette.ref("fill")))
        assertEquals(0xFFC25F33.toInt(), moss.resolved.color(bundle.palette.ref("ribbon", 2)))
        assertEquals(TextPaint.of(0x2C4A27), moss.text.color)
        assertEquals(TextPaint.AUTO, moss.text.shadow)
    }

    @Test
    fun layerContentLoads() {
        val bundle = loaded(SampleBundle.files())
        val body = assertIs<NameplateBundle.LayerContent.Body>(bundle.layer("body").content)
        assertEquals(listOf(4, 2, 4, 2), listOf(body.padLeft, body.padTop, body.padRight, body.padBottom))
        assertEquals(30, body.minWidth)
        assertEquals(3, body.radius)
        assertEquals(bundle.palette.ref("fill"), assertIs<Fill.Solid>(body.fill).ref)
        assertEquals(bundle.palette.ref("border"), assertIs<Fill.Solid>(body.rings.single()).ref)

        val ornaments = assertIs<NameplateBundle.LayerContent.Sprite>(bundle.layer("ornaments").content)
        assertEquals("body", ornaments.of)
        assertEquals(Anchor.TOP_LEFT, ornaments.anchor)
        assertEquals(Anchor.BOTTOM_LEFT, ornaments.pivot)
        assertTrue(ornaments.mirror)
        assertEquals(4, ornaments.frames.single().width)
        assertEquals(bundle.palette.ref("accent"), ornaments.frames.single().color('a'))

        val ribbon = assertIs<NameplateBundle.LayerContent.Sprite>(bundle.layer("ribbon").content)
        assertEquals(Anchor.TOP_LEFT, ribbon.pivot)
        assertEquals(2, assertIs<NameplateBundle.LayerContent.Sprite>(bundle.layer("trail").content).frames.size)
    }

    @Test
    fun timelinesExpandToOneFramePerStep() {
        val timed = TimedBundle.bundle()
        val fan = assertIs<NameplateBundle.LayerContent.Sprite>(timed.layer("fan").content)
        assertContentEquals(intArrayOf(0, 0, 0, 0, 1, 1, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1), IntArray(16) { fan.timelineFrame(it) })
        assertTrue(fan.hasTimeline)
        assertTrue(!assertIs<NameplateBundle.LayerContent.Sprite>(timed.layer("lamp").content).hasTimeline)
    }

    @Test
    fun schedulesGroupAnimationsByTheirSharedId() {
        val timed = TimedBundle.bundle()
        assertEquals(listOf("tail_moves", "blink"), timed.schedules.map { it.id })
        val moves = timed.schedules[0]
        assertEquals(listOf(12, 3), listOf(moves.window, moves.jitter))
        assertEquals(12582912, moves.chance)
        assertEquals(listOf("sway", "flick"), moves.events.map { it.name })
        assertEquals(listOf(3, 1), moves.events.map { it.weight })
        assertEquals(listOf(6, 8), moves.events.map { it.length })
        assertEquals(4, moves.totalWeight)
        assertEquals(moves.id, timed.scheduleOf("tail")?.id)
        assertEquals(moves.id, timed.scheduleOf("tuft")?.id)
        assertEquals("blink", timed.scheduleOf("eye")?.id)
        assertEquals(null, timed.scheduleOf("lamp"))
    }

    @Test
    fun chanceBecomesAnIntegerThreshold() {
        assertEquals(Schedule.CHANCE_ONE, TimedBundle.bundle().schedules[1].chance)
        val never = TimedBundle.bundle(TimedBundle.pick("0.0"))
        assertEquals(0, never.schedules.single().chance)
    }

    @Test
    fun colorwaysKeepSlotPositions() {
        val bundle = loaded(SampleBundle.files())
        val ref = bundle.palette.ref("text")
        assertEquals(0xFF4A3410.toInt(), bundle.variant("sand").resolved.color(ref))
        assertEquals(0xFF23305C.toInt(), bundle.variant("dusk").resolved.color(ref))
    }

    @Test
    fun unknownOrMissingVariantKeysFallBackToTheFirst() {
        val bundle = loaded(SampleBundle.files())
        assertSame(bundle.variant("sand"), bundle.variant("noir"))
        assertSame(bundle.variant("sand"), bundle.variant(null))
    }

    @Test
    fun placedBadgeCentresOnItsAnchorPlusOffset() {
        val badge = loaded(SampleBundle.files()).variant("sand").badge
        assertEquals(BadgeStyling.Placement.Kind.PLACED, badge.placement.kind)
        assertEquals(BadgeStyling.Decoration.Kind.AUTO, badge.decoration.kind)
        val anchor = badge.placement.anchor!!
        assertEquals(40 + 5 - 4, anchor.x(TagBlock.line(40, 0, 40)))
        assertEquals(0 - 2 - 4, anchor.y(TagBlock.line(40, 0, 40)))
    }

    @Test
    fun badgeAnchorsCoverTheBlockEdges() {
        assertEquals(-4, BlockAnchor(Anchor.TOP_LEFT, 0, 0).x(TWO_LINES))
        assertEquals(16, BlockAnchor(Anchor.TOP, 0, 0).x(TWO_LINES))
        assertEquals(5, BlockAnchor(Anchor.LEFT, 0, 0).y(TWO_LINES))
        assertEquals(14, BlockAnchor(Anchor.BOTTOM_RIGHT, 0, 0).y(TWO_LINES))
    }

    @Test
    fun interpolatedSlotsExpandLikeVanillaSrgbLerp() {
        val palettes = """
            { "slots": [ { "id": "fill", "ramp": ["#604020", "#201008"], "interpolate": 12 } ] }
        """.trimIndent()
        val bundle = loaded(SampleBundle.files(manifest = minimalManifest(), palettes = palettes))
        val fill = bundle.palette.slots[0]
        assertEquals(12, fill.size)
        assertEquals(0xFF604020.toInt(), fill.tone(0))
        assertEquals(0xFF201008.toInt(), fill.tone(11))
        assertEquals(0xFF5A3B1D.toInt(), fill.tone(1))
    }

    @Test
    fun colourwayOverridesAreInterpolatedToo() {
        val palettes = """
            {
              "slots": [ { "id": "fill", "ramp": ["#000000", "#FFFFFF"], "interpolate": 3 } ],
              "colorways": { "dark": {}, "red": { "fill": ["#000000", "#FF0000"] } }
            }
        """.trimIndent()
        val manifest = minimalManifest(variants = """{ "dark": { "palette": "dark" }, "red": { "palette": "red" } }""")
        val bundle = loaded(SampleBundle.files(manifest = manifest, palettes = palettes))
        val ref = bundle.palette.ref("fill", 1)
        assertEquals(0xFF7F7F7F.toInt(), bundle.variant("dark").resolved.color(ref))
        assertEquals(0xFF7F0000.toInt(), bundle.variant("red").resolved.color(ref))
    }

    @Test
    fun noVariantsMeansOneDefaultFromTheFirstColourway() {
        val palettes = """
            { "slots": [ { "id": "fill", "ramp": ["#000000"] } ], "colorways": { "night": {}, "day": { "fill": ["#FFFFFF"] } } }
        """.trimIndent()
        val bundle = loaded(SampleBundle.files(manifest = minimalManifest(), palettes = palettes))
        assertEquals(listOf("default"), bundle.variants.keys.toList())
        assertEquals(0xFF000000.toInt(), bundle.variant(null).resolved.color(bundle.palette.ref("fill")))
    }

    @Test
    fun variantsHideAndSwapLayers() {
        val manifest = minimalManifest(
            layers = """[{ "id": "body" }, { "id": "dot" }, { "id": "dot_alt" }]""",
            variants = """{ "plain": { "palette": "default", "hide": ["dot_alt"] }, "alt": { "palette": "default", "hide": ["dot_alt"], "swap": { "dot": "dot_alt" } } }""",
        )
        val bundle = loaded(SampleBundle.files(manifest = manifest, palettes = SINGLE_SLOT, content = DOT_CONTENT))
        assertEquals(setOf("dot_alt"), bundle.variant("plain").hidden)
        assertEquals(emptyMap(), bundle.variant("plain").swaps)
        assertEquals(mapOf("dot" to "dot_alt"), bundle.variant("alt").swaps)
    }

    @Test
    fun newerFormatsAreSkipped() {
        val result = loader.load(SampleBundle.files(manifest = """{ "format": 2, "anything": [1, 2] }"""))
        assertIs<BundleLoad.Skipped>(result)
    }

    @Test
    fun missingCapabilitiesSkipTheBundle() {
        val result = BundleLoader(setOf("canvas")).load(SampleBundle.files())
        val skipped = assertIs<BundleLoad.Skipped>(result)
        assertTrue("sprites" in skipped.reason)
    }

    @Test
    fun brokenInputIsInvalidNotThrown() {
        assertInvalid(BundleSource.of(emptyMap()), "manifest.json")
        assertInvalid(SampleBundle.files(manifest = "{ not json"), "manifest.json")
        assertInvalid(SampleBundle.files(manifest = """{ "format": "one" }"""), "manifest.json#format")
        assertInvalid(SampleBundle.files(manifest = """{ "format": 1, "id": "x" }"""), "manifest.json")
        assertInvalid(BundleSource.of(mapOf("manifest.json" to SampleBundle.MANIFEST)), "palettes.json")
        assertInvalid(SampleBundle.files(manifest = "[".repeat(40) + "]".repeat(40)), "manifest.json")
    }

    @Test
    fun missingOrBrokenContentIsInvalid() {
        assertInvalid(SampleBundle.files(content = SampleBundle.CONTENT - "layers/ribbon.json"), "layers/ribbon.json")
        assertInvalid(SampleBundle.files(content = SampleBundle.CONTENT - "sprites/knot.json"), "sprites/knot.json")
        val unknownKind = SampleBundle.CONTENT + ("layers/ribbon.json" to """{ "kind": "geometry" }""")
        assertInvalid(SampleBundle.files(content = unknownKind), "layers/ribbon.json")
    }

    @Test
    fun validationIssuesComeBackTogether() {
        val manifest = SampleBundle.MANIFEST.replace("\"fps\": 8", "\"fps\": 30").replace("\"palette\": \"moss\"", "\"palette\": \"teal\"")
        val invalid = assertIs<BundleLoad.Invalid>(loader.load(SampleBundle.files(manifest = manifest)))
        assertEquals(setOf("manifest.json#fps", "manifest.json#variants.moss.palette"), invalid.issues.map { it.path }.toSet())
    }

    private fun loaded(source: BundleSource): NameplateBundle {
        val result = loader.load(source)
        return assertIs<BundleLoad.Loaded>(result, result.toString()).bundle
    }

    private fun assertInvalid(source: BundleSource, path: String) {
        val invalid = assertIs<BundleLoad.Invalid>(loader.load(source))
        assertEquals(path, invalid.issues.single().path, invalid.issues.toString())
    }

    private companion object {
        val TWO_LINES: TagBlock = TagBlock.display(intArrayOf(40, 40), 2, 40, TagBlock.Align.CENTER, 1, 0, 40, false)
        val SINGLE_SLOT = """{ "slots": [ { "id": "fill", "ramp": ["#FFFFFF"] } ] }"""
        val DOT_CONTENT = mapOf(
            "layers/body.json" to """{ "kind": "body", "fill": { "color": "fill" } }""",
            "layers/dot.json" to """{ "kind": "sprite", "sprite": "dot" }""",
            "layers/dot_alt.json" to """{ "kind": "sprite", "sprite": "dot", "anchor": "bottom_right" }""",
            "sprites/dot.json" to """{ "colors": { "f": "fill" }, "rows": ["f"] }""",
        )

        fun minimalManifest(layers: String = "[]", variants: String = "{}"): String = """
            { "format": 1, "id": "test", "fps": 8, "loop": 16, "layers": $layers, "variants": $variants }
        """.trimIndent()
    }
}
