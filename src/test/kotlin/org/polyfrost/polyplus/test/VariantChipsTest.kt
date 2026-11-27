package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.EquipChoice
import org.polyfrost.polyplus.client.commandNumber
import org.polyfrost.polyplus.client.cosmetics.CosmeticGroupView
import org.polyfrost.polyplus.client.equipChoice
import org.polyfrost.polyplus.client.gui.wardrobe.lockerVariants
import org.polyfrost.polyplus.client.gui.wardrobe.variantChips
import org.polyfrost.polyplus.client.network.http.responses.BodySlot
import org.polyfrost.polyplus.client.network.http.responses.CosmeticDefinition
import org.polyfrost.polyplus.client.network.http.responses.CosmeticType

class VariantChipsTest {
    private fun glove(id: Int, side: BodySlot, variant: String = "Ember Gloves", model: String? = null) = CosmeticDefinition(
        id = id,
        type = CosmeticType.Glove,
        hash = "h$id",
        allowedSlots = listOf(side),
        groupId = 1,
        groupName = "Ember Gloves",
        variantName = variant,
        model = model,
    )

    private fun gloves(vararg variants: CosmeticDefinition) =
        CosmeticGroupView(1, CosmeticType.Glove, "Ember Gloves", emptyList(), variants.toList())

    @Test
    fun `same-named left and right gloves both get their own label`() {
        val chips = variantChips(gloves(glove(10, BodySlot.LeftHand), glove(11, BodySlot.RightHand)))

        assertEquals(listOf("Left Ember Gloves", "Right Ember Gloves", "Both Ember Gloves"), chips.map { it.name })
        assertEquals(listOf(listOf(10), listOf(11), listOf(10, 11)), chips.map { it.pieces })
    }

    @Test
    fun `skin model copies of one side still collapse to one label`() {
        val chips = variantChips(
            gloves(
                glove(10, BodySlot.LeftHand, model = "wide"),
                glove(12, BodySlot.LeftHand, model = "slim"),
                glove(11, BodySlot.RightHand, model = "wide"),
                glove(13, BodySlot.RightHand, model = "slim"),
            ),
        )

        assertEquals(listOf(listOf(10), listOf(11), listOf(10, 11)), chips.map { it.pieces })
    }

    @Test
    fun `a glove sold for one hand keeps its plain name`() {
        val chips = variantChips(gloves(glove(10, BodySlot.LeftHand, variant = "Gold")))

        assertEquals(listOf("Gold"), chips.map { it.name })
        assertEquals(BodySlot.LeftHand, chips.single().side)
    }

    @Test
    fun `an owned item in the locker only offers the variants the player owns or wears`() {
        val group = gloves(
            glove(10, BodySlot.LeftHand, variant = "Gold"),
            glove(11, BodySlot.LeftHand, variant = "Silver"),
            glove(12, BodySlot.LeftHand, variant = "Bronze"),
        )

        assertEquals(listOf(11), lockerVariants(group, owned = setOf(11), equipped = emptySet()).variants.map { it.id })
        assertEquals("Silver", variantChips(lockerVariants(group, owned = setOf(11), equipped = emptySet())).first().name)
        assertEquals(listOf(10, 11), lockerVariants(group, owned = setOf(11), equipped = setOf(10)).variants.map { it.id })
        assertEquals(listOf(10, 11, 12), lockerVariants(group, owned = emptySet(), equipped = emptySet()).variants.map { it.id })
    }

    private fun skinCopies() = gloves(
        glove(10, BodySlot.LeftHand, variant = "Gold", model = "wide"),
        glove(12, BodySlot.LeftHand, variant = "Gold", model = "slim"),
        glove(11, BodySlot.LeftHand, variant = "Silver"),
    )

    @Test
    fun `the equip command finds an owned skin copy behind an unowned one`() {
        val group = skinCopies()
        val owned = setOf(12)

        assertEquals(listOf(12), (equipChoice(group, 10, null, owned, emptySet()) as EquipChoice.Chosen).chip.pieces)
        assertEquals(listOf(12), (equipChoice(group, 12, null, owned, emptySet()) as EquipChoice.Chosen).chip.pieces)
        assertEquals(listOf(12), (equipChoice(group, 10, "gold", owned, emptySet()) as EquipChoice.Chosen).chip.pieces)
    }

    @Test
    fun `the equip command says when a chosen variant isn't owned`() {
        val group = skinCopies()
        val owned = setOf(12)

        assertEquals(EquipChoice.NotOwned("Silver"), equipChoice(group, 11, null, owned, emptySet()))
        assertEquals(EquipChoice.NotOwned("Silver"), equipChoice(group, 10, "Silver", owned, emptySet()))
        assertEquals(EquipChoice.NoSuchVariant(listOf("Gold")), equipChoice(group, 10, "Bronze", owned, emptySet()))
    }

    @Test
    fun `a group is numbered by the variant the locker shows first`() {
        val group = skinCopies()

        assertEquals(10, commandNumber(group, owned = emptySet()))
        assertEquals(12, commandNumber(group, owned = setOf(12)))
        assertEquals(12, commandNumber(group, owned = setOf(11, 12)))
        assertEquals(11, commandNumber(group, owned = setOf(11)))
    }

    @Test
    fun `the listed number equips the group's default variant`() {
        val group = gloves(
            glove(20, BodySlot.LeftHand, variant = "Gold"),
            glove(21, BodySlot.LeftHand, variant = "Silver"),
            glove(22, BodySlot.LeftHand, variant = "Bronze"),
        )
        val owned = setOf(21, 22)

        val number = commandNumber(group, owned)
        assertEquals(21, number)
        assertEquals(listOf(21), (equipChoice(group, number, null, owned, emptySet()) as EquipChoice.Chosen).chip.pieces)
        assertEquals(listOf(22), (equipChoice(group, 22, null, owned, emptySet()) as EquipChoice.Chosen).chip.pieces)
        assertEquals(EquipChoice.NotOwned("Gold"), equipChoice(group, 20, null, owned, emptySet()))
    }
}
