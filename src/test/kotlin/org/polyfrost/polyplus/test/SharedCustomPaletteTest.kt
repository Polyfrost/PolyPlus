package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.gui.wardrobe.PaletteCounterparts
import org.polyfrost.polyplus.client.gui.wardrobe.designedTargets
import org.polyfrost.polyplus.client.gui.wardrobe.paletteCounterparts
import org.polyfrost.polyplus.client.gui.wardrobe.paletteWrites
import org.polyfrost.polyplus.client.gui.wardrobe.sharedPalette
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

class SharedCustomPaletteTest {
    private fun glove(id: Int, colourway: String, model: String, slot: BodySlot) = CosmeticDefinition(
        id = id,
        type = CosmeticType.Glove,
        hash = "h$id",
        allowedSlots = listOf(slot),
        groupId = 1,
        variantName = colourway,
        model = model,
    )

    private val redWide = glove(10, "Red", "wide", BodySlot.LeftHand)
    private val redSlim = glove(11, "Red", "slim", BodySlot.LeftHand)
    private val blueWide = glove(12, "Blue", "wide", BodySlot.LeftHand)
    private val blueSlim = glove(13, "Blue", "slim", BodySlot.LeftHand)
    private val redRight = glove(14, "Red", "wide", BodySlot.RightHand)
    private val group = listOf(redWide, redSlim, blueWide, blueSlim, redRight)

    private val fur = mapOf("fur" to 0x112233)

    @Test
    fun `counterparts keep the hand and put the other model first`() {
        val found = paletteCounterparts(blueWide, group)
        assertEquals(listOf(blueSlim.id), found.sameColourway)
        assertEquals(listOf(redWide.id, redSlim.id), found.others)
    }

    @Test
    fun `a palette saved on red is the starting point on blue`() {
        val stored = mapOf(redWide.id to fur)
        val palette = sharedPalette(listOf(blueWide.id), paletteCounterparts(blueWide, group), { null }, stored::get)
        assertEquals(fur, palette)
    }

    @Test
    fun `a palette saved on the other model follows a skin switch`() {
        val stored = mapOf(redWide.id to fur, blueWide.id to mapOf("fur" to 0x445566))
        val palette = sharedPalette(listOf(redSlim.id), paletteCounterparts(redSlim, group), stored::get, stored::get)
        assertEquals(fur, palette)
    }

    @Test
    fun `a worn palette wins over a kept one on other colourways`() {
        val worn = mapOf(redSlim.id to fur)
        val kept = mapOf(redWide.id to mapOf("fur" to 0x445566), redSlim.id to fur)
        val palette = sharedPalette(listOf(blueWide.id), paletteCounterparts(blueWide, group), worn::get, kept::get)
        assertEquals(fur, palette)
    }

    @Test
    fun `nothing saved gives no palette`() {
        assertNull(sharedPalette(listOf(blueWide.id), paletteCounterparts(blueWide, group), { null }, { null }))
    }

    @Test
    fun `saving wears the palette on both skin models and leaves other colourways alone`() {
        val worn = mapOf(redWide.id to mapOf("fur" to 0x445566))
        val kept = mapOf(redWide.id to mapOf("fur" to 0x445566), redSlim.id to mapOf("fur" to 0x445566))
        val writes = paletteWrites(listOf(blueWide.id), paletteCounterparts(blueWide, group), fur, { true }, { false }, worn::get, kept::get)
        assertEquals(listOf(blueWide.id, blueSlim.id), writes)
    }

    @Test
    fun `saving another colourway keeps custom on the worn one and gives it the new palette`() {
        val worn = mapOf(redWide.id to mapOf("fur" to 0x445566))
        val equipped = setOf(redWide.id)
        val writes = paletteWrites(listOf(blueWide.id), paletteCounterparts(blueWide, group), fur, { true }, equipped::contains, worn::get, worn::get)
        assertEquals(listOf(blueWide.id, blueSlim.id, redWide.id), writes)
    }

    @Test
    fun `a worn colourway on designed colours is not switched to custom`() {
        val equipped = setOf(redWide.id)
        val writes = paletteWrites(listOf(blueWide.id), paletteCounterparts(blueWide, group), fur, { true }, equipped::contains, { null }, { null })
        assertEquals(listOf(blueWide.id, blueSlim.id), writes)
    }

    @Test
    fun `a three colourway glove save stays well under the colour rate limit`() {
        val gloves = listOf("Red", "Blue", "Green").flatMapIndexed { colourway, name ->
            listOf("wide", "slim").flatMapIndexed { model, fit ->
                listOf(BodySlot.LeftHand, BodySlot.RightHand).mapIndexed { side, slot -> glove(100 + colourway * 4 + model * 2 + side, name, fit, slot) }
            }
        }
        val edited = gloves.filter { it.variantName == "Blue" && it.model == "wide" }
        val counterparts = edited.map { paletteCounterparts(it, gloves) }
        val merged = PaletteCounterparts(
            counterparts.flatMap { it.sameColourway }.distinct(),
            counterparts.flatMap { it.others }.distinct(),
        )
        val wornRed = gloves.filter { it.variantName == "Red" && it.model == "wide" }.map { it.id }.toSet()
        val worn = wornRed.associateWith { mapOf("fur" to 0x445566) }
        val writes = paletteWrites(edited.map { it.id }, merged, fur, { true }, wornRed::contains, worn::get, worn::get)
        assertEquals(6, writes.size)
    }

    @Test
    fun `siblings already holding the palette are left alone`() {
        val worn = mapOf(blueSlim.id to fur)
        val kept = mapOf(blueSlim.id to fur, redWide.id to fur, redSlim.id to fur)
        val writes = paletteWrites(listOf(blueWide.id), paletteCounterparts(blueWide, group), fur, { true }, { false }, worn::get, kept::get)
        assertEquals(listOf(blueWide.id), writes)
    }

    @Test
    fun `pieces the player doesn't own are never written`() {
        val owned = setOf(blueWide.id, blueSlim.id)
        val writes = paletteWrites(listOf(blueWide.id), paletteCounterparts(blueWide, group), fur, owned::contains, { true }, { null }, { null })
        assertEquals(listOf(blueWide.id, blueSlim.id), writes)
    }

    @Test
    fun `the newest saved palette wins over an older one on the edited colourway`() {
        val older = mapOf("fur" to 0x445566)
        val stored = mapOf(redWide.id to older, blueWide.id to fur)
        val order = mapOf(redWide.id to 3L, blueWide.id to 7L)
        val palette = sharedPalette(listOf(redWide.id), paletteCounterparts(redWide, group), stored::get, stored::get) { order[it] ?: 0L }
        assertEquals(fur, palette)
    }

    @Test
    fun `an unordered sibling never beats an ordered save`() {
        val newer = mapOf("fur" to 0x445566)
        val stored = mapOf(redWide.id to fur, blueWide.id to newer)
        val order = mapOf(redWide.id to 2L)
        val palette = sharedPalette(listOf(blueWide.id), paletteCounterparts(blueWide, group), stored::get, stored::get) { order[it] ?: 0L }
        assertEquals(fur, palette)
    }

    @Test
    fun `designed colours turn custom off on both skin models`() {
        val worn = mapOf(blueWide.id to fur, blueSlim.id to fur)
        assertEquals(listOf(blueWide.id, blueSlim.id), designedTargets(listOf(blueWide.id), paletteCounterparts(blueWide, group), { true }, worn::get))
    }
}
