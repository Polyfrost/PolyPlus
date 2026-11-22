package org.polyfrost.polyplus.nameplate.engine.bundle

import org.polyfrost.polyplus.nameplate.engine.api.source.BundleSource

internal object SampleBundle {
    val MANIFEST = """
        {
          "format": 1,
          "id": "sample_plate",
          "requires": ["canvas", "sprites"],
          "fps": 8,
          "loop": 16,
          "ownsBackground": true,
          "params": { "radius": 3, "min_width": 30 },
          "text": { "color": "text", "shadow": "auto" },
          "badge": {
            "placement": "placed",
            "anchor": "top_right",
            "offset": [5, -2],
            "decoration": "auto",
            "candidates": ["text", "#FFFFFF"]
          },
          "layers": [
            { "id": "trail", "step": 2 },
            { "id": "ornaments" },
            { "id": "body" },
            { "id": "ribbon", "depth": "over_background" }
          ],
          "variants": {
            "sand": { "palette": "sand" },
            "dusk": { "palette": "dusk" },
            "moss": { "palette": "moss" }
          },
          "designerNotes": "ignored by the loader"
        }
    """.trimIndent()

    val PALETTES = """
        {
          "slots": [
            { "id": "fill", "name": "Fill", "ramp": ["#F2E6C9"] },
            { "id": "border", "name": "Border", "ramp": ["#B08A4F"] },
            { "id": "accent", "name": "Accent", "ramp": ["#D9B77E"] },
            { "id": "ribbon", "name": "Ribbon", "ramp": ["#3B1F5C", "#5E3591", "#8450C4", "#A97BE0", "#CDB0F2"] },
            { "id": "trail", "name": "Trail", "ramp": ["#B08A4F", "#F2E6C9"] },
            { "id": "text", "name": "Text", "ramp": ["#4A3410"] }
          ],
          "colorways": {
            "sand": {},
            "dusk": {
              "fill": ["#E3E8F5"],
              "border": ["#6F7FAF"],
              "accent": ["#A8B4D8"],
              "ribbon": ["#1F4A2E", "#2F6E45", "#44965F", "#6CBA84", "#A5DDB5"],
              "trail": ["#6F7FAF", "#E3E8F5"],
              "text": ["#23305C"]
            },
            "moss": {
              "fill": ["#DCEBD8"],
              "border": ["#6E9A66"],
              "accent": ["#A3C79C"],
              "ribbon": ["#5C2A12", "#8C4220", "#C25F33", "#E0875C", "#F2B597"],
              "trail": ["#6E9A66", "#DCEBD8"],
              "text": ["#2C4A27"]
            }
          }
        }
    """.trimIndent()

    val CONTENT = mapOf(
        "layers/body.json" to """
            { "kind": "body", "padding": [4, 2, 4, 2], "minWidth": 30, "radius": 3,
              "fill": { "color": "fill" }, "rings": [ { "color": "border" } ] }
        """.trimIndent(),
        "layers/ornaments.json" to """
            { "kind": "sprite", "sprite": "spike", "of": "body", "anchor": "top_left", "pivot": "bottom_left",
              "offset": [5, 1], "mirror": true }
        """.trimIndent(),
        "layers/ribbon.json" to """
            { "kind": "sprite", "sprite": "knot", "of": "body", "anchor": "top_left", "offset": [-2, -2] }
        """.trimIndent(),
        "layers/trail.json" to """
            { "kind": "sprite", "sprite": "spark", "of": "body", "anchor": "bottom_right", "pivot": "top_left",
              "offset": [-2, -1] }
        """.trimIndent(),
        "sprites/spike.json" to """
            { "colors": { "b": "border", "a": "accent" }, "rows": [".b..", "bab.", "baab", "baab"] }
        """.trimIndent(),
        "sprites/knot.json" to """
            { "colors": { "d": "ribbon[0]", "r": "ribbon[2]", "h": "ribbon[4]" }, "rows": ["d.d", "rhr", "d.d"] }
        """.trimIndent(),
        "sprites/spark.json" to """
            { "colors": { "o": "trail[0]", "i": "trail[1]" }, "frames": [ ["oi", "io"], ["io", "oi"] ] }
        """.trimIndent(),
    )

    fun files(
        manifest: String = MANIFEST,
        palettes: String = PALETTES,
        content: Map<String, String> = CONTENT,
    ): BundleSource = BundleSource.of(mapOf("manifest.json" to manifest, "palettes.json" to palettes) + content)
}
