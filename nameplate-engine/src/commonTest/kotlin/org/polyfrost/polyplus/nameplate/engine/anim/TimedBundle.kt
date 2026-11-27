package org.polyfrost.polyplus.nameplate.engine.anim

import kotlin.test.assertIs
import org.polyfrost.polyplus.nameplate.engine.api.bundle.NameplateBundle
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoad
import org.polyfrost.polyplus.nameplate.engine.api.load.BundleLoader
import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource
import org.polyfrost.polyplus.nameplate.engine.internal.canvas.canvas

internal object TimedBundle {
    private val LAYERS = """
        [
          { "id": "body" },
          { "id": "lamp", "step": 2 },
          { "id": "fan" },
          { "id": "tail", "step": 2 },
          { "id": "tuft", "step": 2 },
          { "id": "eye" }
        ]
    """.trimIndent()

    val ANIMATIONS = """
        {
          "sway": {
            "schedule": { "id": "tail_moves", "window": 12, "jitter": 3, "chance": 0.75, "weight": 3 },
            "length": 6,
            "tracks": { "tail": { "frame": [[0, 1], [2, 2], [4, 3]] } }
          },
          "flick": {
            "schedule": { "id": "tail_moves", "window": 12, "jitter": 3, "chance": 0.75 },
            "length": 8,
            "tracks": { "tail": { "frame": [[0, 3], [3, 1]] }, "tuft": { "frame": [[0, 1], [4, 0]] } }
          },
          "blink": {
            "schedule": { "window": 8, "jitter": 2, "chance": 1.0 },
            "length": 5,
            "tracks": { "eye": { "frame": [[0, 1], [2, 2], [3, 1]] } }
          }
        }
    """.trimIndent()

    val MANIFEST = manifest()

    fun manifest(animations: String = ANIMATIONS, layers: String = LAYERS): String = """
        {
          "format": 1,
          "id": "timed_plate",
          "requires": ["canvas", "sprites"],
          "fps": 8,
          "loop": 16,
          "layers": $layers,
          "animations": $animations
        }
    """.trimIndent()

    fun pick(chance: String, firstWeight: Int = 3): String = manifest(
        layers = """[ { "id": "body" }, { "id": "eye" } ]""",
        animations = """
            {
              "heavy": {
                "schedule": { "id": "pick", "window": 4, "jitter": 0, "chance": $chance, "weight": $firstWeight },
                "length": 2,
                "tracks": { "eye": { "frame": [[0, 1]] } }
              },
              "light": {
                "schedule": { "id": "pick", "window": 4, "jitter": 0, "chance": $chance },
                "length": 2,
                "tracks": { "eye": { "frame": [[0, 2]] } }
              }
            }
        """.trimIndent(),
    )

    val PALETTES = """
        { "slots": [ { "id": "fill", "ramp": ["#F2E6C9"] }, { "id": "ink", "ramp": ["#4A3410"] } ] }
    """.trimIndent()

    val CONTENT = mapOf(
        "layers/body.json" to """
            { "kind": "body", "padding": [4, 2, 4, 2], "radius": 2, "fill": { "color": "fill" } }
        """.trimIndent(),
        "layers/lamp.json" to """
            { "kind": "sprite", "sprite": "glow", "of": "body", "anchor": "top_left" }
        """.trimIndent(),
        "layers/fan.json" to """
            { "kind": "sprite", "sprite": "blades", "of": "body", "anchor": "top_right",
              "timeline": [
                { "frame": 0, "hold": 4 }, { "frame": 1, "hold": 2 }, { "frame": 2, "hold": 6 }, { "frame": 1, "hold": 4 }
              ] }
        """.trimIndent(),
        "layers/tail.json" to """
            { "kind": "sprite", "sprite": "wisp", "of": "body", "anchor": "bottom_right" }
        """.trimIndent(),
        "layers/tuft.json" to """
            { "kind": "sprite", "sprite": "tuft", "of": "body", "anchor": "bottom_left" }
        """.trimIndent(),
        "layers/eye.json" to """
            { "kind": "sprite", "sprite": "lid", "of": "body", "anchor": "left",
              "timeline": [ { "frame": 0, "hold": 16 } ] }
        """.trimIndent(),
        "sprites/glow.json" to """
            { "colors": { "f": "fill" }, "frames": [ ["f."], [".f"], ["ff"], ["f."] ] }
        """.trimIndent(),
        "sprites/blades.json" to """
            { "colors": { "f": "fill" }, "frames": [ ["f."], [".f"], ["ff"] ] }
        """.trimIndent(),
        "sprites/wisp.json" to """
            { "colors": { "i": "ink" }, "frames": [ ["i."], [".i"], ["ii"], ["i."] ] }
        """.trimIndent(),
        "sprites/tuft.json" to """
            { "colors": { "i": "ink" }, "frames": [ ["i."], [".i"] ] }
        """.trimIndent(),
        "sprites/lid.json" to """
            { "colors": { "i": "ink" }, "frames": [ ["ii"], ["i."], [".."] ] }
        """.trimIndent(),
    )

    fun files(
        manifest: String = MANIFEST,
        palettes: String = PALETTES,
        content: Map<String, String> = CONTENT,
    ): BundleSource = BundleSource.of(mapOf("manifest.json" to manifest, "palettes.json" to palettes) + content)

    fun load(
        manifest: String = MANIFEST,
        content: Map<String, String> = CONTENT,
    ): BundleLoad = BundleLoader(setOf("canvas", "sprites")).load(files(manifest, content = content))

    fun bundle(manifest: String = MANIFEST, content: Map<String, String> = CONTENT): NameplateBundle {
        val result = load(manifest, content)
        return assertIs<BundleLoad.Loaded>(result, result.toString()).bundle
    }
}
