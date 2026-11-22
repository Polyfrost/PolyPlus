package org.polyfrost.polyplus.nameplate.engine.anim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.internal.anim.frameAt
import org.polyfrost.polyplus.nameplate.engine.internal.anim.states

class SpriteFramesTest {
    private val timed = TimedBundle.bundle()
    private val plain = TimedBundle.bundle(TimedBundle.manifest(animations = "{}"))

    @Test
    fun withoutTimelineOrScheduleTheFrameIsTheStepFormula() {
        for (id in listOf("lamp", "tail", "tuft")) {
            val layer = plain.layer(id)
            val frames = (layer.content as NameplateBundle.LayerContent.Sprite).frames.size
            for (frame in -40..100) {
                for (seed in listOf(0, 1, -5)) {
                    val expected = (frame.mod(plain.loop) / layer.step) % frames
                    assertEquals(expected, frameAt(plain, layer, frame, seed), "$id at $frame")
                }
            }
        }
    }

    @Test
    fun bodiesAlwaysShowTheirOneState() {
        assertEquals(0, frameAt(timed, timed.layer("body"), 37, 9))
        assertEquals(setOf(0), states(timed, timed.layer("body")))
    }

    @Test
    fun aTimelinePlaysItsHoldsInOrderAndLoops() {
        val fan = timed.layer("fan")
        val shown = (0 until 32).joinToString("") { frameAt(timed, fan, it, 3).toString() }
        assertEquals("00001122222211110000112222221111", shown)
        assertEquals(1, frameAt(timed, fan, -1, 3))
        assertEquals(0, frameAt(timed, fan, -16, 3))
    }

    @Test
    fun aTimelineIgnoresTheSeed() {
        val fan = timed.layer("fan")
        for (frame in 0 until 40) {
            assertEquals(frameAt(timed, fan, frame, 0), frameAt(timed, fan, frame, 12345))
        }
    }

    @Test
    fun aTimelineOnASteppedLayerHoldsInSteps() {
        val layers = """[ { "id": "body" }, { "id": "fan", "step": 4 } ]"""
        val content = TimedBundle.CONTENT + (
            "layers/fan.json" to """
                { "kind": "sprite", "sprite": "blades", "timeline": [ { "frame": 2, "hold": 1 }, { "frame": 0, "hold": 3 } ] }
            """.trimIndent()
            )
        val bundle = TimedBundle.bundle(TimedBundle.manifest(animations = "{}", layers = layers), content)
        val shown = (0 until 32).joinToString("") { frameAt(bundle, bundle.layer("fan"), it, 0).toString() }
        assertEquals("22220000000000002222000000000000", shown)
    }

    @Test
    fun scheduledEventsArePinnedForSeedOne() {
        assertSequences(
            seed = 1,
            tail = "001122330011223300112233333333111111111100112233001122330011223300112233111122223333223300112233",
            tuft = "001100110011001100110011111111110000000000110011001100110011001100110011001100110011001100110011",
            eye = "1121100001121100011211000011211000112110011211000112110011211000",
        )
    }

    @Test
    fun scheduledEventsArePinnedForSeed42() {
        assertSequences(
            seed = 42,
            tail = "001111222233333300112233001122330011223300112233333333111111111100112233001111112222333300112233",
            tuft = "001100110011001100110011001100110011001100110011111111110000000000110011001100110011001100110011",
            eye = "0112110000112110011211001121100001121100112110000011211000112110",
        )
    }

    @Test
    fun scheduledEventsArePinnedForANegativeSeed() {
        assertSequences(
            seed = -90210,
            tail = "003333331111111111112233001122330011223300112233001111222233333300112233001111222233333300112233",
            tuft = "001111111100000000110011001100110011001100110011001100110011001100110011001100110011001100110011",
            eye = "0011211011211000001121101121100011211000011211000011211011211000",
        )
    }

    @Test
    fun theSameSeedAndFrameAlwaysGiveTheSameFrame() {
        val tail = timed.layer("tail")
        for (frame in -50..300) {
            assertEquals(frameAt(timed, tail, frame, 77), frameAt(timed, tail, frame, 77))
        }
    }

    @Test
    fun differentSeedsFallOutOfStep() {
        val eye = timed.layer("eye")
        val shown = (0..50).map { seed -> (0 until 64).joinToString("") { frameAt(timed, eye, it, seed).toString() } }
        assertTrue(shown.toSet().size > 25)
    }

    @Test
    fun scheduleTimeUsesTheAbsoluteFrameNotTheLoop() {
        val eye = timed.layer("eye")
        val first = (0 until 16).map { frameAt(timed, eye, it, 1) }
        val second = (0 until 16).map { frameAt(timed, eye, it + timed.loop, 1) }
        assertNotEquals(first, second)
    }

    @Test
    fun anEventNeverCrossesItsWindowsEnd() {
        val eye = timed.layer("eye")
        for (seed in 0 until 200) {
            for (window in -6 until 60) {
                val shown = (0 until 8).map { frameAt(timed, eye, window * 8 + it, seed) }
                val lit = shown.indices.filter { shown[it] != 0 }
                assertEquals(5, lit.size, "seed $seed window $window")
                assertEquals(lit.first() + 4, lit.last(), "seed $seed window $window")
                assertTrue(lit.first() in 0..2, "seed $seed window $window")
            }
        }
    }

    @Test
    fun chanceOneAlwaysFiresAndChanceZeroNeverDoes() {
        val always = TimedBundle.bundle(TimedBundle.pick("1.0"))
        val never = TimedBundle.bundle(TimedBundle.pick("0.0"))
        for (seed in listOf(0, 5, -9)) {
            for (window in 0 until 500) {
                assertNotEquals(0, frameAt(always, always.layer("eye"), window * 4, seed), "seed $seed window $window")
                assertEquals(0, frameAt(never, never.layer("eye"), window * 4, seed), "seed $seed window $window")
            }
        }
    }

    @Test
    fun weightsPickTheAnimationsInProportion() {
        val bundle = TimedBundle.bundle(TimedBundle.pick("1.0"))
        assertEquals(mapOf(0 to 0, 1 to 2980, 2 to 1020), pickedCounts(bundle, seed = 5))
    }

    @Test
    fun chanceDecidesTheShareOfWindowsThatPlay() {
        val bundle = TimedBundle.bundle(TimedBundle.pick("0.5"))
        assertEquals(mapOf(0 to 1945, 1 to 1533, 2 to 522), pickedCounts(bundle, seed = 5))
    }

    @Test
    fun equalWeightsSplitEvenly() {
        val bundle = TimedBundle.bundle(TimedBundle.pick("1.0", firstWeight = 1))
        assertEquals(mapOf(0 to 0, 1 to 2025, 2 to 1975), pickedCounts(bundle, seed = 11))
    }

    @Test
    fun anEventThatLeavesALayerAloneLeavesItOnItsNormalAnimation() {
        val tuft = timed.layer("tuft")
        val plainTuft = plain.layer("tuft")
        val differs = (0 until 96).count { frameAt(timed, tuft, it, 1) != frameAt(plain, plainTuft, it, 1) }
        assertEquals(8, differs)
    }

    @Test
    fun statesListEveryFrameALayerCanShow() {
        assertEquals(setOf(0, 1, 2, 3), states(timed, timed.layer("lamp")))
        assertEquals(setOf(0, 1, 2), states(timed, timed.layer("fan")))
        assertEquals(setOf(0, 1, 2, 3), states(timed, timed.layer("tail")))
        assertEquals(setOf(0, 1), states(timed, timed.layer("tuft")))
        assertEquals(setOf(0, 1, 2), states(timed, timed.layer("eye")))
        assertEquals(setOf(0), states(plain, plain.layer("eye")))
    }

    @Test
    fun statesCoverEveryFrameTheLayerEverShows() {
        for (layer in timed.layers) {
            val allowed = states(timed, layer)
            for (seed in 0 until 20) {
                for (frame in 0 until 200) {
                    assertTrue(frameAt(timed, layer, frame, seed) in allowed, "${layer.id} at $frame")
                }
            }
        }
    }

    @Test
    fun statesListIsSorted() {
        assertEquals(listOf(0, 1, 2), states(timed, timed.layer("eye")).toList())
    }

    private fun assertSequences(seed: Int, tail: String, tuft: String, eye: String) {
        assertEquals(tail, shown("tail", seed, 96))
        assertEquals(tuft, shown("tuft", seed, 96))
        assertEquals(eye, shown("eye", seed, 64))
    }

    private fun shown(id: String, seed: Int, frames: Int): String {
        val layer = timed.layer(id)
        return (0 until frames).joinToString("") { frameAt(timed, layer, it, seed).toString() }
    }

    private fun pickedCounts(bundle: NameplateBundle, seed: Int): Map<Int, Int> {
        val eye = bundle.layer("eye")
        val counts = mutableMapOf(0 to 0, 1 to 0, 2 to 0)
        for (window in 0 until 4000) {
            val frame = frameAt(bundle, eye, window * 4, seed)
            counts[frame] = counts.getValue(frame) + 1
        }
        return counts
    }
}
