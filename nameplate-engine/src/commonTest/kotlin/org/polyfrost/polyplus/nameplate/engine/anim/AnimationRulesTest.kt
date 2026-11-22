package org.polyfrost.polyplus.nameplate.engine.anim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.issue.BundleIssue
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.internal.anim.LayerMasks
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate.ContentValidator

class AnimationRulesTest {
    @Test
    fun theTimedFixtureIsValid() {
        assertIs<BundleLoad.Loaded>(TimedBundle.load())
    }

    @Test
    fun timelineHoldsAreAtLeastOne() {
        assertIssue("layers/fan.json#timeline[1].hold", content = fan(timeline("[0,4],[1,0],[2,8],[1,4]")))
        assertIssue("layers/fan.json#timeline[1].hold", content = fan(timeline("[0,4],[1,-2],[2,8],[1,6]")))
    }

    @Test
    fun timelineHoldsSumToTheLayersCycle() {
        assertIssue("layers/fan.json#timeline", content = fan(timeline("[0,4],[1,2],[2,6],[1,3]")))
        assertIssue("layers/fan.json#timeline", content = fan(timeline("[0,4],[1,2],[2,6],[1,5]")))
        assertIssue("layers/fan.json#timeline", content = fan("[]"))
        assertValid(content = fan(timeline("[0,16]")))
        assertValid(content = fan(timeline("[2,1],[0,1],[1,14]")))
    }

    @Test
    fun timelineHoldsSumToTheCycleOfASteppedLayer() {
        val layers = """[ { "id": "body" }, { "id": "fan", "step": 4 } ]"""
        val manifest = TimedBundle.manifest(animations = "{}", layers = layers)
        assertIssue("layers/fan.json#timeline", manifest, fan(timeline("[0,8]")))
        assertValid(manifest, fan(timeline("[0,1],[1,3]")))
    }

    @Test
    fun timelineFramesStayInsideTheSprite() {
        assertIssue("layers/fan.json#timeline[0].frame", content = fan(timeline("[3,4],[1,2],[2,6],[1,4]")))
        assertIssue("layers/fan.json#timeline[0].frame", content = fan(timeline("[-1,4],[1,2],[2,6],[1,4]")))
        assertValid(content = fan(timeline("[2,16]")))
    }

    @Test
    fun aTimelineLiftsTheFrameCountMustDivideTheCycleRule() {
        assertValid()
        val plainThreeFrames = TimedBundle.CONTENT + ("layers/fan.json" to """{ "kind": "sprite", "sprite": "blades" }""")
        assertIssue("sprites/blades.json#frames", content = plainThreeFrames)
    }

    @Test
    fun scheduleWindowIsAtLeastOne() {
        assertIssue("manifest.json#animations.blink.schedule.window", edit("\"window\": 8", "\"window\": 0"))
        assertIssue("manifest.json#animations.blink.schedule.window", edit("\"window\": 8", "\"window\": -4"))
    }

    @Test
    fun scheduleJitterIsNotNegative() {
        assertIssue("manifest.json#animations.blink.schedule.jitter", edit("\"jitter\": 2", "\"jitter\": -1"))
        assertValid(edit("\"jitter\": 2", "\"jitter\": 0"))
    }

    @Test
    fun scheduleChanceIsBetweenZeroAndOne() {
        assertIssue("manifest.json#animations.blink.schedule.chance", edit("\"chance\": 1.0", "\"chance\": 1.5"))
        assertIssue("manifest.json#animations.blink.schedule.chance", edit("\"chance\": 1.0", "\"chance\": -0.1"))
        assertValid(edit("\"chance\": 1.0", "\"chance\": 0.0"))
    }

    @Test
    fun scheduleWeightIsAWholeNumberFromOne() {
        assertIssue("manifest.json#animations.sway.schedule.weight", edit("\"weight\": 3", "\"weight\": 0"))
        assertIssue("manifest.json#animations.sway.schedule.weight", edit("\"weight\": 3", "\"weight\": 1001"))
        assertValid(edit("\"weight\": 3", "\"weight\": 1000"))
    }

    @Test
    fun scheduleIdsAreSimpleNames() {
        assertIssue("manifest.json#animations.blink.schedule.id", edit("\"window\": 8", "\"id\": \"Blink Now\", \"window\": 8"))
    }

    @Test
    fun theLongestSequencePlusJitterFitsTheWindow() {
        assertIssue("manifest.json#animations.blink.length", edit("\"length\": 5", "\"length\": 7"))
        assertValid(edit("\"length\": 5", "\"length\": 6"))
        assertIssue("manifest.json#animations.flick.length", edit("\"length\": 8", "\"length\": 10"))
    }

    @Test
    fun aScheduledAnimationNeedsALength() {
        assertIssue("manifest.json#animations.blink.length", edit("\"length\": 5,", ""))
        assertIssue("manifest.json#animations.blink.length", edit("\"length\": 5", "\"length\": 0"))
    }

    @Test
    fun animationsSharingAScheduleIdShareItsTiming() {
        assertIssue("manifest.json#animations.flick.schedule.window", edit("\"window\": 12, \"jitter\": 3, \"chance\": 0.75 }", "\"window\": 13, \"jitter\": 3, \"chance\": 0.75 }"))
        assertIssue("manifest.json#animations.flick.schedule.jitter", edit("\"window\": 12, \"jitter\": 3, \"chance\": 0.75 }", "\"window\": 12, \"jitter\": 2, \"chance\": 0.75 }"))
        assertIssue("manifest.json#animations.flick.schedule.chance", edit("\"window\": 12, \"jitter\": 3, \"chance\": 0.75 }", "\"window\": 12, \"jitter\": 3, \"chance\": 0.5 }"))
    }

    @Test
    fun trackLayersMustBeSpriteLayersThatExist() {
        assertIssue("manifest.json#animations.blink.tracks.ghost", edit("\"tracks\": { \"eye\"", "\"tracks\": { \"ghost\": { \"frame\": [[0, 1]] }, \"eye\""))
        assertIssue("manifest.json#animations.blink.tracks.body.frame", edit("\"tracks\": { \"eye\"", "\"tracks\": { \"body\": { \"frame\": [[0, 1]] }, \"eye\""))
    }

    @Test
    fun trackKeysAreSortedInsideTheSequenceAndStartAtZero() {
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[2]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [3, 2], [2, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[1]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [0, 2], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[2]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [2, 2], [5, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[0]", edit("[[0, 1], [2, 2], [3, 1]]", "[[1, 1], [2, 2], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[0]", edit("[[0, 1], [2, 2], [3, 1]]", "[[-1, 1], [2, 2], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame", edit("[[0, 1], [2, 2], [3, 1]]", "[]"))
        assertValid(edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [4, 2]]"))
    }

    @Test
    fun trackValuesStayInsideTheSprite() {
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[1]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [2, 3], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[1]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [2, -1], [3, 1]]"))
    }

    @Test
    fun trackKeysAreStepAndFramePairs() {
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[0]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0], [2, 2], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[1]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [2, \"two\"], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame[1]", edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [2.5, 2], [3, 1]]"))
        assertIssue("manifest.json#animations.blink.tracks.eye.frame", edit("\"frame\": [[0, 1], [2, 2], [3, 1]]", "\"frame\": 4"))
        assertValid(edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1, \"step\"], [2, 2], [3, 1]]"))
    }

    @Test
    fun layersOfOneScheduleShareAStep() {
        assertIssue(
            "manifest.json#animations.flick.tracks.eye",
            edit("\"tuft\": { \"frame\": [[0, 1], [4, 0]] }", "\"tuft\": { \"frame\": [[0, 1], [4, 0]] }, \"eye\": { \"frame\": [[0, 1]] }"),
        )
    }

    @Test
    fun aLayerTakesOneScheduleOnly() {
        assertIssue(
            "manifest.json#animations.blink.tracks.tail",
            edit("\"tracks\": { \"eye\"", "\"tracks\": { \"tail\": { \"frame\": [[0, 1]] }, \"eye\""),
        )
    }

    @Test
    fun animationsTheEngineDoesNotBuildYetAreReported() {
        val pulse = """"pulse": { "length": 4, "tracks": { "eye": { "frame": [[0, 1]] } } },"""
        assertUnsupported("manifest.json#animations.pulse", edit("\"sway\"", pulse + "\"sway\""))
        assertUnsupported("manifest.json#animations.blink.trigger", edit("\"schedule\": { \"window\": 8,", "\"trigger\": \"equip\", \"schedule\": { \"window\": 8,"))
    }

    @Test
    fun unknownChannelsAreReported() {
        for (channel in listOf("wobble", "spin_speed")) {
            assertUnsupported(
                "manifest.json#animations.blink.tracks.eye.$channel",
                edit("\"eye\": { \"frame\"", "\"eye\": { \"$channel\": [[0, 1]], \"frame\""),
            )
        }
    }

    @Test
    fun otherInterpolationThanStepIsReported() {
        assertIssue(
            "manifest.json#animations.blink.tracks.eye.frame[2]",
            edit("[[0, 1], [2, 2], [3, 1]]", "[[0, 1], [2, 2], [3, 1, \"linear\"]]"),
        )
    }

    @Test
    fun phaseAndGroupsLoad() {
        assertValid( edit("\"fps\": 8,", "\"fps\": 8, \"phase\": \"per_player\","))
        assertValid(edit("\"fps\": 8,", "\"fps\": 8, \"groups\": [ { \"id\": \"head\", \"layers\": [\"eye\"] } ],"))
    }

    @Test
    fun cachedStatesAreLimitedPerLayer() {
        val bundle = TimedBundle.bundle()
        assertTrue(ContentValidator.cachedStateIssues(bundle).isEmpty())
        val tight = statesOver(bundle, 3)
        assertEquals(listOf("layers/lamp.json", "layers/tail.json"), tight)
        assertTrue(statesOver(bundle, 4).isEmpty())
    }

    private fun edit(from: String, to: String): String {
        assertTrue(TimedBundle.MANIFEST.contains(from), "fixture has no '$from'")
        return TimedBundle.MANIFEST.replace(from, to)
    }

    private fun timeline(steps: String): String =
        "[" + steps.split("],[").joinToString(",") { pair ->
            val (frame, hold) = pair.trim('[', ']').split(",")
            """{ "frame": $frame, "hold": $hold }"""
        } + "]"

    private fun fan(timeline: String): Map<String, String> = TimedBundle.CONTENT +
        ("layers/fan.json" to """{ "kind": "sprite", "sprite": "blades", "timeline": $timeline }""")

    private fun issues(manifest: String, content: Map<String, String>): List<BundleIssue> {
        val result = TimedBundle.load(manifest, content)
        return if (result is BundleLoad.Invalid) result.issues else emptyList()
    }

    private fun assertIssue(path: String, manifest: String = TimedBundle.MANIFEST, content: Map<String, String> = TimedBundle.CONTENT) {
        val found = issues(manifest, content)
        assertTrue(found.any { it.path == path }, "expected $path in $found")
    }

    private fun assertUnsupported(path: String, manifest: String) {
        val found = issues(manifest, TimedBundle.CONTENT)
        assertTrue(found.any { it.path == path && it.message == "not supported yet" }, "expected $path not supported in $found")
    }

    private fun assertValid(manifest: String = TimedBundle.MANIFEST, content: Map<String, String> = TimedBundle.CONTENT) {
        val result = TimedBundle.load(manifest, content)
        assertIs<BundleLoad.Loaded>(result, result.toString())
    }

    private fun statesOver(bundle: NameplateBundle, limit: Int): List<String> =
        bundle.layers.filter { LayerMasks.cachedStates(bundle, it) > limit }.map { "layers/${it.id}.json" }
}
