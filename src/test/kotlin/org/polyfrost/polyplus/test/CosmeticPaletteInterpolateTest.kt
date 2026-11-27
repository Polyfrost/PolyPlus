package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalette
import org.polyfrost.polyplus.client.cosmetics.colors.CosmeticPalettes
import java.nio.file.Files
import java.nio.file.Path

class CosmeticPaletteInterpolateTest {
    @Test
    fun `a variant follows its manifest palette before a colourway of the same name`() {
        val palettes = """{"slots":[{"id":"main","ramp":["#000000","#FFFFFF"]}],"colorways":{"red":{"main":["#FF0000"]},"blue":{"main":["#0000FF"]}}}"""
        val manifest = """{"kind":"nameplate","variants":{"red":{"palette":"blue"},"plain":{}}}"""
        val palette = CosmeticPalettes.parse(palettes.toByteArray(), manifest.toByteArray())!!
        assertEquals("blue", palette.colorwayFor("red"))
        assertEquals("blue", palette.colorwayFor("blue"))
        assertNull(palette.colorwayFor("plain"))
    }

    @Test
    fun `a nameplate without a variant key uses its first declared variant`() {
        val palettes = """{"slots":[{"id":"main","ramp":["#000000","#FFFFFF"]}],"colorways":{"red":{"main":["#FF0000"]},"blue":{"main":["#0000FF"]}}}"""
        val manifest = """{"kind":"nameplate","variants":{"ocean":{"palette":"blue"},"fire":{"palette":"red"}}}"""
        val palette = CosmeticPalettes.parse(palettes.toByteArray(), manifest.toByteArray())!!
        assertEquals("blue", palette.colorwayFor(null))
        assertEquals("red", palette.colorwayFor("fire"))
        assertEquals("blue", palette.colorwayFor("red"))
    }

    @Test
    fun `a nameplate whose first variant has no palette starts from the base ramps`() {
        val palettes = """{"slots":[{"id":"main","ramp":["#000000","#FFFFFF"]}],"colorways":{"red":{"main":["#FF0000"]}}}"""
        val manifest = """{"kind":"nameplate","variants":{"plain":{},"fire":{"palette":"red"}}}"""
        val palette = CosmeticPalettes.parse(palettes.toByteArray(), manifest.toByteArray())!!
        assertNull(palette.colorwayFor(null))
        assertNull(palette.designedColorway(null))
    }

    @Test
    fun `a declared variant without a palette keeps the base ramps over a same-named colourway`() {
        val palettes = """{"slots":[{"id":"main","ramp":["#000000","#FFFFFF"]}],"colorways":{"red":{"main":["#FF0000"]},"blue":{"main":["#0000FF"]}}}"""
        val manifest = """{"kind":"item","variants":{"red":{},"blue":{"palette":"blue"}}}"""
        val palette = CosmeticPalettes.parse(palettes.toByteArray(), manifest.toByteArray())!!
        assertNull(palette.colorwayFor("red"))
        assertNull(palette.designedColorway("red"))
        assertEquals("blue", palette.designedColorway("blue"))
        assertEquals("green", palette.designedColorway("green"))
        assertNull(palette.colorwayFor(null))
    }

    @Test
    fun `a designed colourway keeps a deliberate base ramp when read from disk`(@TempDir dir: Path) {
        Files.writeString(dir.resolve("palettes.json"), """{"slots":[{"id":"main","ramp":["#000000"]}],"colorways":{"red":{"main":["#FF0000"]}}}""")
        Files.writeString(dir.resolve("manifest.json"), """{"kind":"item","variants":{"red":{}}}""")
        assertNull(CosmeticPalettes.designedColorway(1, dir, "red"))
    }

    @Test
    fun `an interpolate count inside the tone limit expands the ramp`() {
        val palette = palette("5")
        assertEquals(5, palette.slots[0].interpolate)
        assertEquals(5, palette.ramp(palette.slots[0], null).size)
    }

    @Test
    fun `an interpolate count up to sixteen tones is kept`() {
        assertEquals(16, palette("16").slots[0].interpolate)
    }

    @Test
    fun `a huge interpolate count is ignored`() {
        val palette = palette("2000000000")
        assertNull(palette.slots[0].interpolate)
        assertEquals(2, palette.ramp(palette.slots[0], null).size)
    }

    @Test
    fun `an interpolate count past sixteen tones is ignored`() {
        assertNull(palette("17").slots[0].interpolate)
    }

    @Test
    fun `an interpolate count too large for an int is ignored`() {
        assertNull(palette("1e30").slots[0].interpolate)
    }

    @Test
    fun `a fractional interpolate count is ignored`() {
        assertNull(palette("4.5").slots[0].interpolate)
    }

    @Test
    fun `an interpolate count below the stop count is ignored`() {
        assertNull(palette("1").slots[0].interpolate)
    }

    @Test
    fun `a single stop ramp never interpolates`() {
        val json = """{"slots":[{"id":"main","ramp":["#112233"],"interpolate":4}]}"""
        assertNull(CosmeticPalettes.parse(json.toByteArray(), null)!!.slots[0].interpolate)
    }

    private fun palette(count: String): CosmeticPalette {
        val json = """{"slots":[{"id":"main","ramp":["#000000","#FFFFFF"],"interpolate":$count}]}"""
        return CosmeticPalettes.parse(json.toByteArray(), null)!!
    }
}
