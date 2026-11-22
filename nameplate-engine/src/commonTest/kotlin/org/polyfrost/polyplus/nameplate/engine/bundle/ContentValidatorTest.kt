package org.polyfrost.polyplus.nameplate.engine.bundle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.internal.anim.LayerMasks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.AnimationJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BodyLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.FillJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteFileJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SpriteLayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TimelineStepJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate.ContentValidator
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas

class ContentValidatorTest {
    @Test
    fun theSampleContentPasses() {
        assertTrue(ContentValidator.validate(MANIFEST, LAYERS, SPRITES, TONES).isEmpty())
    }

    @Test
    fun bodyRules() {
        assertIssue("layers/body.json#padding", layers = withBody(BodyLayerJson(padding = listOf(1, 2, 3))))
        assertIssue("layers/body.json#padding", layers = withBody(BodyLayerJson(padding = listOf(0, 0, 0, 33))))
        assertIssue("layers/body.json#minWidth", layers = withBody(BodyLayerJson(minWidth = 300)))
        assertIssue("layers/body.json#radius", layers = withBody(BodyLayerJson(radius = 17)))
        assertIssue("layers/body.json#rings", layers = withBody(BodyLayerJson(rings = List(5) { FillJson(color = "fill") })))
        assertIssue("layers/body.json#fill", layers = withBody(BodyLayerJson(fill = FillJson())))
        assertIssue("layers/body.json#fill", layers = withBody(BodyLayerJson(fill = FillJson(color = "fill", gradient = "fill"))))
        assertIssue("layers/body.json#fill", layers = withBody(BodyLayerJson(fill = FillJson(color = "#FFFFFF"))))
        assertIssue("layers/body.json#fill", layers = withBody(BodyLayerJson(fill = FillJson(gradient = "sky"))))
        assertIssue("layers/body.json#fill", layers = withBody(BodyLayerJson(fill = FillJson(blend = "sky"))))
        assertIssue("layers/body.json#fill", layers = withBody(BodyLayerJson(fill = FillJson(blend = "ramp", gradient = "ramp"))))
        assertTrue(ContentValidator.validate(MANIFEST, withBody(BodyLayerJson(fill = FillJson(blend = "ramp"))), SPRITES, TONES).isEmpty())
        assertIssue("layers/body.json#rings[0]", layers = withBody(BodyLayerJson(rings = listOf(FillJson(color = "fill[3]")))))
        assertTrue(ContentValidator.validate(MANIFEST, withBody(BodyLayerJson(fill = FillJson(gradient = "ramp"))), SPRITES, TONES).isEmpty())
    }

    @Test
    fun spriteLayerRules() {
        assertIssue("layers/dot.json#sprite", layers = withDot(SpriteLayerJson("missing")))
        assertIssue("layers/dot.json#of", layers = withDot(SpriteLayerJson("dot", of = "dot")))
        assertIssue("layers/dot.json#of", layers = withDot(SpriteLayerJson("dot", of = "nothing")))
        assertIssue("layers/dot.json#anchor", layers = withDot(SpriteLayerJson("dot", anchor = "nowhere")))
        assertIssue("layers/dot.json#pivot", layers = withDot(SpriteLayerJson("dot", pivot = "centre")))
        assertIssue("layers/dot.json#offset", layers = withDot(SpriteLayerJson("dot", offset = listOf(0, 65))))
    }

    @Test
    fun spriteRules() {
        assertIssue("sprites/dot.json", sprites = withSprite(SpriteFileJson(mapOf("f" to "fill"))))
        assertIssue("sprites/dot.json", sprites = withSprite(SpriteFileJson(mapOf("f" to "fill"), rows = listOf("f"), frames = listOf(listOf("f")))))
        assertIssue("sprites/dot.json#colors.ff", sprites = withSprite(SpriteFileJson(mapOf("ff" to "fill"), rows = listOf("."))))
        assertIssue("sprites/dot.json#colors..", sprites = withSprite(SpriteFileJson(mapOf("." to "fill"), rows = listOf("."))))
        assertIssue("sprites/dot.json#colors.f", sprites = withSprite(SpriteFileJson(mapOf("f" to "#FFFFFF"), rows = listOf("f"))))
        assertIssue("sprites/dot.json#frames[0]", sprites = withSprite(SpriteFileJson(mapOf("f" to "fill"), rows = listOf("fx"))))
        assertIssue("sprites/dot.json#frames[1]", sprites = withSprite(SpriteFileJson(mapOf("f" to "fill"), frames = listOf(listOf("f"), listOf("ff")))))
        assertIssue("sprites/dot.json", sprites = withSprite(SpriteFileJson(mapOf("f" to "fill"), rows = listOf("f".repeat(65)))))
        assertIssue("sprites/dot.json#frames", sprites = withSprite(SpriteFileJson(mapOf("f" to "fill"), frames = List(3) { listOf("f") })))
        assertTrue(ContentValidator.validate(MANIFEST, LAYERS, withSprite(SpriteFileJson(mapOf("f" to "fill"), frames = List(4) { listOf("f") })), TONES).isEmpty())
    }

    @Test
    fun timelineRules() {
        val dot = SpriteLayerJson("dot", of = "body")
        fun timeline(vararg held: Pair<Int, Int>) = mapOf<String, LayerFileJson>(
            "body" to LAYERS.getValue("body"),
            "dot" to dot.copy(timeline = held.map { TimelineStepJson(it.first, it.second) }),
        )
        assertIssue("layers/dot.json#timeline", layers = timeline(0 to 3))
        assertIssue("layers/dot.json#timeline", layers = timeline())
        assertIssue("layers/dot.json#timeline[0].hold", layers = timeline(0 to 0, 0 to 4))
        assertIssue("layers/dot.json#timeline[1].frame", layers = timeline(0 to 2, 1 to 2))
        assertTrue(ContentValidator.validate(MANIFEST, timeline(0 to 1, 0 to 3), SPRITES, TONES).isEmpty())
    }

    @Test
    fun aTimelineLiftsTheDivisibilityRuleForItsSprite() {
        val threeFrames = withSprite(SpriteFileJson(mapOf("f" to "fill"), frames = List(3) { listOf("f") }))
        val timeline = mapOf<String, LayerFileJson>(
            "body" to LAYERS.getValue("body"),
            "dot" to SpriteLayerJson("dot", of = "body", timeline = listOf(TimelineStepJson(2, 4))),
        )
        assertTrue(ContentValidator.validate(MANIFEST, timeline, threeFrames, TONES).isEmpty())
    }

    @Test
    fun unsupportedAnimationFieldsAreReported() {
        val manifest = MANIFEST.copy(animations = mapOf("pulse" to AnimationJson(length = 4)))
        val issues = ContentValidator.validate(manifest, LAYERS, SPRITES, TONES)
        assertEquals(listOf(BundleIssue("manifest.json#animations.pulse", "not supported yet")), issues)
    }

    @Test
    fun cachedStatesAreLimitedPerLayer() {
        val bundle = BundleLoader(setOf("canvas", "sprites")).load(SampleBundle.files()).let { (it as BundleLoad.Loaded).bundle }
        assertTrue(ContentValidator.cachedStateIssues(bundle).isEmpty())
        assertEquals(listOf("layers/trail.json"), statesOver(bundle, 1))
    }

    private fun assertIssue(
        path: String,
        layers: Map<String, LayerFileJson> = LAYERS,
        sprites: Map<String, SpriteFileJson> = SPRITES,
    ) {
        val issues = ContentValidator.validate(MANIFEST, layers, sprites, TONES)
        assertTrue(issues.any { it.path == path }, "expected $path in $issues")
    }

    private fun withBody(body: BodyLayerJson): Map<String, LayerFileJson> = LAYERS + ("body" to body)

    private fun withDot(dot: SpriteLayerJson): Map<String, LayerFileJson> = LAYERS + ("dot" to dot)

    private fun withSprite(sprite: SpriteFileJson): Map<String, SpriteFileJson> = SPRITES + ("dot" to sprite)

    private companion object {
        val MANIFEST = ManifestJson(
            format = 1,
            id = "test",
            fps = 8,
            loop = 16,
            layers = listOf(LayerJson("body"), LayerJson("dot", step = 4)),
        )
        val LAYERS: Map<String, LayerFileJson> = mapOf(
            "body" to BodyLayerJson(padding = listOf(2, 1, 2, 1), radius = 2, fill = FillJson(color = "fill"), rings = listOf(FillJson(color = "ramp[2]"))),
            "dot" to SpriteLayerJson("dot", of = "body", anchor = "top", pivot = "bottom", offset = listOf(0, 1)),
        )
        val SPRITES = mapOf("dot" to SpriteFileJson(mapOf("f" to "fill", "r" to "ramp[1]"), rows = listOf("fr", "rf")))
        val TONES = mapOf("fill" to 1, "ramp" to 3)
    }

    private fun statesOver(bundle: NameplateBundle, limit: Int): List<String> =
        bundle.layers.filter { LayerMasks.cachedStates(bundle, it) > limit }.map { "layers/${it.id}.json" }
}
