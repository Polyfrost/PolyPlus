package org.polyfrost.polyplus.nameplate.engine.bundle

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.api.issue.IssueCode
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource

class FlashLimitTest {
    private val loader = BundleLoader(setOf("canvas", "sprites"))

    @Test
    fun aFullPlateFlashingEveryFrameIsRefused() {
        val result = loader.load(strobe(hold = 1))
        val invalid = assertIs<BundleLoad.Invalid>(result, result.toString())
        assertTrue(invalid.issues.any { it.code == IssueCode.FLASH }, invalid.issues.toString())
    }

    @Test
    fun theSameFlashLoadsWithoutBudgets() {
        assertIs<BundleLoad.Loaded>(loader.load(strobe(hold = 1), budgets = false))
    }

    @Test
    fun aSlowFullPlateFadeIsAllowed() {
        val result = loader.load(strobe(hold = 10))
        assertIs<BundleLoad.Loaded>(result, result.toString())
    }

    private fun strobe(hold: Int): BundleSource {
        val steps = (0 until LOOP / hold).joinToString(", ") { """{ "frame": ${it % 2}, "hold": $hold }""" }
        val dark = List(HEIGHT) { "\"" + "d".repeat(WIDTH) + "\"" }.joinToString(", ")
        val light = List(HEIGHT) { "\"" + "l".repeat(WIDTH) + "\"" }.joinToString(", ")
        return BundleSource.of(
            mapOf(
                "manifest.json" to """
                    { "format": 1, "id": "strobe", "requires": ["canvas", "sprites"], "fps": $LOOP, "loop": $LOOP,
                      "layers": [{ "id": "body" }, { "id": "strobe" }] }
                """.trimIndent(),
                "palettes.json" to """
                    { "slots": [
                      { "id": "fill", "ramp": ["#808080"] },
                      { "id": "dark", "ramp": ["#000000"] },
                      { "id": "light", "ramp": ["#FFFFFF"] }
                    ] }
                """.trimIndent(),
                "layers/body.json" to """{ "kind": "body", "fill": { "color": "fill" } }""",
                "layers/strobe.json" to """
                    { "kind": "sprite", "sprite": "strobe", "of": "body", "anchor": "top_left", "offset": [-8, -4],
                      "timeline": [$steps] }
                """.trimIndent(),
                "sprites/strobe.json" to """
                    { "colors": { "d": "dark", "l": "light" }, "frames": [[$dark], [$light]] }
                """.trimIndent(),
            ),
        )
    }

    private companion object {
        const val LOOP = 20
        const val WIDTH = 48
        const val HEIGHT = 16
    }
}
