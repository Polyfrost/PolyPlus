package org.polyfrost.polyplus.nameplate.engine.bundle

import kotlin.test.Test
import kotlin.test.assertTrue
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.BadgeJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.LayerJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.ManifestJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.PalettesJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.SlotJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.TextJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.json.VariantJson
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.model.Fill
import org.polyfrost.polyplus.nameplate.engine.internal.bundle.validate.BundleValidator

class BundleValidatorTest {
    @Test
    fun aValidBundlePasses() {
        assertTrue(BundleValidator.validate(MANIFEST, PALETTES).isEmpty())
    }

    @Test
    fun manifestRules() {
        assertIssue("manifest.json#id", manifest = MANIFEST.copy(id = "Sample Plate"))
        assertIssue("manifest.json#fps", manifest = MANIFEST.copy(fps = 0))
        assertIssue("manifest.json#fps", manifest = MANIFEST.copy(fps = 25))
        assertIssue("manifest.json#loop", manifest = MANIFEST.copy(loop = 601))
        assertIssue("manifest.json#params.Radius", manifest = MANIFEST.copy(params = mapOf("Radius" to 1.0)))
        assertIssue("manifest.json#params.r", manifest = MANIFEST.copy(params = mapOf("r" to Double.NaN)))
        assertIssue("manifest.json#params", manifest = MANIFEST.copy(params = (0..64).associate { "p$it" to 0.0 }))
    }

    @Test
    fun layerRules() {
        val tooMany = (0..32).map { LayerJson("l$it") }
        assertIssue("manifest.json#layers", manifest = MANIFEST.copy(layers = tooMany, variants = emptyMap()))
        assertIssue("manifest.json#layers.body", manifest = MANIFEST.copy(layers = MANIFEST.layers + LayerJson("body")))
        assertIssue("manifest.json#layers.body.depth", manifest = withLayer(LayerJson("body", depth = "front")))
        assertIssue("manifest.json#layers.body.step", manifest = withLayer(LayerJson("body", step = 0)))
        assertIssue("manifest.json#layers.body.step", manifest = withLayer(LayerJson("body", step = 3)))
        assertIssue("manifest.json#layers.body.mask", manifest = withLayer(LayerJson("body", mask = "body")))
        assertIssue("manifest.json#layers.body.mask", manifest = withLayer(LayerJson("body", mask = "sky")))
        assertTrue(BundleValidator.validate(withLayer(LayerJson("body", step = 4, mask = "ribbon")), PALETTES).isEmpty())
    }

    @Test
    fun paletteRules() {
        assertIssue("palettes.json#slots", palettes = PalettesJson(emptyList()))
        assertIssue("palettes.json#slots", palettes = PalettesJson((0..32).map { SlotJson("s$it", ramp = listOf("#FFFFFF")) }))
        assertIssue("palettes.json#slots.auto", palettes = withSlot(SlotJson("auto", ramp = listOf("#FFFFFF"))))
        assertIssue("palettes.json#slots.Fill", palettes = withSlot(SlotJson("Fill", ramp = listOf("#FFFFFF"))))
        assertIssue("palettes.json#slots.fill", palettes = withSlot(SlotJson("fill", ramp = listOf("#FFFFFF"))))
        assertIssue("palettes.json#slots.x.ramp", palettes = withSlot(SlotJson("x", ramp = emptyList())))
        assertIssue("palettes.json#slots.x.ramp", palettes = withSlot(SlotJson("x", ramp = List(17) { "#FFFFFF" })))
        assertIssue("palettes.json#slots.x.ramp[0]", palettes = withSlot(SlotJson("x", ramp = listOf("white"))))
        assertIssue("palettes.json#slots.x.interpolate", palettes = withSlot(SlotJson("x", ramp = listOf("#FFFFFF"), interpolate = 4)))
        assertIssue("palettes.json#slots.x.interpolate", palettes = withSlot(SlotJson("x", ramp = listOf("#FFFFFF", "#000000"), interpolate = 17)))
        assertIssue("palettes.json#colorways.rose.sky", palettes = PALETTES.copy(colorways = mapOf("rose" to mapOf("sky" to listOf("#FFFFFF")))))
        assertIssue("palettes.json#colorways.Rose", palettes = PALETTES.copy(colorways = mapOf("Rose" to emptyMap())), manifest = MANIFEST.copy(variants = emptyMap()))
    }

    @Test
    fun variantRules() {
        assertIssue("manifest.json#variants.sage.palette", manifest = MANIFEST.copy(variants = mapOf("sage" to VariantJson("sage"))))
        assertIssue("manifest.json#variants.rose.hide", manifest = MANIFEST.copy(variants = mapOf("rose" to VariantJson("rose", hide = listOf("sky")))))
        assertIssue("manifest.json#variants.rose.swap", manifest = MANIFEST.copy(variants = mapOf("rose" to VariantJson("rose", swap = mapOf("ribbon" to "sky")))))
        assertIssue("manifest.json#variants.Rose", manifest = MANIFEST.copy(variants = mapOf("Rose" to VariantJson("rose"))))
    }

    @Test
    fun theDefaultColourwayExistsOnlyWithoutColourways() {
        val noColourways = PALETTES.copy(colorways = emptyMap())
        val manifest = MANIFEST.copy(variants = mapOf("plain" to VariantJson("default")))
        assertTrue(BundleValidator.validate(manifest, noColourways).isEmpty())
        assertIssue("manifest.json#variants.plain.palette", manifest = manifest)
    }

    @Test
    fun textRules() {
        assertIssue("manifest.json#text.mode", manifest = MANIFEST.copy(text = TextJson(mode = "wavy")))
        assertIssue("manifest.json#text.formatting", manifest = MANIFEST.copy(text = TextJson(formatting = "bold")))
        assertIssue("manifest.json#text.color", manifest = MANIFEST.copy(text = TextJson(color = "sky")))
        assertIssue("manifest.json#text.shadow", manifest = MANIFEST.copy(text = TextJson(shadow = "fill[1]")))
        assertIssue("manifest.json#text.outline", manifest = MANIFEST.copy(text = TextJson(outline = "#12")))
        assertIssue("manifest.json#text.candidates", manifest = MANIFEST.copy(text = TextJson(candidates = emptyList())))
        assertIssue("manifest.json#text.candidates[0]", manifest = MANIFEST.copy(text = TextJson(candidates = listOf("auto"))))
        assertIssue("manifest.json#text.backdropHint[1]", manifest = MANIFEST.copy(text = TextJson(backdropHint = listOf("fill", "none"))))
        val good = TextJson(color = "#FFFFFF", shadow = "ribbon[4]", outline = "auto", candidates = listOf("fill", "#000000"), backdropHint = listOf("ribbon[2]"))
        assertTrue(BundleValidator.validate(MANIFEST.copy(text = good), PALETTES).isEmpty())
    }

    @Test
    fun badgeRules() {
        assertIssue("manifest.json#badge.placement", manifest = MANIFEST.copy(badge = BadgeJson(placement = "floating")))
        assertIssue("manifest.json#badge.anchor", manifest = MANIFEST.copy(badge = BadgeJson(placement = "placed")))
        assertIssue("manifest.json#badge.anchor", manifest = MANIFEST.copy(badge = BadgeJson(placement = "placed", anchor = "name_left")))
        assertIssue("manifest.json#badge.offset", manifest = MANIFEST.copy(badge = BadgeJson(offset = listOf(1))))
        assertIssue("manifest.json#badge.offset", manifest = MANIFEST.copy(badge = BadgeJson(offset = listOf(0, 40))))
        assertIssue("manifest.json#badge.tint", manifest = MANIFEST.copy(badge = BadgeJson(tint = "auto")))
        assertIssue("manifest.json#badge.decoration", manifest = MANIFEST.copy(badge = BadgeJson(decoration = "glow")))
        assertIssue("manifest.json#badge.decorationColor", manifest = MANIFEST.copy(badge = BadgeJson(decoration = "outline")))
        assertIssue("manifest.json#badge.decorationColor", manifest = MANIFEST.copy(badge = BadgeJson(decorationColor = "#000000")))
        assertIssue("manifest.json#badge.candidates", manifest = MANIFEST.copy(badge = BadgeJson(candidates = emptyList())))
        val good = BadgeJson(placement = "placed", anchor = "bottom", offset = listOf(-3, 2), tint = "fill", decoration = "shadow", decorationColor = "ribbon[0]")
        assertTrue(BundleValidator.validate(MANIFEST.copy(badge = good), PALETTES).isEmpty())
    }

    private fun assertIssue(path: String, manifest: ManifestJson = MANIFEST, palettes: PalettesJson = PALETTES) {
        val issues = BundleValidator.validate(manifest, palettes)
        assertTrue(issues.any { it.path == path }, "expected $path in $issues")
    }

    private fun withLayer(layer: LayerJson): ManifestJson =
        MANIFEST.copy(layers = MANIFEST.layers.map { if (it.id == layer.id) layer else it })

    private fun withSlot(slot: SlotJson): PalettesJson = PALETTES.copy(slots = PALETTES.slots + slot)

    private companion object {
        val MANIFEST = ManifestJson(
            format = 1,
            id = "sample_plate",
            fps = 8,
            loop = 16,
            layers = listOf(LayerJson("body"), LayerJson("ribbon", depth = "over_background")),
            variants = mapOf("rose" to VariantJson("rose"), "noir" to VariantJson("noir", hide = listOf("ribbon"))),
        )
        val PALETTES = PalettesJson(
            slots = listOf(
                SlotJson("fill", ramp = listOf("#FFC6DC")),
                SlotJson("ribbon", ramp = listOf("#6E0C18", "#FF7A88"), interpolate = 5),
            ),
            colorways = mapOf("rose" to emptyMap(), "noir" to mapOf("fill" to listOf("#202020"))),
        )
    }
}
